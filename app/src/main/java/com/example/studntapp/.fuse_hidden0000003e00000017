package com.example.studntapp

import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.net.URLEncoder

class MediaViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)
        supportActionBar?.hide()

        val fileUrl = intent.getStringExtra("FILE_URL") ?: ""
        val fileType = intent.getStringExtra("FILE_TYPE") ?: "document"

        webView = findViewById(R.id.webView)
        val imageView = findViewById<ImageView>(R.id.imageView)
        videoView = findViewById(R.id.videoView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        if (fileUrl.isEmpty()) {
            Toast.makeText(this, "رابط الملف غير صالح", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        when (fileType) {
            "image" -> {
                imageView.visibility = View.VISIBLE
                Glide.with(this).load(fileUrl).into(imageView)
                progressBar.visibility = View.GONE
            }
            "video", "audio" -> {
                videoView.visibility = View.VISIBLE
                val mediaController = MediaController(this)
                mediaController.setAnchorView(videoView)
                videoView.setMediaController(mediaController)
                videoView.setVideoURI(Uri.parse(fileUrl))

                videoView.setOnPreparedListener {
                    progressBar.visibility = View.GONE
                    it.start()
                    if (fileType == "audio") {
                        // لإظهار أزرار التحكم في الصوت دائماً
                        mediaController.show(0)
                        // تغيير الخلفية لتوضيح أنه ملف صوتي
                        findViewById<View>(android.R.id.content).setBackgroundColor(Color.parseColor("#1a1a1a"))
                    }
                }
                videoView.setOnErrorListener { _, _, _ ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "فشل تشغيل الملف", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            "document", "pdf" -> {
                if (isLocalUrl(fileUrl)) {
                    // إذا كان الرابط داخلي (Local IP)، جوجل لن يتمكن من عرضه، لذا نستخدم المتصفح أو تطبيق خارجي
                    Toast.makeText(this, "جاري فتح الملف في تطبيق خارجي...", Toast.LENGTH_SHORT).show()
                    openExternalBrowser(fileUrl)
                    finish()
                } else {
                    webView.visibility = View.VISIBLE
                    setupWebView(webView, progressBar, fileUrl)
                }
            }
        }
    }

    private fun isLocalUrl(url: String): Boolean = url.contains("192.168.") || url.contains("10.0.") || url.contains("localhost")

    private fun openExternalBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق مناسب لفتح هذا الملف", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupWebView(webView: WebView, progressBar: ProgressBar, fileUrl: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }
            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                progressBar.visibility = View.GONE
                openExternalBrowser(fileUrl)
                finish()
            }
        }

        try {
            val googleDocsUrl = "https://docs.google.com/gview?embedded=true&url=${URLEncoder.encode(fileUrl, "UTF-8")}"
            webView.loadUrl(googleDocsUrl)
        } catch (e: Exception) {
            webView.loadUrl(fileUrl)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::videoView.isInitialized && videoView.isPlaying) videoView.pause()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        if (::videoView.isInitialized) videoView.stopPlayback()
        super.onDestroy()
    }
}