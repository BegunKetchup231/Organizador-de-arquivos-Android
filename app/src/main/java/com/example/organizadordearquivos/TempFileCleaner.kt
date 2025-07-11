package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

// Data class para retornar um resultado claro e organizado
data class CleanResult(val filesRemoved: Int, val spaceFreed: Long)

class TempFileCleaner(private val context: Context) {

    suspend fun clean(
        uri: Uri,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Int) -> Unit
    ): CleanResult = withContext(Dispatchers.IO) {

        onStatusUpdate("\n--- Iniciando Limpeza de Arquivos ---")

        val rootDir = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("Pasta não acessível.")

        val filesToDelete = mutableListOf<DocumentFile>()
        val stack = ArrayDeque<DocumentFile>().apply { add(rootDir) }

        // Mapeia todos os arquivos a serem deletados
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            current.listFiles().forEach {
                if (it.isDirectory) {
                    stack.add(it)
                } else if (it.isFile && (it.length() == 0L || it.getExtension() in TEMP_EXTENSIONS)) {
                    filesToDelete.add(it)
                }
            }
        }

        if (filesToDelete.isEmpty()) {
            onStatusUpdate("Nenhum arquivo para limpar.")
            return@withContext CleanResult(0, 0L)
        }

        var totalFreedSpace = 0L
        var removedFilesCount = 0

        // Deleta os arquivos e reporta o progresso
        filesToDelete.forEachIndexed { index, file ->
            val fileSize = file.length()
            if (file.delete()) {
                totalFreedSpace += fileSize
                removedFilesCount++
            } else {
                onStatusUpdate("Falha ao remover: ${file.name}")
            }
            onProgressUpdate(((index + 1) * 100) / filesToDelete.size)
        }

        // Retorna o resultado bruto (número de arquivos e bytes)
        CleanResult(removedFilesCount, totalFreedSpace)
    }

    // Função auxiliar privada, só esta classe precisa dela
    private fun DocumentFile.getExtension(): String {
        val fileName = this.name ?: return ""
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) {
            fileName.substring(dotIndex).lowercase(Locale.ROOT)
        } else {
            ""
        }
    }

    // Constante privada, só esta classe precisa dela
    companion object {
        private val TEMP_EXTENSIONS = listOf(".tmp", ".bak", ".~tmp", ".~bak", ".temp", ".~lock")
    }
}