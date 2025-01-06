package com.example.notisaaver.util

import android.content.Context
import android.content.SharedPreferences
import com.dropbox.core.DbxSessionStore

class SharedPreferencesSessionStore(
    private val sharedPreferences: SharedPreferences,
    private val key: String
) : DbxSessionStore {

    override fun get(): String? {
        return sharedPreferences.getString(key, null)
    }

    override fun set(value: String?) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override fun clear() {
        sharedPreferences.edit().remove(key).apply()
    }
}
