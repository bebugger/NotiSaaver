package com.example.notisaaver

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.work.Data
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.example.notisaaver.ui.theme.NotiSaaverTheme
import com.example.notisaaver.worker.UploadWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotiSaaverTheme {
                NotificationAccessScreen()
            }
        }

        if (intent != null) {
            handleDropboxRedirectIntent(intent)
        } else {
            Log.w("MainActivity", "Intent is null in onCreate")
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent != null) {
            handleDropboxRedirectIntent(intent)
        } else {
            Log.w("MainActivity", "Intent is null in onResume")
        }
    }

    private fun handleDropboxRedirectIntent(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null && uri.scheme == "https" && uri.host == "sites.google.com" && uri.path == "/view/notisaaver") {
            try {
                DropboxHelper.handleOAuthRedirect(this, uri)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error handling Dropbox OAuth redirect", e)
            }
        } else {
            Log.w("MainActivity", "No valid URI found in intent or URI does not match the required format")
        }
    }

    @Composable
    fun NotificationAccessScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        var authorizationCode by remember { mutableStateOf("") }
        var latestNotification by remember { mutableStateOf<Pair<String, String>?>(null) }
        var accountInfo by remember { mutableStateOf<String?>(null) }
        var folderList by remember { mutableStateOf<List<String>>(emptyList()) }

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

            Button(
                onClick = { navigateToNotificationSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Notification Access Settings")
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                onClick = { folderList = fetchDropboxFolders(context) },
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
        }
    }

    private fun saveAccessTokenFromCode(context: Context, authorizationCode: String) {
        thread {
            try {
                val accessToken = DropboxHelper.getAccessTokenFromCode(context, authorizationCode)
                if (accessToken != null) {
                    DropboxHelper.saveAccessToken(context, accessToken)
                    runOnUiThread {
                        Toast.makeText(context, "Access token saved successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(context, "Failed to retrieve access token", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        val accessToken = DropboxHelper.getAccessToken(context) ?: return emptyList()
        return try {
            val config = DbxRequestConfig.newBuilder("NotiSaaver/1.0").build()
            val client = DbxClientV2(config, accessToken)
            val entries = client.files().listFolder("").entries
            entries.map { it.name }
        } catch (e: Exception) {
            emptyList()
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
