package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

data class CategoryOrganizationResult(val movedFiles: Int)
data class CategoryAnalysisResult(val fileCountsByCategory: Map<String, Int>)

class CategoryOrganizer(private val context: Context) {

    suspend fun analyze(uri: Uri): CategoryAnalysisResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Pasta não acessível.")
        val filesToAnalyze = root.listFiles().filter { it.isFile && !it.name.orEmpty().startsWith('.') }

        val counts = mutableMapOf<String, Int>()
        filesToAnalyze.forEach { file ->
            val extension = file.getExtension()
            val category = FileConfig.FILE_CATEGORIES.getOrDefault(extension, "Diversos")
            counts[category] = (counts[category] ?: 0) + 1
        }
        CategoryAnalysisResult(counts)
    }

    suspend fun organize(
        uri: Uri,
        onStatusUpdate: suspend (String) -> Unit,
        onProgressUpdate: suspend (Int) -> Unit
    ): CategoryOrganizationResult = withContext(Dispatchers.IO) {
        onStatusUpdate("\n--- Iniciando Organização por Categoria ---")

        val root = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Pasta não acessível.")
        val filesToMove = root.listFiles().filter { it.isFile && !it.name.orEmpty().startsWith('.') }

        if (filesToMove.isEmpty()) {
            onStatusUpdate("Nenhum arquivo encontrado para organizar.")
            return@withContext CategoryOrganizationResult(0)
        }

        val mainOutputFolder = findOrCreateDirectory(root, "Organizados por Categoria")!!
        var movedFilesCount = 0

        filesToMove.forEachIndexed { index, file ->
            val extension = file.getExtension()
            val categoryName = FileConfig.FILE_CATEGORIES.getOrDefault(extension, "Audios")
            val categoryFolder = findOrCreateDirectory(mainOutputFolder, categoryName)!!
            val extensionName = extension.removePrefix(".").uppercase(Locale.ROOT)
            val finalSubFolderName = "Arquivos.$extensionName"
            val finalDestFolder = findOrCreateDirectory(categoryFolder, finalSubFolderName)!!

            var finalFileName = file.name!!
            if (finalDestFolder.findFile(finalFileName) != null) {
                val baseName = finalFileName.substringBeforeLast('.')
                val ext = finalFileName.substringAfterLast('.')
                var suffix = 1
                do {
                    finalFileName = "${baseName}_${suffix++}.$ext"
                } while (finalDestFolder.findFile(finalFileName) != null)
            }

            if (moveFile(file, finalDestFolder, finalFileName, onStatusUpdate) != null) {
                movedFilesCount++
            } else {
                onStatusUpdate("Falha ao mover arquivo: '${file.name}'")
            }
            onProgressUpdate(((index + 1) * 100) / filesToMove.size)
        }
        CategoryOrganizationResult(movedFilesCount)
    }

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
    }

    private fun DocumentFile.getExtension(): String {
        val fileName = this.name ?: return ""
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) {
            fileName.substring(dotIndex).lowercase(Locale.ROOT)
        } else { "" }
    }

    // A MUDANÇA ESTÁ AQUI: onStatusUpdate agora é 'suspend'
    private suspend fun moveFile(fileToMove: DocumentFile, destinationDir: DocumentFile, finalFileName: String, onStatusUpdate: suspend (String) -> Unit): DocumentFile? {
        try {
            val fileWithFinalName = if (fileToMove.name != finalFileName) {
                if (fileToMove.renameTo(finalFileName)) {
                    fileToMove.parentFile?.findFile(finalFileName) ?: throw IOException("Falha ao encontrar arquivo após renomear.")
                } else { throw IOException("Falha ao renomear arquivo de origem.") }
            } else { fileToMove }
            val movedUri = DocumentsContract.moveDocument(context.contentResolver, fileWithFinalName.uri, fileWithFinalName.parentFile!!.uri, destinationDir.uri)
            return movedUri?.let { DocumentFile.fromSingleUri(context, it) }
        } catch (e: Exception) {
            onStatusUpdate("Aviso: Usando método de cópia para '${fileToMove.name}' -> '$finalFileName'.")
            return try {
                val newFile = destinationDir.createFile(fileToMove.type ?: "application/octet-stream", finalFileName)
                if (newFile != null) {
                    context.contentResolver.openInputStream(fileToMove.uri)?.use { input ->
                        context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) }
                    }
                }
                if (fileToMove.delete()) newFile else { newFile?.delete(); null }
            } catch (copyError: Exception) { null }
        }
    }
}