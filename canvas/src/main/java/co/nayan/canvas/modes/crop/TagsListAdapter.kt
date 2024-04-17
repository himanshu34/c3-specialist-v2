package co.nayan.canvas.modes.crop

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import co.nayan.c3v2.core.models.Template
import co.nayan.canvas.R
import co.nayan.canvas.databinding.LayoutTagsGroupBinding
import java.util.*

class TagsListAdapter(
    private val onTagsSelectedListener: OnTagsSelectedListener
) : BaseExpandableListAdapter() {

    private val filteredTags = mutableMapOf<String, MutableList<Template>>()
    private val tags = mutableMapOf<String, MutableList<Template>>()
    private val groupHeads = mutableListOf<String>()
    private var searchTerm = ""

    fun add(toAdd: Map<String, MutableList<Template>>) {
        tags.clear()
        filteredTags.clear()
        groupHeads.clear()
        for ((key, value) in toAdd) {
            groupHeads.add(key)
            tags[key] = value
            filteredTags[key] = value
        }
    }

    override fun getGroupCount() = filteredTags.size

    override fun getChildrenCount(groupPosition: Int): Int {
        return filteredTags[groupHeads[groupPosition]]?.size ?: 0
    }

    override fun getGroup(groupPosition: Int): Any? {
        return filteredTags[groupHeads[groupPosition]]
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        return filteredTags[groupHeads[groupPosition]]?.run {
            this[childPosition]
        }
    }

    override fun getGroupId(groupPosition: Int) = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int) = childPosition.toLong()

    override fun hasStableIds() = false

    override fun getGroupView(
        groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?
    ): View? {
        val groupTitle = groupHeads[groupPosition]
        parent?.let {
            val binding =
                LayoutTagsGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            binding.tvGroupTitle.text = groupTitle
            if (isExpanded) binding.ivExpand.rotation = 90f
            else binding.ivExpand.rotation = 0f
            return binding.root
        }
        return null
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View? {
        val tag = getChild(groupPosition, childPosition) as Template
        parent?.let {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.layout_tag, parent, false)
            val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
            val flRoot = view.findViewById<FrameLayout>(R.id.flRoot)

            tvTitle.text = tag.templateName
            if (tag.isSelected) {
                flRoot.background =
                    ContextCompat.getDrawable(parent.context, R.drawable.bg_tag_group_selected)
                tvTitle.setTextColor(Color.parseColor("#80000000"))
            } else {
                flRoot.setBackgroundColor(Color.TRANSPARENT)
                tvTitle.setTextColor(Color.parseColor("#80FFFFFF"))
            }

            flRoot.setOnClickListener {
                tag.isSelected = !tag.isSelected
                updateSelection(tag.id, tag.isSelected)
                notifyDataSetChanged()
                onTagsSelectedListener.onSelected(tag, tag.isSelected)
            }
            return view
        }
        return null
    }

    private fun updateSelection(tagId: Int?, isSelected: Boolean) {
        for ((_, value) in tags) {
            val tag = value.find { it.id == tagId }
            if (tag != null) {
                tag.isSelected = isSelected
                break
            }
        }
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int) = true

    fun filter(constraint: String) {
        searchTerm = constraint.lowercase(Locale.getDefault())
        if (searchTerm.isNotEmpty()) {
            for ((key, value) in tags) {
                val filteredValues = value.filter {
                    it.templateName.lowercase(Locale.getDefault()).contains(searchTerm)
                } as MutableList
                filteredTags[key] = filteredValues
            }
        } else {
            for ((key, value) in tags) {
                filteredTags[key] = value
            }
        }
        notifyDataSetChanged()
    }

    fun update(selectedTags: List<String>) {
        for ((_, value) in tags) {
            value.forEach { tag ->
                tag.isSelected = selectedTags.contains(tag.templateName)
            }
        }
        for ((_, value) in filteredTags) {
            value.forEach { tag ->
                tag.isSelected = selectedTags.contains(tag.templateName)
            }
        }
        notifyDataSetChanged()
    }
}

interface OnTagsSelectedListener {
    fun onSelected(tag: Template, selected: Boolean)
}