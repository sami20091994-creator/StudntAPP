package com.example.studntapp

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : BaseActivity() {

    private var selectedDateStr = ""
    private var currentViewMode = "daily"
    private var role = ""
    private var userId = 0

    private lateinit var rvDaily: RecyclerView
    private lateinit var calendarView: CalendarView
    private lateinit var viewDaily: LinearLayout
    private lateinit var viewWeekly: ScrollView
    private lateinit var viewMonthly: ScrollView
    private lateinit var weeklyGrid: LinearLayout
    private lateinit var monthlyGrid: GridLayout
    private lateinit var layoutNavHeader: LinearLayout
    private lateinit var tvPeriodName: TextView
    private lateinit var tvDailyTitle: TextView

    private val sdfApi = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("ar"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        supportActionBar?.title = "الجدول الزمني"

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        userId = prefs.getInt("USER_ID", 0)
        role = prefs.getString("USER_ROLE", "student") ?: "student"

        selectedDateStr = sdfApi.format(Date())

        // ربط العناصر
        rvDaily = findViewById(R.id.rvDaily)
        rvDaily.layoutManager = LinearLayoutManager(this)
        calendarView = findViewById(R.id.calendarView)
        viewDaily = findViewById(R.id.viewDaily)
        viewWeekly = findViewById(R.id.viewWeekly)
        viewMonthly = findViewById(R.id.viewMonthly)
        weeklyGrid = findViewById(R.id.weeklyGrid)
        monthlyGrid = findViewById(R.id.monthlyGrid)
        layoutNavHeader = findViewById(R.id.layoutNavHeader)
        tvPeriodName = findViewById(R.id.tvPeriodName)
        tvDailyTitle = findViewById(R.id.tvDailyTitle)

        // إعداد أزرار التبديل
        findViewById<TextView>(R.id.btnDaily).setOnClickListener { switchViewMode("daily") }
        findViewById<TextView>(R.id.btnWeekly).setOnClickListener { switchViewMode("weekly") }
        findViewById<TextView>(R.id.btnMonthly).setOnClickListener { switchViewMode("monthly") }

        // أزرار التنقل (السابق / التالي) للأسبوعي والشهري
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { adjustDate(-1) }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { adjustDate(1) }

        // تقويم العرض اليومي
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val m = if (month + 1 < 10) "0${month + 1}" else "${month + 1}"
            val d = if (dayOfMonth < 10) "0$dayOfMonth" else "$dayOfMonth"
            selectedDateStr = "$year-$m-$d"
            tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
            loadSchedule()
        }

        switchViewMode("daily")
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
        
        val btnD = findViewById<TextView>(R.id.btnDaily)
        val btnW = findViewById<TextView>(R.id.btnWeekly)
        val btnM = findViewById<TextView>(R.id.btnMonthly)

        btnD.apply { 
            background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_inactive)
            setTextColor(ContextCompat.getColor(context, R.color.text_grey)) 
        }
        btnW.apply { 
            background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_inactive)
            setTextColor(ContextCompat.getColor(context, R.color.text_grey)) 
        }
        btnM.apply { 
            background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_inactive)
            setTextColor(ContextCompat.getColor(context, R.color.text_grey)) 
        }

        viewDaily.visibility = View.GONE
        viewWeekly.visibility = View.GONE
        viewMonthly.visibility = View.GONE
        layoutNavHeader.visibility = View.GONE

        when (mode) {
            "daily" -> {
                btnD.apply { 
                    background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_active)
                    setTextColor(Color.WHITE) 
                }
                viewDaily.visibility = View.VISIBLE
                val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
                calendarView.date = cal.timeInMillis
                tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
            }
            "weekly" -> {
                btnW.apply { 
                    background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_active)
                    setTextColor(Color.WHITE) 
                }
                viewWeekly.visibility = View.VISIBLE
                layoutNavHeader.visibility = View.VISIBLE
            }
            "monthly" -> {
                btnM.apply { 
                    background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_active)
                    setTextColor(Color.WHITE) 
                }
                viewMonthly.visibility = View.VISIBLE
                layoutNavHeader.visibility = View.VISIBLE
            }
        }
        updateHeaderTitles()
        loadSchedule()
    }

    private fun updateHeaderTitles() {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        if (currentViewMode == "weekly") {
            tvPeriodName.text = "أسبوع: ${selectedDateStr}"
        } else if (currentViewMode == "monthly") {
            tvPeriodName.text = monthFormat.format(cal.time)
        }
    }

    private fun loadSchedule() {
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = selectedDateStr, viewMode = currentViewMode
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
                val list = response.body() ?: emptyList()
                when (currentViewMode) {
                    "daily" -> {
                        val sorted = list.sortedBy { it.startTime }
                        rvDaily.adapter = CalendarAdapter(sorted, selectedDateStr)
                    }
                    "weekly" -> renderWeeklyGrid(list)
                    "monthly" -> renderMonthlyGrid(list)
                }
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) {
                Toast.makeText(this@CalendarActivity, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ========================================================
    // نظام التوليد المخصص للجدول الأسبوعي (يشبه تصميم الويب)
    // ========================================================
    private fun renderWeeklyGrid(schedule: List<ScheduleData>) {
        weeklyGrid.removeAllViews()
        val startHour = 8
        val endHour = 20 // من 8 صباحاً للـ 8 مساءً
        val dpToPx = resources.displayMetrics.density
        val hourHeightPx = (60 * dpToPx).toInt() // كل ساعة = 60dp ارتفاع
        val dayWidthPx = (110 * dpToPx).toInt()
        val timeWidthPx = (50 * dpToPx).toInt()

        // 1. إنشاء عمود الوقت (على اليمين)
        val timeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(timeWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.parseColor("#F8FAFC")) // background_light
        }
        timeCol.addView(TextView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (50 * dpToPx).toInt()) }) // مساحة رأسية فارغة

        for (h in startHour..endHour) {
            val amPm = if (h < 12) "ص" else if (h == 12) "م" else "م"
            val displayH = if (h <= 12) h else h - 12
            val tv = TextView(this).apply {
                text = "$displayH:00 $amPm"
                textSize = 10f
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, hourHeightPx)
                setTextColor(Color.parseColor("#64748B")) // text_grey
            }
            timeCol.addView(tv)
        }
        weeklyGrid.addView(timeCol)

        // 2. إنشاء أعمدة الأيام (الأحد -> السبت)
        val daysOfWeek = arrayOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        for (i in 0..6) {
            val dayDateStr = sdfApi.format(cal.time)

            val dayCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(dayWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 2 }
            }

            // رأس اليوم (تاريخ + اسم)
            val header = TextView(this).apply {
                text = "${daysOfWeek[i]}\n${cal.get(Calendar.DAY_OF_MONTH)}"
                gravity = Gravity.CENTER
                textSize = 12f
                textStyle = android.graphics.Typeface.BOLD
                setTextColor(Color.parseColor("#1E293B")) // text_dark
                setBackgroundColor(Color.parseColor("#E2E8F0")) // divider
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (50 * dpToPx).toInt())
            }
            dayCol.addView(header)

            // إطار يحمل الحصص داخله
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (endHour - startHour + 1) * hourHeightPx)
                setBackgroundColor(Color.WHITE)
            }

            // رسم خطوط الساعات في الخلفية
            for (h in startHour..endHour) {
                val line = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, 1).apply {
                        topMargin = (h - startHour) * hourHeightPx
                    }
                    setBackgroundColor(Color.parseColor("#E2E8F0")) // divider
                }
                frame.addView(line)
            }

            // رسم الحصص لهذا اليوم
            val dayClasses = schedule.filter { it.startDate == dayDateStr }
            for (c in dayClasses) {
                val parts = c.startTime?.split(":") ?: listOf("0", "0")
                val h = parts[0].toInt()
                val m = parts[1].toInt()

                val endParts = c.endTime?.split(":") ?: listOf("0", "0")
                val endH = endParts[0].toInt()
                val endM = endParts[1].toInt()

                // حساب موقع الحصة (MarginTop) من بداية اليوم (8 صباحاً)
                val topMarginMins = (h - startHour) * 60 + m
                val topMarginPx = (topMarginMins * (hourHeightPx / 60.0)).toInt()

                val durationMins = (endH * 60 + endM) - (h * 60 + m)
                val heightPx = (durationMins * (hourHeightPx / 60.0)).toInt()

                val classView = TextView(this).apply {
                    text = "${c.subjectName}\n${c.classroom}"
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    setPadding(8, 8, 8, 8)
                    gravity = Gravity.CENTER_HORIZONTAL

                    val bg = GradientDrawable()
                    bg.setColor(Color.parseColor("#002D56")) // primary_blue
                    bg.cornerRadius = 8f
                    background = bg

                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, heightPx).apply {
                        topMargin = topMarginPx
                        setMargins(8, topMarginPx, 8, 0)
                    }
                }
                frame.addView(classView)
            }
            dayCol.addView(frame)
            weeklyGrid.addView(dayCol)

            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    // ========================================================
    // نظام التوليد المخصص للجدول الشهري (7 أعمدة)
    // ========================================================
    private fun renderMonthlyGrid(schedule: List<ScheduleData>) {
        monthlyGrid.removeAllViews()

        val daysOfWeek = arrayOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

        // رسم رؤوس الأيام
        for (day in daysOfWeek) {
            val tv = TextView(this).apply {
                text = day
                gravity = Gravity.CENTER
                textSize = 12f
                textStyle = android.graphics.Typeface.BOLD
                setBackgroundColor(Color.parseColor("#E2E8F0"))
                setPadding(0, 20, 0, 20)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(1, 1, 1, 1)
                }
            }
            monthlyGrid.addView(tv)
        }

        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sunday
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // إضافة المربعات الفارغة لبداية الشهر
        for (i in 0 until firstDayOfWeek) {
            val emptyView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = (80 * resources.displayMetrics.density).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(1, 1, 1, 1)
                }
                setBackgroundColor(Color.parseColor("#F8FAFC"))
            }
            monthlyGrid.addView(emptyView)
        }

        // إضافة أيام الشهر والحصص
        for (day in 1..daysInMonth) {
            val cellDate = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day)
            val dayClasses = schedule.filter { it.startDate == cellDate }

            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(4, 4, 4, 4)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = (80 * resources.displayMetrics.density).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(1, 1, 1, 1)
                }
            }

            // رقم اليوم
            cell.addView(TextView(this).apply {
                text = day.toString()
                textSize = 12f
                textStyle = android.graphics.Typeface.BOLD
                setTextColor(Color.parseColor("#2d3436"))
            })

            // إضافة نقطة أو نص صغير للحصص (الحد الأقصى 2 لكي لا تتشوه الشاشة)
            for (c in dayClasses.take(2)) {
                val classTv = TextView(this).apply {
                    text = c.subjectName
                    textSize = 8f
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setBackgroundColor(Color.parseColor("#0984e3"))
                    setPadding(4, 2, 4, 2)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 2 }
                }
                cell.addView(classTv)
            }
            if (dayClasses.size > 2) {
                cell.addView(TextView(this).apply { text = "+${dayClasses.size - 2}"; textSize = 8f; gravity = Gravity.CENTER })
            }

            monthlyGrid.addView(cell)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
    private var TextView.textStyle: Int
        get() = typeface?.style ?: 0
        set(value) { setTypeface(typeface, value) }
}