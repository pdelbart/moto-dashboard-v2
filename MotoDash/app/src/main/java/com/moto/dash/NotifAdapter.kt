package com.moto.dash

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotifAdapter(private val onTap: (NotifItem) -> Unit) :
    RecyclerView.Adapter<NotifAdapter.VH>() {

    private var data: List<NotifItem> = emptyList()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(list: List<NotifItem>) {
        data = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.nIcon)
        val app: TextView = v.findViewById(R.id.nApp)
        val text: TextView = v.findViewById(R.id.nText)
        val time: TextView = v.findViewById(R.id.nTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notif, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = data[position]
        holder.app.text = n.app
        holder.text.text = n.text
        holder.time.text = fmt.format(Date(n.time))
        holder.icon.setImageDrawable(n.icon)
        holder.itemView.setOnClickListener { onTap(n) }
    }
}
