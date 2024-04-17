package com.nayan.nayancamv2

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import co.nayan.appsession.SessionManager
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.LocaleHelper
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.scaleBy
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.di.DaggerNayanCamComponent
import com.nayan.nayancamv2.di.SessionInteractor
import com.nayan.nayancamv2.helper.GlobalParams.currentTemperature
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.IMetaDataHelper
import com.nayan.nayancamv2.helper.IRecordingHelper
import com.nayan.nayancamv2.model.ExtCamType
import com.nayan.nayancamv2.repository.repository_cam.INayanCamRepository
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.temperature.TemperatureProvider
import com.nayan.nayancamv2.util.CommonUtils
import com.nayan.nayancamv2.util.EventObserver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

abstract class BaseHoverService : LifecycleService() {

    lateinit var floatingView: View
    lateinit var floatingViewIvLogo: ImageView
    private lateinit var floatingViewTemperatureMessage: View
    lateinit var floatingViewRecordingStatusMessage: View
    lateinit var floatingViewLocationMessage: View
    private lateinit var floatingViewSurveyorMessage: View
    private lateinit var floatingBottomButtons: View
    private lateinit var floatingTopButtons: View
    private lateinit var floatingCenterButtons: View
    private lateinit var flRoot: View
    lateinit var layoutParamsHoverView: WindowManager.LayoutParams
    private lateinit var openRecorder: View
    private lateinit var openDashboard: View
    private lateinit var openSurgeMap: View
    private lateinit var restartCameraService: View
    lateinit var close: View
    lateinit var temperatureMessageTxt: TextView
    lateinit var messageParentLayout: CardView
    lateinit var recordingStatusMessageTxt: TextView
    private lateinit var windowManager: WindowManager
    private var drawable: Int = 0
    private var drawableLite: Int = 0
    private var recording: Int = 0
    private var failed: Int = 0
    private var successful: Int = 0
    private var camType: ExtCamType = ExtCamType.None

    @Inject
    lateinit var mNayanCamModuleInteractor: NayanCamModuleInteractor

    @Inject
    lateinit var nayanCamRepository: INayanCamRepository

    @Inject
    lateinit var storageUtil: StorageUtil

    @Inject
    lateinit var iRecordingHelper: IRecordingHelper

    @Inject
    lateinit var iMetaDataHelper: IMetaDataHelper

    private lateinit var sessionManager: SessionManager

    private val temperatureAnim by lazy { createBlinkAnimation() }
    private val aiScanningAnim by lazy { createBlinkAnimation() }
    var lastShownTempMessage = 0L
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val overHeatingThreshold by lazy { mNayanCamModuleInteractor.getOverheatingRestartTemperature() }
    private val driverLiteThreshold by lazy { mNayanCamModuleInteractor.getDriverLiteTemperature() }
    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences(SessionManager.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val hoverButtonTouchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParamsHoverView.x
                initialY = layoutParamsHoverView.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                showUIButton()
                layoutParamsHoverView.x = initialX + (event.rawX - initialTouchX).toInt()
                layoutParamsHoverView.y = initialY + (event.rawY - initialTouchY).toInt()
                if (::windowManager.isInitialized)
                    windowManager.updateViewLayout(floatingView, layoutParamsHoverView)
                if (checkForCloseTab(event.rawX, event.rawY))
                    close.scaleBy(1.25F)
                else close.scaleBy(1F)

                if (checkForRestartTab(event.rawX, event.rawY))
                    restartCameraService.scaleBy(1.25F)
                else restartCameraService.scaleBy(1F)

                if (checkForCloseToOpenRecorderTab(event.rawX, event.rawY))
                    openRecorder.scaleBy(1.25F)
                else openRecorder.scaleBy(1F)

                if (checkForCloseToOpenDashboardTab(event.rawX, event.rawY))
                    openDashboard.scaleBy(1.25F)
                else openDashboard.scaleBy(1F)

                if (checkForOpenSurgeMap(event.rawX, event.rawY))
                    openSurgeMap.scaleBy(1.25F)
                else openSurgeMap.scaleBy(1F)
            }

            MotionEvent.ACTION_UP -> {
                hideUIButton()
                when {
                    checkForOpenSurgeMap(event.rawX, event.rawY) -> {
                        val comingFrom = if (camType == ExtCamType.Drone) "External" else ""
                        AIResultsHelperImpl.getMutablePIPLD()
                            .postValue(AIResultsHelperImpl.InitState(""))
                        mNayanCamModuleInteractor.startSurgeMapActivity(
                            this,
                            comingFrom = comingFrom
                        )
                        settleFloatingView(event)
                    }

                    checkForCloseTab(event.rawX, event.rawY) -> {
                        floatingView.gone()
                        stopSelf()
                        resetConnectionIfRequired()
                    }

                    checkForCloseToOpenRecorderTab(event.rawX, event.rawY) -> {
                        floatingView.gone()
                        openNayanRecorder()
                    }

                    checkForCloseToOpenDashboardTab(event.rawX, event.rawY) -> {
                        openDashboard()
                    }

                    checkForRestartTab(event.rawX, event.rawY) -> restartCameraService()


                    else -> settleFloatingView(event)
                }
            }
        }
        true
    }

    open fun resetConnectionIfRequired() {}

    abstract fun openNayanRecorder()

    abstract fun restartCameraService()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        DaggerNayanCamComponent.builder()
            .context(this)
            .appDependencies(
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    NayanCamModuleDependencies::class.java
                )
            ).build().inject(this)

        if (mNayanCamModuleInteractor is SessionInteractor) {
            sessionManager = SessionManager(
                sharedPrefs,
                null,
                this,
                null,
                (mNayanCamModuleInteractor as SessionInteractor).getSessionRepositoryInterface()
            ).apply {
                shouldCheckUserInteraction = false
                setMetaData(null, null, null, mNayanCamModuleInteractor.getCurrentRole())
            }
        }

        setNotification()
        setupUI()
        setUpObservers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            if (::windowManager.isInitialized) {
                windowManager.removeView(floatingViewTemperatureMessage)
                windowManager.removeView(floatingTopButtons)
                windowManager.removeView(floatingCenterButtons)
                windowManager.removeView(floatingBottomButtons)
                windowManager.removeView(floatingView)
                windowManager.removeView(floatingViewLocationMessage)
                windowManager.removeView(floatingViewRecordingStatusMessage)
                windowManager.removeView(floatingViewSurveyorMessage)
            }
            nayanCamRepository.stopTemperatureUpdate()
            nayanCamRepository.getTemperatureLiveData().removeObserver(temperatureObserver)
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun setupUI() = lifecycleScope.launch {
        try {
            drawable = R.drawable.ic_hover_default
            drawableLite = R.drawable.ic_hover_driver_lite
            successful = R.drawable.ic_hover_recording_success
            failed = R.drawable.ic_hover_recording_failed
            recording = R.drawable.ic_hover_recording

            if (::windowManager.isInitialized.not())
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(applicationContext)
            val themedContext = ContextThemeWrapper(applicationContext, R.style.AppTheme)
            floatingCenterButtons = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_floating_open_surge_map_view, null)
            floatingTopButtons = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_floating_open_recorder_view, null)
            floatingBottomButtons = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_surface_bottom_icons, null)
            floatingView = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_floating_badge, null)
            floatingViewTemperatureMessage = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_floating_message, null)

            floatingViewRecordingStatusMessage = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_floating_message, null)

            floatingViewLocationMessage = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_floating_error_location, null)
            floatingViewLocationMessage.gone()

            floatingViewSurveyorMessage = inflater.cloneInContext(themedContext)
                .inflate(R.layout.layout_floating_error_surveyor, null)
            floatingViewSurveyorMessage.gone()
            flRoot = floatingView.findViewById(R.id.flRoot)
            floatingViewIvLogo = floatingView.findViewById(R.id.ivLogo)
            openRecorder = floatingTopButtons.findViewById(R.id.open_recorder)
            openDashboard = floatingTopButtons.findViewById(R.id.open_dashboard)
            openSurgeMap = floatingCenterButtons.findViewById(R.id.open_surgeMap)
            close = floatingBottomButtons.findViewById(R.id.close)
            restartCameraService =
                floatingBottomButtons.findViewById(R.id.restartAutomaticRecording)
            temperatureMessageTxt = floatingViewTemperatureMessage.findViewById(R.id.messageTxt)
            messageParentLayout =
                floatingViewTemperatureMessage.findViewById(R.id.messageParentLayout)
            recordingStatusMessageTxt =
                floatingViewRecordingStatusMessage.findViewById(R.id.messageTxt)
            floatingViewRecordingStatusMessage.gone()
            floatingViewLocationMessage.findViewById<Button>(R.id.closeHover).setOnClickListener {
                floatingViewLocationMessage.gone()
                openDashboard()
            }

            floatingViewSurveyorMessage.findViewById<Button>(R.id.closeHover).setOnClickListener {
                floatingViewSurveyorMessage.gone()
                openDashboard()
            }

            val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE

            layoutParamsHoverView = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            layoutParamsHoverView.gravity = Gravity.TOP or Gravity.START
            layoutParamsHoverView.x = 0
            layoutParamsHoverView.y = (screenHeight() / 2) + 2

            val layoutParamsOpenRecorderView = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            layoutParamsOpenRecorderView.gravity = Gravity.TOP

            val layoutParamsBottomView = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            layoutParamsBottomView.gravity = Gravity.BOTTOM

            val layoutParamsRecordingStatus = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            layoutParamsRecordingStatus.gravity = Gravity.BOTTOM
            val layoutParamsOpenSurgeMapView = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            layoutParamsOpenSurgeMapView.gravity = Gravity.CENTER

            val layoutParamsMessage = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            layoutParamsMessage.gravity = Gravity.TOP

            val layoutParamsCenterView = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            layoutParamsCenterView.gravity = Gravity.CENTER

            floatingViewRecordingStatusMessage.gone()
            floatingViewLocationMessage.gone()
            floatingViewSurveyorMessage.gone()
            if (::windowManager.isInitialized) {
                windowManager.addView(floatingViewTemperatureMessage, layoutParamsMessage)
                windowManager.addView(
                    floatingViewRecordingStatusMessage,
                    layoutParamsRecordingStatus
                )
                windowManager.addView(floatingTopButtons, layoutParamsOpenRecorderView)
                windowManager.addView(floatingCenterButtons, layoutParamsOpenSurgeMapView)
                windowManager.addView(floatingBottomButtons, layoutParamsBottomView)
                windowManager.addView(floatingView, layoutParamsHoverView)
                windowManager.addView(floatingViewLocationMessage, layoutParamsCenterView)
                windowManager.addView(floatingViewSurveyorMessage, layoutParamsCenterView)
            }
            floatingView.setOnTouchListener(hoverButtonTouchListener)
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        }
    }

    private fun setUpObservers() = lifecycleScope.launch {
        nayanCamRepository.getTemperatureLiveData()
            .observe(this@BaseHoverService, temperatureObserver)
        nayanCamRepository.startTemperatureUpdate()
    }

    private val temperatureObserver = EventObserver<TemperatureProvider.TempEvent> {
        Timber.tag("TemperatureUpdate").d("*****************Temp update****************\n")
        when (it) {
            TemperatureProvider.TempEvent.Error -> {
                Timber.tag("TemperatureUpdate").d("Error Temp ")
            }

            TemperatureProvider.TempEvent.Loading -> {
                Timber.tag("TemperatureUpdate").d("loading temp")
            }

            is TemperatureProvider.TempEvent.TemperatureUpdate -> {
                Timber.tag("TemperatureUpdate")
                    .d("[BaseHoverService] temp --> ${it.data.temperature}]")
                checkTemperature(it.data.temperature, driverLiteThreshold, overHeatingThreshold)
            }
        }
    }

    abstract fun checkTemperature(
        temperature: Float,
        driverLiteThreshold: Float,
        overHeatingThreshold: Float
    )

    abstract fun setNotification()

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(channelId: String, channelName: String) {
        val description = getString(R.string.recording_started)
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        channel.description = description
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private var isRecordingAnimOn = false
    fun updateHoverIcon(
        type: HoverIconType
    ) = lifecycleScope.launch(Dispatchers.Main) {
        if (isRecordingAnimOn) {
            if (type == HoverIconType.HoverRecordingFinished || type == HoverIconType.HoverRecordingFailed)
                isRecordingAnimOn = false
            else return@launch
        }

        if (isInCorrectScreenOrientation) {
            if (currentTemperature >= driverLiteThreshold)
                floatingViewIvLogo.setImageResource(drawableLite)
            else floatingViewIvLogo.setImageResource(drawable)
            floatingViewIvLogo.clearAnimation()
        }

        when (type) {
            HoverIconType.DefaultHoverIcon -> {
                if (currentTemperature >= driverLiteThreshold)
                    floatingViewIvLogo.setImageResource(drawableLite)
                else if (currentTemperature >= overHeatingThreshold)
                    floatingViewIvLogo.setImageResource(R.drawable.ic_hover_temp_warning)
                else floatingViewIvLogo.setImageResource(drawable)
                floatingViewIvLogo.clearAnimation()
            }

            HoverIconType.HoverRecording -> {
                floatingViewIvLogo.clearAnimation()
                isRecordingAnimOn = true
                floatingViewIvLogo.setImageResource(recording)
                floatingViewIvLogo.startAnimation(createFadeInOutAnimation())
            }

            HoverIconType.HoverRecordingFinished -> {
                floatingViewIvLogo.setImageResource(successful)
                floatingViewIvLogo.clearAnimation()
                delay(1000)
                if (currentTemperature >= driverLiteThreshold)
                    floatingViewIvLogo.setImageResource(drawableLite)
                else floatingViewIvLogo.setImageResource(drawable)
                isRecordingAnimOn = false
            }

            HoverIconType.HoverRecordingFailed -> {
                floatingViewIvLogo.setImageResource(failed)
                floatingViewIvLogo.clearAnimation()
                delay(1000)
                if (currentTemperature >= driverLiteThreshold)
                    floatingViewIvLogo.setImageResource(drawableLite)
                else floatingViewIvLogo.setImageResource(drawable)
                isRecordingAnimOn = false
            }

            HoverIconType.TemperatureWarning -> {
                floatingViewIvLogo.clearAnimation()
                floatingViewIvLogo.setImageResource(R.drawable.ic_thermostat)
                if (temperatureAnim.hasStarted().not() || temperatureAnim.hasEnded())
                    floatingViewIvLogo.startAnimation(temperatureAnim)
            }

            HoverIconType.TemperatureError -> {
                floatingViewIvLogo.clearAnimation()
                floatingViewIvLogo.setImageResource(R.drawable.ic_red_temperature_warning)
                if (temperatureAnim.hasStarted().not() || temperatureAnim.hasEnded())
                    floatingViewIvLogo.startAnimation(temperatureAnim)
            }

            HoverIconType.OrientationError -> {
                showOrientationHint()
            }

            HoverIconType.OpticalFlowSuccessful -> {
                floatingViewIvLogo.clearAnimation()
                if (currentTemperature >= driverLiteThreshold)
                    floatingViewIvLogo.setImageResource(drawableLite)
                else floatingViewIvLogo.setImageResource(drawable)
            }

            HoverIconType.OpticalFlowError -> {
                floatingViewIvLogo.clearAnimation()
                floatingViewIvLogo.setImageResource(R.drawable.ic_hover_motionless)
            }

            HoverIconType.SamplingError -> {
                floatingViewIvLogo.clearAnimation()
//                samplingRate.toString().textAsBitmap(floatingViewIvLogo, Color.WHITE)?.let {
//                    floatingViewIvLogo.setImageBitmap(it)
//                    floatingViewIvLogo.startAnimation(fadeInOutAnimation())
//                }
            }

            HoverIconType.OnAIScanning -> {
                floatingViewIvLogo.clearAnimation()
                if (currentTemperature >= driverLiteThreshold)
                    floatingViewIvLogo.setImageResource(R.drawable.ic_hover_ai_scanning_driver_lite)
                else floatingViewIvLogo.setImageResource(R.drawable.ic_hover_ai_scanning)
                floatingViewIvLogo.startAnimation(aiScanningAnim)
            }

            HoverIconType.OnDrivingFast -> {
                floatingViewIvLogo.clearAnimation()
                floatingViewIvLogo.setImageResource(R.drawable.ic_hover_driving_fast)
            }

            HoverIconType.NotInSurgeError, HoverIconType.NoLocation -> {
                floatingViewIvLogo.clearAnimation()
                floatingViewIvLogo.setImageResource(R.drawable.ic_hover_not_in_surge)
            }

            HoverIconType.BlackSegment -> {
                floatingViewIvLogo.clearAnimation()
                floatingViewIvLogo.setImageResource(R.drawable.ic_hover_stop_recording)
            }
        }
    }

    open fun showOrientationHint() {}

    fun onSurveyorWarningStatus(
        ifUserLocationFallsWithInSurge: Boolean,
        isValidSpeed: Boolean
    ) = lifecycleScope.launch(Dispatchers.Main) {
        if (mNayanCamModuleInteractor.isSurveyor() && isInCorrectScreenOrientation) {
            if (ifUserLocationFallsWithInSurge) {
                if (isValidSpeed) floatingViewSurveyorMessage.gone()
                else {
                    updateHoverIcon(HoverIconType.OnDrivingFast)
                    CommonUtils.playSound(
                        R.raw.speed_alert,
                        this@BaseHoverService,
                        storageUtil.getVolumeLevel()
                    )
                    floatingViewSurveyorMessage.visible()
                }
            } else {
                updateHoverIcon(HoverIconType.NotInSurgeError)
                floatingViewSurveyorMessage.gone()
            }
        } else floatingViewSurveyorMessage.gone()
    }

    enum class HoverIconType {
        DefaultHoverIcon, HoverRecording, HoverRecordingFinished, HoverRecordingFailed,
        OrientationError, OpticalFlowError, OpticalFlowSuccessful, SamplingError,
        TemperatureWarning, TemperatureError, OnAIScanning, NoLocation, OnDrivingFast,
        NotInSurgeError, BlackSegment
    }

    private fun settleFloatingView(event: MotionEvent) {
        if (layoutParamsHoverView.x < screenWidth() / 2) {
            layoutParamsHoverView.x = 0
            layoutParamsHoverView.y = initialY + (event.rawY - initialTouchY).toInt()
            windowManager.updateViewLayout(floatingView, layoutParamsHoverView)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            flRoot.layoutParams = params
        } else {
            layoutParamsHoverView.x = screenWidth()
            layoutParamsHoverView.y = initialY + (event.rawY - initialTouchY).toInt()
            windowManager.updateViewLayout(floatingView, layoutParamsHoverView)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            flRoot.layoutParams = params
        }
    }


    private fun showUIButton() {
        floatingBottomButtons.visible()
        floatingTopButtons.visible()
        floatingCenterButtons.visible()
    }

    private fun hideUIButton() {
        floatingBottomButtons.gone()
        floatingTopButtons.gone()
        floatingCenterButtons.gone()
    }

    private fun checkForCloseToOpenDashboardTab(x: Float, y: Float): Boolean {
        val r = Rect()
        openDashboard.getHitRect(r)
        val openDashboardBounds =
            Rect(r.left, r.top, r.right, r.bottom + getHoverButtonHeight() / 2)
        return openDashboardBounds.contains(x.toInt(), y.toInt())
    }

    private fun checkForCloseToOpenRecorderTab(x: Float, y: Float): Boolean {
        val r = Rect()
        openRecorder.getHitRect(r)
        val openRecorderBounds = Rect(r.left, r.top, r.right, r.bottom + getHoverButtonHeight() / 2)
        return openRecorderBounds.contains(x.toInt(), y.toInt())
    }

    private fun checkForRestartTab(x: Float, y: Float): Boolean {
        val r = Rect()
        restartCameraService.getHitRect(r)
        val restartBounds = Rect(
            r.left,
            screenHeight() - restartCameraService.height - getHoverButtonHeight() / 2,
            r.right,
            screenHeight()
        )
        return x < restartBounds.right && y > restartBounds.top
    }

    private fun checkForCloseTab(x: Float, y: Float): Boolean {
        val r = Rect()
        close.getHitRect(r)
        val closeBounds = Rect(
            r.left,
            screenHeight() - close.height - getHoverButtonHeight() / 2,
            r.right,
            screenHeight()
        )
        return x > closeBounds.left && y > closeBounds.top
    }

    private fun getHoverButtonHeight(): Int {
        val r = Rect()
        flRoot.getHitRect(r)
        return r.height()
    }

    // region check screen width and height
    fun screenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.widthPixels
    }

    private fun screenHeight(): Int {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.heightPixels
    }

    private fun checkForOpenSurgeMap(x: Float, y: Float): Boolean {
        val centerX = screenWidth() / 2
        val centerY = screenHeight() / 2
        val openSurgeMap =
            Rect(
                centerX - (getHoverButtonHeight()),
                centerY - (getHoverButtonHeight()),
                centerX + (getHoverButtonHeight()),
                centerY + (getHoverButtonHeight()),
            )

        return openSurgeMap.contains(x.toInt(), y.toInt())
    }
    //endregion

    fun exit() {
        floatingView.gone()
        stopSelf()
    }

    fun hideHoverIcon() {
        floatingView.gone()
    }

    fun showHoverIcon() {
        floatingView.visible()
    }

    fun setDefaultHoverIcon(camType: ExtCamType) {
        this.camType = camType
        if (camType == ExtCamType.Drone) {
            drawable = R.drawable.ic_hover_drone
            recording = R.drawable.ic_drone_recording
            failed = R.drawable.ic_drone_recording_failed
            successful = R.drawable.ic_drone_recording_success
        } else if (camType == ExtCamType.DashCamera) {
            drawable = R.drawable.ic_hover_dashcam
            recording = R.drawable.ic_dashcam_recording
            failed = R.drawable.ic_dashcam_recording_failed
            successful = R.drawable.ic_dashcam_recording_success
        }
        floatingViewIvLogo.setImageResource(drawable)
    }

    private fun openDashboard() {
        floatingView.gone()
        resetConnectionIfRequired()
        showToast(getString(R.string.please_wait_open_dashboard))
        postDelayed(TimeUnit.SECONDS.toMillis(2)) {
            mNayanCamModuleInteractor.startDashboardActivity(
                this,
                shouldForceStartHover = false
            )
        }
        stopSelf()
    }
}
