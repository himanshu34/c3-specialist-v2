package co.nayan.c3specialist_v2.wallet.referral_transactions

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.databinding.RefListUserItemBinding
import co.nayan.c3v2.core.models.c3_module.ReferralUser
import com.bumptech.glide.Glide

class ReferralUsersAdapter(
    private val data: List<ReferralUser?>
) : RecyclerView.Adapter<ReferralUsersAdapter.ViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            RefListUserItemBinding.inflate(
                LayoutInflater.from(viewGroup.context),
                viewGroup,
                false
            )
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.binding.userName.text = item?.name
        val amount = String.format("%.2f", item?.amount?.toDouble())
        holder.binding.userAmount.text = "₹$amount"
        holder.binding.userImage.apply {
            Glide.with(context)
                .load(item?.photoUrl)
                .circleCrop()
                .into(this)
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class ViewHolder(val binding: RefListUserItemBinding) : RecyclerView.ViewHolder(binding.root)
}