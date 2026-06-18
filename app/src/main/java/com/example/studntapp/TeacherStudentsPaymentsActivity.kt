package com.example.studntapp

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// كلاس جديد لتمثيل (المادة وبداخلها قائمة طلابها)
data class SubjectGroup(val subjectName: String, val students: List<StudentPayment>)

class TeacherStudentsPaymentsActivity : AppCompatActivity() {

    private var teacherId = 0
    private lateinit var rvSubjectsGroups: RecyclerView
    private lateinit var etSearchStudent: EditText

    // حفظ القائمة الكاملة محلياً لتسريع البحث بدون العودة للسيرفر
    private var allPaymentsList: List<StudentPayment> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_students_payments)

        supportActionBar?.title = "مدفوعات طلابي"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvSubjectsGroups = findViewById(R.id.rvSubjectsGroups)
        rvSubjectsGroups.layoutManager = LinearLayoutManager(this)

        etSearchStudent = findViewById(R.id.etSearchStudent)

        teacherId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)

        // تفعيل ميزة البحث الفوري
        etSearchStudent.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterData(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadStudentsPayments()
    }

    private fun loadStudentsPayments() {
        RetrofitClient.instance.getTeacherStudentsPayments(teacherId = teacherId)
            .enqueue(object : Callback<StudentPaymentsResponse> {
                override fun onResponse(call: Call<StudentPaymentsResponse>, response: Response<StudentPaymentsResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        allPaymentsList = response.body()?.data ?: emptyList()

                        if (allPaymentsList.isEmpty()) {
                            Toast.makeText(this@TeacherStudentsPaymentsActivity, "لا يوجد طلاب مسجلين بعد", Toast.LENGTH_LONG).show()
                        } else {
                            // عرض البيانات مجمعة لأول مرة (بدون فلترة)
                            filterData("")
                        }
                    }
                }
                override fun onFailure(call: Call<StudentPaymentsResponse>, t: Throwable) {
                    Toast.makeText(this@TeacherStudentsPaymentsActivity, "خطأ بالاتصال بالخادم", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // دالة الفلترة والتجميع الذكية
    private fun filterData(query: String) {
        // 1. فلترة الطلاب حسب نص البحث
        val filteredList = if (query.trim().isEmpty()) {
            allPaymentsList
        } else {
            allPaymentsList.filter { it.studentName?.contains(query, ignoreCase = true) == true }
        }

        // 2. تجميع (Grouping) الطلاب المتبقين حسب اسم المادة
        val groupedMap = filteredList.groupBy { it.subjectName ?: "مادة غير معروفة" }

        // 3. تحويلها إلى قائمة من نوع SubjectGroup لعرضها
        val groupedList = groupedMap.map { SubjectGroup(it.key, it.value) }

        // 4. إرسالها للـ Adapter الرئيسي (الذي يعرض المواد)
        rvSubjectsGroups.adapter = SubjectGroupsAdapter(groupedList, this)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ================================================================
// Adapter رقم 1: لعرض قائمة (المواد) كعناوين رئيسية
// ================================================================
class SubjectGroupsAdapter(
    private val groups: List<SubjectGroup>,
    private val context: Context
) : RecyclerView.Adapter<SubjectGroupsAdapter.GroupVH>() {

    class GroupVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvSubjectGroupName: TextView = v.findViewById(R.id.tvSubjectGroupName)
        val rvStudentsInSubject: RecyclerView = v.findViewById(R.id.rvStudentsInSubject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        GroupVH(LayoutInflater.from(parent.context).inflate(R.layout.item_subject_group, parent, false))

    override fun onBindViewHolder(holder: GroupVH, position: Int) {
        val group = groups[position]
        holder.tvSubjectGroupName.text = group.subjectName

        // إعداد الـ RecyclerView الداخلي الخاص بطلاب هذه المادة
        holder.rvStudentsInSubject.layoutManager = LinearLayoutManager(context)
        holder.rvStudentsInSubject.adapter = StudentPaymentsAdapter(group.students)
    }

    override fun getItemCount() = groups.size
}

// ================================================================
// Adapter رقم 2: (الداخلي) لعرض قائمة الطلاب داخل كل مادة
// ================================================================
class StudentPaymentsAdapter(private val list: List<StudentPayment>) : RecyclerView.Adapter<StudentPaymentsAdapter.StudentVH>() {

    class StudentVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvStudentName: TextView = v.findViewById(R.id.tvStudentName)
        val tvPaid: TextView = v.findViewById(R.id.tvPaid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        StudentVH(LayoutInflater.from(parent.context).inflate(R.layout.item_student_payment, parent, false))

    override fun onBindViewHolder(holder: StudentVH, position: Int) {
        val item = list[position]
        holder.tvStudentName.text = item.studentName


        val amount = item.totalPaid
        if (amount > 0) {
            val formatter = java.text.DecimalFormat("#,##0.00")
            holder.tvPaid.text = "${formatter.format(amount as Double)} ل.س"
            holder.tvPaid.setTextColor(android.graphics.Color.parseColor("#00b894")) // أخضر أخضر
        } else {
            holder.tvPaid.text = "لم يدفع"
            holder.tvPaid.setTextColor(android.graphics.Color.parseColor("#d63031")) // أحمر
        }
    }

    override fun getItemCount() = list.size
}