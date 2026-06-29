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
    // ym -> (date -> عناوين الأحداث)
    private val scheduleByMonth = HashMap<String, Map<String, List<String>>>()

    var selectedDate: String = sdf.format(Date())
    var expanded: Boolean = true // true = شهر كامل، false = صف الأسبوع

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

    fun setMonthSchedule(ym: String, byDate: Map<String, List<String>>) {
        scheduleByMonth[ym] = byDate
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
        val sched = scheduleByMonth[ym] ?: emptyMap()

        val heads = arrayOf("أحد", "اثنين", "ثلا", "أرب", "خمي", "جمع", "سبت")
        for (h in heads) grid.addView(TextView(ctx).apply {
            text = h; gravity = Gravity.CENTER; textSize = 11f
            setTextColor(col(R.color.ink_muted)); setPadding(0, px(8), 0, px(8))
            layoutParams = GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
        })

        val firstCol = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val curMonth = cal.get(Calendar.MONTH)

        // شبكة كاملة تشمل أيام الشهرين المجاورين (للتعبئة) بلون باهت.
        data class Cell(val date: String, val day: Int, val inMonth: Boolean)
        val total = ((firstCol + daysInMonth + 6) / 7) * 7
        val iter = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -firstCol) }
        val cells = ArrayList<Cell>(total)
        repeat(total) {
            cells.add(Cell(sdf.format(iter.time), iter.get(Calendar.DAY_OF_MONTH), iter.get(Calendar.MONTH) == curMonth))
            iter.add(Calendar.DAY_OF_MONTH, 1)
        }
        val weeks = cells.chunked(7)

        // في الوضع المطويّ نعرض أسبوع اليوم المحدّد فقط (أو الأول).
        val rendered = if (expanded) weeks
            else listOf(weeks.firstOrNull { wk -> wk.any { it.date == selectedDate } } ?: weeks.first())

        for (wk in rendered) for (c in wk) {
            val isSel = c.date == selectedDate && c.inMonth
            val titles = if (c.inMonth) sched[c.date] ?: emptyList() else emptyList()
            val hasEvent = titles.isNotEmpty()
            val cellH = if (expanded) px(54) else px(50)
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = cellH; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(px(2), px(2), px(2), px(2))
                }
                isClickable = true
            }
            // مربع الاختيار: رقم اليوم مُحاذى في وسطه تماماً.
            val numBox = android.widget.FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(px(32), px(32))
                if (isSel) background = GradientDrawable().apply { cornerRadius = px(16).toFloat(); setColor(primary()) }
                addView(TextView(ctx).apply {
                    text = c.day.toString(); textSize = 15f; setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                    setTextColor(when {
                        isSel -> Color.WHITE
                        !c.inMonth -> col(R.color.ink_faint)
                        else -> col(R.color.ink)
                    })
                })
            }
            cell.addView(numBox)

            if (expanded) {
                // عند التوسعة: عرض الأحداث كنص بدلاً من النقطة.
                if (hasEvent) cell.addView(TextView(ctx).apply {
                    text = if (titles.size > 1) "${titles[0]} +${titles.size - 1}" else titles[0]
                    textSize = 8f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER; setTextColor(primary())
                    setPadding(px(2), px(1), px(2), 0)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
            } else {
                // عند الطيّ: نقطة فقط.
                cell.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(px(5), px(5)).apply { topMargin = px(3) }
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(primary()) }
                    visibility = if (hasEvent) View.VISIBLE else View.INVISIBLE
                })
            }
            cell.setOnClickListener {
                selectedDate = c.date
                notifyDataSetChanged()
                onDayClick(c.date)
            }
            grid.addView(cell)
        }
    }
}
