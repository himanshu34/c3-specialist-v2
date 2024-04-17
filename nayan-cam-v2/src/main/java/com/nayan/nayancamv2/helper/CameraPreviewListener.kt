package com.nayan.nayancamv2.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.ImageReader
import android.util.Size
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.ai.AIWorkflowManager
import com.nayan.nayancamv2.getCurrentDate
import com.nayan.nayancamv2.getCurrentEnabledWorkflows
import com.nayan.nayancamv2.helper.GlobalParams.appHasLocationUpdates
import com.nayan.nayancamv2.helper.GlobalParams.hasValidSpeed
import com.nayan.nayancamv2.helper.GlobalParams.ifUserLocationFallsWithInSurge
import com.nayan.nayancamv2.helper.GlobalParams.ifUserRecordingOnBlackLines
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.GlobalParams.isNightModeActive
import com.nayan.nayancamv2.helper.GlobalParams.isProcessingFrame
import com.nayan.nayancamv2.helper.GlobalParams.isRecordingVideo
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.util.Constants.MAX_CONCURRENT_PROCESSING
import com.nayan.nayancamv2.util.Constants.OPTICAL_FLOW_THRESHOLD
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.LAST_CAMERA_FRAME_THRESHOLD
import com.nayan.nayancamv2.util.SamplingRate.SAMPLING_RATE
import com.nayan.nayancamv2.util.SamplingRate.SAMPLING_RATE_LITE
import com.nayan.nayancamv2.util.YuvToRgbConverter
import com.nayan.nayancamv2.util.bitmapToMat
import com.nayan.nayancamv2.util.fromBase64
import com.nayan.nayancamv2.util.jpegToBitmap
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * This class overrides the onImageAvailable() from ImageReader and responsible for sending the
 * image to different AI processor for process
 *
 * @property mContext
 * @property imageProcessor
 * @property sharedPrefManager
 * @property nayanCamModuleInteractor
 */
class CameraPreviewListener(
    val mContext: Context,
    private val imageProcessor: AIWorkflowManager,
    private val sharedPrefManager: SharedPrefManager,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor
) : BaseCameraPreviewListener() {

    private var motionlessFrameCount = 0

    private var previewWidth = 0
    private var previewHeight = 0

    private var crPassword: String? = null
    private var todayDate: String? = null
    private var currentCRMode: Boolean? = null

    private var mSamplingRate: Int = SAMPLING_RATE // Time gap between to 2 AI execution in seconds
    private var lastAIProcessTime = 0L
    private var isManualRecording = false
    private val converter by lazy { YuvToRgbConverter(mContext) }
    private val isAIMode by lazy { nayanCamModuleInteractor.isAIMode() }
    private val isCRMode by lazy { nayanCamModuleInteractor.isCRMode() }
    private val imageProcessingSemaphore = Semaphore(MAX_CONCURRENT_PROCESSING)

    override fun scheduleSampling() {
        mSamplingRate = if (sharedPrefManager.isLITEMode() || sharedPrefManager.isForcedLITEMode())
            SAMPLING_RATE_LITE else SAMPLING_RATE
    }

    private var mPrevGray: Mat? = null

    @OptIn(DelicateCoroutinesApi::class)
    override fun onImageAvailable(imageReader: ImageReader?) {
        Timber.e("**********************************[onImageAvailable] isProcessingFrame: $isProcessingFrame *************************")
        GlobalScope.launch {
            // Acquire a permit from the semaphore, allowing a maximum concurrent threads
            imageProcessingSemaphore.withPermit {
                val image = imageReader?.acquireLatestImage() ?: run { return@launch }
                if (previewWidth == 0 || previewHeight == 0) {
                    image.close()
                    return@launch
                }

                if (isProcessingFrame || isInCorrectScreenOrientation.not()
                    || appHasLocationUpdates.not() || hasValidSpeed.not()
                    || ifUserLocationFallsWithInSurge.not() || ifUserRecordingOnBlackLines
                ) {
                    image.close()
                    return@launch
                } else {
                    val currentTimeMillis = System.currentTimeMillis()
                    if (isInCorrectScreenOrientation) onFrameAvailable?.onFrameAvailable()
                    sharedPrefManager.setLastTimeImageAvailableCalled(currentTimeMillis)
                    val diffGap = currentTimeMillis - lastAIProcessTime
                    if ((isRecordingVideo || isProcessingFrame)
                        && TimeUnit.MILLISECONDS.toSeconds(diffGap) > LAST_CAMERA_FRAME_THRESHOLD
                    ) {
                        isRecordingVideo = false
                        isProcessingFrame = false
                        image.close()
                        return@launch
                    }

                    try {
                        Timber.e("**********************************[onImageAvailable] StartProcessingImage *************************")
                        val nextScan = TimeUnit.MILLISECONDS.toSeconds(diffGap)
                        val allowSamplingRate = (nextScan >= mSamplingRate)
                        if (allowSamplingRate) {
                            if (nayanCamModuleInteractor.isSurveyor().not()
                                && crMode().not() && isAIMode.not()
                            ) return@launch
                            isProcessingFrame = true
                            Timber.e("############################")
                            Timber.e("isProcessingFrame: $isProcessingFrame")
                            Timber.e("############################")

                            withContext(Dispatchers.IO) {
                                return@withContext when (image.format) {
                                    ImageFormat.YUV_420_888 -> converter.yuvToRgb(image)
                                    else -> jpegToBitmap(image) // Convert JPEG to Bitmap
                                }
                            }?.let {
                                if (isNightModeActive) processAI(it)
                                else processOpticalFlow(it)
                            } ?: run { isProcessingFrame = false }
                        }
                    } catch (ex: Exception) {
                        Firebase.crashlytics.recordException(ex)
                        Timber.e(ex, "Exception!")
                    } finally {
                        Timber.e("************** finally image close called ******************")
                        image.close()
                    }
                }
            }
        }
    }

    override suspend fun processOpticalFlow(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val validVectorDisplacement = calculateOpticalFlow(bitmap)
        if (validVectorDisplacement) handleMotionDetected(bitmap)
        else handleMotionless()
    }

    private suspend fun calculateOpticalFlow(
        bitmap: Bitmap
    ): Boolean = withContext(Dispatchers.IO) {
        val mCurrentGray = bitmapToMat(bitmap)
        val validVectorDisplacement =
            mPrevGray != null && OpticalFlowPyrLK.sparseFlow(mCurrentGray, mPrevGray!!)
        mPrevGray = mCurrentGray

        validVectorDisplacement
    }

    private suspend fun handleMotionDetected(bitmap: Bitmap) {
        if (motionlessFrameCount >= OPTICAL_FLOW_THRESHOLD)
            onOpticalFlowMotionDetected?.onMotionContentDetected()
        motionlessFrameCount = 0
        Timber.e("############################")
        Timber.e("processOpticalFlow motionlessFrameCount: $motionlessFrameCount")
        Timber.e("############################")
        processAI(bitmap)
    }

    private fun handleMotionless() {
        motionlessFrameCount += 1
        Timber.e("############################")
        Timber.e("processOpticalFlow motionlessFrameCount: $motionlessFrameCount")
        Timber.e("############################")
        isProcessingFrame = false
        if (motionlessFrameCount >= OPTICAL_FLOW_THRESHOLD)
            onOpticalFlowMotionDetected?.onMotionlessContentDetected()
    }

    override suspend fun processAI(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        lastAIProcessTime = System.currentTimeMillis()
        val location = userLocation?.let { LatLng(it.latitude, it.longitude) } ?: run { null }
        val allWorkFlowList = sharedPrefManager.getCameraAIWorkFlows()
        val currentEnabledWorkflows = getCurrentEnabledWorkflows(allWorkFlowList, location)
        val workflowList = currentEnabledWorkflows.filter { it.workflow_IsDroneEnabled.not() }
        imageProcessor.startProcessing(bitmap, isAIMode, workflowList)
    }

    override fun setScreenRotation(orientation: Int) {
        imageProcessor.setScreenRotation(orientation)
    }

    override fun setIsManualRecording(isManualRecording: Boolean) {
        this.isManualRecording = isManualRecording
    }

    override fun onPreviewSizeChosen(size: Size?) {
        size?.let {
            previewWidth = it.width
            previewHeight = it.height
        }
    }

    private suspend fun crMode(): Boolean = withContext(Dispatchers.IO) {
        if (currentCRMode == null) currentCRMode = isCRMode
        else {
            try {
                if (todayDate == null) todayDate = getCurrentDate()
                if (crPassword == null)
                    crPassword = nayanCamModuleInteractor.getCRModePassword().fromBase64()

                if (crPassword?.contains(todayDate!!) == false) {
                    currentCRMode = false
                    nayanCamModuleInteractor.setCRModeConfig(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Firebase.crashlytics.recordException(e)
            }
        }

        return@withContext currentCRMode!!
    }
}