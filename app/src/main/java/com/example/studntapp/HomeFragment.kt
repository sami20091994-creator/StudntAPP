package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** الصفحة الرئيسية للطالب داخل الـ ViewPager (إعلانات + اختبارات اليوم). */
class HomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.activity_daily_report, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        val tvDate: TextView = view.findViewById(R.id.tvDailyDate)
        val rvQuizzes: RecyclerView = view.findViewById(R.id.rvDailyQuizzes)
        val rvAnn: RecyclerView = view.findViewById(R.id.rvAnnouncements)
        val tvAnnEmpty: TextView = view.findViewById(R.id.tvAnnEmpty)
        val tvQuizzesEmpty: TextView = view.findViewById(R.id.tvQuizzesEmpty)

        rvQuizzes.layoutManager = LinearLayoutManager(ctx)
        rvAnn.layoutManager = LinearLayoutManager(ctx)

        val prefs = ctx.getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val studentId = prefs.getInt("USER_ID", 0)

        // ترحيب باسم المستخدم
        val userName = prefs.getString("USER_NAME", "")?.trim().orEmpty()
        val tvWelcome: TextView = view.findViewById(R.id.tvWelcome)
        tvWelcome.text = if (userName.isEmpty()) "مرحباً بك" else "مرحباً بك، $userName"

        // الضغط على الاسم → تبويب التقرير (زر أقصى اليسار في الشريط السفلي = ReportFragment).
        view.findViewById<View>(R.id.welcomeBlock).setOnClickListener {
            (requireActivity() as? HomeShellActivity)?.goToTab(3)
        }

        // نسبة التقييم (المعدل التراكمي) يسار البطاقة.
        loadEvalPercent(studentId, view.findViewById(R.id.tvHomeEvalPercent))

        // زر "عرض الكل" → صفحة كل الإعلانات.
        val btnSeeAll = view.findViewById<TextView>(R.id.tvAnnSeeAll)
        btnSeeAll.setOnClickListener {
            startActivity(Intent(ctx, AllAnnouncementsActivity::class.java))
            requireActivity().overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
        }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnSeeAll, "عرض كل إعلانات المعهد")

        // زر "عرض سجل الاختبارات" → صفحة السجل المؤرّخ.
        val btnHistory = view.findViewById<TextView>(R.id.tvShowExamHistory)
        btnHistory.setOnClickListener {
            startActivity(Intent(ctx, ExamHistoryActivity::class.java))
            requireActivity().overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
        }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnHistory, "سجل اختباراتك مفروزاً حسب التاريخ")

        loadAnnouncements(studentId, rvAnn, tvAnnEmpty)
        loadDailyReport(studentId, tvDate, prefs, rvQuizzes, tvQuizzesEmpty, view)
        
        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh?.setOnRefreshListener {
            loadAnnouncements(studentId, rvAnn, tvAnnEmpty)
            loadDailyReport(studentId, tvDate, prefs, rvQuizzes, tvQuizzesEmpty, view)
        }
    }

    private fun loadDailyReport(studentId: Int, tvDate: TextView, prefs: android.content.SharedPreferences, rvQuizzes: RecyclerView, tvQuizzesEmpty: TextView, view: View) {        RetrofitClient.instance.getDailyReport(studentId = studentId).enqueue(object : Callback<DailyReportResponse> {
            override fun onResponse(call: Call<DailyReportResponse>, response: Response<DailyReportResponse>) {
                view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (!isAdded) return
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
                view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (!isAdded) return
                tvQuizzesEmpty.text = "تعذّر الاتصال بالخادم"
                tvQuizzesEmpty.visibility = View.VISIBLE
                rvQuizzes.visibility = View.GONE
            }
        })
    }

    /** المعدل العام — نفس قيمة الـ API لصفحة التقرير (get_report_data, subject_id=0). قيمة حيّة فقط، بلا كاش. */
    private fun loadEvalPercent(studentId: Int, tv: TextView) {
        tv.text = "--%"
        RetrofitClient.instance.getReportData(studentId = studentId, subjectId = 0)
            .enqueue(object : Callback<ReportResponse> {
                override fun onResponse(call: Call<ReportResponse>, response: Response<ReportResponse>) {
                    if (!isAdded) return
                    tv.text = if (response.isSuccessful && response.body()?.status == "success")
                        "${response.body()?.data?.average ?: 0.0}%" else "--%"
                }
                override fun onFailure(call: Call<ReportResponse>, t: Throwable) {
                    if (!isAdded) return
                    tv.text = "--%"
                }
            })
    }

    private fun loadAnnouncements(userId: Int, rv: RecyclerView, empty: TextView) {
        RetrofitClient.instance.getAnnouncements(userId = userId).enqueue(object : Callback<AnnouncementResponse> {
            override fun onResponse(call: Call<AnnouncementResponse>, response: Response<AnnouncementResponse>) {
                view?.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (!isAdded) return
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
                view?.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (!isAdded) return
                empty.text = "تعذّر تحميل الإعلانات"
                empty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            }
        })
    }

    private fun openArticle(item: AnnouncementItem) {
        val i = Intent(requireContext(), AnnouncementDetailActivity::class.java).apply {
            putExtra("title", item.title)
            putExtra("tag", item.tag ?: "إعلان")
            putExtra("date", item.createdAt ?: "")
            putExtra("image", item.image)
            putExtra("content", item.content)
            putExtra("article_url", item.articleUrl)
        }
        startActivity(i)
        requireActivity().overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
    }
}
