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
    private lateinit var viewDaily: LinearLayout
    private lateinit var dayPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var dayAdapter: DayPagerAdapter
    private lateinit var viewWeekly: androidx.viewpager2.widget.ViewPager2
    private lateinit var weekAdapter: WeekPagerAdapter
    private lateinit var viewMonthly: ScrollView
    private lateinit var viewYearly: androidx.viewpager2.widget.ViewPager2
    private lateinit var yearAdapter: YearPagerAdapter
    private lateinit var monthlyGrid: GridLayout
    private lateinit var layoutNavHeader: LinearLayout
    private lateinit var tvPeriodName: TextView
    private lateinit var tvDailyTitle: TextView
    private lateinit var monthPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var monthAdapter: MonthPageAdapter
    private lateinit var tvDailyMonth: TextView
    private lateinit var calBlock: LinearLayout
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
        viewDaily = findViewById(R.id.viewDaily)
        dayPager = findViewById(R.id.dayPager)
        viewWeekly = findViewById(R.id.viewWeekly)
        viewMonthly = findViewById(R.id.viewMonthly)
        viewYearly = findViewById(R.id.viewYearly)
        setupYearPager()
        setupWeekPager()
        setupDayPager()
        monthlyGrid = findViewById(R.id.monthlyGrid)
        layoutNavHeader = findViewById(R.id.layoutNavHeader)
        tvPeriodName = findViewById(R.id.tvPeriodName)
        tvDailyTitle = findViewById(R.id.tvDailyTitle)
        monthPager = findViewById(R.id.monthPager)
        tvDailyMonth = findViewById(R.id.tvDailyMonth)
        calBlock = findViewById(R.id.calBlock)
        setupMonthPager()

        // إعداد أزرار التبديل
        findViewById<TextView>(R.id.btnDaily).setOnClickListener { switchViewMode("daily") }
        findViewById<TextView>(R.id.btnWeekly).setOnClickListener { switchViewMode("weekly") }
        findViewById<TextView>(R.id.btnMonthly).setOnClickListener { switchViewMode("monthly") }
        findViewById<TextView>(R.id.btnYearly).setOnClickListener { switchViewMode("yearly") }

        // أزرار التنقل (السابق / التالي) للأسبوعي والشهري
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { adjustDate(-1) }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { adjustDate(1) }

        // كل الأوضاع تعتمد ViewPager2 الآن — تعطيل اعتراض السحب القديم.
        swipeNav = findViewById(R.id.swipeNav)
        swipeNav.interceptEnabled = false

        fabToday = findViewById(R.id.fabToday)
        fabToday.setOnClickListener { goToToday() }

        switchViewMode("monthly")
    }

    private fun adjustDate(amount: Int) {
        // اليومي/الأسبوعي يتنقّلان عبر صفحات الـViewPager.
        if (currentViewMode == "daily") { dayPager.setCurrentItem(dayPager.currentItem + amount, true); return }
        if (currentViewMode == "weekly") { viewWeekly.setCurrentItem(viewWeekly.currentItem + amount, true); return }
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        if (currentViewMode == "monthly") cal.add(Calendar.MONTH, amount)

        selectedDateStr = sdfApi.format(cal.time)
        updateHeaderTitles()
        loadSchedule()
    }

    private fun switchViewMode(mode: String) {
        currentViewMode = mode
        // كل الأوضاع ViewPager2 — اعتراض السحب القديم معطّل.
        if (::swipeNav.isInitialized) swipeNav.interceptEnabled = false

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
                // يومي: بطاقات الأيام المتلاصقة (ViewPager).
                viewDaily.visibility = View.VISIBLE
                calBlock.visibility = View.GONE
                dayPager.visibility = View.VISIBLE
                tvDailyTitle.visibility = View.GONE
                rvDaily.visibility = View.GONE
                // لا نعيد الموضع — اليومي يحتفظ بصفحته المستقلة.
            }
            "weekly" -> {
                btnW.apply { 
                    background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_active)
                    setTextColor(Color.WHITE) 
                }
                viewWeekly.visibility = View.VISIBLE
                layoutNavHeader.visibility = View.VISIBLE
                // لا نعيد الموضع — الأسبوعي يحتفظ بصفحته المستقلة.
            }
            "monthly" -> {
                btnM.apply {
                    background = ContextCompat.getDrawable(context, R.drawable.bg_toggle_active)
                    setTextColor(Color.WHITE)
                }
                // شهري: التقويم القابل للتمرير + قائمة حصص اليوم المحدّد، بدون بطاقة اليوم.
                viewDaily.visibility = View.VISIBLE
                calBlock.visibility = View.VISIBLE
                dayPager.visibility = View.GONE
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
        if (currentViewMode == "weekly")
            tvPeriodName.text = "أسبوع: ${pretty(sdfApi.format(weekAdapter.weekStart(viewWeekly.currentItem).time))}"
        else if (currentViewMode == "monthly")
            tvPeriodName.text = monthFormat.format(monthAdapter.monthCal(monthPager.currentItem).time)
    }

    /** حالة FAB معزولة لكل نافذة حسب موضع صفحتها هي فقط — يظهر فقط عند الابتعاد عن الحالي. */
    private fun updateTodayFab() {
        if (!::fabToday.isInitialized) return
        val today = sdfApi.format(Date())
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val todayVisible = when (currentViewMode) {
            "yearly" -> viewYearly.currentItem == yearAdapter.pageForYear(thisYear)
            "weekly" -> viewWeekly.currentItem == weekAdapter.pageForDate(today)
            "daily" -> dayPager.currentItem == dayAdapter.pageForDate(today)
            "monthly" -> monthPager.currentItem == monthAdapter.pageForDate(today) && selectedDateStr == today
            else -> true
        }
        fabToday.visibility = if (todayVisible) View.GONE else View.VISIBLE
    }

    private fun loadSchedule() {
        updateTodayFab()
        // اليومي/الأسبوعي يعرضان نفسيهما عبر ViewPager2 — لا جلب هنا. يبقى الشهري للقائمة السفلية.
        if (currentViewMode == "daily" || currentViewMode == "weekly") return
        val fetchMode = if (currentViewMode == "monthly") "daily" else currentViewMode
        RetrofitClient.instance.getFullScheduleFiltered(
            userId = userId, role = role, date = selectedDateStr, viewMode = fetchMode
        ).enqueue(object : Callback<List<ScheduleData>> {
            override fun onResponse(call: Call<List<ScheduleData>>, response: Response<List<ScheduleData>>) {
                val list = response.body() ?: emptyList()
                val sorted = list.sortedBy { it.startTime }
                when (currentViewMode) {
                    "monthly" -> rvDaily.adapter = CalendarAdapter(sorted, selectedDateStr)
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
            // نقر يوم خارج الشهر المعروض → انزلاق سلس لذلك الشهر (التالي/السابق).
            val page = monthAdapter.pageForDate(date)
            if (page != monthPager.currentItem) monthPager.setCurrentItem(page, true)
        }
        monthAdapter.selectedDate = selectedDateStr
        monthPager.adapter = monthAdapter
        monthPager.layoutDirection = View.LAYOUT_DIRECTION_RTL // اتجاه RTL مثل حساب الطالب
        // التنقّل بين الأشهر بالسحب يمين/يسار.
        monthPager.isUserInputEnabled = true
        monthPager.setCurrentItem(MonthPageAdapter.CENTER, false)
        tvDailyMonth.text = monthFormat.format(sdfApi.parse(selectedDateStr)!!)
        monthPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val cal = monthAdapter.monthCal(position)
                tvDailyMonth.text = monthFormat.format(cal.time)
                fetchMonthEvents(position)
                updateTodayFab() // الشهر المعروض تغيّر → حدّث ظهور زر اليوم
            }
        })
        fetchMonthEvents(MonthPageAdapter.CENTER)
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
        syncToSelectedDate(smooth = false) // السحب يتبع الإصبع؛ بلا أنيميشن لاحق
    }

    /** انزلاق محتوى عند التنقّل بالسحب (إحساس تغيّر الصفحة). */
    private fun slideIn(v: View, dir: Int, frac: Float = 0.35f) {
        val w = (if (v.width > 0) v.width else resources.displayMetrics.widthPixels).toFloat()
        // RTL: التالي (+1) يدخل من اليسار، السابق (-1) من اليمين — نعكس الإشارة (مطابق للشهري/السنوي).
        v.translationX = -dir * w * frac
        v.alpha = 0.3f
        v.animate().translationX(0f).alpha(1f).setDuration(if (frac >= 1f) 320 else 280).start()
    }

    /** يعيد النافذة الحالية فقط إلى موضع الحالي — بلا تأثير على بقية النوافذ. */
    private fun goToToday() {
        val today = sdfApi.format(Date())
        when (currentViewMode) {
            "yearly" -> {
                val page = yearAdapter.pageForYear(Calendar.getInstance().get(Calendar.YEAR))
                val cur = viewYearly.currentItem
                if (Math.abs(cur - page) > 1) viewYearly.setCurrentItem(if (page > cur) page - 1 else page + 1, false)
                viewYearly.post { viewYearly.setCurrentItem(page, true) }
            }
            "weekly" -> viewWeekly.setCurrentItem(weekAdapter.pageForDate(today), true)
            "daily" -> dayPager.setCurrentItem(dayAdapter.pageForDate(today), true)
            "monthly" -> {
                selectedDateStr = today
                monthAdapter.selectedDate = today
                monthPager.setCurrentItem(monthAdapter.pageForDate(today), true)
                tvDailyTitle.text = "حصص تاريخ: ${pretty(selectedDateStr)}"
                loadSchedule()
            }
        }
        updateTodayFab()
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
        viewYearly.layoutDirection = View.LAYOUT_DIRECTION_RTL // اتجاه RTL مثل حساب الطالب
        viewYearly.isUserInputEnabled = true
        viewYearly.offscreenPageLimit = 1 // ابنِ السنة المجاورة مسبقاً ليبقى انزلاق الـfling سلساً
        // تسريع التنقّل: كاش أكبر لإعادة استخدام صفحات السنوات المزارة بلا إعادة بناء/ربط ثقيل.
        (viewYearly.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(6)
        viewYearly.setCurrentItem(YearPagerAdapter.CENTER, false)
        viewYearly.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateTodayFab() }
        })
    }

    /** العرض الأسبوعي كبطاقات أسابيع متلاصقة عبر ViewPager2. */
    private fun setupWeekPager() {
        weekAdapter = WeekPagerAdapter(this, userId, role)
        viewWeekly.adapter = weekAdapter
        viewWeekly.layoutDirection = View.LAYOUT_DIRECTION_RTL
        viewWeekly.offscreenPageLimit = 1
        viewWeekly.setCurrentItem(weekAdapter.pageForDate(selectedDateStr), false)
        viewWeekly.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // الأسبوعي مستقل: لا يلمس selectedDateStr (الخاص بالشهري).
                if (currentViewMode != "weekly") return
                updateHeaderTitles()
                updateTodayFab()
            }
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


    /** العرض اليومي كبطاقات أيام متلاصقة عبر ViewPager2. */
    private fun setupDayPager() {
        dayAdapter = DayPagerAdapter(this, userId, role)
        dayPager.adapter = dayAdapter
        dayPager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        dayPager.offscreenPageLimit = 1
        dayPager.setCurrentItem(dayAdapter.pageForDate(selectedDateStr), false)
        dayPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // اليومي مستقل: لا يلمس selectedDateStr (الخاص بالشهري).
                if (currentViewMode != "daily") return
                updateTodayFab()
            }
        })
    }

    /** اليومي مستقل عن الشهري — لم يعد يُزامَن مع التاريخ المحدّد. (مُبقاة للتوافق). */
    private fun updateDayCard() { /* no-op: عزل موضع اليومي */ }

    /** نجلب الشهر المعروض + المجاورين ليظهر حدث أيام التعبئة (آخر/أول السطر) على الصفحة الحالية. */
    private fun fetchMonthEvents(displayPosition: Int) {
        for (p in (displayPosition - 1)..(displayPosition + 1)) fetchMonthInto(p, displayPosition)
    }

    private fun fetchMonthInto(fetchPosition: Int, refreshPosition: Int) {
        val ym = monthAdapter.ymOf(fetchPosition)
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
                monthAdapter.setMonthSchedule(refreshPosition, ym, byDate)
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) {}
        })
    }

    // ===== ثوابت + ألوان دلالية تستجيب للوضع الليلي =====
    private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

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
