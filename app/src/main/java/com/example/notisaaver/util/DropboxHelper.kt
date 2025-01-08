package com.example.notisaaver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.dropbox.core.DbxAppInfo
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.DbxSessionStore
import com.dropbox.core.DbxWebAuth
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.users.FullAccount
import com.example.notisaaver.util.BuildConfig
import com.example.notisaaver.util.SharedPreferencesSessionStore
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object DropboxHelper {

    private const val ACCESS_TOKEN_PREF_KEY = "DROPBOX_ACCESS_TOKEN"
    private const val DROPBOX_OAUTH_URL = "https://www.dropbox.com/oauth2/authorize"

    /**
     * Starts the Dropbox OAuth flow by opening the authorization URL in a browser.
     * The user will copy the authorization code from Dropbox and paste it into the app.
     */
    fun startOAuthFlow(context: Context) {
        val authUrl = "$DROPBOX_OAUTH_URL?client_id=${BuildConfig.DROPBOX_APP_KEY}&response_type=code"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Exchanges the authorization code for an access token using the Dropbox API.
     * On success, the access token is saved to shared preferences.
     */
    fun getAccessTokenFromCode(
        context: Context,
        authorizationCode: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                // Create the URL for the token exchange endpoint
                val url = URL("https://api.dropbox.com/oauth2/token")

                // Establish an HTTP connection
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }

                // Prepare the POST data
                val postData = "code=$authorizationCode" +
                        "&grant_type=authorization_code" +
                        "&client_id=${BuildConfig.DROPBOX_APP_KEY}" +
                        "&client_secret=${BuildConfig.DROPBOX_APP_SECRET}"

                // Send the POST request
                connection.outputStream.use { output ->
                    output.write(postData.toByteArray())
                }

                // Parse the response
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)

                // Extract the access token
                val accessToken = jsonResponse.getString("access_token")

                // Save the access token in shared preferences
                saveAccessToken(context, accessToken)

                // Notify the caller of success
                onSuccess(accessToken)
            } catch (e: Exception) {
                // Handle any errors
                onError(e.localizedMessage ?: "An error occurred during token exchange")
            }
        }
    }

    /**
     * Saves the access token securely in shared preferences.
     */
    fun saveAccessToken(context: Context, accessToken: String) {
        val sharedPreferences = context.getSharedPreferences("NotiSaaverPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(ACCESS_TOKEN_PREF_KEY, accessToken).apply()
    }

    /**
     * Retrieves the saved access token from shared preferences.
     */
    fun getAccessToken(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("NotiSaaverPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString(ACCESS_TOKEN_PREF_KEY, null)
    }
}
