package com.example.studntapp

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * ساعات دراستي: تسجيل دقائق الدراسة اليومية لكل مادة (لتنظيم الوقت والإحصائيات).
 * المواد النشطة قابلة للتسجيل؛ المواد المكتملة تُعرض للاطّلاع فقط بلا تعديل.
 */
class StudyHoursActivity : BaseActivity() {

    private var studentId = 0
    private var role = "student"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subjects)
        supportActionBar?.title = "ساعات دراستي"

        // لا حاجة لشريط فرز المواد (نشطة/مكتملة) هنا.
        findViewById<android.view.View?>(R.id.filterBar)?.visibility = android.view.View.GONE

        val rv = findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        studentId = prefs.getInt("USER_ID", 0)
        role = prefs.getString("USER_ROLE", "student") ?: "student"

        loadSubjects(rv)

        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh?.setOnRefreshListener { loadSubjects(rv) }
    }

    private fun loadSubjects(rv: RecyclerView) {
        // كل المواد من نفس مصدر "المواد الدراسية". النشطة قابلة للتسجيل، المكتملة للعرض فقط.
        RetrofitClient.instance.getSubjects(userId = studentId, role = role).enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (response.isSuccessful) {
                    // المواد النشطة فقط (استثناء المكتملة كلياً).
                    val subjects = (response.body()?.data ?: emptyList())
                        .filter { (it.status ?: "active").lowercase() != "completed" }
                    if (subjects.isEmpty())
                        Toast.makeText(this@StudyHoursActivity, "لا توجد مواد نشطة", Toast.LENGTH_SHORT).show()
                    val adapter = SubjectsAdapter(subjects)
                    rv.adapter = adapter
                    adapter.setOnItemClickListener { subject -> showMinutesDialog(subject) }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                Toast.makeText(this@StudyHoursActivity, "تعذّر تحميل المواد", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /** نافذة إدخال دقائق دراسة اليوم لمادة نشطة. */
    private fun showMinutesDialog(subject: SubjectData) {
        val pad = (18 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun col(id: Int) = ContextCompat.getColor(this@StudyHoursActivity, id)
        container.addView(TextView(this).apply {
            text = "تسجيل ساعات الدراسة"
            textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(col(R.color.ink)); setPadding(0, 0, 0, pad / 2)
        })
        container.addView(TextView(this).apply {
            text = "كم دقيقة درست مادة \"${subject.subjectName ?: "المادة"}\" اليوم؟\nمثال: 60 دقيقة"
            textSize = 15f
            setTextColor(col(R.color.ink))
            setLineSpacing((4 * resources.displayMetrics.density), 1f)
        })
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "الدقائق"
            gravity = Gravity.CENTER
            setTextColor(col(R.color.ink))
            setHintTextColor(col(R.color.ink_muted))
            setPadding(pad, (pad * 0.7f).toInt(), pad, (pad * 0.7f).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14f * resources.displayMetrics.density
                setColor(col(R.color.surface_alt))
                setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad }
        }
        container.addView(input)

        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create().apply {
                window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 22f * resources.displayMetrics.density; setColor(col(R.color.surface))
                })
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(col(R.color.gold))
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(col(R.color.ink_muted))
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val minutes = input.text.toString().toIntOrNull()
                    if (minutes == null || minutes <= 0) {
                        Toast.makeText(this@StudyHoursActivity, "أدخل عدد دقائق صحيح", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    saveHours(subject.subjectId ?: 0, minutes)
                    dismiss()
                }
            }
    }

    private fun saveHours(subjectId: Int, minutes: Int) {
        RetrofitClient.instance.saveStudyHours(studentId = studentId, subjectId = subjectId, minutes = minutes)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    Toast.makeText(this@StudyHoursActivity, response.body()?.message ?: "تم حفظ $minutes دقيقة", Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@StudyHoursActivity, "تعذّر حفظ الساعات", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
