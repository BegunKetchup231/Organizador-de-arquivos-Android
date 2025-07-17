package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlin.math.log2 // IMPORT ADICIONADO (caso precise no futuro)
import kotlin.math.pow  // IMPORT ADICIONADO

class OrganizationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_OPERATION_TYPE = "OPERATION_TYPE"
        const val KEY_URI = "URI"
        const val KEY_PROGRESS_PERCENT = "PROGRESS_PERCENT"
        const val KEY_PROGRESS_STATUS = "PROGRESS_STATUS"
        const val KEY_RESULT_SUMMARY = "RESULT_SUMMARY"

        const val OP_ORGANIZE_BY_CATEGORY = "ORGANIZE_BY_CATEGORY"
        const val OP_ORGANIZE_BY_DATE = "ORGANIZE_BY_DATE"
        const val OP_CLEAN_TEMP_FILES = "CLEAN_TEMP_FILES"
        const val OP_REMOVE_EMPTY_FOLDERS = "REMOVE_EMPTY_FOLDERS" // Preparado para o futuro
    }

    override suspend fun doWork(): Result {
        val operationType = inputData.getString(KEY_OPERATION_TYPE)
        val uriString = inputData.getString(KEY_URI)

        if (operationType.isNullOrEmpty() || uriString.isNullOrEmpty()) {
            return Result.failure()
        }

        val uri = uriString.toUri()
        var finalResultData = workDataOf()

        when (operationType) {
            OP_ORGANIZE_BY_CATEGORY -> {
                val organizer = CategoryOrganizer(applicationContext)
                val result = organizer.organize(uri, ::sendStatus, ::sendProgress)
                finalResultData = workDataOf(KEY_RESULT_SUMMARY to "\n--- Resumo: ${result.movedFiles} arquivos movidos para suas categorias.")
            }
            OP_ORGANIZE_BY_DATE -> {
                val organizer = DateOrganizer(applicationContext)
                val result = organizer.organize(uri, ::sendStatus, ::sendProgress)
                finalResultData = workDataOf(KEY_RESULT_SUMMARY to "\n--- Resumo: $result arquivos movidos por data.")
            }
            OP_CLEAN_TEMP_FILES -> {
                val cleaner = TempFileCleaner(applicationContext)
                val result = cleaner.clean(uri, ::sendStatus, ::sendProgress)
                val freedSpace = convertBytes(result.spaceToFree)
                finalResultData = workDataOf(KEY_RESULT_SUMMARY to "\n--- Resumo: ${result.filesFound} arquivos removidos ($freedSpace liberados).")
            }
        }

        return Result.success(finalResultData)
    }

    // CORREÇÃO: 'suspend' foi ADICIONADO DE VOLTA
    private suspend fun sendProgress(progress: Int) {
        setProgressAsync(workDataOf(KEY_PROGRESS_PERCENT to progress))
    }

    // CORREÇÃO: 'suspend' foi ADICIONADO DE VOLTA
    private suspend fun sendStatus(message: String) {
        setProgressAsync(workDataOf(KEY_PROGRESS_STATUS to message))
    }

    private fun convertBytes(num: Long): String {
        if (num < 1024) return "$num bytes"
        val units = listOf("bytes", "KB", "MB", "GB", "TB")
        val i = (log2(num.toDouble()) / log2(1024.0)).toInt()
        val size = num / 1024.0.pow(i.toDouble())
        return String.format(java.util.Locale.getDefault(), "%.1f %s", size, units[i])
    }
}