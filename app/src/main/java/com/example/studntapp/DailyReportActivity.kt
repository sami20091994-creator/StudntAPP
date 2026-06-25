package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** الصفحة الرئيسية للطالب — مخصّصة للإعلانات + اختبارات اليوم. */
class DailyReportActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_report)
        supportActionBar?.title = "الرئيسية"

        // ترحيب باسم المستخدم
        val userName = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getString("USER_NAME", "")?.trim().orEmpty()
        findViewById<TextView>(R.id.tvWelcome).text = if (userName.isEmpty()) "مرحباً بك" else "مرحباً بك، $userName"

        val tvDate: TextView = findViewById(R.id.tvDailyDate)
        val rvQuizzes: RecyclerView = findViewById(R.id.rvDailyQuizzes)
        val rvAnn: RecyclerView = findViewById(R.id.rvAnnouncements)
        val tvAnnEmpty: TextView = findViewById(R.id.tvAnnEmpty)
        val tvQuizzesEmpty: TextView = findViewById(R.id.tvQuizzesEmpty)

        rvQuizzes.layoutManager = LinearLayoutManager(this)
        rvAnn.layoutManager = LinearLayoutManager(this) // قائمة عمودية

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val studentId = prefs.getInt("USER_ID", 0)

        // ====== الإعلانات من الخادم ======
        loadAnnouncements(studentId, rvAnn, tvAnnEmpty)

        // ====== التقرير اليومي / اختبارات اليوم ======
        RetrofitClient.instance.getDailyReport(studentId = studentId).enqueue(object : Callback<DailyReportResponse> {
            override fun onResponse(call: Call<DailyReportResponse>, response: Response<DailyReportResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    val data = response.body()?.data
                    tvDate.text = "تقرير يوم: ${data?.date ?: "اليوم"}"

                    prefs.edit()
                        .putString("LAST_CHECK_IN", data?.checkIn ?: "--:--")
                        .putString("LAST_CHECK_OUT", data?.checkOut ?: "--:--")
                        .apply()

                    val quizzes = data?.quizzes
                    if (quizzes.isNullOrEmpty()) {
                        tvQuizzesEmpty.visibility = View.VISIBLE
                        rvQuizzes.visibility = View.GONE
                    } else {
                        tvQuizzesEmpty.visibility = View.GONE
                        rvQuizzes.visibility = View.VISIBLE
                        rvQuizzes.adapter = DailyQuizzesAdapter(quizzes)
                    }
                }
            }
            override fun onFailure(call: Call<DailyReportResponse>, t: Throwable) {
                tvQuizzesEmpty.text = "تعذّر الاتصال بالخادم"
                tvQuizzesEmpty.visibility = View.VISIBLE
                rvQuizzes.visibility = View.GONE
            }
        })
    }

    private fun loadAnnouncements(userId: Int, rv: RecyclerView, empty: TextView) {
        RetrofitClient.instance.getAnnouncements(userId = userId).enqueue(object : Callback<AnnouncementResponse> {
            override fun onResponse(call: Call<AnnouncementResponse>, response: Response<AnnouncementResponse>) {
                val list = response.body()?.data ?: emptyList()
                if (list.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    empty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    rv.adapter = AnnouncementsAdapter(list) { item -> openArticle(item) }
                }
            }
            override fun onFailure(call: Call<AnnouncementResponse>, t: Throwable) {
                empty.text = "تعذّر تحميل الإعلانات"
                empty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            }
        })
    }

    private fun openArticle(item: AnnouncementItem) {
        val i = Intent(this, AnnouncementDetailActivity::class.java).apply {
            putExtra("title", item.title)
            putExtra("tag", item.tag ?: "إعلان")
            putExtra("date", item.createdAt ?: "")
            putExtra("image", item.image)
            putExtra("content", item.content)
            putExtra("article_url", item.articleUrl)
        }
        startActivity(i)
        overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
    }
}

class AnnouncementsAdapter(
    private val list: List<AnnouncementItem>,
    private val onClick: (AnnouncementItem) -> Unit
) : RecyclerView.Adapter<AnnouncementsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.cardAnnouncement)
        val image: android.widget.ImageView = v.findViewById(R.id.ivAnnImage)
        val tag: TextView = v.findViewById(R.id.tvAnnTag)
        val date: TextView = v.findViewById(R.id.tvAnnDate)
        val title: TextView = v.findViewById(R.id.tvAnnTitle)
        val body: TextView = v.findViewById(R.id.tvAnnBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_announcement, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = list[position]
        holder.tag.text = a.tag ?: "إعلان"
        holder.date.text = a.createdAt ?: ""
        holder.title.text = a.title ?: ""
        holder.body.text = a.summary ?: a.content ?: ""

        if (!a.image.isNullOrEmpty()) {
            val url = if (a.image.startsWith("http")) a.image else RetrofitClient.BASE_URL + a.image
            Glide.with(holder.image.context).load(url).into(holder.image)
        } else {
            holder.image.setImageDrawable(null)
        }
        holder.card.setOnClickListener { onClick(a) }
    }

    override fun getItemCount() = list.size
}

class DailyQuizzesAdapter(private val list: List<DailyQuiz>) : RecyclerView.Adapter<DailyQuizzesAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvSubjectName)
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
            holder.result.setTextColor(Color.parseColor("#2BB673"))
            holder.result.setBackgroundColor(Color.parseColor("#E4F6EC"))
        } else {
            holder.result.setTextColor(Color.parseColor("#E5484D"))
            holder.result.setBackgroundColor(Color.parseColor("#FDECEC"))
        }
    }
    override fun getItemCount() = list.size
}
