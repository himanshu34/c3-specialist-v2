package co.nayan.canvas.widgets

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.canvas.R
import co.nayan.canvas.databinding.InterpolationLabelSelectionBinding

class SelectLabelDialogFragment(
    private val originalBitmap: Bitmap,
    private val annotationDataList: MutableList<AnnotationData>,
    private val callback: ((AnnotationData) -> Unit)? = null
) : DialogFragment() {

    private lateinit var binding: InterpolationLabelSelectionBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return InterpolationLabelSelectionBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ClassifyDialogTheme)
        isCancelable = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.labelsView.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = InterpolationLabelAdapter(originalBitmap, annotationDataList) {
                callback?.invoke(it)
                dismiss()
            }
        }
    }
}