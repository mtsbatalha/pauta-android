package app.pauta.asr

import app.pauta.model.ModelManifest
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Resultado da decodificação de um segmento de fala. */
data class DecodeResult(
    val text: String,
    val audioSeconds: Double,
    val decodeSeconds: Double,
)

/**
 * Empacota o reconhecedor offline (Parakeet TDT 0.6B v3 int8, via sherpa-onnx)
 * e o detector de atividade de voz (Silero VAD).
 *
 * Todas as chamadas de decodificação acontecem em [dispatcher] — uma única
 * thread dedicada — porque o runtime nativo não é thread-safe. As chamadas do
 * VAD são feitas pela coroutine de captura (ver [acceptWaveform]).
 */
class AsrEngine(
    private val filesDir: File,
    private val numThreads: Int = min(4, Runtime.getRuntime().availableProcessors()),
) : Closeable {

    companion object {
        const val SAMPLE_RATE = 16_000
    }

    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "asr-native").apply { priority = Thread.MAX_PRIORITY }
        }.asCoroutineDispatcher()

    private val loaded = AtomicBoolean(false)
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null

    val isLoaded: Boolean get() = loaded.get()

    /**
     * Carrega reconhecedor + VAD na memória (pode levar alguns segundos e
     * ~1 GB de RAM). Idempotente.
     */
    suspend fun load() {
        withContext(dispatcher) {
            if (loaded.get()) return@withContext
            check(ModelManifest.isInstalled(filesDir)) { "Modelos não instalados" }

            val asrDir = ModelManifest.asrDir(filesDir)
            val vadFile = ModelManifest.vadFile(filesDir)

            recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        transducer = OfflineTransducerModelConfig(
                            encoder = File(asrDir, "encoder.int8.onnx").absolutePath,
                            decoder = File(asrDir, "decoder.int8.onnx").absolutePath,
                            joiner = File(asrDir, "joiner.int8.onnx").absolutePath,
                        ),
                        tokens = File(asrDir, "tokens.txt").absolutePath,
                        numThreads = numThreads,
                        modelType = "nemo_transducer",
                        provider = "cpu",
                    ),
                    decodingMethod = "greedy_search",
                ),
            )

            vad = Vad(
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = vadFile.absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.5f,
                        minSpeechDuration = 0.25f,
                        windowSize = 512,
                        maxSpeechDuration = 20.0f,
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                    provider = "cpu",
                ),
            )
            loaded.set(true)
        }
    }

    /** Decodifica um segmento de fala (amostras float normalizadas em -1..1). */
    suspend fun transcribe(samples: FloatArray): DecodeResult = withContext(dispatcher) {
        val rec = checkNotNull(recognizer) { "Engine não carregada" }
        val audioSeconds = samples.size / SAMPLE_RATE.toDouble()
        val start = System.nanoTime()

        val stream = rec.createStream()
        val text: String
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            rec.decode(stream)
            text = rec.getResult(stream).text
        } finally {
            stream.release()
        }
        DecodeResult(
            text = text.trim(),
            audioSeconds = audioSeconds,
            decodeSeconds = (System.nanoTime() - start) / 1e9,
        )
    }

    // ---- VAD: chamado exclusivamente pela coroutine de captura ----

    /** Alimenta o VAD com um bloco de áudio capturado. */
    fun acceptWaveform(samples: FloatArray) {
        checkNotNull(vad) { "Engine não carregada" }.acceptWaveform(samples)
    }

    /** Há segmentos de fala prontos para decodificação? */
    fun hasSegments(): Boolean = checkNotNull(vad) { "Engine não carregada" }.empty().not()

    /** Retira o próximo segmento de fala do VAD. */
    fun nextSegment(): SpeechSegment {
        val v = checkNotNull(vad) { "Engine não carregada" }
        val segment = v.front()
        v.pop()
        return segment
    }

    /** No fim da gravação: devolve o segmento parcial em andamento. */
    fun flushVad() {
        vad?.flush()
    }

    /** Zera o estado do VAD antes de uma nova gravação. */
    fun resetVad() {
        vad?.reset()
        vad?.clear()
    }

    override fun close() {
        loaded.set(false)
        runCatching { recognizer?.release() }
        runCatching { vad?.release() }
        recognizer = null
        vad = null
        dispatcher.close()
    }
}
