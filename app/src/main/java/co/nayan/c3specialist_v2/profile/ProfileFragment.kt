package co.nayan.c3specialist_v2.profile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.applanguage.LanguageSelectionDialogFragment
import co.nayan.c3specialist_v2.compressImage
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.config.KycType
import co.nayan.c3specialist_v2.config.ProfileConstants.BANK_DETAILS
import co.nayan.c3specialist_v2.config.ProfileConstants.PAN
import co.nayan.c3specialist_v2.config.ProfileConstants.PERSONAL_INFO
import co.nayan.c3specialist_v2.config.ProfileConstants.PHOTO_ID
import co.nayan.c3specialist_v2.config.ProfileConstants.REFERRAL
import co.nayan.c3specialist_v2.config.ProfileConstants.RESET_PASSWORD
import co.nayan.c3specialist_v2.config.ProfileConstants.SETTINGS
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.FragmentProfileBinding
import co.nayan.c3specialist_v2.getFrontCameraSpecs
import co.nayan.c3specialist_v2.loadImage
import co.nayan.c3specialist_v2.loadProfileImage
import co.nayan.c3specialist_v2.profile.utils.ProfileResultCallback
import co.nayan.c3specialist_v2.profile.utils.getLanguage
import co.nayan.c3specialist_v2.profile.widgets.AuthenticateUserCallback
import co.nayan.c3specialist_v2.profile.widgets.AuthenticateUserDialogFragment
import co.nayan.c3specialist_v2.profile.widgets.KYCStatusDialogFragment
import co.nayan.c3specialist_v2.profile.widgets.OnKycTypeSelectionListener
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3specialist_v2.wallet.WalletFragment
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.hasNetwork
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.c3_module.ProfileIntentInputData
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.getBatteryLevel
import com.nayan.nayancamv2.hovermode.BackgroundCameraService
import com.nayan.nayancamv2.startAttendanceSyncingRequest
import com.nayan.nayancamv2.startVideoUploadRequest
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.util.isServiceRunning
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : BaseFragment(R.layout.fragment_profile) {

    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val binding by viewBinding(FragmentProfileBinding::bind)
    private var isDriver: Boolean = false
    private lateinit var storageUtil: StorageUtil
    private var fileUri: Uri? = null

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    @Inject
    lateinit var errorUtils: ErrorUtils

    @Inject
    lateinit var userRepository: UserRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPrefManager = SharedPrefManager(requireContext())
        storageUtil = StorageUtil(requireContext(), sharedPrefManager, nayanCamModuleInteractor)

        profileViewModel.state.observe(viewLifecycleOwner, stateObserver)
        profileViewModel.user.observe(viewLifecycleOwner, userObserver)
        profileViewModel.fetchUserDetails()
        setupClicks()

        profileViewModel.cameraClicked = {
            val permissionCheck =
                ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED)
                requestCameraPermissionsLauncher.launch(Manifest.permission.CAMERA)
            else openCameraIntent()
        }

        profileViewModel.galleryClicked = {
            val requestedPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_EXTERNAL_STORAGE
            else Manifest.permission.READ_MEDIA_IMAGES

            val permissionCheck =
                ContextCompat.checkSelfPermission(requireActivity(), requestedPermission)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED)
                requestReadStoragePermissionsLauncher.launch(requestedPermission)
            else openGalleryIntent()
        }

        binding.pullToRefresh.setOnRefreshListener {
            profileViewModel.fetchUserDetails()
        }

        val roles = userRepository.getUserRoles()
        val currentRole = roles.firstOrNull() ?: Role.SPECIALIST
        if (currentRole == Role.DRIVER || roles.contains(Role.DRIVER)) isDriver = true
        if (isDriver) {
            binding.syncVideos.visible()
            updateBatteryWarning()
        } else binding.syncVideos.gone()

        binding.syncVideos.setOnClickListener {
            requireContext().apply {
                if (isServiceRunning<BackgroundCameraService>().not() &&
                    isServiceRunning<ExternalCameraProcessingService>().not()
                ) startVideoUploadRequest(nayanCamModuleInteractor.isSurveyor(), true)
                else showToast(getString(R.string.please_wait_hover_mode_running))
            }
        }

        setupLearningVideoSwitch()
        setupVersionCode()

        if (activity is DashboardActivity) {
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.PROFILE)
        }
    }

    private fun setupLearningVideoSwitch() {
        if (BuildConfig.FLAVOR == "qa") {
            binding.learningVideoSwitch.visible()
            binding.learningVideoSwitch.isChecked = profileViewModel.isLearningVideosEnabled()
        } else binding.learningVideoSwitch.gone()
    }

    private fun setupVersionCode() {
        binding.buildVersionTv.text = profileViewModel.getBuildVersion() ?: run {
            String.format("v %s.%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        }
    }

    private val userObserver: Observer<User?> = Observer {
        it?.let { user ->
            binding.userNameTxt.text = user.name ?: getString(R.string.your_name)
            binding.userEmailTxt.text = user.email ?: getString(R.string.your_email)
            binding.userMobileTxt.text = user.phoneNumber
            binding.languageTxt.text = user.defaultLanguage.getLanguage()
            if (user.referalCode.isNullOrEmpty()) binding.referralContainer.gone()
            else binding.referralContainer.visible()
            binding.profileImageView.apply {
                user.userImage?.let { image ->
                    loadProfileImage(image, R.drawable.bg_profile_photo)
                } ?: run { setImageResource(R.drawable.bg_profile_photo) }
            }
            Timber.d("🦀User Roles:${user.activeRoles}")
            setUpMenus(user.activeRoles)
            setupKycDetails()
        }
    }

    private fun setUpMenus(roles: List<String>?) {
        if (null != roles && roles.contains(Role.DRIVER)) {
            binding.settingsContainer.visible()
            binding.galleryContainer.gone()
            binding.tncContainer.gone()
        } else {
            binding.settingsContainer.gone()
            binding.galleryContainer.gone()
            binding.tncContainer.gone()
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                if (binding.pullToRefresh.isRefreshing)
                    binding.progressBar.invisible()
                else binding.progressBar.visible()
            }

            ProfileViewModel.FetchDetailsSuccessState -> {
                if (binding.progressBar.isVisible)
                    binding.progressBar.invisible()
                binding.pullToRefresh.isRefreshing = false
                updateBatteryWarning()
            }

            is ProfileViewModel.AuthenticationSuccessState -> {
                if (binding.progressBar.isVisible)
                    binding.progressBar.invisible()
                binding.pullToRefresh.isRefreshing = false
                val isCorrect = it.authenticationResponse.isCorrectPassword ?: false
                val token = it.authenticationResponse.idToken

                if (isCorrect && !token.isNullOrEmpty()) {
                    navigateTo(BANK_DETAILS, token)
                } else {
                    showMessage(
                        it.authenticationResponse.message
                            ?: getString(co.nayan.c3v2.core.R.string.something_went_wrong)
                    )
                }
            }

            is ErrorState -> {
                updateBatteryWarning()
                if (binding.progressBar.isVisible)
                    binding.progressBar.invisible()
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun updateBatteryWarning() = lifecycleScope.launch {
        if (isDriver) {
            when {
                (profileViewModel.isSurveyor()
                        || nayanCamModuleInteractor.getDeviceModel().isKentCam()) -> {
                    binding.warningBatteryLow.gone()
                    binding.syncVideos.enabled()
                }

                (requireContext().getBatteryLevel() >= 15f) -> {
                    binding.warningBatteryLow.gone()
                    binding.syncVideos.enabled()
                }

                else -> {
                    binding.warningBatteryLow.visible()
                    binding.syncVideos.disabled()
                }
            }
        }
    }

    private fun setupClicks() {
        binding.profileImageView.setOnClickListener { showFileChooser() }
        binding.logoutTxt.setOnClickListener { uploadDriverAttendance() }
        binding.personalInfoContainer.setOnClickListener { navigateTo(PERSONAL_INFO) }
        binding.bankDetailsContainer.setOnClickListener { showAuthenticationDialog() }
        binding.languageContainer.setOnClickListener { showLanguageSelector() }
        binding.kycContainer.setOnClickListener { showKycStatus() }
        binding.resetPasswordContainer.setOnClickListener { navigateTo(RESET_PASSWORD) }
        binding.settingsContainer.setOnClickListener { navigateTo(SETTINGS) }
        binding.referralContainer.setOnClickListener { navigateTo(REFERRAL) }
        binding.learningVideoSwitch.setOnCheckedChangeListener { _, isChecked ->
            profileViewModel.updateLearningVideosStatus(isChecked)
        }
        binding.walletContainer.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, WalletFragment())
                .commit()
        }
    }

    private fun showFileChooser() {
        childFragmentManager.showDialogFragment(ChooseFileDialogBottomSheet())
    }

    private fun uploadDriverAttendance() = lifecycleScope.launch {
        val currentRoles = userRepository.getUserRoles()
        if (requireContext().hasNetwork()) {
            if (userRepository.isUserLoggedIn() && currentRoles.contains(Role.DRIVER)) {
                val activity = activity as BaseActivity
                val workRequestId = activity.startAttendanceSyncingRequest(true)
                startObservingWorkRequest(activity, workRequestId)
            } else logoutUser()
        } else showMessage(getString(R.string.no_internet_message))
    }

    private fun logoutUser() = lifecycleScope.launch {
        val activity = activity as BaseActivity
        binding.progressBar.visible()
        binding.logoutTxt.disabled()
        val isUploaded = activity.uploadUnsyncedSessions()
        binding.progressBar.invisible()
        val success = activity.unregisterFCMToken()
        if (isUploaded && success) {
            storageUtil.sharedPrefManager.clearPreferences()
            activity.logoutUser(errorMessage = null)
        } else {
            showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            binding.progressBar.invisible()
            binding.logoutTxt.enabled()
        }
    }

    private fun startObservingWorkRequest(
        activity: BaseActivity,
        workRequestId: UUID
    ) = lifecycleScope.launch {
        WorkManager.getInstance(activity)
            .getWorkInfoByIdLiveData(workRequestId) // requestId is the WorkRequest id
            .observe(viewLifecycleOwner) { workInfo ->
                if (workInfo?.state == null) return@observe
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        binding.progressBar.visible()
                        binding.logoutTxt.disabled()
                    }

                    WorkInfo.State.SUCCEEDED -> logoutUser()

                    WorkInfo.State.FAILED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {
                        showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
                        binding.progressBar.invisible()
                        binding.logoutTxt.enabled()
                    }

                    else -> {}
                }
            }
    }

    private val onKycTypeSelectionListener = object : OnKycTypeSelectionListener {
        override fun onSelect(type: String) {
            if (type == KycType.PAN) navigateTo(PAN)
            else navigateTo(PHOTO_ID)
        }
    }

    private fun showKycStatus() {
        KYCStatusDialogFragment.newInstance(onKycTypeSelectionListener)
            .show(childFragmentManager, getString(R.string.kyc_status))
    }

    private fun setupKycDetails() = lifecycleScope.launch {
        val details = profileViewModel.getKycStatus()
        binding.kycStatusIv.apply {
            val drawable = ContextCompat.getDrawable(context, details.statusIconId)
            setImageDrawable(drawable)
        }
        binding.kycStatusTxt.text = getString(details.statusTextId)
    }

    private fun showLanguageSelector() {
        childFragmentManager.fragments.forEach {
            if (it is LanguageSelectionDialogFragment) {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
        }

        childFragmentManager.showDialogFragment(
            LanguageSelectionDialogFragment.newInstance(
                isCancelable = true,
                currentLanguage = profileViewModel.getAppLanguage()
            ), getString(R.string.select_language)
        )
    }

    private val authenticateUserCallback = object : AuthenticateUserCallback {
        override fun onSubmit(password: String) {
            profileViewModel.authenticateUserPassword(password)
        }
    }

    private fun showAuthenticationDialog() {
        AuthenticateUserDialogFragment.newInstance(authenticateUserCallback)
            .show(childFragmentManager, getString(R.string.enter_password))
    }

    private val getContent = registerForActivityResult(ProfileResultCallback()) {
        it?.let {
            showMessage(it)
            profileViewModel.fetchUserDetails()
        }
    }

    private fun navigateTo(screenName: String, token: String? = null) {
        getContent.launch(ProfileIntentInputData(screenName, token))
    }

    private fun showMessage(string: String) {
        Snackbar.make(binding.root, string, Snackbar.LENGTH_LONG).show()
    }

    private fun openCameraIntent() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(context?.packageManager!!) != null) {
                // Check if the device has a front camera
                val frontCameraId = requireActivity().getFrontCameraSpecs()
                frontCameraId?.let {
                    // Specify the camera facing as an extra
                    intent.putExtra("android.intent.extras.CAMERA_FACING", frontCameraId)
//                    intent.putExtra("android.intent.extra.CAMERA_ID", frontCameraId)
//                    intent.putExtra("android.intent.extras.CAMERA_FACING_FRONT", frontCameraId)
//                    intent.putExtra("android.intent.extras.LENS_FACING_FRONT", CameraCharacteristics.LENS_FACING_FRONT)
//                    intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                }

                storageUtil.saveUserImage(requireContext()).also {
                    fileUri = FileProvider.getUriForFile(
                        requireContext(),
                        "${BuildConfig.APPLICATION_ID}.provider",
                        it
                    )
                }
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                captureImageResultLauncher.launch(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
        }
    }

    private fun openGalleryIntent() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        chooseImageResultLauncher.launch(Intent.createChooser(intent, "Select Picture"))
    }

    private val captureImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch {
                    fileUri?.let {
                        binding.profileImageView.loadImage(it, R.drawable.bg_profile_photo)
                        requireContext().compressImage(it)?.let { file ->
                            profileViewModel.uploadImage(file)
                        }
                    }
                }
            }
        }

    private val chooseImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { data ->
                    lifecycleScope.launch {
                        try {
                            fileUri = data.data
                            fileUri?.let {
                                binding.profileImageView.loadImage(it, R.drawable.bg_profile_photo)
                                requireContext().compressImage(it)?.let { file ->
                                    profileViewModel.uploadImage(file)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Firebase.crashlytics.recordException(e)
                        }
                    }
                }
            }
        }

    private val requestCameraPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) openCameraIntent()
            else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                    return@registerForActivityResult
            }
        }

    private val requestReadStoragePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) openGalleryIntent()
            else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                    return@registerForActivityResult
            }
        }
}