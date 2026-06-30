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
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import android.widget.Toast
import com.google.android.material.textfield.TextInputLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** صفحة التقرير الأكاديمي للطالب داخل الـ ViewPager (تُبنى برمجياً). */
class ReportFragment : Fragment() {

    /** آخر معدل عام مُحمّل لطالب محدّد — تَرِثه بطاقة الاسم في الصفحة الرئيسية. */
    companion object {
        private var cachedStudentId: Int = -1
        private var cachedAverage: Double? = null

        fun setAverage(studentId: Int, avg: Double) {
            cachedStudentId = studentId; cachedAverage = avg
        }

        /** يُرجع المعدل المخزَّن فقط إن كان لنفس الطالب، وإلا null. */
        fun averageFor(studentId: Int): Double? =
            if (studentId == cachedStudentId) cachedAverage else null
    }

    private var studentId = 0
    private var role = "student"
    private lateinit var mainLayout: LinearLayout
    private lateinit var subjectAutoComplete: AutoCompleteTextView
    private lateinit var tvAverage: TextView
    private lateinit var tvQuizzes: TextView
    private lateinit var tvRank: TextView
    private lateinit var tvHours: TextView
    private lateinit var lineChart: com.github.mikephil.charting.charts.LineChart
    private lateinit var barChart: com.github.mikephil.charting.charts.BarChart
    private lateinit var activeSubjectsContainer: LinearLayout
    private lateinit var completedSubjectsContainer: LinearLayout
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private var allEnrolledSubjects = mutableListOf<SubjectData>()
    private var currentSelectedSubjectId = 0

    private fun col(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun primaryColor(): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = requireContext().getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        studentId = prefs.getInt("USER_ID", 0)
        role = prefs.getString("USER_ROLE", "student") ?: "student"
        return buildUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchSubjectsAndStatus()
    }

    override fun onResume() {
        super.onResume()
        // عنوان موثوق عند ظهور الصفحة (يصحّح أي تسرّب من صفحة المواد المجاورة).
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "التقرير الأكاديمي"
    }

    private fun buildUI(): View {
        val ctx = requireContext()
        swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnRefreshListener { fetchSubjectsAndStatus() }
        }
        val scrollView = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            setBackgroundColor(col(R.color.canvas))
            layoutDirection = View.LAYOUT_DIRECTION_RTL // فرض اتجاه RTL لكامل الصفحة
        }
        mainLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            layoutDirection = View.LAYOUT_DIRECTION_RTL // ضمان تدفّق العناصر من اليمين لليسار
        }

        mainLayout.addView(buildProfileHeader(ctx))

        // بطاقة الفلتر: عنوان + منسدلة المواد (تصميم مريح بأبعاد مناسبة).
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val filterCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(px(16), px(16), px(16), px(16))
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = px(20).toFloat(); setStroke(2, col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(4); bottomMargin = px(6) }
        }
        filterCard.addView(TextView(ctx).apply {
            text = "تصفية حسب المادة"
            textSize = 14f
            setTextColor(primaryColor())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.START
        })
        val textInputLayout = TextInputLayout(ctx, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) }
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(px(14).toFloat(), px(14).toFloat(), px(14).toFloat(), px(14).toFloat())
        }
        subjectAutoComplete = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
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
        filterCard.addView(textInputLayout)
        mainLayout.addView(filterCard)

        val statsLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f; setPadding(0, 30, 0, 30); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val avgBox = statCard(ctx, primaryColor()).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 18
        }
        avgBox.addView(TextView(ctx).apply { text = "المعدل التراكمي"; setTextColor(col(R.color.ink_muted)); textSize = 14f })
        tvAverage = TextView(ctx).apply { text = "0%"; textSize = 30f; setTextColor(primaryColor()); setTypeface(null, Typeface.BOLD) }
        avgBox.addView(tvAverage)

        val quizBox = statCard(ctx, col(R.color.success_green)).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = 18
        }
        quizBox.addView(TextView(ctx).apply { text = "إجمالي الاختبارات"; setTextColor(col(R.color.ink_muted)); textSize = 14f })
        tvQuizzes = TextView(ctx).apply { text = "0"; textSize = 30f; setTextColor(col(R.color.success_green)); setTypeface(null, Typeface.BOLD) }
        quizBox.addView(tvQuizzes)

        statsLayout.addView(avgBox)
        statsLayout.addView(quizBox)
        mainLayout.addView(statsLayout)

        // صف إحصائيات ثانٍ: الترتيب + ساعات الدراسة
        val statsLayout2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f; setPadding(0, 0, 0, 30); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val rankBox = statCard(ctx, col(R.color.gold)).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = 18 }
        rankBox.addView(TextView(ctx).apply { text = "ترتيبك في الصف"; setTextColor(col(R.color.ink_muted)); textSize = 14f })
        tvRank = TextView(ctx).apply { text = "—"; textSize = 26f; setTextColor(col(R.color.gold)); setTypeface(null, Typeface.BOLD) }
        rankBox.addView(tvRank)
        val hoursBox = statCard(ctx, primaryColor()).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = 18 }
        hoursBox.addView(TextView(ctx).apply { text = "ساعات الدراسة"; setTextColor(col(R.color.ink_muted)); textSize = 14f })
        tvHours = TextView(ctx).apply { text = "0"; textSize = 26f; setTextColor(primaryColor()); setTypeface(null, Typeface.BOLD) }
        hoursBox.addView(tvHours)
        statsLayout2.addView(rankBox); statsLayout2.addView(hoursBox)
        mainLayout.addView(statsLayout2)

        // رسم بياني: تطوّر الأداء عبر الوقت
        lineChart = com.github.mikephil.charting.charts.LineChart(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(220))
        }
        mainLayout.addView(chartCard(ctx, "تطوّر الأداء عبر الوقت", lineChart))

        // رسم بياني: مقارنتك مع زملائك
        barChart = com.github.mikephil.charting.charts.BarChart(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(240))
        }
        mainLayout.addView(chartCard(ctx, "مقارنتك مع زملاء الصف", barChart))

        // البطاقتان عمودياً: قيد الدراسة أولاً ثم المكتملة
        activeSubjectsContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val activeCard = sectionCard(ctx, "قيد الدراسة (نشطة)", primaryColor(), activeSubjectsContainer)
        completedSubjectsContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val completedCard = sectionCard(ctx, "مكتملة (خريج)", col(R.color.success_green), completedSubjectsContainer)

        activeCard.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(12) }
        completedCard.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(4) }

        mainLayout.addView(activeCard)
        mainLayout.addView(completedCard)

        scrollView.addView(mainLayout)
        swipeRefresh.addView(scrollView)
        return swipeRefresh
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL // نفس تدفّق بطاقة الفلتر
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
            setTypeface(null, Typeface.BOLD); gravity = Gravity.START
            setPadding(px(4), 0, px(4), px(10))
        })
        container.layoutDirection = View.LAYOUT_DIRECTION_RTL
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
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(themeAttr(com.google.android.material.R.attr.colorPrimary),
                           themeAttr(com.google.android.material.R.attr.colorPrimaryVariant))
            ).apply { cornerRadius = px(22).toFloat() }
            setPadding(px(20), px(22), px(20), px(22))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = px(16) }
        }
        // أفاتار: صورة الطالب المعتمدة (USER_IMAGE)، وإلا الصورة الافتراضية.
        val avatar = android.widget.ImageView(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#33FFFFFF"))
                setStroke(px(2), android.graphics.Color.parseColor("#66FFFFFF"))
            }
            val pad = px(2); setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(px(60), px(60))
        }
        row.addView(avatar)
        run {
            val img = ctx.getSharedPreferences("AppSession", Context.MODE_PRIVATE).getString("USER_IMAGE", null)
            val model: Any = if (!img.isNullOrEmpty())
                (if (img.startsWith("http")) img else RetrofitClient.BASE_URL + img)
            else R.mipmap.ic_launcher_round
            com.bumptech.glide.Glide.with(ctx).load(model).circleCrop().into(avatar)
        }
        // الاسم + الوصف
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(14) }
        }
        info.addView(TextView(ctx).apply {
            text = name.ifEmpty { "طالب" }; setTextColor(android.graphics.Color.WHITE)
            textSize = 16f; setTypeface(null, Typeface.BOLD)
        })
        info.addView(TextView(ctx).apply {
            text = "التقرير الأكاديمي الشامل"; setTextColor(android.graphics.Color.parseColor("#E0E0F0")); textSize = 14f
        })
        row.addView(info)
        // مؤشّر المعدل
        val hl = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        tvHeaderAvg = TextView(ctx).apply {
            text = "0%"; setTextColor(android.graphics.Color.WHITE); textSize = 30f; setTypeface(null, Typeface.BOLD)
        }
        hl.addView(tvHeaderAvg)
        hl.addView(TextView(ctx).apply { text = "المعدل العام"; setTextColor(android.graphics.Color.parseColor("#E0E0F0")); textSize = 12f })
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

    /** بطاقة رسم بياني: عنوان + الرسم داخل خلفية متكيّفة مع الثيم. */
    private fun chartCard(ctx: Context, title: String, chart: View): View {
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(px(14), px(14), px(14), px(14))
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = px(20).toFloat(); setStroke(2, col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(8); bottomMargin = px(8) }
            addView(TextView(ctx).apply {
                text = title; textSize = 16f; setTypeface(null, Typeface.BOLD)
                setTextColor(col(R.color.ink)); setPadding(px(4), 0, px(4), px(10))
            })
            addView(chart)
        }
    }

    private fun cardBg() = GradientDrawable().apply {
        setColor(col(R.color.surface)); cornerRadius = 40f
        setStroke(2, col(R.color.line))
    }

    private fun createSectionTitle(title: String, color: Int): TextView {
        return TextView(requireContext()).apply {
            text = title
            textSize = 16f
            setTextColor(color)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 40, 10, 20)
            gravity = Gravity.END
        }
    }

    private fun fetchSubjectsAndStatus() {
        // نفس مصدر "قائمة المواد الدراسية" (get_subjects) ليظهر للطالب موادّه فعلاً.
        RetrofitClient.instance.getSubjects(userId = studentId, role = role).enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                if (!isAdded) return
                swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    allEnrolledSubjects.clear()
                    response.body()?.data?.let { allEnrolledSubjects.addAll(it) }

                    // ترتيب: النشطة أولاً ثم المكتملة، مع فاصل بينهم
                    val activeSubs = allEnrolledSubjects.filter { (it.status ?: "active").lowercase() == "active" }
                    val doneSubs = allEnrolledSubjects.filter { (it.status ?: "active").lowercase() != "active" }
                    
                    val spinnerList = mutableListOf<SubjectData>()
                    spinnerList.add(SubjectData(subjectId = null, subjectName = "جميع المواد", teacherName = null))
                    
                    if (activeSubs.isNotEmpty()) {
                        spinnerList.addAll(activeSubs)
                    }
                    if (doneSubs.isNotEmpty()) {
                        // عنصر وهمي ليعمل كفاصل
                        spinnerList.add(SubjectData(subjectId = -1, subjectName = "—— المواد المكتملة ——", teacherName = null))
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
                    val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, labels) {
                        override fun isEnabled(position: Int): Boolean {
                            return spinnerList[position].subjectId != -1
                        }
                        override fun areAllItemsEnabled(): Boolean = false
                    }
                    subjectAutoComplete.setAdapter(adapter)

                    // اعرض المواد فوراً (لا تنتظر getReportData) لئلا تختفي عند فشله.
                    renderEnrolledSubjects()

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
                if (isAdded) swipeRefresh.isRefreshing = false
            }
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
                    // المعدل العام (كل المواد) فقط يُورَّث لبطاقة الاسم.
                    if (currentSelectedSubjectId == 0) setAverage(studentId, data?.average ?: 0.0)
                    tvQuizzes.text = "${data?.quizzesCount ?: 0}"
                    tvRank.text = if ((data?.rank ?: 0) > 0) "${data?.rank} / ${data?.classSize}" else "—"
                    tvHours.text = "${data?.totalStudyHours ?: 0.0}"

                    populateTimelineChart(data?.timeline)
                    populateComparisonChart(data?.comparison)

                    val performanceMap = data?.subjects?.associateBy { it.subjectName ?: "" } ?: emptyMap()
                    renderEnrolledSubjects(performanceMap) // إغناء بالدرجات
                }
            }
            override fun onFailure(call: Call<ReportResponse>, t: Throwable) {}
        })
    }

    private fun chartTextColor() = col(R.color.ink_muted)

    /** خط زمني: الاختبارات + الواجبات عبر التواريخ. */
    private fun populateTimelineChart(t: ReportTimeline?) {
        if (!::lineChart.isInitialized) return
        val dates = t?.dates ?: emptyList()
        val quizVals = t?.quiz ?: emptyList()
        val hwVals = t?.homework ?: emptyList()
        val quizEntries = ArrayList<Entry>()
        val hwEntries = ArrayList<Entry>()
        for (i in dates.indices) {
            quizVals.getOrNull(i)?.let { quizEntries.add(Entry(i.toFloat(), it.toFloat())) }
            hwVals.getOrNull(i)?.let { hwEntries.add(Entry(i.toFloat(), it.toFloat())) }
        }
        val sets = ArrayList<ILineDataSet>()
        if (quizEntries.isNotEmpty()) sets.add(LineDataSet(quizEntries, "الاختبارات").apply {
            color = primaryColor(); setCircleColor(primaryColor()); lineWidth = 2f; circleRadius = 3f
            setDrawValues(false); setDrawCircleHole(false)
        })
        if (hwEntries.isNotEmpty()) sets.add(LineDataSet(hwEntries, "الواجبات").apply {
            color = col(R.color.success_green); setCircleColor(col(R.color.success_green)); lineWidth = 2f; circleRadius = 3f
            setDrawValues(false); setDrawCircleHole(false)
        })
        lineChart.apply {
            data = if (sets.isEmpty()) null else LineData(sets)
            description.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f; axisLeft.axisMaximum = 100f; axisLeft.textColor = chartTextColor()
            xAxis.position = XAxis.XAxisPosition.BOTTOM; xAxis.granularity = 1f; xAxis.textColor = chartTextColor()
            xAxis.valueFormatter = IndexAxisValueFormatter(dates.map { it.takeLast(5) })
            legend.textColor = col(R.color.ink)
            setNoDataText("لا توجد بيانات أداء"); setNoDataTextColor(chartTextColor())
            animateX(600); invalidate()
        }
    }

    /** أعمدة: مقارنة الطالب مع زملائه (عمود الطالب بلون مميّز). */
    private fun populateComparisonChart(list: List<ClassmateScore>?) {
        if (!::barChart.isInitialized) return
        val data = list ?: emptyList()
        val entries = ArrayList<BarEntry>()
        val colors = ArrayList<Int>()
        data.forEachIndexed { i, c ->
            entries.add(BarEntry(i.toFloat(), c.percentage.toFloat()))
            colors.add(if (c.isCurrent) col(R.color.gold) else primaryColor())
        }
        val set = BarDataSet(entries, "النسبة %").apply { setColors(colors); valueTextColor = chartTextColor() }
        barChart.apply {
            this.data = if (entries.isEmpty()) null else BarData(set).apply { barWidth = 0.6f }
            description.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f; axisLeft.axisMaximum = 100f; axisLeft.textColor = chartTextColor()
            xAxis.position = XAxis.XAxisPosition.BOTTOM; xAxis.granularity = 1f; xAxis.textColor = chartTextColor()
            xAxis.setLabelRotationAngle(-40f)
            xAxis.valueFormatter = IndexAxisValueFormatter(data.map { (it.name ?: "").take(8) })
            legend.isEnabled = false
            setNoDataText("لا توجد بيانات مقارنة"); setNoDataTextColor(chartTextColor())
            setFitBars(true); animateY(600); invalidate()
        }
    }

    /** يرسم المواد المسجّلة (مستقلّاً عن نجاح getReportData) موزّعةً حسب الحالة، مع إغناء الدرجات إن توفّرت. */
    private fun renderEnrolledSubjects(performanceMap: Map<String, SubjectPerformance> = emptyMap()) {
        if (!::activeSubjectsContainer.isInitialized) return
        activeSubjectsContainer.removeAllViews()
        completedSubjectsContainer.removeAllViews()
        allEnrolledSubjects.forEach { sub ->
            if (currentSelectedSubjectId != 0 && sub.subjectId != currentSelectedSubjectId) return@forEach
            val perf = performanceMap[sub.subjectName ?: ""]
            addSubjectToUI(sub.subjectName ?: "مادة", perf?.avgPercentage ?: 0.0, sub.status ?: "active")
        }
        checkEmptySections()
    }

    private fun addSubjectToUI(name: String, percentage: Double, status: String) {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        
        val isLow = percentage < 60
        val isActive = status.lowercase() == "active"

        val barLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(16), px(16), px(16))
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = px(16).toFloat()
                setStroke(2, col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, px(12)) }
            elevation = px(2).toFloat()
        }

        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 10f; gravity = Gravity.CENTER_VERTICAL }

        val tvStatus = TextView(ctx).apply {
            text = if (isActive) "مستمر" else "✓ مكتمل"
            setTextColor(if (isActive) primaryColor() else col(R.color.white))
            background = GradientDrawable().apply {
                setColor(if (isActive) col(R.color.surface_alt) else col(R.color.success_green))
                cornerRadius = px(8).toFloat()
            }
            setPadding(px(10), px(4), px(10), px(4)); textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f)
        }

        val tvName = TextView(ctx).apply {
            text = name; textSize = 16f; setTypeface(null, Typeface.BOLD)
            setTextColor(col(R.color.ink))
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 7f)
        }
        header.addView(tvStatus); header.addView(tvName); barLayout.addView(header)

        val tvPercent = TextView(ctx).apply {
            text = "$percentage%"; textSize = 18f; setTypeface(null, Typeface.BOLD); gravity = Gravity.END
            setTextColor(if (isLow) col(R.color.error_red) else primaryColor())
            setPadding(0, px(8), 0, px(4))
        }
        barLayout.addView(tvPercent)

        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(10)).apply { topMargin = px(6) }
            max = 100; this.progress = percentage.toInt()
            val pColor = if (isLow) col(R.color.error_red) else if (isActive) primaryColor() else col(R.color.success_green)
            progressTintList = android.content.res.ColorStateList.valueOf(pColor)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(col(R.color.line))
        }
        barLayout.addView(progress)

        if (!isActive) {
            val tvDone = TextView(ctx).apply {
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

    private fun createEmptyMsg(m: String) = TextView(requireContext()).apply { text = m; setTextColor(col(R.color.ink_faint)); gravity = Gravity.CENTER; setPadding(0, 20, 0, 20) }
}
