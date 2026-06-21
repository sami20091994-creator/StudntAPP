package com.example.studntapp

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReportActivity : BaseActivity() {

    private var studentId = 0
    private lateinit var mainLayout: LinearLayout
    private lateinit var spinnerSubjects: Spinner
    private lateinit var tvAverage: TextView
    private lateinit var tvQuizzes: TextView
    private lateinit var activeSubjectsContainer: LinearLayout
    private lateinit var completedSubjectsContainer: LinearLayout

    private var allEnrolledSubjects = mutableListOf<SubjectData>()
    private var currentSelectedSubjectId = 0

    // ===== ألوان متوافقة مع الثيم/الوضع الليلي =====
    private fun col(id: Int) = ContextCompat.getColor(this, id)
    private fun primaryColor(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        studentId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)

        buildUI()
        supportActionBar?.title = "التقرير الأكاديمي"
        fetchSubjectsAndStatus()
    }

    private fun buildUI() {
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // (عنوان الصفحة يظهر في شريط العناوين العلوي — لا نكرّره داخل المحتوى)
        val filterLabel = TextView(this).apply {
            text = "تصفية حسب المادة:"; setTextColor(col(R.color.ink_muted)); setPadding(0, 0, 0, 10); gravity = Gravity.END
        }
        spinnerSubjects = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
            background = cardBg()
        }
        mainLayout.addView(filterLabel)
        mainLayout.addView(spinnerSubjects)

        val statsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f; setPadding(0, 30, 0, 30) }
        val avgBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 18 }
            gravity = Gravity.CENTER; setPadding(24, 36, 24, 36); background = cardBg()
        }
        avgBox.addView(TextView(this).apply { text = "المعدل التراكمي"; setTextColor(col(R.color.ink_muted)); textSize = 13f })
        tvAverage = TextView(this).apply { text = "0%"; textSize = 32f; setTextColor(primaryColor()); setTypeface(null, Typeface.BOLD) }
        avgBox.addView(tvAverage)

        val quizBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 18 }
            gravity = Gravity.CENTER; setPadding(24, 36, 24, 36); background = cardBg()
        }
        quizBox.addView(TextView(this).apply { text = "إجمالي الاختبارات"; setTextColor(col(R.color.ink_muted)); textSize = 13f })
        tvQuizzes = TextView(this).apply { text = "0"; textSize = 32f; setTextColor(col(R.color.success_green)); setTypeface(null, Typeface.BOLD) }
        quizBox.addView(tvQuizzes)

        statsLayout.addView(avgBox)
        statsLayout.addView(quizBox)
        mainLayout.addView(statsLayout)

        mainLayout.addView(createSectionTitle("المواد قيد الدراسة (النشطة)", primaryColor()))
        activeSubjectsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(activeSubjectsContainer)

        mainLayout.addView(createSectionTitle("سجل المواد المكتملة (خريج)", col(R.color.success_green)))
        completedSubjectsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(completedSubjectsContainer)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun cardBg() = GradientDrawable().apply {
        setColor(col(R.color.surface)); cornerRadius = 40f
        setStroke(2, col(R.color.line))
    }

    private fun createSectionTitle(title: String, color: Int): TextView {
        return TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(color)
            setTypeface(null, Typeface.BOLD)
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
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = 30f
                setStroke(2, col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 30) }
            elevation = 6f
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 10f; gravity = Gravity.CENTER_VERTICAL }

        val tvStatus = TextView(this).apply {
            text = if (isActive) "مستمر" else "✓ مكتمل"
            setTextColor(if (isActive) primaryColor() else col(R.color.white))
            background = GradientDrawable().apply {
                setColor(if (isActive) col(R.color.surface_alt) else col(R.color.success_green))
                cornerRadius = 12f
            }
            setPadding(20, 8, 20, 8); textSize = 11f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f)
        }

        val tvName = TextView(this).apply {
            text = name; textSize = 15f; setTypeface(null, Typeface.BOLD)
            setTextColor(col(R.color.ink)) // الاسم دائماً بلون النص الأساسي؛ التنبيه يظهر في النسبة فقط
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 7f)
        }
        header.addView(tvStatus); header.addView(tvName); barLayout.addView(header)

        val tvPercent = TextView(this).apply {
            text = "$percentage%"; setTypeface(null, Typeface.BOLD); gravity = Gravity.END
            setTextColor(if (isLow) col(R.color.error_red) else primaryColor())
            setPadding(0, 15, 0, 5)
        }
        barLayout.addView(tvPercent)

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 26).apply { topMargin = 14 }
            max = 100; this.progress = percentage.toInt()
            val pColor = if (isLow) col(R.color.error_red) else if (isActive) primaryColor() else col(R.color.success_green)
            // تلوين الجزء المُنجَز فقط، وإبقاء المسار غير المُنجَز بلون خفيف — مظهر Material أنظف.
            progressTintList = android.content.res.ColorStateList.valueOf(pColor)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(col(R.color.line))
        }
        barLayout.addView(progress)

        if (!isActive) {
            val tvDone = TextView(this).apply {
                text = "تم التخرج من هذه المادة 🎓"; textSize = 12f; setTextColor(col(R.color.success_green))
                setPadding(0, 15, 0, 0); gravity = Gravity.END; setTypeface(null, Typeface.ITALIC)
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

    private fun createEmptyMsg(m: String) = TextView(this).apply { text = m; setTextColor(col(R.color.ink_faint)); gravity = Gravity.CENTER; setPadding(0, 20, 0, 20) }
}
