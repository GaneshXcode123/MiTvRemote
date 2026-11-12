package com.mitvremote.app.tv

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** Android TV remote protocol (v2) on port 6466. */
class RemoteClient(
    private val ctx: Context,
    private val host: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady(c: RemoteClient)
        fun onClosed(c: RemoteClient, reason: String)
        fun onPower(c: RemoteClient, on: Boolean)
        fun onVolume(c: RemoteClient, level: Int, max: Int, muted: Boolean)
    }

    private companion object {
        const val F_PING = 1
        const val F_KEY = 2
        const val F_IME = 4
        const val F_VOICE = 8
        const val F_POWER = 32
        const val F_VOLUME = 64
        const val F_APP_LINK = 512
        const val OUR_FEATURES = F_PING or F_KEY or F_IME or F_VOICE or F_POWER or F_VOLUME or F_APP_LINK
        const val DIR_SHORT = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closed = false
    @Volatile private var activeFeatures = 0
    @Volatile private var voiceWait: CompletableDeferred<Int>? = null

    private var imeCounter = 0
    private var fieldCounter = 0
    private var hasImeCounter = false
    private var hasFieldCounter = false

    fun connect() {
        scope.launch {
            val reason = try {
                session()
                "Connection closed"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                describe(e)
            }
            finish(reason)
        }
    }

    private suspend fun session() = coroutineScope {
        val s = Tls.connect(ctx, host, 6466)
        socket = s
        s.soTimeout = 25_000
        val out = s.outputStream
        val inp = s.inputStream

        val writer = launch {
            try {
                for (m in outbox) out.writeFrame(m)
            } catch (_: Exception) {
                try {
                    s.close()
                } catch (_: Exception) {
                }
            }
        }
        try {
            while (!closed) {
                handle(inp.readFrame())
            }
        } finally {
            writer.cancel()
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun finish(reason: String) {
        if (closed) return
        closed = true
        listener.onClosed(this, reason)
        outbox.close()
        scope.cancel()
    }

    fun close() {
        if (closed) return
        closed = true
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        outbox.close()
        scope.cancel()
    }

    private fun describe(e: Exception): String = when (e) {
        is SocketTimeoutException -> "The TV didn't respond"
        is ConnectException -> "Can't reach the TV (is it on, on the same Wi-Fi?)"
        is EOFException -> "The TV closed the connection"
        is SSLException -> "The TV refused the secure connection (it may need pairing again)"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun post(bytes: ByteArray) {
        if (!closed) outbox.trySend(bytes)
    }

    // ---- incoming ----------------------------------------------------------------------

    private fun handle(raw: ByteArray) {
        val f = Pb.parse(raw)

        f.sub(1)?.let { cfg ->
            val supported = cfg.int(1)
            activeFeatures = supported and OUR_FEATURES
            post(PbWriter.build {
                msg(1) {
                    int(1, activeFeatures)
                    msg(2) {
                        int(3, 1)
                        str(4, "1")
                        str(5, "atvremote")
                        str(6, "1.0.0")
                    }
                }
            })
        }
        if (f.has(2)) {
            post(PbWriter.build { msg(2) { int(1, activeFeatures) } })
        }
        f.sub(8)?.let { ping ->
            val v = ping.int(1)
            post(PbWriter.build { msg(9) { int(1, v) } })
        }
        f.sub(20)?.let { inject ->
            val counter = inject.sub(1)?.int(1) ?: 0
            val field = inject.sub(2)?.int(1) ?: 0
            imeCounter = counter
            fieldCounter = field
            hasImeCounter = counter != 0
            hasFieldCounter = field != 0
        }
        f.sub(22)?.let { show ->
            val field = show.sub(2)?.int(1) ?: 0
            if (field != 0) {
                fieldCounter = field
                hasFieldCounter = true
            }
        }
        f.sub(21)?.let { batch ->
            if (!hasImeCounter) imeCounter = batch.int(1)
            if (!hasFieldCounter) fieldCounter = batch.int(2)
        }
        f.sub(30)?.let { voice -> voiceWait?.complete(voice.int(1)) }
        f.sub(40)?.let { start ->
            val on = start.int(1) != 0
            listener.onReady(this)
            listener.onPower(this, on)
        }
        f.sub(50)?.let { vol ->
            listener.onVolume(this, vol.int(7), vol.int(6), vol.int(8) != 0)
        }
    }

    // ---- outgoing ----------------------------------------------------------------------

    fun key(code: Int, direction: Int = DIR_SHORT) {
        post(PbWriter.build { msg(10) { int(1, code); int(2, direction) } })
    }

    fun text(t: String) {
        if (t.isEmpty()) return
        post(PbWriter.build {
            msg(21) {
                int(1, imeCounter)
                int(2, fieldCounter)
                msg(3) {
                    int(1, 1)
                    msg(2) {
                        int(1, t.length)
                        int(2, t.length)
                        str(3, t)
                    }
                }
            }
        })
    }

    fun launch(appIdOrLink: String) {
        val link = if (appIdOrLink.contains("://")) appIdOrLink else "market://launch?id=$appIdOrLink"
        post(PbWriter.build { msg(90) { str(1, link) } })
    }

    /** Asks the TV to start listening (like pressing the Assistant button). Returns a session id. */
    suspend fun beginVoice(): Int? {
        if ((activeFeatures and F_VOICE) == 0) return null
        val d = CompletableDeferred<Int>()
        voiceWait = d
        key(Key.SEARCH)
        val id = withTimeoutOrNull(2500) { d.await() }
        voiceWait = null
        if (id != null) post(PbWriter.build { msg(30) { int(1, id) } })
        return id
    }

    fun voiceChunk(session: Int, pcm: ByteArray) {
        post(PbWriter.build { msg(31) { int(1, session); bytes(2, pcm) } })
    }

    fun endVoice(session: Int) {
        post(PbWriter.build { msg(32) { int(1, session) } })
    }
}
