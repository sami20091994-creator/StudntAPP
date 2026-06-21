package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView

/**
 * الأساس الموحّد لكل الشاشات:
 *  - تطبيق الباقة اللونية + الوضع الليلي/النهاري + اتجاه RTL.
 *  - مراعاة أبعاد الشاشة والنوتش (Edge-to-edge insets).
 *  - الدرج الجانبي يفتح من اليمين (نفس جهة الأيقونة).
 *  - شريط سفلي للوجهات الرئيسية + سايدبار للبقية (بلا تكرار).
 *  - تنقّل بلا تكدّس مع أنيميشن احترافي.
 */
open class BaseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var drawerLayout: DrawerLayout
    private var appliedSignature: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeManager.wrapRtl(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ThemeManager.forceRtl(this)
        appliedSignature = ThemeManager.currentSignature(this)
    }

    override fun onResume() {
        super.onResume()
        // إن تغيّر الثيم/الوضع بينما كانت الشاشة في الخلفية (مثلاً من الإعدادات)،
        // نعيد بناءها فوراً لتطبيق الألوان الجديدة دون الحاجة لإعادة تشغيل التطبيق.
        if (appliedSignature != ThemeManager.currentSignature(this)) {
            recreate()
        }
    }

    private val isTeacher: Boolean
        get() = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
            .getString("USER_ROLE", "student") == "teacher"

    override fun setContentView(layoutResID: Int) {
        val fullView = inflateShell()
        val container = fullView.findViewById<FrameLayout>(R.id.activity_content)
        layoutInflater.inflate(layoutResID, container, true)
        installShell(fullView)
    }

    override fun setContentView(view: View?) {
        val fullView = inflateShell()
        val container = fullView.findViewById<FrameLayout>(R.id.activity_content)
        view?.let { container.addView(it) }
        installShell(fullView)
    }

    override fun setContentView(view: View?, params: android.view.ViewGroup.LayoutParams?) {
        val fullView = inflateShell()
        val container = fullView.findViewById<FrameLayout>(R.id.activity_content)
        view?.let { if (params != null) container.addView(it, params) else container.addView(it) }
        installShell(fullView)
    }

    private fun inflateShell(): DrawerLayout {
        // Edge-to-edge لمراعاة النوتش والحواف
        WindowCompat.setDecorFitsSystemWindows(window, false)
        return layoutInflater.inflate(R.layout.activity_base, null) as DrawerLayout
    }

    private fun installShell(fullView: DrawerLayout) {
        super.setContentView(fullView)

        drawerLayout = fullView
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = Color.TRANSPARENT
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        applyInsets(fullView, toolbar)
        setupDrawer(toolbar)
        setupBottomNav()
        setupShellExtras()
        // (أُزيلت خلفية النقاط بناءً على طلب المستخدم — الخلفية الآن لون صلب من الثيم.)
    }

    // ===== زر الرسائل العائم + جرس الإشعارات (في كل الشاشات) =====
    private var cachedNotifs: List<NotificationData>? = null

    private fun setupShellExtras() {
        // الزر العائم للرسائل: يظهر في كل الشاشات عدا شاشة الرسائل نفسها.
        findViewById<FloatingActionButton?>(R.id.fabMessages)?.let { fab ->
            if (this is MessagesActivity) {
                fab.visibility = View.GONE
            } else {
                fab.visibility = View.VISIBLE
                fab.setOnClickListener {
                    val i = Intent(this, MessagesActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(i)
                    overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
                }
            }
        }

        // جرس الإشعارات: يُخفى داخل شاشة الإشعارات نفسها (لا داعي لتكراره).
        val bellContainer = findViewById<View?>(R.id.notifBellContainer)
        if (this is NotificationsActivity) {
            bellContainer?.visibility = View.GONE
        } else {
            bellContainer?.visibility = View.VISIBLE
            findViewById<ImageButton?>(R.id.btnNotifBell)?.setOnClickListener { anchor ->
                showNotificationsBubble(anchor)
            }
            loadNotifBadge()
        }
    }

    private fun loadNotifBadge() {
        val uid = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)
        if (uid == 0) return
        RetrofitClient.instance.getNotifications(userId = uid)
            .enqueue(object : retrofit2.Callback<NotificationResponse> {
                override fun onResponse(call: retrofit2.Call<NotificationResponse>, response: retrofit2.Response<NotificationResponse>) {
                    val list = response.body()?.data ?: emptyList()
                    cachedNotifs = list
                    val badge = findViewById<TextView?>(R.id.tvNotifCount) ?: return
                    // غير المقروء = ما لم يُعلَّم محلياً (يدوم عبر إعادة التشغيل) ولا من السيرفر.
                    val count = NotifReadStore.unreadCount(this@BaseActivity, list)
                    if (count > 0) {
                        badge.visibility = View.VISIBLE
                        badge.text = if (count > 9) "9+" else count.toString()
                    } else badge.visibility = View.GONE
                }
                override fun onFailure(call: retrofit2.Call<NotificationResponse>, t: Throwable) {}
            })
    }

    private fun showNotificationsBubble(anchor: View) {
        val view = LayoutInflater.from(this).inflate(R.layout.popup_notifications, null)
        val container = view.findViewById<LinearLayout>(R.id.notifBubbleContainer)
        val popup = PopupWindow(
            view,
            (328 * resources.displayMetrics.density).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 24f
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            animationStyle = android.R.style.Animation_Dialog
        }

        view.findViewById<TextView>(R.id.btnSeeAllNotif).setOnClickListener {
            popup.dismiss()
            openDetail(Intent(this, NotificationsActivity::class.java))
        }

        view.findViewById<TextView>(R.id.btnBubbleMarkAll).setOnClickListener {
            cachedNotifs?.let { NotifReadStore.markRead(this, it.map { n -> n.id }) }
            findViewById<TextView?>(R.id.tvNotifCount)?.visibility = View.GONE
            val uid = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)
            if (uid != 0) {
                RetrofitClient.instance.markAllNotificationsRead(userId = uid)
                    .enqueue(object : retrofit2.Callback<SimpleResponse> {
                        override fun onResponse(call: retrofit2.Call<SimpleResponse>, response: retrofit2.Response<SimpleResponse>) {}
                        override fun onFailure(call: retrofit2.Call<SimpleResponse>, t: Throwable) {}
                    })
            }
            popup.dismiss()
        }

        fun render(list: List<NotificationData>) {
            container.removeAllViews()
            if (list.isEmpty()) {
                container.addView(bubbleEmptyRow())
            } else {
                list.take(8).forEach { container.addView(bubbleRow(it)) }
            }
        }

        val cached = cachedNotifs
        if (cached != null) {
            render(cached)
        } else {
            container.addView(bubbleEmptyRow("جارٍ التحميل..."))
            val uid = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)
            RetrofitClient.instance.getNotifications(userId = uid)
                .enqueue(object : retrofit2.Callback<NotificationResponse> {
                    override fun onResponse(call: retrofit2.Call<NotificationResponse>, response: retrofit2.Response<NotificationResponse>) {
                        val list = response.body()?.data ?: emptyList()
                        cachedNotifs = list
                        render(list)
                    }
                    override fun onFailure(call: retrofit2.Call<NotificationResponse>, t: Throwable) {
                        render(emptyList())
                    }
                })
        }

        popup.showAsDropDown(anchor, 0, dp(6), android.view.Gravity.START)
    }

    private fun bubbleRow(item: NotificationData): View {
        val selBg = android.util.TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(selBg.resourceId)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(TextView(this).apply {
            text = item.title
            setTextColor(ContextCompat.getColor(this@BaseActivity, R.color.ink))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.END
        })
        row.addView(TextView(this).apply {
            text = item.message
            setTextColor(ContextCompat.getColor(this@BaseActivity, R.color.ink_muted))
            textSize = 13f
            gravity = android.view.Gravity.END
            setPadding(0, dp(2), 0, 0)
        })
        item.createdAt?.let { t ->
            row.addView(TextView(this).apply {
                text = t
                setTextColor(ContextCompat.getColor(this@BaseActivity, R.color.ink_faint))
                textSize = 11f
                gravity = android.view.Gravity.END
                setPadding(0, dp(3), 0, 0)
            })
        }
        row.setOnClickListener { openNotificationTarget(item) }
        return row
    }

    /** ضغط الإشعار: يُعلّمه مقروءاً ويأخذنا إلى الدردشة مباشرةً (المحادثة المحدّدة إن توفّر المرسل). */
    private fun openNotificationTarget(item: NotificationData) {
        NotifReadStore.markRead(this, listOf(item.id))
        cachedNotifs?.let { list ->
            val c = NotifReadStore.unreadCount(this, list)
            findViewById<TextView?>(R.id.tvNotifCount)?.visibility = if (c > 0) View.VISIBLE else View.GONE
        }
        val i = Intent(this, MessagesActivity::class.java).apply {
            if ((item.senderId ?: 0) != 0) {
                putExtra("OPEN_CHAT_ID", item.senderId)
                putExtra("OPEN_CHAT_TYPE", item.chatType ?: "user")
                putExtra("OPEN_CHAT_NAME", item.senderName)
            }
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(i)
        overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
    }

    private fun bubbleEmptyRow(msg: String = "لا توجد إشعارات حالياً"): View =
        TextView(this).apply {
            text = msg
            setTextColor(ContextCompat.getColor(this@BaseActivity, R.color.ink_muted))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(dp(14), dp(22), dp(14), dp(22))
        }

    // ===== مراعاة أبعاد الشاشة / النوتش =====
    private fun applyInsets(root: View, toolbar: Toolbar) {
        val appbar = findViewById<View>(R.id.appbar)
        val bottomBar = findViewById<View>(R.id.barBackground)
        val navView = findViewById<NavigationView?>(R.id.nav_view)
        val navHeader = navView?.getHeaderView(0)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            appbar.updatePadding(top = bars.top)
            bottomBar?.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = bars.bottom + dp(16)
            }
            // الدرج الجانبي يراعي النوتش كباقي الشاشات:
            //  - محتوى الرأس يُزاح للأسفل ليتجاوز النوتش (وتبقى خلفية الرأس الملوّنة
            //    ممتدّة حتى أعلى الشاشة لمظهر أنيق).
            //  - أسفل القائمة يُزاح للأعلى ليتجاوز شريط التنقّل السفلي.
            navHeader?.updatePadding(top = bars.top + dp(20))
            navView?.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ===== الدرج الجانبي (من اليمين) =====
    private fun setupDrawer(toolbar: Toolbar) {
        val navigationView = findViewById<NavigationView>(R.id.nav_view)

        // اختيار القائمة حسب الدور
        navigationView.menu.clear()
        navigationView.inflateMenu(if (isTeacher) R.menu.nav_menu_teacher else R.menu.nav_menu_student)
        navigationView.setNavigationItemSelectedListener(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_drawer)
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // رأس الدرج
        val headerView = navigationView.getHeaderView(0)
        val tvNavName = headerView.findViewById<TextView>(R.id.tvNavName)
        val tvNavRole = headerView.findViewById<TextView>(R.id.tvNavRole)
        val tvNavCheckIn = headerView.findViewById<TextView?>(R.id.tvNavCheckIn)
        val tvNavCheckOut = headerView.findViewById<TextView?>(R.id.tvNavCheckOut)
        val ivNavPhoto = headerView.findViewById<ImageView?>(R.id.ivNavPhoto)
        val btnTheme = headerView.findViewById<ImageButton?>(R.id.btnNavThemeToggle)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        tvNavName.text = prefs.getString("USER_NAME", "مستخدم")
        tvNavRole.text = if (isTeacher) "حساب معلم" else "حساب طالب"
        tvNavCheckIn?.text = prefs.getString("LAST_CHECK_IN", "--:--")
        tvNavCheckOut?.text = prefs.getString("LAST_CHECK_OUT", "--:--")

        val img = prefs.getString("USER_IMAGE", null)
        if (!img.isNullOrEmpty() && ivNavPhoto != null) {
            val url = if (img.startsWith("http")) img else RetrofitClient.BASE_URL + img
            Glide.with(this).load(url).placeholder(R.mipmap.ic_launcher_round).into(ivNavPhoto)
        }

        // مبدّل الوضع الليلي/النهاري
        btnTheme?.setImageResource(
            if (ThemeManager.isNight(this)) R.drawable.ic_light_mode else R.drawable.ic_dark_mode
        )
        btnTheme?.setOnClickListener {
            ThemeManager.setNight(this, !ThemeManager.isNight(this))
            drawerLayout.closeDrawer(GravityCompat.START)
            recreate()
        }
    }

    // ===== الشريط السفلي (4 وجهات) =====
    private val activeIconColor get() = themeColor(com.google.android.material.R.attr.colorPrimary)
    private val idleIconColor get() = ContextCompat.getColor(this, R.color.ink_faint)

    private fun themeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun setupBottomNav() {
        val tabHome = findViewById<LinearLayout?>(R.id.tab_home) ?: return
        val tabSchedule = findViewById<LinearLayout>(R.id.tab_schedule)
        val tabMaterials = findViewById<LinearLayout>(R.id.tab_materials)
        val tabReports = findViewById<LinearLayout>(R.id.tab_reports)

        tabHome.setOnClickListener { navigateMainTab(0) }
        tabSchedule.setOnClickListener { navigateMainTab(1) }
        tabMaterials.setOnClickListener { navigateMainTab(2) }
        tabReports.setOnClickListener { navigateMainTab(3) }
        highlightActiveTab()
    }

    /**
     * تنقّل موحّد للوجهات الرئيسية الأربع يمنع التكدّس:
     *  - داخل مضيف الـ ViewPager: نحرّك الصفحة فقط (handleMainTab).
     *  - الطالب في صفحة فرعية: نعود إلى HomeShellActivity على الصفحة المطلوبة وننهي الفرعية.
     *  - المعلّم: نفتح النشاط الرئيسي مع CLEAR_TOP (السلوك السابق).
     */
    private fun navigateMainTab(index: Int) {
        if (handleMainTab(index)) return
        if (isTeacher) {
            val cls = when (index) {
                0 -> DailyReportActivity::class.java
                1 -> CalendarActivity::class.java
                2 -> MaterialsActivity::class.java
                else -> TeacherReportActivity::class.java
            }
            if (this::class.java != cls) openTop(Intent(this, cls))
        } else {
            val i = Intent(this, HomeShellActivity::class.java)
            i.putExtra("PAGE", index)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(i)
            overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
            if (this !is HomeShellActivity) finish()
        }
    }

    /**
     * تتجاوزه الشاشة المضيفة لـ ViewPager لتحريك الصفحة بدل فتح نشاط جديد.
     * يعيد true إذا تولّى التنقّل داخلياً.
     */
    protected open fun handleMainTab(index: Int): Boolean = false

    private fun setTab(iconId: Int, indicatorId: Int, active: Boolean) {
        findViewById<ImageView?>(iconId)?.setColorFilter(if (active) activeIconColor else idleIconColor)
        findViewById<View?>(indicatorId)?.visibility = if (active) View.VISIBLE else View.INVISIBLE
    }

    private fun highlightActiveTab() {
        setTab(R.id.icon_home, R.id.ind_home, this is DailyReportActivity)
        setTab(R.id.icon_schedule, R.id.ind_schedule, this is CalendarActivity)
        setTab(R.id.icon_materials, R.id.ind_materials, this is MaterialsActivity)
        setTab(R.id.icon_reports, R.id.ind_reports, this is TeacherReportActivity || this is ReportActivity)
    }

    /** تُستخدم من الشاشة المضيفة لـ ViewPager لإبراز التبويب حسب رقم الصفحة. */
    fun highlightTabByIndex(index: Int) {
        setTab(R.id.icon_home, R.id.ind_home, index == 0)
        setTab(R.id.icon_schedule, R.id.ind_schedule, index == 1)
        setTab(R.id.icon_materials, R.id.ind_materials, index == 2)
        setTab(R.id.icon_reports, R.id.ind_reports, index == 3)
    }

    /**
     * تحريك مؤشّر الشريط السفلي وتكبير أيقونة التبويب النشط بشكل متزامن مع سحب
     * الصفحات (pos = position + positionOffset). يعطي إحساس واتساب/المشغّل.
     */
    fun updateTabScroll(pos: Float) {
        val tabs = listOf(
            Triple(R.id.icon_home, R.id.ind_home, 0),
            Triple(R.id.icon_schedule, R.id.ind_schedule, 1),
            Triple(R.id.icon_materials, R.id.ind_materials, 2),
            Triple(R.id.icon_reports, R.id.ind_reports, 3)
        )
        for ((iconId, indId, idx) in tabs) {
            val sel = (1f - kotlin.math.abs(idx - pos)).coerceIn(0f, 1f)
            findViewById<ImageView?>(iconId)?.let {
                val scale = 1f + 0.28f * sel
                it.scaleX = scale; it.scaleY = scale
                it.setColorFilter(blendColor(idleIconColor, activeIconColor, sel))
            }
            findViewById<View?>(indId)?.let {
                it.visibility = View.VISIBLE
                it.alpha = sel
                it.scaleX = sel
            }
        }
    }

    private fun blendColor(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        return Color.rgb(r, g, b)
    }

    /** الوجهات الرئيسية للشريط السفلي (الرئيسية هي الجذر). */
    private fun isMainDestination(): Boolean =
        this is DailyReportActivity || this is CalendarActivity ||
        this is MaterialsActivity || this is ReportActivity || this is TeacherReportActivity

    // ===== تنقّل موحّد بلا تكدّس + أنيميشن =====
    /**
     * للوجهات الرئيسية: نمسح ما فوق الوجهة في المكدّس (CLEAR_TOP) ونعيد استخدام النسخة
     * إن وُجدت (SINGLE_TOP) — فلا تتراكم التبويبات ولا يدور زر الرجوع بينها.
     */
    private fun openTop(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
    }

    /**
     * للشاشات الفرعية: دفع مع أنيميشن انزلاق، مع منع التكدّس:
     * إذا كنا في صفحة فرعية أصلاً (ليست المضيف ولا وجهة رئيسية) نُنهيها بعد فتح التالية،
     * فلا تتراكم صفحات السايدبار فوق بعضها.
     */
    private fun openDetail(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
        if (this !is HomeShellActivity && !isMainDestination()) finish()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        drawerLayout.postDelayed({
            when (item.itemId) {
                R.id.nav_study_hours -> if (this !is StudyHoursActivity) openDetail(Intent(this, StudyHoursActivity::class.java))
                R.id.nav_online_lectures -> if (this !is OnlineLecturesActivity) openDetail(Intent(this, OnlineLecturesActivity::class.java))
                R.id.nav_auto_quizzes -> if (this !is AutoExamsListActivity) openDetail(Intent(this, AutoExamsListActivity::class.java))
                R.id.nav_evaluation -> if (this !is EvaluationActivity) openDetail(Intent(this, EvaluationActivity::class.java))
                R.id.nav_statement -> if (this !is StatementActivity) openDetail(Intent(this, StatementActivity::class.java))
                R.id.nav_messages -> if (this !is MessagesActivity) openDetail(Intent(this, MessagesActivity::class.java))
                R.id.nav_notifications -> openDetail(Intent(this, NotificationsActivity::class.java))
                R.id.nav_financials -> openDetail(Intent(this, TeacherFinancialsActivity::class.java))
                R.id.nav_students_payments -> openDetail(Intent(this, TeacherStudentsPaymentsActivity::class.java))
                R.id.nav_settings -> if (this !is SettingsActivity) openDetail(Intent(this, SettingsActivity::class.java))
                R.id.nav_logout -> {
                    getSharedPreferences("AppSession", Context.MODE_PRIVATE).edit().clear().apply()
                    val i = Intent(this, MainActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(i)
                    finishAffinity()
                }
            }
        }, 220)
        return true
    }

    override fun onBackPressed() {
        // 1) إن كان الدرج مفتوحاً نُغلقه أولاً.
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        // 2) من أي تبويب رئيسي (غير الرئيسية) يرجع زر Back إلى الرئيسية مباشرةً،
        //    ويمسح ما فوقها — فلا يدور المستخدم بين التبويبات حسب ترتيب فتحها.
        if (isMainDestination() && this !is DailyReportActivity) {
            val i = Intent(this, DailyReportActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(i)
            overridePendingTransition(R.anim.slide_in_back, R.anim.slide_out_back)
            finish()
            return
        }
        // 3) الرئيسية أو الشاشات الفرعية: رجوع عادي.
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_back, R.anim.slide_out_back)
    }
}
