package com.example.studntapp

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * (مُلغاة) كانت خدمة أمامية دائمة عبر Soketi/Pusher تُبقي إشعاراً ثابتاً وتستنزف البطارية.
 * تم استبدالها بالكامل بـ Firebase Cloud Messaging (انظر FcmService.kt)،
 * فلم يعد هناك اتصال دائم ولا إشعار ثابت. أُبقيت الفئة فارغة لتفادي كسر أي مرجع قديم.
 */
class NotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
