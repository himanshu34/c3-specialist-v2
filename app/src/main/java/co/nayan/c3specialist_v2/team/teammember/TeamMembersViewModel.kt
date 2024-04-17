package co.nayan.c3specialist_v2.team.teammember

import androidx.activity.result.ActivityResultCaller
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.MemberStatus
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.screen_sharing.PermissionManagerProvider
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingPermissionsListener
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingPermissionsManager
import co.nayan.c3specialist_v2.screen_sharing.users.UsersRepository
import co.nayan.c3specialist_v2.screen_sharing.users.UsersViewModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.UserListItem
import co.nayan.c3v2.core.models.c3_module.responses.Member
import co.nayan.c3v2.core.models.c3_module.responses.TeamMemberResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TeamMembersViewModel @Inject constructor(
    private val teamMembersRepository: TeamMembersRepository,
    private val permissionManagerProvider: PermissionManagerProvider,
    private val usersRepository: UsersRepository,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _permissionState: MutableLiveData<Boolean> = MutableLiveData()
    val permissionState: LiveData<Boolean> = _permissionState

    var selectedUser: UserListItem? = null
    private var status = MemberStatus.MEMBERS
    private var memberResponse: TeamMemberResponse? = null
    private var meetingPermissionsManager: MeetingPermissionsManager? = null

    fun fetchTeamMembers() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            memberResponse = teamMembersRepository.fetchTeamMembers()
            _state.value = FinishedState
            setStatus(status)
        }
    }

    fun setStatus(toSet: MemberStatus) {
        status = toSet
        val members = mutableListOf<Member>()
        val message: String
        when (toSet) {
            MemberStatus.MEMBERS -> {
                message = "You don't have any member in your team..."
                members.addAll(memberResponse?.members ?: emptyList())
            }
            MemberStatus.PENDING -> {
                message = "You don't have any pending request..."
                members.addAll(memberResponse?.pending ?: emptyList())
            }
            MemberStatus.REJECTED -> {
                message = "No data found..."
                members.addAll(memberResponse?.rejected ?: emptyList())
            }
        }
        _state.value = if (members.isEmpty()) {
            NoTeamMembersState(message)
        } else {
            FetchTeamMembersSuccessState(members, toSet)
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun inviteUser() {
        if (selectedUser == null) {
            return
        }
        viewModelScope.launch(exceptionHandler) {
            _state.value = UsersViewModel.NotificationProgressState
            val user = userRepository.getUserInfo()
            val message = "${user?.name} is calling you..."
            usersRepository.inviteUser(selectedUser?.id, message, user?.name)
            _state.value = UsersViewModel.InviteUserSuccessState
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


    object InviteUserSuccessState : ActivityState()
    object NotificationProgressState : ActivityState()

    data class NoTeamMembersState(val message: String) : ActivityState()
    data class FetchTeamMembersSuccessState(
        val members: List<Member>,
        val status: MemberStatus
    ) : ActivityState()
}