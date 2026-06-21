package com.example.studntapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NotificationsActivity : BaseActivity() {

    private lateinit var rv: RecyclerView
    private var userId = 0
    private var lastLoaded: List<NotificationData>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        supportActionBar?.title = "الإشعارات"

        userId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)

        rv = findViewById(R.id.rvNotifications)
        rv.layoutManager = LinearLayoutManager(this)

        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener { markAllRead() }

        loadNotifications()
    }

    /** ضغط الإشعار: يُعلّمه مقروءاً ويأخذنا إلى الدردشة (المحادثة المحدّدة إن توفّر المرسل). */
    private fun onNotificationClick(item: NotificationData) {
        NotifReadStore.markRead(this, listOf(item.id))
        val i = android.content.Intent(this, MessagesActivity::class.java).apply {
            if ((item.senderId ?: 0) != 0) {
                putExtra("OPEN_CHAT_ID", item.senderId)
                putExtra("OPEN_CHAT_TYPE", item.chatType ?: "user")
                putExtra("OPEN_CHAT_NAME", item.senderName)
            }
            addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(i)
        overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
    }

    private fun markAllRead() {
        // تعليم محلي دائم فوراً (يدوم عبر إعادة التشغيل).
        lastLoaded?.let { NotifReadStore.markRead(this, it.map { n -> n.id }) }
        Toast.makeText(this, "تم تعليم كل الإشعارات كمقروءة", Toast.LENGTH_SHORT).show()
        if (userId != 0) {
            RetrofitClient.instance.markAllNotificationsRead(userId = userId)
                .enqueue(object : Callback<SimpleResponse> {
                    override fun onResponse(call: Call<SimpleResponse>, response: Response<SimpleResponse>) {}
                    override fun onFailure(call: Call<SimpleResponse>, t: Throwable) {}
                })
        }
    }

    private fun loadNotifications() {
        RetrofitClient.instance.getNotifications(userId = userId).enqueue(object : Callback<NotificationResponse> {
            override fun onResponse(call: Call<NotificationResponse>, response: Response<NotificationResponse>) {
                if (response.isSuccessful) {
                    val list = response.body()?.data ?: emptyList()
                    lastLoaded = list
                    if (list.isEmpty()) {
                        Toast.makeText(this@NotificationsActivity, "لا توجد إشعارات حالياً", Toast.LENGTH_SHORT).show()
                    }
                    rv.adapter = NotificationsAdapter(list) { item -> onNotificationClick(item) }
                }
            }
            override fun onFailure(call: Call<NotificationResponse>, t: Throwable) {
                Toast.makeText(this@NotificationsActivity, "السيرفر المحلي لا يستجيب", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

class NotificationsAdapter(
    private val list: List<NotificationData>,
    private val onClick: (NotificationData) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvNotifTitle)
        val message: TextView = v.findViewById(R.id.tvNotifBody)
        val time: TextView = v.findViewById(R.id.tvNotifTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        val isMessage = item.type == "message"
        if (isMessage) {
            // إشعار رسالة: نعرض اسم المرسل ونوضّح أنها رسالة وأنها قابلة للفتح.
            holder.title.text = "💬 رسالة من ${item.senderName ?: "مستخدم"}"
            holder.message.text = item.message ?: "اضغط لفتح المحادثة"
        } else {
            holder.title.text = item.title
            holder.message.text = item.message
        }
        holder.time.text = item.createdAt ?: item.date
        holder.itemView.setOnClickListener { onClick(item) }
    }
    override fun getItemCount() = list.size
}