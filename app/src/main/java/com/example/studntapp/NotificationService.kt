package com.example.studntapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import org.json.JSONObject

class NotificationService : Service() {

    private var userId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var pusher: Pusher? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StudntApp::NotificationWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        userId = prefs.getInt("USER_ID", 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        connectToSoketiServer()
        return START_STICKY
    }

    // الربط المستمر بـ Soketi بدلاً من الـ Polling
    private fun connectToSoketiServer() {
        if (userId == 0) return

        val options = PusherOptions().apply {
            setHost("olms.inspirers-ngo.org")
            setUseTLS(true)
        }

        pusher = Pusher("app-key", options)
        pusher?.connect()

        val channel = pusher?.subscribe("private_user_$userId")

        // 1. استقبال رسائل الشات
        channel?.bind("new_message") { event ->
            try {
                val json = JSONObject(event.data)
                val senderName = json.optString("sender_name", "مستخدم")
                val shortBody = json.optString("short_body", "رسالة جديدة")

                showPushNotification("رسالة جديدة من $senderName", shortBody, MessagesActivity::class.java)
            } catch (e: Exception) {}
        }

        // 2. استقبال إشعارات النظام
        channel?.bind("new_notification") { event ->
            try {
                val json = JSONObject(event.data)
                val title = json.optString("title", "إشعار نظام")
                val msg = json.optString("message", "لديك تنبيه جديد")

                showPushNotification(title, msg, NotificationsActivity::class.java)
            } catch (e: Exception) {}
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "OLMS_SERVICE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "خدمة الاتصال اللحظي", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("المنصة التعليمية")
            .setContentText("متصل لاستقبال الرسائل...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
    }

    private fun showPushNotification(title: String, message: String, targetActivity: Class<*>) {
        val channelId = "OLMS_ALERT_CHANNEL"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "تنبيهات المنصة", NotificationManager.IMPORTANCE_HIGH)
            manager?.createNotificationChannel(channel)
        }

        val intent = Intent(this, targetActivity)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager?.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        pusher?.disconnect()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}