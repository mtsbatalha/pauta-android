package app.pauta.model

import java.io.File

/**
 * Fontes oficiais, checksums e artefatos esperados dos modelos locais.
 *
 * O export int8 do Parakeet TDT 0.6B v3 é o mantido pelo projeto sherpa-onnx
 * (k2-fsa). O Silero VAD é distribuído pelo mesmo projeto.
 */
object ModelManifest {

    /** Tarball oficial do Parakeet TDT 0.6B v3 (int8), 25 idiomas europeus. */
    const val ASR_TARBALL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2"

    /** SHA-256 do tarball (calculado sobre o arquivo publicado). */
    const val ASR_TARBALL_SHA256 =
        "5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf"

    const val VAD_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    const val VAD_SHA256 =
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"

    /** Arquivos do tarball que interessam (test_wavs e afins são ignorados). */
    val ASR_FILES = listOf(
        "encoder.int8.onnx",
        "decoder.int8.onnx",
        "joiner.int8.onnx",
        "tokens.txt",
    )

    const val VAD_FILE = "silero_vad.onnx"

    /** Espaço livre mínimo durante a instalação (tarball + extraído), ~2 GiB. */
    const val MIN_FREE_BYTES_DURING_INSTALL = 2_200_000_000L

    fun asrDir(filesDir: File): File = File(filesDir, "models/parakeet-v3")

    fun vadFile(filesDir: File): File = File(File(filesDir, "models/vad"), VAD_FILE)

    fun isInstalled(filesDir: File): Boolean =
        ASR_FILES.all { File(asrDir(filesDir), it).isFile } && vadFile(filesDir).isFile
}
