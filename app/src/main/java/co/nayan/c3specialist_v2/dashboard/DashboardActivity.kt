package co.nayan.c3specialist_v2.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.applanguage.LanguageSelectionDialogFragment
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.config.Extras.SHOULD_FORCE_START_HOVER
import co.nayan.c3specialist_v2.databinding.ActivityDashboardBinding
import co.nayan.c3specialist_v2.home.HomeFragment
import co.nayan.c3specialist_v2.profile.ProfileFragment
import co.nayan.c3specialist_v2.search.SearchFragment
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.tutorial.utils.LearningVideosContractInput
import co.nayan.tutorial.utils.LearningVideosResultCallback
import com.google.android.material.snackbar.Snackbar
import com.nayan.nayancamv2.hovermode.HoverPermissionCallback
import com.nayan.nayancamv2.isHoverPermissionGranted
import com.nayan.nayancamv2.launchHoverService
import com.nayan.nayancamv2.requestHoverPermission
import com.nayan.nayancamv2.startSyncingVideoFiles
import com.nayan.nayancamv2.ui.PermissionsDisclosureActivity
import com.nayan.nayancamv2.util.TrackingUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DashboardActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val binding: ActivityDashboardBinding by viewBinding(ActivityDashboardBinding::inflate)

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupBottomContainer()
        setupClickListeners()
        setupLanguageContainer()
        binding.homeContainer.callOnClick()

        dashboardViewModel.state.observe(this, stateObserver)
        dashboardViewModel.fetchMinVersionCodeRequired()
        dashboardViewModel.fetchSurgeLocations()
        val userInfo = dashboardViewModel.getUserInfo()
        if (userInfo?.address.isNullOrEmpty()
            && userInfo?.city.isNullOrEmpty()
            && userInfo?.state.isNullOrEmpty()
            && userInfo?.country.isNullOrEmpty()
        ) dashboardViewModel.getUserLocation(userInfo?.name, this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragments = supportFragmentManager.fragments
                var fragment = fragments.firstOrNull()
                val isLocationFragment = fragment?.tag == "SupportLifecycleFragmentImpl"
                if (isLocationFragment && fragment != null) {
                    supportFragmentManager.beginTransaction().remove(fragment).commit()
                    fragment = fragments.firstOrNull()
                }

                if (fragment is HomeFragment || fragment is RoleRequestFragment) {
                    lifecycleScope.launch { uploadUnsyncedSessions() }
                    finish()
                } else if (fragment is SearchFragment) {
                    if (!fragment.canGoBack()) binding.homeContainer.performClick()
                } else binding.homeContainer.performClick()
            }
        })

        if (intent.getBooleanExtra(SHOULD_FORCE_START_HOVER, false))
            moveToNayanDriver()
        startSyncingVideoFiles()
    }

    override fun onResume() {
        super.onResume()
        dashboardViewModel.getAppLanguage()?.let { dashboardViewModel.fetchUserDetails() }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> hideProgressDialog()
            ProgressState -> {
                //showProgressDialog(getString(R.string.please_wait))
            }

            DashboardViewModel.NoActiveRolesState -> {
                binding.homeContainer.callOnClick()
            }

            is DashboardViewModel.UpdateAppState -> {
                hideProgressDialog()
                setupAppUpdateDialog(it.minVersionRequired)
            }

            is DashboardViewModel.RolesUpdateState -> {
                setupRolesUpdateDialog(it.message)
            }

            is DashboardViewModel.AppLearningVideoState -> {
                moveToLearningVideoScreen(it.video)
            }

            is DashboardViewModel.FetchUserLocationSuccessState -> {
                dashboardViewModel.updateBasePersonalInfo(
                    it.userName,
                    it.userLocation.address,
                    it.userLocation.state,
                    it.userLocation.city,
                    it.userLocation.country
                )
            }

            is ErrorState -> {
                hideProgressDialog()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupBottomContainer() {
        if (userRepository.getUserRoles().size == 1 &&
            userRepository.getUserRoles().contains(Role.DELHI_POLICE)
        ) {
            binding.bottomButtonsContainer.gone()
            return
        }
    }

    private fun setupClickListeners() {
        binding.homeContainer.setOnClickListener {
            val fragment = if (userRepository.getUserRoles().isEmpty())
                RoleRequestFragment() else HomeFragment()
            replaceFragment(fragment)
        }

        binding.profileContainer.setOnClickListener {
            it.selected()
            binding.homeContainer.unSelected()
            binding.searchContainer.unSelected()
            replaceFragment(ProfileFragment())
        }

        binding.searchContainer.setOnClickListener {
            it.selected()
            binding.homeContainer.unSelected()
            binding.profileContainer.unSelected()
            replaceFragment(SearchFragment())
        }
    }

    private fun setupLanguageContainer() = lifecycleScope.launch {
        if (dashboardViewModel.getAppLanguage() == null) {
            supportFragmentManager.fragments.forEach {
                if (it is LanguageSelectionDialogFragment) {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
            }

            supportFragmentManager.showDialogFragment(
                LanguageSelectionDialogFragment.newInstance(
                    isCancelable = false,
                    currentLanguage = null
                ), getString(R.string.select_language)
            )
        } else {
            if (userRepository.getUserRoles().isEmpty().not())
                dashboardViewModel.setUpAppLearningVideoStatus()
        }
    }

    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.fragmentContainer, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun setupRolesUpdateDialog(message: String) {
        val title = getString(R.string.roles_updated)
        val positiveText = getString(R.string.ok)
        showAlert(
            message = message,
            title = title,
            shouldFinish = false,
            showPositiveBtn = true,
            positiveText = positiveText,
            tag = Tag.ROLES_UPDATE,
            isCancelable = false
        )
    }

    private fun setupAppUpdateDialog(minVersionRequired: Int?) {
        val message = String.format(getString(R.string.app_update_message), minVersionRequired ?: 1)
        val title = getString(R.string.new_update_available)
        val positiveText = getString(R.string.ok)
        showAlert(
            message = message,
            title = title,
            showPositiveBtn = true,
            positiveText = positiveText,
            tag = Tag.APP_UPDATE,
            isCancelable = false,
            shouldFinish = true
        )
    }

    override fun alertDialogPositiveClick(shouldFinishActivity: Boolean, tag: String?) {
        when (tag) {
            Tag.ROLES_UPDATE -> binding.homeContainer.performClick()
            Tag.APP_UPDATE -> redirectToGooglePlay()
            Tag.NO_ACTIVE_ROLE -> {
            }

            Tag.LOCATION_OFF -> {
            }
        }
        if (shouldFinishActivity) this@DashboardActivity.finish()
    }

    private fun redirectToGooglePlay() {
        val uriBuilder = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uriBuilder)
        startActivity(intent)
    }

    private val launchLearningVideoActivity =
        registerForActivityResult(LearningVideosResultCallback()) {
            dashboardViewModel.saveAppLearningVideoCompletedFor()
        }

    private fun moveToLearningVideoScreen(video: Video?) {
        if (video == null) return
        else {
            launchLearningVideoActivity.launch(
                LearningVideosContractInput(
                    showDoneButton = true,
                    video = video,
                    workAssignment = null
                )
            )
        }
    }

    object Tag {
        const val ROLES_UPDATE = "roles update"
        const val APP_UPDATE = "app update"
        const val LOCATION_OFF = "location off"
        const val NO_ACTIVE_ROLE = "no active role"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun updateHomeBackground(role: CurrentRole) {
        when (role) {
            CurrentRole.DRIVER -> {
                if (nayanCamModuleInteractor.isSurveyor())
                    binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item_surveyor)
                else binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item_driver)
                binding.homeTv.setTextColor(
                    ContextCompat.getColor(
                        this,
                        co.nayan.canvas.R.color.role_driver
                    )
                )
                binding.homeContainer.selected()
                binding.searchContainer.unSelected()
                binding.profileContainer.unSelected()
            }

            CurrentRole.SPECIALIST -> {
                binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item_specialist)
                binding.homeTv.setTextColor(
                    ContextCompat.getColor(
                        this,
                        co.nayan.canvas.R.color.role_specialist
                    )
                )
                binding.homeContainer.selected()
                binding.searchContainer.unSelected()
                binding.profileContainer.unSelected()
            }

            CurrentRole.MANAGER -> {
                binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item_manager)
                binding.homeTv.setTextColor(
                    ContextCompat.getColor(
                        this,
                        co.nayan.canvas.R.color.role_manager
                    )
                )
                binding.homeContainer.selected()
                binding.searchContainer.unSelected()
                binding.profileContainer.unSelected()
            }

            CurrentRole.LEADER -> {
                binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item_leader)
                binding.homeTv.setTextColor(
                    ContextCompat.getColor(
                        this,
                        co.nayan.canvas.R.color.role_team_lead
                    )
                )
                binding.homeContainer.selected()
                binding.searchContainer.unSelected()
                binding.profileContainer.unSelected()
            }

            CurrentRole.ADMIN -> {
                binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item_admin)
                binding.homeTv.setTextColor(
                    ContextCompat.getColor(
                        this,
                        co.nayan.canvas.R.color.role_admin
                    )
                )
                binding.homeContainer.selected()
                binding.searchContainer.unSelected()
                binding.profileContainer.unSelected()
            }

            CurrentRole.SEARCH -> {
                binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item)
                binding.homeTv.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                binding.searchContainer.selected()
                binding.homeContainer.unSelected()
                binding.profileContainer.unSelected()
            }

            CurrentRole.PROFILE -> {
                binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item)
                binding.homeTv.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                binding.profileContainer.selected()
                binding.homeContainer.unSelected()
                binding.searchContainer.unSelected()
            }

            CurrentRole.ROLEREQUEST -> {
                binding.homeIv.setBackgroundResource(R.drawable.bg_home_navigation_item)
                binding.homeTv.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                binding.homeContainer.selected()
                binding.profileContainer.unSelected()
                binding.searchContainer.unSelected()
            }
        }
    }

    private fun moveToNayanDriver() {
        if (TrackingUtility.hasRequiredPermissions(this)) {
            if (!isHoverPermissionGranted())
                requestHoverPermission(
                    nayanCamModuleInteractor.getDeviceModel(),
                    hoverPermissionCallback
                )
            else launchHoverService()
        } else startActivity(Intent(this, PermissionsDisclosureActivity::class.java))
    }

    private val hoverPermissionCallback = object : HoverPermissionCallback {
        override fun onPermissionGranted() {
            launchHoverService()
        }

        override fun onPermissionDenied(intent: Intent) {
            requestOverLayPermissionLauncher.launch(intent)
        }

        override fun onPermissionDeniedAdditional(intent: Intent) {
            AlertDialog.Builder(this@DashboardActivity)
                .setTitle("Please Enable the additional permissions")
                .setMessage("Hover mode can not function in background if you disable these permissions.")
                .setPositiveButton("Enable now!") { _, _ -> startActivity(intent) }
                .setCancelable(false)
                .show()
        }
    }

    private val requestOverLayPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (isHoverPermissionGranted()) launchHoverService()
            else {
                showToast(getString(co.nayan.nayancamv2.R.string.draw_over_other_app_))
                finish()
            }
        }
}