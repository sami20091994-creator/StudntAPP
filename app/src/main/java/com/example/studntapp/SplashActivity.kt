package com.example.studntapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<CardView>(R.id.cvSplashLogo)
        val title = findViewById<TextView>(R.id.tvSplashTitle)

        // حالة البداية
        logo.alpha = 0f
        logo.scaleX = 0.5f
        logo.scaleY = 0.5f
        title.alpha = 0f
        title.translationY = 40f

        // حركة الشعار: ظهور + تكبير مع ارتداد لطيف
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(650L)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        // حركة العنوان: ظهور + صعود
        title.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(350L)
            .setDuration(500L)
            .start()

        // الانتقال للشاشة التالية بعد انتهاء الحركة
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1600L)
    }
}
