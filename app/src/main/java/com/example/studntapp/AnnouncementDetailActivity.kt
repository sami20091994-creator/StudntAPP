package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide

/** صفحة المقال المخصصة للإعلان. */
class AnnouncementDetailActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeManager.wrapRtl(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ThemeManager.forceRtl(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_announcement_detail)
        window.statusBarColor = Color.TRANSPARENT

        val appbar = findViewById<View>(R.id.appbarDetail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            appbar.updatePadding(top = bars.top)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbarDetail)
        toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_out)
        }

        val title = intent.getStringExtra("title") ?: "التفاصيل"
        val tag = intent.getStringExtra("tag") ?: "إعلان"
        val date = intent.getStringExtra("date") ?: ""
        val image = intent.getStringExtra("image")
        val content = intent.getStringExtra("content") ?: ""
        val articleUrl = intent.getStringExtra("article_url")

        findViewById<TextView>(R.id.tvDetailTitle).text = title
        findViewById<TextView>(R.id.tvDetailTag).text = tag
        findViewById<TextView>(R.id.tvDetailDate).text = date
        findViewById<TextView>(R.id.tvDetailContent).text =
            if (content.isNotBlank()) content else "سيتم نشر تفاصيل هذا الإعلان قريباً."

        val iv = findViewById<ImageView>(R.id.ivDetailImage)
        if (!image.isNullOrEmpty()) {
            val url = if (image.startsWith("http")) image else RetrofitClient.BASE_URL + image
            Glide.with(this).load(url).into(iv)
        }

        // إن كان للإعلان رابط مقال خارجي وبدون محتوى داخلي، نفتحه في المتصفح
        if (content.isBlank() && !articleUrl.isNullOrEmpty()) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)))
                finish()
            } catch (_: Exception) { }
        }
    }
}
