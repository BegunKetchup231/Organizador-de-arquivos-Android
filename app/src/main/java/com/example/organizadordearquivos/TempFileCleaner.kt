package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

// A data class agora será usada para os dois resultados (análise e limpeza)
data class CleanResult(val filesFound: Int, val spaceToFree: Long)

class TempFileCleaner(private val context: Context) {

    // NOVA FUNÇÃO DE ANÁLISE
    suspend fun analyze(uri: Uri): CleanResult = withContext(Dispatchers.IO) {
        val rootDir = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("Pasta não acessível.")

        var filesFound = 0
        var spaceToFree = 0L
        val stack = ArrayDeque<DocumentFile>().apply { add(rootDir) }

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            current.listFiles().forEach {
                if (it.isDirectory) {
                    stack.add(it)
                } else if (it.isFile && (it.length() == 0L || it.getExtension() in FileConfig.TEMP_EXTENSIONS)) {
                    filesFound++
                    spaceToFree += it.length()
                }
            }
        }
        // Retorna o resultado da análise
        CleanResult(filesFound, spaceToFree)
    }

    // Função de limpeza (agora usa a mesma data class)
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

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            current.listFiles().forEach {
                if (it.isDirectory) {
                    stack.add(it)
                } else if (it.isFile && (it.length() == 0L || it.getExtension() in FileConfig.TEMP_EXTENSIONS)) {
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

        CleanResult(removedFilesCount, totalFreedSpace)
    }

    private fun DocumentFile.getExtension(): String {
        val fileName = this.name ?: return ""
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) {
            fileName.substring(dotIndex).lowercase(Locale.ROOT)
        } else { "" }
    }
}