package com.nayan.nayancamv2.scout

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Events
import co.nayan.nayancamv2.databinding.ListItemEventGridBinding

class EventsAdapter : RecyclerView.Adapter<EventsAdapter.ViewHolder>() {

    private var isLandscape: Boolean? = null
    private val events = mutableListOf<Events>()

    fun addAll(toAdd: List<Events>) {
        events.clear()
        events.addAll(toAdd)
    }

    fun updateOrientation(isLandscape: Boolean) {
        this.isLandscape = isLandscape
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ListItemEventGridBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return events.size
    }

    @SuppressLint("SetTextI18n", "CheckResult")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        isLandscape?.let {
            val params = holder.binding.parent.layoutParams as GridLayoutManager.LayoutParams
            if (it) params.setMargins(0, 0, 0, 1)
            else params.setMargins(0, 0, 1, 0)
            holder.binding.parent.layoutParams = params
        }

        holder.binding.eventsData = events[position]
    }

    class ViewHolder(val binding: ListItemEventGridBinding) : RecyclerView.ViewHolder(binding.root)
}
