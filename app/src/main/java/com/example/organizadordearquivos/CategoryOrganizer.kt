package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

// Data class para um resultado de retorno claro
data class CategoryOrganizationResult(val movedFolders: Int, val movedFiles: Int)

class CategoryOrganizer(private val context: Context) {

    // A única função pública da nossa classe especialista
    suspend fun organize(
        uri: Uri,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Int) -> Unit
    ): CategoryOrganizationResult = withContext(Dispatchers.IO) {

        onStatusUpdate("\n--- Iniciando Organização por Categoria ---")

        val root = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Pasta não acessível.")
        val items = root.listFiles().toList()
        val foldersToIgnore = setOf("Arquivos", "Pastas_Organizadas", "Organizado_Por_Data")
        val filesToMove = items.filter { it.isFile && !it.name.orEmpty().startsWith('.') }
        val foldersToMove = items.filter { it.isDirectory && it.name !in foldersToIgnore }
        val totalItems = filesToMove.size + foldersToMove.size

        if (totalItems == 0) {
            onStatusUpdate("Nenhum item para organizar.")
            return@withContext CategoryOrganizationResult(0, 0)
        }

        var movedFilesCount = 0
        var movedFoldersCount = 0
        var itemsProcessed = 0

        // Lógica para mover pastas
        if (foldersToMove.isNotEmpty()) {
            val organizedFoldersBase = findOrCreateDirectory(root, "Pastas_Organizadas")!!
            val existingFolderNames = organizedFoldersBase.listFiles().mapNotNull { it.name }.toMutableSet()
            foldersToMove.forEach { folder ->
                var finalFolderName = folder.name!!
                if (existingFolderNames.contains(finalFolderName)) {
                    var suffix = 1
                    do { finalFolderName = "${folder.name}_${suffix++}" } while (existingFolderNames.contains(finalFolderName))
                }
                if (moveFile(folder, organizedFoldersBase, finalFolderName, onStatusUpdate) != null) {
                    onStatusUpdate("Pasta movida: '$finalFolderName'")
                    movedFoldersCount++
                    existingFolderNames.add(finalFolderName)
                } else {
                    onStatusUpdate("Falha ao mover pasta: '${folder.name}'")
                }
                itemsProcessed++
                onProgressUpdate((itemsProcessed * 100) / totalItems)
            }
        }

        // Lógica para mover arquivos
        if (filesToMove.isNotEmpty()){
            val mainArchiveFolder = findOrCreateDirectory(root, "Arquivos")!!
            filesToMove.forEach { file ->
                val extension = file.getExtension()
                // Usando a constante do seu arquivo FileConfig
                val categoryName = FileConfig.FILE_CATEGORIES.getOrDefault(extension, "Diversos")
                val categoryFolder = findOrCreateDirectory(mainArchiveFolder, categoryName)!!

                var finalFileName = file.name!!
                if (categoryFolder.findFile(finalFileName) != null) {
                    val baseName = finalFileName.substringBeforeLast('.')
                    val ext = finalFileName.substringAfterLast('.')
                    var suffix = 1
                    do { finalFileName = "${baseName}_${suffix++}.$ext" } while (categoryFolder.findFile(finalFileName) != null)
                }

                if (moveFile(file, categoryFolder, finalFileName, onStatusUpdate) != null) {
                    movedFilesCount++
                } else {
                    onStatusUpdate("Falha ao mover arquivo: '${file.name}'")
                }
                itemsProcessed++
                onProgressUpdate((itemsProcessed * 100) / totalItems)
            }
        }
        CategoryOrganizationResult(movedFoldersCount, movedFilesCount)
    }

    // --- Funções auxiliares privadas (só este especialista precisa delas) ---

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
    }

    private fun DocumentFile.getExtension(): String {
        val fileName = this.name ?: return ""
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) fileName.substring(dotIndex).lowercase(Locale.ROOT) else ""
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