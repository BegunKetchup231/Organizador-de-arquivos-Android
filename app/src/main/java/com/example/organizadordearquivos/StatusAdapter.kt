package com.example.organizadordearquivos

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class StatusAdapter(private val messages: MutableList<StatusMessage>) :
    RecyclerView.Adapter<StatusAdapter.StatusViewHolder>() {

    class StatusViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon_status)
        val text: TextView = view.findViewById(R.id.text_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_status, parent, false)
        return StatusViewHolder(view)
    }

    override fun getItemCount() = messages.size

    // MÉTODO onBindViewHolder CORRIGIDO
    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        val message = messages[position]
        holder.text.text = message.text

        val context = holder.itemView.context

        when (message.type) {
            StatusType.INFO -> {
                holder.icon.setImageResource(R.drawable.ic_info)
                // Usamos uma cor padrão de texto para informação
                val defaultTextColor = ContextCompat.getColor(context, android.R.color.tertiary_text_dark)
                holder.icon.setColorFilter(defaultTextColor)
                holder.text.setTextColor(defaultTextColor)
            }
            StatusType.SUCCESS -> {
                holder.icon.setImageResource(R.drawable.ic_success)
                // Usamos a cor verde padrão do Android
                val successColor = ContextCompat.getColor(context, android.R.color.holo_green_dark)
                holder.icon.setColorFilter(successColor)
                holder.text.setTextColor(successColor)
            }
            StatusType.ERROR -> {
                holder.icon.setImageResource(R.drawable.ic_error)
                // Usamos a cor vermelha padrão do Android
                val errorColor = ContextCompat.getColor(context, android.R.color.holo_red_dark)
                holder.icon.setColorFilter(errorColor)
                holder.text.setTextColor(errorColor)
            }
        }
    }

    fun addMessage(message: StatusMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }
}