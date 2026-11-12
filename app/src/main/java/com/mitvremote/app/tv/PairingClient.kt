package com.mitvremote.app.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class BadPairingCode(message: String) : Exception(message)

/**
 * Android TV pairing ("polo") protocol on port 6467.
 * 1) start(): TV shows a 6 character code.   2) finish(code): proves we know it.
 */
class PairingClient(private val ctx: Context, private val host: String) {
    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private fun outer(block: PbWriter.() -> Unit): ByteArray = PbWriter.build {
        int(1, 2)       // protocol_version
        int(2, 200)     // status OK
        block()
    }

    private fun send(bytes: ByteArray) {
        val o = output ?: throw IOException("Not connected")
        o.writeFrame(bytes)
    }

    private fun receive(): List<PbField> {
        val i = input ?: throw IOException("Not connected")
        val f = Pb.parse(i.readFrame())
        val status = f.int(2, 200)
        if (status != 200) throw IOException("The TV refused pairing (status $status)")
        return f
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        val s = Tls.connect(ctx, host, 6467)
        socket = s
        input = s.inputStream
        output = s.outputStream
        s.soTimeout = 10_000

        send(outer {
            msg(10) {
                str(1, "atvremote")
                str(2, "Mi TV Remote")
            }
        })

        var done = false
        while (!done) {
            val f = receive()
            when {
                f.has(11) -> send(outer {            // pairing_request_ack -> options
                    msg(20) {
                        msg(1) {
                            int(1, 3)                // hexadecimal
                            int(2, 6)                // 6 symbols
                        }
                        int(3, 1)                    // role: input
                    }
                })
                f.has(20) -> send(outer {            // options -> configuration
                    msg(30) {
                        msg(1) {
                            int(1, 3)
                            int(2, 6)
                        }
                        int(2, 1)
                    }
                })
                f.has(31) -> done = true             // configuration_ack: TV now shows the code
                else -> throw IOException("Unexpected reply from the TV")
            }
        }
    }

    suspend fun finish(code: String) = withContext(Dispatchers.IO) {
        val c = code.trim().uppercase()
        if (!Regex("[0-9A-F]{6}").matches(c)) {
            throw BadPairingCode("The code has 6 characters: digits 0-9 and letters A-F.")
        }
        val s = socket ?: throw IOException("Not connected")
        val clientKey = Tls.clientPublicKey(ctx)
        val serverKey = s.session.peerCertificates[0].publicKey as? RSAPublicKey
            ?: throw IOException("The TV uses an unsupported certificate type")

        val md = MessageDigest.getInstance("SHA-256")
        md.update(unsigned(clientKey.modulus))
        md.update(unsigned(clientKey.publicExponent))
        md.update(unsigned(serverKey.modulus))
        md.update(unsigned(serverKey.publicExponent))
        md.update(hexToBytes(c.substring(2)))
        val hash = md.digest()

        if ((hash[0].toInt() and 0xFF) != c.substring(0, 2).toInt(16)) {
            throw BadPairingCode("That code doesn't match. Check the TV screen and try again.")
        }

        send(outer { msg(40) { bytes(1, hash) } })

        var done = false
        while (!done) {
            val f = receive()
            if (f.has(41)) done = true else throw IOException("The TV rejected the code")
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun unsigned(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return if (b.size > 1 && b[0].toInt() == 0) b.copyOfRange(1, b.size) else b
    }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
