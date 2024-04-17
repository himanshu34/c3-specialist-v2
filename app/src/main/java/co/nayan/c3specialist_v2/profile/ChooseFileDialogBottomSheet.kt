package co.nayan.c3specialist_v2.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.FragmentChooseFileBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ChooseFileDialogBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentChooseFileBinding
    private val viewModel: ProfileViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentChooseFileBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textViewCamera.setOnClickListener {
            viewModel.cameraClicked?.invoke()
            dismiss()
        }

        binding.textViewGallery.setOnClickListener {
            viewModel.galleryClicked?.invoke()
            dismiss()
        }

        binding.textViewCancel.setOnClickListener { dismiss() }
    }
}