package co.nayan.c3v2.core.api.client_error

import android.content.Intent
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client error class
 */
@Singleton
class ClientErrorManagerImpl @Inject constructor() : IClientErrorHelper {

    private val _errorIntent: MutableLiveData<Intent?> = MutableLiveData(null)
    override fun success(intent: Intent) {
        _errorIntent.postValue(intent)
    }

    override fun catchError(intent: Intent) {
        _errorIntent.postValue(intent)
    }

    override fun subscribe() = _errorIntent
}