package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AutoExamsListActivity : BaseActivity() {

    private lateinit var rvExams: RecyclerView
    private lateinit var progressBar: android.widget.ProgressBar
    private var studentId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_exams_list)

        // نُبقي شريط الهيكل ظاهراً ليظهر زر السايدبار كباقي الصفحات.
        supportActionBar?.title = "الاختبارات الذكية"

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        studentId = prefs.getInt("USER_ID", 0)

        rvExams = findViewById(R.id.rvSubjects) // Using existing id from XML
        progressBar = findViewById(R.id.progressBar)

        rvExams.layoutManager = LinearLayoutManager(this)

        loadAvailableExams()
        
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh?.setOnRefreshListener { loadAvailableExams() }
    }

    private fun loadAvailableExams() {
        progressBar.visibility = View.VISIBLE
        RetrofitClient.instance.getAvailableAutoExams(studentId = studentId)
            .enqueue(object : Callback<AutoExamListResponse> {
                override fun onResponse(
                    call: Call<AutoExamListResponse>,
                    response: Response<AutoExamListResponse>
                ) {
                    findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                    progressBar.visibility = View.GONE
                    android.util.Log.d("EXAMS_DEBUG", "Response: ${response.code()}, Body: ${response.body()}")
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val exams = response.body()?.data ?: emptyList()
                        rvExams.adapter = ExamAdapter(exams) { exam ->
                            val intent = Intent(this@AutoExamsListActivity, AutoExamActivity::class.java).apply {
                                putExtra("EXAM_ID", exam.id)
                                putExtra("EXAM_TITLE", exam.title)
                                putExtra("SUBJECT_NAME", exam.subjectName)
                                putExtra("EXAM_DATE", exam.examDate)
                                putExtra("EXAM_TIME", exam.examTime)
                            }
                            startActivity(intent)
                        }
                    } else {
                        showEmptyMessage()
                    }
                }

                override fun onFailure(call: Call<AutoExamListResponse>, t: Throwable) {
                    findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@AutoExamsListActivity, "خطأ في الاتصال بالخادم", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showEmptyMessage() {
        Toast.makeText(this, "لا توجد اختبارات متاحة حالياً", Toast.LENGTH_LONG).show()
    }
}

// ========== Adapter ==========
class ExamAdapter(
    private val exams: List<AutoExam>,
    private val onItemClick: (AutoExam) -> Unit
) : RecyclerView.Adapter<ExamAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvExamTitle)
        val tvSubject: TextView = view.findViewById(R.id.tvSubjectName)
        val tvDate: TextView = view.findViewById(R.id.tvExamDate)
        val tvTime: TextView = view.findViewById(R.id.tvExamTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_auto_exam, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exam = exams[position]
        holder.tvTitle.text = exam.title
        holder.tvSubject.text = exam.subjectName
        holder.tvDate.text = exam.examDate ?: "----"
        holder.tvTime.text = exam.examTime?.take(5) ?: "--:--"
        holder.itemView.setOnClickListener { onItemClick(exam) }
    }

    override fun getItemCount() = exams.size
}
