package co.nayan.c3specialist_v2.profile.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.KycStatus
import co.nayan.c3specialist_v2.config.KycType
import co.nayan.c3specialist_v2.databinding.LayoutKycStatusBinding
import co.nayan.c3specialist_v2.profile.utils.KycManager
import co.nayan.c3specialist_v2.profile.utils.maskPanNumber
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KYCStatusDialogFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var kycManager: KycManager
    private lateinit var binding: LayoutKycStatusBinding
    lateinit var onKycTypeSelectionListener: OnKycTypeSelectionListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutKycStatusBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPanDetails()
        setupPhotoIdDetails()

        binding.panContainer.setOnClickListener { showDetailsFor(KycType.PAN) }
        binding.photoIdContainer.setOnClickListener { showDetailsFor(KycType.PHOTO_ID) }
    }

    private fun setupPanDetails() = lifecycleScope.launch {
        val panDetails = kycManager.getPanDetails()
        val status = panDetails.status
        binding.panTxt.text = if (status == KycStatus.NOT_UPLOADED)
            getString(R.string.add_pan_details)
        else String.format(getString(R.string.pan_text), panDetails.kycNumber?.maskPanNumber())
        context?.let {
            binding.panStatusIv.setImageDrawable(ContextCompat.getDrawable(it, panDetails.statusIconId))
            binding.panStatusTxt.text = getString(panDetails.statusTextId)
            binding.panStatusTxt.setTextColor(ContextCompat.getColor(it, panDetails.statusColorId))
        }
    }

    private fun setupPhotoIdDetails() = lifecycleScope.launch {
        val photoIdDetails = kycManager.getPhotoIdDetails()
        val status = photoIdDetails.status
        binding.photoIdTxt.text = if (status == KycStatus.NOT_UPLOADED)
            getString(R.string.add_photo_id_details)
        else String.format(getString(R.string.photo_id_text), photoIdDetails.kycNumber)
        context?.let {
            binding.photoIdStatusIv.setImageDrawable(
                ContextCompat.getDrawable(
                    it,
                    photoIdDetails.statusIconId
                )
            )
            binding.photoIdStatusTxt.text = getString(photoIdDetails.statusTextId)
            binding.photoIdStatusTxt.setTextColor(ContextCompat.getColor(it, photoIdDetails.statusColorId))
        }
    }

    private fun showDetailsFor(type: String) {
        if (this@KYCStatusDialogFragment::onKycTypeSelectionListener.isInitialized) {
            onKycTypeSelectionListener.onSelect(type)
            dismiss()
        }
    }

    companion object {
        fun newInstance(callback: OnKycTypeSelectionListener): KYCStatusDialogFragment {
            val kycStatusDialogFragment = KYCStatusDialogFragment()
            kycStatusDialogFragment.onKycTypeSelectionListener = callback
            return kycStatusDialogFragment
        }
    }
}

interface OnKycTypeSelectionListener {
    fun onSelect(type: String)
}
