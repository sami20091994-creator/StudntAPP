package com.example.studntapp

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// 1. واجهة الـ API
interface ApiService {

    @FormUrlEncoded
    @POST("api.php")
    fun login(
        @Field("action") action: String = "login",
        @Field("phone") phone: String,
        @Field("password") password: String,
        @Field("device_id") deviceId: String
    ): Call<LoginResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getSubjects(
        @Field("action") action: String = "get_subjects",
        @Field("user_id") userId: Int,
        @Field("role") role: String
    ): Call<SubjectListResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getNotifications(
        @Field("action") action: String = "get_notifications",
        @Field("user_id") userId: Int
    ): Call<NotificationResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun markAllNotificationsRead(
        @Field("action") action: String = "mark_all_notifications_read",
        @Field("user_id") userId: Int
    ): Call<SimpleResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun forgotPassword(
        @Field("action") action: String = "forgot_password",
        @Field("phone") phone: String
    ): Call<SimpleResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getAvailableAutoExams(
        @Field("action") action: String = "get_auto_exams",
        @Field("student_id") studentId: Int
    ): Call<AutoExamListResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getExamQuestions(
        @Field("action") action: String = "get_exam_questions",
        @Field("exam_id") examId: Int
    ): Call<AutoQuestionResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun submitExam(
        @Field("action") action: String = "submit_exam",
        @Field("student_id") studentId: Int,
        @Field("exam_id") examId: Int,
        @Field("answers") answersJson: String
    ): Call<SubmitExamResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getSubjectMaterials(
        @Field("action") action: String = "get_subject_materials",
        @Field("subject_id") subjectId: Int
    ): Call<MaterialResponse>

    @Multipart
    @POST("api.php")
    fun uploadMaterial(
        @Part("action") action: RequestBody,
        @Part("subject_id") subjectId: RequestBody,
        @Part("title") title: RequestBody,
        @Part file: MultipartBody.Part
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getDailyReport(
        @Field("action") action: String = "get_daily_report",
        @Field("student_id") studentId: Int,
        // اختياري: تاريخ محدّد (yyyy-MM-dd) لسجل اختبارات يوم معيّن. فارغ = اليوم (توافق خلفي).
        @Field("date") date: String? = null
    ): Call<DailyReportResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getTeacherDues(
        @Field("action") action: String = "get_teacher_dues",
        @Field("teacher_id") teacherId: Int
    ): Call<TeacherDuesResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getChatList(
        @Field("action") action: String = "get_chat_list",
        @Field("user_id") userId: Int,
        @Field("role") role: String
    ): Call<ChatListResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getChatMessages(
        @Field("action") action: String = "get_chat_messages",
        @Field("user_id") userId: Int,
        @Field("role") role: String,
        @Field("target_type") targetType: String,
        @Field("target_id") targetId: Int
    ): Call<ChatMessageResponse>

    @Multipart
    @POST("api.php")
    fun sendChatMessage(
        @Part("action") action: RequestBody,
        @Part("sender_id") senderId: RequestBody,
        @Part("target_type") targetType: RequestBody,
        @Part("target_id") targetId: RequestBody,
        @Part("body") body: RequestBody,
        @Part("role") role: RequestBody,
        @Part file: MultipartBody.Part?
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getFullScheduleFiltered(
        @Field("action") action: String = "get_full_schedule",
        @Field("user_id") userId: Int,
        @Field("role") role: String,
        @Field("date") date: String,
        @Field("view_mode") viewMode: String
    ): Call<List<ScheduleData>>

    @FormUrlEncoded
    @POST("api.php")
    fun getEnrolledSubjects(
        @Field("action") action: String = "get_enrolled_subjects",
        @Field("student_id") studentId: Int,
        @Field("role") role: String? = null
    ): Call<SubjectListResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun saveDetailedEvaluation(
        @Field("action") action: String = "save_detailed_evaluation",
        @FieldMap fields: Map<String, String>
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getStatement(
        @Field("action") action: String = "get_statement",
        @Field("student_id") studentId: Int
    ): Call<StatementResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getStudentsProgress(
        @Field("action") action: String = "get_students_progress",
        @Field("subject_id") subjectId: Int
    ): Call<StudentProgressResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getReportData(
        @Field("action") action: String = "get_report_data",
        @Field("student_id") studentId: Int,
        @Field("subject_id") subjectId: Int
    ): Call<ReportResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun saveStudyHours(
        @Field("action") action: String = "save_study_hours",
        @Field("student_id") studentId: Int,
        @Field("subject_id") subjectId: Int,
        @Field("minutes") minutes: Int
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getTeacherStudentsPayments(
        @Field("action") action: String = "get_teacher_students_payments",
        @Field("teacher_id") teacherId: Int
    ): Call<StudentPaymentsResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun checkVersion(
        @Field("action") action: String = "check_version",
        @Field("platform") platform: String = "android"
    ): Call<VersionResponse>

    @FormUrlEncoded
    @POST("api.php")
    fun getAnnouncements(
        @Field("action") action: String = "get_announcements",
        @Field("user_id") userId: Int
    ): Call<AnnouncementResponse>

    // تسجيل رمز FCM للجهاز ليتمكّن السيرفر من إرسال الإشعارات الفورية (بدون اتصال دائم).
    @FormUrlEncoded
    @POST("api.php")
    fun registerFcmToken(
        @Field("action") action: String = "register_fcm_token",
        @Field("user_id") userId: Int,
        @Field("token") token: String,
        @Field("platform") platform: String = "android"
    ): Call<SimpleResponse>
}

data class SimpleResponse(val status: String? = null, val message: String? = null)

// 2. كلاسات البيانات (Models)
data class LoginResponse(
    val status: String,
    val message: String,
    val data: UserData?,
    val role: String?
)
data class UserData(val id: Int, val name: String, val image: String? = null)

data class ProfileResponse(
    val status: String,
    val name: String?,
    val phone: String?,
    val image: String?
)

data class Subject(val id: Int, val name: String, val teacher: String)

data class Message(val id: Int, val text: String, val date: String)

data class NotificationData(
    val id: Int,
    val title: String?,
    val message: String?,
    val date: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("is_read") val isRead: Int? = null,
    // نوع الإشعار: "message" يعني رسالة محادثة (يفتح المحادثة عند الضغط).
    val type: String? = null,
    @SerializedName("sender_id") val senderId: Int? = null,
    @SerializedName("sender_name") val senderName: String? = null,
    // نوع المحادثة: "user" أو "group" (افتراضي user).
    @SerializedName("chat_type") val chatType: String? = null
)

data class NotificationResponse(
    val status: String,
    val data: List<NotificationData>?
)

data class AutoExam(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("subject_name") val subjectName: String,
    @SerializedName("exam_date") val examDate: String? = null,
    @SerializedName("exam_time") val examTime: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AutoExamListResponse(
    val status: String,
    val data: List<AutoExam>?
)

data class AutoQuestion(
    val id: Int,
    @SerializedName("question_type") val questionType: String,
    @SerializedName("question_text") val questionText: String,
    val marks: Double,
    val options: List<AutoOption>?
)

data class AutoOption(
    val id: Int,
    val text: String,
    @SerializedName("match_pair") val matchPair: String?
)

data class AutoQuestionResponse(
    val status: String,
    val data: List<AutoQuestion>?
)

data class SubmitExamResponse(
    val status: String,
    val message: String?,
    val score: Double?
)

data class ApiResponse(val status: String, val message: String?)

// ====== فحص النسخة (إجبار التحديث) ======
data class VersionResponse(
    val status: String,
    @SerializedName("latest_version_code") val latestVersionCode: Int = 0,
    @SerializedName("latest_version_name") val latestVersionName: String? = null,
    @SerializedName("force_update") val forceUpdate: Boolean = false,
    @SerializedName("update_url") val updateUrl: String? = null,
    val message: String? = null
)

// ====== الإعلانات / الأخبار ======
data class AnnouncementResponse(val status: String, val data: List<AnnouncementItem>?)
data class AnnouncementItem(
    val id: Int,
    val title: String?,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("article_url") val articleUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val tag: String? = null
)

data class SubjectData(
    @SerializedName("subject_id") val subjectId: Int?,
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("teacher_name") val teacherName: String?,
    @SerializedName("teacher_id") val teacherId: Int? = null,
    val status: String? = null,
    @SerializedName("id") val id: Int? = null,
    @SerializedName("is_live") val isLive: Int? = 0
)

data class SubjectListResponse(val status: String, val data: List<SubjectData>?)

data class MaterialResponse(val status: String, val data: List<MaterialData>?)

data class MaterialData(
    val title: String?,
    @SerializedName("file_path") val filePath: String?,
    @SerializedName("file_type") val fileType: String?,
    @SerializedName("uploaded_at") val uploadedAt: String?
)

data class DailyReportResponse(val status: String, val data: DailyReportData?)
data class DailyReportData(
    val date: String?,
    @SerializedName("check_in") val checkIn: String?,
    @SerializedName("check_out") val checkOut: String?,
    val quizzes: List<DailyQuiz>?
)
data class DailyQuiz(
    val title: String?,
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("marks_obtained") val marksObtained: Double,
    @SerializedName("total_marks") val totalMarks: Double,
    val percentage: Double
)

data class TeacherDuesResponse(
    val status: String,
    val data: List<TeacherDue>?,
    val withdrawals: List<TeacherWithdrawal>?,
    val summary: TeacherSummary?
)

data class TeacherDue(
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("teacher_percentage") val teacherPercentage: Double,
    @SerializedName("total_revenue") val totalRevenue: Double,
    @SerializedName("teacher_share") val teacherShare: Double,
    @SerializedName("sub_completed_classes") val subCompletedClasses: Int = 0,
    @SerializedName("sub_remaining_classes") val subRemainingClasses: Int = 0,
    @SerializedName("sub_cancelled_classes") val subCancelledClasses: Int = 0
)

data class TeacherWithdrawal(
    val amount: Double,
    @SerializedName("payment_date") val date: String? = null,
    val notes: String?
)

data class TeacherSummary(
    @SerializedName("total_classes") val totalClasses: Int,
    @SerializedName("completed_classes") val completedClasses: Int,
    @SerializedName("remaining_classes") val remainingClasses: Int,
    @SerializedName("cancelled_classes") val cancelledClasses: Int,
    @SerializedName("total_share") val totalShare: Double,
    @SerializedName("total_received") val totalReceived: Double,
    val remaining: Double
)

data class StudentPaymentsResponse(
    val status: String,
    val data: List<StudentPayment>?
)

data class StudentPayment(
    @SerializedName("student_name") val studentName: String?,
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("amount_paid") val amountPaid: Double = 0.0,
    @SerializedName("total_paid") val totalPaid: Double = 0.0,
    @SerializedName("total_price") val totalPrice: Double,
    @SerializedName("payment_date") val paymentDate: String?
)

data class ChatListResponse(val status: String, val data: ChatListData?)
data class ChatListData(
    val groups: List<ChatEntity>?,
    val contacts: List<ChatEntity>?
)
data class ChatEntity(
    val id: Int,
    val name: String?,
    val type: String?,
    @SerializedName("last_msg_time") val lastMsgTime: String?,
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0
)

data class ChatMessageResponse(val status: String, val data: List<ChatMessageData>?)
data class ChatMessageData(
    val id: Int,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("sender_name") val senderName: String?,
    val body: String? = null,
    @SerializedName("attachment_path") val attachmentPath: String? = null,
    @SerializedName("attachment_type") val attachmentType: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("sender_role") val senderRole: String? = null
)

data class ScheduleData(
    val id: Int? = null,
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("teacher_name") val teacherName: String? = null,
    val classroom: String?,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("teacher_note") val teacherNote: String? = null,
    val type: String? = null
)

data class StatementResponse(val status: String, val data: List<TransactionData>?)
data class TransactionData(
    val description: String? = null,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val date: String?,
    val balance: Double?
)

data class ReportResponse(val status: String, val data: ReportData?)
data class ReportData(
    @SerializedName("student_name") val studentName: String? = null,
    val average: Double = 0.0,
    @SerializedName("quizzes_count") val quizzesCount: Int = 0,
    val rank: Int = 0,
    @SerializedName("class_size") val classSize: Int = 0,
    @SerializedName("total_study_hours") val totalStudyHours: Double = 0.0,
    @SerializedName("avg_hours_per_week") val avgHoursPerWeek: Double = 0.0,
    val subjects: List<SubjectPerformance>? = null,
    val timeline: ReportTimeline? = null,
    val comparison: List<ClassmateScore>? = null
)
data class ReportTimeline(
    val dates: List<String>? = null,
    val quiz: List<Double?>? = null,
    val homework: List<Double?>? = null
)
data class ClassmateScore(
    val name: String? = null,
    val percentage: Double = 0.0,
    @SerializedName("is_current") val isCurrent: Boolean = false
)
data class SubjectPerformance(
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("avg_percentage") val avgPercentage: Double
)

data class StudentProgressResponse(val status: String, val data: List<StudentProgress>?)
data class StudentProgress(
    @SerializedName("student_name") val studentName: String? = null,
    @SerializedName("average_score") val averageScore: Double = 0.0,
    @SerializedName("quizzes_count") val quizzesCount: Int = 0,
    @SerializedName("total_study_minutes") val totalStudyMinutes: Int = 0
)

// 3. إعداد Retrofit
object RetrofitClient {
    const val BASE_URL = "http://187.77.109.185:8080/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "StudntApp-Android")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}
