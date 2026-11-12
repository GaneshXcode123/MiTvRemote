package com.mitvremote.app.tv

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Streams the phone microphone (16-bit PCM, 8 kHz, mono) to the TV's voice assistant. */
class VoiceStreamer(private val client: RemoteClient) {
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onEnded: (error: String?) -> Unit) {
        job = scope.launch(Dispatchers.IO) {
            var error: String? = null
            try {
                val session = client.beginVoice()
                if (session == null) {
                    error = "This TV didn't start voice listening"
                    return@launch
                }
                val rate = 8000
                val chunk = 8192
                val minBuf = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, chunk * 2),
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release()
                    client.endVoice(session)
                    error = "Couldn't open the microphone"
                    return@launch
                }
                try {
                    rec.startRecording()
                    val buf = ByteArray(chunk)
                    while (isActive) {
                        var n = 0
                        while (n < chunk && isActive) {
                            val r = rec.read(buf, n, chunk - n)
                            if (r <= 0) break
                            n += r
                        }
                        if (n <= 0) break
                        client.voiceChunk(session, buf.copyOf(n))
                    }
                } finally {
                    try {
                        rec.stop()
                    } catch (_: Exception) {
                    }
                    rec.release()
                    client.endVoice(session)
                }
            } catch (e: Exception) {
                error = e.message ?: "Voice failed"
            } finally {
                onEnded(error)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
