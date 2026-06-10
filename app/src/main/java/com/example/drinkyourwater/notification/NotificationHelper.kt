package com.example.drinkyourwater.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.drinkyourwater.R

class NotificationHelper(private val context: Context) {
    private val channelId = "alarm_reminder_channel_v3"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Health Reminders"
            val descriptionText = "Loud alarms for water and medicine reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setSound(soundUri, null)
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun showNotification(title: String, message: String, type: String? = null, name: String? = null, scheduledTime: Long? = null, medicineId: Int? = null) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notificationId = medicineId ?: name?.hashCode() ?: title.hashCode()
        
        val largeIcon = getBitmapFromVectorDrawable(R.drawable.ic_worm_happy)
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_worm) // Monochrome icon for status bar
            .setLargeIcon(largeIcon) // Big color icon for the notification body
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 1000, 500, 1000))
            .setAutoCancel(true)
            .setColor(context.getColor(android.R.color.holo_green_dark))

        // Set Content Intent (Clicking the notification opens the app to log custom time)
        val clickIntent = Intent(context, com.example.drinkyourwater.MainActivity::class.java).apply {
            putExtra("ACTION_LOG_DONE", true)
            putExtra("type", type)
            putExtra("name", name)
            putExtra("medicineId", medicineId)
            putExtra("notificationId", notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 500,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(clickPendingIntent)

        if (type != null && name != null) {
            // Set Delete Intent (Swiping away the notification triggers a reschedule)
            val dismissIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra("type", type)
                putExtra("name", name)
                putExtra("medicineId", medicineId)
                putExtra("scheduledTime", scheduledTime)
                putExtra("title", title)
                putExtra("message", message)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 600,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setDeleteIntent(dismissPendingIntent)

            // Button 1: JUST NOW (Background action using current time)
            val justNowIntent = Intent(context, LogActionReceiver::class.java).apply {
                putExtra("type", type)
                putExtra("name", name)
                putExtra("medicineId", medicineId)
                putExtra("notificationId", notificationId)
            }
            val justNowPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 100,
                justNowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "JUST NOW", justNowPendingIntent)

            // Button 2: ON SCHED (Background action using scheduled time)
            if (scheduledTime != null && scheduledTime > 0) {
                val onSchedIntent = Intent(context, LogActionReceiver::class.java).apply {
                    putExtra("type", type)
                    putExtra("name", name)
                    putExtra("medicineId", medicineId)
                    putExtra("notificationId", notificationId)
                    putExtra("timestamp", scheduledTime)
                }
                val onSchedPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + 300,
                    onSchedIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "ON SCHED", onSchedPendingIntent)
            }
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}
