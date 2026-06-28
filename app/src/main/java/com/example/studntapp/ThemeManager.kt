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
 *  - 6 ثيمات لونية (نيلي، زمردي، ملكي، محيطي، عنابي، داكن).
 *  - الثيم الداكن يحاكي ألوان الوضع الليلي دون الاعتماد على وضع النظام.
 *  - فرض RTL واللغة العربية.
 * يُحفظ الاختيار في SharedPreferences ويُطبّق على كل شاشة قبل setContentView.
 */
object ThemeManager {

    private const val PREFS = "AppSession"
    private const val KEY_PALETTE = "THEME_PALETTE"
    private const val KEY_DARK = "THEME_DARK" // الوضع الداكن مستقل عن الباقة

    // مفاتيح الباقات اللونية الخمس
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

    /** الوضع الداكن مفعّل؟ (مستقل عن الباقة — يُبدّله زر التبديل.) */
    fun isNight(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK, false)

    fun setNight(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK, on).apply()
    }

    fun toggleNight(ctx: Context) = setNight(ctx, !isNight(ctx))

    /** يُستدعى داخل onCreate قبل setContentView لتطبيق الباقة + الوضع الداكن. */
    fun applyTheme(activity: AppCompatActivity) {
        // الوضع الداكن يُضبط عالمياً مرّة واحدة عبر setDefaultNightMode (لا per-activity)،
        // فلا تحدث إعادة بناء مزدوجة (سبب الومضة/الـ Drop). تُحمَّل موارد values-night
        // فوق الباقة اللونية المختارة.
        val want = if (isNight(activity)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != want) AppCompatDelegate.setDefaultNightMode(want)
        activity.setTheme(styleFor(savedPaletteKey(activity)))
    }

    /** بصمة الحالة (الباقة + الوضع الداكن) لاكتشاف التغيّر وإعادة البناء. */
    fun currentSignature(ctx: Context): String =
        savedPaletteKey(ctx) + if (isNight(ctx)) ":dark" else ":light"

    // ===== فرض RTL + اللغة العربية + الوضع الليلي (مرتبط بالباقة الداكنة) =====
    fun wrapRtl(base: Context): Context {
        val locale = Locale("ar")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        // لا نتلاعب بِبت الوضع الليلي هنا — يديره AppCompat عبر setDefaultNightMode،
        // فلا يحدث تعارض يؤدّي لإعادة بناء مزدوجة.
        return base.createConfigurationContext(config)
    }

    /** يفرض اتجاه RTL على نافذة النشاط (احتياط إضافي فوق إعداد الـ Manifest). */
    fun forceRtl(activity: AppCompatActivity) {
        activity.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    // ===== انتقال شبيه تيليجرام: انكشاف دائري (رشّة طلاء) عند تبديل الثيم =====
    private var animateNextRecreate = false
    private var revealOld: android.graphics.Bitmap? = null
    private var revealCx = 0
    private var revealCy = 0
    private var revealAt = 0L

    /** إعادة بناء بسيطة مع تلاشٍ (احتياط/توافق قديم). */
    fun smoothRecreate(activity: android.app.Activity) {
        animateNextRecreate = true
        activity.recreate()
    }

    /**
     * إعادة بناء الشاشة مع انكشاف دائري للثيم الجديد من نقطة الزر (مثل تيليجرام).
     * نلتقط صورة للوضع الحالي قبل إعادة البناء، ثم نكشف الجديد بدائرة متوسّعة.
     */
    /** يلتقط لقطة الشاشة الحالية ومركز الانكشاف، تمهيداً لإعادة البناء. يُرجِع true إن نجح. */
    private fun captureForReveal(activity: android.app.Activity, source: View?): Boolean {
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) return false
        if (source != null) {
            val loc = IntArray(2); source.getLocationInWindow(loc)
            revealCx = loc[0] + source.width / 2
            revealCy = loc[1] + source.height / 2
        } else {
            revealCx = decor.width / 2; revealCy = decor.height / 2
        }
        revealOld = try {
            val b = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(b)); b
        } catch (e: Exception) { null }
        revealAt = System.currentTimeMillis()
        return true
    }

    /** تبديل **الباقة اللونية**: إعادة بناء يدوية واحدة دائماً + انكشاف دائري. */
    fun circularRecreate(activity: android.app.Activity, source: View?) {
        if (!captureForReveal(activity, source)) { smoothRecreate(activity); return }
        activity.recreate()
    }

    /** تبديل **الوضع الداكن**: إعادة بناء واحدة عبر المفوّض العام (بلا ازدواج/Drop) + انكشاف. */
    fun circularRecreateNight(activity: android.app.Activity, source: View?) {
        if (!captureForReveal(activity, source)) { smoothRecreate(activity); return }
        val want = if (isNight(activity)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != want) {
            AppCompatDelegate.setDefaultNightMode(want) // إعادة بناء واحدة يديرها AppCompat
        } else {
            activity.recreate() // احتياط: لو كان مضبوطاً مسبقاً
        }
    }

    /**
     * يُستدعى في onCreate: ينفّذ الانكشاف الدائري إن وُجدت لقطة، وإلا تلاشٍ بسيط.
     */
    fun maybeFadeIn(activity: android.app.Activity) {
        val old = revealOld
        if (old != null) {
            if (System.currentTimeMillis() - revealAt > 1500L) { // لقطة قديمة جداً → أسقطها
                revealOld = null; old.recycle(); return
            }
            playReveal(activity)
            return
        }
        if (animateNextRecreate) {
            animateNextRecreate = false
            val dv = activity.window.decorView
            dv.alpha = 0f
            dv.animate().alpha(1f).setDuration(240).start()
        }
    }

    private fun playReveal(activity: android.app.Activity) {
        val decor = activity.window.decorView as? android.view.ViewGroup ?: return
        val cx = revealCx; val cy = revealCy
        decor.post {
            val old = revealOld ?: return@post
            try {
                val w = decor.width; val h = decor.height
                if (w <= 0 || h <= 0) return@post
                // نستهلك اللقطة الآن.
                revealOld = null
                // لقطة للوضع الجديد (حيّ الآن) لنكشفها فوق القديم.
                val newBmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                decor.draw(android.graphics.Canvas(newBmp))

                val oldOverlay = android.widget.ImageView(activity).apply { setImageBitmap(old); scaleType = android.widget.ImageView.ScaleType.FIT_XY }
                val newOverlay = android.widget.ImageView(activity).apply { setImageBitmap(newBmp); scaleType = android.widget.ImageView.ScaleType.FIT_XY; visibility = View.INVISIBLE }
                val lp = android.view.ViewGroup.LayoutParams(w, h)
                decor.addView(oldOverlay, lp)   // القديم بالأسفل
                decor.addView(newOverlay, lp)   // الجديد فوقه — سيُكشف

                newOverlay.post {
                    val maxR = Math.hypot(w.toDouble(), h.toDouble()).toFloat()
                    val anim = android.view.ViewAnimationUtils.createCircularReveal(newOverlay, cx, cy, 0f, maxR)
                    anim.duration = 520
                    anim.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    newOverlay.visibility = View.VISIBLE
                    anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: android.animation.Animator) {
                            decor.removeView(newOverlay); decor.removeView(oldOverlay)
                            old.recycle(); newBmp.recycle()
                        }
                    })
                    anim.start()
                }
            } catch (e: Exception) { old.recycle() }
        }
    }
}
