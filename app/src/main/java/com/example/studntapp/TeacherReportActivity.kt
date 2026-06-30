package com.example.studntapp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeacherReportActivity : BaseActivity() {

    private var userId = 0
    private var role = ""
    private lateinit var rvSubjects: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // يمكنك استخدام activity_subjects.xml أو إنشاء layout خاص، سنفترض وجود rvSubjects
        setContentView(R.layout.activity_teacher_report)
        supportActionBar?.title = "تقارير أداء الطلاب"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvSubjects = findViewById(R.id.rvReportSubjects)
        rvSubjects.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        userId = prefs.getInt("USER_ID", 0)
        role = prefs.getString("USER_ROLE", "student") ?: "student"

        loadTeacherSubjects()
        
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { loadTeacherSubjects() }
    }

    private fun loadTeacherSubjects() {
        // نرسل دور المعلم (role) لكي يقوم الـ API بجلب مواده هو فقط (مع اسم المجموعة المدمج)
        RetrofitClient.instance.getEnrolledSubjects(studentId = userId, role = role)
            .enqueue(object : Callback<SubjectListResponse> {
                override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                    findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                    if (response.isSuccessful) {
                        val subjects = response.body()?.data ?: emptyList()
                        if (subjects.isEmpty()) {
                            Toast.makeText(this@TeacherReportActivity, "لا توجد مواد مسندة إليك حالياً", Toast.LENGTH_LONG).show()
                        } else {
                            val adapter = ReportSubjectsAdapter(subjects) { subject ->
                                // عند الضغط على مادة، نعرض تقرير طلابها
                                subject.subjectId?.let {
                                    showStudentsReportDialog(it, subject.subjectName ?: "المادة")
                                }
                            }
                            rvSubjects.adapter = adapter
                        }
                    } else {
                        Toast.makeText(this@TeacherReportActivity, "خطأ في قراءة البيانات", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                    findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                    Toast.makeText(this@TeacherReportActivity, "خطأ في الاتصال بالخادم", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showStudentsReportDialog(subjectId: Int, subjectName: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_students_report, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val rvStudents = view.findViewById<RecyclerView>(R.id.rvStudentsList)

        tvTitle.text = "تقرير أداء الطلاب: $subjectName"
        rvStudents.layoutManager = LinearLayoutManager(this)

        // جلب تقدم الطلاب لهذه المادة تحديداً
        RetrofitClient.instance.getStudentsProgress(subjectId = subjectId)
            .enqueue(object : Callback<StudentProgressResponse> {
                override fun onResponse(call: Call<StudentProgressResponse>, response: Response<StudentProgressResponse>) {
                    val students = response.body()?.data ?: emptyList()
                    if (students.isEmpty()) {
                        Toast.makeText(this@TeacherReportActivity, "لا يوجد طلاب مسجلين في هذه المادة", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        rvStudents.adapter = StudentProgressAdapter(students)
                    }
                }
                override fun onFailure(call: Call<StudentProgressResponse>, t: Throwable) {
                    Toast.makeText(this@TeacherReportActivity, "فشل في جلب أداء الطلاب", Toast.LENGTH_SHORT).show()
                }
            })

        dialog.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// ==========================================
// محول (Adapter) لعرض المواد بشكل أنيق
// ==========================================
class ReportSubjectsAdapter(
    private val list: List<SubjectData>,
    private val onItemClick: (SubjectData) -> Unit
) : RecyclerView.Adapter<ReportSubjectsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvSubjectName)
        val teacher: TextView = v.findViewById(R.id.tvTeacherName)
//        val group: TextView = v.findViewById(R.id.tvGroup)
        val semester: TextView = v.findViewById(R.id.tvSemester)
        val status: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        val ctx = holder.itemView.context

        // اسم المادة + استخراج تاغ المجموعة [..] أو (..) إن وُجد داخل الاسم.
        val rawName = item.subjectName ?: ""
        val groupMatch = Regex("[\\[(]([^\\])]+)[\\])]").find(rawName)
        val groupTag = groupMatch?.groupValues?.get(1)?.trim()
        holder.title.text = if (groupTag != null) rawName.replace(groupMatch!!.value, "").trim() else rawName

        // فقاعة المدرّس
        val t = item.teacherName?.takeIf { it.isNotBlank() && it != "null" }
        holder.teacher.text = t ?: ""
        holder.teacher.visibility = if (t != null) View.VISIBLE else View.GONE

        // فقاعة المجموعة (subject_groups) بجانب اسم المدرّس
//        holder.group.text = groupTag ?: ""
//        holder.group.visibility = if (groupTag != null) View.VISIBLE else View.GONE

        // فقاعة الحالة: مكتملة (outline رمادي + strikethrough) / نشطة (filled green)
        val raw = item.status?.trim()?.lowercase()
        when {
            raw == "completed" || raw == "complete" || raw == "done" || raw == "finished" || raw == "مكتملة" -> {
                holder.status.text = "  مكتملة  "
                holder.status.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_completed)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.status_completed_text))
                holder.status.paintFlags = holder.status.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                holder.status.letterSpacing = 0.05f
                holder.status.visibility = View.VISIBLE
            }
            raw == "active" || raw == "نشطة" || raw == "ongoing" -> {
                holder.status.text = "نشطة"
                holder.status.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_active)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.status_active_text))
                holder.status.paintFlags = holder.status.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.status.letterSpacing = 0f
                holder.status.visibility = View.VISIBLE
            }
            else -> holder.status.visibility = View.GONE
        }

        holder.semester.visibility = View.GONE
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = list.size
}

// ==========================================
// محول (Adapter) لعرض الطلاب والنسبة المئوية
// ==========================================
class StudentProgressAdapter(private val list: List<StudentProgress>) : RecyclerView.Adapter<StudentProgressAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(android.R.id.text1)
        val tvScore: TextView = v.findViewById(android.R.id.text2)
        val progressBar: ProgressBar = v.findViewById(android.R.id.progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(30, 25, 30, 25)
        }

        val headerLayout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val tvName = TextView(parent.context).apply {
            id = android.R.id.text1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 16f
            setTextColor(Color.parseColor("#1B1E3A"))
        }

        val tvScore = TextView(parent.context).apply {
            id = android.R.id.text2
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        headerLayout.addView(tvName)
        headerLayout.addView(tvScore)

        val pb = ProgressBar(parent.context, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = android.R.id.progress
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 24).apply { topMargin = 15 }
            max = 100
        }

        layout.addView(headerLayout)
        layout.addView(pb)

        return VH(layout)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvName.text = item.studentName ?: "طالب"
        holder.tvScore.text = "${item.averageScore}%"
        holder.progressBar.progress = item.averageScore.toInt()

        // مطابقة الألوان مع لوحة تحكم الويب بناءً على النسبة
        when {
            item.averageScore >= 85 -> holder.tvScore.setTextColor(Color.parseColor("#2BB673")) // ممتاز (أخضر)
            item.averageScore >= 60 -> holder.tvScore.setTextColor(Color.parseColor("#4C53A4")) // جيد (أزرق)
            item.averageScore >= 40 -> holder.tvScore.setTextColor(Color.parseColor("#F7A61B")) // مقبول (برتقالي)
            else -> holder.tvScore.setTextColor(Color.parseColor("#E5484D")) // ضعيف (أحمر)
        }
    }

    override fun getItemCount() = list.size
}