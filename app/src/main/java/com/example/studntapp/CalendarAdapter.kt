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
    }

    override fun getItemCount(): Int = scheduleList.size
}
