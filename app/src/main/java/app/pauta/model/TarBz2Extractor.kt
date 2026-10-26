package app.pauta.model

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Extrai, de um tarball .tar.bz2, apenas as entradas cujo nome de arquivo
 * (último componente do caminho) está na lista de nomes desejados.
 *
 * O arquivo de saída é sempre gravado com o nome simples escolhido por nós
 * dentro do diretório destino — nunca com o caminho vindo do tarball — o que
 * elimina qualquer risco de path traversal.
 */
object TarBz2Extractor {

    /**
     * @param tarBz2 tarball a extrair.
     * @param targetDir diretório onde gravar os arquivos (criado se necessário).
     * @param wantedNames nomes de arquivo desejados (ex.: "encoder.int8.onnx").
     * @return arquivos criados, na ordem de [wantedNames].
     * @throws IOException se alguma entrada desejada não existir no tarball.
     */
    fun extract(tarBz2: File, targetDir: File, wantedNames: Collection<String>): List<File> {
        require(wantedNames.isNotEmpty()) { "wantedNames não pode ser vazio" }
        if (!targetDir.isDirectory && !targetDir.mkdirs()) {
            throw IOException("Não foi possível criar o diretório ${targetDir.absolutePath}")
        }

        val wanted = wantedNames.toSet()
        val extracted = HashMap<String, File>(wanted.size)

        FileInputStream(tarBz2).use { fileInput ->
            TarArchiveInputStream(BZip2CompressorInputStream(fileInput, true)).use { tar ->
                var entry: TarArchiveEntry? = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val simpleName = entry.name.substringAfterLast('/')
                        if (simpleName in wanted) {
                            val outFile = File(targetDir, simpleName)
                            FileOutputStream(outFile).use { out ->
                                copyCurrentEntry(tar, out)
                            }
                            extracted[simpleName] = outFile
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }

        val missing = wantedNames.filterNot { extracted.containsKey(it) }
        if (missing.isNotEmpty()) {
            throw IOException("Entradas ausentes no tarball: ${missing.joinToString()}")
        }
        return wantedNames.map { requireNotNull(extracted[it]) }
    }

    private fun copyCurrentEntry(tar: TarArchiveInputStream, out: FileOutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = tar.read(buffer)
            if (read < 0) break
            if (read > 0) out.write(buffer, 0, read)
        }
    }
}
