package co.nayan.c3v2.core.api.client_error

import android.content.Intent
import androidx.lifecycle.MutableLiveData

interface IClientErrorHelper {

    fun success(intent: Intent)

    fun catchError(intent: Intent)

    fun subscribe(): MutableLiveData<Intent?>
}