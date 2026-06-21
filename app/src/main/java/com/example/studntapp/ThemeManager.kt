package com.example.studntapp

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * مدير الهوية البصرية:
 *  - 5 ثيمات لونية + الوضع الليلي/النهاري.
 *  - فرض RTL واللغة العربية.
 * يُحفظ الاختيار في SharedPreferences ويُطبّق على كل شاشة قبل setContentView.
 */
object ThemeManager {

    private const val PREFS = "AppSession"
    private const val KEY_PALETTE = "THEME_PALETTE"
    private const val KEY_NIGHT = "NIGHT_MODE" // 0=نهاري ,1=ليلي

    // مفاتيح الثيمات
    const val INDIGO = "indigo"
    const val EMERALD = "emerald"
    const val ROYAL = "royal"
    const val OCEAN = "ocean"
    const val CRIMSON = "crimson"

    data class Palette(val key: String, val title: String, val color: Int, val styleRes: Int)

    val palettes = listOf(
        Palette(INDIGO,  "النيلي الكلاسيكي", 0xFF4C53A4.toInt(), R.style.Theme_Resalaty_Indigo),
        Palette(EMERALD, "الأخضر الزمردي",   0xFF0E9F6E.toInt(), R.style.Theme_Resalaty_Emerald),
        Palette(ROYAL,   "البنفسجي الملكي",  0xFF6D28D9.toInt(), R.style.Theme_Resalaty_Royal),
        Palette(OCEAN,   "الأزرق المحيطي",   0xFF0E7490.toInt(), R.style.Theme_Resalaty_Ocean),
        Palette(CRIMSON, "العنابي الفاخر",   0xFF9D174D.toInt(), R.style.Theme_Resalaty_Crimson)
    )

    fun savedPaletteKey(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PALETTE, INDIGO) ?: INDIGO

    fun savePalette(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PALETTE, key).apply()
    }

    private fun styleFor(key: String): Int =
        palettes.firstOrNull { it.key == key }?.styleRes ?: R.style.Theme_Resalaty_Indigo

    /** يُستدعى داخل onCreate قبل setContentView لتطبيق الثيم المختار. */
    fun applyTheme(activity: AppCompatActivity) {
        // نضبط الوضع الليلي لكل نشاط عبر مفوّض AppCompat (localNightMode) بقيمة
        // مطابقة تماماً لِما يضبطه wrapRtl في uiMode. بدون هذا السطر كان AppCompat
        // يتبع وضع النظام ويُلغي تفعيلنا اليدوي — فلا يتغيّر شيء عند الضغط على الزر.
        // وبتطابق القيمتين لا يحدث تذبذب ولا حلقة إعادة تشغيل، وتُحمَّل موارد
        // values-night/ تلقائياً عند تفعيل الوضع الليلي.
        activity.delegate.localNightMode =
            if (isNight(activity)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        activity.setTheme(styleFor(savedPaletteKey(activity)))
    }

    // ===== الوضع الليلي/النهاري =====
    fun savedNightMode(ctx: Context): Int {
        val v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_NIGHT, 0)
        return if (v == 1) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
    }

    fun isNight(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_NIGHT, 0) == 1

    fun setNight(ctx: Context, night: Boolean) {
        // نتحكم بالوضع الليلي يدوياً عبر uiMode داخل wrapRtl فقط،
        // ولا نستخدم AppCompatDelegate.setDefaultNightMode حتى لا يحدث تعارض
        // يؤدي إلى حلقة إعادة تشغيل لا نهائية (خاصة على أجهزة MIUI).
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_NIGHT, if (night) 1 else 0).apply()
    }

    /** بصمة الحالة الحالية (الباقة + الوضع) لاكتشاف تغيّر الثيم وإعادة البناء عند الحاجة. */
    fun currentSignature(ctx: Context): String =
        savedPaletteKey(ctx) + ":" + if (isNight(ctx)) "n" else "d"

    // ===== فرض RTL + اللغة العربية + الوضع الليلي =====
    fun wrapRtl(base: Context): Context {
        val locale = Locale("ar")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        // نثبّت بِت الوضع الليلي/النهاري داخل الإعداد نفسه ليتطابق مع تفضيل المستخدم،
        // فلا يبقى أي تعارض بين الإطار و AppCompat (سبب حلقة إعادة التشغيل سابقاً).
        val nightBit = if (isNight(base)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBit
        return base.createConfigurationContext(config)
    }

    /** يفرض اتجاه RTL على نافذة النشاط (احتياط إضافي فوق إعداد الـ Manifest). */
    fun forceRtl(activity: AppCompatActivity) {
        activity.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }
}
