package com.example.notisaaver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import com.example.notisaaver.ui.theme.NotiSaaverTheme
import com.example.notisaaver.util.Logger

import java.io.File
import java.io.FileInputStream

import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotiSaaverTheme {
                NotificationAccessScreen()
            }
        }
    }

    @Composable
    fun NotificationAccessScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        var authorizationCode by remember { mutableStateOf("") }
        var latestNotification by remember { mutableStateOf<Pair<String, String>?>(null) }
        var accountInfo by remember { mutableStateOf<String?>(null) }
        var folderList by remember { mutableStateOf<List<String>>(emptyList()) }

        // Check if access token is present
        val accessToken = DropboxHelper.getAccessToken(context)

        // Check if notification access is granted
        val isNotificationAccessGranted = isNotificationAccessEnabled(context)

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Grant Notification Access",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Show the button only if notification access is not granted
            if (!isNotificationAccessGranted) {
                Button(
                    onClick = { navigateToNotificationSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Notification Access Settings")
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { latestNotification = getLatestNotificationDetails(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Notification Log")
            }

            latestNotification?.let { (title, text) ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Latest Notification", style = MaterialTheme.typography.headlineSmall)
                Text(text = "Title: $title", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Text: $text", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (accessToken == null || accessToken.isEmpty()) {
                // Only show if access token is not present
                Button(
                    onClick = { DropboxHelper.startOAuthFlow(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Dropbox OAuth Flow")
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = authorizationCode,
                    onValueChange = { authorizationCode = it },
                    label = { Text("Authorization Code") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { saveAccessTokenFromCode(context, authorizationCode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get Access Token")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { fetchDropboxAccountInfo { accountDetails -> accountInfo = accountDetails } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get Dropbox Account Info")
            }

            accountInfo?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Account Info:", style = MaterialTheme.typography.headlineSmall)
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    thread {
                        val folders = fetchDropboxFolders(context)
                        folderList = folders
                        if (folders.isEmpty()) {
                            runOnUiThread {
                                Toast.makeText(context, "No folders found or an error occurred.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("List Dropbox Folders")
            }

            if (folderList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Folders:", style = MaterialTheme.typography.headlineSmall)
                folderList.forEach { folder ->
                    Text(text = folder, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Upload Log Button
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { uploadNotificationLogToDropbox(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Upload Notification Logs")
            }
        }
    }

    // Check if notification access permission is granted
    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabledListeners?.contains(context.packageName) == true
    }

    private fun uploadNotificationLogToDropbox(context: Context) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "NotiSaver/notifications_log.txt")
        if (!file.exists()) {
            Toast.makeText(context, "No notification logs found", Toast.LENGTH_SHORT).show()
            return
        }

        thread {
            try {
                val accessToken = DropboxHelper.getAccessToken(context)
                if (accessToken == null) {
                    runOnUiThread {
                        Toast.makeText(context, "No access token. Please authenticate first.", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }

                val config = DbxRequestConfig.newBuilder("NotiSaaver/1.0").build()
                val client = DbxClientV2(config, accessToken)

                // Open file for upload
                val inputStream = FileInputStream(file)

                // Upload file to Dropbox, overwrite if exists
                val metadata = client.files().uploadBuilder("/notifications_log.txt")
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)

                // Success feedback
                runOnUiThread {
                    Toast.makeText(context, "Notification log uploaded successfully: ${metadata.name}", Toast.LENGTH_SHORT).show()
                }
                Logger.d("MainActivity", "Uploaded notification_log.txt to Dropbox successfully")
            } catch (e: Exception) {
                Logger.e("MainActivity", "Error uploading notification log to Dropbox", e)
                runOnUiThread {
                    Toast.makeText(context, "Error uploading log to Dropbox", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Existing function to save access token from code
    private fun saveAccessTokenFromCode(context: Context, authorizationCode: String) {
        DropboxHelper.getAccessTokenFromCode(
            context,
            authorizationCode,
            onSuccess = { accessToken ->
                DropboxHelper.saveAccessToken(context, accessToken)
                runOnUiThread {
                    Toast.makeText(context, "Access token saved successfully", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun navigateToNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun getLatestNotificationDetails(context: Context): Pair<String, String> {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "NotiSaver")
        val file = File(dir, "notifications_log.txt")

        if (!file.exists() || file.readLines().isEmpty()) {
            return Pair("No Data", "No notifications logged yet")
        }

        val latestNotification = file.readLines().lastOrNull { it.isNotBlank() } ?: "No Data Available"
        val title = latestNotification.substringAfter("Title: ", "No Title")
        val text = latestNotification.substringAfter("Text: ", "No Text")
        return Pair(title, text)
    }

    private fun fetchDropboxAccountInfo(onResult: (String) -> Unit) {
        thread {
            try {
                val accessToken = DropboxHelper.getAccessToken(this)
                if (accessToken != null) {
                    val config = DbxRequestConfig.newBuilder("NotiSaaver/1.0").build()
                    val client = DbxClientV2(config, accessToken)
                    val account = client.users().currentAccount
                    onResult("Name: ${account.name.displayName}\nEmail: ${account.email}")
                } else {
                    onResult("Access token not available.")
                }
            } catch (e: Exception) {
                onResult("Error fetching account info: ${e.localizedMessage}")
            }
        }
    }

    private fun fetchDropboxFolders(context: Context): List<String> {
        val accessToken = DropboxHelper.getAccessToken(context)
        if (accessToken == null) {
            runOnUiThread {
                Toast.makeText(context, "Access token is missing. Please authenticate with Dropbox.", Toast.LENGTH_SHORT).show()
            }
            return emptyList()
        }

        return try {
            val config = DbxRequestConfig.newBuilder("NotiSaaver/1.0").build()
            val client = DbxClientV2(config, accessToken)

            val folderNames = mutableListOf<String>()
            var result = client.files().listFolder("")

            while (true) {
                for (metadata in result.entries) {
                    Log.d("MainActivity", "Found entry: ${metadata.pathLower}")
                    folderNames.add(metadata.name)
                }

                if (!result.hasMore) {
                    break
                }

                result = client.files().listFolderContinue(result.cursor)
            }

            folderNames
        } catch (e: Exception) {
            val errorMessage = "Error fetching folders: ${e.localizedMessage}"
            Log.e("MainActivity", errorMessage, e)
            runOnUiThread {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
            emptyList()
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Thread.currentThread() != mainLooper.thread) {
            this.runOnUiThread { action() }
        } else {
            action()
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun NotificationAccessScreenPreview() {
        NotiSaaverTheme {
            NotificationAccessScreen()
        }
    }
}
