package com.example.snacksmack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.snacksmack.notifications.NotificationHelper
import com.example.snacksmack.notifications.NotificationScheduler

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
