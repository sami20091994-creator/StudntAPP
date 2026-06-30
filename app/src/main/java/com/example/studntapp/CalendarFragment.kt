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

    /** لون نص متباين فوق خلفية (أبيض للداكنة، أسود للفاتحة) — يمنع تعارض النص مع لون الثيم. */
    private fun contrastTextOn(bg: Int): Int {
        val lum = (0.299 * android.graphics.Color.red(bg) +
            0.587 * android.graphics.Color.green(bg) +
            0.114 * android.graphics.Color.blue(bg)) / 255.0
        return if (lum > 0.6) android.graphics.Color.parseColor("#1A1A1A") else android.graphics.Color.WHITE
    }

    /** هل انصرم الحدث (تاريخ + وقت النهاية قبل الآن)؟ */
    private fun isPastEvent(dateStr: String, endHHmm: String): Boolean = try {
        val end = endHHmm.ifEmpty { "23:59" }
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).parse("$dateStr $end")?.before(Date()) ?: false
    } catch (e: Exception) { false }

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
        dayPager = view.findViewById(R.id.dayPager)
        viewWeekly = view.findViewById(R.id.viewWeekly)
        viewMonthly = view.findViewById(R.id.viewMonthly)
        viewYearly = view.findViewById(R.id.viewYearly)
        setupYearPager()
        setupWeekPager()
        setupDayPager()
        monthlyGrid = view.findViewById(R.id.monthlyGrid)
        layoutNavHeader = view.findViewById(R.id.layoutNavHeader)
        tvPeriodName = view.findViewById(R.id.tvPeriodName)
        tvDailyTitle = view.findViewById(R.id.tvDailyTitle)
        monthPager = view.findViewById(R.id.monthPager)
        tvDailyMonth = view.findViewById(R.id.tvDailyMonth)
        calBlock = view.findViewById(R.id.calBlock)
        setupMonthPager()

        view.findViewById<TextView>(R.id.btnDaily).setOnClickListener { switchViewMode("daily") }
        view.findViewById<TextView>(R.id.btnWeekly).setOnClickListener { switchViewMode("weekly") }
        view.findViewById<TextView>(R.id.btnMonthly).setOnClickListener { switchViewMode("monthly") }
        view.findViewById<TextView>(R.id.btnYearly).setOnClickListener { switchViewMode("yearly") }

        view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { adjustDate(-1) }
        view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener { adjustDate(1) }

        // كل الأوضاع تعتمد ViewPager2 الآن — تعطيل اعتراض السحب القديم.
        swipeNav = view.findViewById(R.id.swipeNav)
        swipeNav.interceptEnabled = false

        fabToday = requireActivity().findViewById(R.id.fabToday)
        fabToday.setOnClickListener { goToToday() }

        switchViewMode("monthly")
    }

    override fun onResume() {
        super.onResume()
        if (::fabToday.isInitialized) { fabToday.setOnClickListener { goToToday() }; updateTodayFab() }
    }

    override fun onPause() {
        super.onPause()
        if (::fabToday.isInitialized) fabToday.visibility = View.GONE // لا يتسرّب لبقية التبويبات
    }

    private fun setupMonthPager() {
        monthAdapter = MonthPageAdapter(ctx) { date ->
            selectedDateStr = date
            tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
            updateDayCard()
            loadSchedule()
            // نقر يوم خارج الشهر المعروض → انزلاق سلس لذلك الشهر (التالي/السابق).
            val page = monthAdapter.pageForDate(date)
            if (page != monthPager.currentItem) monthPager.setCurrentItem(page, true)
        }
        monthAdapter.selectedDate = selectedDateStr
        monthPager.adapter = monthAdapter
        monthPager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        monthPager.isUserInputEnabled = true
        monthPager.setCurrentItem(MonthPageAdapter.CENTER, false)
        tvDailyMonth.text = monthFormat.format(sdfApi.parse(selectedDateStr)!!)
        monthPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvDailyMonth.text = monthFormat.format(monthAdapter.monthCal(position).time)
                fetchMonthEvents(position)
                updateTodayFab()
            }
        })
        fetchMonthEvents(MonthPageAdapter.CENTER)
        setupCalCollapse()
    }

    private val calExpandedH get() = (380 * resources.displayMetrics.density).toInt()
    private val calCollapsedH get() = (110 * resources.displayMetrics.density).toInt()

    private fun setupCalCollapse() {
        val handle = root.findViewById<View>(R.id.calExpandHandle)
        var startY = 0f
        var startH = 0
        var dragged = false
        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = ev.rawY; startH = monthPager.height; dragged = false
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
                    if (!dragged) setCalExpanded(!monthAdapter.expanded)
                    else setCalExpanded(monthPager.height > mid)
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

    private fun shiftDay(dir: Int) {
        val cal = Calendar.getInstance().apply { time = sdfApi.parse(selectedDateStr)!! }
        cal.add(Calendar.DAY_OF_MONTH, dir)
        selectedDateStr = sdfApi.format(cal.time)
        syncToSelectedDate(smooth = false) // السحب يتبع الإصبع؛ بلا أنيميشن لاحق
    }

    private fun slideIn(v: View, dir: Int, frac: Float = 0.35f) {
        val w = (if (v.width > 0) v.width else resources.displayMetrics.widthPixels).toFloat()
        // RTL: التالي (+1) يدخل من اليسار، السابق (-1) من اليمين — لذلك نعكس الإشارة.
        v.translationX = -dir * w * frac
        v.alpha = 0.3f
        v.animate().translationX(0f).alpha(1f).setDuration(if (frac >= 1f) 320 else 280).start()
    }

    /** يعيد النافذة الحالية فقط إلى موضع اليوم/الأسبوع/الشهر/السنة الحالية — بلا تأثير على بقية النوافذ. */
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
                tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
                loadSchedule()
            }
        }
        updateTodayFab()
    }

    private fun syncToSelectedDate(smooth: Boolean = false) {
        monthAdapter.selectedDate = selectedDateStr
        smoothPagerTo(monthAdapter.pageForDate(selectedDateStr), smooth)
        tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
        updateDayCard()
        loadSchedule()
    }

    private fun smoothPagerTo(page: Int, smooth: Boolean) {
        val cur = monthPager.currentItem
        if (cur == page) { monthAdapter.notifyDataSetChanged(); return }
        if (!smooth) { monthPager.setCurrentItem(page, false); return }
        if (Math.abs(cur - page) > 1) {
            monthPager.setCurrentItem(if (page > cur) page - 1 else page + 1, false)
        }
        monthPager.post { monthPager.setCurrentItem(page, true) }
    }

    private fun setupYearPager() {
        yearAdapter = YearPagerAdapter(ctx) { y, m -> openMonth(y, m) }
        viewYearly.adapter = yearAdapter
        viewYearly.layoutDirection = View.LAYOUT_DIRECTION_RTL
        viewYearly.isUserInputEnabled = true
        viewYearly.offscreenPageLimit = 1
        // تسريع التنقّل: كاش أكبر لإعادة استخدام صفحات السنوات المزارة بلا إعادة بناء/ربط ثقيل.
        (viewYearly.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(6)
        viewYearly.setCurrentItem(YearPagerAdapter.CENTER, false)
        viewYearly.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateTodayFab() }
        })
    }

    private fun setupWeekPager() {
        weekAdapter = WeekPagerAdapter(ctx, userId, role)
        viewWeekly.adapter = weekAdapter
        viewWeekly.layoutDirection = View.LAYOUT_DIRECTION_RTL
        viewWeekly.offscreenPageLimit = 1
        viewWeekly.setCurrentItem(weekAdapter.pageForDate(selectedDateStr), false)
        viewWeekly.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // الأسبوعي مستقل: لا يلمس selectedDateStr (الخاص بالشهري) — فقط رأس الصفحة والـFAB.
                if (currentViewMode != "weekly") return
                updateHeaderTitles()
                updateTodayFab()
            }
        })
    }

    private fun setupDayPager() {
        dayAdapter = DayPagerAdapter(ctx, userId, role)
        dayPager.adapter = dayAdapter
        dayPager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        dayPager.offscreenPageLimit = 1
        dayPager.setCurrentItem(dayAdapter.pageForDate(selectedDateStr), false)
        dayPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // اليومي مستقل: لا يلمس selectedDateStr (الخاص بالشهري) — فقط الـFAB.
                if (currentViewMode != "daily") return
                updateTodayFab()
            }
        })
    }

    private fun openMonth(year: Int, monthIdx: Int) {
        val c = Calendar.getInstance().apply { clear(); set(year, monthIdx, 1) }
        selectedDateStr = sdfApi.format(c.time)
        monthAdapter.selectedDate = selectedDateStr
        monthPager.setCurrentItem(monthAdapter.pageForDate(selectedDateStr), false)
        switchViewMode("monthly")
    }

    /** اليومي مستقل عن الشهري — لم يعد يُزامَن مع التاريخ المحدّد. (مُبقاة للتوافق). */
    private fun updateDayCard() { /* no-op: عزل موضع اليومي */ }

    // نجلب الشهر المعروض + المجاورين ليظهر حدث أيام التعبئة (آخر/أول السطر) على الصفحة الحالية.
    private fun fetchMonthEvents(displayPosition: Int) {
        for (p in (displayPosition - 1)..(displayPosition + 1)) fetchMonthInto(p, displayPosition)
    }

    private fun fetchMonthInto(fetchPosition: Int, refreshPosition: Int) {
        val ym = monthAdapter.ymOf(fetchPosition)
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
                monthAdapter.setMonthSchedule(refreshPosition, ym, byDate)
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) {}
        })
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
                dayPager.visibility = View.VISIBLE
                tvDailyTitle.visibility = View.GONE
                rvDaily.visibility = View.GONE
                // لا نعيد الموضع — اليومي يحتفظ بصفحته المستقلة.
            }
            "weekly" -> {
                btnW.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewWeekly.visibility = View.VISIBLE
                layoutNavHeader.visibility = View.VISIBLE
                // لا نعيد الموضع — الأسبوعي يحتفظ بصفحته المستقلة.
            }
            "monthly" -> {
                btnM.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
                viewDaily.visibility = View.VISIBLE
                calBlock.visibility = View.VISIBLE
                dayPager.visibility = View.GONE
                tvDailyTitle.visibility = View.VISIBLE
                rvDaily.visibility = View.VISIBLE
                tvDailyTitle.text = "حصص تاريخ: $selectedDateStr"
            }
            "yearly" -> {
                btnY.apply { background = ContextCompat.getDrawable(ctx, R.drawable.bg_toggle_active); setTextColor(col(R.color.white)) }
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
            tvPeriodName.text = "أسبوع: ${sdfApi.format(weekAdapter.weekStart(viewWeekly.currentItem).time)}"
        else if (currentViewMode == "monthly")
            tvPeriodName.text = monthFormat.format(monthAdapter.monthCal(monthPager.currentItem).time)
    }

    /** حالة FAB معزولة لكل نافذة حسب موضع صفحتها هي فقط — يظهر فقط عند الابتعاد عن اليوم/الأسبوع/الشهر/السنة الحالية. */
    private fun updateTodayFab() {
        if (!::fabToday.isInitialized) return
        val today = sdfApi.format(Date())
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val todayVisible = when (currentViewMode) {
            "yearly" -> viewYearly.currentItem == yearAdapter.pageForYear(thisYear)
            "weekly" -> viewWeekly.currentItem == weekAdapter.pageForDate(today)
            "daily" -> dayPager.currentItem == dayAdapter.pageForDate(today)
            // الشهري: صفحة شهر اليوم الحالي + اليوم المحدّد هو اليوم.
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
                if (!isAdded) return
                val list = response.body() ?: emptyList()
                val sorted = list.sortedBy { it.startTime }
                when (currentViewMode) {
                    "monthly" -> rvDaily.adapter = CalendarAdapter(sorted, selectedDateStr)
                }
            }
            override fun onFailure(call: Call<List<ScheduleData>>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(ctx, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

}
