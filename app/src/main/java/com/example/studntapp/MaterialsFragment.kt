package com.example.studntapp

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.fragment.app.Fragment
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

/** صفحة المواد/الملفات داخل الـ ViewPager (نسخة الطالب). */
class MaterialsFragment : Fragment(), BackInterceptor {

    private lateinit var rv: RecyclerView
    private lateinit var fabUpload: FloatingActionButton
    private var role = ""
    private var userId = 0
    private var currentSubjectId = 0
    private var allSubjects: List<SubjectData> = emptyList()
    private var currentFilter = "" // "" | active | completed
    private var allMaterials: List<MaterialData> = emptyList()
    private var contentFilter = "" // "" | video | pdf | audio

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> uploadFileToServer(uri) }
        }
    }

    private fun setTitle(t: String) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = t
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.activity_subjects, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        role = prefs.getString("USER_ROLE", "student") ?: "student"
        userId = prefs.getInt("USER_ID", 0)

        rv = view.findViewById(R.id.rvSubjects)
        fabUpload = view.findViewById(R.id.fabUpload)
        rv.layoutManager = LinearLayoutManager(ctx)
        fabUpload.setOnClickListener { selectFileToUpload() }

        view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupFilter)
            .setOnCheckedStateChangeListener { _, ids ->
                currentFilter = when {
                    ids.contains(R.id.chipActive) -> "active"
                    ids.contains(R.id.chipCompleted) -> "completed"
                    else -> ""
                }
                renderSubjects()
            }

        view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupContent)
            .setOnCheckedStateChangeListener { _, ids ->
                contentFilter = when {
                    ids.contains(R.id.chipVideo) -> "video"
                    ids.contains(R.id.chipPdf) -> "pdf"
                    ids.contains(R.id.chipAudio) -> "audio"
                    else -> ""
                }
                renderMaterials()
            }

        loadSubjects()
    }

    private fun renderMaterials() {
        if (!isAdded) return
        val v = view ?: return
        val byType = { k: String -> allMaterials.filter { materialKind(it) == k } }
        v.findViewById<com.google.android.material.chip.Chip>(R.id.chipVideo).isEnabled = byType("video").isNotEmpty()
        v.findViewById<com.google.android.material.chip.Chip>(R.id.chipPdf).isEnabled = byType("pdf").isNotEmpty()
        v.findViewById<com.google.android.material.chip.Chip>(R.id.chipAudio).isEnabled = byType("audio").isNotEmpty()

        val filtered = if (contentFilter.isEmpty()) allMaterials else byType(contentFilter)
        rv.adapter = MaterialsAdapter(filtered, requireContext())
        v.findViewById<View?>(R.id.emptyState)?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderSubjects() {
        if (!isAdded) return
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
        rv.alpha = 0f; rv.translationX = -rv.width.toFloat().coerceAtLeast(120f) * 0.25f
        rv.animate().alpha(1f).translationX(0f).setDuration(240).start()
    }

    private fun loadSubjects() {
        if (currentSubjectId != 0) {
            rv.animate().alpha(0f).translationX(rv.width.toFloat().coerceAtLeast(120f) * 0.25f).setDuration(170).start()
        }
        currentSubjectId = 0
        setTitle("قائمة المواد الدراسية")
        fabUpload.visibility = View.GONE
        view?.findViewById<View?>(R.id.filterBar)?.visibility = View.VISIBLE
        view?.findViewById<View?>(R.id.contentFilterBar)?.visibility = View.GONE
        view?.findViewById<View?>(R.id.contentHeader)?.visibility = View.GONE
        view?.findViewById<View?>(R.id.emptyState)?.visibility = View.GONE

        RetrofitClient.instance.getSubjects(userId = userId, role = role)
            .enqueue(object : Callback<SubjectListResponse> {
                override fun onResponse(call: Call<SubjectListResponse>, response: Response<SubjectListResponse>) {
                    if (!isAdded) return
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val subjects = response.body()?.data ?: emptyList()
                        allSubjects = subjects
                        if (subjects.isEmpty()) {
                            Toast.makeText(requireContext(), "لا توجد مواد متاحة", Toast.LENGTH_SHORT).show()
                        } else {
                            renderSubjects()
                        }
                    }
                }
                override fun onFailure(call: Call<SubjectListResponse>, t: Throwable) {
                    if (!isAdded) return
                    Toast.makeText(requireContext(), "فشل الاتصال", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadMaterialsForSubject(subjectId: Int, subjectName: String, teacher: String? = null, status: String? = null) {
        currentSubjectId = subjectId
        setTitle("المواد الدراسية المرفوعة")
        view?.findViewById<View?>(R.id.filterBar)?.visibility = View.GONE
        view?.findViewById<View?>(R.id.contentFilterBar)?.visibility = View.VISIBLE
        view?.findViewById<View?>(R.id.contentHeader)?.visibility = View.VISIBLE
        view?.findViewById<TextView>(R.id.tvContentTitle)?.text = cleanSubjectName(subjectName)
        val tTeacher = view?.findViewById<TextView>(R.id.tvContentTeacher)
        val tStatus = view?.findViewById<TextView>(R.id.tvContentStatus)
        if (tTeacher != null && tStatus != null) bindCourseMeta(tTeacher, tStatus, teacher, status)
        contentFilter = ""
        view?.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupContent)?.clearCheck()
        fabUpload.visibility = if (role == "teacher") View.VISIBLE else View.GONE

        // أنيميشن دخول للمقرّر
        rv.alpha = 0f; rv.translationX = rv.width.toFloat().coerceAtLeast(120f)
        rv.animate().alpha(1f).translationX(0f).setDuration(260).start()

        RetrofitClient.instance.getSubjectMaterials(subjectId = subjectId).enqueue(object : Callback<MaterialResponse> {
            override fun onResponse(call: Call<MaterialResponse>, response: Response<MaterialResponse>) {
                if (!isAdded) return
                allMaterials = response.body()?.data ?: emptyList()
                renderMaterials()
            }
            override fun onFailure(call: Call<MaterialResponse>, t: Throwable) {
                if (!isAdded) return
                allMaterials = emptyList(); renderMaterials()
                Toast.makeText(requireContext(), "فشل تحميل الملفات", Toast.LENGTH_SHORT).show()
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
        val ctx = requireContext()
        Toast.makeText(ctx, "جاري رفع الملف... الرجاء الانتظار قليلاً", Toast.LENGTH_LONG).show()
        try {
            val fileName = getFileName(uri) ?: "upload_${System.currentTimeMillis()}.file"
            val file = File(ctx.cacheDir, fileName)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            val mimeType = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val actionBody = "upload_material".toRequestBody(MultipartBody.FORM)
            val subjectIdBody = currentSubjectId.toString().toRequestBody(MultipartBody.FORM)
            val titleBody = fileName.toRequestBody(MultipartBody.FORM)

            RetrofitClient.instance.uploadMaterial(actionBody, subjectIdBody, titleBody, body).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (!isAdded) return
                    if (response.isSuccessful && response.body()?.status == "success") {
                        Toast.makeText(requireContext(), response.body()?.message ?: "تم الرفع بنجاح", Toast.LENGTH_SHORT).show()
                        loadMaterialsForSubject(currentSubjectId, "المادة")
                    } else {
                        Toast.makeText(requireContext(), response.body()?.message ?: "فشل الرفع من السيرفر", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    if (!isAdded) return
                    Toast.makeText(requireContext(), "خطأ بالاتصال: تأكد من أن الملف ليس ضخماً جداً", Toast.LENGTH_LONG).show()
                }
            })
        } catch (e: Exception) {
            Toast.makeText(ctx, "حدث خطأ أثناء قراءة الملف من الهاتف", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.let { File(it).name }
    }

    /** الرجوع داخل المواد: من ملفات مادة إلى قائمة المواد. */
    override fun handleBack(): Boolean {
        if (currentSubjectId != 0) {
            loadSubjects()
            return true
        }
        return false
    }
}
