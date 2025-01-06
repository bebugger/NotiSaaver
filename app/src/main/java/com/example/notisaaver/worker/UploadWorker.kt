package com.example.notisaaver.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import java.io.File
import java.io.FileInputStream

class UploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // Dropbox Access Token
        val dropboxAccessToken = inputData.getString("DROPBOX_ACCESS_TOKEN") ?: return Result.failure()

        // Path to the notifications_log.txt file
        val logFilePath = inputData.getString("LOG_FILE_PATH") ?: return Result.failure()
        val logFile = File(logFilePath)

        if (!logFile.exists()) {
            return Result.failure()
        }

        return try {
            // Configure Dropbox client
            val config = DbxRequestConfig.newBuilder("NotiSaver/1.0").build()
            val client = DbxClientV2(config, dropboxAccessToken)

            // Upload the file
            FileInputStream(logFile).use { inputStream ->
                client.files().uploadBuilder("/notifications_log.txt")
                    .uploadAndFinish(inputStream)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
