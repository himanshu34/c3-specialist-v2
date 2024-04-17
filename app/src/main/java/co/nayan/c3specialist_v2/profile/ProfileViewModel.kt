package co.nayan.c3specialist_v2.profile

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.AppLanguage
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.profile.utils.ImagePickerListener
import co.nayan.c3specialist_v2.profile.utils.ImagePickerManager
import co.nayan.c3specialist_v2.profile.utils.ImagePickerProvider
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.BankDetails
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.c3_module.AuthenticationResponse
import co.nayan.c3v2.core.models.c3_module.requests.UpdateBankDetailsRequest
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePasswordRequest
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePersonalInfoRequest
import co.nayan.c3v2.core.models.c3_module.responses.UserResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.model.UserLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
    private val sharedStorage: SharedStorage,
    private val imagePickerProvider: ImagePickerProvider,
    private val deviceInfoHelperImpl: DeviceInfoHelperImpl
) : BaseViewModel() {

    private var imagePickerManager: ImagePickerManager? = null
    var cameraClicked: (() -> Unit)? = null
    var galleryClicked: (() -> Unit)? = null

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    private val _user: MutableLiveData<User?> = MutableLiveData(userRepository.getUserInfo())
    val user: LiveData<User?> = _user

    fun isSurveyor() = userRepository.getUserInfo()?.isSurveyor ?: false

    fun getAppLanguage() = sharedStorage.getAppLanguage() ?: AppLanguage.ENGLISH

    private fun getDeviceConfig() = deviceInfoHelperImpl.getDeviceConfig()
    fun getBuildVersion() = deviceInfoHelperImpl.getDeviceConfig()?.buildVersion

    fun uploadImage(
        compressedFile: File
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val fileName = compressedFile.path.substringAfterLast("/")
        val reqFile = compressedFile.asRequestBody("image/*".toMediaTypeOrNull())
        val imageBodyPart =
            MultipartBody.Part.createFormData("profile_photo", fileName, reqFile)
        val response = profileRepository.uploadImage(imageBodyPart)
        response.let {
            it.user?.let { user ->
                _user.postValue(user)
                userRepository.setUserInfo(user)
            }
        }
        _state.postValue(FetchDetailsSuccessState)
    }

    fun fetchUserDetails() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val response = profileRepository.fetchUserDetails()
        response.let { user ->
            _user.postValue(user)
            userRepository.setUserInfo(user)
        }
        _state.postValue(FetchDetailsSuccessState)
    }

    fun authenticateUserPassword(
        password: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val response = profileRepository.authenticatePassword(password)
        _state.postValue(AuthenticationSuccessState(response))
    }

    fun updatePersonalInfo(
        name: String,
        address: String,
        state: String,
        city: String,
        country: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val deviceConfig = getDeviceConfig()
        val response = profileRepository.updatePersonalInfo(
            UpdatePersonalInfoRequest(
                name = name,
                address = address,
                city = city,
                state = state,
                country = country,
                model = deviceConfig?.model,
                version = deviceConfig?.version,
                buildVersion = deviceConfig?.buildVersion,
                ram = deviceConfig?.ram
            )
        )
        val user = userRepository.getUserInfo()
        user!!.address = address
        user.city = city
        user.state = state
        user.country = country
        userRepository.setUserInfo(user)
        _state.postValue(UpdateInfoSuccessState(response))
    }

    fun updateBasePersonalInfo(
        name: String,
        address: String,
        state: String,
        city: String,
        country: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val deviceConfig = getDeviceConfig()
        profileRepository.updatePersonalInfo(
            UpdatePersonalInfoRequest(
                name = name,
                address = address,
                city = city,
                state = state,
                country = country,
                model = deviceConfig?.model,
                version = deviceConfig?.version,
                buildVersion = deviceConfig?.buildVersion,
                ram = deviceConfig?.ram
            )
        )
        val user = userRepository.getUserInfo()
        user!!.address = address
        user.city = city
        user.state = state
        user.country = country
        userRepository.setUserInfo(user)
        _state.postValue(FinishedState)
    }

    fun fetchBankDetails(
        token: String?
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val data = profileRepository.fetchBankDetails(token)
        _state.postValue(BankDetailsSuccessState(data))
    }

    fun updateBankDetails(
        beneficiaryName: String,
        accountNumber: String,
        ifscCode: String,
        bankName: String,
        token: String?
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val response = profileRepository.updateBankDetails(
            UpdateBankDetailsRequest(beneficiaryName, accountNumber, bankName, ifscCode, token)
        )
        _state.postValue(BankDetailsSuccessState(response))
    }

    fun getUserInfo() = userRepository.getUserInfo()

    suspend fun getKycStatus() = profileRepository.getKycStatus()

    fun resetPassword(
        currentPassword: String,
        newPassword: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val response = profileRepository.updatePassword(
            UpdatePasswordRequest(currentPassword, newPassword)
        )
        val success = response.isCorrectPassword != false
        _state.postValue(UpdatePasswordState(response.message, success))
    }

    fun initImagePickerManager(activityResultCaller: ActivityResultCaller) {
        imagePickerManager = imagePickerProvider.provide(activityResultCaller)
        imagePickerManager?.imagePickerListener = object : ImagePickerListener {
            override fun report(message: String) {
                _state.value = ImagePickerMessageState(message)
            }

            override fun setImage(uri: Uri?) {
                _state.value = ImagePickerSetImageState(uri)
            }

            override fun permissionsGranted() {
                imagePickerManager?.pickImage()
            }
        }
    }

    fun setImagePickerType(picker: Int) {
        imagePickerManager?.imagePickerType = picker
    }

    fun requestPermissions() {
        imagePickerManager?.requestPermissions()
    }

    fun updateKYCDetails(
        idType: String,
        number: String,
        isForPAN: Boolean
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val idTypePart = idType.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        val numberPart = number.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        val path = imagePickerManager?.imagePath
        if (path.isNullOrEmpty() || imagePickerManager?.isImageUpdated == false) {
            _state.postValue(UpdateImageErrorState)
        } else {
            val file = File(path)
            val fileName = createFileName(file, idType.replace(" ", "_"), number)
            val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val imageBodyPart = if (isForPAN)
                MultipartBody.Part.createFormData("pan_image", fileName, reqFile)
            else MultipartBody.Part.createFormData("photo_id_image", fileName, reqFile)
            if (isForPAN) updatePanDetails(idTypePart, numberPart, imageBodyPart)
            else updatePhotoIdDetails(idTypePart, numberPart, imageBodyPart)
        }
    }

    private fun createFileName(file: File, idType: String, number: String): String {
        return "${idType.lowercase(Locale.getDefault())}_${getUserInfo()?.id.toString()}_${number}.${file.extension}"
    }

    private fun updatePanDetails(
        idTypePart: RequestBody,
        numberPart: RequestBody,
        imageBodyPart: MultipartBody.Part
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val response = profileRepository.updatePanDetails(idTypePart, numberPart, imageBodyPart)
        _state.postValue(UpdateInfoSuccessState(response))
    }

    private fun updatePhotoIdDetails(
        idTypePart: RequestBody,
        numberPart: RequestBody,
        imageBodyPart: MultipartBody.Part
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val response = profileRepository.updatePhotoIdDetails(idTypePart, numberPart, imageBodyPart)
        _state.postValue(UpdateInfoSuccessState(response))
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun isLearningVideosEnabled(): Boolean {
        return userRepository.isLearningVideosEnabled()
    }

    fun updateLearningVideosStatus(status: Boolean) {
        userRepository.updateLearningVideosStatus(status)
    }

    fun getUserLocation(
        context: Context
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val userLocation = UserLocation()
        try {
            val lastLocation = userRepository.getLastLocation()
            val location = Location("")
            location.longitude = lastLocation.longitude
            location.latitude = lastLocation.latitude

            userLocation.latitude = location.latitude
            userLocation.longitude = location.longitude
            userLocation.speedMpS = location.speed
            userLocation.speedKpH = location.speed
            userLocation.altitude = location.altitude
            userLocation.time = location.time

            _state.postValue(ProgressState)
            val geoCoder = Geocoder(context, Locale.getDefault())
            val value = try {
                val addresses: List<Address>? = geoCoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
                // If any additional address line present than only,
                // check with max available address lines by getMaxAddressLineIndex()
                addresses?.let {
                    if (it.isNotEmpty()) {
                        userLocation.address = it[0].getAddressLine(0)
                        userLocation.postalCode = it[0].postalCode ?: ""
                        userLocation.city = it[0].locality ?: ""
                        userLocation.state = it[0].adminArea ?: ""
                        userLocation.country = it[0].countryName ?: ""
                        userLocation.countryCode = it[0].countryCode ?: ""
                        userLocation.validLocation = true
                    }
                }
                FetchUserLocationSuccessState(userLocation)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                FinishedState
            }
            _state.postValue(value)
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            _state.postValue(FinishedState)
        }
    }

    data class AuthenticationSuccessState(
        val authenticationResponse: AuthenticationResponse
    ) : ActivityState()

    data class UpdateInfoSuccessState(val response: UserResponse) : ActivityState()
    data class BankDetailsSuccessState(val bankDetails: BankDetails) : ActivityState()
    object FetchDetailsSuccessState : ActivityState()
    data class FetchUserLocationSuccessState(val userLocation: UserLocation) : ActivityState()
    data class UpdatePasswordState(val message: String?, val success: Boolean) : ActivityState()
    data class ImagePickerMessageState(val message: String) : ActivityState()
    data class ImagePickerSetImageState(val uri: Uri?) : ActivityState()
    object UpdateImageErrorState : ActivityState()
}