package com.example.studntapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

/**
 * يولّد خلفية نقاط (شبكة 45°) برمجياً دون أي ملف bitmap/vector في XML،
 * لتفادي أخطاء inflation نهائياً. آمن تماماً.
 */
object DotsBackground {

    fun create(context: Context, bgColor: Int, dotColor: Int): Drawable? {
        return try {
            val density = context.resources.displayMetrics.density
            val tile = (34 * density).toInt().coerceAtLeast(24)
            val r = 1.5f * density

            val bmp = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }

            val h = tile / 2f
            val f = tile.toFloat()
            // نقاط منتصفات الأضلاع + المركز => شبكة بزاوية 45°
            canvas.drawCircle(h, 0f, r, paint)
            canvas.drawCircle(0f, h, r, paint)
            canvas.drawCircle(f, h, r, paint)
            canvas.drawCircle(h, f, r, paint)
            canvas.drawCircle(h, h, r, paint)

            val tiled = BitmapDrawable(context.resources, bmp).apply {
                setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            }
            LayerDrawable(arrayOf(ColorDrawable(bgColor), tiled))
        } catch (e: Throwable) {
            null
        }
    }

    fun applyTo(view: android.view.View, isNight: Boolean) {
        val bg = if (isNight) Color.parseColor("#0F1522") else Color.parseColor("#F4F6F9")
        val dot = if (isNight) Color.parseColor("#33475569") else Color.parseColor("#22647488")
        create(view.context, bg, dot)?.let { view.background = it }
    }
}
