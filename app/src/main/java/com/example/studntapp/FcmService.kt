package com.example.studntapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * استقبال الإشعارات الفورية عبر Firebase Cloud Messaging:
 *  - بلا اتصال دائم وبلا إشعار ثابت وبلا استنزاف بطارية (يدير Google التسليم).
 *  - يعرض الإشعار ويوجّه إلى الشاشة المناسبة حسب النوع.
 */
class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        uploadToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // ندعم حمولة data (مفضّلة) أو notification.
        val data = message.data
        val type = data["type"] ?: "notification"
        val title = data["title"] ?: message.notification?.title ?: "إشعار جديد"
        val body = data["body"] ?: data["message"] ?: message.notification?.body ?: ""

        val target: Class<*> = if (type == "message") MessagesActivity::class.java else NotificationsActivity::class.java
        showNotification(title, body, target, data)
    }

    private fun showNotification(title: String, body: String, target: Class<*>, data: Map<String, String> = emptyMap()) {
        val channelId = "resalaty_alerts"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "تنبيهات رسالتي", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعارات الرسائل والتنبيهات"
            }
            manager?.createNotificationChannel(channel)
        }

        val intent = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // إن كان إشعار رسالة، نمرّر بيانات المحادثة ليُفتح الحوار مباشرةً عند الضغط.
        if (data["type"] == "message") {
            data["sender_id"]?.toIntOrNull()?.let { intent.putExtra("OPEN_CHAT_ID", it) }
            intent.putExtra("OPEN_CHAT_TYPE", data["chat_type"] ?: "user")
            intent.putExtra("OPEN_CHAT_NAME", data["sender_name"])
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        manager?.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        /**
         * يطلب رمز FCM الحالي ويرسله للسيرفر إن كان المستخدم مسجّلاً للدخول.
         * محاط بـ try/catch حتى لا ينهار التطبيق إذا لم يُفعَّل Firebase بعد
         * (قبل إضافة google-services.json وتفعيل المُلحق).
         */
        fun syncToken(context: Context) {
            try {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    uploadToken(context, token)
                }
            } catch (_: Exception) {
                // Firebase غير مُهيّأ بعد — يُتجاهَل بأمان حتى التفعيل.
            }
        }

        private fun uploadToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences("AppSession", Context.MODE_PRIVATE)
            val userId = prefs.getInt("USER_ID", 0)
            if (userId == 0 || token.isBlank()) return
            RetrofitClient.instance.registerFcmToken(userId = userId, token = token)
                .enqueue(object : Callback<SimpleResponse> {
                    override fun onResponse(call: Call<SimpleResponse>, response: Response<SimpleResponse>) {}
                    override fun onFailure(call: Call<SimpleResponse>, t: Throwable) {}
                })
        }
    }
}
