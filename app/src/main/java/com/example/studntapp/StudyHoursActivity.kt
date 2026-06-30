package com.example.studntapp

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StudyHoursActivity : BaseActivity() {

    private var studentId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subjects)
        supportActionBar?.title = "تسجيل ساعات الدراسة"

        val rv = findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        studentId = prefs.getInt("USER_ID", 0)

        loadSubjects(rv)
        
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh?.setOnRefreshListener { loadSubjects(rv) }
    }
    
    private fun loadSubjects(rv: RecyclerView) {
        RetrofitClient.instance.getEnrolledSubjects(studentId = studentId).enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (response.isSuccessful) {
                    val activeSubjects = response.body()?.data?.filter { it.status == "active" } ?: emptyList()
                    val adapter = SubjectsAdapter(activeSubjects)
                    rv.adapter = adapter

                    adapter.setOnItemClickListener { subject ->
                        showInputMinutesDialog(subject)
                    }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
            }
        })
    }

    private fun showInputMinutesDialog(subject: SubjectData) {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "مثال: 60 (يعني ساعة واحدة)"

        AlertDialog.Builder(this)
            .setTitle("دراسة: ${subject.subjectName}")
            .setMessage("كم دقيقة قضيت في دراسة هذه المادة اليوم خارج المركز؟")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val minutesStr = input.text.toString()
                if (minutesStr.isNotEmpty()) {
                    saveHours(subject.subjectId ?: 0, minutesStr.toInt())
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun saveHours(subjectId: Int, minutes: Int) {
        RetrofitClient.instance.saveStudyHours(studentId = studentId, subjectId = subjectId, minutes = minutes)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    Toast.makeText(this@StudyHoursActivity, response.body()?.message ?: "تم الحفظ", Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
            })
    }
}
