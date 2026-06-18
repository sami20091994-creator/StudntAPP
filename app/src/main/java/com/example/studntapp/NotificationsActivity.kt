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

class NotificationsActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private var userId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        supportActionBar?.title = "الإشعارات"

        userId = getSharedPreferences("AppSession", Context.MODE_PRIVATE).getInt("USER_ID", 0)

        rv = findViewById(R.id.rvNotifications)
        rv.layoutManager = LinearLayoutManager(this)

        loadNotifications()
    }

    private fun loadNotifications() {
        RetrofitClient.instance.getNotifications(userId = userId).enqueue(object : Callback<NotificationResponse> {
            override fun onResponse(call: Call<NotificationResponse>, response: Response<NotificationResponse>) {
                if (response.isSuccessful) {
                    val list = response.body()?.data ?: emptyList()
                    if (list.isEmpty()) {
                        Toast.makeText(this@NotificationsActivity, "لا توجد إشعارات حالياً", Toast.LENGTH_SHORT).show()
                    }
                    rv.adapter = NotificationsAdapter(list)
                }
            }
            override fun onFailure(call: Call<NotificationResponse>, t: Throwable) {
                Toast.makeText(this@NotificationsActivity, "السيرفر المحلي لا يستجيب", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

class NotificationsAdapter(private val list: List<NotificationData>) : RecyclerView.Adapter<NotificationsAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvNotifTitle)
        val message: TextView = v.findViewById(R.id.tvNotifBody)
        val time: TextView = v.findViewById(R.id.tvNotifTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.title.text = item.title
        holder.message.text = item.message
        holder.time.text = item.createdAt
    }
    override fun getItemCount() = list.size
}