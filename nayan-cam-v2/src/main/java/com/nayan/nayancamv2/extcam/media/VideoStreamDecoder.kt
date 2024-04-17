package com.nayan.nayancamv2.extcam.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Size
import android.view.Surface
import androidx.lifecycle.LiveData
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.encoder.CircularEncoder
import com.nayan.nayancamv2.encoder.CircularEncoderHandler
import com.nayan.nayancamv2.encoder.CircularEncoderHandler.Companion.MSG_FRAME_AVAILABLE
import com.nayan.nayancamv2.getColorFormatSurfaceFromDeviceName
import com.nayan.nayancamv2.helper.IMetaDataHelper
import com.nayan.nayancamv2.helper.IRecordingHelper
import com.nayan.nayancamv2.model.RecordingData
import com.nayan.nayancamv2.model.SensorMeta
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.model.UserLocationMeta
import com.nayan.nayancamv2.model.VideoData
import com.nayan.nayancamv2.util.Constants.SAVING_VIDEO_DELAY
import dji.common.product.Model
import dji.log.DJILog
import dji.midware.R
import dji.midware.data.model.P3.DataCameraGetPushStateInfo
import dji.sdk.codec.DJICodecManager.YuvDataCallback
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue

class VideoStreamDecoder(
    val iRecordingHelper: IRecordingHelper,
    private val iMetaDataHelper: IMetaDataHelper,
    private val deviceModel: String,
    private val shouldSendToEncoder: Boolean
) : NativeHelper.NativeDataListener {

    var fileSaveInProgress = false
    var shouldGetFrameData = false
    private val handlerThreadNew: HandlerThread
    private val handlerNew: Handler
    private val DEBUG = false
    private val frameQueue: Queue<DJIFrame>?
    private var dataHandlerThread: HandlerThread? = null
    private var dataHandler: Handler? = null
    private var context: Context? = null
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var frameIndex = -1
    private var currentTime: Long = 0
    var width = 0
    var height = 0
    private var hasIFrameInQueue = false
    private var bufferInfo = MediaCodec.BufferInfo()
    private var bufferChangedQueue = LinkedList<Long>()
    var currentLocationMeta: UserLocationMeta? = null
    var currentSensorMeta: SensorMeta? = null
    private lateinit var mCircularEncoder: CircularEncoder

    private val streamDecoderJob = SupervisorJob()
    private val streamDecoderScope = CoroutineScope(Dispatchers.IO + streamDecoderJob)

    private val encoderCallback = object : CircularEncoderHandler.Callback {
        override fun drawFrame() {
            if (!fileSaveInProgress) {
                if (::mCircularEncoder.isInitialized) mCircularEncoder.frameAvailableSoon()
                else {
                    Firebase.crashlytics.log("::fun drawFrame circularEncoder is not Initialized")
                    Firebase.crashlytics.recordException(Exception("late init property circularEncoder has not been initialized"))
                }
            }
        }

        override fun fileSaveComplete(status: Int, recordingData: RecordingData) {
            Timber.e("fileSaveComplete called")
            fileSaveInProgress = false
            streamDecoderScope.launch {
                iMetaDataHelper.setMetaData()
                iRecordingHelper.setLastRecordedAt(
                    System.currentTimeMillis(),
                    status,
                    recordingData.file,
                    recordingData.workFlowMetaData,
                    VideoData(userLocation, 0.0F, null, Size(width, height))
                )
                iRecordingHelper.recordingDelay(delayVideoRecordingData) { saveVideo(it) }
            }
            Timber.tag(TAG).e("fileSaveComplete end")
        }

        override fun updateBufferStatus(duration: Long) {}
    }


    private val circularEncoderHandler = CircularEncoderHandler(encoderCallback)
    private var yuvDataCallback: YuvDataCallback? = null
    fun setYuvDataListener(yuvDataCallback: YuvDataCallback?) {
        this.yuvDataCallback = yuvDataCallback
    }

    /**
     * A data structure for containing the frames.
     */
    private class DJIFrame(
        var videoBuffer: ByteArray,
        var size: Int,
        var pts: Long,
        var incomingTimeMs: Long,
        var isKeyFrame: Boolean,
        var frameNum: Int,
        var frameIndex: Long,
        var width: Int,
        var height: Int
    ) {
        var fedIntoCodecTime: Long = 0
        val queueDelay: Long
            get() = fedIntoCodecTime - incomingTimeMs
    }

    private fun logd(tag: String, log: String) {
        if (!DEBUG) {
            return
        }
        Timber.tag(tag).d(log)
    }

    private fun loge(tag: String, log: String) {
        if (!DEBUG) {
            return
        }
        Timber.tag(tag).e(log)
    }

    private fun logd(log: String) {
        logd(TAG, log)
    }

    private fun loge(log: String) {
        loge(TAG, log)
    }

    init {
        frameQueue = ArrayBlockingQueue(BUF_QUEUE_SIZE)
        startDataHandler()
        handlerThreadNew = HandlerThread("native parser thread")
        handlerThreadNew.start()
        handlerNew = Handler(handlerThreadNew.looper) { msg ->
            val buf = msg.obj as ByteArray
            NativeHelper.getInstance().parse(buf, msg.arg1)
            false
        }
    }

    /**
     * Initialize the decoder
     *
     * @param context The application context
     * @param surface The displaying surface for the video stream. What should be noted here is that the hardware decoder would not output
     * any yuv data if a surface is configured into, which mean that if you want the yuv frames, you
     * should set "null" surface when calling the "configure" method of MediaCodec.
     */
    fun init(context: Context?, surface: Surface?) {
        this.context = context
        this.surface = surface
        NativeHelper.getInstance().addDataListener(this)
        if (dataHandler != null && !dataHandler!!.hasMessages(MSG_INIT_CODEC)) {
            dataHandler!!.sendEmptyMessage(MSG_INIT_CODEC)
        }
    }

    fun parse(buf: ByteArray?, size: Int) {
        val message = handlerNew.obtainMessage()
        message.obj = buf
        message.arg1 = size
        handlerNew.sendMessage(message)
    }

    @Throws(IOException::class)
    private fun getDefaultKeyFrame(width: Int): ByteArray? {
        var iframeId = 0
        val product = DJISDKManager.getInstance().product
        if (product == null || product.model == null) {
            return null
        }

        if (shouldGetFrameData) iframeId = getIframeRawId(product.model, width)

        if (iframeId >= 0) {
            val inputStream = context!!.resources.openRawResource(iframeId)
            val length = inputStream.available()
            val buffer = ByteArray(length)
            inputStream.read(buffer)
            inputStream.close()
            return buffer
        }

        return null
    }

    private fun getIframeRawId(pModel: Model?, width: Int): Int {
        var iframeId: Int = R.raw.iframe_1280x720_ins
        when (pModel) {
            Model.PHANTOM_3_ADVANCED, Model.PHANTOM_3_STANDARD -> iframeId =
                when (width) {
                    960 -> {
                        //for photo mode, 960x720, GDR
                        R.raw.iframe_960x720_3s
                    }

                    640 -> {
                        R.raw.iframe_640x368_osmo_gop
                    }

                    else -> {
                        //for record mode, 1280x720, GDR
                        R.raw.iframe_1280x720_3s
                    }
                }

            Model.INSPIRE_1 -> {
                val cameraType = DataCameraGetPushStateInfo.getInstance().cameraType
                if (cameraType == DataCameraGetPushStateInfo.CameraType.DJICameraTypeCV600) { //ZENMUSE_Z3
                    iframeId = when (width) {
                        960 -> {
                            //for photo mode, 960x720, GDR
                            R.raw.iframe_960x720_3s
                        }

                        640 -> {
                            R.raw.iframe_640x368_osmo_gop
                        }

                        else -> {
                            //for record mode, 1280x720, GDR
                            R.raw.iframe_1280x720_3s
                        }
                    }
                }
            }

            Model.Phantom_3_4K -> iframeId = when (width) {
                640 ->                         //for P3-4K with resolution 640*480
                    R.raw.iframe_640x480

                848 ->                         //for P3-4K with resolution 848*480
                    R.raw.iframe_848x480

                896 -> R.raw.iframe_896x480
                960 ->                         //DJILog.i(TAG, "Selected Iframe=iframe_960x720_3s");
                    //for photo mode, 960x720, GDR
                    R.raw.iframe_960x720_3s

                else -> R.raw.iframe_1280x720_3s
            }

            Model.OSMO -> iframeId = if (DataCameraGetPushStateInfo.getInstance().verstion >= 4) {
                -1
            } else {
                R.raw.iframe_1280x720_ins
            }

            Model.OSMO_PLUS -> iframeId = when (width) {
                960 -> {
                    R.raw.iframe_960x720_osmo_gop
                }

                1280 -> {
                    //for record mode, 1280x720, GDR
                    //DJILog.i(TAG, "Selected Iframe=iframe_1280x720_3s");
                    //                    iframeId = R.raw.iframe_1280x720_3s;
                    R.raw.iframe_1280x720_osmo_gop
                }

                640 -> {
                    R.raw.iframe_640x368_osmo_gop
                }

                else -> {
                    R.raw.iframe_1280x720_3s
                }
            }

            Model.OSMO_PRO, Model.OSMO_RAW -> iframeId = R.raw.iframe_1280x720_ins
            Model.MAVIC_PRO, Model.MAVIC_2 -> iframeId =
                if ((DJISDKManager.getInstance().product as Aircraft).mobileRemoteController != null) {
                    R.raw.iframe_1280x720_wm220
                } else {
                    -1
                }

            Model.Spark -> iframeId = when (width) {
                1280 -> R.raw.iframe_1280x720_p4
                1024 -> R.raw.iframe_1024x768_wm100
                else -> R.raw.iframe_1280x720_p4
            }

            Model.MAVIC_AIR -> iframeId = when (height) {
                960 -> R.raw.iframe_1280x960_wm230
                720 -> R.raw.iframe_1280x720_wm230
                else -> R.raw.iframe_1280x720_wm230
            }

            Model.PHANTOM_4 -> iframeId = R.raw.iframe_1280x720_p4
            Model.PHANTOM_4_PRO, Model.PHANTOM_4_ADVANCED -> iframeId =
                when (width) {
                    1280 -> R.raw.iframe_p4p_720_16x9
                    960 -> R.raw.iframe_p4p_720_4x3
                    1088 -> R.raw.iframe_p4p_720_3x2
                    1344 -> R.raw.iframe_p4p_1344x720
                    1440 -> R.raw.iframe_1440x1088_wm620
                    1920 -> when (height) {
                        1024 -> R.raw.iframe_1920x1024_wm620
                        800 -> R.raw.iframe_1920x800_wm620
                        else -> R.raw.iframe_1920x1088_wm620
                    }

                    else -> R.raw.iframe_p4p_720_16x9
                }

            Model.MATRICE_600, Model.MATRICE_600_PRO -> {
                val cameraType = DataCameraGetPushStateInfo.getInstance().cameraType
                iframeId = if (width == 720 && height == 480) {
                    R.raw.iframe_720x480_m600
                } else if (width == 720 && height == 576) {
                    R.raw.iframe_720x576_m600
                } else {
                    if (width == 1280 && height == 720) {
                        when (cameraType) {
                            DataCameraGetPushStateInfo.CameraType.DJICameraTypeGD600 -> {
                                R.raw.iframe_gd600_1280x720
                            }

                            DataCameraGetPushStateInfo.CameraType.DJICameraTypeCV600 -> {
                                R.raw.iframe_1280x720_osmo_gop
                            }

                            DataCameraGetPushStateInfo.CameraType.DJICameraTypeFC350 -> {
                                R.raw.iframe_1280x720_ins
                            }

                            else -> {
                                R.raw.iframe_1280x720_m600
                            }
                        }
                    } else if (width == 1920 && (height == 1080 || height == 1088)) {
                        R.raw.iframe_1920x1080_m600
                    } else if (width == 1080 && height == 720) {
                        R.raw.iframe_1080x720_gd600
                    } else if (width == 960 && height == 720) {
                        R.raw.iframe_960x720_3s
                    } else {
                        -1
                    }
                }
            }

            Model.MATRICE_100 -> {
                val cameraType = DataCameraGetPushStateInfo.getInstance().cameraType
                iframeId =
                    if (cameraType == DataCameraGetPushStateInfo.CameraType.DJICameraTypeGD600) {
                        if (width == 1280 && height == 720) {
                            R.raw.iframe_gd600_1280x720
                        } else {
                            R.raw.iframe_1080x720_gd600
                        }
                    } else {
                        R.raw.iframe_1280x720_ins
                    }
            }

            Model.MATRICE_200, Model.MATRICE_210, Model.MATRICE_210_RTK, Model.INSPIRE_2 -> {
                val cameraType = DataCameraGetPushStateInfo.getInstance().getCameraType(0)
                if (cameraType == DataCameraGetPushStateInfo.CameraType.DJICameraTypeGD600) {
                    iframeId = R.raw.iframe_1080x720_gd600
                } else {
                    if (width == 640 && height == 368) {
                        DJILog.i(TAG, "Selected Iframe=iframe_640x368_wm620")
                        iframeId = R.raw.iframe_640x368_wm620
                    }
                    if (width == 608 && height == 448) {
                        DJILog.i(TAG, "Selected Iframe=iframe_608x448_wm620")
                        iframeId = R.raw.iframe_608x448_wm620
                    } else if (width == 720 && height == 480) {
                        DJILog.i(TAG, "Selected Iframe=iframe_720x480_wm620")
                        iframeId = R.raw.iframe_720x480_wm620
                    } else if (width == 1280 && height == 720) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1280x720_wm620")
                        iframeId = R.raw.iframe_1280x720_wm620
                    } else if (width == 1080 && height == 720) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1080x720_wm620")
                        iframeId = R.raw.iframe_1080x720_wm620
                    } else if (width == 1088 && height == 720) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1088x720_wm620")
                        iframeId = R.raw.iframe_1088x720_wm620
                    } else if (width == 960 && height == 720) {
                        DJILog.i(TAG, "Selected Iframe=iframe_960x720_wm620")
                        iframeId = R.raw.iframe_960x720_wm620
                    } else if (width == 1360 && height == 720) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1360x720_wm620")
                        iframeId = R.raw.iframe_1360x720_wm620
                    } else if (width == 1344 && height == 720) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1344x720_wm620")
                        iframeId = R.raw.iframe_1344x720_wm620
                    } else if (width == 1440 && height == 1088) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1440x1088_wm620")
                        iframeId = R.raw.iframe_1440x1088_wm620
                    } else if (width == 1632 && height == 1080) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1632x1080_wm620")
                        iframeId = R.raw.iframe_1632x1080_wm620
                    } else if (width == 1760 && height == 720) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1760x720_wm620")
                        iframeId = R.raw.iframe_1760x720_wm620
                    } else if (width == 1920 && height == 800) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1920x800_wm620")
                        iframeId = R.raw.iframe_1920x800_wm620
                    } else if (width == 1920 && height == 1024) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1920x1024_wm620")
                        iframeId = R.raw.iframe_1920x1024_wm620
                    } else if (width == 1920 && height == 1088) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1920x1080_wm620")
                        iframeId = R.raw.iframe_1920x1088_wm620
                    } else if (width == 1920 && height == 1440) {
                        DJILog.i(TAG, "Selected Iframe=iframe_1920x1440_wm620")
                        iframeId = R.raw.iframe_1920x1440_wm620
                    }
                }
            }

            Model.PHANTOM_4_PRO_V2, Model.PHANTOM_4_RTK -> {
                iframeId = -1
            }

            Model.MAVIC_AIR_2 -> {}
            Model.MAVIC_2_ENTERPRISE, Model.MAVIC_2_ENTERPRISE_DUAL -> iframeId =
                R.raw.iframe_1280x720_wm220

            Model.MAVIC_2_ENTERPRISE_ADVANCED -> {}
            else -> iframeId = R.raw.iframe_1280x720_ins
        }
        return iframeId
    }


    /**
     * Initialize the hardware decoder.
     */
    private fun initCodec() {
        if (width == 0 || height == 0) {
            return
        }
        if (codec != null) {
            releaseCodec()
        }
        // create the media format
        val format = MediaFormat.createVideoFormat(VIDEO_ENCODING_FORMAT, width, height)
        val deviceName = deviceModel.lowercase(Locale.getDefault())
        val mediaCodecInfo = if (surface == null && shouldSendToEncoder.not()) {
            logd("initVideoDecoder: yuv output")
            // The surface is null, which means that the yuv data is needed, so the color format should
            // be set to YUV420.
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        } else {
            logd("initVideoDecoder: display")
            getColorFormatSurfaceFromDeviceName(deviceName)
        }
        Timber.tag("DASHCAM").d("device --> $deviceName & $mediaCodecInfo")
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mediaCodecInfo)

        try {
            // Create the codec instance.
            codec = MediaCodec.createDecoderByType(VIDEO_ENCODING_FORMAT)
            logd("initVideoDecoder create: " + (codec == null))
            // Configure the codec. What should be noted here is that the hardware decoder would not output
            // any yuv data if a surface is configured into, which mean that if you want the yuv frames, you
            // should set "null" surface when calling the "configure" method of MediaCodec.
            val surface = if (shouldSendToEncoder && ::mCircularEncoder.isInitialized)
                mCircularEncoder.inputSurface else this.surface
            codec!!.configure(format, surface, null, 0)
            logd("initVideoDecoder configure")
            if (codec == null) {
                loge("Can't find video info!")
                return
            }
            // Start the codec
            codec!!.start()
        } catch (e: Exception) {
            loge("init codec failed, do it again: $e")
            e.printStackTrace()
        }
    }

    private fun startDataHandler() {
        if (dataHandlerThread != null && dataHandlerThread!!.isAlive) {
            return
        }
        dataHandlerThread = HandlerThread("frame data handler thread")
        dataHandlerThread!!.start()
        dataHandler = object : Handler(dataHandlerThread!!.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_INIT_CODEC -> {
                        try {
                            initCodec()
                        } catch (e: Exception) {
                            loge("init codec error: " + e.message)
                            e.printStackTrace()
                        }
                        removeCallbacksAndMessages(null)
                        sendEmptyMessageDelayed(MSG_DECODE_FRAME, 1)
                    }

                    MSG_FRAME_QUEUE_IN -> {
                        try {
                            onFrameQueueIn(msg)
                        } catch (e: Exception) {
                            loge("queue in frame error: $e")
                            e.printStackTrace()
                        }
                        if (!hasMessages(MSG_DECODE_FRAME)) {
                            sendEmptyMessage(MSG_DECODE_FRAME)
                        }
                    }

                    MSG_DECODE_FRAME -> try {
                        decodeFrame()
                    } catch (e: Exception) {
                        loge("handle frame error: $e")
                        if (e is CodecException) {
                        }
                        e.printStackTrace()
                        initCodec()
                    } finally {
                        if (frameQueue!!.size > 0) {
                            sendEmptyMessage(MSG_DECODE_FRAME)
                        }
                    }

                    MSG_YUV_DATA -> {}
                    else -> {}
                }
            }
        }
    }

    /**
     * Stop the data processing thread
     */
    private fun stopDataHandler() = streamDecoderScope.launch {
        if (dataHandlerThread == null || !dataHandlerThread!!.isAlive) {
            return@launch
        }
        if (dataHandler != null) {
            dataHandler!!.removeCallbacksAndMessages(null)
        }
        try {
            dataHandlerThread!!.quitSafely()
            dataHandlerThread!!.join(3000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        releaseCodec()
        dataHandler = null
    }

    /**
     * Change the displaying surface of the decoder. What should be noted here is that the hardware decoder would not output
     * any yuv data if a surface is configured into, which mean that if you want the yuv frames, you
     * should set "null" surface when calling the "configure" method of MediaCodec.
     *
     * @param surface
     */
    fun changeSurface(surface: Surface) {
        if (this.surface !== surface) {
            this.surface = surface
            if (dataHandler != null && !dataHandler!!.hasMessages(MSG_INIT_CODEC)) {
                dataHandler!!.sendEmptyMessage(MSG_INIT_CODEC)
            }
        }
    }

    /**
     * Release and close the codec.
     */
    private fun releaseCodec() = streamDecoderScope.launch {
        if (frameQueue != null) {
            frameQueue.clear()
            hasIFrameInQueue = false
        }
        if (codec != null) {
            try {
                codec!!.flush()
            } catch (e: Exception) {
                loge("flush codec error: " + e.message)
            }
            try {
                codec!!.stop()
                codec!!.release()
            } catch (e: Exception) {
                loge("close codec error: " + e.message)
            } finally {
                codec = null
            }
        }
    }

    /**
     * Queue in the frame.
     *
     * @param msg
     */
    private fun onFrameQueueIn(msg: Message) {
        loge("onFrameQueueIn")
        val inputFrame = msg.obj as DJIFrame
        if (!hasIFrameInQueue) { // check the I frame flag
            if (inputFrame.frameNum != 1 && !inputFrame.isKeyFrame) {
                loge("the timing for setting iframe has not yet come.")
                return
            }
            var defaultKeyFrame: ByteArray? = null
            try {
                defaultKeyFrame = getDefaultKeyFrame(inputFrame.width) // Get I frame data
            } catch (e: IOException) {
                loge("get default key frame error: " + e.message)
            }
            if (defaultKeyFrame != null) {
                val iFrame = DJIFrame(
                    defaultKeyFrame,
                    defaultKeyFrame.size,
                    inputFrame.pts,
                    System.currentTimeMillis(),
                    inputFrame.isKeyFrame,
                    0,
                    inputFrame.frameIndex - 1,
                    inputFrame.width,
                    inputFrame.height
                )
                frameQueue?.clear()
                frameQueue?.offer(iFrame) // Queue in the I frame.
                logd("add iframe success!!!!")
                hasIFrameInQueue = true
            } else if (inputFrame.isKeyFrame) {
                logd("onFrameQueueIn no need add i frame!!!!")
                //   long currentTime = System.currentTimeMillis();
                // Log.d("RTSP", "i-frame Interval = " + (currentTime - frameTime));
                //  frameTime = currentTime;
                hasIFrameInQueue = true
            } else {
                loge("input key frame failed")
            }
        }
        if (inputFrame.width != 0 && inputFrame.height != 0 &&
            (inputFrame.width != width || inputFrame.height != height)
        ) {
            width = inputFrame.width
            height = inputFrame.height
            if (shouldSendToEncoder && ::mCircularEncoder.isInitialized.not()) {
                try {
                    mCircularEncoder = getCircularEncoder(Size(width, height))
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    e.printStackTrace()
                }
            }

            /*
             * On some devices, the codec supports changing of resolution during the fly
             * However, on some devices, that is not the case.
             * So, reset the codec in order to fix this issue.
             */loge("init decoder for the 1st time or when resolution changes")
            if (dataHandler != null && !dataHandler!!.hasMessages(MSG_INIT_CODEC)) {
                dataHandler!!.sendEmptyMessage(MSG_INIT_CODEC)
            }
        }
        // Queue in the input frame.
        if (frameQueue?.offer(inputFrame) == true) {
            logd("put a frame into the Extended-Queue with index=" + inputFrame.frameIndex)
        } else {
            // If the queue is full, drop a frame.
            val dropFrame = frameQueue?.poll()
            frameQueue?.offer(inputFrame)
            loge("Drop a frame with index=" + dropFrame?.frameIndex + " and append a frame with index=" + inputFrame.frameIndex)
        }

    }

    /**
     * Dequeue the frames from the queue and decode them using the hardware decoder.
     *
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun decodeFrame() {
        val inputFrame = frameQueue?.poll() ?: return
        if (codec == null) {
            if (dataHandler != null && !dataHandler!!.hasMessages(MSG_INIT_CODEC)) {
                dataHandler!!.sendEmptyMessage(MSG_INIT_CODEC)
            }
            return
        }
        val inIndex = codec!!.dequeueInputBuffer(0)
        // Decode the frame using MediaCodec
        if (inIndex >= 0) {
            //Log.d(TAG, "decodeFrame: index=" + inIndex);
            val buffer = codec!!.getInputBuffer(inIndex)
            buffer!!.put(inputFrame.videoBuffer)
            inputFrame.fedIntoCodecTime = System.currentTimeMillis()
            val queueingDelay: Long = inputFrame.queueDelay
            // Feed the frame data to the decoder.
            codec!!.queueInputBuffer(inIndex, 0, inputFrame.size, inputFrame.pts, 0)

            // Get the output data from the decoder.
            val outIndex = codec!!.dequeueOutputBuffer(bufferInfo, 0)
            when {
                (outIndex >= 0) -> {
                    val yuvDataBuf = codec!!.getOutputBuffer(outIndex)
                    yuvDataBuf!!.position(bufferInfo.offset)
                    yuvDataBuf.limit(bufferInfo.size - bufferInfo.offset)
                    if (yuvDataCallback != null) {
                        // Frame extraction mode
                        yuvDataCallback!!.onYuvDataReceived(
                            codec!!.outputFormat,
                            yuvDataBuf,
                            bufferInfo.size - bufferInfo.offset,
                            width,
                            height
                        )
                    }

                    try {
                        Timber.e("releaseOutputBuffer() fun called")
                        // All the output buffer must be release no matter whether the yuv data is output or
                        // not, so that the codec can reuse the buffer.
                        codec!!.releaseOutputBuffer(outIndex, true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                (outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) -> {
                    // The output buffer set is changed. So the decoder should be reinitialized and the
                    // output buffers should be retrieved.
                    val curTime = System.currentTimeMillis()
                    bufferChangedQueue.addLast(curTime)
                    if (bufferChangedQueue.size >= 10) {
                        val headTime = bufferChangedQueue.pollFirst() ?: 0L
                        if (curTime - headTime < 1000) {
                            // reset decoder
                            loge("Reset decoder. Get INFO_OUTPUT_BUFFERS_CHANGED more than 10 times within a second.")
                            bufferChangedQueue.clear()
                            dataHandler!!.removeCallbacksAndMessages(null)
                            dataHandler!!.sendEmptyMessage(MSG_INIT_CODEC)
                            return
                        }
                    }
                }

                (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) -> {
                    loge("format changed, color: " + codec!!.outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT))
                }
            }
        } else {
            codec!!.flush()
        }
    }

    /**
     * Stop the decoding process.
     */
    fun stop() = streamDecoderScope.launch {
        if (dataHandler != null) {
            dataHandler!!.removeCallbacksAndMessages(null)
        }
        if (frameQueue != null) {
            frameQueue.clear()
            hasIFrameInQueue = false
        }
        if (codec != null) {
            try {
                codec!!.flush()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
        try {
            NativeHelper.getInstance().removeDataListener(this@VideoStreamDecoder)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopDataHandler()
    }

    fun resume() {
        startDataHandler()
    }

    fun destroy() {
        codec?.release()
    }

    override fun onDataReceived(
        data: ByteArray,
        size: Int,
        frameNum: Int,
        isKeyFrame: Boolean,
        width: Int,
        height: Int
    ) {
        if (dataHandler == null || dataHandlerThread == null || !dataHandlerThread!!.isAlive || width == 0 || height == 0) return
        if (data.size != size) loge("recv data size: " + size + ", data lenght: " + data.size)
        else {
            logd(
                "recv data size: " + size + ", frameNum: " + frameNum + ", isKeyframe: " + isKeyFrame + "," +
                        " width: " + width + ", height: " + height
            )

            currentTime = System.nanoTime() / 1000
            frameIndex++
            val newFrame = DJIFrame(
                data, size, currentTime, currentTime, isKeyFrame,
                frameNum, frameIndex.toLong(), width, height
            )
            dataHandler!!.obtainMessage(MSG_FRAME_QUEUE_IN, newFrame).sendToTarget()
        }

        if (::mCircularEncoder.isInitialized) {
            saveMetaDataBuffer()
            circularEncoderHandler.sendEmptyMessage(MSG_FRAME_AVAILABLE)
        }
    }

    private fun getCircularEncoder(previewSize: Size): CircularEncoder {
        return CircularEncoder(
            previewSize.width, previewSize.height, 6000000,
            30, 11, Surface.ROTATION_270, circularEncoderHandler
        )
    }

    lateinit var userLocation: UserLocation
    private var delayVideoRecordingData: RecordingData? = null

    companion object {
        private val TAG = VideoStreamDecoder::class.java.simpleName
        private const val BUF_QUEUE_SIZE = 30
        private const val MSG_INIT_CODEC = 0
        private const val MSG_FRAME_QUEUE_IN = 1
        private const val MSG_DECODE_FRAME = 2
        private const val MSG_YUV_DATA = 3
        const val VIDEO_ENCODING_FORMAT = "video/avc"
    }

    private fun saveMetaDataBuffer(
    ) = streamDecoderScope.launch {
        iMetaDataHelper.saveMetaData(
            currentLocationMeta,
            currentSensorMeta
        )
    }

    fun setDelayVideoRecordingData(videoData: RecordingData?) {
        delayVideoRecordingData = videoData
    }

    private fun saveVideo(recordingData: RecordingData) = streamDecoderScope.launch {
        delay(SAVING_VIDEO_DELAY)
        if (::mCircularEncoder.isInitialized) {
            fileSaveInProgress = true
            mCircularEncoder.saveVideo(recordingData)
        } else {
            Firebase.crashlytics.log("::fun saveVideo circularEncoder is not Initialized")
            Firebase.crashlytics.recordException(Exception("late init property circularEncoder has not been initialized"))
        }
    }

    suspend fun recordVideo(
        file: File,
        currentUserLocation: UserLocation,
        modelName: String = "",
        labelName: String = "",
        confidence: String = "",
        isManual: Boolean = false,
        recordedWorkFlowMetaData: String = ""
    ) = streamDecoderScope.launch {
        userLocation = currentUserLocation
        iRecordingHelper.recordVideo(
            file,
            currentUserLocation,
            modelName,
            labelName,
            confidence,
            isManual,
            recordedWorkFlowMetaData
        )?.let { saveVideo(it) }
    }

    fun getRecordingStateLD() = iRecordingHelper.getRecordingStateLD()

    fun getFileSaveLD(): LiveData<Boolean> = iRecordingHelper.getFileSaveProgressLD()
}