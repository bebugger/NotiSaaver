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
import com.example.notisaaver.util.SharedPreferencesSessionStore
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object DropboxHelper {

    private const val APP_KEY = "mrqqi4cirp533s8"
    private const val APP_SECRET = "jkm74jzpmsn3j39"
    private const val REDIRECT_URI = "https://sites.google.com/view/notisaaver"
    private const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
    private const val SESSION_STORE_KEY = "dbx_auth_session"

    private fun getDbxAppInfo(): DbxAppInfo {
        return DbxAppInfo(APP_KEY, APP_SECRET)
    }

    private fun getDbxSessionStore(context: Context): DbxSessionStore {
        val sharedPreferences = context.getSharedPreferences("DropboxPrefs", Context.MODE_PRIVATE)
        return SharedPreferencesSessionStore(sharedPreferences, SESSION_STORE_KEY)
    }

    fun startOAuthFlow(context: Context) {
        val config = DbxRequestConfig.newBuilder("NotiSaaver").build()
        val appInfo = getDbxAppInfo()
        val sessionStore = getDbxSessionStore(context)

        val webAuth = DbxWebAuth(config, appInfo)
        val request = DbxWebAuth.Request.newBuilder()
//            .withRedirectUri(REDIRECT_URI, sessionStore)
            .build()

        val authorizeUrl = webAuth.authorize(request)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))
        context.startActivity(intent)
    }

    fun handleOAuthRedirect(context: Context, uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            exchangeCodeForAccessToken(code, context)
        } else {
            Toast.makeText(context, "Error: No authorization code found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exchangeCodeForAccessToken(code: String, context: Context) {
        val client = OkHttpClient()

        val formBody = FormBody.Builder()
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("client_id", APP_KEY)
            .add("client_secret", APP_SECRET)
//            .add("redirect_uri", REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Toast.makeText(context, "Error exchanging code for token: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val accessToken = parseAccessToken(responseData)
                    if (accessToken != null) {
                        saveAccessToken(context, accessToken)
                        Toast.makeText(context, "Access token saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error: Access token not found in response", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun parseAccessToken(responseData: String?): String? {
        responseData?.let {
            return try {
                val json = JSONObject(it)
                json.getString("access_token")
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun saveAccessToken(context: Context, accessToken: String) {
        val sharedPreferences = context.getSharedPreferences("DropboxPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("access_token", accessToken).apply()
    }

    fun getAccessToken(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("DropboxPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("access_token", null)
    }

    fun getAccountInfo(client: DbxClientV2?) {
        if (client != null) {
            try {
                val account: FullAccount = client.users().currentAccount
                println("Account name: ${account.name.displayName}")
            } catch (e: Exception) {
                println("Error fetching account info: ${e.message}")
            }
        }
    }
}
