package com.example.organizadordearquivos

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class OrganizationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_OPERATION_TYPE = "OPERATION_TYPE"
        const val KEY_URI = "URI"
        const val KEY_PROGRESS_PERCENT = "PROGRESS_PERCENT"
        const val KEY_PROGRESS_STATUS = "PROGRESS_STATUS"

        // Nomes das operações
        const val OP_ORGANIZE_BY_CATEGORY = "ORGANIZE_BY_CATEGORY"
        const val OP_ORGANIZE_BY_DATE = "ORGANIZE_BY_DATE" // <<< NOVA OPERAÇÃO ADICIONADA
    }

    override suspend fun doWork(): Result {
        val operationType = inputData.getString(KEY_OPERATION_TYPE)
        val uriString = inputData.getString(KEY_URI)

        if (operationType.isNullOrEmpty() || uriString.isNullOrEmpty()) {
            return Result.failure()
        }

        val uri = uriString.toUri()

        // Decide qual especialista chamar com base no tipo de operação
        when (operationType) {
            OP_ORGANIZE_BY_CATEGORY -> {
                val organizer = CategoryOrganizer(applicationContext)
                organizer.organize(
                    uri = uri,
                    onStatusUpdate = { message -> setProgressAsync(workDataOf(KEY_PROGRESS_STATUS to message)) },
                    onProgressUpdate = { progress -> setProgressAsync(workDataOf(KEY_PROGRESS_PERCENT to progress)) }
                )
            }

            // <<< NOVO CASE ADICIONADO PARA ORGANIZAR POR DATA
            OP_ORGANIZE_BY_DATE -> {
                val organizer = DateOrganizer(applicationContext)
                organizer.organize(
                    uri = uri,
                    onStatusUpdate = { message -> setProgressAsync(workDataOf(KEY_PROGRESS_STATUS to message)) },
                    onProgressUpdate = { progress -> setProgressAsync(workDataOf(KEY_PROGRESS_PERCENT to progress)) }
                )
            }
        }

        return Result.success()
    }
}