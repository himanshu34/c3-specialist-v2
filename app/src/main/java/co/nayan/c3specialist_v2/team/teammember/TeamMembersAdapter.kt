package co.nayan.c3specialist_v2.team.teammember

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.MemberStatus
import co.nayan.c3specialist_v2.databinding.TeamMemberItemBinding
import co.nayan.c3specialist_v2.utils.setRandomColor
import co.nayan.c3v2.core.models.c3_module.responses.Member
import co.nayan.c3v2.core.models.c3_module.responses.initials
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import timber.log.Timber
import java.util.*

class TeamMembersAdapter(
    private val onTeamMemberClickListener: OnTeamMemberClickListener
) : RecyclerView.Adapter<TeamMemberViewHolder>() {

    private val members = mutableListOf<Member>()
    private val filteredItems = mutableListOf<Member>()
    private var searchQuery: String = ""
    private var status = MemberStatus.MEMBERS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamMemberViewHolder {
        return TeamMemberViewHolder(
            TeamMemberItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onClickListener
        )
    }

    override fun getItemCount() = filteredItems.size

    override fun onBindViewHolder(holder: TeamMemberViewHolder, position: Int) {
        holder.bind(filteredItems[position], status)
    }

    private val onClickListener = View.OnClickListener {
        val member = it.getTag(R.id.team_member) as Member
        onTeamMemberClickListener.onClick(member)
    }

    fun addAll(toAdd: List<Member>, toSet: MemberStatus) {
        status = toSet
        members.clear()
        members.addAll(toAdd)

        filterListItems(searchQuery)
    }

    fun filterListItems(query: String) {
        searchQuery = query
        filteredItems.clear()
        val items = if (query.isNotEmpty()) {
            members.filter {
                (it.name ?: "").lowercase(Locale.getDefault())
                    .contains(query.lowercase(Locale.getDefault()))
            }
        } else {
            members
        }
        filteredItems.addAll(items)
    }
}

class TeamMemberViewHolder(
    val binding: TeamMemberItemBinding,
    private val onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(member: Member, status: MemberStatus) {
        binding.nameTxt.text = member.name
        try {
            binding.profileTxt.text = member.name.initials()
        } catch (e: Exception) {
            e.printStackTrace()
            Timber.e("${member.name} error found")
        }
        binding.emailTxt.text = member.email ?: "Email"
        binding.phoneNumberTxt.text = member.phoneNumber ?: "Mobile number"

        if (status == MemberStatus.MEMBERS) binding.inviteUserIv.visible()
        else binding.inviteUserIv.gone()

        binding.profileIv.setRandomColor()
        binding.inviteUserIv.setOnClickListener(onClickListener)
        binding.inviteUserIv.setTag(R.id.team_member, member)
    }
}

interface OnTeamMemberClickListener {
    fun onClick(teamMember: Member)
}