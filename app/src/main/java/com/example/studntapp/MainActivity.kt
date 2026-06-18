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
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 1. طلب الصلاحيات
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 2. التحقق من أذونات البطارية للحصول على استقرار كامل للإشعارات
        checkBatteryOptimizations()

        val sharedPreferences = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            // تشغيل محرك الإشعارات الفوري في الخلفية
            startRealTimeNotificationService()
            startActivity(Intent(this, DailyReportActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etNewPassword)
        btnLogin = findViewById(R.id.btnLogin)

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
                        btnLogin.text = "تسجيل الدخول"

                        if (response.isSuccessful && response.body() != null) {
                            val apiResponse = response.body()!!
                            when (apiResponse.status) {
                                "success" -> {
                                    val editor = sharedPreferences.edit()
                                    editor.putBoolean("isLoggedIn", true)
                                    editor.putInt("USER_ID", apiResponse.data?.id ?: 0)
                                    editor.putString("USER_NAME", apiResponse.data?.name)
                                    editor.putString("USER_ROLE", apiResponse.role)
                                    editor.putString("USER_IMAGE", apiResponse.data?.image)
                                    editor.apply()

                                    // بدء تشغيل خدمة الإشعارات الأمامية
                                    startRealTimeNotificationService()

                                    startActivity(Intent(this@MainActivity, DailyReportActivity::class.java))
                                    finish()
                                }
                                "require_new_password" -> Toast.makeText(this@MainActivity, apiResponse.message, Toast.LENGTH_LONG).show()
                                else -> Toast.makeText(this@MainActivity, apiResponse.message ?: "فشل تسجيل الدخول", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "خطأ في الاتصال بالخادم", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "تسجيل الدخول"
                        Toast.makeText(this@MainActivity, "خطأ في الاتصال: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                })
        }
    }

    // دالة بدء الخدمة الأمامية (Foreground Service)
    private fun startRealTimeNotificationService() {
        val serviceIntent = Intent(this, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // دالة لمنع نظام Redmi / شاومي من قتل التطبيق في الخلفية
    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // رسالة تنبيه لمستخدمي شاومي / Redmi لتفعيل "التشغيل التلقائي"
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("لاحقاً") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}