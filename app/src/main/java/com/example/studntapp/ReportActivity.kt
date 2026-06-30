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
import com.google.android.material.textfield.TextInputLayout
import androidx.core.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReportActivity : BaseActivity() {

    private var studentId = 0
    private lateinit var mainLayout: LinearLayout
    private lateinit var subjectAutoComplete: AutoCompleteTextView
    private lateinit var tvAverage: TextView
    private lateinit var tvQuizzes: TextView
    private lateinit var activeSubjectsContainer: LinearLayout
    private lateinit var completedSubjectsContainer: LinearLayout

    private var allEnrolledSubjects = mutableListOf<SubjectData>()
    private var currentSelectedSubjectId = 0
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

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
        swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnRefreshListener { fetchSubjectsAndStatus() }
        }
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            layoutDirection = View.LAYOUT_DIRECTION_RTL // ضمان تدفّق العناصر من اليمين لليسار
        }

        // (عنوان الصفحة يظهر في شريط العناوين العلوي — لا نكرّره داخل المحتوى)
        val filterLabel = TextView(this).apply {
            text = "تصفية حسب المادة"
            textSize = 16f
            setTextColor(primaryColor())
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 4, 14)
            gravity = Gravity.END
        }
        val textInputLayout = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(35f, 35f, 35f, 35f)
        }
        subjectAutoComplete = com.google.android.material.textfield.MaterialAutoCompleteTextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL
            setTextColor(col(R.color.ink))
            textSize = 14f
            isFocusable = false
            isClickable = true
            setOnClickListener { 
                if (isPopupShowing) dismissDropDown() else showDropDown() 
            }
        }
        textInputLayout.addView(subjectAutoComplete)
        mainLayout.addView(filterLabel)
        mainLayout.addView(textInputLayout)

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
        swipeRefresh.addView(scrollView)
        setContentView(swipeRefresh)
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
                swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    allEnrolledSubjects.clear()
                    response.body()?.data?.let { allEnrolledSubjects.addAll(it) }

                    val activeSubs = allEnrolledSubjects.filter { (it.status ?: "active").lowercase() == "active" }
                    val doneSubs = allEnrolledSubjects.filter { (it.status ?: "active").lowercase() != "active" }
                    
                    val spinnerList = mutableListOf<SubjectData>()
                    spinnerList.add(SubjectData(null, null, "جميع المواد", null, ""))
                    
                    if (activeSubs.isNotEmpty()) {
                        spinnerList.addAll(activeSubs)
                    }
                    if (doneSubs.isNotEmpty()) {
                        spinnerList.add(SubjectData(-1, null, "—— المواد المكتملة ——", null, ""))
                        spinnerList.addAll(doneSubs)
                    }

                    val labels = spinnerList.map { sub ->
                        if (sub.subjectId == null) "جميع المواد"
                        else if (sub.subjectId == -1) sub.subjectName ?: ""
                        else {
                            val active = (sub.status ?: "active").lowercase() == "active"
                            "${sub.subjectName ?: "مادة"} — ${if (active) "نشطة" else "مكتملة"}"
                        }
                    }

                    val adapter = object : ArrayAdapter<String>(this@ReportActivity, android.R.layout.simple_dropdown_item_1line, labels) {
                        override fun isEnabled(position: Int): Boolean {
                            return spinnerList[position].subjectId != -1
                        }
                        override fun areAllItemsEnabled(): Boolean = false
                    }
                    subjectAutoComplete.setAdapter(adapter)

                    loadReportData()

                    subjectAutoComplete.setOnItemClickListener { _, _, position, _ ->
                        currentSelectedSubjectId = spinnerList[position].subjectId ?: 0
                        loadReportData()
                    }
                    if (labels.isNotEmpty()) {
                        subjectAutoComplete.setText(labels[0], false)
                    }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                swipeRefresh.isRefreshing = false
            }
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
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val isLow = percentage < 60
        val isActive = status.lowercase() == "active"

        val barLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(16), px(16), px(16))
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = px(16).toFloat()
                setStroke(2, col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, px(12)) }
            elevation = px(2).toFloat()
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 10f; gravity = Gravity.CENTER_VERTICAL }

        val tvStatus = TextView(this).apply {
            text = if (isActive) "مستمر" else "✓ مكتمل"
            setTextColor(if (isActive) primaryColor() else col(R.color.white))
            background = GradientDrawable().apply {
                setColor(if (isActive) col(R.color.surface_alt) else col(R.color.success_green))
                cornerRadius = px(8).toFloat()
            }
            setPadding(px(10), px(4), px(10), px(4)); textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f)
        }

        val tvName = TextView(this).apply {
            text = name; textSize = 16f; setTypeface(null, Typeface.BOLD)
            setTextColor(col(R.color.ink))
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 7f)
        }
        header.addView(tvStatus); header.addView(tvName); barLayout.addView(header)

        val tvPercent = TextView(this).apply {
            text = "$percentage%"; textSize = 18f; setTypeface(null, Typeface.BOLD); gravity = Gravity.END
            setTextColor(if (isLow) col(R.color.error_red) else primaryColor())
            setPadding(0, px(8), 0, px(4))
        }
        barLayout.addView(tvPercent)

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(10)).apply { topMargin = px(6) }
            max = 100; this.progress = percentage.toInt()
            val pColor = if (isLow) col(R.color.error_red) else if (isActive) primaryColor() else col(R.color.success_green)
            progressTintList = android.content.res.ColorStateList.valueOf(pColor)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(col(R.color.line))
        }
        barLayout.addView(progress)

        if (!isActive) {
            val tvDone = TextView(this).apply {
                text = "تم التخرج من هذه المادة 🎓"; textSize = 13f; setTextColor(col(R.color.success_green))
                setPadding(0, px(8), 0, 0); gravity = Gravity.END; setTypeface(null, Typeface.ITALIC)
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
