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

        val rv = findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        studentId = prefs.getInt("USER_ID", 0)

        RetrofitClient.instance.getEnrolledSubjects(studentId = studentId).enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                if (response.isSuccessful) {
                    val activeSubjects = response.body()?.data?.filter { it.status == "active" } ?: emptyList()

                    if (activeSubjects.isEmpty()) {
                        Toast.makeText(this@EvaluationActivity, "لا توجد مواد مسجلة لتقييمها", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val adapter = SubjectsAdapter(activeSubjects)
                    rv.adapter = adapter

                    adapter.setOnItemClickListener { subject ->
                        showDetailedRatingDialog(subject)
                    }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                android.util.Log.e("EVAL_FAIL", "getEnrolledSubjects failed (studentId=$studentId)", t)
                val reason = t.message ?: t.javaClass.simpleName
                Toast.makeText(this@EvaluationActivity, "خطأ في الاتصال بالخادم: $reason", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showDetailedRatingDialog(subject: SubjectData) {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 20)
        }

        val teacherRatingBars = mutableMapOf<String, RatingBar>()
        val adminRatingBars = mutableMapOf<String, RatingBar>()

        // عنوان قسم المعلم
        layout.addView(createSectionTitle("أولاً: تقييم المعلم", "#2F358F"))

        // توليد أسئلة المعلم
        for ((key, label) in teacherCriteria) {
            layout.addView(createQuestionLabel(label))
            val rb = createRatingBar()
            teacherRatingBars[key] = rb
            layout.addView(rb)
        }

        // فاصل
        layout.addView(createDivider())

        // عنوان قسم الإدارة
        layout.addView(createSectionTitle("ثانياً: تقييم الإدارة", "#2BB673"))

        // توليد أسئلة الإدارة
        for ((key, label) in adminCriteria) {
            layout.addView(createQuestionLabel(label))
            val rb = createRatingBar()
            adminRatingBars[key] = rb
            layout.addView(rb)
        }

        // فاصل
        layout.addView(createDivider())

        // الملاحظات والاقتراحات
        val notesInput = EditText(this).apply {
            hint = "ملاحظات إضافية..."
            setPadding(30, 30, 30, 30)
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            setBackgroundResource(android.R.drawable.edit_text)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 20, 0, 20) }
        }

        val suggestionsInput = EditText(this).apply {
            hint = "اقتراحات للتطوير..."
            setPadding(30, 30, 30, 30)
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            setBackgroundResource(android.R.drawable.edit_text)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 20) }
        }

        layout.addView(notesInput)
        layout.addView(suggestionsInput)
        scrollView.addView(layout)

        AlertDialog.Builder(this)
            .setTitle("تقييم: ${subject.teacherName ?: "المعلم"}")
            .setView(scrollView)
            .setPositiveButton("إرسال التقييم", null) // سنقوم ببرمجته يدوياً لمنع الإغلاق عند الخطأ
            .setNegativeButton("إلغاء", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    // التحقق من الإجابة على جميع الأسئلة
                    var allFilled = true
                    val evaluationData = mutableMapOf<String, String>()

                    for ((key, rb) in teacherRatingBars) {
                        if (rb.rating == 0f) allFilled = false
                        evaluationData[key] = rb.rating.toInt().toString()
                    }
                    for ((key, rb) in adminRatingBars) {
                        if (rb.rating == 0f) allFilled = false
                        evaluationData[key] = rb.rating.toInt().toString()
                    }

                    if (!allFilled) {
                        Toast.makeText(this@EvaluationActivity, "يرجى الإجابة على جميع الأسئلة بالنجوم", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val sId = subject.subjectId ?: 0
                    val tId = subject.teacherId ?: 0

                    if (sId == 0 || tId == 0) {
                        Toast.makeText(this@EvaluationActivity, "معرف المادة غير صالح", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // تجهيز البيانات للإرسال
                    evaluationData["student_id"] = studentId.toString()
                    evaluationData["subject_id"] = sId.toString()
                    evaluationData["teacher_id"] = tId.toString()
                    evaluationData["notes"] = notesInput.text.toString()
                    evaluationData["suggestions"] = suggestionsInput.text.toString()

                    submitDetailedEvaluation(evaluationData)
                    dismiss()
                }
            }
    }

    // دوال مساعدة لرسم الواجهة
    private fun createSectionTitle(title: String, colorStr: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor(colorStr))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 16)
        }
    }

    private fun createQuestionLabel(textLabel: String): TextView {
        return TextView(this).apply {
            text = textLabel
            setTextColor(Color.parseColor("#1B1E3A")) // text_dark
            textSize = 15f
            setPadding(0, 24, 0, 8)
            setLineSpacing(4f, 1f)
        }
    }

    private fun createRatingBar(): RatingBar {
        return RatingBar(this).apply {
            numStars = 5
            stepSize = 1.0f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                gravity = android.view.Gravity.START 
            }
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#E7E8F2")) // divider
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { 
                setMargins(0, 32, 0, 16) 
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