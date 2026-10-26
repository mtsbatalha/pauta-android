package app.pauta.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavWriterTest {

    @Test
    fun `cabecalho e payload do wav estao corretos`() {
        val file = File.createTempFile("gravacao", ".wav")
        try {
            val samples = ShortArray(1_600) { (it % 251).toShort() }
            val writer = WavWriter(file, sampleRate = 16_000)
            writer.writePcm(samples)
            writer.close()

            val bytes = file.readBytes()
            val dataSize = samples.size * 2

            assertEquals(WavWriter.HEADER_SIZE + dataSize, bytes.size)

            // Marcadores RIFF/WAVE
            assertEquals("RIFF", ascii(bytes, 0, 4))
            assertEquals("WAVE", ascii(bytes, 8, 4))
            assertEquals("fmt ", ascii(bytes, 12, 4))
            assertEquals("data", ascii(bytes, 36, 4))

            // Campos do cabeçalho (little-endian)
            assertEquals(36 + dataSize, readIntLE(bytes, 4))
            assertEquals(16, readIntLE(bytes, 16))       // tamanho do chunk fmt
            assertEquals(1, readShortLE(bytes, 20))      // PCM
            assertEquals(1, readShortLE(bytes, 22))      // mono
            assertEquals(16_000, readIntLE(bytes, 24))   // sample rate
            assertEquals(32_000, readIntLE(bytes, 28))    // byte rate
            assertEquals(2, readShortLE(bytes, 32))       // block align
            assertEquals(16, readShortLE(bytes, 34))      // bits por amostra
            assertEquals(dataSize, readIntLE(bytes, 40))

            // Payload little-endian
            for (i in samples.indices) {
                val lo = bytes[WavWriter.HEADER_SIZE + 2 * i].toInt() and 0xFF
                val hi = bytes[WavWriter.HEADER_SIZE + 2 * i + 1].toInt() and 0xFF
                val decoded = (hi shl 8) or lo
                assertEquals(samples[i].toInt(), if (decoded >= 0x8000) decoded - 0x10000 else decoded)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `escrever apos fechar lanca excecao`() {
        val file = File.createTempFile("gravacao", ".wav")
        try {
            val writer = WavWriter(file)
            writer.writePcm(shortArrayOf(1, 2, 3))
            writer.close()
            val error = runCatching { writer.writePcm(shortArrayOf(4)) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
        } finally {
            file.delete()
        }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
