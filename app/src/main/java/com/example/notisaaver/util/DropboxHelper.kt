package com.example.notisaaver

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.notisaaver.util.BuildConfig
import com.example.notisaaver.util.Logger
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object DropboxHelper {

    private const val ACCESS_TOKEN_PREF_KEY = "DROPBOX_ACCESS_TOKEN"
    private const val DROPBOX_OAUTH_URL = "https://www.dropbox.com/oauth2/authorize"
    private val client = OkHttpClient()

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

        Logger.d("DropboxHelper", "Started OAuth flow, opening URL: $authUrl")
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
        Logger.d("DropboxHelper", "Exchanging authorization code for access token. Code: $authorizationCode")

        val url = "https://api.dropbox.com/oauth2/token"
        val formBody = FormBody.Builder()
            .add("code", authorizationCode)
            .add("grant_type", "authorization_code")
            .add("client_id", BuildConfig.DROPBOX_APP_KEY)
            .add("client_secret", BuildConfig.DROPBOX_APP_SECRET)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("DropboxHelper", "Token exchange failed", e)
                onError(e.localizedMessage ?: "An error occurred during token exchange")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                    val accessToken = jsonResponse.getString("access_token")

                    // Save the access token in shared preferences
                    saveAccessToken(context, accessToken)

                    // Notify the caller of success
                    Logger.d("DropboxHelper", "Access token retrieved successfully")
                    onSuccess(accessToken)
                } else {
                    Logger.e("DropboxHelper", "Failed to get access token: ${response.message}")
                    onError("Failed to get access token: ${response.message}")
                }
            }
        })
    }

    /**
     * Saves the access token securely in shared preferences.
     */
    fun saveAccessToken(context: Context, accessToken: String) {
        val sharedPreferences = context.getSharedPreferences("NotiSaaverPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(ACCESS_TOKEN_PREF_KEY, accessToken).apply()

        Logger.d("DropboxHelper", "Access token saved successfully: $accessToken")
    }

    /**
     * Retrieves the saved access token from shared preferences.
     */
    fun getAccessToken(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("NotiSaaverPrefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString(ACCESS_TOKEN_PREF_KEY, null)

        if (accessToken != null) {
            Logger.d("DropboxHelper", "Access token retrieved from shared preferences")
        } else {
            Logger.d("DropboxHelper", "No access token found in shared preferences")
        }

        return accessToken
    }
}
