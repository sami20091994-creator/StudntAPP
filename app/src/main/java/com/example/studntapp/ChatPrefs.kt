package com.example.studntapp

import android.content.Context

/**
 * تخزين محلي لحالة المحادثات: الأرشيف، النجمة، التثبيت، وتجاوز حالة القراءة.
 * المفتاح لكل محادثة = "type:id".
 */
object ChatPrefs {
    private const val FILE = "ChatListPrefs"
    private const val ARCHIVED = "archived"
    private const val STARRED = "starred"
    private const val PINNED = "pinned"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun keyOf(chat: ChatEntity): String = "${chat.type ?: "user"}:${chat.id}"

    private fun getSet(c: Context, k: String): MutableSet<String> =
        HashSet(sp(c).getStringSet(k, emptySet()) ?: emptySet())

    private fun toggle(c: Context, name: String, key: String, on: Boolean?) {
        val s = getSet(c, name)
        val newOn = on ?: !s.contains(key)
        if (newOn) s.add(key) else s.remove(key)
        sp(c).edit().putStringSet(name, s).apply()
    }

    fun isArchived(c: Context, key: String) = getSet(c, ARCHIVED).contains(key)
    fun isStarred(c: Context, key: String) = getSet(c, STARRED).contains(key)
    fun isPinned(c: Context, key: String) = getSet(c, PINNED).contains(key)

    fun setArchived(c: Context, key: String, on: Boolean) = toggle(c, ARCHIVED, key, on)
    fun toggleStar(c: Context, key: String) = toggle(c, STARRED, key, null)
    fun togglePin(c: Context, key: String) = toggle(c, PINNED, key, null)

    // تجاوز حالة القراءة: "read" يفرض صفر غير مقروء، "unread" يفرض وجود غير مقروء.
    private fun readState(c: Context, key: String): String? = sp(c).getString("ro_$key", null)
    fun setRead(c: Context, key: String) = sp(c).edit().putString("ro_$key", "read").apply()
    fun setUnread(c: Context, key: String) = sp(c).edit().putString("ro_$key", "unread").apply()
    fun clearRead(c: Context, key: String) = sp(c).edit().remove("ro_$key").apply()

    fun effectiveUnread(c: Context, chat: ChatEntity): Int =
        when (readState(c, keyOf(chat))) {
            "read" -> 0
            "unread" -> if (chat.unreadCount > 0) chat.unreadCount else 1
            else -> chat.unreadCount
        }

    // ===== قوائم مخصّصة =====
    private const val LIST_NAMES = "list_names"

    fun listNames(c: Context): List<String> =
        getSet(c, LIST_NAMES).sorted()

    fun createList(c: Context, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val s = getSet(c, LIST_NAMES); s.add(n)
        sp(c).edit().putStringSet(LIST_NAMES, s).apply()
    }

    fun deleteList(c: Context, name: String) {
        val s = getSet(c, LIST_NAMES); s.remove(name)
        sp(c).edit().putStringSet(LIST_NAMES, s).remove("list_$name").apply()
    }

    fun listMembers(c: Context, name: String): MutableSet<String> = getSet(c, "list_$name")

    fun isInList(c: Context, name: String, key: String) = listMembers(c, name).contains(key)

    fun toggleListMember(c: Context, name: String, key: String) {
        val s = listMembers(c, name)
        if (s.contains(key)) s.remove(key) else s.add(key)
        sp(c).edit().putStringSet("list_$name", s).apply()
    }
}
