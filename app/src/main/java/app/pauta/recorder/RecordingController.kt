package app.pauta.recorder

import app.pauta.asr.AsrEngine
import app.pauta.audio.AudioRecorder
import app.pauta.audio.WavWriter
import app.pauta.benchmark.RtfTracker
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estatísticas de desempenho da gravação (benchmark da Fase 0). */
data class RecordingStats(
    val segments: Int,
    val audioSeconds: Double,
    val decodeSeconds: Double,
    val averageRtf: Double,
)

/** Resultado final de uma gravação. */
data class RecordingResult(
    val text: String,
    val stats: RecordingStats,
    val wavFile: File?,
)

/**
 * Coordena captura de áudio, VAD, decodificação e arquivo WAV.
 *
 * Duas coroutines: a de captura alimenta o VAD e o WAV; a de decodificação
 * consome segmentos do canal e roda o ASR (thread nativa dedicada). Assim a
 * captura nunca é bloqueada pela decodificação.
 */
class RecordingController(
    private val engine: AsrEngine,
    private val recordingsDir: File,
) {
    private val recorder = AudioRecorder()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _segmentText = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val segmentText: SharedFlow<String> = _segmentText.asSharedFlow()

    /** Falhas de captura/decodificação (a gravação é interrompida). */
    private val _failure = MutableSharedFlow<Throwable>(extraBufferCapacity = 4)
    val failure: SharedFlow<Throwable> = _failure.asSharedFlow()

    private val tracker = RtfTracker()
    private val texts = mutableListOf<String>()

    private var captureJob: Job? = null
    private var decodeJob: Job? = null
    private var segmentChannel: Channel<FloatArray>? = null
    private var wavWriter: WavWriter? = null

    var wavFile: File? = null
        private set

    val isRunning: Boolean get() = captureJob?.isActive == true

    /** Inicia a gravação. [scope] deve sobreviver à tela (ex.: viewModelScope). */
    fun start(scope: CoroutineScope) {
        check(!isRunning) { "Já existe uma gravação em andamento" }

        tracker.reset()
        texts.clear()
        engine.resetVad()

        recordingsDir.mkdirs()
        val file = File(recordingsDir, "gravacao-${System.currentTimeMillis()}.wav")
        wavFile = file
        val writer = WavWriter(file)
        wavWriter = writer

        val channel = Channel<FloatArray>(Channel.UNLIMITED)
        segmentChannel = channel

        captureJob = scope.launch(Dispatchers.Default) {
            var failure: Throwable? = null
            try {
                recorder.record().collect { chunk ->
                    writer.writePcm(chunk.samples)
                    _level.value = chunk.rms
                    engine.acceptWaveform(toFloats(chunk.samples))
                    while (engine.hasSegments()) {
                        channel.send(engine.nextSegment().samples)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                failure = t
            } finally {
                withContext(NonCancellable) {
                    runCatching { engine.flushVad() }
                    while (engine.hasSegments()) {
                        channel.trySend(engine.nextSegment().samples)
                    }
                    channel.close()
                }
                failure?.let { _failure.tryEmit(it) }
            }
        }

        decodeJob = scope.launch(Dispatchers.Default) {
            try {
                for (samples in channel) {
                    val result = engine.transcribe(samples)
                    tracker.record(result.audioSeconds, result.decodeSeconds)
                    if (result.text.isNotEmpty()) {
                        texts += result.text
                        _segmentText.emit(result.text)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _failure.tryEmit(t)
                captureJob?.cancel()
            }
        }
    }

    /** Encerra a gravação e devolve o resultado acumulado. */
    suspend fun stop(): RecordingResult {
        val capture = captureJob
        val decode = decodeJob
        if (capture == null || decode == null) {
            return emptyResult()
        }

        capture.cancel() // o finally faz o flush do VAD e fecha o canal
        capture.join()
        decode.join()

        val writer = wavWriter
        runCatching { writer?.close() }

        val file = wavFile
        return RecordingResult(
            text = texts.joinToString(" ").trim(),
            stats = RecordingStats(
                segments = tracker.segmentsCount,
                audioSeconds = tracker.totalAudioSeconds,
                decodeSeconds = tracker.totalDecodeSeconds,
                averageRtf = tracker.averageRtf,
            ),
            wavFile = if (file != null && file.length() > WavWriter.HEADER_SIZE) file else null,
        )
    }

    private fun emptyResult(): RecordingResult =
        RecordingResult("", RecordingStats(0, 0.0, 0.0, 0.0), null)

    private fun toFloats(samples: ShortArray): FloatArray {
        val floats = FloatArray(samples.size)
        for (i in samples.indices) {
            floats[i] = samples[i] / 32768f
        }
        return floats
    }
}
