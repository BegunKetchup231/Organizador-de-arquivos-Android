package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class EmptyFolderRemover(private val context: Context) {

    // A função pública que a MainActivity vai chamar.
    // Retorna o número de pastas removidas.
    suspend fun remove(
        uri: Uri,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {

        onStatusUpdate("\n--- Iniciando Remoção de Pastas Vazias ---")

        val rootDir = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("Pasta não acessível.")

        // 1. Mapeia todas as pastas primeiro
        val allFolders = mutableListOf<DocumentFile>()
        val stack = ArrayDeque<DocumentFile>().apply { add(rootDir) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            allFolders.add(current)
            current.listFiles().filter { it.isDirectory }.forEach { stack.add(it) }
        }

        if (allFolders.size <= 1) {
            onStatusUpdate("Nenhuma subpasta encontrada para verificar.")
            return@withContext 0
        }

        var removedCount = 0
        // 2. Itera de forma reversa para deletar as mais internas primeiro
        allFolders.asReversed().forEachIndexed { index, folder ->
            // Garante que não estamos tentando deletar a pasta raiz selecionada
            if (folder.uri != rootDir.uri && folder.listFiles().isEmpty()) {
                if (folder.delete()) {
                    removedCount++
                } else {
                    onStatusUpdate("Falha ao remover: ${folder.name}")
                }
            }
            onProgressUpdate(((index + 1) * 100) / allFolders.size)
        }
        // Retorna o resultado
        removedCount
    }
}