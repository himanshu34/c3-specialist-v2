package co.nayan.c3specialist_v2.screen_sharing.users

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutUserItemBinding
import co.nayan.c3specialist_v2.databinding.ListItemProgressBinding
import co.nayan.c3specialist_v2.incorrectrecords.ProgressViewHolder
import co.nayan.c3specialist_v2.utils.setRandomColor
import co.nayan.c3v2.core.models.c3_module.UserListItem
import co.nayan.review.recordsgallery.viewholders.BaseViewHolder
import java.util.Locale

class UsersAdapter(private val userClickListener: OnUserClickListener) :
    RecyclerView.Adapter<BaseViewHolder>() {

    var shouldLoadMore: Boolean = true
    private val userListItems = mutableListOf<UserListItem>()
    private val filteredUsers = mutableListOf<UserListItem>()
    private var searchQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == ITEM_TYPE_USER) {
            return UserViewHolder(
                LayoutUserItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ), onClickListener
            )
        } else {
            ProgressViewHolder(
                ListItemProgressBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (getItemViewType(position) == ITEM_TYPE_USER) {
            (holder as UserViewHolder).bind(filteredUsers[position])
        } else {
            if (shouldLoadMore) {
                (holder as ProgressViewHolder).showProgressBar()
            } else {
                (holder as ProgressViewHolder).hideProgressBar()
            }
        }
    }

    override fun getItemCount(): Int {
        return if (filteredUsers.size > 0) {
            filteredUsers.size + 1
        } else {
            filteredUsers.size
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == filteredUsers.size) {
            ITEM_TYPE_LOADER
        } else {
            ITEM_TYPE_USER
        }
    }

    fun addAll(toAdd: List<UserListItem>) {
        userListItems.clear()
        userListItems.addAll(toAdd)
        filterListItems(searchQuery)
    }

    fun addNewUsers(toAdd: List<UserListItem>) {
        val startIndex = userListItems.size
        val endIndex = startIndex + toAdd.size

        userListItems.addAll(toAdd)
        filterListItems(searchQuery)
        notifyItemRangeChanged(startIndex, endIndex)
    }

    private val onClickListener = View.OnClickListener {
        val user = it.getTag(R.id.user_info) as UserListItem
        when (it.id) {
            R.id.inviteUserIv -> userClickListener.onRequest(user)
        }
    }

    fun filterListItems(query: String) {
        searchQuery = query
        filteredUsers.clear()
        val items = if (query.isNotEmpty()) {
            userListItems.filter {
                (it.name ?: "").lowercase(Locale.getDefault())
                    .contains(query.lowercase(Locale.getDefault()))
            }
        } else {
            userListItems
        }
        filteredUsers.addAll(items)
    }

    companion object {
        const val ITEM_TYPE_USER = 1
        const val ITEM_TYPE_LOADER = 2
    }
}

interface OnUserClickListener {
    fun onRequest(user: UserListItem)
}

class UserViewHolder(
    val binding: LayoutUserItemBinding,
    private val onClickListener: View.OnClickListener
) : BaseViewHolder(binding.root) {

    fun bind(userListItem: UserListItem) {
        binding.userNameTxt.text = userListItem.name
        binding.profileTxt.text = userListItem.name?.first().toString()

        binding.profileIv.setRandomColor()
        binding.inviteUserIv.setOnClickListener(onClickListener)
        binding.inviteUserIv.setTag(R.id.user_info, userListItem)
    }
}