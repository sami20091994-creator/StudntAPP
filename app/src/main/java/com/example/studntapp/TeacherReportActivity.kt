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
    }

    private fun loadTeacherSubjects() {
        // نرسل دور المعلم (role) لكي يقوم الـ API بجلب مواده هو فقط (مع اسم المجموعة المدمج)
        RetrofitClient.instance.getEnrolledSubjects(studentId = userId, role = role)
            .enqueue(object : Callback<SubjectListResponse> {
                override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
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
                    Toast.makeText(this@TeacherReportActivity, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
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
        val title: TextView = v.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // بناء تصميم بسيط برمجياً لعرض اسم المادة + المجموعة
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.WHITE)
        }

        val tvTitle = TextView(parent.context).apply {
            id = android.R.id.text1
            textSize = 18f
            setTextColor(Color.parseColor("#2d3436"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_sort_by_size, 0, 0, 0)
            compoundDrawablePadding = 20
        }

        val divider = View(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 30 }
            setBackgroundColor(Color.parseColor("#F1F5F9"))
        }

        layout.addView(tvTitle)
        layout.addView(divider)

        return VH(layout)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        // الاسم هنا سيأتي من السيرفر مدمجاً بـ [اسم المجموعة]
        holder.title.text = item.subjectName

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
            setTextColor(Color.parseColor("#2d3436"))
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
            item.averageScore >= 85 -> holder.tvScore.setTextColor(Color.parseColor("#00b894")) // ممتاز (أخضر)
            item.averageScore >= 60 -> holder.tvScore.setTextColor(Color.parseColor("#0984e3")) // جيد (أزرق)
            item.averageScore >= 40 -> holder.tvScore.setTextColor(Color.parseColor("#f39c12")) // مقبول (برتقالي)
            else -> holder.tvScore.setTextColor(Color.parseColor("#d63031")) // ضعيف (أحمر)
        }
    }

    override fun getItemCount() = list.size
}