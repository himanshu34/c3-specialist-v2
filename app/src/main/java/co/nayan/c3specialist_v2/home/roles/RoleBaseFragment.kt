package co.nayan.c3specialist_v2.home.roles

import android.content.Intent
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.faq.FaqCallbackInput
import co.nayan.c3specialist_v2.faq.FaqResultCallback
import co.nayan.c3specialist_v2.home.widgets.FileDownloadDialogFragment
import co.nayan.c3specialist_v2.home.widgets.FileDownloadStatusDialogListener
import co.nayan.c3specialist_v2.workrequeststatus.WorkRequestStatusDialogFragment
import co.nayan.c3specialist_v2.workrequeststatus.WorkRequestStatusDialogListener
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.widgets.CustomAlertDialogFragment
import co.nayan.c3v2.core.widgets.CustomAlertDialogListener
import co.nayan.canvas.CanvasActivity

abstract class RoleBaseFragment(layoutID: Int) : BaseFragment(layoutID) {

    private val fileDownloadStatusDialogListener = object : FileDownloadStatusDialogListener {
        override fun succeeded(workAssignment: WorkAssignment) {
            saveDownloadDetailsFor(workAssignment.wfStep?.cameraAiModel)
            setupWork(workAssignment)
        }

        override fun failed() {
            showMessage(getString(R.string.downloading_failed))
        }
    }

    protected fun showFileDownloadDialog(workAssignment: WorkAssignment) {
        FileDownloadDialogFragment.newInstance(fileDownloadStatusDialogListener, workAssignment)
            .show(childFragmentManager, getString(R.string.downloading_ai_engine))
    }

    private val workRequestStatusDialogListener = object : WorkRequestStatusDialogListener {
        override fun succeeded(workAssignment: WorkAssignment?, role: String?) {
            if (workAssignment == null) showMessage(getString(R.string.no_pending_work))
            else {
                role?.let { setCanvasRole(it) }
                setupWork(workAssignment)
            }
        }

        override fun failed(errorMessage: String) {
            showMessage(errorMessage)
        }

        override fun noWork(role: String?) {
            role?.let {
                if (it == Role.MANAGER) lookForSpecialistWork(getString(R.string.no_pending_work))
                else showMessage(getString(R.string.no_pending_work))
            } ?: run { showMessage(getString(R.string.no_pending_work)) }
        }
    }

    protected fun showWorkRequestingStatusDialog(workRequestId: Int, role: String?) {
        WorkRequestStatusDialogFragment.newInstance(
            workRequestStatusDialogListener,
            workRequestId,
            role
        ).show(childFragmentManager, getString(R.string.requesting_work))
    }

    protected fun moveToCanvasScreen(workAssignment: WorkAssignment?, user: User?) {
        Intent(activity, CanvasActivity::class.java).apply {
            putExtra(CanvasActivity.WORK_ASSIGNMENT, workAssignment)
            putExtra(CanvasActivity.APP_FLAVOR, BuildConfig.FLAVOR)
            putExtra(CanvasActivity.USER, user)
            startActivity(this)
        }
    }

    private val faqResultCallback =
        registerForActivityResult(FaqResultCallback()) { workAssignment ->
            if (workAssignment != null) {
                workAssignment.faqRequired = false
                setupWork(workAssignment)
            }
        }

    protected fun moveToIllustrationScreen(workAssignment: WorkAssignment, userRole: String) {
        faqResultCallback.launch(
            FaqCallbackInput(
                workAssignment = workAssignment,
                userRole = userRole,
                isIllustration = true
            )
        )
    }

    protected fun showEarningAlert(workAssignment: WorkAssignment) {
        val title = getString(R.string.potential_alert_title)
        val positiveBtnText = getString(R.string.lets_start)
        val negativeBtnText = getString(R.string.cancel)
        val message =
            getString(R.string.potential_earning_message).format(workAssignment.potentialPoints)
        CustomAlertDialogFragment().apply {
            setTitle(title)
            setMessage(message)
            showPositiveBtn(true)
            setPositiveBtnText(positiveBtnText)
            showNegativeBtn(true)
            setNegativeBtnText(negativeBtnText)
            isCancelable = false
            customAlertDialogListener = object : CustomAlertDialogListener {
                override fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?) {
                    workAssignment.isEarningShown = true
                    setupWork(workAssignment)
                }

                override fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?) {
                    //nothing to do
                }
            }
        }.show(childFragmentManager.beginTransaction(), "Potential Earning")
    }

    open fun saveDownloadDetailsFor(cameraAiModel: CameraAIModel?) = Unit
    open fun saveLearningVideoCompletedFor(applicationModeName: String?) = Unit
    open fun moveToWorkScreen(workAssignment: WorkAssignment) = Unit
    open fun setCanvasRole(role: String) = Unit
    open fun lookForSpecialistWork(message: String) = Unit

    abstract fun showMessage(message: String)
    abstract fun setupWork(workAssignment: WorkAssignment)
    abstract fun enableUI()
    abstract fun disableUI()
}