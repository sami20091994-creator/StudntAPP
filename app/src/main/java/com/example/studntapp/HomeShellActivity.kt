package com.example.studntapp

import android.os.Bundle
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

/** تنفّذه الصفحات التي تريد التعامل مع زر الرجوع داخلياً (مثل المواد). */
interface BackInterceptor {
    fun handleBack(): Boolean
}

/**
 * الشاشة المضيفة للصفحات الأربع الرئيسية داخل ViewPager2 واحد —
 * تنقّل بالسحب بالإصبع (مثل تبويبات واتساب) مع مزامنة الشريط السفلي.
 * مخصّصة لدور الطالب؛ يبقى دور المعلّم على التنقّل بالأنشطة المستقلة.
 */
class HomeShellActivity : BaseActivity() {

    private lateinit var pager: ViewPager2

    private val titles = listOf(
        "الرئيسية",
        "الجدول الزمني",
        "موادي الدراسية",
        "التقرير الأكاديمي"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_pager)

        pager = findViewById(R.id.homePager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> HomeFragment()
                1 -> CalendarFragment()
                2 -> MaterialsFragment()
                else -> ReportFragment()
            }
        }
        pager.offscreenPageLimit = 1
        // تعطيل السحب الأفقي بين الصفحات؛ التبديل (تقويم/تقرير...) عبر الشريط السفلي فقط
        // كي لا تتعارض إيماءات التقويم مع تبديل الصفحات.
        pager.isUserInputEnabled = false

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // مؤشّر منزلق + تكبير أيقونة التبويب النشط متزامناً مع الإصبع.
                updateTabScroll(position + positionOffset)
            }
            override fun onPageSelected(position: Int) {
                supportActionBar?.title = titles[position]
            }
        })

        // استعادة الصفحة بعد إعادة البناء (مثلاً عند تغيير الثيم) كي لا يعود للرئيسية.
        val saved = savedInstanceState?.getInt("CUR_PAGE", -1) ?: -1
        val startPage = if (saved in 0..3) saved else intent.getIntExtra("PAGE", 0).coerceIn(0, 3)
        pager.setCurrentItem(startPage, false)
        supportActionBar?.title = titles[startPage]
        pager.post { updateTabScroll(startPage.toFloat()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::pager.isInitialized) outState.putInt("CUR_PAGE", pager.currentItem)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // عند العودة إلى المضيف من صفحة فرعية عبر تبويب، ننتقل للصفحة المطلوبة.
        val page = intent.getIntExtra("PAGE", -1)
        if (::pager.isInitialized && page in 0..3) pager.setCurrentItem(page, false)
    }

    /** تبديل علني لصفحة الـ ViewPager (يُستدعى من الأجزاء، مثل الضغط على الاسم في الرئيسية). */
    fun goToTab(index: Int) {
        if (::pager.isInitialized && index in 0..3) pager.currentItem = index
    }

    /** الضغط على تبويب يحرّك صفحة الـ ViewPager بدل فتح نشاط جديد. */
    override fun handleMainTab(index: Int): Boolean {
        if (::pager.isInitialized) {
            // زر "المواد الدراسية": إن كنّا داخل مادة، يعيدنا لقائمة المواد النشطة.
            if (index == 2) (supportFragmentManager.findFragmentByTag("f2") as? MaterialsFragment)?.resetToSubjects()
            pager.currentItem = index
            return true
        }
        return false
    }

    private var backPressedTime = 0L

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        // أولاً: نمنح الصفحة الحالية فرصة التعامل مع الرجوع (مثل العودة لقائمة المواد).
        val current = supportFragmentManager.findFragmentByTag("f" + pager.currentItem)
        if (current is BackInterceptor && current.handleBack()) return

        // من أي صفحة (غير الرئيسية) يعيدنا الرجوع إلى الصفحة الأولى أولاً.
        if (::pager.isInitialized && pager.currentItem != 0) {
            pager.currentItem = 0
            return
        }
        // تأكيد الخروج بالضغط مرتين خلال ثانيتين.
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            super.onBackPressed()
        } else {
            backPressedTime = System.currentTimeMillis()
            android.widget.Toast.makeText(this, "اضغط رجوع مرة أخرى للخروج", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
