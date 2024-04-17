package co.nayan.c3specialist_v2.driversetting

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.applanguage.CRPasswordAuthDialogFragment
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras.IS_STORAGE_FULL
import co.nayan.c3specialist_v2.databinding.ActivityDriverSettingBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.getDeviceAvailableRAM
import co.nayan.c3v2.core.hasPermission
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.getActualVideoFile
import com.nayan.nayancamv2.getCurrentDate
import com.nayan.nayancamv2.nightmode.NightModeConstraint
import com.nayan.nayancamv2.nightmode.NightModeConstraintSelector
import com.nayan.nayancamv2.nightmode.OnTimeSelectionListener
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.SharedPrefManager.Companion.DEFAULT_MAX_DATA_USAGE_LIMIT
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.util.Constants.MOBILE_DATA
import com.nayan.nayancamv2.util.Constants.WIFI_DATA
import com.nayan.nayancamv2.util.fromBase64
import com.nayan.nayancamv2.util.toBase64
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class DriverSettingActivity : BaseActivity() {

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor
    lateinit var nightModeConstraintSelector: NightModeConstraintSelector
    lateinit var sharedPrefManager: SharedPrefManager
    private val binding: ActivityDriverSettingBinding by viewBinding(ActivityDriverSettingBinding::inflate)
    lateinit var storageUtil: StorageUtil

    override fun showMessage(message: String) {
        //Not required
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefManager = SharedPrefManager(this)
        setContentView(binding.root)
        storageUtil = StorageUtil(this, sharedPrefManager, nayanCamModuleInteractor)
        nightModeConstraintSelector = NightModeConstraintSelector(sharedPrefManager)
        setSupportActionBar(binding.appToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.appToolbar.setNavigationOnClickListener {
            if (intent.getBooleanExtra(IS_STORAGE_FULL, false))
                nayanCamModuleInteractor.startDashboardActivity(this)
            finish()
        }
        title = getString(R.string.driver_setting)

        setUpInternalStorageUI()
        setUpDataUsageUI()
        setNetworkType()
        setupNightModeUI()
        setUpHoverSwitch()
        setUpDataUsageSwitch()
        setUpVolumeControl()
        setupAIModeUI()
        setupCRModeUI()
        setupDriverLITEModeUI()

        if (intent.hasExtra(IS_STORAGE_FULL) && intent.getBooleanExtra(IS_STORAGE_FULL, false))
            showMemoryError()

        userRepository.getUserInfo()?.let {
            it.allowCopy?.let { allow ->
                if (allow) binding.downloadContainer.visible()
            }
        }

        binding.downloadContainer.setOnClickListener {
            if (hasPermission(WRITE_EXTERNAL_STORAGE)) {
                offloadVideoInPhoneDirectory()
            } else requestPermission.launch(WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun setUpHoverSwitch() {
        binding.hoverModeSwitch.isChecked = storageUtil.isDefaultHoverMode()
        binding.hoverModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            storageUtil.setDefaultHoverMode(isChecked)
        }
    }

    private fun setUpDataUsageSwitch() {
        toggleDataUsageLimit(sharedPrefManager.isDataUsageLimitEnabled())
        binding.dataLimitSwitch.isChecked = sharedPrefManager.isDataUsageLimitEnabled()
        binding.dataLimitSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefManager.setDefaultDataUsageLimitEnabled(isChecked)
            toggleDataUsageLimit(isChecked)
        }
    }

    private fun toggleDataUsageLimit(isChecked: Boolean) {
        if (isChecked) {
            binding.tvAllocUsage.visible()
            binding.tvCurrentUsage.visible()
            binding.seekBarDataUsage.visible()
        } else {
            binding.tvAllocUsage.gone()
            binding.tvCurrentUsage.gone()
            binding.seekBarDataUsage.gone()
        }
    }

    private fun setUpInternalStorageUI() {
        val phoneStorage = storageUtil.getPhoneStorage()
        val totalStorage = (getString(R.string.available_total_storage_head) + " " +
                phoneStorage.availableStorage.memoryInfoInGB()
                + " / " + phoneStorage.totalStorage.memoryInfoInGB())
        val availableStorage =
            getAllocatedPhoneStorage(storageUtil.getAllocatedPhoneStorage(nayanCamModuleInteractor.getDeviceModel()))
        val count = sharedPrefManager.getOfflineVideoBatch()?.size ?: 0
        val offlineVideos = "${getString(R.string.offline_videos_head)} $count"
        setSeekBar()
        binding.tvAvailable.text = totalStorage
        binding.tvAlloc.text = availableStorage
        binding.tvLabel.text = offlineVideos
    }

    private fun setUpDataUsageUI() {
        val dataLimit: String =
            getAllocatedDataLimit(sharedPrefManager.getDataLimitForTheDay())
        setDataUsageSeekBar()
        binding.tvAllocUsage.text = dataLimit
        binding.tvCurrentUsage.text = getCurrentDataUsage()
    }

    private fun setDataUsageSeekBar() {
        binding.seekBarDataUsage.max = 100
        binding.seekBarDataUsage.progress = sharedPrefManager.getDataLimitForTheDay().toInt()
        binding.seekBarDataUsage.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                sharedPrefManager.setDataLimitForTheDay(progress.toFloat())
                binding.tvAllocUsage.text = getAllocatedDataLimit(progress.toFloat())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setSeekBar() {
        binding.seekBar.max = 100
        binding.seekBar.progress =
            storageUtil.getAllocatedPhoneStorage(nayanCamModuleInteractor.getDeviceModel()).toInt()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                storageUtil.setAllocatedPhoneStorage(progress.toFloat())
                binding.tvAlloc.text = getAllocatedPhoneStorage(progress.toFloat())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setNetworkType() {
        val networkType = storageUtil.getUploadNetworkType()
        if (networkType == MOBILE_DATA) binding.rgUploadType.check(R.id.type_mobile)
        else binding.rgUploadType.check(R.id.type_wifi)

        binding.rgUploadType.setOnCheckedChangeListener { _, checkedId ->
            var uploadType: Int = networkType
            if (checkedId == R.id.type_wifi) {
                uploadType = WIFI_DATA
            } else if (checkedId == R.id.type_mobile) {
                uploadType = MOBILE_DATA
            }
            storageUtil.setUploadNetworkType(uploadType)
        }
    }

    private fun setUpVolumeControl() {
        binding.tvLabelVolume.text =
            getString(R.string.current_volume, storageUtil.getVolumeLevel().toString())
        binding.seekBarVolume.progress = storageUtil.getVolumeLevel()
        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                storageUtil.setVolumeLevel(progress)
                binding.tvLabelVolume.text = getString(R.string.current_volume, progress.toString())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupAIModeUI() {
        val availMem = getDeviceAvailableRAM()
        var usedMemory = 0f
        var isDeviceCapableToRunWorkflow = false
        sharedPrefManager.getCameraAIWorkFlows()?.forEach { aiFlow ->
            if (aiFlow.cameraAIModels.isNotEmpty() && aiFlow.workflow_IsEnabled && aiFlow.workflow_IsDroneEnabled.not()) {
                aiFlow.cameraAIModels.firstOrNull()?.let {
                    usedMemory += it.ram
                    if (it.ram < availMem) isDeviceCapableToRunWorkflow = true
                }
            }
        }

        if ((availMem - usedMemory) < 0.5) {
            Timber.e("#### Available Ram usage: $availMem and memory used: $usedMemory")
            sharedPrefManager.setLITEMode(true)
            Firebase.crashlytics.log("#### Available Ram usage: $availMem and memory used: $usedMemory")
        }

        if (isDeviceCapableToRunWorkflow) {
            binding.aiModeSwitch.isChecked = nayanCamModuleInteractor.isAIMode()
            if (nayanCamModuleInteractor.isAIMode()) {
                binding.crModeSwitch.isChecked = false
                nayanCamModuleInteractor.setCRModeConfig(false)
            }

            binding.aiModeSwitch.setOnCheckedChangeListener { _, isChecked ->
                nayanCamModuleInteractor.setAIMode(isChecked)
                if (isChecked) {
                    binding.crModeSwitch.isChecked = false
                    nayanCamModuleInteractor.setCRModeConfig(false)
                }
            }
        } else {
            binding.aiModeSwitch.isChecked = false
            binding.aiModeSwitch.isEnabled = false
            binding.aiModeSwitch.isFocusable = false
            binding.aiModeSwitch.isClickable = false
        }
    }

    private fun setupCRModeUI() {
        val formattedDate = getCurrentDate()
        val email = userRepository.getUserInfo()?.email
        Timber.e("formattedDate: $formattedDate")
        Timber.e("email: $email")

        val toBase64 = (email!! + formattedDate).toBase64()
        Timber.e("toBase64: $toBase64")

        val fromBase64 = toBase64.fromBase64()
        Timber.e("fromBase64: $fromBase64")

        val password = nayanCamModuleInteractor.getCRModePassword()
        Timber.e("password: $password")

        binding.crModeSwitch.isChecked = if (nayanCamModuleInteractor.isSurveyor())
            nayanCamModuleInteractor.isCRMode()
        else if (toBase64.trim() == password.trim()) nayanCamModuleInteractor.isCRMode()
        else {
            nayanCamModuleInteractor.setCRModeConfig(false)
            false
        }

        binding.crModeSwitch.setOnClickListener {
            if (binding.crModeSwitch.isChecked) {
                binding.crModeSwitch.isChecked = false
                val fragment = CRPasswordAuthDialogFragment(email, formattedDate)
                fragment.onTokenUpdate = object : CRPasswordAuthDialogFragment.OnTokenUpdate {
                    override fun onFail() {
                        binding.crModeSwitch.isChecked = false
                        nayanCamModuleInteractor.setCRModeConfig(false)
                    }

                    override fun onSuccess() {
                        binding.crModeSwitch.isChecked = true
                        binding.aiModeSwitch.isChecked = false
                        nayanCamModuleInteractor.setCRModeConfig(true, toBase64)
                    }
                }

                fragment.show(supportFragmentManager, null)
            } else nayanCamModuleInteractor.setCRModeConfig(false)
        }
    }

    private fun setupDriverLITEModeUI() {
        binding.forcedLiteMode.text =
            "Forced LITE mode: ${if (sharedPrefManager.isForcedLITEMode()) "ON" else "OFF"}"
        val isLITEMode = sharedPrefManager.isLITEMode()
        binding.liteModeSwitch.isChecked = isLITEMode
        binding.liteModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefManager.setLITEMode(isChecked)
        }
    }

    private fun getAllocatedPhoneStorage(progress: Float): String {
        val phoneStorage = storageUtil.getPhoneStorage()
        val storage =
            String.format("%.2f GB", progress / 100f * phoneStorage.availableStorage.covertToGB())
        return "${getString(R.string.allocated_storage_head)} $storage"
    }

    private fun getAllocatedDataLimit(progress: Float): String {
        val maxDataUsage = String.format("%.2f MB", progress / 100f * DEFAULT_MAX_DATA_USAGE_LIMIT)
        return "${getString(R.string.data_limit)} $maxDataUsage"
    }

    private fun getCurrentDataUsage(): String {
        val dataUsage =
            String.format("%.2f MB", sharedPrefManager.getCurrentDataUsage().data.toFloat())
        return "${getString(R.string.current_data_usage)} $dataUsage"
    }

    private fun setupNightModeUI() {
        binding.nightModeSwitch.isChecked = nightModeConstraintSelector.isFeatureEnabled()
        binding.nightModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            nightModeConstraintSelector.toggleFeatureStatus(isChecked)
            binding.textView6.isVisible = isChecked
            binding.textView7.isVisible = isChecked
            binding.startTimeTv.isVisible = isChecked
            binding.endTimeTv.isVisible = isChecked
        }

        binding.tvNightMode.setOnClickListener {
            binding.nightModeSwitch.isChecked = !binding.nightModeSwitch.isChecked
        }

        binding.startTimeTv.text = nightModeConstraintSelector.getStartTime()
        binding.endTimeTv.text = nightModeConstraintSelector.getEndTime()

        binding.startTimeTv.setOnClickListener { getNightModeTimePicker(NightModeConstraintSelector.START_TIME) }
        binding.endTimeTv.setOnClickListener { getNightModeTimePicker(NightModeConstraintSelector.END_TIME) }

        binding.textView6.isVisible = nightModeConstraintSelector.isFeatureEnabled()
        binding.textView7.isVisible = nightModeConstraintSelector.isFeatureEnabled()
        binding.startTimeTv.isVisible = nightModeConstraintSelector.isFeatureEnabled()
        binding.endTimeTv.isVisible = nightModeConstraintSelector.isFeatureEnabled()
    }

    private fun getNightModeTimePicker(constraintType: String) {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this, { _: TimePicker?, hourOfDay: Int, minute: Int ->

                var hour = hourOfDay
                val timeSet = if (hourOfDay >= 12) {
                    "PM"
                } else {
                    "AM"
                }
                when {
                    hour > 12 -> {
                        hour -= 12
                    }

                    hour == 0 -> {
                        hour += 12
                    }
                }

                nightModeConstraintSelector.updateNightModeConstraints(
                    NightModeConstraint(hour, minute, timeSet, constraintType),
                    onTimeSelectionListener
                )
            }, calendar[Calendar.HOUR_OF_DAY], calendar[Calendar.MINUTE], false
        )
        timePickerDialog.setTitle(getString(R.string.select_time))
        timePickerDialog.show()
    }

    private val onTimeSelectionListener: OnTimeSelectionListener =
        object : OnTimeSelectionListener {
            override fun success() {
                binding.startTimeTv.text = nightModeConstraintSelector.getStartTime()
                binding.endTimeTv.text = nightModeConstraintSelector.getEndTime()
            }

            override fun failure(errorMessage: String) {
                showToast(errorMessage)
            }
        }

    private fun showMemoryError() {
        showToast(getString(co.nayan.nayancamv2.R.string.memory_error))
    }

    override fun onBackPressed() {
        if (intent.getBooleanExtra(IS_STORAGE_FULL, false))
            nayanCamModuleInteractor.startDashboardActivity(this)
        super.onBackPressed()
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) offloadVideoInPhoneDirectory()
        }

    private fun offloadVideoInPhoneDirectory() {
        try {
            sharedPrefManager.getOfflineVideoBatch()?.forEach {
                getActualVideoFile(storageUtil, it)?.let { file ->
                    storageUtil.saveInDownloads(this, file)
                }
            }
            showToast("Videos downloaded successfully")
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Timber.e(e)
        }
    }
}