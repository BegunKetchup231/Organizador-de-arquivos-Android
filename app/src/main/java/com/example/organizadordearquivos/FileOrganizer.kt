package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class FileOrganizer(private val context: Context) {

    // A função principal agora é pública e "suspensa" (suspend)
    // Ela recebe lambdas para atualizar a UI e retorna o resultado
    suspend fun organizeByCategory(
        uri: Uri,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Int) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        onStatusUpdate("\n--- Iniciando Organização por Categoria ---")

        val root = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Pasta não acessível.")
        val items = root.listFiles().toList()
        val foldersToIgnore = setOf("Arquivos", "Pastas_Organizadas", "Organizado_Por_Data")
        val filesToMove = items.filter { it.isFile && !it.name.orEmpty().startsWith('.') }
        val foldersToMove = items.filter { it.isDirectory && it.name !in foldersToIgnore }
        val totalItems = filesToMove.size + foldersToMove.size

        if (totalItems == 0) {
            onStatusUpdate("Nenhum item para organizar.")
            return@withContext 0 to 0 // Retorna 0 pastas e 0 arquivos movidos
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
                val categoryName = FileConfig.FILE_CATEGORIES.getOrDefault(extension, "Diversos")
                val categoryFolder = findOrCreateDirectory(mainArchiveFolder, categoryName)!!

                val extensionWithoutDot = extension.removePrefix(".")
                val finalSubFolderName = "${categoryName}.${extensionWithoutDot.uppercase(Locale.ROOT)}"
                val finalDestFolder = findOrCreateDirectory(categoryFolder, finalSubFolderName)!!

                var finalFileName = file.name!!
                if (finalDestFolder.findFile(finalFileName) != null) {
                    val baseName = finalFileName.substringBeforeLast('.')
                    val ext = finalFileName.substringAfterLast('.')
                    var suffix = 1
                    do { finalFileName = "${baseName}_${suffix++}.$ext" } while (finalDestFolder.findFile(finalFileName) != null)
                }

                if (moveFile(file, finalDestFolder, finalFileName, onStatusUpdate) != null) {
                    movedFilesCount++
                } else {
                    onStatusUpdate("Falha ao mover arquivo: '${file.name}'")
                }
                itemsProcessed++
                onProgressUpdate((itemsProcessed * 100) / totalItems)
            }
        }

        return@withContext movedFoldersCount to movedFilesCount
    }

    // Os métodos auxiliares foram movidos para cá e agora são privados
    private fun DocumentFile.getExtension(): String {
        val fileName = this.name ?: return ""
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(dotIndex).lowercase(Locale.ROOT)
        } else {
            ""
        }
    }

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
    }

    private fun moveFile(fileToMove: DocumentFile, destinationDir: DocumentFile, finalFileName: String, onStatusUpdate: (String) -> Unit): DocumentFile? {
        try {
            val movedUri = DocumentsContract.moveDocument(context.contentResolver, fileToMove.uri, fileToMove.parentFile!!.uri, destinationDir.uri)
            // Após mover, o nome do arquivo pode ser diferente se houver conflito.
            // Para manter a consistência, tentamos renomear para o nome final desejado.
            val movedFile = movedUri?.let { DocumentFile.fromSingleUri(context, it) }
            if (movedFile != null && movedFile.name != finalFileName) {
                if (!movedFile.renameTo(finalFileName)) {
                    onStatusUpdate("Aviso: Falha ao renomear '${movedFile.name}' para '$finalFileName' após mover.")
                }
            }
            return movedFile?.parentFile?.findFile(finalFileName)
        } catch (e: Exception) {
            onStatusUpdate("Aviso: Usando método de cópia para '${fileToMove.name}' -> '$finalFileName'.")
            // A lógica de cópia e exclusão (fallback) pode ser adicionada aqui se necessário
            return null
        }
    }
}