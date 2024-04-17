package co.nayan.review.incorrectreviews

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.fragment.app.DialogFragment
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.utils.answer
import co.nayan.review.R
import co.nayan.review.databinding.FragmentClassifyVideoBinding
import co.nayan.review.utils.isVideo
import timber.log.Timber

class ClassificationVideoFragment(
    private val record: Record,
    private val appFlavor: String?
) : DialogFragment() {

    private lateinit var binding: FragmentClassifyVideoBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentClassifyVideoBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.junkRecordIv.gone()
        setupRecord()
        setupSniffingView()
    }

    private fun setupRecord() {
        val recordId = if (record.isSniffingRecord == true)
            record.randomSniffingId else record.id
        binding.recordIdTxt.text =
            String.format(requireContext().getString(R.string.record_id_text), recordId)
        binding.videoView.invisible()
        val url = if (record.mediaType == MediaType.VIDEO)
            record.mediaUrl else record.displayImage
        url?.let { mediaUrl ->
            if (mediaUrl.isVideo()) loadVideo(mediaUrl)
        }
    }

    private fun loadVideo(mediaUrl: String) {
        binding.videoView.visible()
        // Play Video in VideoView
        val mediaController = MediaController(requireContext())
        binding.videoView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setVideoURI(Uri.parse(mediaUrl))
            setOnPreparedListener {
                val mediaProportion: Float = it.videoHeight.toFloat() / it.videoWidth.toFloat()
                binding.videoView.layoutParams = binding.videoView.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = (binding.videoView.width.toFloat() * mediaProportion).toInt()
                    Timber.e("VideoView -> [$width, $height]")
                }
                it.start()
                it.isLooping = true
                it.setOnVideoSizeChangedListener { _, _, _ ->
                    this.setMediaController(mediaController)
                    mediaController.setAnchorView(this)
                }
            }
        }

        setUpAnswer(record.answer())
    }

    private fun setUpAnswer(answer: String?) {
        if (answer.isNullOrEmpty()) {
            binding.junkRecordIv.gone()
            binding.answerTxt.gone()
        } else {
            if (answer == Judgment.JUNK) {
                binding.junkRecordIv.visible()
                binding.answerTxt.gone()
            } else {
                binding.junkRecordIv.gone()
                binding.answerTxt.visible()
                binding.answerTxt.text = answer
            }
        }
    }

    private fun setupSniffingView() {
        if ((appFlavor == "qa" || appFlavor == "dev") && record.isSniffingRecord == true) {
            if (record.needsRejection == true) {
                binding.needsRejectionView.visible()
                binding.needsNoRejectionView.gone()
            } else {
                binding.needsRejectionView.gone()
                binding.needsNoRejectionView.visible()
            }
        } else {
            binding.needsNoRejectionView.gone()
            binding.needsRejectionView.gone()
        }
    }
}