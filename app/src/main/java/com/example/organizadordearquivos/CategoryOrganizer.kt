package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

data class CategoryOrganizationResult(val movedFiles: Int) // Simplificado para retornar apenas arquivos

class CategoryOrganizer(private val context: Context) {

    suspend fun organize(
        uri: Uri,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Int) -> Unit
    ): CategoryOrganizationResult = withContext(Dispatchers.IO) {

        onStatusUpdate("\n--- Iniciando Organização por Categoria ---")

        val root = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Pasta não acessível.")

        // A lógica agora foca apenas em arquivos, como no diagrama
        val filesToMove = root.listFiles().filter { it.isFile && !it.name.orEmpty().startsWith('.') }

        if (filesToMove.isEmpty()) {
            onStatusUpdate("Nenhum arquivo encontrado para organizar.")
            return@withContext CategoryOrganizationResult(0)
        }

        // MUDANÇA 1: O nome da pasta raiz agora é "Organizados por Categoria"
        val mainOutputFolder = findOrCreateDirectory(root, "Organizados por Categoria")!!

        var movedFilesCount = 0

        filesToMove.forEachIndexed { index, file ->
            val extension = file.getExtension()
            val categoryName = FileConfig.FILE_CATEGORIES.getOrDefault(extension, "Diversos")

            // Nível 1: Pasta da Categoria (ex: Videos)
            val categoryFolder = findOrCreateDirectory(mainOutputFolder, categoryName)!!

            // Nível 2: Subpasta da Extensão (ex: Arquivos.MP4)
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

    // --- Funções auxiliares privadas (não precisam de alteração) ---

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
    }

    private fun DocumentFile.getExtension(): String {
        val fileName = this.name ?: return ""
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) {
            fileName.substring(dotIndex).lowercase(Locale.ROOT)
        } else {
            ""
        }
    }

    private fun moveFile(fileToMove: DocumentFile, destinationDir: DocumentFile, finalFileName: String, onStatusUpdate: (String) -> Unit): DocumentFile? {
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