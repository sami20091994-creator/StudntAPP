package com.example.studntapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class LectureRoomActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val dataString = result.data?.dataString
            if (dataString != null) {
                filePathCallback?.onReceiveValue(arrayOf(Uri.parse(dataString)))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (!cameraGranted || !audioGranted) {
            Toast.makeText(this, "يجب منح صلاحيات الكاميرا والمايكروفون لتعمل القاعة بشكل صحيح", Toast.LENGTH_LONG).show()
        } else {
            webView.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // إبقاء الشاشة مضاءة أثناء عرض المحاضرة
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_lecture_room)
        supportActionBar?.hide()

        webView = findViewById(R.id.webViewLecture)

        // مراعاة النوتش/الحواف: نزيح محتوى المحاضرة أسفل شريط الحالة وفوق شريط التنقّل.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val prefs = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", 0)
        val userName = prefs.getString("USER_NAME", "مستخدم") ?: "مستخدم"
        val role = prefs.getString("USER_ROLE", "student") ?: "student"

        val subjectId = intent.getIntExtra("SUBJECT_ID", 0)

        if (subjectId == 0) {
            Toast.makeText(this, "خطأ في رقم المادة", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWebView()
        checkAndRequestPermissions()

        val roomUrl = "${RetrofitClient.BASE_URL}lecture_room.php?subject_id=$subjectId&app_user_id=$userId&app_user_name=${Uri.encode(userName)}&app_role=$role"
        webView.loadUrl(roomUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidApp")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            // ✨ الحل السحري لإخفاء المربع الرمادي وزر التشغيل الافتراضي
            override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@LectureRoomActivity.filePathCallback?.onReceiveValue(null)
                this@LectureRoomActivity.filePathCallback = filePathCallback
                val chooserIntent = fileChooserParams?.createIntent()
                if (chooserIntent == null) {
                    this@LectureRoomActivity.filePathCallback = null
                    return false
                }
                try {
                    fileChooserLauncher.launch(chooserIntent)
                } catch (e: Exception) {
                    this@LectureRoomActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    it.grant(it.resources) // منح صلاحيات الكاميرا لـ MiroTalk مباشرة
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        webView.destroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    class WebAppInterface(private val activity: Activity) {
        @JavascriptInterface
        fun closeRoom() {
            activity.runOnUiThread {
                activity.finish()
            }
        }
    }
}