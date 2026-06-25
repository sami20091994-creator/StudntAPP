package com.example.studntapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class MaterialsActivity : BaseActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var fabUpload: FloatingActionButton
    private var role = ""
    private var userId = 0
    private var currentSubjectId = 0

    // الطريقة الحديثة والمستقرة لاختيار الملفات في الأندرويد
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadFileToServer(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subjects)

        supportActionBar?.title = "موادي الدراسية"

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        role = prefs.getString("USER_ROLE", "student") ?: "student"
        userId = prefs.getInt("USER_ID", 0)

        rv = findViewById(R.id.rvSubjects)
        fabUpload = findViewById(R.id.fabUpload)
        rv.layoutManager = LinearLayoutManager(this)

        fabUpload.setOnClickListener { selectFileToUpload() }

        val sId = intent.getIntExtra("SUBJECT_ID", 0)
        val sName = intent.getStringExtra("SUBJECT_NAME")

        if (sId != 0 && sName != null) {
            loadMaterialsForSubject(sId, sName)
        } else {
            loadSubjects()
        }
    }

    private fun loadSubjects() {
        currentSubjectId = 0
        supportActionBar?.title = "قائمة المواد الدراسية"
        fabUpload.visibility = View.GONE
        findViewById<View?>(R.id.fabMessages)?.translationY = 0f // إعادة زر الرسائل لمكانه

        val call = if (role == "teacher") {
            RetrofitClient.instance.getSubjects(userId = userId, role = role)
        } else {
            RetrofitClient.instance.getSubjects(userId = userId, role = role) // السيرفر يفلتر تلقائياً
        }

        call.enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    val subjects = response.body()?.data ?: emptyList()
                    if (subjects.isEmpty()) {
                        Toast.makeText(this@MaterialsActivity, "لا توجد مواد متاحة", Toast.LENGTH_SHORT).show()
                    } else {
                        val adapter = ReportSubjectsAdapter(subjects) { sub ->
                            val subId = sub.subjectId ?: 0
                            if (subId != 0) {
                                loadMaterialsForSubject(subId, sub.subjectName ?: "مادة")
                            }
                        }
                        rv.adapter = adapter
                    }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                Toast.makeText(this@MaterialsActivity, "فشل الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadMaterialsForSubject(subjectId: Int, subjectName: String) {
        currentSubjectId = subjectId
        supportActionBar?.title = "المواد الدراسية المرفوعة"

        // إظهار زر الرفع فقط للأستاذ، ورفع زر الرسائل ليتوضّع فوقه.
        if (role == "teacher") {
            fabUpload.visibility = View.VISIBLE
            findViewById<View?>(R.id.fabMessages)?.translationY = -resources.displayMetrics.density * 70
        } else {
            fabUpload.visibility = View.GONE
        }

        RetrofitClient.instance.getSubjectMaterials(subjectId = subjectId).enqueue(object : Callback<MaterialResponse> {
            override fun onResponse(call: Call<MaterialResponse>, response: Response<MaterialResponse>) {
                if (response.isSuccessful) {
                    val materials = response.body()?.data ?: emptyList()
                    if (materials.isEmpty()) {
                        Toast.makeText(this@MaterialsActivity, "لا توجد ملفات مرفوعة", Toast.LENGTH_SHORT).show()
                    }
                    rv.adapter = MaterialsAdapter(materials, this@MaterialsActivity)
                }
            }
            override fun onFailure(call: Call<MaterialResponse>, t: Throwable) {
                Toast.makeText(this@MaterialsActivity, "فشل تحميل الملفات", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun selectFileToUpload() {
        if (currentSubjectId == 0) return

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickFileLauncher.launch(intent)
    }

    private fun uploadFileToServer(uri: Uri) {
        Toast.makeText(this, "جاري رفع الملف... الرجاء الانتظار قليلاً", Toast.LENGTH_LONG).show()

        try {
            val fileDir = applicationContext.cacheDir // استخدام الـ Cache لتجنب مشاكل الصلاحيات
            val fileName = getFileName(uri) ?: "upload_${System.currentTimeMillis()}.file"
            val file = File(fileDir, fileName)

            // نسخ الملف المختار إلى مسار مؤقت يمكن لـ Retrofit قراءته
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }

            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val actionBody = "upload_material".toRequestBody(MultipartBody.FORM)
            val subjectIdBody = currentSubjectId.toString().toRequestBody(MultipartBody.FORM)
            val titleBody = fileName.toRequestBody(MultipartBody.FORM)

            RetrofitClient.instance.uploadMaterial(actionBody, subjectIdBody, titleBody, body).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        Toast.makeText(this@MaterialsActivity, response.body()?.message ?: "تم الرفع بنجاح", Toast.LENGTH_SHORT).show()
                        loadMaterialsForSubject(currentSubjectId, supportActionBar?.title?.toString()?.replace("ملفات: ", "") ?: "المادة")
                    } else {
                        val errMsg = response.body()?.message ?: "فشل الرفع من السيرفر"
                        Toast.makeText(this@MaterialsActivity, errMsg, Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@MaterialsActivity, "خطأ بالاتصال: تأكد من أن الملف ليس ضخماً جداً", Toast.LENGTH_LONG).show()
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "حدث خطأ أثناء قراءة الملف من الهاتف", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.let { File(it).name }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentSubjectId != 0) {
            loadSubjects()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// محول عرض الملفات للطلاب والأساتذة
class MaterialsAdapter(private val list: List<MaterialData>, private val context: Context) : RecyclerView.Adapter<MaterialsAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvSubjectName)
        val info: TextView = v.findViewById(R.id.tvTeacherName)
        val status: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.title.text = item.title
        holder.info.text = "نوع الملف: ${item.fileType}"
        holder.status.text = item.uploadedAt
        holder.status.setTextColor(ContextCompat.getColor(context, R.color.ink_muted))

        holder.itemView.setOnClickListener {
            val path = item.filePath
            if (path.isNullOrEmpty()) {
                Toast.makeText(context, "رابط الملف غير متاح", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val baseUrl = RetrofitClient.BASE_URL
            val fullUrl = if (!path.startsWith("http")) baseUrl + path else path

            try {
                val intent = Intent(context, MediaViewerActivity::class.java)
                intent.putExtra("FILE_URL", fullUrl)
                intent.putExtra("FILE_TYPE", item.fileType ?: "document")
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "لا يوجد تطبيق مناسب لفتح هذا الملف", Toast.LENGTH_SHORT).show()
            } 
        }
    }

    override fun getItemCount() = list.size
}