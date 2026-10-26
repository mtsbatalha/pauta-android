package app.pauta

import android.app.Application
import app.pauta.asr.AsrEngine
import app.pauta.model.ModelManager
import java.io.File

/**
 * Contêiner de dependências da Fase 0 (injeção manual, sem framework).
 * A engine vive no nível da Application para ser reutilizada entre
 * recriações da Activity (rotação etc.).
 */
class PautaApplication : Application() {

    lateinit var modelManager: ModelManager
        private set

    val asrEngine: AsrEngine by lazy { AsrEngine(filesDir) }

    val recordingsDir: File
        get() = File(filesDir, "recordings")

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(filesDir)
    }
}
