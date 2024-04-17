package co.nayan.canvas

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.utils.ImageUtils
import co.nayan.c3views.keyboard.KeyboardActionListener
import co.nayan.c3views.utils.annotations
import co.nayan.canvas.edgedetection.utils.ImageScanner
import co.nayan.canvas.image_processing.ImagePreviewAnalyzer
import co.nayan.canvas.image_processing.ObjectOfInterestListener
import co.nayan.canvas.modes.crop.CropFragment
import co.nayan.canvas.modes.input.LPInputManager
import co.nayan.canvas.utils.BitmapRequestListener
import co.nayan.canvas.utils.GifRequestListener
import co.nayan.canvas.utils.VoiceRecognitionListener
import co.nayan.canvas.utils.singleValue
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import co.nayan.canvas.widgets.JunkDialogListener
import co.nayan.canvas.widgets.JunkRecordDialogFragment
import co.nayan.canvas.widgets.SelectLabelDialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import timber.log.Timber
import java.net.SocketException
import java.util.*
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.collections.ArrayList

abstract class AnnotationCanvasFragment(layoutID: Int) : CanvasFragment(layoutID) {

    @Inject
    lateinit var imagePreviewAnalyzer: ImagePreviewAnalyzer

    @Inject
    lateinit var inputManager: LPInputManager

    protected lateinit var originalBitmap: Bitmap
    protected var frameCount: Int? = 0

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    protected var sniffingAnswer: String? = null

    private val imageScannerListener = object : ImageScanner.ImageScannerListener {
        override fun onPointsDetected(points: List<PointF>, screenSizeBitmap: Bitmap) {
            lifecycleScope.launch(Dispatchers.Main) {
                onDetected(points, screenSizeBitmap)
            }
        }

        override fun onScannedCompleted() {

        }
    }

    protected val imageScanner = ImageScanner(imageScannerListener)
    abstract fun resetViews(applicationMode: String?, correctAnnotationList: List<AnnotationData>)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupImagePreviewAnalyzer()
        canvasViewModel.resetViews = { applicationMode, correctAnnotationList ->
            resetViews(applicationMode, correctAnnotationList)
        }
    }

    private fun setupImagePreviewAnalyzer() {
        if (isImageProcessingEnabled()) {
            canvasViewModel.cameraAiModel?.let {
                if (::imagePreviewAnalyzer.isInitialized) {
                    imagePreviewAnalyzer.setCameraAIModel(it)
                    imagePreviewAnalyzer.setObjectOfInterestListener(objectOfInterestListener)
                }
            }
        }
    }

    private val objectOfInterestListener = object : ObjectOfInterestListener {
        override fun onObjectDetected(validObjects: ArrayList<Pair<List<RectF>, String?>>) {
            Timber.d("Final results size : ${validObjects.size}")
            addBoundingBoxes(validObjects)
        }
    }

    open fun setupViews() {
        if (isSandbox()) setupSandboxView(canvasViewModel.shouldEnableHintButton())
        else setupCanvasView()
    }

    abstract fun setupSandboxView(shouldEnableHintBtn: Boolean)
    abstract fun setupCanvasView()

    protected fun setupTemplateObserver() {
        canvasViewModel.templateState.observe(viewLifecycleOwner) {
            when (it) {
                is BaseCanvasViewModel.TemplatesSuccessState -> {
                    addTemplates(it.templates)
                }

                is BaseCanvasViewModel.TemplatesFailedState -> {
                    it.message?.let { it1 -> showMessage(it1) }
                }
            }
        }
    }

    protected fun fetchTemplates() {
        canvasViewModel.fetchTemplates()
    }

    protected fun saveRecentSearchedTemplate(template: Template?) {
        template?.let { canvasViewModel.saveRecentSearchedTemplate(it) }
    }

    protected fun submitAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>) {
        if (canvasViewModel.mediaType == MediaType.VIDEO)
            submitVideoFrameAnnotation(annotationObjectsAttributes)
        else {
            disabledJudgmentButtons()
            canvasViewModel.submitAnnotation(annotationObjectsAttributes)
            canvasViewModel.setupForNextRecord()
        }
    }

    protected fun undoAnnotation() {
        val annotationObjectsAttributes = canvasViewModel.undoAnnotation()
        canvasViewModel.setupForPrevRecord(annotationObjectsAttributes)
    }

    protected suspend fun loadVideo(mediaUrl: String) {
        val progress = canvasViewModel.getContrast()
//        val colorFilter = ImageUtils.getColorMatrix(progress)
        lifecycleScope.launch {
            onVideoReady(progress, mediaUrl)
            enabledJudgmentButtons()
        }
    }

    protected suspend fun loadImage(view: PhotoView, imageUrl: String?) {
        if (imageUrl?.contains(".gif") == true) loadGif(view, imageUrl)
        else loadBitmap(view, imageUrl)
    }

    protected suspend fun loadBitmap(view: PhotoView, imageUrl: String?) {
        try {
            context?.let {
                Glide.with(it)
                    .asBitmap()
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(bitmapRequestListener)
                    .into(view)
                originalBitmap = getOriginalBitmapFromUrl(imageUrl, it)
                if (isBitmapInitialized())
                    canvasViewModel.setImageSize(originalBitmap.width, originalBitmap.height)
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Timber.d(e)
        }
    }

    private suspend fun loadGif(view: PhotoView, imageUrl: String?) {
        try {
            context?.let {
                Glide.with(it)
                    .asGif()
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(gifRequestListener)
                    .into(view)
            }
        } catch (e: ExecutionException) {
            Firebase.crashlytics.recordException(e)
            Timber.d(e)
            onBitmapLoadFailed()
        } catch (e: SocketException) {
            Firebase.crashlytics.recordException(e)
            Timber.d(e)
            onBitmapLoadFailed()
        }
    }

    protected fun isBitmapInitialized() =
        this@AnnotationCanvasFragment::originalBitmap.isInitialized

    private suspend fun getOriginalBitmapFromUrl(imageUrl: String?, context: Context): Bitmap =
        withContext(Dispatchers.IO) {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .submit()
                .get()
        }

    private val gifRequestListener = object : GifRequestListener() {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<GifDrawable>,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch {
                onBitmapLoadFailed()
                enabledJudgmentButtons()
            }
            return false
        }

        override fun onResourceReady(
            resource: GifDrawable,
            model: Any,
            target: Target<GifDrawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            val progress = canvasViewModel.getContrast()
            val colorFilter = ImageUtils.getColorMatrix(progress)
            lifecycleScope.launch {
                onBitmapReady(colorFilter, progress)
                enabledJudgmentButtons()
            }
            return false
        }
    }

    private val bitmapRequestListener = object : BitmapRequestListener() {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Bitmap>,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch {
                onBitmapLoadFailed()
                enabledJudgmentButtons()
            }
            return false
        }

        override fun onResourceReady(
            resource: Bitmap,
            model: Any,
            target: Target<Bitmap>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            val progress = canvasViewModel.getContrast()
            val colorFilter = ImageUtils.getColorMatrix(progress)
            lifecycleScope.launch {
                onBitmapReady(colorFilter, progress)
                enabledJudgmentButtons()
            }
            return false
        }
    }

    protected fun reloadRecord() {
        canvasViewModel.reloadRecord()
    }

    private val junkDialogListener = object : JunkDialogListener {
        override fun junkRecord() {
            when (canvasViewModel.mediaType) {
                MediaType.VIDEO -> {
                    when (canvasViewModel.getVideoModeState()) {
                        PARENT_STEP_SANDBOX,
                        PARENT_STEP_VIDEO_ANNOTATION -> {
                            canvasViewModel.clearAnnotatedFrame(frameCount)
                        }

                        CHILD_STEP_SANDBOX,
                        CHILD_STEP_VIDEO_ANNOTATION -> {
                            canvasViewModel.clearChildAnnotatedFrame()
                        }
                    }

                    requireActivity().supportFragmentManager.fragments.map {
                        if (it is CropFragment) {
                            requireActivity().supportFragmentManager.popBackStack()
                        }
                    }
                }

                else -> {
                    if (canvasViewModel.applicationMode == Mode.BINARY_CLASSIFY) {
                        canvasViewModel.submitAnnotationsForBNC(null, Judgment.JUNK)
                    } else {
                        val annotationObjectsAttributes =
                            listOf(AnnotationObjectsAttribute(AnnotationValue(Judgment.JUNK)))
                        submitAnnotation(annotationObjectsAttributes)
                    }
                }
            }
        }
    }

    protected fun showJunkRecordDialog(message: String? = null) {
        val dialogMessage: String?
        val negativeBtnText: String?
        when (canvasViewModel.getVideoModeState()) {
            PARENT_STEP_VIDEO_ANNOTATION -> {
                dialogMessage = getString(R.string.remove_all_annotation_from_frame)
                negativeBtnText = getString(R.string.remove_all)
            }

            CHILD_STEP_VIDEO_ANNOTATION -> {
                dialogMessage = "Remove Child annotation?"
                negativeBtnText = getString(R.string.remove_all)
            }

            else -> {
                dialogMessage = message
                negativeBtnText = null
            }
        }

        childFragmentManager.fragments.forEach {
            if (it is JunkRecordDialogFragment) {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
        }

        val junkRecordDialogFragment = JunkRecordDialogFragment.newInstance(junkDialogListener)
        junkRecordDialogFragment.setMessage(dialogMessage)
        junkRecordDialogFragment.setNegativeButtonText(negativeBtnText)
        junkRecordDialogFragment.show(childFragmentManager.beginTransaction(), "Junk Record")
    }

    protected fun getAnnotationData(record: Record): List<AnnotationData> {
        val annotationData = if (canvasViewModel.shouldLoadPrevAnnotations)
            canvasViewModel.getAnnotationData()
        else {
            if (isSandbox()) {
                if (isAdminRole()) record.annotation.annotations()
                else emptyList()
            } else {
                if (isAIAssistEnabled()) detectObjects()
                if (record.isSniffingRecord == true) emptyList()
                else record.annotations()
            }
        }
        canvasViewModel.shouldLoadPrevAnnotations = true
        return annotationData
    }

    protected fun setupHintData(record: Record) {
        val shouldSetupHint =
            (isSandbox() && isAdminRole().not()) || record.isSniffingRecord == true
        if (shouldSetupHint) addHintData(record)
    }

    protected fun isValidAnnotation() = getCurrentAnnotation().isNotEmpty()

    protected fun setupZoomViewPosition(x: Float, y: Float) {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isRight = x < 350 && y < 350
        val isLeft = x > screenWidth - 350 && y < 350
        if (isRight) setupZoomView(getZoomViewLayoutParams(RelativeLayout.ALIGN_PARENT_RIGHT))
        else if (isLeft) setupZoomView(getZoomViewLayoutParams(RelativeLayout.ALIGN_PARENT_LEFT))
    }

    private fun getZoomViewLayoutParams(rule: Int): ViewGroup.LayoutParams {
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.addRule(rule)
        return layoutParams
    }

    protected val keyboardActionListener = object : KeyboardActionListener {
        override fun setValue(value: String) {
            appendInput(value)
        }

        override fun delete() {
            deleteLastInput()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected val makeTransparentListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                hideLabel()
            }

            MotionEvent.ACTION_UP -> {
                showLabel()
            }
        }
        true
    }

    @SuppressLint("ClickableViewAccessibility")
    protected val onSniffingTouchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                showHint()
            }

            MotionEvent.ACTION_UP -> {
                hideHint()
            }
        }
        true
    }

    protected val sniffingClickListener = View.OnClickListener {
        showMessage("Sniffing Answer: $sniffingAnswer")
    }

    protected fun setAnnotationObjectiveAttributes(toSet: List<AnnotationObjectsAttribute>) {
        canvasViewModel.setAnnotationObjectiveAttributes(toSet)
    }

    protected fun getQuestion() = canvasViewModel.question ?: ""

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else audioPermissionDenied()
        }

    fun checkAudioPermission() {
        requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private val recognitionListener = object : VoiceRecognitionListener() {
        override fun onError(error: Int) {
            super.onError(error)
            showMessage(getString(R.string.recognitionError))
        }

        override fun onResults(results: Bundle?) {
            super.onResults(results)
            results?.let {
                val values = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val input = (values?.firstOrNull() ?: "")
                    .uppercase(Locale.getDefault())
                    .replace(Regex("\\s"), "")
                Timber.e("$input -- ${input.singleValue()}")
                appendInput(input.singleValue())
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {
            super.onReadyForSpeech(params)
            readyForSpeech()
        }

        override fun onEndOfSpeech() {
            super.onEndOfSpeech()
            endOfSpeech()
        }
    }

    protected fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en")
        }
        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    private fun startListening() {
        if (this@AnnotationCanvasFragment::recognizerIntent.isInitialized) {
            speechRecognizer?.startListening(recognizerIntent)
        }
    }

    protected fun stopListening() {
        speechRecognizer?.stopListening()
    }

    private fun submitVideoFrameAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>) {
        canvasViewModel.setAnnotationObjectiveAttributes(annotationObjectsAttributes)
        if (isBitmapInitialized()) {
            val videoAnnotationData = VideoAnnotationData(frameCount, originalBitmap)
            val annotations = canvasViewModel.getAnnotationData()
            annotations.forEach { it.frameCount = frameCount }
            videoAnnotationData.annotations.addAll(annotations)
            canvasViewModel.addVideoAnnotationData(videoAnnotationData)
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun populateBitmap(videoAnnotationData: VideoAnnotationData) {
        frameCount = videoAnnotationData.frameCount
        videoAnnotationData.bitmap?.let { originalBitmap = it }
        if (isBitmapInitialized())
            canvasViewModel.setImageSize(originalBitmap.width, originalBitmap.height)
    }

    protected fun isSandbox() = canvasViewModel.isSandbox()

    fun detectObjects() = lifecycleScope.launch {
        if (::imagePreviewAnalyzer.isInitialized && isBitmapInitialized())
            imagePreviewAnalyzer.analyze(originalBitmap)
    }

    protected suspend fun initOpenCv() {
        val isInitialized = withContext(Dispatchers.IO) {
            OpenCVLoader.initDebug()
        }
        canvasViewModel.setOpenCvInitialized(isInitialized)
    }

    protected fun getScreenSize(): Pair<Int, Int> {
        val height =
            requireActivity().window?.findViewById<View>(Window.ID_ANDROID_CONTENT)?.height ?: 0
        val width =
            requireActivity().window?.findViewById<View>(Window.ID_ANDROID_CONTENT)?.width ?: 0
        return Pair(width, height)
    }

    protected fun isQAEnvironment() = listOf("qa", "dev").contains(appFlavor())

    protected fun isOpenCvInitialized() = canvasViewModel.isOpenCvInitialized.value ?: false

    protected fun isAdminRole() = canvasViewModel.isAdminRole()

    protected fun isMediaTypeImage() = canvasViewModel.mediaType == MediaType.IMAGE

    protected fun isInPreviewMode() = canvasViewModel.isInPreviewMode

    protected fun getRandomHexCode(): String {
        val random = Random()
        val randNum = random.nextInt(0xffffff + 1)
        return String.format("#%06x", randNum)
    }

    protected fun setupInterpolationLinking() {
        val videoAnnotationDataList = canvasViewModel.getVideoAnnotationData()
        val activeAnnotation = videoAnnotationDataList.flatMap { it.annotations }
            .findLast { it.objectIndex == canvasViewModel.activeAnnotationId }
        if (activeAnnotation == null) onAddNewInterpolationAnnotation()
        else onSelectInterpolationAnnotation(activeAnnotation)
    }

    protected fun uniqueAnnotationNameCount(): String {
        val videoAnnotationDataList = canvasViewModel.getVideoAnnotationData()
        val count = videoAnnotationDataList.flatMap { it.annotations }.distinctBy { it.objectIndex }
            .count()
        return "Object ${(count + 1)}"
    }

    protected fun fetchUniqueAnnotationList(): MutableList<AnnotationData> {
        val previousAnnotationIds = canvasViewModel.getAnnotationData().map { it.objectIndex }
        val videoAnnotationDataList =
            canvasViewModel.getVideoAnnotationData().filter { it.bitmap != null }
        return videoAnnotationDataList.flatMap { it.annotations }.distinctBy { it.objectIndex }
            .filter { previousAnnotationIds.contains(it.objectIndex).not() }.toMutableList()
    }

    protected fun showLabelDialog(uniqueInterpolations: MutableList<AnnotationData>) {
        childFragmentManager.showDialogFragment(
            SelectLabelDialogFragment(originalBitmap, uniqueInterpolations) {
                onSelectInterpolationAnnotation(it)
            }, "Choose Label"
        )
    }

    open fun drawAnnotations(annotations: List<AnnotationData>) = Unit
    open fun showLabel() = Unit
    open fun hideLabel() = Unit
    open fun appendInput(value: String) = Unit
    open fun deleteLastInput() = Unit
    open fun setupZoomView(params: ViewGroup.LayoutParams) = Unit
    open fun addHintData(record: Record) = Unit
    open fun showHint() = Unit
    open fun hideHint() = Unit
    open fun getCurrentAnnotation(isSubmittingRecord: Boolean = false) = emptyList<AnnotationObjectsAttribute>()
    open fun setupPreviousAnnotations(record: Record) = Unit
    open fun enableEditMode() = Unit
    open fun disableEditMode() = Unit
    open suspend fun onBitmapLoadFailed() = Unit
    open suspend fun onBitmapReady(colorFilter: ColorMatrixColorFilter, contrastProgress: Int) =
        Unit

    open suspend fun onVideoReady(contrastProgress: Int, mediaUrl: String) = Unit
    open fun endOfSpeech() = Unit
    open fun readyForSpeech() = Unit
    open fun audioPermissionDenied() = Unit
    open fun addTemplates(templates: List<Template>) = Unit
    open fun addBoundingBoxes(validObjects: ArrayList<Pair<List<RectF>, String?>>) = Unit
    open fun clearAIAssistAnnotations() = Unit
    open fun isImageProcessingEnabled() = canvasViewModel.isImageProcessingEnabled
    open fun isAIAssistEnabled() =
        (canvasViewModel.isImageProcessingEnabled && canvasViewModel.isManualAIAssistEnabled)

    open fun onDetected(points: List<PointF>, screenSizeBitmap: Bitmap) = Unit
    open fun onSelectInterpolationAnnotation(annotationData: AnnotationData) = Unit
    open fun onAddNewInterpolationAnnotation() = Unit
}