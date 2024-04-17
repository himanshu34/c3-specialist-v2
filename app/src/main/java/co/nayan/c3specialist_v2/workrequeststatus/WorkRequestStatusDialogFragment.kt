package co.nayan.c3specialist_v2.workrequeststatus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutWorkRequestStatusDialogBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WorkRequestStatusDialogFragment : DialogFragment() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val workRequestStatusViewModel: WorkRequestStatusViewModel by viewModels()
    private lateinit var binding: LayoutWorkRequestStatusDialogBinding
    private var workRequestStatusDialogListener: WorkRequestStatusDialogListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
        arguments?.let {
            if (it.containsKey(WORK_REQUEST_ID))
                workRequestStatusViewModel.setWorkRequestId(it.getInt(WORK_REQUEST_ID))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutWorkRequestStatusDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        workRequestStatusViewModel.state.observe(viewLifecycleOwner, stateObserver)
        workRequestStatusViewModel.startStatusCheck()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            is WorkRequestStatusViewModel.WorkRequestProgressState -> {
                val progress = it.progress
                if (progress == 10) {
                    binding.progressBar.isIndeterminate = false
                    binding.progressPercentageTxt.visible()
                }
                binding.progressBar.progress = progress
                binding.progressPercentageTxt.text = "%d'%%'".format(progress)
                Timber.e("$progress")
            }

            is WorkRequestStatusViewModel.WorkRequestSuccessState -> {
                workRequestStatusDialogListener?.succeeded(
                    it.workAssignment,
                    arguments?.getString(ROLE)
                )
                dismiss()
            }

            WorkRequestStatusViewModel.WorkRequestFailedState -> {
                workRequestStatusDialogListener?.failed(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
                dismiss()
            }

            WorkRequestStatusViewModel.WorkRequestNoWorkState -> {
                workRequestStatusDialogListener?.noWork(arguments?.getString(ROLE))
                dismiss()
            }

            is ErrorState -> {
                workRequestStatusDialogListener?.failed(errorUtils.parseExceptionMessage(it.exception))
                dismiss()
            }
        }
    }

    companion object {
        private const val WORK_REQUEST_ID = "workRequestId"
        private const val ROLE = "role"

        fun newInstance(
            callback: WorkRequestStatusDialogListener,
            workRequestId: Int,
            role: String?
        ): WorkRequestStatusDialogFragment {
            val fragment = WorkRequestStatusDialogFragment()
            fragment.workRequestStatusDialogListener = callback
            val args = Bundle()
            args.apply {
                putInt(WORK_REQUEST_ID, workRequestId)
                putString(ROLE, role)
            }
            fragment.arguments = args
            return fragment
        }
    }
}

interface WorkRequestStatusDialogListener {
    fun succeeded(workAssignment: WorkAssignment?, role: String?)
    fun failed(errorMessage: String)
    fun noWork(role: String?)
}