package co.nayan.c3specialist_v2.config

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineExceptionHandler

abstract class BaseViewModel : ViewModel() {

    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwException(throwable as Exception)
    }

    abstract fun throwException(e: Exception)
}