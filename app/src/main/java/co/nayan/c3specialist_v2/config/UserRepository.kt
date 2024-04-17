package co.nayan.c3specialist_v2.config

import co.nayan.c3specialist_v2.storage.FileManager
import co.nayan.c3specialist_v2.storage.LearningVideoSharedStorage
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.c3_module.requests.TokenRequest
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val sharedStorage: SharedStorage,
    private val mPreferenceHelper: PreferenceHelper,
    private val apiClientFactory: ApiClientFactory,
    private val learningVideoSharedStorage: LearningVideoSharedStorage,
    private val deviceInfoHelperImpl: DeviceInfoHelperImpl,
    private val fileManager: FileManager
) {
    fun setUserInfo(user: User) {
        sharedStorage.setUserProfileInfo(user)
        sharedStorage.setUserLoggedInStatus(true)
    }

    fun getUserInfo() = sharedStorage.getUserProfileInfo()

    fun isSurveyor() = getUserInfo()?.isSurveyor ?: false

    fun isUserLoggedIn() = sharedStorage.isUserLoggedIn()

    suspend fun userLoggedOut() {
        fileManager.deleteCameraAIModels()
        sharedStorage.clearPreferences()
        mPreferenceHelper.clearPreferences()
        learningVideoSharedStorage.clearPreferences()
    }

    fun getUserRoles() = getUserInfo()?.activeRoles ?: listOf()

    fun getAppLanguage() = sharedStorage.getAppLanguage()

    fun isOnBoardingDone() = sharedStorage.isOnBoardingDone()

    fun setOnBoardingDone() {
        sharedStorage.setOnBoardingDone()
    }

    fun getUID() = getUserInfo()?.email ?: ""

    fun isPhoneVerified() = getUserInfo()?.isPhoneVerified ?: false

    suspend fun registerFCMToken(token: String) {
        val deviceConfig = deviceInfoHelperImpl.getDeviceConfig()
        apiClientFactory.apiClientBase.registerFCMToken(
            TokenRequest(
                token,
                deviceConfig?.model,
                deviceConfig?.version,
                deviceConfig?.buildVersion,
                deviceConfig?.ram
            )
        )
        sharedStorage.saveFCMToken(token)
    }

    suspend fun unregisterFCMToken(): Boolean {
        return try {
            apiClientFactory.apiClientBase.unregisterFCMToken()
            sharedStorage.saveFCMToken(null)
            true
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            false
        }
    }

    fun saveAppLearningVideoCompletedFor() {
        learningVideoSharedStorage.saveAppLearningVideoCompletedFor(
            learningVideoSharedStorage.appLearningVideoCompletedFor(),
            getUID()
        )
    }

    fun isApplicationTutorialDone() =
        learningVideoSharedStorage.appLearningVideoCompletedFor().contains(getUID())

    fun setDriverLearningVideoCompleted() {
        learningVideoSharedStorage.saveDriverLearningVideoCompletedFor(
            learningVideoSharedStorage.driverLearningVideoCompletedFor(),
            getUID()
        )
    }

    fun isDriverTutorialDone() =
        learningVideoSharedStorage.driverLearningVideoCompletedFor().contains(getUID())

    fun isLearningVideosEnabled() = learningVideoSharedStorage.isLearningVideosEnabled()

    fun updateLearningVideosStatus(status: Boolean) {
        learningVideoSharedStorage.updateLearningVideosStatus(status)
    }

    fun getLastLocation() = sharedStorage.getLastLocation()

    fun isUserAllowed() = sharedStorage.isUserAllowedToUseApplication()

    fun removeAllRolesExceptDriver() {
        sharedStorage.removeAllRolesExceptDriver()
    }
}