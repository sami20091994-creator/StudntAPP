package com.example.studntapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * تمت إزالة صفحة الملف الشخصي. هذا الكلاس محتفظ به فقط كموجِّه
 * لأي رابط قديم؛ يحوّل المستخدم مباشرة إلى شاشة الإعدادات الجديدة.
 */
class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}
