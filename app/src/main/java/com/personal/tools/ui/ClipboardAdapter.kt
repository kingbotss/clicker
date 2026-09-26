package com.personal.tools.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.personal.tools.R

/** Renders the clipboard history; row tap re-copies, button deletes. */
class ClipboardAdapter(
    private var items: List<String>,
    private val onCopy: (String) -> Unit,
    private val onDelete: (Int) -> Unit,
) : RecyclerView.Adapter<ClipboardAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.clip_text)
        val delete: Button = view.findViewById(R.id.btn_delete)
    }

    fun submit(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_clip, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val value = items[position]
        holder.text.text = value
        holder.itemView.setOnClickListener { onCopy(value) }
        holder.delete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete(pos)
        }
    }

    override fun getItemCount(): Int = items.size
}
