package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.bumptech.glide.Glide

/**
 * شاشة الإعدادات:
 *  - معلومات الطالب وحالة الحساب.
 *  - اختيار الثيم (5 باقات) + الوضع الليلي/النهاري.
 *  - معلومات وحسابات التواصل، من نحن، فريق التطوير.
 */
class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "الإعدادات"

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val role = prefs.getString("USER_ROLE", "student")
        val name = prefs.getString("USER_NAME", "مستخدم")
        val phone = prefs.getString("USER_PHONE", null)
        val image = prefs.getString("USER_IMAGE", null)

        findViewById<TextView>(R.id.tvSettingsName).text = name
        findViewById<TextView>(R.id.tvSettingsRole).text = if (role == "teacher") "حساب معلم" else "حساب طالب"
        findViewById<TextView>(R.id.tvSettingsPhone).text = "رقم الهاتف: ${phone ?: "—"}"

        val iv = findViewById<ImageView>(R.id.ivSettingsPhoto)
        if (!image.isNullOrEmpty()) {
            val url = if (image.startsWith("http")) image else RetrofitClient.BASE_URL + image
            Glide.with(this).load(url).placeholder(R.mipmap.ic_launcher_round).into(iv)
        }

        buildThemeSwatches()
        setupNightSwitch()
        buildContacts()

        // الإصدار
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(pInfo)
            findViewById<TextView>(R.id.tvVersion).text = "الإصدار ${pInfo.versionName} ($code)"
        } catch (_: Exception) { }
    }

    // ===== الثيمات =====
    private fun buildThemeSwatches() {
        val container = findViewById<LinearLayout>(R.id.themeContainer)
        val tvThemeName = findViewById<TextView>(R.id.tvThemeName)
        val current = ThemeManager.savedPaletteKey(this)
        tvThemeName.text = ThemeManager.palettes.firstOrNull { it.key == current }?.title ?: ""

        val size = dpx(46)
        val margin = dpx(6)
        ThemeManager.palettes.forEach { p ->
            val swatch = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = margin
            swatch.layoutParams = lp

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(p.color)
                if (p.key == current) setStroke(dpx(3), Color.parseColor("#F7A61B"))
                else setStroke(dpx(1), Color.parseColor("#33000000"))
            }
            swatch.background = bg
            swatch.setOnClickListener {
                if (p.key != ThemeManager.savedPaletteKey(this)) {
                    ThemeManager.savePalette(this, p.key)
                    Toast.makeText(this, "تم تطبيق: ${p.title}", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            container.addView(swatch)
        }
    }

    private fun setupNightSwitch() {
        val sw = findViewById<SwitchCompat>(R.id.switchNight)
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = ThemeManager.isNight(this)
        sw.setOnCheckedChangeListener { _, checked ->
            if (checked != ThemeManager.isNight(this)) {
                ThemeManager.setNight(this, checked)
                recreate()
            }
        }
    }

    // ===== التواصل =====
    private fun buildContacts() {
        val container = findViewById<LinearLayout>(R.id.contactContainer)
        // عدّل هذه القيم بحساباتكم الحقيقية
        addContactRow(container, "📧", "البريد الإلكتروني", "resalatygrp@gmail.com",
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:resalatygrp@gmail.com")))
        addContactRow(container, "💬", "واتساب", "تواصل عبر واتساب",
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")))
        addContactRow(container, "📘", "فيسبوك", "صفحتنا على فيسبوك",
            Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com/")))
        addContactRow(container, "🌐", "الموقع الإلكتروني", "زيارة الموقع",
            Intent(Intent.ACTION_VIEW, Uri.parse("https://resalaty.app/")), last = true)
    }

    private fun addContactRow(parent: LinearLayout, emoji: String, label: String, value: String, intent: Intent, last: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpx(14), dpx(14), dpx(14), dpx(14))
            isClickable = true
            isFocusable = true
            val out = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            setOnClickListener {
                try { context.startActivity(intent) }
                catch (_: Exception) { Toast.makeText(context, "لا يوجد تطبيق لفتح هذا الرابط", Toast.LENGTH_SHORT).show() }
            }
        }
        val ic = TextView(this).apply { text = emoji; textSize = 20f }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dpx(14) }
        }
        texts.addView(TextView(this).apply {
            text = label; textSize = 14f; setTextColor(col(R.color.ink)); setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = value; textSize = 12.5f; setTextColor(col(R.color.ink_muted))
        })
        row.addView(ic)
        row.addView(texts)
        row.addView(TextView(this).apply { text = "‹"; textSize = 22f; setTextColor(col(R.color.ink_faint)) })
        parent.addView(row)

        if (!last) {
            parent.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpx(1)).also {
                    it.marginStart = dpx(48); it.marginEnd = dpx(14)
                }
                setBackgroundColor(col(R.color.line))
            })
        }
    }

    private fun col(id: Int) = ContextCompat.getColor(this, id)
    private fun dpx(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
