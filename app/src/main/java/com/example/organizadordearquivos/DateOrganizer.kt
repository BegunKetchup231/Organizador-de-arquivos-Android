package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateOrganizer(private val context: Context) {

    suspend fun organize(
        uri: Uri,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {

        onStatusUpdate("\n--- Iniciando Organização por Data ---")

        val root = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Pasta não acessível.")
        val filesToOrganize = root.listFiles().filter { it.isFile && !it.name.orEmpty().startsWith('.') }

        if (filesToOrganize.isEmpty()) {
            onStatusUpdate("Nenhum arquivo para organizar.")
            return@withContext 0
        }

        val dateOutputBase = findOrCreateDirectory(root, "Organizado_Por_Data")!!
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MM - MMMM", Locale.getDefault())
        val monthFolderCache = mutableMapOf<String, DocumentFile>()
        val existingFilesInDestCache = mutableMapOf<String, MutableSet<String>>()
        var movedCount = 0

        filesToOrganize.forEachIndexed { index, file ->
            val modDate = Date(file.lastModified())
            val yearString = yearFormat.format(modDate)
            val monthString = monthFormat.format(modDate)
            val monthPathKey = "$yearString/$monthString"

            val monthFolder = monthFolderCache.getOrPut(monthPathKey) {
                val yearFolder = findOrCreateDirectory(dateOutputBase, yearString)!!
                findOrCreateDirectory(yearFolder, monthString)!!
            }

            val destinationExistingFiles = existingFilesInDestCache.getOrPut(monthPathKey) {
                monthFolder.listFiles().mapNotNull { it.name }.toMutableSet()
            }

            var finalFileName = file.name!!
            if (destinationExistingFiles.contains(finalFileName)) {
                val baseName = finalFileName.substringBeforeLast('.')
                val ext = file.getExtension()
                var suffix = 1
                do { finalFileName = "${baseName}_${suffix++}$ext" } while (destinationExistingFiles.contains(finalFileName))
            }

            val movedFile = moveFile(file, monthFolder, finalFileName, onStatusUpdate)
            if (movedFile != null) {
                movedCount++
                destinationExistingFiles.add(movedFile.name!!)
            } else {
                onStatusUpdate("Falha ao mover: '${file.name}'")
            }
            onProgressUpdate(((index + 1) * 100) / filesToOrganize.size)
        }
        movedCount
    }

    // --- Funções Auxiliares Privadas ---

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
    }

    private fun DocumentFile.getExtension(): String {
        return this.name?.substringAfterLast('.', "")?.let { ".$it" }?.lowercase(Locale.ROOT) ?: ""
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