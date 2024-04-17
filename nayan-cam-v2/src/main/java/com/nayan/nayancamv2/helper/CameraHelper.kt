package com.nayan.nayancamv2.helper

import android.Manifest
import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.content.Context.WINDOW_SERVICE
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest.Builder
import android.hardware.camera2.CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION
import android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE
import android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Range
import android.util.Size
import android.util.SizeF
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.isKentCam
import co.nayan.nayancamv2.R
import com.google.android.gms.common.util.concurrent.HandlerExecutor
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.ai.CameraProcessor
import com.nayan.nayancamv2.encoder.CircularEncoder
import com.nayan.nayancamv2.encoder.CircularEncoderHandler
import com.nayan.nayancamv2.encoder.CircularEncoderHandler.Companion.MSG_FRAME_AVAILABLE
import com.nayan.nayancamv2.env.SIZE_1080P
import com.nayan.nayancamv2.env.chooseOptimalSize
import com.nayan.nayancamv2.env.chooseVideoSize
import com.nayan.nayancamv2.env.getDisplaySmartSize
import com.nayan.nayancamv2.helper.GlobalParams.isCameraExternal
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.GlobalParams.isRecordingVideo
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.isYuvFormatSupported
import com.nayan.nayancamv2.model.RecordingData
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.SensorMeta
import com.nayan.nayancamv2.model.UserLocationMeta
import com.nayan.nayancamv2.model.VideoData
import com.nayan.nayancamv2.nightmode.NightModeConstraintSelector
import com.nayan.nayancamv2.nightmode.NightModeManagerImpl
import com.nayan.nayancamv2.storage.FileMetaDataEditor
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.temperature.StateManager
import com.nayan.nayancamv2.ui.views.AutoFitTextureView
import com.nayan.nayancamv2.util.Constants.CIRCULAR_ENCODER_BUFFER_LENGTH_IN_SEC
import com.nayan.nayancamv2.util.Constants.CIRCULAR_ENCODER_FPS
import com.nayan.nayancamv2.util.Constants.SAVING_VIDEO_DELAY
import com.nayan.nayancamv2.util.OrientationLiveData
import com.nayan.nayancamv2.util.RecordingEventState.AI_SCANNING
import com.nayan.nayancamv2.util.RecordingEventState.NOT_IN_SURGE
import com.nayan.nayancamv2.util.RecordingEventState.ORIENTATION_ERROR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

/**
 * This class handles all camera related process
 *
 * @property context
 * @property cameraUtils
 * @property nightModeConstraintSelector
 * @property nayanCamModuleInteractor
 * @property cameraProcessor
 * @property fileMetaDataEditor
 * @property sharedPrefManager
 * @property stateManager
 * @property iRecordingHelper
 * @property iMetaDataHelper
 */
class CameraHelper @Inject constructor(
    private val context: Context,
    private val cameraUtils: CameraUtils,
    private val nightModeConstraintSelector: NightModeConstraintSelector,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor,
    private val cameraProcessor: CameraProcessor,
    private val fileMetaDataEditor: FileMetaDataEditor,
    private val sharedPrefManager: SharedPrefManager,
    private val stateManager: StateManager,
    private val iRecordingHelper: IRecordingHelper,
    private val iMetaDataHelper: IMetaDataHelper
) {

    private var delayVideoRecordingData: RecordingData? = null

    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null

    private var fileSaveInProgress = false

    /** A [Handler] for running tasks in the background.  */
    private var backgroundHandler: Handler? = null

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

    private var textureView: AutoFitTextureView? = null

    private val nightModeHandler by lazy { Handler(Looper.getMainLooper()) }

    private lateinit var nightModeManagerImpl: NightModeManagerImpl

    /** The [Size] of camera preview.  */
    private var previewSize: Size? = null

    private var isCameraRunning = false

    private var baseCameraPreviewListener: BaseCameraPreviewListener? = null
    private var imageListener: ImageReader.OnImageAvailableListener? = null

    private val _recordingState = MutableStateFlow<RecordingState?>(null)
    val recordingState: StateFlow<RecordingState?> = _recordingState

    var currentLocationMeta: UserLocationMeta? = null
    var currentSensorMeta: SensorMeta? = null

    private val tag = CameraHelper::class.java.simpleName

    private val deviceModel by lazy { nayanCamModuleInteractor.getDeviceModel() }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager by lazy { context.getSystemService(CAMERA_SERVICE) as CameraManager }
    private val defaultDisplay by lazy {
        (context.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    /** A [CameraCaptureSession] for camera preview.  */
    private lateinit var captureSession: CameraCaptureSession

    /** A reference to the opened [CameraDevice].  */
    private lateinit var cameraDevice: CameraDevice

    /** An [ImageReader] that handles preview frame capture.  */
    private var previewReader: ImageReader? = null

    /** [CaptureRequest.Builder] for the camera preview  */
    lateinit var previewRequestBuilder: Builder

    /** The rotation in degrees of the camera sensor from the display.
     * Orientation of the camera as 0, 90, 180, or 270 degrees */
    private val sensorOrientation: Int by lazy {
        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    }

    private lateinit var orientationLiveData: OrientationLiveData

    private val helperJob = SupervisorJob()
    private val helperScope = CoroutineScope(Dispatchers.IO + helperJob)
    private val flowJob = SupervisorJob()
    private val flowScope = CoroutineScope(Dispatchers.Main + flowJob)
    var lastFrameAvailableAt = 0L

    private val onFrameAvailable = object : BaseCameraPreviewListener.OnFrameAvailable {
        override fun onFrameAvailable() {
            val currentTime = System.currentTimeMillis()
            val diff = currentTime - lastFrameAvailableAt
            if (diff >= CIRCULAR_ENCODER_FPS) saveMetaDataBuffer()
            lastFrameAvailableAt = currentTime
        }
    }

    private fun saveMetaDataBuffer() = helperScope.launch {
        iMetaDataHelper.saveMetaData(
            currentLocationMeta,
            currentSensorMeta
        )
    }

    fun setDelayVideoRecordingData(videoData: RecordingData?) {
        delayVideoRecordingData = videoData
    }

    /** A [Semaphore] to prevent the app from exiting before closing the camera.  */
    private val cameraOpenCloseLock = Semaphore(1)

    private val encoderCallback = object : CircularEncoderHandler.Callback {
        override fun drawFrame() {
            if (!fileSaveInProgress) {
                if (::circularEncoder.isInitialized) circularEncoder.frameAvailableSoon()
                else {
                    Firebase.crashlytics.log("::fun drawFrame circularEncoder is not Initialized")
                    Firebase.crashlytics.recordException(Exception("::fun drawFrame circularEncoder is not Initialized"))
                }
            }
        }

        override fun fileSaveComplete(status: Int, recordingData: RecordingData) {
            Timber.tag(tag).e("fileSaveComplete called")
            fileSaveInProgress = false
            helperScope.launch {
                iMetaDataHelper.setMetaData()
                iRecordingHelper.setLastRecordedAt(
                    System.currentTimeMillis(),
                    status,
                    recordingData.file,
                    recordingData.workFlowMetaData,
                    VideoData(userLocation, getFocalLength(), getSensorSize(), previewSize!!)
                )
                iRecordingHelper.recordingDelay(delayVideoRecordingData) { saveVideo(it) }
            }
            Timber.tag(tag).e("fileSaveComplete end")
        }

        override fun updateBufferStatus(duration: Long) {}
    }

    private fun getSensorSize(): SizeF? {
        return characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    }

    private fun getFocalLength(): Float? {
        return characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
    }

    private lateinit var circularEncoder: CircularEncoder
    private val circularEncoderHandler = CircularEncoderHandler(encoderCallback)

    /**
     * Setup and initialize camera
     *
     * @param textureView
     * @param baseCameraPreviewListener
     * @param imageAvailableListener
     */
    internal fun setupNayanCamera(
        textureView: AutoFitTextureView? = null,
        baseCameraPreviewListener: BaseCameraPreviewListener? = null,
        imageAvailableListener: ImageReader.OnImageAvailableListener? = null,
    ) {
        this.textureView = textureView
        this.baseCameraPreviewListener = baseCameraPreviewListener
        this.imageListener = imageAvailableListener

        helperScope.launch {
            startBackgroundThread()

            // When the screen is turned off and turned back on, the SurfaceTexture is already
            // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
            // a camera and start preview from here (otherwise, we wait until the surface is ready in
            // the SurfaceTextureListener).
            when {
                (textureView == null) -> openCamera()
                (textureView.isAvailable) -> openCamera()
                else -> textureView.surfaceTextureListener = surfaceTextureListener
            }
        }

        this.baseCameraPreviewListener?.onFrameAvailable = onFrameAvailable
    }

    /** Starts a background thread and its [Handler].  */
    private fun startBackgroundThread() {
        try {
            backgroundThread = HandlerThread("ImageListener")
            backgroundThread!!.start()
            backgroundHandler = Handler(backgroundThread!!.looper)
        } catch (e: InterruptedException) {
            Firebase.crashlytics.recordException(e)
            Timber.tag(tag).d(e)
        }
        isCamRunning = true
    }

    /** Stops the background thread and its [Handler].  */
    private fun stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread!!.quitSafely()
            try {
                backgroundThread!!.join()
                backgroundThread = null
                backgroundHandler = null
            } catch (e: InterruptedException) {
                Firebase.crashlytics.recordException(e)
                Timber.tag(tag).e(e)
            }
        }
        isCamRunning = false
    }

    private var isCamRunning: Boolean? = null

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a [ ].
     */
    private val surfaceTextureListener: TextureView.SurfaceTextureListener = object :
        TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            helperScope.launch { openCamera() }
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            helperScope.launch { configureTransform(width, height) }
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /**
     * Opens the camera specified by [CameraHelper.cameraId].
     */
    suspend fun openCamera() {
        val permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Firebase.crashlytics.log("Camera Permission not granted in camera helper #### cameraId ###  $cameraId")
            return
        }

        try {
            setUpCameraOutputs(cameraManager.cameraIdList)
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Firebase.crashlytics.log("Time out waiting to lock camera opening.")
                return
            }
            isCameraExternal = deviceModel.isKentCam()
            sharedPrefManager.setDefaultDashcam(false)
            flowScope.launch(Dispatchers.Main) {
                orientationLiveData = OrientationLiveData(context, characteristics).apply {
                    observeForever(orientationObserver)
                    onActive()
                }
                iRecordingHelper.getFileSaveProgressLD().observeForever(fileSaveObserver)
                if (::cameraId.isInitialized)
                    cameraManager.openCamera(cameraId, stateCallback, backgroundHandler!!)
                iRecordingHelper.getRecordingStateLD().collect(recordingStateObserver)
            }
        } catch (e: CameraAccessException) {
            Firebase.crashlytics.log("CameraExcessException found #### cameraId #### $cameraId on device #### $deviceModel")
            Firebase.crashlytics.recordException(e)
        } catch (ex: InterruptedException) {
            Firebase.crashlytics.log("Interrupted while trying to lock camera opening. -- ${ex.message}")
            Firebase.crashlytics.recordException(ex)
        } catch (nullEx: NullPointerException) {
            Firebase.crashlytics.log("NullPointerException #### cameraId ###  $cameraId")
            Firebase.crashlytics.recordException(nullEx)
        } catch (exx: Exception) {
            Firebase.crashlytics.log("Exception #### cameraId ###  $cameraId")
            Firebase.crashlytics.recordException(exx)
        }
    }

    private suspend fun setUpCameraOutputs(cameraIds: Array<String>) = withContext(Dispatchers.IO) {
        for (cameraId in cameraIds) {
            this@CameraHelper.cameraId = cameraId

            // We don't use a front facing camera in this sample.
            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT)
                continue

            CameraParamsHelper.setCameraParams(characteristics, sharedPrefManager)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: continue

            // Maximum video size supported by media recorder for the device
            val maxVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))

            // Find out if we need to swap dimension to get the preview size relative to sensor coordinate.
            val displayRotation = (defaultDisplay as Display).rotation
            val swappedDimensions = areDimensionsSwapped(displayRotation)

            val screenSize = getDisplaySmartSize(defaultDisplay as Display)
            val width = textureView?.width ?: screenSize.width
            val height = textureView?.height ?: screenSize.height
            var maxPreviewWidth = if (swappedDimensions) height else width
            var maxPreviewHeight = if (swappedDimensions) width else height

            val allowedSize = SIZE_1080P
            if (maxPreviewWidth > allowedSize.width) maxPreviewWidth = allowedSize.width
            if (maxPreviewHeight > allowedSize.height) maxPreviewHeight = allowedSize.height
            val maxPreviewSize = Size(maxPreviewWidth, maxPreviewHeight)

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            previewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture::class.java),
                maxPreviewSize,
                allowedSize,
                maxVideoSize
            )
            Timber.tag(tag)
                .d("######## Preview Size ${previewSize!!.width}*${previewSize!!.height} ########")

            // Create the reader for the preview frames.
            val imageFormat = if (isYuvFormatSupported(deviceModel.lowercase(Locale.getDefault())))
                ImageFormat.YUV_420_888 else ImageFormat.JPEG
            Timber.tag(tag).d("######## Image Format $imageFormat ########")
            previewReader = ImageReader.newInstance(
                previewSize!!.width, previewSize!!.height,
                imageFormat, /*maxImages*/ 3
            ).apply {
                imageListener?.let { setOnImageAvailableListener(it, backgroundHandler) }
            }

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            updateAspectRatio(previewSize!!)

            OpticalFlowPyrLK.initPoints(previewSize!!)
            baseCameraPreviewListener?.onPreviewSizeChosen(previewSize)

            // We've found a viable camera and finished setting up member variables,
            // so we don't need to iterate through other available cameras.
            return@withContext
        }
    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param displayRotation The current rotation of the display
     *
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }

            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }

            else -> Timber.tag(tag).d("Display rotation is invalid: $displayRotation")
        }
        return swappedDimensions
    }

    private suspend fun updateAspectRatio(screenSize: Size) = withContext(Dispatchers.Main) {
        textureView?.let { textureView ->
            // We fit the aspect ratio of TextureView to the size of preview we picked.
            val orientation: Int = context.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                textureView.setAspectRatio(screenSize.width, screenSize.height)
            else textureView.setAspectRatio(screenSize.height, screenSize.width)

            configureTransform(textureView.width, textureView.height)
        }
    }

    /** [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.  */
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cd: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release()
            cameraDevice = cd
            createCameraPreviewSession()
        }

        override fun onDisconnected(cd: CameraDevice) {
            cameraOpenCloseLock.release()
            cd.close()
        }

        override fun onError(cd: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cd.close()
            when (error) {
                ERROR_CAMERA_DEVICE -> Timber.tag(tag)
                    .e("onError id: ${cd.id}, ERROR_CAMERA_DEVICE = $error")

                ERROR_CAMERA_DISABLED -> Timber.tag(tag)
                    .e("onError id: ${cd.id}, ERROR_CAMERA_DISABLED = $error")

                ERROR_CAMERA_IN_USE -> Timber.tag(tag)
                    .e("onError id: ${cd.id}, ERROR_CAMERA_IN_USE = $error")

                ERROR_CAMERA_SERVICE -> Timber.tag(tag)
                    .e("onError id: ${cd.id}, ERROR_CAMERA_SERVICE = $error")

                ERROR_MAX_CAMERAS_IN_USE -> Timber.tag(tag)
                    .e("onError id: ${cd.id}, ERROR_MAX_CAMERAS_IN_USE = $error")

                else -> Timber.tag(tag).e("onError id: ${cd.id}, code = $error")
            }
            Firebase.crashlytics.recordException(Exception("CameraHelper stateCallback onError: $error"))
        }
    }

    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`. This method should be
     * called after the camera preview size is determined in setUpCameraOutputs and also the size of
     * `mTextureView` is fixed.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private suspend fun configureTransform(
        viewWidth: Int?,
        viewHeight: Int?
    ) = withContext(Dispatchers.Main) {
        if (null == textureView || null == previewSize || null == viewHeight || null == viewWidth) return@withContext

        val actualPreview = previewSize!!
        val rotation = (defaultDisplay as Display).rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect =
            RectF(0f, 0f, actualPreview.height.toFloat(), actualPreview.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                viewHeight.toFloat() / actualPreview.height,
                viewWidth.toFloat() / actualPreview.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView?.setTransform(matrix)
    }

    private fun getCircularEncoder(previewSize: Size, displayRotation: Int): CircularEncoder {
        return CircularEncoder(
            previewSize.width, previewSize.height, 6000000,
            CIRCULAR_ENCODER_FPS, CIRCULAR_ENCODER_BUFFER_LENGTH_IN_SEC,
            displayRotation, circularEncoderHandler
        )
    }

    /** Creates a new [CameraCaptureSession] for camera preview.  */
    private fun createCameraPreviewSession() {
        if (previewSize == null) return
        try {
            val surfaceList = mutableListOf<Surface>()
            val displayRotation = (defaultDisplay as Display).rotation
            try {
                circularEncoder = getCircularEncoder(previewSize!!, displayRotation)
            } catch (ex: Exception) {
                Timber.tag(tag).e(ex)
                Firebase.crashlytics.recordException(ex)
            }

            if (::cameraDevice.isInitialized) {
                previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                // We set up a CaptureRequest.Builder with the output Surface.
                textureView?.let { textureView ->
                    val surfaceTexture = textureView.surfaceTexture
                    if (surfaceTexture != null) {
                        // We configure the size of default buffer to be the size of camera preview we want.
                        surfaceTexture.setDefaultBufferSize(
                            previewSize!!.width,
                            previewSize!!.height
                        )
                        // This is the output Surface we need to start preview.
                        val surface = Surface(surfaceTexture)
                        previewRequestBuilder.addTarget(surface)
                        surfaceList.add(surface)
                    }
                }

                Timber.tag(tag)
                    .i("Opening camera preview: ${previewSize!!.width} x ${previewSize!!.height}")

                previewReader?.surface?.let {
                    previewRequestBuilder.addTarget(it)
                    surfaceList.add(it)
                }

                if (::circularEncoder.isInitialized) {
                    previewRequestBuilder.addTarget(circularEncoder.inputSurface)
                    surfaceList.add(circularEncoder.inputSurface)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val outputConfigs = mutableListOf<OutputConfiguration>()
                    surfaceList.map { target ->
                        try {
                            val outputConfig = OutputConfiguration(target)
                            outputConfigs.add(outputConfig)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Timber.tag(tag).e("Error creating OutputConfiguration: ${e.message}")
                        }
                    }

                    // Check if any errors occurred during OutputConfiguration creation
                    if (outputConfigs.isEmpty()) {
                        // Log an error and handle the situation appropriately
                        Timber.tag(tag).e("No valid OutputConfigurations created")
                        return
                    }
                    cameraDevice.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            outputConfigs,
                            HandlerExecutor(backgroundHandler!!.looper),
                            captureSessionCallback
                        )
                    )
                } else {
                    cameraDevice.createCaptureSession(
                        surfaceList,
                        captureSessionCallback,
                        backgroundHandler
                    )
                }
            }
        } catch (e: CameraAccessException) {
            Firebase.crashlytics.recordException(e)
            Timber.tag(tag).e(e, "Exception! ${e.message}")
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Timber.tag(tag).e(e, "Exception! ${e.message}")
        }
    }

    private val captureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            if (::nightModeManagerImpl.isInitialized) nightModeManagerImpl.removeNightMode()
        }

        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            // The camera is already closed
            if (::cameraDevice.isInitialized.not()) return
            // When the session is ready, we start displaying the preview.
            captureSession = cameraCaptureSession
            try {
                // Auto focus should be continuous for camera preview.
                previewRequestBuilder.apply {
                    set(CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    adaptFpsRange()?.let { set(CONTROL_AE_TARGET_FPS_RANGE, it) }
                }

                // Finally, we start displaying the camera preview.
                captureSession.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    captureCallback,
                    backgroundHandler
                )

                if (baseCameraPreviewListener is CameraPreviewListener)
                    initializeNightMode()
                isCameraRunning = true
            } catch (e: CameraAccessException) {
                Firebase.crashlytics.recordException(e)
                Timber.tag(tag).e(e, "Exception!")
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                Timber.tag(tag).e(e, "Exception!")
            }
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            Firebase.crashlytics.recordException(Exception("onConfigureFailed: "))
            Timber.tag(tag).e("onConfigureFailed")
        }
    }

    private fun initializeNightMode() = helperScope.launch {
        nightModeManagerImpl = NightModeManagerImpl(
            cameraUtils.getExposureCompensationRange(characteristics),
            nightModeHandler,
            nightModeConstraintSelector.getSession(),
            updateExposure = { it?.let { it1 -> requestChangeExposure(it1) } }
        )
        nightModeManagerImpl.checkForNightMode()
    }

    private fun requestChangeExposure(exposureValue: Int) {
        try {
            if (::captureSession.isInitialized && captureSession.isReprocessable) {
                previewRequestBuilder.apply {
                    set(CONTROL_AE_EXPOSURE_COMPENSATION, exposureValue)
                }
                captureSession.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    captureCallback, nightModeHandler
                )
            }
        } catch (e: Exception) {
            if (::nightModeManagerImpl.isInitialized) nightModeManagerImpl.removeNightMode()
            Firebase.crashlytics.recordException(e)
            Timber.tag(tag).e(e, "Exception!")
        }
    }

    fun resetNightMode() = helperScope.launch {
        if (::nightModeManagerImpl.isInitialized) nightModeManagerImpl.reset()
    }

    fun updateISOExposureBuffer() = helperScope.launch {
        if (::nightModeManagerImpl.isInitialized) nightModeManagerImpl.updateISOExposureBuffer()
    }

    /**
     * Restarting the camera
     *
     * @param textureView
     * @param baseCameraPreviewListener
     * @param imageAvailableListener
     */
    private fun reStartCamera(
        textureView: AutoFitTextureView?,
        baseCameraPreviewListener: BaseCameraPreviewListener? = null,
        imageAvailableListener: ImageReader.OnImageAvailableListener? = null,
    ) = helperScope.launch {
        closeCamera()
        setupNayanCamera(
            textureView = textureView,
            baseCameraPreviewListener = baseCameraPreviewListener,
            imageAvailableListener = imageAvailableListener
        )
    }

    private val orientationObserver = Observer<Int> { orientation ->
        helperScope.launch {
            if (isInCorrectScreenOrientation.not()) {
                val state =
                    RecordingState(ORIENTATION_ERROR, context.getString(R.string.tilt_warning))
                _recordingState.emit(state)
            }

            if (isRecordingVideo.not() && isCameraRunning) {
                if (::circularEncoder.isInitialized) circularEncoder.setOrientationHint(orientation)
                baseCameraPreviewListener?.setScreenRotation(orientation)
                previewSize?.let { updateAspectRatio(it) }
                    ?: run { configureTransform(textureView?.width, textureView?.height) }
            }
        }
    }

    private val recordingStateObserver = FlowCollector<RecordingState?> { recordingState ->
        _recordingState.emit(recordingState)
    }

    private val fileSaveObserver = Observer<Boolean> { fileSaveInProgress = it }

    /** Closes the current [CameraDevice].  */
    suspend fun closeCamera() = withContext(Dispatchers.IO) {
        if (isCameraRunning) {
            Timber.tag(tag).d("Camera is running stopping camera")
            try {
                isCameraRunning = false
                circularEncoderHandler.removeCallbacksAndMessages(null)
                cameraOpenCloseLock.acquire()
                if (::nightModeManagerImpl.isInitialized) nightModeManagerImpl.removeNightMode()
                if (::captureSession.isInitialized) {
                    // Stop repeating requests
                    if (captureSession.isReprocessable) captureSession.stopRepeating()
                    captureSession.close()
                }
                if (::cameraDevice.isInitialized) cameraDevice.close()
                if (null != previewReader) {
                    previewReader?.close()
                    previewReader = null
                }
                if (::circularEncoder.isInitialized) circularEncoder.shutdown()
                else {
                    Firebase.crashlytics.log("::fun closeCamera circularEncoder is not Initialized")
                    Firebase.crashlytics.recordException(Exception("late init property circularEncoder has not been initialized"))
                }
                withContext(Dispatchers.Main) {
                    if (::orientationLiveData.isInitialized) {
                        orientationLiveData.onInactive()
                        orientationLiveData.removeObserver(orientationObserver)
                    }
                    iRecordingHelper.getFileSaveProgressLD().removeObserver(fileSaveObserver)
                    if (flowJob.isActive) flowJob.cancel()
                }
            } catch (e: InterruptedException) {
                Firebase.crashlytics.log("Interrupted while trying to lock camera closing.")
                Firebase.crashlytics.recordException(e)
            } finally {
                cameraOpenCloseLock.release()
            }
        }
        stopBackgroundThread()
    }

    private val captureCallback: CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
            ) {
                if (isCameraRunning) circularEncoderHandler.sendEmptyMessage(MSG_FRAME_AVAILABLE)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (isCameraRunning) circularEncoderHandler.sendEmptyMessage(MSG_FRAME_AVAILABLE)
            }
        }

    fun saveVideo(recordingData: RecordingData) = helperScope.launch {
        delay(SAVING_VIDEO_DELAY)
        if (::circularEncoder.isInitialized) {
            fileSaveInProgress = true
            circularEncoder.saveVideo(recordingData)
        } else {
            Firebase.crashlytics.log("::fun saveVideo circularEncoder is not Initialized")
            Firebase.crashlytics.recordException(Exception("late init property circularEncoder has not been initialized"))
        }
    }

    fun onAIScanning() = helperScope.launch {
        _recordingState.emit(RecordingState(AI_SCANNING, ""))
    }

    fun onWithInSurgeLocationStatus() = helperScope.launch {
        _recordingState.emit(RecordingState(NOT_IN_SURGE, ""))
    }

    private fun adaptFpsRange(): Range<Int>? {
        val expectedFps = CIRCULAR_ENCODER_FPS

        //[[15, 15], [15, 20], [20, 20], [24, 24], [8, 30], [10, 30], [15, 30], [30, 30]]
        val fpsRanges =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)

        var closestRange: Range<Int>? = null
        fpsRanges?.let { ranges ->
            var measure = if (fpsRanges.isNotEmpty())
                abs(ranges[0].lower - expectedFps) + abs(ranges[0].upper - expectedFps) else null
            ranges.map {
                if (it.lower <= expectedFps && it.upper >= expectedFps) {
                    val curMeasure = abs(it.lower - expectedFps) + abs(it.upper - expectedFps)
                    if (measure != null && curMeasure < measure!!) {
                        closestRange = it
                        measure = curMeasure
                    }
                }
            }
        }

        return closestRange
    }
}