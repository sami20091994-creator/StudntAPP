package com.example.studntapp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReportActivity : AppCompatActivity() {

    private var studentId = 0
    private lateinit var mainLayout: LinearLayout
    private lateinit var spinnerSubjects: Spinner
    private lateinit var tvAverage: TextView
    private lateinit var tvQuizzes: TextView
    private lateinit var activeSubjectsContainer: LinearLayout
    private lateinit var completedSubjectsContainer: LinearLayout

    private var allEnrolledSubjects = mutableListOf<SubjectData>()
    private var currentSelectedSubjectId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تم تصحيح هذا السطر ليستدعي הגلسة الصحيحة AppSession
        studentId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)

        buildUI()
        fetchSubjectsAndStatus()
    }

    private fun buildUI() {
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#f4f6f9"))
            isFillViewport = true
        }
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "التقرير الأكاديمي الشامل"
            textSize = 24f
            setTextColor(Color.parseColor("#2d3436"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        mainLayout.addView(title)

        val filterLabel = TextView(this).apply { text = "تصفية حسب المادة:"; setTextColor(Color.GRAY); setPadding(0, 0, 0, 10); gravity = Gravity.END }
        spinnerSubjects = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
            setBackgroundColor(Color.WHITE)
        }
        mainLayout.addView(filterLabel)
        mainLayout.addView(spinnerSubjects)

        val statsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f; setPadding(0, 40, 0, 40) }
        val avgBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); gravity = Gravity.CENTER }
        avgBox.addView(TextView(this).apply { text = "المعدل التراكمي"; setTextColor(Color.GRAY) })
        tvAverage = TextView(this).apply { text = "0%"; textSize = 32f; setTextColor(Color.parseColor("#6c5ce7")); setTypeface(null, android.graphics.Typeface.BOLD) }
        avgBox.addView(tvAverage)

        val quizBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); gravity = Gravity.CENTER }
        quizBox.addView(TextView(this).apply { text = "إجمالي الاختبارات"; setTextColor(Color.GRAY) })
        tvQuizzes = TextView(this).apply { text = "0"; textSize = 32f; setTextColor(Color.parseColor("#00b894")); setTypeface(null, android.graphics.Typeface.BOLD) }
        quizBox.addView(tvQuizzes)

        statsLayout.addView(avgBox)
        statsLayout.addView(quizBox)
        mainLayout.addView(statsLayout)

        mainLayout.addView(createSectionTitle("المواد قيد الدراسة (النشطة)", "#2980b9"))
        activeSubjectsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(activeSubjectsContainer)

        mainLayout.addView(createSectionTitle("سجل المواد المكتملة (خريج)", "#27ae60"))
        completedSubjectsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(completedSubjectsContainer)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun createSectionTitle(title: String, color: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor(color))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 40, 10, 20)
            gravity = Gravity.END
        }
    }

    private fun fetchSubjectsAndStatus() {
        RetrofitClient.instance.getEnrolledSubjects(studentId = studentId).enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                if (response.isSuccessful) {
                    allEnrolledSubjects.clear()
                    response.body()?.data?.let { allEnrolledSubjects.addAll(it) }

                    val spinnerList = mutableListOf(SubjectData(null, null, "جميع المواد", null, ""))
                    spinnerList.addAll(allEnrolledSubjects)

                    val adapter = ArrayAdapter(this@ReportActivity, android.R.layout.simple_spinner_item, spinnerList.map { it.subjectName ?: "" })
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerSubjects.adapter = adapter

                    spinnerSubjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                            currentSelectedSubjectId = spinnerList[position].subjectId ?: 0
                            loadReportData()
                        }
                        override fun onNothingSelected(p0: AdapterView<*>?) {}
                    }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {}
        })
    }

    private fun loadReportData() {
        RetrofitClient.instance.getReportData(studentId = studentId, subjectId = currentSelectedSubjectId).enqueue(object : Callback<ReportResponse> {
            override fun onResponse(call: Call<ReportResponse>, response: Response<ReportResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    tvAverage.text = "${data?.average ?: 0.0}%"
                    tvQuizzes.text = "${data?.quizzesCount ?: 0}"

                    activeSubjectsContainer.removeAllViews()
                    completedSubjectsContainer.removeAllViews()

                    val performanceMap = data?.subjects?.associateBy { it.subjectName ?: "" } ?: emptyMap()

                    allEnrolledSubjects.forEach { enrolledSub ->
                        if (currentSelectedSubjectId != 0 && enrolledSub.subjectId != currentSelectedSubjectId) return@forEach

                        val perf = performanceMap[enrolledSub.subjectName ?: ""]
                        val score = perf?.avgPercentage ?: 0.0
                        val status = enrolledSub.status ?: "active"

                        addSubjectToUI(enrolledSub.subjectName ?: "مادة", score, status)
                    }
                    checkEmptySections()
                }
            }
            override fun onFailure(call: Call<ReportResponse>, t: Throwable) {}
        })
    }

    private fun addSubjectToUI(name: String, percentage: Double, status: String) {
        val isLow = percentage < 60
        val isActive = status.lowercase() == "active"

        val barLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = 25f
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 30) }
            elevation = 8f
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 10f; gravity = Gravity.CENTER_VERTICAL }

        val tvStatus = TextView(this).apply {
            text = if (isActive) "مستمر" else "✓ مكتمل"
            setTextColor(if (isActive) Color.parseColor("#3498db") else Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#ebf5fb") else Color.parseColor("#27ae60"))
                cornerRadius = 12f
            }
            setPadding(20, 8, 20, 8); textSize = 11f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f)
        }

        val tvName = TextView(this).apply {
            text = name; textSize = 15f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (isLow) Color.parseColor("#d32f2f") else Color.parseColor("#2d3436"))
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 7f)
        }
        header.addView(tvStatus); header.addView(tvName); barLayout.addView(header)

        val tvPercent = TextView(this).apply {
            text = "$percentage%"; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.END
            setTextColor(if (isLow) Color.parseColor("#d32f2f") else Color.parseColor("#2980b9"))
            setPadding(0, 15, 0, 5)
        }
        barLayout.addView(tvPercent)

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 25)
            max = 100; this.progress = percentage.toInt()
            val pColor = if (isLow) "#d32f2f" else if (isActive) "#3498db" else "#2ecc71"
            progressDrawable.setColorFilter(Color.parseColor(pColor), android.graphics.PorterDuff.Mode.SRC_IN)
        }
        barLayout.addView(progress)

        if (!isActive) {
            val tvDone = TextView(this).apply {
                text = "تم التخرج من هذه المادة 🎓"; textSize = 12f; setTextColor(Color.parseColor("#27ae60"))
                setPadding(0, 15, 0, 0); gravity = Gravity.END; setTypeface(null, android.graphics.Typeface.ITALIC)
            }
            barLayout.addView(tvDone)
            completedSubjectsContainer.addView(barLayout)
        } else {
            activeSubjectsContainer.addView(barLayout)
        }
    }

    private fun checkEmptySections() {
        if (activeSubjectsContainer.childCount == 0) activeSubjectsContainer.addView(createEmptyMsg("لا توجد مواد نشطة حالياً"))
        if (completedSubjectsContainer.childCount == 0) completedSubjectsContainer.addView(createEmptyMsg("لا توجد مواد مكتملة"))
    }

    private fun createEmptyMsg(m: String) = TextView(this).apply { text = m; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(0, 20, 0, 20) }
}