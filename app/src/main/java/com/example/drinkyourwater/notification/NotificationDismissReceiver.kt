package com.example.drinkyourwater.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Show overlay message informing the user about the reschedule
        // The actual rescheduling is handled when the notification was first shown
        Toast.makeText(
            context,
            "We'll remind you again in 30 minutes!",
            Toast.LENGTH_LONG
        ).show()
    }
}
