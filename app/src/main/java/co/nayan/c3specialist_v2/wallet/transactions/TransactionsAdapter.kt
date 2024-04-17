package co.nayan.c3specialist_v2.wallet.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.TransactionStatus
import co.nayan.c3specialist_v2.databinding.LayoutTransactionsItemBinding
import co.nayan.c3specialist_v2.databinding.RowLoadingBinding
import co.nayan.c3specialist_v2.utils.timeString
import co.nayan.c3specialist_v2.wallet.WalletViewModel
import co.nayan.c3v2.core.models.c3_module.PresentedTransactionFilters
import co.nayan.c3v2.core.models.c3_module.Transaction
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import java.util.Date

class TransactionsAdapter(
    private val viewModel: WalletViewModel,
    private val onTransactionClickListener: OnTransactionClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val appliedFilters = arrayListOf<String>()
    private val transactions = mutableListOf<Transaction>()
    private val filteredTransactions = mutableListOf<Transaction>()
    private val viewTypeLoading = 0
    private val viewTypeItem = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            viewTypeLoading -> {
                LoadingViewHolder(
                    RowLoadingBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }

            else -> {
                TransactionViewHolder(
                    LayoutTransactionsItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ), onItemClickListener
                )
            }
        }
    }

    override fun getItemCount() = filteredTransactions.size

    override fun getItemViewType(position: Int): Int {
        return if (position == transactions.size - 1 && viewModel.shouldStopLoading.not())
            viewTypeLoading else viewTypeItem
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        when (viewHolder) {
            is TransactionViewHolder -> viewHolder.onBind(filteredTransactions[position])
            else -> {

            }
        }
    }

    fun addAll(toAdd: List<Transaction>) {
        val diffCourses = DiffUtil.calculateDiff(TransactionDiffCallback(transactions, toAdd))
        toAdd.apply {
            transactions.clear()
            transactions.addAll(this)
            filteredTransactions.clear()
            if (getAppliedFilters().isNotEmpty())
                filteredTransactions.addAll(transactions.filter { getAppliedFilters().contains(it.aasmState) })
            else filteredTransactions.addAll(transactions)
        }

        if (getAppliedFilters().isNotEmpty()) notifyDataSetChanged()
        else diffCourses.dispatchUpdatesTo(this)
    }

    private val onItemClickListener = View.OnClickListener {
        val transaction = it.getTag(R.id.transaction) as Transaction
        onTransactionClickListener.onClick(transaction)
    }

    fun getPresentFilterItems() = PresentedTransactionFilters(
        transactions.any { it.aasmState == TransactionStatus.PROCESSED },
        transactions.any { it.aasmState == TransactionStatus.FAILURE },
        transactions.any { it.aasmState == TransactionStatus.INITIATED },
        transactions.any { it.aasmState == TransactionStatus.CREATED }
    )

    fun setFilters(filters: List<String>) {
        appliedFilters.clear()
        filteredTransactions.clear()
        if (filters.isNotEmpty()) {
            appliedFilters.addAll(filters)
            filteredTransactions.addAll(transactions.filter { filters.contains(it.aasmState) })
        } else filteredTransactions.addAll(transactions)

        notifyDataSetChanged()
    }

    fun getAppliedFilters() = appliedFilters
    fun isTransactionsPresent() = transactions.isNotEmpty()

    private class TransactionDiffCallback(
        private val oldList: List<Transaction>,
        private val newList: List<Transaction>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}

class LoadingViewHolder(val binding: RowLoadingBinding) : RecyclerView.ViewHolder(binding.root)

class TransactionViewHolder(
    val binding: LayoutTransactionsItemBinding,
    private val onItemClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun onBind(transaction: Transaction) {
        binding.pointsTxt.text = String.format(
            itemView.context.getString(R.string.transaction_points),
            transaction.score ?: 0
        )
        binding.amountTxt.text =
            String.format("₹ ${transaction.amountRupees ?: 0}.${transaction.amountPaise ?: 0}")


        setupTransactionCreatedOn(transaction.createdAt)
        setupTransactionUpdatedOn(transaction.updatedAt)
        setupTransactionStatus(transaction.aasmState)

        itemView.setOnClickListener(onItemClickListener)
        itemView.setTag(R.id.transaction, transaction)
    }

    private fun setupTransactionCreatedOn(createdAt: Date?) {
        if (createdAt == null) binding.createdOnTxt.gone()
        else {
            binding.createdOnTxt.visible()
            binding.createdOnTxt.text = createdAt.timeString()
        }
    }

    private fun setupTransactionUpdatedOn(updatedOn: Date?) {
        if (updatedOn == null) binding.lastUpdatedOnTxt.gone()
        else {
            binding.lastUpdatedOnTxt.visible()
            binding.lastUpdatedOnTxt.text = String.format(
                itemView.context.getString(R.string.last_updated_on), updatedOn.timeString()
            )
        }
    }

    private fun setupTransactionStatus(aasmState: String?) {
        val iconId = getTransactionStatusIcon(aasmState)
        if (iconId == null) {
            binding.statusIv.gone()
            binding.statusTxt.gone()
        } else {
            binding.statusIv.visible()
            binding.statusTxt.visible()
            binding.statusTxt.text = aasmState
            binding.statusIv.setImageDrawable(ContextCompat.getDrawable(itemView.context, iconId))
        }
    }

    private fun getTransactionStatusIcon(aasmState: String?) = when (aasmState) {
        TransactionStatus.CREATED -> R.drawable.ic_created
        TransactionStatus.INITIATED -> R.drawable.ic_initiated
        TransactionStatus.PROCESSED -> R.drawable.ic_processed
        TransactionStatus.FAILURE -> R.drawable.ic_failed
        else -> null
    }
}

interface OnTransactionClickListener {
    fun onClick(transaction: Transaction)
}