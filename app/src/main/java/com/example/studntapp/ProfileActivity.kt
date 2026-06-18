package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide

class ProfileActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        supportActionBar?.title = "الملف الشخصي"

        // 1. جلب بيانات المستخدم
        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val role = prefs.getString("USER_ROLE", "student") ?: "student"
        val userName = prefs.getString("USER_NAME", "مستخدم")
        val userImage = prefs.getString("USER_IMAGE", null)

        val tvStudentName = findViewById<TextView>(R.id.tvStudentName)
        val tvStudentCode = findViewById<TextView>(R.id.tvStudentCode)
        val ivStudentPhoto = findViewById<ImageView>(R.id.ivStudentPhoto)

        tvStudentName.text = userName
        tvStudentCode.text = if (role == "teacher") "حساب معلم" else "حساب طالب"

        if (!userImage.isNullOrEmpty()) {
            val fullUrl = if (userImage.startsWith("http")) userImage else RetrofitClient.BASE_URL + userImage
            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.mipmap.ic_launcher_round)
                .into(ivStudentPhoto)
        }

        // زر تبديل الثيم
        val btnThemeToggle = findViewById<ImageButton>(R.id.btnThemeToggle)
        btnThemeToggle.setOnClickListener {
            val currentMode = AppCompatDelegate.getDefaultNightMode()
            if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        // 2. ربط عناصر الواجهة
        val layoutTeacherFinance = findViewById<LinearLayout>(R.id.layoutTeacherFinance)
        val btnEvaluation = findViewById<View>(R.id.btnEvaluation)
        val btnStudyHours = findViewById<View>(R.id.btnStudyHours)
        val btnStatement = findViewById<View>(R.id.btnStatement)
        val btnDailyReport = findViewById<View>(R.id.btnDailyReport)

        // ** إضافة زر الاختبارات المؤتمتة **
        val btnAutoQuizzes = findViewById<View>(R.id.btnAutoQuizzes) // تأكد من إضافته في الـ XML

        // ==========================================
        // 3. تطبيق فلتر الصلاحيات (إخفاء ما لا يخص المعلم)
        // ==========================================
        if (role == "teacher") {
            layoutTeacherFinance.visibility = View.VISIBLE
            btnEvaluation.visibility = View.GONE
            btnStudyHours.visibility = View.GONE
            btnStatement.visibility = View.GONE
            btnDailyReport.visibility = View.GONE
            btnAutoQuizzes.visibility = View.GONE // إخفاء زر الاختبارات عن المعلم
        } else {
            layoutTeacherFinance.visibility = View.GONE
            btnEvaluation.visibility = View.VISIBLE
            btnStudyHours.visibility = View.VISIBLE
            btnStatement.visibility = View.VISIBLE
            btnDailyReport.visibility = View.VISIBLE
            btnAutoQuizzes.visibility = View.VISIBLE // إظهار زر الاختبارات للطالب
        }

        // 4. برمجة التنقل بين الشاشات
        findViewById<View>(R.id.btnTeacherDues).setOnClickListener {
            startActivity(Intent(this, TeacherFinancialsActivity::class.java))
        }

        findViewById<View>(R.id.btnStudentsPayments).setOnClickListener {
            startActivity(Intent(this, TeacherStudentsPaymentsActivity::class.java))
        }

        findViewById<View>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        findViewById<View>(R.id.btnSubjects).setOnClickListener {
            startActivity(Intent(this, MaterialsActivity::class.java))
        }

        findViewById<View>(R.id.btnStudyHours).setOnClickListener {
            startActivity(Intent(this, StudyHoursActivity::class.java))
        }

        findViewById<View>(R.id.btnEvaluation).setOnClickListener {
            startActivity(Intent(this, EvaluationActivity::class.java))
        }

        findViewById<View>(R.id.btnStatement).setOnClickListener {
            startActivity(Intent(this, StatementActivity::class.java))
        }

        findViewById<View>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        findViewById<View>(R.id.btnMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
        }

        findViewById<View>(R.id.btnDailyReport).setOnClickListener {
            startActivity(Intent(this, DailyReportActivity::class.java))
        }
        findViewById<View>(R.id.btnOnlineLectures).setOnClickListener {
            startActivity(Intent(this, OnlineLecturesActivity::class.java))
        }
        findViewById<View>(R.id.btnReport).setOnClickListener {
            if (role == "teacher") {
                startActivity(Intent(this, TeacherReportActivity::class.java))
            } else {
                Toast.makeText(this, "تقرير الطالب قيد التطوير", Toast.LENGTH_SHORT).show()
            }
        }

        // ** برمجة حدث النقر لزر الاختبارات **
        btnAutoQuizzes.setOnClickListener {
            startActivity(Intent(this, AutoExamsListActivity::class.java)) // ستقوم بإنشاء هذا الـ Activity
        }
    }
}