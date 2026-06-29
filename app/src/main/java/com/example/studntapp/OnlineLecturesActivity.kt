package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OnlineLecturesActivity : BaseActivity() {

    private lateinit var rvLectures: RecyclerView
    private var role = ""
    private var userId = 0
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_lectures)

        supportActionBar?.title = "قاعات المحاضرات والمواد"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        role = prefs.getString("USER_ROLE", "student") ?: "student"
        userId = prefs.getInt("USER_ID", 0)
        userName = prefs.getString("USER_NAME", "طالب من التطبيق") ?: "طالب من التطبيق"

        rvLectures = findViewById(R.id.rvLectures)
        rvLectures.layoutManager = LinearLayoutManager(this)

        loadOnlineRooms()
    }

    private fun loadOnlineRooms() {
        RetrofitClient.instance.getSubjects(userId = userId, role = role)
            .enqueue(object : Callback<SubjectListResponse> {
                override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val subjects = response.body()?.data ?: emptyList()

                        if (subjects.isEmpty()) {
                            Toast.makeText(this@OnlineLecturesActivity, "لا توجد مواد مسجلة لك", Toast.LENGTH_SHORT).show()
                        } else {
                            rvLectures.adapter = OnlineLecturesAdapter(subjects) { subject ->
                                // حل مشكلة "المادة غير محددة" بقراءة المتغير الصحيح
                                val subId = subject.subjectId ?: subject.id ?: 0

                                if (subId != 0) {
                                    val intent = Intent(this@OnlineLecturesActivity, LectureRoomActivity::class.java).apply {
                                        putExtra("SUBJECT_ID", subId)
                                        putExtra("USER_ID", userId)
                                        putExtra("USER_NAME", userName)
                                        putExtra("USER_ROLE", role)
                                    }
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(this@OnlineLecturesActivity, "خطأ: تعذر قراءة معرف المادة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this@OnlineLecturesActivity, "فشل في قراءة البيانات", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                    Toast.makeText(this@OnlineLecturesActivity, "فشل الاتصال بالخادم", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

class OnlineLecturesAdapter(
    private val list: List<SubjectData>,
    private val onItemClick: (SubjectData) -> Unit
) : RecyclerView.Adapter<OnlineLecturesAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvLectureTitle)
        val status: TextView = v.findViewById(R.id.tvLectureStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lecture_card, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        val ctx = holder.itemView.context
        holder.title.text = item.subjectName

        if ((item.isLive ?: 0) == 1) {
            holder.status.text = "● بث مباشر الآن"
            holder.status.setTextColor(Color.parseColor("#E84393"))
        } else {
            holder.status.text = "الدخول للمساحة والسبورة"
            holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.ink_muted))
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = list.size
}