package com.example.studntapp

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

/** صفحة الجدول الزمني داخل الـ ViewPager (يومي/أسبوعي/شهري). */
class CalendarFragment : Fragment() {

    private var selectedDateStr = ""
    private var currentViewMode = "daily"
    private var role = ""
    private var userId = 0

    private lateinit var rvDaily: RecyclerView
    private lateinit var viewDaily: LinearLayout
    private lateinit var viewWeekly: ScrollView
    private lateinit var viewMonthly: ScrollView
    private lateinit var weeklyGrid: LinearLayout
    private lateinit var monthlyGrid: GridLayout
    private lateinit var layoutNavHeader: LinearLayout
    private lateinit var tvPeriodName: TextView
    private lateinit var tvDailyTitle: TextView
    private lateinit var dailyCalGrid: GridLayout
    private lateinit var tvDailyMonth: TextView
    private val eventDays = mutableSetOf<String>()
    private lateinit var root: View

    private val sdfApi = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("ar"))

    private val ctx get() = requireContext()
    private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun col(id: Int) = ContextCompat.getColor(ctx, id)
    private fun primaryColor(): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        root = inflater.inflate(R.layout.activity_calendar, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = ctx.getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        userId = prefs.getInt("USER_ID", 0)
        role = prefs.getString("USER_ROLE", "student") ?: "student"
        selectedDateStr = sdfApi.format(Date())

        rvDaily = view.findViewById(R.id.rvDaily)
        rvDaily.layoutManager = LinearLayoutManager(ctx)
        viewDaily = view.findViewById(R.id.viewDaily)
        viewWeekly = view.findViewById(R.id.viewWeekly)
        viewMonthly = view.findViewById(R.id.viewMonthly)
        weeklyGrid = view.findViewById(R.id.weeklyGrid)
        monthlyGrid = view.findViewById(R.id.monthlyGrid)
        layoutNavHeader = view.findViewById(R.id.layoutNavHeader)
        tvPeriodName = view.findViewById(R.id.tvPeriodName)
        tvDailyTitle = view.findViewById(R.id.tvDailyTitle)
        dailyCalGrid = view.findViewById(R.id.dailyCalGrid)
        tvDailyMonth = view.findViewById(R.id.tvDailyMonth)

        view.findViewById<TextView>(R.id.btnDaily).setOnClickListener { switchViewMode("daily") }
        view.findViewById<TextView>(R.id.btnWeekly).setOnClickListener { switchViewMode("weekly") }
        view.findViewById<TextView>(R.id.btnMonthly).setOnClickListener { switchViewMode("monthly") }

        view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { adjustDate(-1) }
        view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener { adjustDate(1) }
        view.findViewById<ImageButton>(R.id.btnDailyPrev).setOnClickListener { shiftDailyMonth(-1) }
        view.findViewById<ImageButton>(R.id.btnDailyNext).setOnClickListener { shiftDailyMonth(1) }

        switchViewMode("daily")
    }

    private fun primaryCol() = primaryColor()

    private fun loadMonthEvents() {
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = selectedDateStr, viewMode = "monthly"
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
                if (!isAdded) return
                eventDays.clear()
                response.body()?.forEach { it.startDate?.let { d -> if (d.length >= 10) eventDays.add(d.substring(0, 10)) } }
                buildDailyCalendar()
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) { if (isAdded) buildDailyCalendar() }
        })
    }

    private fun shiftDailyMonth(amount: Int) {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        cal.add(Calendar.MONTH, amount)
        selectedDateStr = sdfApi.format(cal.time)
        loadMonthEvents()
    }

    private fun buildDailyCalendar() {
        dailyCalGrid.removeAllViews()
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        tvDailyMonth.text = monthFormat.format(sdfApi.parse(selectedDateStr)!!)

        val heads = arrayOf("أحد", "إثنين", "ثلا", "أرب", "خمي", "جمع", "سبت")
        for (h in heads) dailyCalGrid.addView(TextView(ctx).apply {
            text = h; gravity = Gravity.CENTER; textSize = 11f
            setTextColor(col(R.color.ink_muted)); setPadding(0, px(6), 0, px(6))
            layoutParams = GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
        })

        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!!; set(Calendar.DAY_OF_MONTH, 1) }
        val firstCol = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val ym = SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(cal.time)

        repeat(firstCol) {
            dailyCalGrid.addView(View(ctx).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = 0; height = px(48); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
            })
        }
        for (day in 1..daysInMonth) {
            val dateStr = "$ym-${"%02d".format(day)}"
            val isSel = dateStr == selectedDateStr
            val hasEvent = eventDays.contains(dateStr)
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = px(48); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(px(2), px(2), px(2), px(2))
                }
                isClickable = true
                if (isSel) background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(primaryCol()) }
            }
            cell.addView(TextView(ctx).apply {
                text = day.toString(); textSize = 15f; setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (isSel) col(R.color.white) else col(R.color.ink))
            })
            cell.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(px(5), px(5)).apply { topMargin = px(3) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (isSel) col(R.color.white) else primaryCol()) }
                visibility = if (hasEvent) View.VISIBLE else View.INVISIBLE
            })
            cell.setOnClickListener {
                selectedDateStr = dateStr
                tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
                loadSchedule(); buildDailyCalendar()
            }
            dailyCalGrid.addView(cell)
        }
    }

    private fun adjustDate(amount: Int) {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        if (currentViewMode == "weekly") cal.add(Calendar.WEEK_OF_YEAR, amount)
        else if (currentViewMode == "monthly") cal.add(Calendar.MONTH, amount)
        selectedDateStr = sdfApi.format(cal.time)
        updateHeaderTitles()
        loadSchedule()
    }

    private fun switchViewMode(mode: String) {
        currentViewMode = mode
        val btnD = root.findViewById<TextView>(R.id.btnDaily)
        val btnW = root.findViewById<TextView>(R.id.btnWeekly)
        val btnM = root.findViewById<TextView>(R.id.btnMonthly)

        btnD.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_inactive); setTextColor(col(R.color.ink_muted)) }
        btnW.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_inactive); setTextColor(col(R.color.ink_muted)) }
        btnM.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_inactive); setTextColor(col(R.color.ink_muted)) }

        viewDaily.visibility = View.GONE
        viewWeekly.visibility = View.GONE
        viewMonthly.visibility = View.GONE
        layoutNavHeader.visibility = View.GONE

        when (mode) {
            "daily" -> {
                btnD.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewDaily.visibility = View.VISIBLE
                tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
                loadMonthEvents()
            }
            "weekly" -> {
                btnW.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewWeekly.visibility = View.VISIBLE
                layoutNavHeader.visibility = View.VISIBLE
            }
            "monthly" -> {
                btnM.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewMonthly.visibility = View.VISIBLE
                layoutNavHeader.visibility = View.VISIBLE
            }
        }
        updateHeaderTitles()
        loadSchedule()
    }

    private fun updateHeaderTitles() {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        if (currentViewMode == "weekly") tvPeriodName.text = "أسبوع: $selectedDateStr"
        else if (currentViewMode == "monthly") tvPeriodName.text = monthFormat.format(cal.time)
    }

    private fun loadSchedule() {
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = selectedDateStr, viewMode = currentViewMode
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
                if (!isAdded) return
                val list = response.body() ?: emptyList()
                when (currentViewMode) {
                    "daily" -> rvDaily.adapter = CalendarAdapter(list.sortedBy { it.startTime }, selectedDateStr)
                    "weekly" -> renderWeeklyGrid(list)
                    "monthly" -> renderMonthlyGrid(list)
                }
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(ctx, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun renderWeeklyGrid(schedule: List<ScheduleData>) {
        weeklyGrid.removeAllViews()
        val startHour = 8
        val endHour = 20
        val dpToPx = resources.displayMetrics.density
        val hourHeightPx = (60 * dpToPx).toInt()
        val dayWidthPx = (110 * dpToPx).toInt()
        val timeWidthPx = (50 * dpToPx).toInt()

        val timeCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(timeWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(col(R.color.canvas))
        }
        timeCol.addView(TextView(ctx).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (50 * dpToPx).toInt()) })

        for (h in startHour..endHour) {
            val amPm = if (h < 12) "ص" else "م"
            val displayH = if (h <= 12) h else h - 12
            timeCol.addView(TextView(ctx).apply {
                text = "$displayH:00 $amPm"
                textSize = 11f
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, hourHeightPx)
                setTextColor(col(R.color.ink_muted))
            })
        }
        weeklyGrid.addView(timeCol)

        val daysOfWeek = arrayOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        for (i in 0..6) {
            val dayDateStr = sdfApi.format(cal.time)
            val dayCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(dayWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 2 }
            }

            dayCol.addView(TextView(ctx).apply {
                text = "${daysOfWeek[i]}\n${cal.get(Calendar.DAY_OF_MONTH)}"
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(col(R.color.ink))
                setBackgroundColor(col(R.color.surface_alt))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (50 * dpToPx).toInt())
            })

            val frame = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (endHour - startHour + 1) * hourHeightPx)
                setBackgroundColor(col(R.color.surface))
            }

            for (h in startHour..endHour) {
                frame.addView(View(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = (h - startHour) * hourHeightPx }
                    setBackgroundColor(col(R.color.line))
                })
            }

            val dayClasses = schedule.filter { it.startDate == dayDateStr }
            for (c in dayClasses) {
                val parts = c.startTime?.split(":") ?: listOf("0", "0")
                val h = parts[0].toInt(); val m = parts[1].toInt()
                val endParts = c.endTime?.split(":") ?: listOf("0", "0")
                val endH = endParts[0].toInt(); val endM = endParts[1].toInt()
                val topMarginMins = (h - startHour) * 60 + m
                val topMarginPx = (topMarginMins * (hourHeightPx / 60.0)).toInt()
                val durationMins = (endH * 60 + endM) - (h * 60 + m)
                val heightPx = (durationMins * (hourHeightPx / 60.0)).toInt()

                frame.addView(TextView(ctx).apply {
                    text = "${c.subjectName}\n${c.classroom}"
                    textSize = 11f
                    setTextColor(col(R.color.white))
                    setPadding(8, 8, 8, 8)
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = GradientDrawable().apply { setColor(primaryColor()); cornerRadius = 12f }
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, heightPx).apply { setMargins(8, topMarginPx, 8, 0) }
                })
            }
            dayCol.addView(frame)
            weeklyGrid.addView(dayCol)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun renderMonthlyGrid(schedule: List<ScheduleData>) {
        monthlyGrid.removeAllViews()
        val daysOfWeek = arrayOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

        for (day in daysOfWeek) {
            monthlyGrid.addView(TextView(ctx).apply {
                text = day
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(col(R.color.ink_muted))
                setBackgroundColor(col(R.color.surface_alt))
                setPadding(0, 20, 0, 20)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(1, 1, 1, 1)
                }
            })
        }

        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 0 until firstDayOfWeek) {
            monthlyGrid.addView(View(ctx).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = (80 * resources.displayMetrics.density).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(1, 1, 1, 1)
                }
                setBackgroundColor(col(R.color.canvas))
            })
        }

        for (day in 1..daysInMonth) {
            val cellDate = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day)
            val dayClasses = schedule.filter { it.startDate == cellDate }
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(col(R.color.surface))
                setPadding(4, 4, 4, 4)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = (80 * resources.displayMetrics.density).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(1, 1, 1, 1)
                }
            }
            cell.addView(TextView(ctx).apply {
                text = day.toString()
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(col(R.color.ink))
            })
            for (c in dayClasses.take(2)) {
                cell.addView(TextView(ctx).apply {
                    text = c.subjectName
                    textSize = 9f
                    setTextColor(col(R.color.white))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    background = GradientDrawable().apply { setColor(primaryColor()); cornerRadius = 6f }
                    setPadding(6, 2, 6, 2)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 2 }
                })
            }
            if (dayClasses.size > 2) {
                cell.addView(TextView(ctx).apply { text = "+${dayClasses.size - 2}"; textSize = 8f; gravity = Gravity.CENTER; setTextColor(col(R.color.ink_muted)) })
            }
            monthlyGrid.addView(cell)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}
