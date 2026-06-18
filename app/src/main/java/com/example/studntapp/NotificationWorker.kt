package com.example.studntapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

// يستخدم كنسخة احتياطية تعمل كل عدة ساعات إذا تم إغلاق التطبيق بالقوة
class NotificationWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val prefs = context.getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", 0)

        if (userId == 0) return Result.success()

        try {
            val response = RetrofitClient.instance.getNotifications(userId = userId).execute()
            if (response.isSuccessful) {
                val notifications = response.body()?.data ?: emptyList()
                if (notifications.isNotEmpty()) {
                    val latestNotif = notifications[0]
                    val lastNotifId = prefs.getInt("LAST_NOTIF_ID", 0)

                    if (latestNotif.id > lastNotifId) {
                        showPhoneNotification(latestNotif.title ?: "إشعار جديد", latestNotif.message ?: "توجد رسالة جديدة في المنصة")
                        prefs.edit().putInt("LAST_NOTIF_ID", latestNotif.id).apply()
                    }
                }
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    private fun showPhoneNotification(title: String, message: String) {
        val channelId = "OLMS_NOTIFICATIONS_CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "إشعارات المنصة", NotificationManager.IMPORTANCE_HIGH)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val intent = Intent(context, NotificationsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}