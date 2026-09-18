package com.mossa.pro

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class PacketAdapter(
    private val items: List<PacketInfo>,
    private val onClick: (PacketInfo) -> Unit
) : RecyclerView.Adapter<PacketAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val colorBar: View = v.findViewById(R.id.colorBar)
        val number: TextView = v.findViewById(R.id.tvNumber)
        val type: TextView = v.findViewById(R.id.tvType)
        val direction: TextView = v.findViewById(R.id.tvDirection)
        val time: TextView = v.findViewById(R.id.tvTime)
        val size: TextView = v.findViewById(R.id.tvSize)
        val category: TextView = v.findViewById(R.id.tvCategory)
        val star: TextView = v.findViewById(R.id.tvStar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_packet, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val important = PacketTypes.isImportant(p.type)

        holder.number.text = "#${p.number}"
        holder.type.text = "${p.type} · ${PacketTypes.name(p.type)}"
        holder.direction.text = p.direction
        holder.time.text = p.datetime.takeLast(8)
        holder.size.text = "${p.hex.length / 2} B"

        // الفئة
        val cat = PacketTypes.category(p.type)
        holder.category.text = cat.label
        try {
            holder.category.setTextColor(Color.parseColor(cat.color))
        } catch (_: Exception) {}

        // اللون
        try {
            val c = Color.parseColor(PacketTypes.color(p.type))
            holder.type.setTextColor(c)
            holder.colorBar.setBackgroundColor(c)
        } catch (_: Exception) {}

        // النجمة للباكيتات المهمة
        holder.star.visibility = if (important) View.VISIBLE else View.GONE

        // خلفية مختلفة للمهم
        val ctx = holder.itemView.context
        if (important) {
            holder.itemView.alpha = 1.0f
            holder.colorBar.layoutParams.width = (6 * ctx.resources.displayMetrics.density).toInt()
        } else {
            holder.itemView.alpha = 0.85f
            holder.colorBar.layoutParams.width = (3 * ctx.resources.displayMetrics.density).toInt()
        }

        holder.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount() = items.size
}
