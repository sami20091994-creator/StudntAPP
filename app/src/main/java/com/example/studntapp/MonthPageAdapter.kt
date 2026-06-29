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
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

/**
 * تقويم شهري قابل للتمرير الأفقي بين الأشهر (ViewPager2).
 * كل صفحة = شبكة شهر بأرقام الأيام + نقطة تحت أيام الأحداث. يحترم ألوان الثيم والوضع الداكن.
 */
class MonthPageAdapter(
    private val ctx: Context,
    private val onDayClick: (String) -> Unit
) : RecyclerView.Adapter<MonthPageAdapter.VH>() {

    companion object {
        const val COUNT = 2400
        const val CENTER = 1200
    }

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val ymf = SimpleDateFormat("yyyy-MM", Locale.ENGLISH)
    private val eventsByMonth = HashMap<String, Set<String>>()

    var selectedDate: String = sdf.format(Date())

    fun monthCal(pos: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, pos - CENTER)
    }

    fun ymOf(pos: Int): String = ymf.format(monthCal(pos).time)

    /** الصفحة المقابلة لشهر تاريخ معيّن. */
    fun pageForDate(dateStr: String): Int {
        val target = Calendar.getInstance().apply { time = sdf.parse(dateStr) ?: Date() }
        val now = Calendar.getInstance()
        val months = (target.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 12 +
            (target.get(Calendar.MONTH) - now.get(Calendar.MONTH))
        return CENTER + months
    }

    fun setMonthEvents(ym: String, days: Set<String>) {
        eventsByMonth[ym] = days
        notifyDataSetChanged()
    }

    private fun col(id: Int) = ContextCompat.getColor(ctx, id)
    private fun primary(): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }

    class VH(val grid: GridLayout) : RecyclerView.ViewHolder(grid)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val grid = GridLayout(ctx).apply {
            columnCount = 7
            // صفحات ViewPager2 يجب أن تملأ كامل الحاوية.
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT
            )
        }
        return VH(grid)
    }

    override fun getItemCount() = COUNT

    override fun onBindViewHolder(holder: VH, position: Int) {
        buildMonth(holder.grid, monthCal(position))
    }

    private fun buildMonth(grid: GridLayout, cal: Calendar) {
        grid.removeAllViews()
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val ym = ymf.format(cal.time)
        val events = eventsByMonth[ym] ?: emptySet()

        val heads = arrayOf("أحد", "اثنين", "ثلا", "أرب", "خمي", "جمع", "سبت")
        for (h in heads) grid.addView(TextView(ctx).apply {
            text = h; gravity = Gravity.CENTER; textSize = 11f
            setTextColor(col(R.color.ink_muted)); setPadding(0, px(8), 0, px(8))
            layoutParams = GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
        })

        val firstCol = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val ymStr = ymf.format(cal.time)

        repeat(firstCol) {
            grid.addView(View(ctx).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = 0; height = px(50); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
            })
        }
        for (day in 1..daysInMonth) {
            val dateStr = "$ymStr-${"%02d".format(day)}"
            val isSel = dateStr == selectedDate
            val hasEvent = events.contains(dateStr)
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = px(50); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(px(2), px(2), px(2), px(2))
                }
                isClickable = true
                if (isSel) background = GradientDrawable().apply { cornerRadius = px(14).toFloat(); setColor(primary()) }
            }
            cell.addView(TextView(ctx).apply {
                text = day.toString(); textSize = 16f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSel) Color.WHITE else col(R.color.ink))
            })
            cell.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(px(5), px(5)).apply { topMargin = px(3) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (isSel) Color.WHITE else primary()) }
                visibility = if (hasEvent) View.VISIBLE else View.INVISIBLE
            })
            cell.setOnClickListener {
                selectedDate = dateStr
                notifyDataSetChanged()
                onDayClick(dateStr)
            }
            grid.addView(cell)
        }
    }
}
