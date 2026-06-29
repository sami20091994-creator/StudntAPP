package com.example.studntapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CalendarAdapter(
    private val scheduleList: List<ScheduleData>,
    private val selectedDate: String
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCalTime: TextView = view.findViewById(R.id.tvCalTime)
        val tvCalSubject: TextView = view.findViewById(R.id.tvCalSubject)
        val tvCalDateAndRoom: TextView = view.findViewById(R.id.tvCalDateAndRoom)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val item = scheduleList[position]
        holder.tvCalTime.text = "${item.startTime} - ${item.endTime}"
        holder.tvCalSubject.text = item.subjectName ?: "N/A"
        holder.tvCalDateAndRoom.text = "${item.startDate} | ${item.classroom ?: "N/A"}"

        // حدث منصرم → شطب النص + تعتيم للدلالة أنه مضى.
        val past = isPast(item)
        listOf(holder.tvCalTime, holder.tvCalSubject, holder.tvCalDateAndRoom).forEach { tv ->
            tv.paintFlags = if (past)
                tv.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            else
                tv.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        holder.itemView.alpha = if (past) 0.5f else 1f
    }

    /** هل انتهى موعد الحصة (التاريخ + وقت النهاية) قبل الآن؟ */
    private fun isPast(item: ScheduleData): Boolean {
        return try {
            val d = item.startDate ?: return false
            val end = (item.endTime ?: "23:59").take(5) // HH:mm
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ENGLISH)
            val dt = fmt.parse("$d $end") ?: return false
            dt.before(java.util.Date())
        } catch (e: Exception) { false }
    }

    override fun getItemCount(): Int = scheduleList.size
}
