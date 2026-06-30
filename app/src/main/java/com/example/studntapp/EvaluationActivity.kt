package com.example.studntapp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EvaluationActivity : BaseActivity() {

    private var studentId = 0
    private var role = "student"

    // معايير تقييم المعلم (مطابقة لملف الويب)
    private val teacherCriteria = mapOf(
        "teacher_voice" to "صوت المعلم مسموع وواضح",
        "teacher_handwriting" to "خطه مقروء وواضح",
        "teacher_organization" to "يقدم المعلومة منظمة على السبورة",
        "teacher_questions" to "يقدم اسئلة علمية ويعلق عليها",
        "teacher_followup" to "يتابع تطويري في التحصيل العلمي",
        "teacher_class_control" to "قدرة المعلم على ضبط الصف",
        "teacher_favorite" to "انتظر مادته وهو معلمي المفضل",
        "teacher_problem_solving" to "ألجأ اليه عند حل المشاكل العلمية"
    )

    // معايير تقييم الإدارة
    private val adminCriteria = mapOf(
        "admin_treatment" to "معاملة الإدارة للطالب",
        "admin_care" to "اهتمام الإدارة بالطالب"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subjects)
        supportActionBar?.title = "التقييم الشامل"

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
        // نفس مصدر "المواد الدراسية" (get_subjects) — يتجنّب الـendpoint المعطوب الذي يسبب خطأ التحليل.
        RetrofitClient.instance.getSubjects(userId = studentId, role = role).enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (response.isSuccessful) {
                    val activeSubjects = response.body()?.data?.filter { (it.status ?: "active") != "completed" } ?: emptyList()

                    if (activeSubjects.isEmpty()) {
                        Toast.makeText(this@EvaluationActivity, "لا توجد مواد مسجلة لتقييمها", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // بطاقة مستقلة لتقييم الإدارة (مرّة واحدة) بدل تكراره مع كل مادة.
                    val adminItem = SubjectData(subjectId = -1, subjectName = "تقييم الإدارة العامة", teacherName = "إدارة المعهد")
                    val adapter = SubjectsAdapter(activeSubjects + listOf(adminItem))
                    rv.adapter = adapter

                    adapter.setOnItemClickListener { subject ->
                        if (subject.subjectId == -1) showAdminEvalDialog()
                        else showTeacherEvalDialog(subject)
                    }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                android.util.Log.e("EVAL_FAIL", "getEnrolledSubjects failed (studentId=$studentId)", t)
                val reason = t.message ?: t.javaClass.simpleName
                Toast.makeText(this@EvaluationActivity, "خطأ في الاتصال بالخادم: $reason", Toast.LENGTH_LONG).show()
            }
        })
    }

    /** تقييم المعلم لمادة محدّدة (معايير المعلم فقط). */
    private fun showTeacherEvalDialog(subject: SubjectData) {
        buildEvalDialog("تقييم المعلم: ${subject.teacherName ?: "المعلم"}", "معايير تقييم المعلم", primaryColor(), teacherCriteria) { ratings, notes, suggestions ->
            val sId = subject.subjectId ?: 0
            val tId = subject.teacherId ?: 0
            if (sId == 0 || tId == 0) {
                Toast.makeText(this, "معرف المادة غير صالح", Toast.LENGTH_SHORT).show()
                return@buildEvalDialog false
            }
            val data = ratings.mapValues { it.value.toString() }.toMutableMap()
            data["student_id"] = studentId.toString()
            data["subject_id"] = sId.toString()
            data["teacher_id"] = tId.toString()
            data["notes"] = notes
            data["suggestions"] = suggestions
            submitDetailedEvaluation(data)
            true
        }
    }

    /** تقييم الإدارة (مرّة واحدة، مستقل عن المواد). */
    private fun showAdminEvalDialog() {
        buildEvalDialog("تقييم الإدارة العامة", "معايير تقييم الإدارة", col(R.color.success_green), adminCriteria) { ratings, notes, suggestions ->
            val data = ratings.mapValues { it.value.toString() }.toMutableMap()
            data["student_id"] = studentId.toString()
            data["target"] = "admin"
            data["notes"] = notes
            data["suggestions"] = suggestions
            submitDetailedEvaluation(data)
            true
        }
    }

    /** نافذة تقييم عامة: عنوان + معايير بالنجوم + ملاحظات/اقتراحات. onSubmit يُرجع true عند النجاح ليُغلق. */
    private fun buildEvalDialog(
        titleText: String,
        sectionTitle: String,
        sectionColor: Int,
        criteria: Map<String, String>,
        onSubmit: (ratings: Map<String, Int>, notes: String, suggestions: String) -> Boolean
    ) {
        val scrollView = ScrollView(this).apply { setBackgroundColor(col(R.color.surface)) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.surface))
            setPadding(48, 40, 48, 24)
        }
        val bars = mutableMapOf<String, MoodSelector>()

        layout.addView(TextView(this).apply {
            text = titleText
            textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(col(R.color.ink)); setPadding(dp(2), 0, dp(2), dp(6))
        })
        layout.addView(createSectionTitle(sectionTitle, sectionColor))
        for ((key, label) in criteria) {
            val (card, rb) = questionCard(label)
            bars[key] = rb
            layout.addView(card)
        }
        layout.addView(createDivider())
        val notesInput = themedInput("ملاحظات إضافية...")
        val suggestionsInput = themedInput("اقتراحات للتطوير...")
        layout.addView(notesInput)
        layout.addView(suggestionsInput)
        scrollView.addView(layout)

        AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("إرسال التقييم", null)
            .setNegativeButton("إلغاء", null)
            .create().apply {
                window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat(); setColor(col(R.color.surface))
                })
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(primaryColor())
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(col(R.color.ink_muted))
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    var allFilled = true
                    val ratings = mutableMapOf<String, Int>()
                    for ((key, ms) in bars) {
                        if (ms.rating == 0) allFilled = false
                        ratings[key] = ms.rating
                    }
                    if (!allFilled) {
                        Toast.makeText(this@EvaluationActivity, "يرجى اختيار وجه لكل سؤال", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (onSubmit(ratings, notesInput.text.toString(), suggestionsInput.text.toString())) dismiss()
                }
            }
    }

    // ===== دوال مساعدة لرسم الواجهة (متكيّفة مع الثيمات والوضع المظلم) =====
    private fun col(id: Int) = androidx.core.content.ContextCompat.getColor(this, id)
    private fun primaryColor(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return tv.data
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun createSectionTitle(title: String, color: Int): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(color)
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(2), dp(18), dp(2), dp(10))
        }
    }

    /** بطاقة سؤال عصرية: عنوان + وجوه مزاجية (Mood Faces) للتعبير عن الرضى. */
    private fun questionCard(label: String): Pair<LinearLayout, MoodSelector> {
        val mood = MoodSelector(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(col(R.color.surface_alt))
                setStroke(dp(1), col(R.color.line))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            addView(TextView(this@EvaluationActivity).apply {
                text = label; textSize = 14.5f
                setTextColor(col(R.color.ink)); setLineSpacing(dp(2).toFloat(), 1f)
            })
            addView(mood)
        }
        return card to mood
    }

    /** حقل إدخال عصري متكيّف مع الثيم. */
    private fun themedInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(col(R.color.ink_muted))
            setTextColor(col(R.color.ink))
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(col(R.color.surface_alt))
                setStroke(dp(1), col(R.color.line))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(col(R.color.line))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(18), 0, dp(8))
            }
        }
    }

    private fun submitDetailedEvaluation(data: Map<String, String>) {
        RetrofitClient.instance.saveDetailedEvaluation(fields = data).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    Toast.makeText(this@EvaluationActivity, response.body()?.message ?: "تم إرسال التقييم شكراً لك!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@EvaluationActivity, "فشل الإرسال", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                Toast.makeText(this@EvaluationActivity, "تأكد من اتصالك بالإنترنت", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

/** صفّ وجوه مزاجية لاختيار مستوى الرضى (1=غاضب ... 5=ممتاز). rating=0 يعني لا اختيار. */
class MoodSelector(ctx: Context) : LinearLayout(ctx) {
    var rating: Int = 0
        private set
    private val faces = listOf("😠", "🙁", "😐", "😁", "🤩")
    private val items = mutableListOf<TextView>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val d = resources.displayMetrics.density
        faces.forEachIndexed { i, face ->
            val tv = TextView(ctx).apply {
                text = face
                textSize = 24f
                gravity = Gravity.CENTER
                alpha = 0.7f // واضح حتى في اللايت مود (بلا تعتيم زائد)
                scaleX = 0.92f; scaleY = 0.92f
                val p = (8 * d).toInt()
                setPadding(p, p, p, p)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { select(i + 1) }
            }
            items.add(tv)
            addView(tv)
        }
    }

    private fun select(value: Int) {
        rating = value
        val d = resources.displayMetrics.density
        items.forEachIndexed { idx, tv ->
            val sel = idx == value - 1
            tv.alpha = if (sel) 1f else 0.7f
            // المختار: خلفية دائرية مميّزة بدل الاعتماد على التعتيم.
            tv.background = if (sel) android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33F7A61B) // ذهبي شفاف يظهر باللايت والدارك
            } else null
            tv.animate().scaleX(if (sel) 1.3f else 0.92f).scaleY(if (sel) 1.3f else 0.92f).setDuration(120).start()
        }
    }
}