package com.example.studntapp

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** صفحة التقرير الأكاديمي للطالب داخل الـ ViewPager (تُبنى برمجياً). */
class ReportFragment : Fragment() {

    private var studentId = 0
    private lateinit var mainLayout: LinearLayout
    private lateinit var spinnerSubjects: Spinner
    private lateinit var tvAverage: TextView
    private lateinit var tvQuizzes: TextView
    private lateinit var activeSubjectsContainer: LinearLayout
    private lateinit var completedSubjectsContainer: LinearLayout

    private var allEnrolledSubjects = mutableListOf<SubjectData>()
    private var currentSelectedSubjectId = 0

    private fun col(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun primaryColor(): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        studentId = requireContext().getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)
        return buildUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchSubjectsAndStatus()
    }

    private fun buildUI(): View {
        val ctx = requireContext()
        val scrollView = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            setBackgroundColor(col(R.color.canvas))
        }
        mainLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            layoutDirection = View.LAYOUT_DIRECTION_RTL // ضمان تدفّق العناصر من اليمين لليسار
        }

        val filterLabel = TextView(ctx).apply {
            text = "تصفية حسب المادة"
            textSize = 16f
            setTextColor(primaryColor())
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 4, 14)
            gravity = Gravity.END
        }
        spinnerSubjects = Spinner(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
            background = cardBg()
        }
        mainLayout.addView(buildProfileHeader(ctx))
        mainLayout.addView(filterLabel)
        mainLayout.addView(spinnerSubjects)

        val statsLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f; setPadding(0, 30, 0, 30); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val avgBox = statCard(ctx, primaryColor()).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 18
        }
        avgBox.addView(TextView(ctx).apply { text = "المعدل التراكمي"; setTextColor(col(R.color.ink_muted)); textSize = 13f })
        tvAverage = TextView(ctx).apply { text = "0%"; textSize = 32f; setTextColor(primaryColor()); setTypeface(null, Typeface.BOLD) }
        avgBox.addView(tvAverage)

        val quizBox = statCard(ctx, col(R.color.success_green)).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = 18
        }
        quizBox.addView(TextView(ctx).apply { text = "إجمالي الاختبارات"; setTextColor(col(R.color.ink_muted)); textSize = 13f })
        tvQuizzes = TextView(ctx).apply { text = "0"; textSize = 32f; setTextColor(col(R.color.success_green)); setTypeface(null, Typeface.BOLD) }
        quizBox.addView(tvQuizzes)

        statsLayout.addView(avgBox)
        statsLayout.addView(quizBox)
        mainLayout.addView(statsLayout)

        activeSubjectsContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(sectionCard(ctx, "المواد قيد الدراسة (النشطة)", primaryColor(), activeSubjectsContainer))

        completedSubjectsContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(sectionCard(ctx, "سجل المواد المكتملة (خريج)", col(R.color.success_green), completedSubjectsContainer))

        scrollView.addView(mainLayout)
        return scrollView
    }

    private var tvHeaderAvg: TextView? = null

    private fun themeAttr(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /** بطاقة قسم: عنوان ملوّن + حاوية المواد بداخلها. */
    private fun sectionCard(ctx: Context, title: String, color: Int, container: LinearLayout): View {
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(14), px(14), px(14))
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = px(20).toFloat()
                setStroke(2, col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(8); bottomMargin = px(8) }
        }
        card.addView(TextView(ctx).apply {
            text = title; textSize = 16f; setTextColor(color)
            setTypeface(null, Typeface.BOLD); gravity = Gravity.END
            setPadding(0, 0, px(4), px(10))
        })
        card.addView(container)
        return card
    }

    /** رأس الطالب المتدرّج (هوية مستوحاة من تقرير الويب). */
    private fun buildProfileHeader(ctx: Context): View {
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val name = ctx.getSharedPreferences("AppSession", Context.MODE_PRIVATE).getString("USER_NAME", "طالب")?.trim().orEmpty()
        val initial = name.firstOrNull()?.toString()?.uppercase() ?: "ط"

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL // العناصر من اليمين لليسار
            // تدرّج من ألوان الثيم الحالي بدل لون ثابت.
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(themeAttr(com.google.android.material.R.attr.colorPrimary),
                           themeAttr(com.google.android.material.R.attr.colorPrimaryVariant))
            ).apply { cornerRadius = px(22).toFloat() }
            setPadding(px(20), px(22), px(20), px(22))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = px(16) }
        }
        // أفاتار (الحرف الأول)
        val avatar = TextView(ctx).apply {
            text = initial; setTextColor(android.graphics.Color.WHITE); textSize = 26f
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#33FFFFFF"))
                setStroke(px(2), android.graphics.Color.parseColor("#66FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(px(60), px(60))
        }
        row.addView(avatar)
        // الاسم + الوصف
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(14) }
        }
        info.addView(TextView(ctx).apply {
            text = name.ifEmpty { "طالب" }; setTextColor(android.graphics.Color.WHITE)
            textSize = 18f; setTypeface(null, Typeface.BOLD)
        })
        info.addView(TextView(ctx).apply {
            text = "التقرير الأكاديمي الشامل"; setTextColor(android.graphics.Color.parseColor("#E0E0F0")); textSize = 13f
        })
        row.addView(info)
        // مؤشّر المعدل
        val hl = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        tvHeaderAvg = TextView(ctx).apply {
            text = "0%"; setTextColor(android.graphics.Color.WHITE); textSize = 24f; setTypeface(null, Typeface.BOLD)
        }
        hl.addView(tvHeaderAvg)
        hl.addView(TextView(ctx).apply { text = "المعدل العام"; setTextColor(android.graphics.Color.parseColor("#E0E0F0")); textSize = 11f })
        row.addView(hl)
        return row
    }

    /** بطاقة إحصاء بحدّ علوي ملوّن (نمط تقرير الويب). */
    private fun statCard(ctx: Context, topColor: Int): LinearLayout {
        val d = ctx.resources.displayMetrics.density
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 36, 24, 36)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            background = android.graphics.drawable.LayerDrawable(arrayOf(
                GradientDrawable().apply { setColor(topColor); cornerRadius = 36f },
                GradientDrawable().apply {
                    setColor(col(R.color.surface)); cornerRadius = 34f; setStroke(2, col(R.color.line))
                }
            )).apply { setLayerInset(1, 0, (4 * d).toInt(), 0, 0) }
        }
    }

    private fun cardBg() = GradientDrawable().apply {
        setColor(col(R.color.surface)); cornerRadius = 40f
        setStroke(2, col(R.color.line))
    }

    private fun createSectionTitle(title: String, color: Int): TextView {
        return TextView(requireContext()).apply {
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
                if (!isAdded) return
                if (response.isSuccessful) {
                    allEnrolledSubjects.clear()
                    response.body()?.data?.let { allEnrolledSubjects.addAll(it) }

                    val spinnerList = mutableListOf(SubjectData(null, null, "جميع المواد", null, ""))
                    spinnerList.addAll(allEnrolledSubjects)

                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerList.map { it.subjectName ?: "" })
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
                if (!isAdded) return
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    tvAverage.text = "${data?.average ?: 0.0}%"
                    tvHeaderAvg?.text = "${data?.average ?: 0.0}%"
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
        val ctx = requireContext()
        val isLow = percentage < 60
        val isActive = status.lowercase() == "active"

        val barLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = 30f
                setStroke(2, col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 30) }
            elevation = 6f
        }

        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 10f; gravity = Gravity.CENTER_VERTICAL }

        val tvStatus = TextView(ctx).apply {
            text = if (isActive) "مستمر" else "✓ مكتمل"
            setTextColor(if (isActive) primaryColor() else col(R.color.white))
            background = GradientDrawable().apply {
                setColor(if (isActive) col(R.color.surface_alt) else col(R.color.success_green))
                cornerRadius = 12f
            }
            setPadding(20, 8, 20, 8); textSize = 11f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f)
        }

        val tvName = TextView(ctx).apply {
            text = name; textSize = 15f; setTypeface(null, Typeface.BOLD)
            setTextColor(col(R.color.ink))
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 7f)
        }
        header.addView(tvStatus); header.addView(tvName); barLayout.addView(header)

        val tvPercent = TextView(ctx).apply {
            text = "$percentage%"; setTypeface(null, Typeface.BOLD); gravity = Gravity.END
            setTextColor(if (isLow) col(R.color.error_red) else primaryColor())
            setPadding(0, 15, 0, 5)
        }
        barLayout.addView(tvPercent)

        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 26).apply { topMargin = 14 }
            max = 100; this.progress = percentage.toInt()
            val pColor = if (isLow) col(R.color.error_red) else if (isActive) primaryColor() else col(R.color.success_green)
            progressTintList = android.content.res.ColorStateList.valueOf(pColor)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(col(R.color.line))
        }
        barLayout.addView(progress)

        if (!isActive) {
            val tvDone = TextView(ctx).apply {
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

    private fun createEmptyMsg(m: String) = TextView(requireContext()).apply { text = m; setTextColor(col(R.color.ink_faint)); gravity = Gravity.CENTER; setPadding(0, 20, 0, 20) }
}
