package com.example.studntapp

import android.content.Context
import android.graphics.Color
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

class DailyReportActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_report)
        supportActionBar?.title = "التقرير اليومي"

        val tvDate: TextView = findViewById(R.id.tvDailyDate)
        val tvCheckIn: TextView = findViewById(R.id.tvCheckIn)
        val tvCheckOut: TextView = findViewById(R.id.tvCheckOut)
        val rvQuizzes: RecyclerView = findViewById(R.id.rvDailyQuizzes)

        rvQuizzes.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val studentId = prefs.getInt("USER_ID", 0)

        RetrofitClient.instance.getDailyReport(studentId = studentId).enqueue(object : Callback<DailyReportResponse> {
            override fun onResponse(call: Call<DailyReportResponse>, response: Response<DailyReportResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    val data = response.body()?.data
                    tvDate.text = "تقرير يوم: ${data?.date}"
                    tvCheckIn.text = data?.checkIn
                    tvCheckOut.text = data?.checkOut

                    val quizzes = data?.quizzes
                    if (quizzes.isNullOrEmpty()) {
                        Toast.makeText(this@DailyReportActivity, "لا توجد اختبارات اليوم", Toast.LENGTH_SHORT).show()
                    } else {
                        rvQuizzes.adapter = DailyQuizzesAdapter(quizzes)
                    }
                }
            }
            override fun onFailure(call: Call<DailyReportResponse>, t: Throwable) {
                Toast.makeText(this@DailyReportActivity, "خطأ في الاتصال بالخادم", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

class DailyQuizzesAdapter(private val list: List<DailyQuiz>) : RecyclerView.Adapter<DailyQuizzesAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvSubjectName) // نستخدم نفس تصميم البطاقة لتوفير الملفات
        val subject: TextView = v.findViewById(R.id.tvTeacherName)
        val result: TextView = v.findViewById(R.id.tvStatus)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val q = list[position]
        holder.title.text = q.title
        holder.subject.text = "${q.subjectName ?: "عام"} - ${q.marksObtained}/${q.totalMarks}"

        holder.result.text = "${q.percentage}%"
        if (q.percentage >= 50) {
            holder.result.setTextColor(Color.parseColor("#27ae60")) // أخضر ناجح
            holder.result.setBackgroundColor(Color.parseColor("#e8f5e9"))
        } else {
            holder.result.setTextColor(Color.parseColor("#c0392b")) // أحمر راسب
            holder.result.setBackgroundColor(Color.parseColor("#fdf2f0"))
        }
    }
    override fun getItemCount() = list.size
}
