package co.nayan.c3specialist_v2.home.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutFileDownloaderDialogBinding
import co.nayan.c3specialist_v2.utils.FileDownloadWorker
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.visible
import com.nayan.nayancamv2.util.getMD5

class FileDownloadDialogFragment : DialogFragment() {

    private var fileDownloadStatusDialogListener: FileDownloadStatusDialogListener? = null
    private var workAssignment: WorkAssignment? = null
    private var fileLength: Long? = null
    private lateinit var binding: LayoutFileDownloaderDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
        arguments?.parcelable<WorkAssignment>(WORK_ASSIGNMENT)?.let { data ->
            workAssignment = data
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutFileDownloaderDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        workAssignment?.let { downloadAIModels(it) }
    }

    private fun downloadAIModels(workAssignment: WorkAssignment) {
        val id = "AI Engine Downloader"
        val cameraAIModel = workAssignment.wfStep?.cameraAiModel
        val fileName = "${cameraAIModel?.name?.replace(" ", "_")}"
        val file = requireContext().getFileStreamPath("$fileName.tflite")
        if (file.exists() && cameraAIModel?.checksum == file.getMD5()) {
            fileDownloadStatusDialogListener?.succeeded(workAssignment)
            return
        }

        val data = workDataOf(
            FileDownloadWorker.FILE_URL to cameraAIModel?.link,
            FileDownloadWorker.FILE_EXTENSION to ".tflite",
            FileDownloadWorker.FILE_NAME to fileName
        )

        val downloadWorkManager = OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setInputData(data)
            .addTag(id)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(downloadWorkManager)
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(downloadWorkManager.id)
            .observe(this) {
                it?.let { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            fileDownloadStatusDialogListener?.succeeded(workAssignment)
                            dialog?.dismiss()
                        }

                        WorkInfo.State.FAILED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {
                            fileDownloadStatusDialogListener?.failed()
                            dialog?.dismiss()
                        }

                        else -> {
                            when (workInfo.progress.getString(FileDownloadWorker.WORK_TYPE)) {
                                FileDownloadWorker.WORK_IN_PROGRESS -> {
                                    val progress = workInfo.progress.getInt(
                                        FileDownloadWorker.WORK_PROGRESS_VALUE,
                                        0
                                    )
                                    if (progress > 0 && binding.progressBar.isIndeterminate) {
                                        binding.progressBar.isIndeterminate = false
                                        binding.progressStatusContainer.visible()
                                    }
                                    updateDownloadingStatus(progress)
                                }

                                FileDownloadWorker.WORK_START -> {
                                    fileLength =
                                        workInfo.progress.getLong(
                                            FileDownloadWorker.WORK_LENGTH,
                                            0L
                                        )
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun updateDownloadingStatus(progress: Int) {
        if (fileLength == null || fileLength == 0L || progress == 0) return
        binding.progressBar.progress = progress
        fileLength?.let { contentLength ->
            val fileSizeInMB = contentLength.toFloat() / (1024 * 1024)
            val downloadSize = progress * fileSizeInMB / 100
            val sizeText = "%.2f/%.2f MB".format(downloadSize, fileSizeInMB)
            binding.downloadSizeTxt.text = sizeText
        }
        binding.downloadPercentageTxt.text = "%d%%".format(progress)
    }

    companion object {
        private const val WORK_ASSIGNMENT = "workAssignment"

        fun newInstance(
            callback: FileDownloadStatusDialogListener,
            workAssignment: WorkAssignment
        ): FileDownloadDialogFragment {
            val fragment = FileDownloadDialogFragment()
            fragment.fileDownloadStatusDialogListener = callback
            val args = Bundle()
            args.apply { putParcelable(WORK_ASSIGNMENT, workAssignment) }
            fragment.arguments = args
            return fragment
        }
    }
}

interface FileDownloadStatusDialogListener {
    fun succeeded(workAssignment: WorkAssignment)
    fun failed()
}