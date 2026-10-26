package app.pauta.model

import java.io.File
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TarBz2ExtractorTest {

    @Test
    fun `extrai apenas as entradas desejadas e ignora o resto`() {
        val workDir = createWorkDir("extract-ok")
        try {
            val encoder = ByteArray(50_000) { (it % 253).toByte() }
            val decoder = ByteArray(9_000) { (it % 17).toByte() }
            val tokens = "a\nb\n".toByteArray(Charsets.UTF_8)

            val tarball = File(workDir, "modelo.tar.bz2")
            buildTarBz2(tarball) {
                put("sherpa-onnx-nemo-x/encoder.int8.onnx", encoder)
                put("sherpa-onnx-nemo-x/tokens.txt", tokens)
                put("sherpa-onnx-nemo-x/test_wavs/pt.wav", ByteArray(64))
                put("sherpa-onnx-nemo-x/decoder.int8.onnx", decoder)
            }

            val outDir = File(workDir, "out")
            val files = TarBz2Extractor.extract(
                tarball,
                outDir,
                listOf("encoder.int8.onnx", "decoder.int8.onnx"),
            )

            assertEquals(2, files.size)
            assertArrayEquals(encoder, File(outDir, "encoder.int8.onnx").readBytes())
            assertArrayEquals(decoder, File(outDir, "decoder.int8.onnx").readBytes())

            // Entradas não pedidas não são extraídas.
            assertFalse(File(outDir, "tokens.txt").exists())
            assertFalse(File(outDir, "pt.wav").exists())
            assertFalse(File(outDir, "test_wavs").exists())
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `falta de entrada desejada lanca IOException`() {
        val workDir = createWorkDir("extract-missing")
        try {
            val tarball = File(workDir, "modelo.tar.bz2")
            buildTarBz2(tarball) {
                put("dir/tokens.txt", "x".toByteArray())
            }

            val outDir = File(workDir, "out")
            val error = runCatching {
                TarBz2Extractor.extract(tarball, outDir, listOf("encoder.int8.onnx"))
            }.exceptionOrNull()

            assertTrue(error is java.io.IOException)
            assertTrue(error!!.message!!.contains("encoder.int8.onnx"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    // ---- Auxiliares ----

    private fun createWorkDir(prefix: String): File =
        File.createTempFile(prefix, "").let { tmp ->
            tmp.delete()
            File(tmp.parentFile, "${prefix}-${System.nanoTime()}").apply { mkdirs() }
        }

    private fun buildTarBz2(target: File, body: TarArchiveOutputStream.() -> Unit) {
        TarArchiveOutputStream(BZip2CompressorOutputStream(FileOutputStream(target))).use { tar ->
            tar.body()
            tar.finish()
        }
    }

    private fun TarArchiveOutputStream.put(name: String, content: ByteArray) {
        val entry = TarArchiveEntry(name)
        entry.size = content.size.toLong()
        putArchiveEntry(entry)
        write(content)
        closeArchiveEntry()
    }
}
