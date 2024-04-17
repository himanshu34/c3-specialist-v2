package co.nayan.c3specialist_v2.profile.widgets

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutImageChooserBinding
import co.nayan.c3specialist_v2.profile.utils.PICK_IMAGE_DEVICE
import co.nayan.c3specialist_v2.profile.utils.PICK_IMAGE_FROM_CAMERA
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImageChooserDialogFragment : BottomSheetDialogFragment() {

    lateinit var imageChooserSelectListener: OnImageChooserSelectListener
    private lateinit var binding: LayoutImageChooserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutImageChooserBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupIcons()
        binding.cameraContainer.setOnClickListener { onItemClick(PICK_IMAGE_FROM_CAMERA) }
        binding.galleryContainer.setOnClickListener { onItemClick(PICK_IMAGE_DEVICE) }
    }

    private fun setupIcons() {
        getAppIcon("Camera")?.let { icon ->
            binding.cameraIv.setImageDrawable(icon)
        }

        val galleryIcon = getAppIcon("Gallery")
        if (galleryIcon == null) {
            getAppIcon("Photos")?.let {
                binding.galleryIv.setImageDrawable(it)
            }
        } else binding.galleryIv.setImageDrawable(galleryIcon)
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun getAppIcon(appName: String): Drawable? {
        context?.let { context ->
            val packageManager = context.packageManager
            return packageManager?.getInstalledApplications(PackageManager.GET_META_DATA)
                ?.find { packageManager.getApplicationLabel(it) == appName }
                ?.loadIcon(packageManager)
        }
        return null
    }

    private fun onItemClick(picker: Int) {
        if (this@ImageChooserDialogFragment::imageChooserSelectListener.isInitialized) {
            imageChooserSelectListener.onSelect(picker)
            dismiss()
        }
    }

    companion object {
        fun newInstance(callback: OnImageChooserSelectListener): ImageChooserDialogFragment {
            val imageChooserDialogFragment = ImageChooserDialogFragment()
            imageChooserDialogFragment.imageChooserSelectListener = callback
            return imageChooserDialogFragment
        }
    }
}

interface OnImageChooserSelectListener {
    fun onSelect(picker: Int)
}
