package com.example.studntapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeManager.wrapRtl(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ThemeManager.forceRtl(this)
        ThemeManager.maybeFadeIn(this)

        // الأذونات
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        // ملاحظة: لا نفتح إعدادات البطارية ولا نُظهر تنبيه Autostart عند الإقلاع،
        // فذلك كان يفتح شاشة النظام ويُظهر نافذة خاطفة ويسرّب نافذة الحوار.
        // (إرشاد Autostart متاح لاحقاً من شاشة الإعدادات عند الحاجة.)

        // 1) فحص رقم النسخة أولاً ثم متابعة التدفق
        checkVersionThenContinue()
    }

    // ====== فحص النسخة (إجبار التحديث) ======
    private fun currentVersionCode(): Long =
        PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

    private fun checkVersionThenContinue() {
        RetrofitClient.instance.checkVersion().enqueue(object : Callback<VersionResponse> {
            override fun onResponse(call: Call<VersionResponse>, response: Response<VersionResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.status == "success"
                    && currentVersionCode() < body.latestVersionCode) {
                    if (body.forceUpdate) {
                        showForceUpdateDialog(body.updateUrl, body.message)
                        return
                    } else {
                        showOptionalUpdateDialog(body.updateUrl, body.message)
                    }
                }
                routeAfterVersionCheck()
            }
            override fun onFailure(call: Call<VersionResponse>, t: Throwable) {
                // لا نمنع المستخدم عند فشل الشبكة
                routeAfterVersionCheck()
            }
        })
    }

    private fun openUpdateUrl(url: String?) {
        val target = if (url.isNullOrEmpty()) "https://play.google.com/store/apps/details?id=$packageName" else url
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر فتح رابط التحديث", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showForceUpdateDialog(url: String?, msg: String?) {
        AlertDialog.Builder(this)
            .setTitle("تحديث إلزامي")
            .setMessage(msg ?: "يتوفّر إصدار جديد من التطبيق. يجب التحديث للمتابعة.")
            .setCancelable(false)
            .setPositiveButton("تحديث الآن") { _, _ ->
                openUpdateUrl(url)
                finishAffinity() // إغلاق التطبيق لإجبار التحديث
            }
            .setNegativeButton("خروج") { _, _ -> finishAffinity() }
            .show()
    }

    private fun showOptionalUpdateDialog(url: String?, msg: String?) {
        AlertDialog.Builder(this)
            .setTitle("يتوفّر تحديث")
            .setMessage(msg ?: "يتوفّر إصدار أحدث من التطبيق، ننصح بالتحديث.")
            .setPositiveButton("تحديث") { _, _ -> openUpdateUrl(url) }
            .setNegativeButton("لاحقاً", null)
            .show()
    }

    // ====== متابعة التدفق بعد الفحص ======
    private fun routeAfterVersionCheck() {
        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        if (prefs.getBoolean("isLoggedIn", false)) {
            startRealTimeNotificationService()
            startActivity(homeIntent())
            overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
            finish()
            return
        }
        showLoginForm(prefs)
    }

    /**
     * وجهة البداية حسب الدور: الطالب يدخل على HomeShellActivity (تنقّل بالسحب
     * بين الصفحات الأربع)، والمعلّم يبقى على التنقّل بالأنشطة المستقلة.
     */
    private fun homeIntent(): Intent {
        val role = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
            .getString("USER_ROLE", "student")
        return if (role == "teacher")
            Intent(this, DailyReportActivity::class.java)
        else
            Intent(this, HomeShellActivity::class.java)
    }

    /** استعادة كلمة المرور: يرسل الطلب للسيرفر عبر API ليتولّى المعهد الموافقة. */
    private fun showForgotPasswordDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val input = v.findViewById<EditText>(R.id.etForgotPhone)
        input.setText(etPhone.text?.toString() ?: "")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("استعادة كلمة المرور")
            .setView(v)
            .setPositiveButton("إرسال") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.isEmpty()) {
                    Toast.makeText(this, "الرجاء إدخال رقم الهاتف", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(this, "جارٍ إرسال الطلب...", Toast.LENGTH_SHORT).show()
                RetrofitClient.instance.forgotPassword(phone = phone).enqueue(object : Callback<SimpleResponse> {
                    override fun onResponse(call: Call<SimpleResponse>, response: Response<SimpleResponse>) {
                        val raw = response.body()?.message
                        val ok = response.body()?.status == "success"
                        // نتجاهل رسائل السيرفر الغامضة (مثل "إجراء غير معروف") ونعرض رسالة واضحة.
                        val msg = when {
                            ok -> raw ?: "تم إرسال طلبك بنجاح، سيتم التواصل معك قريباً."
                            raw != null && !raw.contains("معروف") && !raw.contains("unknown", true) -> raw
                            else -> "تعذّر تنفيذ الطلب حالياً. يرجى التواصل مع إدارة المعهد لإعادة التعيين."
                        }
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    }
                    override fun onFailure(call: Call<SimpleResponse>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "تعذّر الاتصال، تحقّق من اتصالك بالإنترنت", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showLoginForm(prefs: android.content.SharedPreferences) {
        setContentView(R.layout.activity_main)

        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etNewPassword)
        btnLogin = findViewById(R.id.btnLogin)

        // تركيز المؤشر تلقائياً على حقل الهاتف وإظهار لوحة المفاتيح.
        etPhone.requestFocus()
        etPhone.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(etPhone, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        // زر Enter: الهاتف → كلمة المرور → ثم تسجيل الدخول.
        etPhone.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) { etPassword.requestFocus(); true } else false
        }
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { btnLogin.performClick(); true } else false
        }

        // نسيت كلمة المرور؟
        findViewById<TextView>(R.id.btnForgotPassword).setOnClickListener { showForgotPasswordDialog() }

        // أزرار التواصل الاجتماعي أسفل شاشة الدخول.
        SocialLinks.wire(findViewById(android.R.id.content))

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        btnLogin.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (phone.isEmpty()) { etPhone.error = "الرجاء إدخال رقم الهاتف"; return@setOnClickListener }
            if (password.isEmpty()) { etPassword.error = "الرجاء إدخال كلمة المرور"; return@setOnClickListener }

            btnLogin.isEnabled = false
            btnLogin.text = "جاري التحقق..."

            RetrofitClient.instance.login(phone = phone, password = password, deviceId = deviceId)
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "دخول"

                        if (response.isSuccessful && response.body() != null) {
                            val apiResponse = response.body()!!
                            when (apiResponse.status) {
                                "success" -> {
                                    prefs.edit().apply {
                                        putBoolean("isLoggedIn", true)
                                        putInt("USER_ID", apiResponse.data?.id ?: 0)
                                        putString("USER_NAME", apiResponse.data?.name)
                                        putString("USER_ROLE", apiResponse.role)
                                        putString("USER_IMAGE", apiResponse.data?.image)
                                        putString("USER_PHONE", phone)
                                        apply()
                                    }
                                    startRealTimeNotificationService()
                                    startActivity(homeIntent())
                                    overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
                                    finish()
                                }
                                "require_new_password" -> Toast.makeText(this@MainActivity, apiResponse.message, Toast.LENGTH_LONG).show()
                                else -> Toast.makeText(this@MainActivity, apiResponse.message ?: "رقم الهاتف أو كلمة المرور غير صحيحة", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // رمز غير ناجح: غالباً بيانات دخول خاطئة (4xx)، وليس خطأ اتصال.
                            val m = if (response.code() in 400..499)
                                "رقم الهاتف أو كلمة المرور غير صحيحة"
                            else
                                "تعذّر الوصول إلى الخادم، حاول لاحقاً"
                            Toast.makeText(this@MainActivity, m, Toast.LENGTH_LONG).show()
                        }
                    }
                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "دخول"
                        Toast.makeText(this@MainActivity, "تعذّر الاتصال، تحقّق من اتصالك بالإنترنت", Toast.LENGTH_LONG).show()
                    }
                })
        }
    }

    // لم نعد نشغّل خدمة أمامية دائمة؛ نكتفي بتسجيل رمز FCM لاستقبال الإشعارات الفورية.
    private fun startRealTimeNotificationService() {
        FcmService.syncToken(this)
    }

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                try { startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
            }
        }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val hasSeenWarning = prefs.getBoolean("has_seen_autostart_warning", false)

        if (!hasSeenWarning && (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco"))) {
            AlertDialog.Builder(this)
                .setTitle("تنبيه هام للإشعارات")
                .setMessage("لضمان وصول الإشعارات الفورية، يرجى تفعيل (التشغيل التلقائي - Autostart) لهذا التطبيق من إعدادات هاتفك.")
                .setPositiveButton("الذهاب للإعدادات") { _, _ ->
                    prefs.edit().putBoolean("has_seen_autostart_warning", true).apply()
                    try {
                        val intent = Intent()
                        intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                        startActivity(intent)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                .setNegativeButton("لاحقاً") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}
