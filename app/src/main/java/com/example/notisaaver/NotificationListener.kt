package com.example.notisaaver

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationListener : NotificationListenerService() {

    private var callReceiver: CallReceiver? = null

    override fun onCreate() {
        super.onCreate()

        // Register for phone call state changes
        callReceiver = CallReceiver()
        val intentFilter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(callReceiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to prevent leaks
        callReceiver?.let {
            unregisterReceiver(it)
        }
        callReceiver = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val notification: Notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE, "No Title")
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val text = textLines?.joinToString("\n") { it.toString() }
            ?: extras.getString(Notification.EXTRA_TEXT, "No Text")

        // Add date and time of the notification
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())

        val logEntry = "Date: $currentTime\nTitle: $title\nText: $text\n"

        saveNotificationToExternalFile(logEntry)
    }

    private fun saveNotificationToExternalFile(data: String) {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            return // External storage not available
        }

        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "NotiSaver")
        if (!dir.exists()) {
            dir.mkdirs() // Create directory if it doesn't exist
        }

        val file = File(dir, "notifications_log.txt")

        try {
            FileOutputStream(file, true).bufferedWriter().use {
                it.append(data)
                it.newLine()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Call receiver for logging call details
    inner class CallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())

            val callDetails = when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> "Incoming call from: $phoneNumber"
                TelephonyManager.EXTRA_STATE_OFFHOOK -> "Call started with: $phoneNumber"
                TelephonyManager.EXTRA_STATE_IDLE -> "Call ended with: $phoneNumber"
                else -> null
            }

            callDetails?.let {
                val logEntry = "$it at $currentTime\n"
                saveNotificationToExternalFile(logEntry)
            }
        }
    }
}
