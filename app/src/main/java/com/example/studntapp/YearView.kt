package com.example.studntapp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.*

/** يبني عرض السنة: 12 شهراً مصغّرة (3 أعمدة). الضغط على شهر يفتحه. */
fun renderYearView(ctx: Context, container: LinearLayout, year: Int, onMonthClick: (Int, Int) -> Unit) {
    container.removeAllViews()
    val d = ctx.resources.displayMetrics.density
    fun px(v: Int) = (v * d).toInt()
    fun col(id: Int) = ContextCompat.getColor(ctx, id)
    val primary = android.util.TypedValue().let { ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, it, true); it.data }
    val monthNames = arrayOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    val today = Calendar.getInstance()

    var row: LinearLayout? = null
    for (m in 0 until 12) {
        if (m % 3 == 0) {
            row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            container.addView(row)
        }
        val isCurMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == m

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(6), px(8), px(6), px(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(4), px(4), px(4), px(4)) }
            isClickable = true
            setOnClickListener { onMonthClick(year, m) }
        }
        card.addView(TextView(ctx).apply {
            text = monthNames[m]; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(if (isCurMonth) primary else col(R.color.ink)); gravity = Gravity.START
            setPadding(px(2), 0, 0, px(4))
        })

        val grid = GridLayout(ctx).apply { columnCount = 7 }
        val cal = Calendar.getInstance().apply { clear(); set(year, m, 1) }
        val firstCol = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat(firstCol) {
            grid.addView(View(ctx).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = 0; height = px(16); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
            })
        }
        for (day in 1..daysInMonth) {
            val isToday = isCurMonth && today.get(Calendar.DAY_OF_MONTH) == day
            grid.addView(TextView(ctx).apply {
                text = day.toString(); textSize = 9f; gravity = Gravity.CENTER
                setTextColor(if (isToday) Color.WHITE else col(R.color.ink_muted))
                if (isToday) background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(primary) }
                layoutParams = GridLayout.LayoutParams().apply { width = 0; height = px(16); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
            })
        }
        card.addView(grid)
        row!!.addView(card)
    }
}
