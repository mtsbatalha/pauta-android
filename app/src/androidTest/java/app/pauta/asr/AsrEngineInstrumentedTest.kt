package app.pauta.asr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pauta.model.ModelManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste instrumentado de fumaça da engine.
 *
 * Requer os modelos instalados no aparelho (primeira execução do app com
 * download concluído). Se não estiverem instalados, o teste é pulado.
 *
 * Execução:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AsrEngineInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun carregaModelosEDecodificaSilencio() = runBlocking {
        assumeTrue(
            "Modelos não instalados no aparelho",
            ModelManifest.isInstalled(context.filesDir),
        )

        val engine = AsrEngine(context.filesDir)
        try {
            engine.load()
            assertTrue(engine.isLoaded)

            // 1 segundo de silêncio: deve decodificar sem erro (texto vazio).
            val result = engine.transcribe(FloatArray(AsrEngine.SAMPLE_RATE))
            assertTrue(result.decodeSeconds >= 0.0)
        } finally {
            engine.close()
        }
    }
}
