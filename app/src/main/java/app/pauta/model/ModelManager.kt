package app.pauta.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Passos da instalação dos modelos (exibidos na tela de preparo). */
sealed interface SetupStep {
    data object Idle : SetupStep
    data class Downloading(val label: String, val fraction: Float) : SetupStep
    data object Extracting : SetupStep
    data object Done : SetupStep
    data class Error(val message: String) : SetupStep
}

/**
 * Baixa, verifica (SHA-256) e instala os modelos locais.
 *
 * Única parte do app que usa a rede — e apenas quando o usuário pede.
 * Depois de instalados, o app funciona 100% offline.
 */
class ModelManager(private val filesDir: File) {

    fun isReady(): Boolean = ModelManifest.isInstalled(filesDir)

    private fun freeBytes(): Long = filesDir.usableSpace

    fun downloadAndInstall(): Flow<SetupStep> = flow {
        if (isReady()) {
            emit(SetupStep.Done)
            return@flow
        }
        if (freeBytes() < ModelManifest.MIN_FREE_BYTES_DURING_INSTALL) {
            emit(SetupStep.Error(EspaçoInsuficienteMensagem))
            return@flow
        }

        val modelsDir = File(filesDir, "models").apply { mkdirs() }

        try {
            // 1. VAD (pequeno, ~0,6 MB)
            emit(SetupStep.Downloading(ModelManifest.VAD_FILE, 0f))
            val vadTmp = File(modelsDir, "vad-download.tmp")
            downloadFile(
                url = ModelManifest.VAD_URL,
                expectedSha256 = ModelManifest.VAD_SHA256,
                dest = vadTmp,
            )
            move(vadTmp, ModelManifest.vadFile(filesDir))

            // 2. Tarball do ASR (~487 MB)
            val tarball = File(modelsDir, "asr-tarball.tar.bz2")
            downloadFile(
                url = ModelManifest.ASR_TARBALL_URL,
                expectedSha256 = ModelManifest.ASR_TARBALL_SHA256,
                dest = tarball,
            ) { read, total ->
                emit(SetupStep.Downloading(ASR_LABEL, fraction(read, total)))
            }

            // 3. Extração (1–3 min em aparelhos comuns)
            emit(SetupStep.Extracting)
            val asrDir = ModelManifest.asrDir(filesDir)
            TarBz2Extractor.extract(tarball, asrDir, ModelManifest.ASR_FILES)
            if (!isReady()) {
                throw IOException("Extração incompleta do modelo")
            }
            tarball.delete()

            emit(SetupStep.Done)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(SetupStep.Error(e.message ?: "Erro desconhecido ao instalar os modelos"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFile(
        url: String,
        expectedSha256: String,
        dest: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code ao baixar ${dest.name}")

            val total = connection.contentLengthLong // -1 se desconhecido
            val digest = MessageDigest.getInstance("SHA-256")
            val part = File(dest.parentFile, dest.name + ".part")

            connection.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    var lastReport = -1L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalRead += read
                        if (totalRead - lastReport >= PROGRESS_STEP_BYTES || (total > 0 && totalRead == total)) {
                            lastReport = totalRead
                            onProgress(totalRead, total)
                        }
                    }
                }
            }

            val actualSha256 = digest.digest().toHexString()
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                part.delete()
                throw IOException("Checksum SHA-256 divergente (${dest.name})")
            }
            move(part, dest)
        } finally {
            connection.disconnect()
        }
    }

    private fun move(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (from.renameTo(to)) return
        from.copyTo(to, overwrite = true)
        from.delete()
    }

    private fun fraction(read: Long, total: Long): Float =
        if (total > 0) (read.toDouble() / total).toFloat() else -1f

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val PROGRESS_STEP_BYTES = 4L * 1024 * 1024 // reporta a cada ~4 MB

        private const val ASR_LABEL = "Parakeet TDT 0.6B v3 (int8)"
        private const val EspaçoInsuficienteMensagem =
            "Espaço livre insuficiente: são necessários ~2 GB livres durante a instalação."
    }
}
