package com.example.studntapp

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AutoExamActivity : AppCompatActivity() {

    private lateinit var tvExamTitle: TextView
    private lateinit var tvSubjectName: TextView
    private var tvExamDate: TextView? = null
    private var tvExamTime: TextView? = null
    private lateinit var btnSubmitExam: Button
    private lateinit var layoutResultContainer: View
    private lateinit var tvFinalMark: TextView
    private lateinit var btnBackToMenu: Button
    private lateinit var llQuestionsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar

    private var examId: Int = 0
    private var studentId: Int = 0
    private var questionsList: List<AutoQuestion> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_exam)

        supportActionBar?.hide()

        examId = intent.getIntExtra("EXAM_ID", 0)
        val examTitle = intent.getStringExtra("EXAM_TITLE") ?: "اختبار تجريبي"
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: "المادة"
        val examDate = intent.getStringExtra("EXAM_DATE")      // "YYYY-MM-DD" or null
        val examTime = intent.getStringExtra("EXAM_TIME")      // "HH:mm:ss" or null

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        studentId = prefs.getInt("USER_ID", 0)

        // ربط العناصر الموجودة في الـ XML
        tvExamTitle = findViewById(R.id.tvExamTitle)
        tvSubjectName = findViewById(R.id.tvSubjectName)
        btnSubmitExam = findViewById(R.id.btnSubmitExam)
        layoutResultContainer = findViewById(R.id.layoutResultContainer)
        tvFinalMark = findViewById(R.id.tvFinalMark)
        btnBackToMenu = findViewById(R.id.btnBackToMenu)
        llQuestionsContainer = findViewById(R.id.llQuestionsContainer)
        progressBar = findViewById(R.id.progressBar)

        // محاولة ربط عناصر التاريخ والوقت إذا كانت موجودة في الـ XML
        try {
            tvExamDate = findViewById(R.id.tvExamDate)
            tvExamTime = findViewById(R.id.tvExamTime)
        } catch (e: Exception) {
            // ستبقى null وسيتم إنشاؤها برمجياً
        }

        tvExamTitle.text = examTitle
        tvSubjectName.text = subjectName

        // إدارة عرض التاريخ والوقت
        setupDateTimeDisplay(examDate, examTime)

        if (examId != 0) {
            loadQuestions()
        }

        btnSubmitExam.setOnClickListener {
            submitAnswers()
        }

        btnBackToMenu.setOnClickListener {
            finish()
        }
    }

    private fun setupDateTimeDisplay(date: String?, time: String?) {
        // إذا كان العنصران غير موجودين، قم بإنشائهما وإضافتهما قبل حاوية الأسئلة
        if (tvExamDate == null || tvExamTime == null) {
            val dateTimeLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 8, 16, 8)
                gravity = Gravity.CENTER_VERTICAL
            }

            tvExamDate = TextView(this).apply {
                text = date?.let { "📅 $it" } ?: ""
                textSize = 16f
                setPadding(8, 8, 16, 8)
            }
            tvExamTime = TextView(this).apply {
                text = time?.let { "⏰ ${it.take(5)}" } ?: ""  // عرض HH:mm فقط
                textSize = 16f
                setPadding(8, 8, 16, 8)
            }

            dateTimeLayout.addView(tvExamDate)
            dateTimeLayout.addView(tvExamTime)

            // إضافة هذا الـ Layout قبل llQuestionsContainer مباشرةً
            val parent = llQuestionsContainer.parent as? ViewGroup
            val index = parent?.indexOfChild(llQuestionsContainer) ?: -1
            if (parent != null && index >= 0) {
                parent.addView(dateTimeLayout, index)
            } else {
                // احتياط: إضافته كأول عنصر في الـ root view
                (findViewById<View>(android.R.id.content) as? ViewGroup)?.addView(dateTimeLayout, 0)
            }
        } else {
            // إذا كانا موجودين، فقط نقوم بتعيين النص
            tvExamDate?.text = date?.let { "📅 $it" } ?: ""
            tvExamTime?.text = time?.let { "⏰ ${it.take(5)}" } ?: ""
        }

        // إخفاء أي منهما إذا كانت القيمة فارغة
        if (date.isNullOrBlank()) tvExamDate?.visibility = View.GONE
        if (time.isNullOrBlank()) tvExamTime?.visibility = View.GONE
    }

    private fun loadQuestions() {
        progressBar.visibility = View.VISIBLE
        RetrofitClient.instance.getExamQuestions(examId = examId).enqueue(object : Callback<AutoQuestionResponse> {
            override fun onResponse(call: Call<AutoQuestionResponse>, response: Response<AutoQuestionResponse>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body()?.status == "success") {
                    questionsList = response.body()?.data ?: emptyList()
                    displayQuestions()
                } else {
                    Toast.makeText(this@AutoExamActivity, "فشل في تحميل الأسئلة", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AutoQuestionResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AutoExamActivity, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayQuestions() {
        llQuestionsContainer.removeAllViews()
        for ((index, q) in questionsList.withIndex()) {
            val qView = layoutInflater.inflate(android.R.layout.simple_list_item_1, llQuestionsContainer, false)
            val textView = qView as TextView
            textView.text = "${index + 1}. ${q.questionText} (${q.marks} درجة)"
            textView.setPadding(16, 32, 16, 16)
            textView.textSize = 18f
            llQuestionsContainer.addView(qView)

            when (q.questionType) {
                "mcq", "tf" -> {
                    val rg = RadioGroup(this)
                    rg.tag = "q_${q.id}"
                    for (opt in q.options ?: emptyList()) {
                        val rb = RadioButton(this)
                        rb.text = opt.text
                        rb.id = opt.id
                        rg.addView(rb)
                    }
                    llQuestionsContainer.addView(rg)
                }
                "short_essay", "fill_blank" -> {
                    val et = EditText(this)
                    et.tag = "q_${q.id}"
                    et.hint = "اكتب إجابتك هنا"
                    llQuestionsContainer.addView(et)
                }
                "long_essay" -> {
                    val et = EditText(this)
                    et.tag = "q_${q.id}"
                    et.hint = "اكتب مقالك هنا"
                    et.minLines = 3
                    llQuestionsContainer.addView(et)
                }
                // يمكن إضافة أنواع ordering و matching لاحقاً
            }

            // فاصل بين الأسئلة
            val divider = View(this)
            divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
            divider.setBackgroundColor(0xFFCCCCCC.toInt())
            llQuestionsContainer.addView(divider)
        }
    }

    private fun submitAnswers() {
        val answers = mutableListOf<Map<String, Any>>()

        for (q in questionsList) {
            val view = llQuestionsContainer.findViewWithTag<View>("q_${q.id}")
            val answerValue: Any = when (q.questionType) {
                "mcq", "tf" -> {
                    val rg = view as RadioGroup
                    rg.checkedRadioButtonId  // إرجاع ID الخيار المختار
                }
                "short_essay", "fill_blank", "long_essay" -> {
                    val et = view as EditText
                    et.text.toString()
                }
                else -> ""
            }

            answers.add(mapOf(
                "question_id" to q.id,
                "answer" to answerValue
            ))
        }

        val answersJson = Gson().toJson(answers)

        progressBar.visibility = View.VISIBLE
        btnSubmitExam.isEnabled = false

        RetrofitClient.instance.submitExam(studentId = studentId, examId = examId, answersJson = answersJson)
            .enqueue(object : Callback<SubmitExamResponse> {
                override fun onResponse(call: Call<SubmitExamResponse>, response: Response<SubmitExamResponse>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body()?.status == "success") {
                        showResult(response.body()?.score ?: 0.0)
                    } else {
                        btnSubmitExam.isEnabled = true
                        Toast.makeText(this@AutoExamActivity, response.body()?.message ?: "فشل في تسليم الاختبار", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<SubmitExamResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    btnSubmitExam.isEnabled = true
                    Toast.makeText(this@AutoExamActivity, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showResult(score: Double) {
        findViewById<View>(R.id.layoutExamContainer).visibility = View.GONE
        layoutResultContainer.visibility = View.VISIBLE

        val totalMarks = questionsList.sumOf { it.marks }
        tvFinalMark.text = "$score / $totalMarks"

        Toast.makeText(this, "تم تسليم الاختبار بنجاح", Toast.LENGTH_SHORT).show()
    }
}