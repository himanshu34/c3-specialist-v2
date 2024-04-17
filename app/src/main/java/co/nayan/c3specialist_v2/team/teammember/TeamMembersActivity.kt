package co.nayan.c3specialist_v2.team.teammember

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.MemberStatus
import co.nayan.c3specialist_v2.config.Tag
import co.nayan.c3specialist_v2.databinding.ActivityTeamMembersBinding
import co.nayan.c3specialist_v2.screen_sharing.MeetingService
import co.nayan.c3specialist_v2.screen_sharing.config.UserStatus
import co.nayan.c3specialist_v2.screen_sharing.models.MeetingStatus
import co.nayan.c3specialist_v2.screen_sharing.users.ScreenSharingContract
import co.nayan.c3specialist_v2.team.request_member.RequestNewMemberActivity
import co.nayan.c3specialist_v2.utils.setChildren
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.UserListItem
import co.nayan.c3v2.core.models.c3_module.responses.Member
import co.nayan.c3v2.core.utils.*
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TeamMembersActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val teamMembersViewModel: TeamMembersViewModel by viewModels()
    private val binding: ActivityTeamMembersBinding by viewBinding(ActivityTeamMembersBinding::inflate)
    private var localStatus: String? = null

    private val onTeamMemberClickListener = object : OnTeamMemberClickListener {
        override fun onClick(teamMember: Member) {
            if (localStatus != UserStatus.CONNECTED) {
                teamMembersViewModel.selectedUser = UserListItem(teamMember.id, teamMember.name)
                teamMembersViewModel.requestPermission()
            }
        }
    }

    private val teamMemberAdapter = TeamMembersAdapter(onTeamMemberClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.my_team)

        setupViews()
        setupClicks()

        teamMembersViewModel.initMeetingPermissionsManager(this)
        teamMembersViewModel.permissionState.observe(this, permissionObserver)

        if (MeetingService.MEETING_STATUS.hasActiveObservers()) {
            MeetingService.MEETING_STATUS.observe(this, meetingStatusObserver)
        }

        teamMembersViewModel.state.observe(this, stateObserver)

        binding.statusContainer.setChildren(R.id.membersTxt)
        binding.statusContainer.disabled()
        teamMembersViewModel.fetchTeamMembers()

        binding.pullToRefresh.setOnRefreshListener {
            binding.statusContainer.disabled()
            teamMembersViewModel.fetchTeamMembers()
        }
    }

    override fun onDestroy() {
        if (MeetingService.MEETING_STATUS.hasObservers()) {
            MeetingService.MEETING_STATUS.removeObserver(meetingStatusObserver)
        }
        super.onDestroy()
    }

    private val meetingStatusObserver: Observer<MeetingStatus?> = Observer { status ->
        status?.let {
            localStatus = it.localStatus
        }
    }

    override fun alertDialogPositiveClick(shouldFinishActivity: Boolean, tag: String?) {
        when (tag) {
            Tag.SEND_REQUEST -> {
                teamMembersViewModel.inviteUser()
            }
        }
        if (shouldFinishActivity) {
            this@TeamMembersActivity.finish()
        }
    }

    private val permissionObserver: Observer<Boolean> = Observer { isGranted ->
        if (isGranted) {
            showAlert(
                title = "Invite to Join",
                shouldFinish = false,
                message = String.format(
                    "Wants to invite the %s to join meeting?",
                    teamMembersViewModel.selectedUser?.name
                ),
                positiveText = getString(R.string.invite),
                negativeText = getString(R.string.cancel),
                showNegativeBtn = true,
                showPositiveBtn = true,
                tag = Tag.SEND_REQUEST
            )
        } else {
            showMessage(
                String.format(getString(R.string.permission_denied), "Storage and Audio")
            )
        }
    }

    private fun setupClicks() {
        binding.newMemberContainer.setOnClickListener {
            requestMemberContract.launch("")
        }

        binding.membersTxt.setOnClickListener {
            binding.statusContainer.setChildren(it.id)
            teamMembersViewModel.setStatus(MemberStatus.MEMBERS)
        }

        binding.pendingTxt.setOnClickListener {
            binding.statusContainer.setChildren(it.id)
            teamMembersViewModel.setStatus(MemberStatus.PENDING)
        }

        binding.rejectedTxt.setOnClickListener {
            binding.statusContainer.setChildren(it.id)
            teamMembersViewModel.setStatus(MemberStatus.REJECTED)
        }
    }

    private fun setupViews() {
        binding.teamMembersView.layoutManager = LinearLayoutManager(this)
        binding.teamMembersView.adapter = teamMemberAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                if (binding.pullToRefresh.isRefreshing.not()) {
                    binding.shimmerViewContainer.visible()
                    binding.shimmerViewContainer.startShimmer()
                    binding.teamMembersView.gone()
                    binding.noTeamMemberContainer.gone()
                }
            }
            FinishedState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noTeamMemberContainer.gone()
                binding.statusContainer.enabled()
                binding.teamMembersView.visible()
            }
            is TeamMembersViewModel.NoTeamMembersState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.errorTxt.text = it.message
                binding.noTeamMemberContainer.visible()
                binding.teamMembersView.gone()
            }
            is TeamMembersViewModel.FetchTeamMembersSuccessState -> {
                teamMemberAdapter.addAll(it.members, it.status)
                teamMemberAdapter.notifyDataSetChanged()
                binding.noTeamMemberContainer.gone()
                binding.teamMembersView.visible()
                binding.teamMembersView.scheduleLayoutAnimation()
                binding.teamMembersView.addOnScrollListener(onScrollChangeListener)
            }
            TeamMembersViewModel.NotificationProgressState -> {
                showProgressDialog(getString(R.string.sending_invite))
            }
            TeamMembersViewModel.InviteUserSuccessState -> {
                hideProgressDialog()
                moveToNextScreen()
            }
            is ErrorState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private val screenSharingContract =
        registerForActivityResult(ScreenSharingContract()) { permissionDenied ->
            if (permissionDenied == true) {
                showMessage(
                    String.format(getString(R.string.permission_denied), "Storage and Audio")
                )
            }
        }

    private fun moveToNextScreen() {
        if (teamMembersViewModel.selectedUser?.id == null) return
        val input = UserListItem(
            teamMembersViewModel.selectedUser?.id, teamMembersViewModel.selectedUser?.name
        )
        screenSharingContract.launch(input)
    }

    private val onScrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy != 0 && binding.newMemberContainer.isShown) {
                binding.newMemberContainer.gone()
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                binding.newMemberContainer.visible()
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_item, menu)
        val menuItem = menu.findItem(R.id.search)
        val searchView = menuItem?.actionView as SearchView
        searchView.setOnQueryTextListener(queryTextListener)
        return super.onPrepareOptionsMenu(menu)
    }


    @SuppressLint("NotifyDataSetChanged")
    private val queryTextListener =
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    teamMemberAdapter.filterListItems(it)
                    teamMemberAdapter.notifyDataSetChanged()
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    teamMemberAdapter.filterListItems(it)
                    teamMemberAdapter.notifyDataSetChanged()
                }
                return false
            }

        }

    private val requestMemberContract =
        registerForActivityResult(object : ActivityResultContract<String?, Boolean>() {
            override fun createIntent(context: Context, input: String?): Intent {
                return Intent(this@TeamMembersActivity, RequestNewMemberActivity::class.java)
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                return resultCode == Activity.RESULT_OK
            }
        }) { isAdded ->
            if (isAdded) {
                showMessage(getString(R.string.request_sent_successfully))
                teamMembersViewModel.fetchTeamMembers()
            }
        }

    override fun showMessage(message: String) {
        Snackbar.make(binding.shimmerViewContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}