package co.nayan.c3v2.core.models

import android.location.Location
import com.google.firebase.FirebaseException

open class ActivityState

object InitialState : ActivityState()
object ProgressState : ActivityState()
object FailureState : ActivityState()
object FinishedState : ActivityState()
object NoVideosState : ActivityState()
object GooglePlayClientConnectedState : ActivityState()
object GooglePlayClientDisconnectedState : ActivityState()
object AnimatingState : ActivityState()
data class OrientationState(
    val orientationState: Int,
    val orientation: Int
) : ActivityState()
data class LocationSuccessState(
    val location: Location?
) : ActivityState()
data class LocationFailureState(
    val errorMessage: String,
    val exception: Exception?
) : ActivityState()

// Events Used by OTP Manager
object EventSendSuccessState : ActivityState()
data class EventSendFailedState(val exception: FirebaseException) : ActivityState()
object EventValidationSuccessState : ActivityState()
object EventValidationFailedState : ActivityState()
data class ErrorState(val exception: Exception) : ActivityState()
data class ErrorMessageState(
    val errorTitle: String? = "",
    val errorMessage: String? = ""
) : ActivityState()