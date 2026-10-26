package app.pauta.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** Um bloco de áudio capturado (PCM16 mono) e seu nível RMS (0..1). */
class AudioChunk(val samples: ShortArray, val rms: Float)

/**
 * Captura do microfone como Flow de blocos de 100 ms (16 kHz, mono, PCM16).
 * A permissão RECORD_AUDIO deve ter sido concedida antes de coletar o flow.
 */
class AudioRecorder(private val sampleRate: Int = 16_000) {

    fun record(): Flow<AudioChunk> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            close(IllegalStateException("Microfone indisponível neste aparelho"))
            return@callbackFlow
        }

        @SuppressLint("MissingPermission") // conferida pelo chamador antes de coletar
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 4, sampleRate * 2), // folga para decodificação em paralelo
            )
        } catch (e: SecurityException) {
            close(IllegalStateException("Sem permissão de microfone"))
            return@callbackFlow
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(IllegalStateException("Falha ao inicializar o microfone"))
            return@callbackFlow
        }

        record.startRecording()

        val reader: Job = launch(Dispatchers.IO) {
            val buffer = ShortArray(sampleRate / 10) // 100 ms
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                when {
                    read < 0 -> {
                        close(IllegalStateException("Erro de leitura do microfone (código $read)"))
                        return@launch
                    }
                    read > 0 -> trySend(AudioChunk(buffer.copyOf(read), rms(buffer, read)))
                }
            }
        }

        awaitClose {
            reader.cancel()
            runCatching { record.stop() }
            record.release()
        }
    }

    private fun rms(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) {
            val v = samples[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / count).toFloat()
    }
}
