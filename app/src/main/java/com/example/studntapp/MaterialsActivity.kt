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

/** ينظّف اسم المادة من تاغ المجموعة بين الأقواس للعرض كعنوان واضح. */
fun cleanSubjectName(name: String?): String {
    val n = name ?: return "المادة"
    val cleaned = n.replace(Regex("[\\[(][^\\])]*[\\])]"), "").trim()
    return if (cleaned.isNotEmpty()) cleaned else n.trim()
}

/** يضبط فقاعتي اسم المدرّس وحالة المادة في رأس المقرّر. */
fun bindCourseMeta(teacherTv: TextView, statusTv: TextView, teacher: String?, status: String?) {
    val ctx = statusTv.context
    val t = teacher?.takeIf { it.isNotBlank() && it != "null" }
    teacherTv.text = t ?: ""
    teacherTv.visibility = if (t != null) View.VISIBLE else View.GONE

    val raw = status?.trim()?.lowercase()
    val done = setOf("completed", "complete", "done", "finished", "مكتملة")
    val active = setOf("active", "نشطة", "ongoing")
    when (raw) {
        in done -> {
            statusTv.text = "مكتملة"
            statusTv.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_completed)
            statusTv.setTextColor(android.graphics.Color.parseColor("#166534"))
            statusTv.visibility = View.VISIBLE
        }
        in active -> {
            statusTv.text = "نشطة"
            statusTv.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_active)
            statusTv.setTextColor(ContextCompat.getColor(ctx, R.color.success_green))
            statusTv.visibility = View.VISIBLE
        }
        else -> statusTv.visibility = View.GONE
    }
}

/** تصنيف نوع المحتوى التعليمي حسب النوع/الامتداد: video / pdf / audio / other. */
fun materialKind(m: MaterialData): String {
    val s = ((m.fileType ?: "") + " " + (m.filePath ?: "")).lowercase()
    return when {
        s.contains("video") || Regex("\\.(mp4|mkv|avi|mov|webm|3gp)").containsMatchIn(s) -> "video"
        s.contains("pdf") -> "pdf"
        s.contains("audio") || Regex("\\.(mp3|wav|m4a|ogg|aac|opus)").containsMatchIn(s) -> "audio"
        else -> "other"
    }
}

class MaterialsActivity : BaseActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var fabUpload: FloatingActionButton
    private var role = ""
    private var userId = 0
    private var currentSubjectId = 0
    private var allSubjects: List<SubjectData> = emptyList()
    private var currentFilter = "" // "" | active | completed
    private var allMaterials: List<MaterialData> = emptyList()
    private var contentFilter = "" // "" | video | pdf | audio

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

        // أزرار الفرز (نشطة / مكتملة)
        findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupFilter)
            .setOnCheckedStateChangeListener { _, ids ->
                currentFilter = when {
                    ids.contains(R.id.chipActive) -> "active"
                    ids.contains(R.id.chipCompleted) -> "completed"
                    else -> ""
                }
                renderSubjects()
            }

        // أزرار فرز المحتوى (فيديو/PDF/صوتي)
        findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupContent)
            .setOnCheckedStateChangeListener { _, ids ->
                contentFilter = when {
                    ids.contains(R.id.chipVideo) -> "video"
                    ids.contains(R.id.chipPdf) -> "pdf"
                    ids.contains(R.id.chipAudio) -> "audio"
                    else -> ""
                }
                renderMaterials()
            }

        val sId = intent.getIntExtra("SUBJECT_ID", 0)
        val sName = intent.getStringExtra("SUBJECT_NAME")

        if (sId != 0 && sName != null) {
            loadMaterialsForSubject(sId, sName)
        } else {
            loadSubjects()
        }
    }

    /** يبني قائمة المواد بعد تطبيق الفرز الحالي (نشطة/مكتملة/الكل). */
    private fun renderSubjects() {
        val active = setOf("active", "نشطة", "ongoing")
        val done = setOf("completed", "complete", "done", "finished", "مكتملة")
        val filtered = when (currentFilter) {
            "active" -> allSubjects.filter { it.status?.trim()?.lowercase() in active }
            "completed" -> allSubjects.filter { it.status?.trim()?.lowercase() in done }
            else -> allSubjects
        }
        rv.adapter = ReportSubjectsAdapter(filtered) { sub ->
            val subId = sub.subjectId ?: 0
            if (subId != 0) loadMaterialsForSubject(subId, sub.subjectName ?: "مادة", sub.teacherName, sub.status)
        }
        // دخول قائمة المواد بنعومة (تظهر بعد الرجوع من مقرّر)
        rv.alpha = 0f; rv.translationX = -rv.width.toFloat().coerceAtLeast(120f) * 0.25f
        rv.animate().alpha(1f).translationX(0f).setDuration(240).start()
    }

    private fun loadSubjects() {
        // أنيميشن خروج من صفحة المحتوى المرفوع عند الرجوع للقائمة.
        if (currentSubjectId != 0) {
            rv.animate().alpha(0f).translationX(rv.width.toFloat().coerceAtLeast(120f) * 0.25f).setDuration(170).start()
        }
        currentSubjectId = 0
        supportActionBar?.title = "قائمة المواد الدراسية"
        fabUpload.visibility = View.GONE
        findViewById<View?>(R.id.filterBar)?.visibility = View.VISIBLE
        findViewById<View?>(R.id.contentFilterBar)?.visibility = View.GONE
        findViewById<View?>(R.id.contentHeader)?.visibility = View.GONE
        findViewById<View?>(R.id.emptyState)?.visibility = View.GONE

        val call = if (role == "teacher") {
            RetrofitClient.instance.getSubjects(userId = userId, role = role)
        } else {
            RetrofitClient.instance.getSubjects(userId = userId, role = role) // السيرفر يفلتر تلقائياً
        }

        call.enqueue(object : Callback<SubjectListResponse> {
            override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    val subjects = response.body()?.data ?: emptyList()
                    allSubjects = subjects
                    if (subjects.isEmpty()) {
                        Toast.makeText(this@MaterialsActivity, "لا توجد مواد متاحة", Toast.LENGTH_SHORT).show()
                    } else {
                        renderSubjects()
                    }
                }
            }
            override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                Toast.makeText(this@MaterialsActivity, "فشل الاتصال", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /** يطبّق فرز نوع المحتوى + يُظهر الحالة الفارغة + يفعّل/يعطّل أزرار الفرز حسب توفّر المحتوى. */
    private fun renderMaterials() {
        val byType = { k: String -> allMaterials.filter { materialKind(it) == k } }
        // فحص وجود محتوى لكل نوع → تعطيل الزر إن لم يوجد.
        findViewById<com.google.android.material.chip.Chip>(R.id.chipVideo).isEnabled = byType("video").isNotEmpty()
        findViewById<com.google.android.material.chip.Chip>(R.id.chipPdf).isEnabled = byType("pdf").isNotEmpty()
        findViewById<com.google.android.material.chip.Chip>(R.id.chipAudio).isEnabled = byType("audio").isNotEmpty()

        val filtered = if (contentFilter.isEmpty()) allMaterials else byType(contentFilter)
        rv.adapter = MaterialsAdapter(filtered, this@MaterialsActivity)
        findViewById<View?>(R.id.emptyState)?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadMaterialsForSubject(subjectId: Int, subjectName: String, teacher: String? = null, status: String? = null) {
        currentSubjectId = subjectId
        supportActionBar?.title = "المواد الدراسية المرفوعة"

        findViewById<View?>(R.id.filterBar)?.visibility = View.GONE
        findViewById<View?>(R.id.contentFilterBar)?.visibility = View.VISIBLE
        findViewById<View?>(R.id.contentHeader)?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvContentTitle).text = cleanSubjectName(subjectName)
        bindCourseMeta(findViewById(R.id.tvContentTeacher), findViewById(R.id.tvContentStatus), teacher, status)
        contentFilter = ""
        findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupContent).clearCheck()
        // زر الرفع (للأستاذ) يقع فوق زر الدردشات عبر هامشه في التخطيط.
        fabUpload.visibility = if (role == "teacher") View.VISIBLE else View.GONE

        // أنيميشن دخول للمقرّر
        rv.alpha = 0f; rv.translationX = rv.width.toFloat().coerceAtLeast(120f)
        rv.animate().alpha(1f).translationX(0f).setDuration(260).start()

        RetrofitClient.instance.getSubjectMaterials(subjectId = subjectId).enqueue(object : Callback<MaterialResponse> {
            override fun onResponse(call: Call<MaterialResponse>, response: Response<MaterialResponse>) {
                allMaterials = response.body()?.data ?: emptyList()
                renderMaterials()
            }
            override fun onFailure(call: Call<MaterialResponse>, t: Throwable) {
                allMaterials = emptyList(); renderMaterials()
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

        val typeLabel = when (materialKind(item)) {
            "video" -> "فيديو"; "pdf" -> "PDF"; "audio" -> "صوتي"; else -> "ملف"
        }
        holder.info.text = typeLabel
        holder.info.visibility = View.VISIBLE

        holder.status.text = item.uploadedAt ?: ""
        holder.status.visibility = if (item.uploadedAt.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.status.background = ContextCompat.getDrawable(context, R.drawable.bg_pill)
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