package co.nayan.c3specialist_v2.screen_sharing.users

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
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.Tag
import co.nayan.c3specialist_v2.databinding.ActivityUsersBinding
import co.nayan.c3specialist_v2.screen_sharing.MeetingService
import co.nayan.c3specialist_v2.screen_sharing.ScreenSharingActivity
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants
import co.nayan.c3specialist_v2.screen_sharing.config.UserStatus
import co.nayan.c3specialist_v2.screen_sharing.models.MeetingStatus
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.UserListItem
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UsersActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val usersViewModel: UsersViewModel by viewModels()
    private val binding: ActivityUsersBinding by viewBinding(ActivityUsersBinding::inflate)
    private var isLoading: Boolean = false
    private var localStatus: String? = null

    private val onUserClickListener = object : OnUserClickListener {
        override fun onRequest(user: UserListItem) {
            if (localStatus != UserStatus.CONNECTED) {
                usersViewModel.selectedUser = user
                usersViewModel.requestPermission()
            }
        }
    }
    private val usersAdapter = UsersAdapter(onUserClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.users)

        setupViews()

        usersViewModel.initMeetingPermissionsManager(this)
        usersViewModel.permissionState.observe(this, permissionObserver)
        usersViewModel.state.observe(this, stateObserver)
        usersViewModel.fetchFirstPage()

        binding.pullToRefresh.setOnRefreshListener {
            usersViewModel.fetchFirstPage()
        }

        if (MeetingService.MEETING_STATUS.hasActiveObservers()) {
            MeetingService.MEETING_STATUS.observe(this, meetingStatusObserver)
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

    private val permissionObserver: Observer<Boolean> = Observer { isGranted ->
        if (isGranted) {
            showAlert(
                title = "Invite to Join",
                shouldFinish = false,
                message = String.format(
                    "Wants to invite the %s to join meeting?", usersViewModel.selectedUser?.name
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

    private fun setupViews() {
        binding.usersView.layoutManager = LinearLayoutManager(this)
        binding.usersView.adapter = usersAdapter
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                isLoading = true
                if (binding.pullToRefresh.isRefreshing.not()) {
                    binding.shimmerViewContainer.visible()
                    binding.shimmerViewContainer.startShimmer()
                    binding.usersView.gone()
                    binding.loadingFailedLayout.loadingFailedContainer.gone()
                }
            }

            is UsersViewModel.FetchUsersSuccessState -> {
                setupUsersView(it.users, it.shouldLoadMore)
            }

            is UsersViewModel.SetUpNextPageUsersState -> {
                isLoading = false
                usersAdapter.shouldLoadMore = it.shouldLoadMore
                if (it.users.isNotEmpty()) {
                    usersAdapter.addNewUsers(it.users)
                }
            }

            UsersViewModel.NotificationProgressState -> {
                showProgressDialog(getString(R.string.sending_invite))
            }

            UsersViewModel.InviteUserSuccessState -> {
                hideProgressDialog()
                moveToNextScreen()
            }

            is ErrorState -> {
                isLoading = false
                hideProgressDialog()
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private val onScrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = binding.usersView.layoutManager as LinearLayoutManager
            val visibleCount = layoutManager.findLastVisibleItemPosition()
            if (visibleCount == usersAdapter.itemCount - 1 && isLoading.not()) {
                isLoading = true
                usersViewModel.fetchNextPage()
            }
        }
    }

    private fun setupUsersView(users: List<UserListItem>, shouldLoadMore: Boolean) {
        isLoading = false
        usersAdapter.shouldLoadMore = shouldLoadMore
        binding.shimmerViewContainer.gone()
        binding.shimmerViewContainer.stopShimmer()
        binding.pullToRefresh.isRefreshing = false

        if (users.isEmpty()) {
            binding.loadingFailedLayout.loadingFailedContainer.visible()
            binding.usersView.gone()
        } else {
            binding.loadingFailedLayout.loadingFailedContainer.gone()
            binding.usersView.visible()
            usersAdapter.addAll(users)
            usersAdapter.notifyDataSetChanged()
            binding.usersView.addOnScrollListener(onScrollChangeListener)
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.usersView, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun alertDialogPositiveClick(shouldFinishActivity: Boolean, tag: String?) {
        when (tag) {
            Tag.SEND_REQUEST -> {
                usersViewModel.inviteUser()
            }
        }
        if (shouldFinishActivity) {
            this@UsersActivity.finish()
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
        if (usersViewModel.selectedUser?.id == null) return
        val input = UserListItem(
            usersViewModel.selectedUser?.id, usersViewModel.selectedUser?.name
        )
        screenSharingContract.launch(input)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_item, menu)
        val menuItem = menu.findItem(R.id.search)
        val searchView = menuItem?.actionView as SearchView
        searchView.setOnQueryTextListener(queryTextListener)
        return super.onPrepareOptionsMenu(menu)
    }

    @SuppressLint("NotifyDataSetChanged")
    private val queryTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            query?.let {
                usersAdapter.filterListItems(it)
                usersAdapter.notifyDataSetChanged()
            }
            return false
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            newText?.let {
                usersAdapter.filterListItems(it)
                usersAdapter.notifyDataSetChanged()
            }
            return false
        }
    }
}

class ScreenSharingContract : ActivityResultContract<UserListItem?, Boolean?>() {
    override fun createIntent(context: Context, input: UserListItem?): Intent {
        val intent = Intent(context, ScreenSharingActivity::class.java)
        intent.apply {
            action = MeetingServiceConstants.START_SERVICE
            putExtra(Extras.IS_ADMIN, true)
            putExtra(Extras.REMOTE_USER_ID, input?.id)
            putExtra(Extras.REMOTE_USER_NAME, input?.name)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean? {
        return when {
            resultCode != Activity.RESULT_OK -> false
            else -> intent?.getBooleanExtra(Extras.PERMISSION_DENIED, false)
        }
    }
}