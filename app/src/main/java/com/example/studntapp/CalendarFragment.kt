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
    private lateinit var viewYearly: ScrollView
    private lateinit var yearContainer: LinearLayout
    private lateinit var weeklyGrid: LinearLayout
    private lateinit var monthlyGrid: GridLayout
    private lateinit var layoutNavHeader: LinearLayout
    private lateinit var tvPeriodName: TextView
    private lateinit var tvDailyTitle: TextView
    private lateinit var monthPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var monthAdapter: MonthPageAdapter
    private lateinit var tvDailyMonth: TextView
    private lateinit var tvDayCardYM: TextView
    private lateinit var tvDayCardNum: TextView
    private lateinit var tvDayCardWeek: TextView
    private lateinit var calBlock: LinearLayout
    private lateinit var dayCard: androidx.cardview.widget.CardView
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
        viewYearly = view.findViewById(R.id.viewYearly)
        yearContainer = view.findViewById(R.id.yearContainer)
        weeklyGrid = view.findViewById(R.id.weeklyGrid)
        monthlyGrid = view.findViewById(R.id.monthlyGrid)
        layoutNavHeader = view.findViewById(R.id.layoutNavHeader)
        tvPeriodName = view.findViewById(R.id.tvPeriodName)
        tvDailyTitle = view.findViewById(R.id.tvDailyTitle)
        monthPager = view.findViewById(R.id.monthPager)
        tvDailyMonth = view.findViewById(R.id.tvDailyMonth)
        tvDayCardYM = view.findViewById(R.id.tvDayCardYM)
        tvDayCardNum = view.findViewById(R.id.tvDayCardNum)
        tvDayCardWeek = view.findViewById(R.id.tvDayCardWeek)
        calBlock = view.findViewById(R.id.calBlock)
        dayCard = view.findViewById(R.id.dayCard)
        setupMonthPager()
        updateDayCard()

        view.findViewById<TextView>(R.id.btnDaily).setOnClickListener { switchViewMode("daily") }
        view.findViewById<TextView>(R.id.btnWeekly).setOnClickListener { switchViewMode("weekly") }
        view.findViewById<TextView>(R.id.btnMonthly).setOnClickListener { switchViewMode("monthly") }
        view.findViewById<TextView>(R.id.btnYearly).setOnClickListener { switchViewMode("yearly") }

        view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { adjustDate(-1) }
        view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener { adjustDate(1) }
        view.findViewById<ImageButton>(R.id.btnDailyPrev).setOnClickListener { monthPager.currentItem = monthPager.currentItem - 1 }
        view.findViewById<ImageButton>(R.id.btnDailyNext).setOnClickListener { monthPager.currentItem = monthPager.currentItem + 1 }

        switchViewMode("monthly")
    }

    private fun setupMonthPager() {
        monthAdapter = MonthPageAdapter(ctx) { date ->
            selectedDateStr = date
            tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
            updateDayCard()
            loadSchedule()
        }
        monthAdapter.selectedDate = selectedDateStr
        monthPager.adapter = monthAdapter
        monthPager.isUserInputEnabled = false
        monthPager.setCurrentItem(MonthPageAdapter.CENTER, false)
        tvDailyMonth.text = monthFormat.format(sdfApi.parse(selectedDateStr)!!)
        monthPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvDailyMonth.text = monthFormat.format(monthAdapter.monthCal(position).time)
                fetchMonthEvents(monthAdapter.ymOf(position))
            }
        })
        fetchMonthEvents(monthAdapter.ymOf(MonthPageAdapter.CENTER))
        setupCalCollapse()
    }

    private val calExpandedH get() = (380 * resources.displayMetrics.density).toInt()
    private val calCollapsedH get() = (110 * resources.displayMetrics.density).toInt()

    private fun setupCalCollapse() {
        val handle = root.findViewById<View>(R.id.calExpandHandle)
        val gd = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onScroll(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, dx: Float, dy: Float): Boolean {
                if (dy > 12 && monthAdapter.expanded) setCalExpanded(false)
                else if (dy < -12 && !monthAdapter.expanded) setCalExpanded(true)
                return true
            }
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean { setCalExpanded(!monthAdapter.expanded); return true }
        })
        handle.setOnTouchListener { _, ev -> gd.onTouchEvent(ev); true }
    }

    private fun setCalExpanded(expand: Boolean) {
        if (monthAdapter.expanded == expand) return
        monthAdapter.expanded = expand
        monthAdapter.notifyDataSetChanged()
        val from = monthPager.layoutParams.height
        val to = if (expand) calExpandedH else calCollapsedH
        android.animation.ValueAnimator.ofInt(from, to).apply {
            duration = 260
            addUpdateListener {
                monthPager.layoutParams = monthPager.layoutParams.apply { height = it.animatedValue as Int }
            }
            start()
        }
    }

    private fun updateDayCard() {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        tvDayCardYM.text = "${cal.get(Calendar.YEAR)} / ${cal.get(Calendar.MONTH) + 1}"
        tvDayCardNum.text = cal.get(Calendar.DAY_OF_MONTH).toString()
        tvDayCardWeek.text = SimpleDateFormat("EEEE", Locale("ar")).format(cal.time)
    }

    private fun fetchMonthEvents(ym: String) {
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = "$ym-01", viewMode = "monthly"
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
                if (!isAdded) return
                val byDate = HashMap<String, MutableList<String>>()
                response.body()?.forEach { ev ->
                    ev.startDate?.let { d ->
                        if (d.length >= 10) byDate.getOrPut(d.substring(0, 10)) { mutableListOf() }
                            .add(ev.subjectName ?: "حصة")
                    }
                }
                monthAdapter.setMonthSchedule(ym, byDate)
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) {}
        })
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

        val btnY = root.findViewById<TextView>(R.id.btnYearly)
        listOf(btnD, btnW, btnM, btnY).forEach {
            it.background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_inactive); it.setTextColor(col(R.color.ink_muted))
        }

        viewDaily.visibility = View.GONE
        viewWeekly.visibility = View.GONE
        viewMonthly.visibility = View.GONE
        viewYearly.visibility = View.GONE
        layoutNavHeader.visibility = View.GONE

        when (mode) {
            "daily" -> {
                btnD.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewDaily.visibility = View.VISIBLE
                calBlock.visibility = View.GONE
                dayCard.visibility = View.VISIBLE
                tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
            }
            "weekly" -> {
                btnW.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewWeekly.visibility = View.VISIBLE
                layoutNavHeader.visibility = View.VISIBLE
            }
            "monthly" -> {
                btnM.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewDaily.visibility = View.VISIBLE
                calBlock.visibility = View.VISIBLE
                dayCard.visibility = View.GONE
                tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
            }
            "yearly" -> {
                btnY.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewYearly.visibility = View.VISIBLE
                val year = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }.get(Calendar.YEAR)
                renderYearView(ctx, yearContainer, year) { y, mIdx ->
                    val c = Calendar.getInstance().apply { clear(); set(y, mIdx, 1) }
                    selectedDateStr = sdfApi.format(c.time)
                    monthAdapter.selectedDate = selectedDateStr
                    monthPager.setCurrentItem(monthAdapter.pageForDate(selectedDateStr), false)
                    switchViewMode("daily")
                    loadSchedule()
                }
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
        val fetchMode = if (currentViewMode == "monthly") "daily" else currentViewMode
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = selectedDateStr, viewMode = fetchMode
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
                if (!isAdded) return
                val list = response.body() ?: emptyList()
                when (currentViewMode) {
                    "daily", "monthly" -> rvDaily.adapter = CalendarAdapter(list.sortedBy { it.startTime }, selectedDateStr)
                    "weekly" -> renderWeeklyGrid(list)
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
        val timeWidthPx = (44 * dpToPx).toInt()
        val padPx = (16 * dpToPx).toInt()
        val dayWidthPx = ((resources.displayMetrics.widthPixels - timeWidthPx - padPx) / 7) - (2 * dpToPx).toInt()

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
