package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class EmptyFolderRemover(private val context: Context) {

    // NOVA FUNÇÃO DE ANÁLISE
    suspend fun analyze(uri: Uri): Int = withContext(Dispatchers.IO) {
        val rootDir = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("Pasta não acessível.")

        var emptyFolderCount = 0
        val stack = ArrayDeque<DocumentFile>().apply { add(rootDir) }

        // A análise apenas percorre e conta, sem deletar
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            // Adiciona as subpastas à pilha para verificação futura
            current.listFiles().filter { it.isDirectory }.forEach { stack.add(it) }
            // Conta a pasta atual se ela estiver vazia e não for a pasta raiz
            if (current.uri != rootDir.uri && current.listFiles().isEmpty()) {
                emptyFolderCount++
            }
        }
        emptyFolderCount
    }


    // Função de remoção (sem alterações)
    suspend fun remove(
        uri: Uri,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {

        onStatusUpdate("\n--- Iniciando Remoção de Pastas Vazias ---")

        val rootDir = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("Pasta não acessível.")

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
        allFolders.asReversed().forEachIndexed { index, folder ->
            if (folder.uri != rootDir.uri && folder.listFiles().isEmpty()) {
                if (folder.delete()) {
                    removedCount++
                } else {
                    onStatusUpdate("Falha ao remover: ${folder.name}")
                }
            }
            onProgressUpdate(((index + 1) * 100) / allFolders.size)
        }
        removedCount
    }
}