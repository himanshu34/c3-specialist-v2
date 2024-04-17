package co.nayan.c3specialist_v2.performance.teamperformance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.MemberPerformanceItemBinding
import co.nayan.c3v2.core.models.c3_module.responses.MemberStats
import java.util.*

class MembersPerformanceAdapter(
    private val onMemberStatsClickListener: OnMemberStatsClickListener
) : RecyclerView.Adapter<MemberStatsViewHolder>() {

    private val members = mutableListOf<MemberStats>()
    private val filteredItems = mutableListOf<MemberStats>()
    private var searchQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberStatsViewHolder {
        return MemberStatsViewHolder(
            MemberPerformanceItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onClickListener
        )
    }

    override fun getItemCount() = filteredItems.size

    override fun onBindViewHolder(holder: MemberStatsViewHolder, position: Int) {
        holder.bind(filteredItems[position])
    }

    private val onClickListener = View.OnClickListener {
        val stats = it.getTag(R.id.member_stats) as MemberStats
        onMemberStatsClickListener.onClick(stats)
    }

    fun addAll(toAdd: List<MemberStats>) {
        members.clear()
        members.addAll(toAdd)

        filterListItems(searchQuery)
    }

    fun filterListItems(query: String) {
        searchQuery = query
        filteredItems.clear()
        val items = if (query.isNotEmpty()) {
            members.filter {
                (it.userName ?: "").lowercase(Locale.getDefault())
                    .contains(query.lowercase(Locale.getDefault()))
            }
        } else members
        filteredItems.addAll(items)
    }
}

class MemberStatsViewHolder(
    val binding: MemberPerformanceItemBinding,
    private val onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(stats: MemberStats) {
        binding.nameTxt.text = stats.userName
        binding.emailTxt.text = stats.userEmail
        binding.accuracyTxt.text = String.format("%.1f%%", stats.getAccuracy())
        binding.accuracyTxt.textColor(stats.getAccuracy())
        binding.hoursWorkedTxt.text = String.format("%s hrs worked", stats.workDuration ?: "00:00")

        itemView.setOnClickListener(onClickListener)
        itemView.setTag(R.id.member_stats, stats)
    }

    fun TextView.textColor(accuracy: Float) {
        val colorId = when {
            accuracy >= 80 -> {
                R.color.green
            }
            accuracy >= 60 -> {
                R.color.yellow
            }
            else -> {
                R.color.red
            }
        }
        setTextColor(ContextCompat.getColor(context, colorId))
    }
}

interface OnMemberStatsClickListener {
    fun onClick(stats: MemberStats)
}