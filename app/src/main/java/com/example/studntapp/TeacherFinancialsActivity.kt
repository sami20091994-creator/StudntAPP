package com.example.studntapp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeacherFinancialsActivity : AppCompatActivity() {

    private var teacherId = 0

    // إحصائيات الحصص
    private lateinit var tvTotalClasses: TextView
    private lateinit var tvCompletedClasses: TextView
    private lateinit var tvRemainingClasses: TextView
    private lateinit var tvCancelledClasses: TextView

    // المسحوبات
    private lateinit var tvTotalReceived: TextView
    private lateinit var rvWithdrawals: RecyclerView

    // الدورات الخاصة
    private lateinit var layoutPrivateCourses: LinearLayout
    private lateinit var tvTotalShare: TextView
    private lateinit var tvRemaining: TextView
    private lateinit var rvPrivateDues: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_financials)

        supportActionBar?.title = "مستحقاتي وإحصائيات الحصص"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 1. ربط عناصر الحصص
        tvTotalClasses = findViewById(R.id.tvTotalClasses)
        tvCompletedClasses = findViewById(R.id.tvCompletedClasses)
        tvRemainingClasses = findViewById(R.id.tvRemainingClasses)
        tvCancelledClasses = findViewById(R.id.tvCancelledClasses)

        // 2. ربط عناصر المسحوبات (التي تظهر لجميع الأساتذة)
        tvTotalReceived = findViewById(R.id.tvTotalReceived)
        rvWithdrawals = findViewById(R.id.rvWithdrawals)
        rvWithdrawals.layoutManager = LinearLayoutManager(this)

        // 3. ربط عناصر الدورات الخاصة (التي تظهر لمن لديه نسبة فقط)
        layoutPrivateCourses = findViewById(R.id.layoutPrivateCourses)
        tvTotalShare = findViewById(R.id.tvTotalShare)
        tvRemaining = findViewById(R.id.tvRemaining)
        rvPrivateDues = findViewById(R.id.rvPrivateDues)
        rvPrivateDues.layoutManager = LinearLayoutManager(this)

        teacherId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)
        loadTeacherData()
    }

    private fun loadTeacherData() {
        RetrofitClient.instance.getTeacherDues(teacherId = teacherId)
            .enqueue(object : Callback<TeacherDuesResponse> {
                override fun onResponse(call: Call<TeacherDuesResponse>, response: Response<TeacherDuesResponse>) {
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {

                        body.summary?.let { sum ->
                            // عرض إحصائيات الحصص للجميع
                            tvTotalClasses.text = sum.totalClasses.toString()
                            tvCompletedClasses.text = sum.completedClasses.toString()
                            tvRemainingClasses.text = sum.remainingClasses.toString()
                            tvCancelledClasses.text = sum.cancelledClasses.toString()

                            // المستلم (المسحوبات)
                            val formatter = java.text.DecimalFormat("#,##0.00")
                            tvTotalReceived.text = "${formatter.format(sum.totalReceived)} ل.س"
                            tvTotalShare.text = "${formatter.format(sum.totalShare)} ل.س"
                            tvRemaining.text = "${formatter.format(sum.remaining)} ل.س"
                        }

                        // تعبئة سجل المسحوبات
                        val withdrawalsList = body.withdrawals ?: emptyList()
                        rvWithdrawals.adapter = TeacherWithdrawalsAdapter(withdrawalsList)

                        // فلترة المواد لمعرفة ما إذا كان يمتلك دورات بنسبة
                        val allDues = body.data ?: emptyList()
                        val privateCourses = allDues.filter { it.teacherPercentage > 0 }

                        if (privateCourses.isNotEmpty()) {
                            // إظهار القسم وتعبئة الجدول إذا كان لديه دورات خاصة
                            layoutPrivateCourses.visibility = View.VISIBLE
                            rvPrivateDues.adapter = TeacherDuesAdapter(privateCourses)
                        } else {
                            // إخفاء قسم الأموال الخاصة إذا كان الراتب مقطوعاً
                            layoutPrivateCourses.visibility = View.GONE
                        }

                    } else {
                        Toast.makeText(this@TeacherFinancialsActivity, "لم يتم العثور على بيانات", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<TeacherDuesResponse>, t: Throwable) {
                    Toast.makeText(this@TeacherFinancialsActivity, "خطأ في الاتصال بالخادم", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ----------------------------------------------------
// Adapter مخصص لسجل المسحوبات والدفعات المستلمة
// ----------------------------------------------------
class TeacherWithdrawalsAdapter(private val list: List<TeacherWithdrawal>) : RecyclerView.Adapter<TeacherWithdrawalsAdapter.WH>() {
    class WH(v: View) : RecyclerView.ViewHolder(v) {
        val tvAmount: TextView = v.findViewById(android.R.id.text1)
        val tvDateNotes: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WH {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 15, 0, 15)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val tv1 = TextView(parent.context).apply { id = android.R.id.text1; textSize = 16f; setTextColor(Color.parseColor("#00b894")); setTypeface(null, android.graphics.Typeface.BOLD) }
        val tv2 = TextView(parent.context).apply { id = android.R.id.text2; textSize = 13f; setTextColor(Color.GRAY) }
        val divider = View(parent.context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 15 }; setBackgroundColor(Color.parseColor("#F1F5F9")) }

        layout.addView(tv1); layout.addView(tv2); layout.addView(divider)
        return WH(layout)
    }

    override fun onBindViewHolder(holder: WH, position: Int) {
        val item = list[position]
        val formatter = java.text.DecimalFormat("#,##0.00")
        holder.tvAmount.text = "استلام: ${formatter.format(item.amount)} ل.س"
        holder.tvDateNotes.text = "${item.date ?: ""} | ${item.notes ?: "دفعة من المستحقات"}"
    }
    override fun getItemCount() = list.size
}

// ----------------------------------------------------
// Adapter مخصص لـ "تفاصيل الدورات الخاصة"
// ----------------------------------------------------
class TeacherDuesAdapter(private val list: List<TeacherDue>) : RecyclerView.Adapter<TeacherDuesAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvSubject: TextView = v.findViewById(R.id.tvSubjectName)
        val tvDetails: TextView = v.findViewById(R.id.tvDetails)
        val tvHighlight: TextView = v.findViewById(R.id.tvHighlight)
        val tvClassesStats: TextView = v.findViewById(R.id.tvClassesStats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_teacher_due, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvSubject.text = item.subjectName

        holder.tvClassesStats.text = "الحصص: ${item.subCompletedClasses} منجزة | ${item.subRemainingClasses} متبقية | ${item.subCancelledClasses} ملغاة"

        val formatter = java.text.DecimalFormat("#,##0.00")
        holder.tvDetails.text = "النسبة: ${item.teacherPercentage}%\nدخل المادة: ${formatter.format(item.totalRevenue)} ل.س"
        holder.tvHighlight.text = "حصتك:\n${formatter.format(item.teacherShare)} ل.س"
        holder.tvHighlight.setTextColor(Color.parseColor("#0984e3"))
    }
    override fun getItemCount() = list.size
}
