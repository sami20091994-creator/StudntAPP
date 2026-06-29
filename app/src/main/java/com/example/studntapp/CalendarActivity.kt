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
    private lateinit var rvDayEvents: RecyclerView
    private lateinit var viewDaily: LinearLayout
    private lateinit var viewWeekly: ScrollView
    private lateinit var viewMonthly: ScrollView
    private lateinit var viewYearly: androidx.viewpager2.widget.ViewPager2
    private lateinit var yearAdapter: YearPagerAdapter
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
    private lateinit var fabToday: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var swipeNav: SwipeNavLayout

    private val sdfApi = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("ar"))
    // تنسيق عربي موحّد لشريط التاريخ في كل الأوضاع (يومي/أسبوعي/شهري).
    private val prettyFormat = SimpleDateFormat("d MMMM yyyy", Locale("ar"))
    private fun pretty(dateStr: String): String =
        try { sdfApi.parse(dateStr)?.let { prettyFormat.format(it) } ?: dateStr } catch (e: Exception) { dateStr }

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
        rvDayEvents = findViewById(R.id.rvDayEvents)
        rvDayEvents.layoutManager = LinearLayoutManager(this)
        viewDaily = findViewById(R.id.viewDaily)
        viewWeekly = findViewById(R.id.viewWeekly)
        viewMonthly = findViewById(R.id.viewMonthly)
        viewYearly = findViewById(R.id.viewYearly)
        setupYearPager()
        weeklyGrid = findViewById(R.id.weeklyGrid)
        monthlyGrid = findViewById(R.id.monthlyGrid)
        layoutNavHeader = findViewById(R.id.layoutNavHeader)
        tvPeriodName = findViewById(R.id.tvPeriodName)
        tvDailyTitle = findViewById(R.id.tvDailyTitle)
        monthPager = findViewById(R.id.monthPager)
        tvDailyMonth = findViewById(R.id.tvDailyMonth)
        tvDayCardYM = findViewById(R.id.tvDayCardYM)
        tvDayCardNum = findViewById(R.id.tvDayCardNum)
        tvDayCardWeek = findViewById(R.id.tvDayCardWeek)
        calBlock = findViewById(R.id.calBlock)
        dayCard = findViewById(R.id.dayCard)
        setupMonthPager()
        updateDayCard()

        // إعداد أزرار التبديل
        findViewById<TextView>(R.id.btnDaily).setOnClickListener { switchViewMode("daily") }
        findViewById<TextView>(R.id.btnWeekly).setOnClickListener { switchViewMode("weekly") }
        findViewById<TextView>(R.id.btnMonthly).setOnClickListener { switchViewMode("monthly") }
        findViewById<TextView>(R.id.btnYearly).setOnClickListener { switchViewMode("yearly") }

        // أزرار التنقل (السابق / التالي) للأسبوعي والشهري
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { adjustDate(-1) }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { adjustDate(1) }

        // سحب أفقي موحّد فوق أي عنصر → تنقّل + انزلاق الصفحة كاملة.
        swipeNav = findViewById(R.id.swipeNav)
        swipeNav.onSwipe = { dir ->
            when (currentViewMode) {
                "daily" -> shiftDay(dir)
                "weekly" -> { adjustDate(dir); slideIn(viewWeekly, dir) }
                // الشهري والسنوي يعتمدان سحب الـViewPager الأصلي.
            }
        }

        fabToday = findViewById(R.id.fabToday)
        fabToday.setOnClickListener { goToToday() }

        switchViewMode("monthly")
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
        // الشهري يعتمد سحب الـViewPager الأصلي؛ بقية الأوضاع تعتمد حاوية السحب الموحّدة.
        if (::swipeNav.isInitialized) swipeNav.interceptEnabled = mode != "monthly" && mode != "yearly"

        val btnD = findViewById<TextView>(R.id.btnDaily)
        val btnW = findViewById<TextView>(R.id.btnWeekly)
        val btnM = findViewById<TextView>(R.id.btnMonthly)
        val btnY = findViewById<TextView>(R.id.btnYearly)

        listOf(btnD, btnW, btnM, btnY).forEach {
            it.background = ContextCompat.getDrawable(this, R.drawable.bg_toggle_inactive)
            it.setTextColor(ContextCompat.getColor(this, R.color.text_grey))
        }

        viewDaily.visibility = View.GONE
        viewWeekly.visibility = View.GONE
        viewMonthly.visibility = View.GONE
        viewYearly.visibility = View.GONE
        layoutNavHeader.visibility = View.GONE

        when (mode) {
            "daily" -> {
                btnD.apply {
                    background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_active)
                    setTextColor(Color.WHITE)
                }
                // يومي: بطاقة اليوم الكبيرة والأحداث بداخلها (بلا تقويم ولا قائمة خارجية).
                viewDaily.visibility = View.VISIBLE
                calBlock.visibility = View.GONE
                dayCard.visibility = View.VISIBLE
                tvDailyTitle.visibility = View.GONE
                rvDaily.visibility = View.GONE
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
                // شهري: التقويم القابل للتمرير + قائمة حصص اليوم المحدّد، بدون بطاقة اليوم.
                viewDaily.visibility = View.VISIBLE
                calBlock.visibility = View.VISIBLE
                dayCard.visibility = View.GONE
                tvDailyTitle.visibility = View.VISIBLE
                rvDaily.visibility = View.VISIBLE
                tvDailyTitle.text = "حصص تاريخ: ${pretty(selectedDateStr)}"
            }
            "yearly" -> {
                btnY.apply {
                    background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_active)
                    setTextColor(Color.WHITE)
                }
                viewYearly.visibility = View.VISIBLE
                val year = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }.get(Calendar.YEAR)
                viewYearly.setCurrentItem(yearAdapter.pageForYear(year), false)
            }
        }
        updateHeaderTitles()
        loadSchedule()
    }

    private fun updateHeaderTitles() {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        if (currentViewMode == "weekly") {
            tvPeriodName.text = "أسبوع: ${pretty(selectedDateStr)}"
        } else if (currentViewMode == "monthly") {
            tvPeriodName.text = monthFormat.format(cal.time)
        }
    }

    /** يُظهر FAB العودة لليوم فقط حين لا يكون "اليوم" ظاهراً في العرض الحالي. */
    private fun updateTodayFab() {
        if (!::fabToday.isInitialized) return
        val t = Calendar.getInstance()
        val s = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        val todayVisible = when (currentViewMode) {
            "weekly" -> t.get(Calendar.YEAR) == s.get(Calendar.YEAR) && t.get(Calendar.WEEK_OF_YEAR) == s.get(Calendar.WEEK_OF_YEAR)
            "yearly" -> t.get(Calendar.YEAR) == yearAdapter.yearOf(viewYearly.currentItem)
            "monthly" -> { // الشهر المعروض في الـpager (لا التاريخ المحدّد الثابت)
                val mc = monthAdapter.monthCal(monthPager.currentItem)
                t.get(Calendar.YEAR) == mc.get(Calendar.YEAR) && t.get(Calendar.MONTH) == mc.get(Calendar.MONTH)
            }
            else -> selectedDateStr == sdfApi.format(Date()) // daily
        }
        fabToday.visibility = if (todayVisible) View.GONE else View.VISIBLE
    }

    private fun loadSchedule() {
        updateTodayFab()
        // في "شهري" أيضاً تعرض القائمة السفلية حصص اليوم المحدّد (نفس "يومي").
        val fetchMode = if (currentViewMode == "monthly") "daily" else currentViewMode
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = selectedDateStr, viewMode = fetchMode
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
                val list = response.body() ?: emptyList()
                val sorted = list.sortedBy { it.startTime }
                when (currentViewMode) {
                    "daily" -> rvDayEvents.adapter = CalendarAdapter(sorted, selectedDateStr)
                    "monthly" -> rvDaily.adapter = CalendarAdapter(sorted, selectedDateStr)
                    "weekly" -> renderWeeklyGrid(list)
                }
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) {
                Toast.makeText(this@CalendarActivity, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /** إعداد تقويم الأشهر القابل للتمرير الأفقي (ViewPager2) + نقاط الأحداث + الطيّ/التوسعة. */
    private fun setupMonthPager() {
        monthAdapter = MonthPageAdapter(this) { date ->
            selectedDateStr = date
            tvDailyTitle.text = "حصص تاريخ: ${pretty(selectedDateStr)}"
            updateDayCard()
            loadSchedule()
        }
        monthAdapter.selectedDate = selectedDateStr
        monthPager.adapter = monthAdapter
        // التنقّل بين الأشهر بالسحب يمين/يسار.
        monthPager.isUserInputEnabled = true
        monthPager.setCurrentItem(MonthPageAdapter.CENTER, false)
        tvDailyMonth.text = monthFormat.format(sdfApi.parse(selectedDateStr)!!)
        monthPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val cal = monthAdapter.monthCal(position)
                tvDailyMonth.text = monthFormat.format(cal.time)
                fetchMonthEvents(monthAdapter.ymOf(position))
                updateTodayFab() // الشهر المعروض تغيّر → حدّث ظهور زر اليوم
            }
        })
        fetchMonthEvents(monthAdapter.ymOf(MonthPageAdapter.CENTER))
        setupCalCollapse()
    }

    private val calExpandedH get() = (380 * resources.displayMetrics.density).toInt()
    private val calCollapsedH get() = (110 * resources.displayMetrics.density).toInt()

    /** مقبض الطيّ/التوسعة: السحب يتبع الإصبع مباشرة (مرن)، وعند الإفلات يستقرّ لأقرب حالة. */
    private fun setupCalCollapse() {
        val handle = findViewById<View>(R.id.calExpandHandle)
        var startY = 0f
        var startH = 0
        var dragged = false
        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = ev.rawY; startH = monthPager.height; dragged = false
                    // أظهر كل الأسابيع أثناء السحب لتكون الحركة مرئية.
                    if (!monthAdapter.expanded) { monthAdapter.expanded = true; monthAdapter.notifyDataSetChanged() }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val nh = (startH + (ev.rawY - startY)).toInt().coerceIn(calCollapsedH, calExpandedH)
                    if (Math.abs(ev.rawY - startY) > 4) dragged = true
                    monthPager.layoutParams = monthPager.layoutParams.apply { height = nh }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    val mid = (calCollapsedH + calExpandedH) / 2
                    if (!dragged) setCalExpanded(!monthAdapter.expanded) // نقر = تبديل
                    else setCalExpanded(monthPager.height > mid)        // إفلات = استقرار لأقرب
                    true
                }
                else -> false
            }
        }
    }

    private fun setCalExpanded(expand: Boolean) {
        monthAdapter.expanded = expand
        monthAdapter.notifyDataSetChanged()
        val from = monthPager.layoutParams.height
        val to = if (expand) calExpandedH else calCollapsedH
        if (from == to) return
        android.animation.ValueAnimator.ofInt(from, to).apply {
            duration = 220
            addUpdateListener {
                monthPager.layoutParams = monthPager.layoutParams.apply { height = it.animatedValue as Int }
            }
            start()
        }
    }

    /** تنقّل اليوم (±1) عند السحب الأفقي في القائمة. */
    private fun shiftDay(dir: Int) {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        cal.add(Calendar.DAY_OF_MONTH, dir)
        selectedDateStr = sdfApi.format(cal.time)
        syncToSelectedDate(smooth = false)
        slideIn(viewDaily, dir) // انزلاق الصفحة كاملة
    }

    /** انزلاق محتوى عند التنقّل بالسحب (إحساس تغيّر الصفحة). */
    private fun slideIn(v: View, dir: Int, frac: Float = 0.35f) {
        val w = (if (v.width > 0) v.width else resources.displayMetrics.widthPixels).toFloat()
        v.translationX = dir * w * frac
        v.alpha = 0.3f
        v.animate().translationX(0f).alpha(1f).setDuration(if (frac >= 1f) 320 else 280).start()
    }

    /** العودة لليوم الحالي بحركة سلسة (في كل الأوضاع). */
    private fun goToToday() {
        val dir = if (sdfApi.format(Date()) > selectedDateStr) 1 else -1
        selectedDateStr = sdfApi.format(Date())
        when (currentViewMode) {
            "yearly" -> {
                val y = Calendar.getInstance().get(Calendar.YEAR)
                val cur = viewYearly.currentItem
                val page = yearAdapter.pageForYear(y)
                if (Math.abs(cur - page) > 1) viewYearly.setCurrentItem(if (page > cur) page - 1 else page + 1, false)
                viewYearly.post { viewYearly.setCurrentItem(page, true) } // انزلاق أصلي سلس
                updateTodayFab()
            }
            "weekly" -> { syncToSelectedDate(smooth = true); slideIn(viewWeekly, dir, 1f) }
            else -> { syncToSelectedDate(smooth = true); slideIn(viewDaily, dir, 1f) }
        }
    }

    /** يزامن التقويم + البطاقة + القائمة مع التاريخ المحدّد. */
    private fun syncToSelectedDate(smooth: Boolean = false) {
        monthAdapter.selectedDate = selectedDateStr
        smoothPagerTo(monthAdapter.pageForDate(selectedDateStr), smooth)
        tvDailyTitle.text = "حصص تاريخ: ${pretty(selectedDateStr)}"
        updateDayCard()
        loadSchedule()
    }

    /** ينقل الـpager للصفحة المطلوبة. عند smooth: قفزة فورية لجوار الهدف ثم انزلاق خطوة واحدة (سلس بلا تجمّد). */
    private fun smoothPagerTo(page: Int, smooth: Boolean) {
        val cur = monthPager.currentItem
        if (cur == page) { monthAdapter.notifyDataSetChanged(); return }
        if (!smooth) { monthPager.setCurrentItem(page, false); return }
        if (Math.abs(cur - page) > 1) {
            monthPager.setCurrentItem(if (page > cur) page - 1 else page + 1, false)
        }
        monthPager.post { monthPager.setCurrentItem(page, true) }
    }

    /** إعداد العرض السنوي كـViewPager2 (سحب أصلي + انزلاق بطاقة). */
    private fun setupYearPager() {
        yearAdapter = YearPagerAdapter(this) { y, m -> openMonth(y, m) }
        viewYearly.adapter = yearAdapter
        viewYearly.isUserInputEnabled = true
        viewYearly.offscreenPageLimit = 1 // ابنِ السنة المجاورة مسبقاً ليبقى انزلاق الـfling سلساً
        viewYearly.setCurrentItem(YearPagerAdapter.CENTER, false)
        viewYearly.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateTodayFab() }
        })
    }

    /** يفتح شهراً مختاراً من السنة في العرض الشهري. */
    private fun openMonth(year: Int, monthIdx: Int) {
        val c = Calendar.getInstance().apply { clear(); set(year, monthIdx, 1) }
        selectedDateStr = sdfApi.format(c.time)
        monthAdapter.selectedDate = selectedDateStr
        monthPager.setCurrentItem(monthAdapter.pageForDate(selectedDateStr), false)
        switchViewMode("monthly")
    }


    /** يحدّث بطاقة اليوم المحدّد (السنة/الشهر + الرقم الكبير + اسم اليوم). */
    private fun updateDayCard() {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        tvDayCardYM.text = "${cal.get(Calendar.YEAR)} / ${cal.get(Calendar.MONTH) + 1}"
        tvDayCardNum.text = cal.get(Calendar.DAY_OF_MONTH).toString()
        tvDayCardWeek.text = SimpleDateFormat("EEEE", Locale("ar")).format(cal.time)
    }

    /** يجلب حصص شهر معيّن (yyyy-MM) لرسم النقاط. */
    private fun fetchMonthEvents(ym: String) {
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = "$ym-01", viewMode = "monthly"
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
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

    // ========================================================
    // نظام التوليد المخصص للجدول الأسبوعي (يشبه تصميم الويب)
    // ========================================================
    private fun renderWeeklyGrid(schedule: List<ScheduleData>) {
        weeklyGrid.removeAllViews()
        val startHour = 8
        val endHour = 20 // من 8 صباحاً للـ 8 مساءً
        val dpToPx = resources.displayMetrics.density
        val hourHeightPx = (60 * dpToPx).toInt() // كل ساعة = 60dp ارتفاع
        val timeWidthPx = (44 * dpToPx).toInt()
        // عرض عمود اليوم يملأ الشاشة (7 أيام) بدل قياس ثابت يسبب تمريراً أفقياً.
        val padPx = (16 * dpToPx).toInt()
        val dayWidthPx = ((resources.displayMetrics.widthPixels - timeWidthPx - padPx) / 7) - (2 * dpToPx).toInt()

        // 1. إنشاء عمود الوقت (على اليمين)
        val timeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(timeWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(col(R.color.canvas))
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
                setTextColor(col(R.color.ink_muted))
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
                setTextColor(col(R.color.ink))
                setBackgroundColor(col(R.color.surface_alt))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (50 * dpToPx).toInt())
            }
            dayCol.addView(header)

            // إطار يحمل الحصص داخله
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (endHour - startHour + 1) * hourHeightPx)
                setBackgroundColor(col(R.color.surface))
            }

            // رسم خطوط الساعات في الخلفية
            for (h in startHour..endHour) {
                val line = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, 1).apply {
                        topMargin = (h - startHour) * hourHeightPx
                    }
                    setBackgroundColor(col(R.color.line))
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
                    setTextColor(col(R.color.white))
                    setPadding(8, 8, 8, 8)
                    gravity = Gravity.CENTER_HORIZONTAL

                    val bg = GradientDrawable()
                    bg.setColor(primaryColor()) // لون الثيم
                    bg.cornerRadius = 12f
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
                setTextColor(col(R.color.ink_muted))
                setBackgroundColor(col(R.color.surface_alt))
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
                setBackgroundColor(col(R.color.canvas))
            }
            monthlyGrid.addView(emptyView)
        }

        // إضافة أيام الشهر والحصص
        for (day in 1..daysInMonth) {
            val cellDate = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day)
            val dayClasses = schedule.filter { it.startDate == cellDate }

            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(col(R.color.surface))
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
                setTextColor(col(R.color.ink))
            })

            // إضافة نقطة أو نص صغير للحصص (الحد الأقصى 2 لكي لا تتشوه الشاشة)
            for (c in dayClasses.take(2)) {
                val classTv = TextView(this).apply {
                    text = c.subjectName
                    textSize = 8f
                    setTextColor(col(R.color.white))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    val chipBg = GradientDrawable()
                    chipBg.setColor(primaryColor())
                    chipBg.cornerRadius = 6f
                    background = chipBg
                    setPadding(6, 2, 6, 2)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 2 }
                }
                cell.addView(classTv)
            }
            if (dayClasses.size > 2) {
                cell.addView(TextView(this).apply { text = "+${dayClasses.size - 2}"; textSize = 8f; gravity = Gravity.CENTER; setTextColor(col(R.color.ink_muted)) })
            }

            monthlyGrid.addView(cell)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

    // ===== ألوان دلالية تستجيب للوضع الليلي =====
    private fun col(id: Int) = ContextCompat.getColor(this, id)
    private fun primaryColor(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }
    private var TextView.textStyle: Int
        get() = typeface?.style ?: 0
        set(value) { setTypeface(typeface, value) }
}