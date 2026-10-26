package app.pauta.audio

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Grava PCM16 mono em um arquivo WAV (little-endian).
 * O cabeçalho é gravado como placeholder e corrigido em [close].
 */
class WavWriter(
    val file: File,
    private val sampleRate: Int = 16_000,
) : Closeable {

    private val output = FileOutputStream(file)
    private var framesWritten = 0L
    private var closed = false

    init {
        output.write(ByteArray(HEADER_SIZE))
    }

    /** Acrescenta amostras PCM16 (mono). */
    fun writePcm(samples: ShortArray) {
        check(!closed) { "WavWriter já fechado" }
        val bytes = ByteArray(samples.size * 2)
        var i = 0
        for (sample in samples) {
            val v = sample.toInt()
            bytes[i++] = (v and 0xFF).toByte()
            bytes[i++] = ((v shr 8) and 0xFF).toByte()
        }
        output.write(bytes)
        framesWritten += samples.size
    }

    override fun close() {
        if (closed) return
        closed = true
        output.flush()
        output.close()
        patchHeader()
    }

    private fun patchHeader() {
        val dataSize = (framesWritten * 2).toInt()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLE(raf, 36 + dataSize)
            raf.write("WAVE".toByteArray(Charsets.US_ASCII))
            raf.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLE(raf, 16)             // tamanho do chunk fmt
            writeShortLE(raf, 1)           // formato PCM
            writeShortLE(raf, 1)           // mono
            writeIntLE(raf, sampleRate)
            writeIntLE(raf, sampleRate * 2) // byte rate
            writeShortLE(raf, 2)           // block align
            writeShortLE(raf, 16)          // bits por amostra
            raf.write("data".toByteArray(Charsets.US_ASCII))
            writeIntLE(raf, dataSize)
        }
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value ushr 8) and 0xFF)
        raf.write((value ushr 16) and 0xFF)
        raf.write((value ushr 24) and 0xFF)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value ushr 8) and 0xFF)
    }

    companion object {
        const val HEADER_SIZE = 44
    }
}
