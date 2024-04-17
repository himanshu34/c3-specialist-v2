package co.nayan.appsession

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.*
import co.nayan.c3v2.core.fromPrettyJsonList
import co.nayan.c3v2.core.toPrettyJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SessionManager(
    private val sharedPreferences: SharedPreferences,
    private val lifecycleOwner: LifecycleOwner?,
    private val serviceLifecycleOwner: LifecycleOwner? = null,
    private val onSessionTimeoutListener: OnSessionTimeoutListener?,
    private val sessionRepository: SessionRepositoryInterface
) {

    var shouldCheckUserInteraction = true
    private var hasUserInteracted = true
    private var activityName: String? = null
    private var wfStepId: Int? = null
    private var workType: String? = null
    private var workAssignmentId: Int? = null
    private var role: String? = null
    private var preferenceEditor: SharedPreferences.Editor = sharedPreferences.edit()

    init {
        lifecycleOwner?.lifecycle?.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
            fun setMetaData() {
                activityName = lifecycleOwner.javaClass.simpleName
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun startSession() {
                Timber.d("Starting Session for activity: ${lifecycleOwner.javaClass.name}")
                handler.post(runnable)
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun pauseSession() {
                Timber.d("Pausing Session for activity: ${lifecycleOwner.javaClass.name}")
                handler.removeCallbacks(runnable)
            }
        })

        serviceLifecycleOwner?.lifecycle?.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
            fun startSession() {
                activityName = serviceLifecycleOwner.javaClass.simpleName
                Timber.d("Starting Session for activity: ${serviceLifecycleOwner.javaClass.name}")
                handler.post(runnable)
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun pauseSession() {
                Timber.d("Pausing Session for activity: ${serviceLifecycleOwner.javaClass.name}")
                handler.removeCallbacks(runnable)
            }
        })
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runnable: Runnable = object : Runnable {
        override fun run() {
            handler.postDelayed(this, Session.HEARTBEAT_DURATION)
            heartBeat()
        }
    }

    fun onUserInteraction() {
        hasUserInteracted = true
    }

    fun resumeSession() {
        hasUserInteracted = true

        handler.removeCallbacks(runnable)
        handler.post(runnable)
    }

    private fun heartBeat() {
        if (hasUserInteracted) {
            val heartBeats = getRemainingHeartBeats()
            val currentSession = heartBeats.getLastSession()
            lifecycleScope {
                if (currentSession != null && currentSession.isActive()) {
                    if (currentSession.isForSameActivity(activityName)) heartBeats.updateCurrentSession()
                    else heartBeats.updateLastAndCreateNewSession()
                } else {
                    heartBeats.createNewSession()
                    syncSessions()
                }
            }
        } else {
            handler.removeCallbacks(runnable)
            onSessionTimeoutListener?.onTimeout()
        }

        hasUserInteracted = false || shouldCheckUserInteraction.not()
    }

    private suspend fun MutableList<Session>.updateLastAndCreateNewSession() {
        updateCurrentSession()
        createNewSession()
        syncSessions()
    }

    private fun MutableList<Session>.getLastSession(): Session? {
        return if (isNotEmpty()) last()
        else null
    }

    private suspend fun MutableList<Session>.updateCurrentSession() {
        val currentSession = removeAt(size - 1)
        add(currentSession.update())
        persistUnsyncedSessions()
        Timber.d("$this")
    }

    private suspend fun MutableList<Session>.createNewSession() {
        val newSession = Session.newSession(
            activityName,
            wfStepId,
            workAssignmentId,
            workType,
            getPhoneNumber(),
            role
        )
        add(newSession)
        persistUnsyncedSessions()
        Timber.d("$this")
    }

    suspend fun syncSessions(syncAll: Boolean = false): Boolean {
        val toSync = getHeartBeatsToUpload(syncAll)
        clearSessions(toSync)
        return if (toSync.isNotEmpty()) {
            val success = sessionRepository.submitSessions(toSync.convertSessionsToListOfString())
            if (!success) addAllSessions(toSync)
            success
        } else true
    }

    private fun getHeartBeatsToUpload(syncAll: Boolean): MutableList<Session> {
        val heartBeats = getRemainingHeartBeats()
        val lastSession = heartBeats.getLastSession()
        if (lastSession != null) {
            if (lastSession.isNewer() && !syncAll)
                heartBeats.removeAt(heartBeats.size - 1)
            return heartBeats
        }
        return mutableListOf()
    }

    private fun getRemainingHeartBeats(): MutableList<Session> {
        val stringValue = sharedPreferences.getString(UNSYNCED_HEARTBEATS, "[]")
        return stringValue?.fromPrettyJsonList() ?: run { mutableListOf() }
    }

    private suspend fun MutableList<Session>.persistUnsyncedSessions() =
        withContext(Dispatchers.IO) {
            preferenceEditor.putString(
                UNSYNCED_HEARTBEATS,
                this@persistUnsyncedSessions.toPrettyJson()
            ).apply()
        }

    private suspend fun addAllSessions(sessions: List<Session>) = withContext(Dispatchers.IO) {
        val sessionBeats = mutableListOf<Session>()
        sessionBeats.addAll(sessions)
        sessionBeats.addAll(getRemainingHeartBeats())
        preferenceEditor.putString(UNSYNCED_HEARTBEATS, sessionBeats.toPrettyJson()).apply()
    }

    private suspend fun clearSessions(toDelete: List<Session>) = withContext(Dispatchers.IO) {
        val remainingBeats = getRemainingHeartBeats()
        remainingBeats.removeAll(toDelete)
        preferenceEditor.putString(UNSYNCED_HEARTBEATS, remainingBeats.toPrettyJson()).apply()
    }

    fun setMetaData(workAssignmentId: Int?, wfStepId: Int?, workType: String?, role: String?) {
        this.workAssignmentId = workAssignmentId
        this.wfStepId = wfStepId
        this.workType = workType
        this.role = role
    }

    private fun MutableList<Session>.convertSessionsToListOfString(): List<List<Any>> =
        map { it.toValueArray() }

    private fun getPhoneNumber() = sessionRepository.userPhoneNumber()

    private fun lifecycleScope(action: suspend () -> Unit) {
        val owner = lifecycleOwner ?: serviceLifecycleOwner
        owner?.lifecycleScope?.launch {
            action()
        }
    }

    companion object {
        const val SHARED_PREFS_NAME = "SessionManagerPrefs"
        const val UNSYNCED_HEARTBEATS = "unsynced sessions"
    }
}

interface OnSessionTimeoutListener {
    fun onTimeout()
}