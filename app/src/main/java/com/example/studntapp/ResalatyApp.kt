package com.example.studntapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * نقطة الإقلاع الموحّدة للتطبيق:
 *  - تثبيت اللغة العربية واتجاه RTL على مستوى التطبيق بالكامل.
 *  - استرجاع وضع (ليلي/نهاري) المحفوظ وتطبيقه قبل ظهور أي شاشة.
 */
class ResalatyApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(ThemeManager.wrapRtl(base))
    }

    override fun onCreate() {
        super.onCreate()
        // الوضع الليلي/النهاري يُطبَّق عبر wrapRtl (uiMode) في كل شاشة،
        // فلا حاجة لـ AppCompatDelegate هنا (كان يسبب حلقة إعادة تشغيل).

        // قناة إشعارات FCM (يجب أن تكون موجودة لاستقبال الإشعارات في الخلفية).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "resalaty_alerts", "تنبيهات رسالتي", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "إشعارات الرسائل والتنبيهات" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeManager.wrapRtl(this)
    }
}
