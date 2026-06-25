package com.example.studntapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast

/**
 * روابط التواصل الاجتماعي لمعهد رسالتي.
 * ✏️ عدّل القيم أدناه بروابطك الحقيقية (واتساب/فيسبوك/الموقع/البريد).
 */
object SocialLinks {
    var WHATSAPP = "https://wa.me/970000000000"   // ضع رقم واتساب بصيغة دولية بلا +
    var FACEBOOK = "https://facebook.com/"          // ضع رابط صفحتك
    var WEBSITE  = "https://www.google.com/"        // ضع رابط موقعك
    var EMAIL    = "resalatygrp@gmail.com"

    private fun open(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "تعذّر فتح الرابط", Toast.LENGTH_SHORT).show()
        }
    }

    private fun email(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "لا يوجد تطبيق بريد", Toast.LENGTH_SHORT).show()
        }
    }

    /** يربط أزرار صف التواصل داخل أي تخطيط يتضمّن المعرّفات القياسية. */
    fun wire(root: View) {
        val ctx = root.context
        root.findViewById<View?>(R.id.btnSocialWhatsapp)?.setOnClickListener { open(ctx, WHATSAPP) }
        root.findViewById<View?>(R.id.btnSocialFacebook)?.setOnClickListener { open(ctx, FACEBOOK) }
        root.findViewById<View?>(R.id.btnSocialWebsite)?.setOnClickListener { open(ctx, WEBSITE) }
        root.findViewById<View?>(R.id.btnSocialEmail)?.setOnClickListener { email(ctx) }
    }
}
