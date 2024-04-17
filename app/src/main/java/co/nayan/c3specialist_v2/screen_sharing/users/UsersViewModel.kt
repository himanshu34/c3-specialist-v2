package co.nayan.c3specialist_v2.screen_sharing.users

import androidx.activity.result.ActivityResultCaller
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.screen_sharing.PermissionManagerProvider
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingPermissionsListener
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingPermissionsManager
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.UserListItem
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val usersRepository: UsersRepository,
    private val userRepository: UserRepository,
    private val permissionManagerProvider: PermissionManagerProvider
) : BaseViewModel() {

    private var meetingPermissionsManager: MeetingPermissionsManager? = null
    private val _permissionState: MutableLiveData<Boolean> = MutableLiveData()
    val permissionState: LiveData<Boolean> = _permissionState

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    var selectedUser: UserListItem? = null

    var page: Int = 1
    private var shouldLoadMore = true

    fun fetchFirstPage() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            page = 1
            val response = fetchUsers()
            val users = response.users?.filter { it.id != userRepository.getUserInfo()?.id }
                ?: emptyList()
            setupPagination(response.currentPage ?: 0, response.totalPages ?: 0)
            _state.value = FetchUsersSuccessState(users, shouldLoadMore)
        }
    }

    fun fetchNextPage() {
        if (shouldLoadMore) {
            viewModelScope.launch(exceptionHandler) {
                val response = fetchUsers()
                val users = response.users?.filter { it.id != userRepository.getUserInfo()?.id }
                    ?: emptyList()
                setupPagination(response.currentPage ?: 0, response.totalPages ?: 0)
                _state.value = SetUpNextPageUsersState(users, shouldLoadMore)
            }
        }
    }

    private suspend fun fetchUsers() =
        usersRepository.fetchUsers(page = page, perPage = USERS_PER_PAGE)

    private fun setupPagination(currentPage: Int, totalPages: Int) {
        page = currentPage + 1
        shouldLoadMore = currentPage < totalPages
    }

    fun inviteUser() {
        if (selectedUser == null) {
            return
        }
        viewModelScope.launch(exceptionHandler) {
            _state.value = NotificationProgressState
            val user = userRepository.getUserInfo()
            val message = "${user?.name} is calling you..."
            usersRepository.inviteUser(selectedUser?.id, message, user?.name)
            _state.value = InviteUserSuccessState
        }
    }

    fun initMeetingPermissionsManager(activityResultCaller: ActivityResultCaller) {
        meetingPermissionsManager = permissionManagerProvider.provide(activityResultCaller)
        meetingPermissionsManager?.meetingPermissionListener = object : MeetingPermissionsListener {
            override fun onPermissionGranted() {
                _permissionState.value = true
            }

            override fun onPermissionsDenied() {
                _permissionState.value = false
            }
        }
    }

    fun requestPermission() {
        meetingPermissionsManager?.requestPermissions()
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    data class FetchUsersSuccessState(val users: List<UserListItem>, val shouldLoadMore: Boolean) :
        ActivityState()

    data class SetUpNextPageUsersState(val users: List<UserListItem>, val shouldLoadMore: Boolean) :
        ActivityState()

    object InviteUserSuccessState : ActivityState()
    object NotificationProgressState : ActivityState()

    companion object {
        private const val USERS_PER_PAGE = 10
    }
}