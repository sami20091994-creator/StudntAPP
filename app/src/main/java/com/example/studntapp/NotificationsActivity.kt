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
    private var adapter: NotificationsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        supportActionBar?.title = "الإشعارات"

        userId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)

        rv = findViewById(R.id.rvNotifications)
        rv.layoutManager = LinearLayoutManager(this)

        // السحب لليمين يعلّم الإشعار كمقروء.
        val swipe = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val a = adapter ?: return 0
                val item = a.itemAt(vh.bindingAdapterPosition) ?: return 0
                // المقروء مسبقاً لا يُسحب.
                return if (NotifReadStore.isRead(this@NotificationsActivity, item)) 0
                else androidx.recyclerview.widget.ItemTouchHelper.RIGHT
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                adapter?.markReadAt(vh.bindingAdapterPosition)
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipe).attachToRecyclerView(rv)

        loadNotifications()
        
        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh?.setOnRefreshListener { loadNotifications() }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        // أيقونة في الشريط العلوي لتعليم الكل كمقروء (بدل قائمة الثلاث نقاط).
        menu.add(0, 1, 0, "تعليم الكل كمقروء").apply {
            setIcon(R.drawable.ic_done_all)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == 1) { markAllRead(); return true }
        return super.onOptionsItemSelected(item)
    }

    /** ضغط الإشعار: يُعلّمه مقروءاً. إشعار رسالة فقط يفتح الدردشة (الـAPI الجديد بلا sender_id لغير الرسائل). */
    private fun onNotificationClick(item: NotificationData) {
        NotifReadStore.markRead(this, listOf(item.id))
        adapter?.refreshReadState()
        val hasSender = (item.senderId ?: 0) != 0
        val isMsg = hasSender || (item.title?.contains("رسالة") == true)
        if (!isMsg) return // إشعار عام — يبقى داخل شاشة الإشعارات
        val i = android.content.Intent(this, MessagesActivity::class.java).apply {
            if (hasSender) {
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
        adapter?.refreshReadState()
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
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                if (response.isSuccessful) {
                    val list = response.body()?.data ?: emptyList()
                    lastLoaded = list
                    if (list.isEmpty()) {
                        Toast.makeText(this@NotificationsActivity, "لا توجد إشعارات حالياً", Toast.LENGTH_SHORT).show()
                    }
                    adapter = NotificationsAdapter(this@NotificationsActivity, list.toMutableList()) { item -> onNotificationClick(item) }
                    rv.adapter = adapter
                }
            }
            override fun onFailure(call: Call<NotificationResponse>, t: Throwable) {
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
                Toast.makeText(this@NotificationsActivity, "السيرفر المحلي لا يستجيب", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

class NotificationsAdapter(
    private val ctx: Context,
    private val list: MutableList<NotificationData>,
    private val onClick: (NotificationData) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvNotifTitle)
        val message: TextView = v.findViewById(R.id.tvNotifBody)
        val time: TextView = v.findViewById(R.id.tvNotifTime)
        val strip: View = v.findViewById(R.id.viewUnreadStrip)
        val dot: View = v.findViewById(R.id.dotUnread)
    }

    fun itemAt(pos: Int): NotificationData? = list.getOrNull(pos)

    /** تعليم عنصر واحد كمقروء (السحب لليمين). */
    fun markReadAt(pos: Int) {
        val item = list.getOrNull(pos) ?: return
        NotifReadStore.markRead(ctx, listOf(item.id))
        notifyItemChanged(pos)
    }

    /** إعادة رسم حالة القراءة لكل العناصر. */
    fun refreshReadState() = notifyDataSetChanged()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        // حالة القراءة: غير المقروء يُظهر الشريط الجانبي والنقطة بلون ذهبي وبوضوح كامل.
        val read = NotifReadStore.isRead(ctx, item)
        holder.strip.visibility = if (read) View.INVISIBLE else View.VISIBLE
        holder.dot.visibility = if (read) View.GONE else View.VISIBLE
        holder.itemView.alpha = if (read) 0.6f else 1f
        // الـAPI الجديد بلا type/sender — نستدلّ على الرسالة من العنوان أيضاً.
        val isMessage = item.type == "message" || !item.senderName.isNullOrBlank() || (item.title?.contains("رسالة") == true)
        if (isMessage) {
            // رسالة: اسم المرسل كعنوان + مضمون الرسالة تحته.
            val nm = item.senderName?.takeIf { it.isNotBlank() && it != "null" }
                ?: item.title?.takeIf { it.isNotBlank() && it != "null" }
                ?: "مرسل غير معروف"
            holder.title.text = "💬 $nm"
            holder.message.text = item.message ?: "اضغط لفتح المحادثة"
        } else {
            // إشعار متابعة: نص الإشعار.
            holder.title.text = item.title ?: "إشعار"
            holder.message.text = item.message
        }
        holder.time.text = item.createdAt ?: item.date
        holder.itemView.setOnClickListener { onClick(item) }
    }
    override fun getItemCount() = list.size
}