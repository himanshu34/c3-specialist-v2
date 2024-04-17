package co.nayan.c3specialist_v2.config

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.appsession.SessionActivity
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.utils.ViewUtils
import co.nayan.c3v2.core.api.client_error.ClientErrorManagerImpl
import co.nayan.c3v2.core.utils.Constants.Error.INTERNAL_ERROR_TAG
import co.nayan.c3v2.core.utils.Constants.Error.NOT_FOUND_TAG
import co.nayan.c3v2.core.utils.Constants.Error.UNAUTHORIZED_TAG
import co.nayan.c3v2.core.widgets.CustomAlertDialogFragment
import co.nayan.c3v2.core.widgets.CustomAlertDialogListener
import co.nayan.c3v2.core.widgets.ProgressDialogFragment
import co.nayan.c3v2.login.LoginActivity
import com.skydoves.balloon.showAlignBottom
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class BaseActivity : SessionActivity() {

    @Inject
    lateinit var viewUtils: ViewUtils

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var clientErrorManagerImpl: ClientErrorManagerImpl

    private val errorCodeReceiver = Observer<Intent?> { intent ->
        intent?.let {
            when (it.action) {
                NOT_FOUND_TAG -> {
                    showMessage(getString(R.string.page_not_found))
                }

                UNAUTHORIZED_TAG -> {
                    if (::userRepository.isInitialized && userRepository.isUserLoggedIn())
                        logoutUser(getString(R.string.unauthorized_error_message))
                }

                INTERNAL_ERROR_TAG -> {
                    showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
                }

                else -> {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clientErrorManagerImpl.subscribe().observe(this, errorCodeReceiver)
    }

    suspend fun unregisterFCMToken(): Boolean {
        showProgressDialog(getString(co.nayan.canvas.R.string.please_wait))
        val success = userRepository.unregisterFCMToken()
        hideProgressDialog()
        return success
    }

    fun logoutUser(errorMessage: String?) = lifecycleScope.launch {
        userRepository.userLoggedOut()
        moveToLoginScreen(errorMessage)
    }

    private fun moveToLoginScreen(errorMessage: String?) {
        Intent(this, LoginActivity::class.java).apply {
            putExtra(LoginActivity.VERSION_NAME, BuildConfig.VERSION_NAME)
            putExtra(LoginActivity.VERSION_CODE, BuildConfig.VERSION_CODE)
            putExtra(LoginActivity.ERROR_MESSAGE, errorMessage)
            startActivity(this)
        }
        finishAffinity()
    }

    fun popupErrorMessage(errorView: View?, message: String) {
        errorView?.requestFocus()
        errorView?.showAlignBottom(viewUtils.getErrorBalloon(message).build())
    }

    fun showProgressDialog(message: String, isCancelable: Boolean = false) {
        hideProgressDialog()
        val progressDialog = ProgressDialogFragment()
        progressDialog.setMessage(message)
        progressDialog.isCancelable = isCancelable
        progressDialog.show(supportFragmentManager.beginTransaction(), message)
    }

    fun hideProgressDialog() {
        supportFragmentManager.fragments.forEach {
            if (it is ProgressDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
    }

    private val customAlertDialogListener = object : CustomAlertDialogListener {
        override fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?) {
            alertDialogPositiveClick(shouldFinish, tag)
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?) {
            alertDialogNegativeClick(shouldFinish, tag)
        }
    }

    protected fun showAlert(
        message: String,
        shouldFinish: Boolean,
        tag: String,
        title: String? = null,
        positiveText: String? = null,
        negativeText: String? = null,
        showPositiveBtn: Boolean = false,
        showNegativeBtn: Boolean = false,
        isCancelable: Boolean = true
    ) {
        supportFragmentManager.fragments.forEach {
            if (it is CustomAlertDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }

        val customAlertDialogFragment =
            CustomAlertDialogFragment.newInstance(customAlertDialogListener).apply {
                setTitle(title)
                setMessage(message)
                showNegativeBtn(showNegativeBtn)
                showPositiveBtn(showPositiveBtn)
                shouldFinish(shouldFinish)
                if (positiveText != null)
                    setPositiveBtnText(positiveText)
                if (negativeText != null)
                    setNegativeBtnText(negativeText)
            }
        customAlertDialogFragment.isCancelable = isCancelable
        customAlertDialogFragment.show(supportFragmentManager.beginTransaction(), tag)
    }

    open fun alertDialogPositiveClick(shouldFinishActivity: Boolean, tag: String?) = Unit
    open fun alertDialogNegativeClick(shouldFinishActivity: Boolean, tag: String?) = Unit
    abstract fun showMessage(message: String)
}