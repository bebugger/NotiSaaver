package com.example.notisaaver.util

import android.content.Context
import android.util.Log
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import java.io.File
import java.io.FileInputStream

class DropboxUploader(context: Context) {

    private val APP_KEY = "mrqqi4cirp533s8" // Replace with your App Key
    private val ACCESS_TOKEN = "sl.CDXvnpn0pKOrV2GK1ldj89UB78OLOL7yudlPCwF6-VXMKmgcbdgesuYXzlNjCP9-8qrPCxvaJP96xFbr29VdEIH0h6BJ5nEkeFTnqnRi7baJ0asfTffjqPyk1od8_RvhG2u6NS1KXlOu" // Replace with OAuth token if applicable

    private val client: DbxClientV2

    init {
        val config = DbxRequestConfig.newBuilder("NotiSaaver/1.0").build()
        client = DbxClientV2(config, ACCESS_TOKEN)
    }

    fun uploadFile(localFilePath: String, dropboxPath: String): Boolean {
        return try {
            val file = File(localFilePath)
            if (!file.exists()) {
                Log.e("DropboxUploader", "File does not exist: $localFilePath")
                return false
            }

            FileInputStream(file).use { inputStream ->
                client.files().uploadBuilder(dropboxPath).uploadAndFinish(inputStream)
            }
            Log.d("DropboxUploader", "File uploaded successfully to $dropboxPath")
            true
        } catch (e: Exception) {
            Log.e("DropboxUploader", "Failed to upload file to Dropbox", e)
            false
        }
    }
}
