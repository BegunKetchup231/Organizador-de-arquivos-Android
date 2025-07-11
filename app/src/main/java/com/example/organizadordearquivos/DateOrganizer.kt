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
            onStatusUpdate("Nenhum arquivo para organizar por data.")
            return@withContext 0
        }

        // MUDANÇA 1: Nome da pasta base alterado para usar espaços.
        val dateOutputBase = findOrCreateDirectory(root, "Organizado Por Data")!!

        // Formatos de data para os novos nomes de pasta
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        val monthYearFormat = SimpleDateFormat("MMMM 'de' yyyy", Locale.getDefault())

        val monthFolderCache = mutableMapOf<String, DocumentFile>()
        var movedCount = 0

        filesToOrganize.forEachIndexed { index, file ->
            val modDate = Date(file.lastModified())

            // MUDANÇA 2: Cria os nomes de pasta personalizados.
            val yearString = yearFormat.format(modDate)
            val yearFolderName = "Arquivos de $yearString"

            val monthFolderNameRaw = monthYearFormat.format(modDate)
            // Capitaliza a primeira letra do mês (ex: "julho" -> "Julho")
            val monthFolderName = monthFolderNameRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            val pathCacheKey = "$yearFolderName/$monthFolderName"

            // O cache garante que não vamos procurar as mesmas pastas repetidamente
            val monthFolder = monthFolderCache.getOrPut(pathCacheKey) {
                val yearFolder = findOrCreateDirectory(dateOutputBase, yearFolderName)!!
                findOrCreateDirectory(yearFolder, monthFolderName)!!
            }

            var finalFileName = file.name!!
            // Lida com conflitos de nome
            if (monthFolder.findFile(finalFileName) != null) {
                val baseName = finalFileName.substringBeforeLast('.')
                val ext = finalFileName.substringAfterLast('.')
                var suffix = 1
                do {
                    finalFileName = "${baseName}_${suffix++}.$ext"
                } while (monthFolder.findFile(finalFileName) != null)
            }

            val movedFile = moveFile(file, monthFolder, finalFileName, onStatusUpdate)
            if (movedFile != null) {
                movedCount++
            } else {
                onStatusUpdate("Falha ao mover: '${file.name}'")
            }
            onProgressUpdate(((index + 1) * 100) / filesToOrganize.size)
        }
        // Retorna o total de arquivos movidos
        movedCount
    }

    // --- Funções Auxiliares Privadas ---

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
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