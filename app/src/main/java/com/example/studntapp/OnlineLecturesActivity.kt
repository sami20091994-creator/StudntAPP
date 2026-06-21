package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
        val title: TextView = v.findViewById(android.R.id.text1)
        val subtitle: TextView = v.findViewById(android.R.id.text2)
        val divider: View = v.findViewById(android.R.id.background) // استخدام أي id وهمي كمرجع
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(40, 40, 40, 40)
            setBackgroundColor(ContextCompat.getColor(parent.context, R.color.surface))
        }

        val tvTitle = TextView(parent.context).apply {
            id = android.R.id.text1
            textSize = 17f
            setTextColor(ContextCompat.getColor(parent.context, R.color.ink))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nav_lectures, 0, 0, 0)
            compoundDrawablePadding = 24
            val tv = android.util.TypedValue()
            parent.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(tv.data)
        }

        val tvSub = TextView(parent.context).apply {
            id = android.R.id.text2
            textSize = 13f
            setPadding(0, 10, 0, 0)
        }

        val divider = View(parent.context).apply {
            id = android.R.id.background
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 30 }
        }

        layout.addView(tvTitle)
        layout.addView(tvSub)
        layout.addView(divider)

        return VH(layout)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.title.text = item.subjectName

        // التحقق من حالة البث
        val isLive = item.isLive ?: 0
        if (isLive == 1) {
            holder.subtitle.text = "🔴 بث مباشر الآن - اضغط للدخول"
            holder.subtitle.setTextColor(Color.parseColor("#e84393"))
            holder.divider.setBackgroundColor(Color.parseColor("#fd79a8"))
        } else {
            holder.subtitle.text = "⚪ لا يوجد بث حالياً - الدخول للمساحة والسبورة"
            holder.subtitle.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.ink_muted))
            holder.divider.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.line))
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = list.size
}