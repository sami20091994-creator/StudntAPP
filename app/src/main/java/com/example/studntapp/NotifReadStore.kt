package com.example.studntapp

import android.content.Context

/**
 * تخزين محلي دائم لمعرّفات الإشعارات المقروءة — يدوم عبر إعادة تشغيل التطبيق،
 * فلا يعود الإشعار «غير مقروء» بعد تعليمه (حتى لو لم يحفظه السيرفر بعد).
 * عند توفّر is_read من السيرفر نستخدمه أيضاً.
 */
object NotifReadStore {
    private const val PREFS = "AppSession"
    private const val KEY = "READ_NOTIF_IDS"

    private fun ids(ctx: Context): MutableSet<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun markRead(ctx: Context, notifIds: List<Int>) {
        val set = ids(ctx)
        set.addAll(notifIds.map { it.toString() })
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
    }

    fun isRead(ctx: Context, item: NotificationData): Boolean =
        (item.isRead ?: 0) == 1 || item.id.toString() in ids(ctx)

    fun unreadCount(ctx: Context, list: List<NotificationData>): Int =
        list.count { !isRead(ctx, it) }
}
