package co.nayan.c3specialist_v2.wallet.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.DialogFragment
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.TransactionStatus
import co.nayan.c3specialist_v2.databinding.LayoutTransactionsFilterBinding
import co.nayan.c3v2.core.models.c3_module.PresentedTransactionFilters
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TransactionsFilterDialog : BottomSheetDialogFragment() {

    lateinit var onFiltersSelection: OnFiltersSelection
    private var filters: PresentedTransactionFilters? = null
    private val appliedFilters = mutableListOf<String>()
    private lateinit var binding: LayoutTransactionsFilterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        arguments?.let {
            filters = it.parcelable(FILTERS)
            it.getStringArrayList(APPLIED_FILTERS)?.let { filterList ->
                appliedFilters.addAll(filterList)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutTransactionsFilterBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupView()
        setupClicks()
        setupCheckBoxes()
    }

    private fun setupCheckBoxes() {
        binding.createdCb.setOnCheckChanged()
        binding.processedCb.setOnCheckChanged()
        binding.failedCb.setOnCheckChanged()
        binding.initiatedCb.setOnCheckChanged()
    }

    private fun applyFilters() {
        if (this@TransactionsFilterDialog::onFiltersSelection.isInitialized) {
            val applied = mutableListOf<String>()

            if (binding.createdCb.isChecked) applied.add(TransactionStatus.CREATED)
            if (binding.processedCb.isChecked) applied.add(TransactionStatus.PROCESSED)
            if (binding.failedCb.isChecked) applied.add(TransactionStatus.FAILURE)
            if (binding.initiatedCb.isChecked) applied.add(TransactionStatus.INITIATED)

            onFiltersSelection.apply(applied)
            dismiss()
        }
    }

    private fun setupClicks() {
        binding.createdContainer.setOnClickListener {
            binding.createdCb.isChecked = !binding.createdCb.isChecked
        }
        binding.initiatedContainer.setOnClickListener {
            binding.initiatedCb.isChecked = !binding.initiatedCb.isChecked
        }
        binding.failedContainer.setOnClickListener {
            binding.failedCb.isChecked = !binding.failedCb.isChecked
        }
        binding.processedContainer.setOnClickListener {
            binding.processedCb.isChecked = !binding.processedCb.isChecked
        }
        binding.applyBtn.setOnClickListener {
            applyFilters()
        }
        binding.closeTxt.setOnClickListener {
            dismiss()
        }
        binding.clearTxt.setOnClickListener {
            if (this@TransactionsFilterDialog::onFiltersSelection.isInitialized) {
                onFiltersSelection.clear()
                dismiss()
            }
        }
    }

    private fun setupApplyButton() {
        binding.applyBtn.isEnabled =
            binding.createdCb.isChecked || binding.initiatedCb.isChecked || binding.failedCb.isChecked || binding.processedCb.isChecked
    }

    private fun setupView() {
        filters?.let {
            if (it.isCreatedPresent) {
                binding.createdContainer.visible()
            }
            if (it.isFailedPresent) {
                binding.failedContainer.visible()
            }
            if (it.isProcessedPresent) {
                binding.processedContainer.visible()
            }
            if (it.isInitiatedPresent) {
                binding.initiatedContainer.visible()
            }
        }

        binding.createdCb.isChecked = appliedFilters.contains(TransactionStatus.CREATED)
        binding.processedCb.isChecked = appliedFilters.contains(TransactionStatus.PROCESSED)
        binding.failedCb.isChecked = appliedFilters.contains(TransactionStatus.FAILURE)
        binding.initiatedCb.isChecked = appliedFilters.contains(TransactionStatus.INITIATED)

        setupApplyButton()
    }

    private fun CheckBox.setOnCheckChanged() {
        setOnCheckedChangeListener { _, _ ->
            setupApplyButton()
        }
    }

    companion object {
        const val FILTERS = "filters"
        const val APPLIED_FILTERS = "appliedFilters"

        fun newInstance(
            callback: OnFiltersSelection,
            filters: PresentedTransactionFilters?,
            appliedFilters: ArrayList<String>?
        ): TransactionsFilterDialog {
            val transactionsFilterDialog = TransactionsFilterDialog()
            transactionsFilterDialog.onFiltersSelection = callback

            val bundle = Bundle()
            bundle.putParcelable(FILTERS, filters)
            bundle.putStringArrayList(APPLIED_FILTERS, appliedFilters)

            transactionsFilterDialog.arguments = bundle
            return transactionsFilterDialog
        }
    }
}

interface OnFiltersSelection {
    fun apply(filters: List<String>)
    fun clear()
}