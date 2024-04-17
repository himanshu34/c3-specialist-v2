package com.nayan.nayancamv2

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.databinding.ListItemAiResultsBinding

class AIResultsAdapter : RecyclerView.Adapter<AIResultsAdapter.ViewHolder>() {

    private var dataSet: ArrayList<HashMap<Int, Pair<Bitmap, String>>> = arrayListOf()

    class ViewHolder(val binding: ListItemAiResultsBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ListItemAiResultsBinding.inflate(
                LayoutInflater.from(viewGroup.context),
                viewGroup,
                false
            )
        )
    }

    fun addAll(aiResultList: ArrayList<HashMap<Int, Pair<Bitmap, String>>>) {
        this.dataSet = aiResultList
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val dataMap = dataSet[position]
        viewHolder.binding.apply {
            img1.setImageBitmap(null)
            tvImg1Size.text = ""
            tvImg1Size.gone()

            img2.setImageBitmap(null)
            tvImg2Size.text = ""
            tvImg2Size.gone()

            img3.setImageBitmap(null)
            tvImg3Size.text = ""
            tvImg3Size.gone()
        }

        dataMap.forEach { (key, value) ->
            val bitmap = value.first
            val className = value.second
            when (key) {
                0 -> {
                    viewHolder.binding.apply {
                        img1.setImageBitmap(bitmap)
//                        tvImg1Size.text = String.format("${bitmap.width} * ${bitmap.height}")
                        tvImg1Size.text = className
                        tvImg1Size.visible()
                    }
                }
                1 -> {
                    viewHolder.binding.apply {
                        img2.setImageBitmap(bitmap)
                        tvImg2Size.text = String.format("${bitmap.width} * ${bitmap.height}")
                        tvImg2Size.visible()
                    }
                }
                2 -> {
                    viewHolder.binding.apply {
                        img3.setImageBitmap(bitmap)
                        tvImg3Size.text = String.format("${bitmap.width} * ${bitmap.height}")
                        tvImg3Size.visible()
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }
}