package co.nayan.appsession

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.nayan.appsession.SessionManager.Companion.SHARED_PREFS_NAME
import co.nayan.c3v2.core.utils.LocaleHelper
import javax.inject.Inject

abstract class SessionActivity : AppCompatActivity() {

    @Inject
    lateinit var sessionRepositoryInterface: SessionRepositoryInterface
    private lateinit var sessionManager: SessionManager

    private val onSessionTimeoutListener = object : OnSessionTimeoutListener {
        override fun onTimeout() {
            if (isDestroyed.not()) {
                removeSessionDialog()
                val sessionDialog = SessionDialogFragment.newInstance(dialogInteractionListener)
                supportFragmentManager.beginTransaction()
                    .add(sessionDialog, "Session Alert")
                    .commitAllowingStateLoss()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        sessionManager = SessionManager(
            sharedPrefs,
            this,
            null,
            onSessionTimeoutListener,
            sessionRepositoryInterface
        )
    }

    override fun onDestroy() {
        removeSessionDialog()
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    private val dialogInteractionListener = object : DialogInteractionListener {
        override fun onResume() {
            if (isSessionManagerInitialized()) sessionManager.resumeSession()
        }
    }

    private fun removeSessionDialog() {
        supportFragmentManager.fragments.forEach {
            if (it is SessionDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        sessionManager.onUserInteraction()
    }

    fun setMetaData(
        workAssignmentId: Int? = null,
        wfStepId: Int? = null,
        workType: String? = null,
        role: String? = null
    ) {
        sessionManager.setMetaData(workAssignmentId, wfStepId, workType, role)
    }

    suspend fun uploadUnsyncedSessions() = sessionManager.syncSessions(true)

    private fun isSessionManagerInitialized() = this@SessionActivity::sessionManager.isInitialized
}