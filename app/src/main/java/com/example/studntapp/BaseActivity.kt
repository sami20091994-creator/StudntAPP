package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

open class BaseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var drawerLayout: DrawerLayout

    override fun setContentView(layoutResID: Int) {
        val fullView = layoutInflater.inflate(R.layout.activity_base, null) as DrawerLayout
        val activityContainer = fullView.findViewById<FrameLayout>(R.id.activity_content)
        layoutInflater.inflate(layoutResID, activityContainer, true)
        super.setContentView(fullView)

        drawerLayout = fullView
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // تعبئة بيانات الهيدر
        val headerView = navigationView.getHeaderView(0)
        val tvNavName = headerView.findViewById<TextView>(R.id.tvNavName)
        val tvNavRole = headerView.findViewById<TextView>(R.id.tvNavRole)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        tvNavName.text = prefs.getString("USER_NAME", "مستخدم")
        tvNavRole.text = if (prefs.getString("USER_ROLE", "student") == "teacher") "حساب معلم" else "حساب طالب"

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        // لا نستخدم الـ toggle الافتراضي للتحكم في الضغطة لأنه قد يعكس الاتجاه حسب لغة النظام
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                drawerLayout.openDrawer(GravityCompat.END)
            }
        }
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        // إجبار الأيقونة على اليمين بغض النظر عن اللغة
        toolbar.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.END)
        
        when (item.itemId) {
            R.id.nav_daily_report -> if (this !is DailyReportActivity) startActivity(Intent(this, DailyReportActivity::class.java))
            R.id.nav_subjects -> if (this !is MaterialsActivity) startActivity(Intent(this, MaterialsActivity::class.java))
            R.id.nav_study_hours -> if (this !is StudyHoursActivity) startActivity(Intent(this, StudyHoursActivity::class.java))
            R.id.nav_evaluation -> if (this !is EvaluationActivity) startActivity(Intent(this, EvaluationActivity::class.java))
            R.id.nav_report -> {
                val role = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getString("USER_ROLE", "student")
                if (role == "teacher") {
                    if (this !is TeacherReportActivity) startActivity(Intent(this, TeacherReportActivity::class.java))
                } else {
                    // Student report activity if exists
                }
            }
            R.id.nav_schedule -> if (this !is CalendarActivity) startActivity(Intent(this, CalendarActivity::class.java))
            R.id.nav_statement -> if (this !is StatementActivity) startActivity(Intent(this, StatementActivity::class.java))
            R.id.nav_online_lectures -> if (this !is OnlineLecturesActivity) startActivity(Intent(this, OnlineLecturesActivity::class.java))
            R.id.nav_auto_quizzes -> if (this !is AutoExamsListActivity) startActivity(Intent(this, AutoExamsListActivity::class.java))
            R.id.nav_profile -> if (this !is ProfileActivity) startActivity(Intent(this, ProfileActivity::class.java))
            R.id.nav_logout -> {
                getSharedPreferences("AppSession", Context.MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }
        }
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }
}