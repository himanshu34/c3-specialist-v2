package co.nayan.c3specialist_v2.wallet.referral_transactions

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.RefListLevelItemBinding
import co.nayan.c3v2.core.models.c3_module.ReferralUser

class ReferralNetworkAdapter(private val mContext: Context) : RecyclerView.Adapter<ReferralNetworkAdapter.ViewHolder>() {

    private val referralNetworkList = mutableListOf<MutableList<ReferralUser?>>()

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            RefListLevelItemBinding.inflate(
                LayoutInflater.from(viewGroup.context),
                viewGroup,
                false
            )
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val totalAmount = (referralNetworkList[position]).sumOf { it?.amount?.toDoubleOrNull()?:0.0 }
        holder.binding.level.text = "${mContext.getString(R.string.level_text)} ${position + 1}: "
        holder.binding.levelAmount.text=" ₹${String.format("%.2f", totalAmount)}"
        holder.binding.levelUsers.apply {
            val linearLayoutManager = LinearLayoutManager(context)
            linearLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
            adapter = ReferralUsersAdapter(referralNetworkList[position])
            layoutManager = linearLayoutManager
        }
    }

    fun addAll(toAdd: MutableList<MutableList<ReferralUser?>>) {
        referralNetworkList.clear()
        referralNetworkList.addAll(toAdd)
    }

    override fun getItemCount(): Int {
        return referralNetworkList.size
    }

    class ViewHolder(val binding: RefListLevelItemBinding) : RecyclerView.ViewHolder(binding.root)
}