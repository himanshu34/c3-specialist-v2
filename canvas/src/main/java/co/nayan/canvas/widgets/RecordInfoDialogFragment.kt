package co.nayan.canvas.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.canvas.R
import co.nayan.canvas.databinding.LayoutRecordInfoDialogBinding

class RecordInfoDialogFragment : DialogFragment() {

    private lateinit var binding: LayoutRecordInfoDialogBinding
    private var record: Record? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutRecordInfoDialogBinding.inflate(inflater, container, false).apply {
            binding = this
            record = arguments?.parcelable("record")
            binding.recordData = record
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeBtn.setOnClickListener { dismiss() }
    }

    companion object {
        fun newInstance(record: Record): RecordInfoDialogFragment {
            return RecordInfoDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("record", record)
                }
            }
        }
    }
}