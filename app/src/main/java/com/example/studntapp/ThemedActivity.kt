package com.example.studntapp

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * أساس خفيف للشاشات التي لا تحتاج درجاً/شريطاً سفلياً،
 * لكنها يجب أن تتبع نفس الباقة اللونية واتجاه RTL.
 */
open class ThemedActivity : AppCompatActivity() {
    private var appliedSignature: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeManager.wrapRtl(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ThemeManager.forceRtl(this)
        appliedSignature = ThemeManager.currentSignature(this)
    }
    override fun onResume() {
        super.onResume()
        // تطبيق تغيّر الثيم/الوضع فوراً عند العودة لهذه الشاشة.
        if (appliedSignature != ThemeManager.currentSignature(this)) recreate()
    }
}
