package com.mossa.pro

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PacketAdapter(
    private val items: List<PacketInfo>,
    private val onClick: (PacketInfo) -> Unit
) : RecyclerView.Adapter<PacketAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.tvNumber)
        val type: TextView = v.findViewById(R.id.tvType)
        val direction: TextView = v.findViewById(R.id.tvDirection)
        val time: TextView = v.findViewById(R.id.tvTime)
        val size: TextView = v.findViewById(R.id.tvSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_packet, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.number.text = "#${p.number}"
        holder.type.text = "${p.type} (${PacketTypes.name(p.type)})"
        holder.direction.text = p.direction
        holder.time.text = p.datetime.takeLast(8)
        holder.size.text = "${p.hex.length / 2}B"

        try {
            holder.type.setTextColor(Color.parseColor(PacketTypes.color(p.type)))
        } catch (_: Exception) {}

        holder.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount() = items.size
}
