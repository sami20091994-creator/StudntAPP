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
import android.content.Intent

class SubjectsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subjects)

        val rv = findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = LinearLayoutManager(this)

        val studentId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)

        loadSubjects(studentId, rv)
        
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh?.setOnRefreshListener { loadSubjects(studentId, rv) }
    }

    private fun loadSubjects(studentId: Int, rv: RecyclerView) {

        RetrofitClient.instance.getEnrolledSubjects(studentId = studentId).enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (response.isSuccessful) {
                    val subjects = response.body()?.data ?: emptyList()
                    val adapter = SubjectsAdapter(subjects)
                    
                    adapter.setOnItemClickListener { subject ->
                        val intent = Intent(this@SubjectsActivity, MaterialsActivity::class.java)
                        intent.putExtra("SUBJECT_ID", subject.subjectId ?: 0)
                        intent.putExtra("SUBJECT_NAME", subject.subjectName)
                        startActivity(intent)
                    }
                    
                    rv.adapter = adapter
                }
            }

            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                Toast.makeText(this@SubjectsActivity, "فشل الاتصال بالخادم", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

// محول القائمة (Adapter) مدمج لتسهيل الكود
// هذا هو المحول (Adapter) المحدث الذي يدعم النقر على العناصر
class SubjectsAdapter(private val list: List<SubjectData>) : RecyclerView.Adapter<SubjectsAdapter.VH>() {

    // 1. هنا نُعرّف متغير النقر (الذي سيمسك الحدث عند الضغط)
    private var onItemClickListener: ((SubjectData) -> Unit)? = null

    // 2. هذه الدالة تسمح لنا بتفعيل النقر من خارج الكلاس (من الأكتيفيتي)
    fun setOnItemClickListener(listener: (SubjectData) -> Unit) {
        onItemClickListener = listener
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvSubjectName)
        val teacher: TextView = v.findViewById(R.id.tvTeacherName)
        val status: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.name.text = item.subjectName
        holder.teacher.text = "المعلم: ${item.teacherName ?: "غير محدد"}"

        if (item.status == "active") {
            holder.status.text = "نشط"
            holder.status.setTextColor(android.graphics.Color.parseColor("#2BB673"))
        } else {
            holder.status.text = "غير نشط"
            holder.status.setTextColor(android.graphics.Color.parseColor("#E5484D"))
        }

        // 3. هنا نخبر الأندرويد: "عندما يتم النقر على هذا العنصر، قم بتشغيل حدث النقر وأرسل بيانات هذه المادة"
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(item)
        }
    }

    override fun getItemCount() = list.size
}