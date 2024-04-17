package co.nayan.c3specialist_v2.record_visualization.video_type_record

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.Tag.DOWNLOAD_FAILED
import co.nayan.c3specialist_v2.config.Tag.EXTRACTION_FAILED
import co.nayan.c3specialist_v2.databinding.ActivityVideoTypeRecordBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.utils.*
import co.nayan.canvas.utils.SimpleDividerItemDecoration
import co.nayan.canvas.utils.getFormattedTimeStamp
import co.nayan.canvas.videoannotation.*
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class VideoTypeRecordActivity : BaseActivity() {

    private val videoTypeRecordViewModel: VideoTypeRecordViewModel by viewModels()
    private val binding: ActivityVideoTypeRecordBinding by viewBinding(
        ActivityVideoTypeRecordBinding::inflate
    )

    private var record: Record? = null
    private var nonActiveDots: Drawable? = null
    private var activeDot: Drawable? = null
    private var dots: Array<ImageView?>? = null
    private var isAnswerJunk: Boolean = false

    private var isMediaPlaying = false
    private var frameCount = 0
    private var totalFrames = 1
    private var isMediaPlaybackCompleted = false
    private var framesDir: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupFullScreenMode()
        setupExoPlayer()
        setupClickListeners()
        videoTypeRecordViewModel.initVideoDownloadManager()
        videoTypeRecordViewModel.foregroundVideoDownloading.observe(this, videoDownloadingObserver)
        videoTypeRecordViewModel.downloadingProgress.observe(this, downloadingProgressObserver)
        videoTypeRecordViewModel.state.observe(this, stateObserver)
        videoTypeRecordViewModel.record.observe(this, recordObserver)
        setupExtras()
        setupViews()
    }

    @Suppress("DEPRECATION")
    private fun setupFullScreenMode() {
        window.apply {
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exoPlayerProgressHandler.post(exoPlayerProgressRunnable)
    }

    override fun onPause() {
        super.onPause()
        exoPlayerProgressHandler.removeCallbacks(exoPlayerProgressRunnable)
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            VideoTypeRecordViewModel.RefreshVideoAnnotationModeState -> {
                refreshVideoAnnotationSlider()
            }

            is VideoTypeRecordViewModel.DrawAnnotationState -> {
                drawOverlays(it.annotations, it.frameCount)
            }

            VideoTypeRecordViewModel.ClearAnnotationState -> {
                clearOverlays()
            }

            is VideoTypeRecordViewModel.DownloadingFailedState -> {
                showAlert(
                    shouldFinish = true,
                    message = getString(co.nayan.canvas.R.string.downloading_failed_video_message).format(
                        it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId
                    ),
                    positiveText = getString(R.string.ok),
                    negativeText = getString(R.string.cancel),
                    showNegativeBtn = false,
                    showPositiveBtn = true,
                    tag = DOWNLOAD_FAILED
                )
            }

            is VideoTypeRecordViewModel.FrameExtractionFailedState -> {
                showAlert(
                    shouldFinish = true,
                    message = getString(co.nayan.canvas.R.string.extraction_failed_video_message).format(
                        it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId
                    ),
                    positiveText = getString(R.string.ok),
                    negativeText = getString(R.string.cancel),
                    showNegativeBtn = false,
                    showPositiveBtn = true,
                    tag = EXTRACTION_FAILED
                )
            }
        }
    }

    private fun drawOverlays(annotations: MutableList<AnnotationData>, frameCount: Int?) {
        if (annotations.isEmpty()) {
            clearOverlays()
            return
        }
        this.frameCount = frameCount ?: 0
        val bitmap = getBitmap() ?: return
        when (annotations.firstOrNull()?.type) {
            DrawType.BOUNDING_BOX -> {
                val translucentBitmap =
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                binding.parentCropView.setBitmapAttributes(bitmap.width, bitmap.height)
                binding.parentCropView.crops.clear()
                binding.parentCropView.crops.addAll(annotations.crops(bitmap))
                Glide.with(this).load(translucentBitmap).into(binding.parentCropView)
            }
        }
    }

    private fun clearOverlays() {
        binding.parentCropView.reset()
        binding.parentCropView.invalidate()
    }

    override fun alertDialogPositiveClick(shouldFinishActivity: Boolean, tag: String?) {
        if (shouldFinishActivity) this@VideoTypeRecordActivity.finish()
    }

    private val recordObserver: Observer<Record> = Observer {
        populateVideoRecord(it)
    }

    private val videoDownloadingObserver: Observer<Boolean> = Observer {
        showVideoDownloadingProgress(it)
    }

    private val downloadingProgressObserver: Observer<Int> = Observer {
        updateDownloadingProgress(it)
    }

    private fun setupExtras() {
        videoTypeRecordViewModel.applicationMode = intent.getStringExtra(Extras.APPLICATION_MODE)
        intent.parcelable<Record>(Extras.RECORD)?.let {
            videoTypeRecordViewModel.startVideoProcessing(it)
        } ?: run { showMessage(getString(R.string.record_cant_be_null)) }
        videoTypeRecordViewModel.question = intent.getStringExtra(Extras.QUESTION)
    }

    private val exoplayerControlsHandler = Handler(Looper.getMainLooper())
    private val hideExoPlayerControlsRunnable = Runnable {
        hideExoplayerControls()
    }

    private fun hideExoplayerControls() {
        binding.parentAnnotationHintContainer.gone()
        binding.exoPlayerView.exoPlayerControlButtonsView.gone()
        binding.questionTxt.gone()
        binding.recordTxt.gone()
        if (isAnswerJunk) binding.junkAnnotationIv.gone()
    }

    private val onVideoAnnotationClickListener = object : OnVideoAnnotationClickListener {
        override fun onClick(videoAnnotationData: VideoAnnotationData) {
            if (videoAnnotationData.bitmap == null)
                showToast(getString(co.nayan.canvas.R.string.preview_not_available))
            else {
                updateFrameMonitor(videoAnnotationData)
                binding.drawerLayout.closeDrawer(GravityCompat.END)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateFrameMonitor(selected: VideoAnnotationData) {
        binding.frameMonitorContainer.visible()
        binding.sliderDotsPanel.removeAllViews()
        val frames = when (videoTypeRecordViewModel.getVideoModeState()) {
            PARENT_STEP_VIDEO -> videoTypeRecordViewModel.getVideoAnnotationDataForFrameMonitor()
            CHILD_STEP_VIDEO ->
                videoTypeRecordViewModel.getParentChildAnnotationDataForFrameMonitor(selected)

            else -> mutableListOf()
        }

        if (frames.isEmpty()) {
            showToast(getString(co.nayan.canvas.R.string.preview_not_available))
            return
        }

        val frameCount = frames.size
        dots = arrayOfNulls(frameCount)
        for (i in 0 until frameCount) {
            dots!![i] = ImageView(this)
            dots!![i]?.setImageDrawable(nonActiveDots)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            binding.sliderDotsPanel.addView(dots!![i], params)
        }
        frameMonitorAdapter.add(frames)
        val selectedIndex = frames.indexOf(selected)
        binding.frameMonitor.setCurrentItem(selectedIndex, true)
        dots!![selectedIndex]?.setImageDrawable(activeDot)
        frameMonitorAdapter.notifyDataSetChanged()
    }

    private val videoAnnotationViewAdapter =
        VideoAnnotationViewAdapter(onVideoAnnotationClickListener)
    private val workflowStepAnnotationAdapter =
        WorkflowStepAnnotationAdapter(onVideoAnnotationClickListener)

    private val frameMonitorAdapter = FrameMonitorAdapter()
    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            when (videoTypeRecordViewModel.getVideoModeState()) {
                PARENT_STEP_VIDEO -> {
                    val frame =
                        videoTypeRecordViewModel.getVideoAnnotationDataForFrameMonitor()[position]
                    frameCount = frame.frameCount ?: 0
                    playNext()
                    videoTypeRecordViewModel.selectVideoFrame(frame)
                }

                CHILD_STEP_VIDEO -> {
                }
            }

            for (i in 0 until frameMonitorAdapter.itemCount) {
                dots?.get(i)?.setImageDrawable(nonActiveDots)
            }
            dots?.get(position)?.setImageDrawable(activeDot)
        }
    }

    private val exoPlayerProgressHandler = Handler(Looper.getMainLooper())
    private val exoPlayerProgressRunnable = object : Runnable {
        override fun run() {
            if (isMediaPlaying) {
                frameCount += 1
                setupTimeBar()
                videoTypeRecordViewModel.monitorFrames(frameCount)
            }
            exoPlayerProgressHandler.postDelayed(this, 30)
        }
    }

    private fun playNext() {
        framesDir?.let {
            Timber.tag("Frame count").e("$frameCount")
            val file = File(it, "out$frameCount.jpg")
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                binding.framesView.setImageURI(uri)
            } else {
                isMediaPlaying = false
                frameCount = 0
            }
        }
    }

    private fun setupTimeBar() {
        binding.exoPlayerView.exoProgress.progress = frameCount * 30
    }

    private val drawerLayoutListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerStateChanged(newState: Int) {}

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

        override fun onDrawerClosed(drawerView: View) {
            binding.openAnnotationSlider.visible()
        }

        override fun onDrawerOpened(drawerView: View) {
            binding.openAnnotationSlider.gone()
        }
    }

    private fun setupVideoMode() {
        setupFrameMonitor()
        binding.frameMonitorContainer.gone()
    }

    private fun setupFrameMonitor() {
        nonActiveDots =
            ContextCompat.getDrawable(this, co.nayan.canvas.R.drawable.non_active_dots)
        activeDot = ContextCompat.getDrawable(this, co.nayan.canvas.R.drawable.active_dots)
        binding.frameMonitor.registerOnPageChangeCallback(onPageChangeCallback)
        binding.frameMonitor.adapter = frameMonitorAdapter
    }

    private fun setupViews() {
        if (videoTypeRecordViewModel.isInterpolationEnabled()) {
            binding.parentCropView.visible()
            binding.openAnnotationSlider.gone()
            binding.parentCropView.touchEnabled(false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExoPlayer() {
        binding.framesView.onCaptureReleasePoint = {
            showNavigationBarControls()
        }
        setupPlayerControls()
    }

    private fun showNavigationBarControls() {
        if (binding.exoPlayerView.exoPlayerControlButtonsView.isVisible.not()) {
            showExoplayerControls()
            return
        }

        if (frameCount == totalFrames) {
            isMediaPlaying = false
            binding.exoPlayerView.ivPlayPause.apply {
                setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        co.nayan.canvas.R.drawable.ic_play
                    )
                )
            }
        } else {
            val drawableId = if (isMediaPlaying) co.nayan.canvas.R.drawable.ic_play
            else co.nayan.canvas.R.drawable.ic_pause
            isMediaPlaying = !isMediaPlaying
            binding.exoPlayerView.ivPlayPause.apply {
                setImageDrawable(ContextCompat.getDrawable(context, drawableId))
            }
        }
    }

    private fun setupClickListeners() {
        binding.exoPlayerView.ivPlayPause.setOnClickListener {
            val drawableId = if (isMediaPlaying) co.nayan.canvas.R.drawable.ic_play
            else {
                if (frameCount == totalFrames) frameCount = 0
                co.nayan.canvas.R.drawable.ic_pause
            }
            isMediaPlaying = !isMediaPlaying
            binding.exoPlayerView.ivPlayPause.apply {
                setImageDrawable(ContextCompat.getDrawable(context, drawableId))
            }
        }

        binding.openAnnotationSlider.setOnClickListener {
            if (videoTypeRecordViewModel.hasVideoAnnotationData())
                showToast(getString(co.nayan.canvas.R.string.no_annotated_video_frame_found))
            else refreshVideoAnnotationSlider()
        }

        binding.closeAnnotationSlider.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            resumePlayer()
        }

        binding.parentAnnotationHintContainer.setOnClickListener {
            videoTypeRecordViewModel.getLastParentAnnotationHint()?.let {
                frameCount = it.frameCount ?: 0
                playNext()
            }
        }

        binding.closeFrameMonitor.setOnClickListener { binding.frameMonitorContainer.gone() }
    }

    private fun pausePlayer() {
        if (isMediaPlaying) {
            isMediaPlaying = !isMediaPlaying
            binding.exoPlayerView.ivPlayPause.apply {
                setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        co.nayan.canvas.R.drawable.ic_play
                    )
                )
            }
        }
    }

    private fun resumePlayer() {
        if (isMediaPlaying.not()) {
            isMediaPlaying = !isMediaPlaying
            binding.exoPlayerView.ivPlayPause.apply {
                setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        co.nayan.canvas.R.drawable.ic_pause
                    )
                )
            }
        }
    }

    private fun getBitmap(): Bitmap? {
        val file = File(framesDir, "out$frameCount.jpg")
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshVideoAnnotationSlider() {
        hideExoplayerControls()
        when (videoTypeRecordViewModel.getVideoModeState()) {
            PARENT_STEP_VIDEO -> {
                managePreviewForParentStep()
            }

            CHILD_STEP_VIDEO -> {
                val previewFrames =
                    videoTypeRecordViewModel.getVideoParentAnnotationDataForPreview()
                pausePlayer()

                previewFrames.forEach { stepAnnotations ->
                    stepAnnotations.forEach {
                        if (it.bitmap == null && frameCount == it.frameCount) {
                            val bitmap = getBitmap()
                            it.bitmap = bitmap
                        }
                    }
                }

                workflowStepAnnotationAdapter.add(previewFrames)
                workflowStepAnnotationAdapter.notifyDataSetChanged()
                if (binding.frameMonitorContainer.visibility == View.GONE)
                    binding.drawerLayout.openDrawer(GravityCompat.END)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun managePreviewForParentStep() {
        if (videoTypeRecordViewModel.getVideoAnnotationData().isEmpty()) {
            videoAnnotationViewAdapter.add(listOf())
            videoAnnotationViewAdapter.notifyDataSetChanged()
        } else {
            val previewFrames = videoTypeRecordViewModel.getVideoAnnotationData()
            pausePlayer()
            previewFrames.forEach {
                if (it.frameCount == frameCount) {
                    if (it.bitmap == null) {
                        it.bitmap = getBitmap()
                    }
                }
            }
            videoAnnotationViewAdapter.add(previewFrames)
            videoAnnotationViewAdapter.notifyDataSetChanged()
            if (binding.frameMonitorContainer.visibility == View.GONE)
                binding.drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    private fun setupVideoAnnotationSlider() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.drawerLayout.addDrawerListener(drawerLayoutListener)
        workflowStepAnnotationAdapter.setVideoAnnotationViewAdapterMode(videoTypeRecordViewModel.getVideoModeState())
        when (videoTypeRecordViewModel.getVideoModeState()) {
            PARENT_STEP_VIDEO -> {
                val gridLayoutManager = GridLayoutManager(this, 3)
                gridLayoutManager.orientation = LinearLayoutManager.VERTICAL
                binding.videoAnnotationRecyclerView.layoutManager = gridLayoutManager
                binding.videoAnnotationRecyclerView.adapter = videoAnnotationViewAdapter
            }

            CHILD_STEP_VIDEO -> {
                val linearLayoutManager = LinearLayoutManager(this)
                linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
                binding.videoAnnotationRecyclerView.layoutManager = linearLayoutManager
                binding.videoAnnotationRecyclerView.adapter = workflowStepAnnotationAdapter
                binding.videoAnnotationRecyclerView.addItemDecoration(
                    SimpleDividerItemDecoration(
                        ContextCompat.getDrawable(this, co.nayan.canvas.R.drawable.line_divider)
                    )
                )
            }
        }
    }

    private fun setupPlayerControls() {
        binding.exoPlayerView.ivForward.setOnClickListener {
            val toSet = if (frameCount + 3 >= totalFrames) totalFrames
            else frameCount + 3
            binding.exoPlayerView.exoProgress.progress = toSet * 30
            showExoplayerControls()
            videoTypeRecordViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerView.ivRewind.setOnClickListener {
            val toSet = when {
                frameCount > totalFrames -> totalFrames - 3
                frameCount <= 3 -> 0
                else -> frameCount - 3
            }
            binding.exoPlayerView.exoProgress.progress = toSet * 30
            showExoplayerControls()
            videoTypeRecordViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerView.ivForwardByFrame.setOnClickListener {
            val toSet = if (frameCount + 1 >= totalFrames) totalFrames
            else frameCount + 1
            binding.exoPlayerView.exoProgress.progress = toSet * 30
            showExoplayerControls()
            videoTypeRecordViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerView.ivRewindByFrame.setOnClickListener {
            val toSet = when {
                frameCount > totalFrames -> totalFrames - 1
                frameCount <= 1 -> 0
                else -> frameCount - 1
            }
            binding.exoPlayerView.exoProgress.progress = toSet * 30
            showExoplayerControls()
            videoTypeRecordViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerView.exoProgress.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var toSet = progress / 30
                if (toSet >= totalFrames) {
                    isMediaPlaybackCompleted = true
                    toSet = totalFrames
                    pausePlayer()
                }
                frameCount = toSet
                binding.exoPlayerView.exoPosition.text = frameCount.getFormattedTimeStamp()
                playNext()
                if (fromUser) showExoplayerControls()
                videoTypeRecordViewModel.refreshAnnotations(frameCount)
                if (frameCount == totalFrames && binding.exoPlayerView.exoPlayerControlButtonsView.isVisible.not()) {
                    showExoplayerControls()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                pausePlayer()
            }
        })
    }

    private fun populateVideoRecord(record: Record) {
        if (record.mediaUrl == null) showToast(getString(co.nayan.canvas.R.string.video_url_not_found))
        else {
            this.record = record
            val recordId =
                if (BuildConfig.FLAVOR != "qa" && record.isSniffingRecord == true && record.randomSniffingId != null)
                    String.format(
                        getString(co.nayan.canvas.R.string.record_id_text),
                        record.randomSniffingId
                    )
                else String.format(getString(co.nayan.canvas.R.string.record_id_text), record.id)
            binding.recordTxt.text = recordId
            Timber.d(record.toString())
            videoAnnotationViewAdapter.clear()
            binding.questionTxt.text = videoTypeRecordViewModel.question
            binding.previewQuestionTxt.text = videoTypeRecordViewModel.question
            videoTypeRecordViewModel.populateVideoAnnotation(record)
            setupVideoMode()
            setupVideoAnnotationSlider()
            initFramesPlayer(record)

            val annotations = record.annotations()
            isAnswerJunk = annotations.isNotEmpty() && annotations.all { DrawType.JUNK == it.type }

            if (isAnswerJunk) binding.junkAnnotationIv.visible()
            else binding.junkAnnotationIv.gone()
        }
    }

    private fun initFramesPlayer(record: Record) {
        isMediaPlaybackCompleted = false
        frameCount = 0
        framesDir = File(filesDir, "NayanVideoMode/${record.id}")
        totalFrames = framesDir?.listFiles()?.size ?: 1
        binding.exoPlayerView.exoProgress.max = totalFrames * 30
        binding.exoPlayerView.exoProgress.progress = 0
        isMediaPlaying = true
        showExoplayerControls()
    }

    private fun showExoplayerControls() {
        binding.exoPlayerView.exoPlayerControlButtonsView.visible()
        if (videoTypeRecordViewModel.isInChildParentAssociationMode()) {
            binding.questionTxt.gone()
            binding.recordTxt.gone()
            setupParentContainerHint()
        } else {
            binding.questionTxt.visible()
            binding.recordTxt.visible()
            binding.parentAnnotationHintContainer.gone()
        }

        if (isAnswerJunk) binding.junkAnnotationIv.visible()
        else binding.junkAnnotationIv.gone()

        exoplayerControlsHandler.removeCallbacks(hideExoPlayerControlsRunnable)
        exoplayerControlsHandler.postDelayed(hideExoPlayerControlsRunnable, 3000)
    }

    override fun onStop() {
        super.onStop()
        exoplayerControlsHandler.removeCallbacks(hideExoPlayerControlsRunnable)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupParentContainerHint() {
        binding.parentAnnotationHintContainer.visible()
        hideAllParentAnnotationHints()
        videoTypeRecordViewModel.getLastParentAnnotationHint()?.let { videoAnnotationData ->
            if (videoAnnotationData.bitmap == null) {
                binding.parentPhotoView.visible()
                binding.parentPhotoView.setOnTouchListener { _, _ -> false }
            } else {
                videoAnnotationData.bitmap?.let {
                    when (videoAnnotationData.annotations.firstOrNull()?.type) {
                        DrawType.BOUNDING_BOX -> {
                            binding.parentCropView.reset()
                            binding.parentCropView.visible()
                            binding.parentCropView.touchEnabled(false)
                            binding.parentCropView.setBitmapAttributes(
                                it.width,
                                it.height
                            )
                            binding.parentCropView.crops.addAll(
                                videoAnnotationData.annotations
                                    .crops(it)
                            )
                            binding.parentCropView.setImageBitmap(it)
                        }

                        DrawType.QUADRILATERAL -> {
                            binding.parentQuadrilateralView.reset()
                            binding.parentQuadrilateralView.visible()
                            binding.parentQuadrilateralView.touchEnabled(false)
                            binding.parentQuadrilateralView.quadrilaterals.addAll(
                                videoAnnotationData.annotations.quadrilaterals(it)
                            )
                            binding.parentQuadrilateralView.setImageBitmap(it)
                        }

                        DrawType.POLYGON -> {
                            binding.parentPolygonView.reset()
                            binding.parentPolygonView.visible()
                            binding.parentPolygonView.touchEnabled(false)
                            binding.parentPolygonView.points.addAll(
                                videoAnnotationData.annotations.polygonPoints(it)
                            )
                            binding.parentPolygonView.setImageBitmap(it)
                        }

                        DrawType.CONNECTED_LINE -> {
                            binding.parentPaintView.reset()
                            binding.parentPaintView.visible()
                            binding.parentPaintView.touchEnabled(false)
                            binding.parentPaintView.setBitmapAttributes(
                                it.width,
                                it.height
                            )
                            binding.parentPaintView.paintDataList.addAll(
                                videoAnnotationData.annotations
                                    .paintDataList(bitmap = it)
                            )
                            binding.parentPaintView.setImageBitmap(it)
                        }

                        DrawType.SPLIT_BOX -> {
                            binding.parentDragSplitView.reset()
                            binding.parentDragSplitView.visible()
                            binding.parentDragSplitView.touchEnabled(false)
                            binding.parentDragSplitView.setBitmapAttributes(
                                it.width, it.height
                            )
                            binding.parentDragSplitView.splitCropping
                                .addAll(videoAnnotationData.annotations.splitCrops(it))
                            binding.parentDragSplitView.setImageBitmap(it)
                        }

                        else -> {
                            binding.parentPhotoView.visible()
                            binding.parentPhotoView.setOnTouchListener { _, _ -> false }
                            binding.parentPhotoView.setImageBitmap(it)
                        }
                    }
                }
            }
        }
    }

    private fun hideAllParentAnnotationHints() {
        binding.parentCropView.gone()
        binding.parentDragSplitView.gone()
        binding.parentPaintView.gone()
        binding.parentQuadrilateralView.gone()
        binding.parentPolygonView.gone()
        binding.parentPhotoView.gone()
    }

    private fun showVideoDownloadingProgress(isDownloading: Boolean) {
        if (isDownloading) {
            showProgress()
            binding.downloadingPb.isIndeterminate = false
        } else hideProgress()
    }

    private fun showProgress() {
        binding.foregroundDownloadContainer.visible()
        binding.downloadingPb.progress = 0
        binding.progressMessageTxt.text =
            getString(co.nayan.canvas.R.string.downloading_video_message)
    }

    private fun hideProgress() {
        binding.foregroundDownloadContainer.gone()
    }

    private fun updateDownloadingProgress(progress: Int) {
        if (progress >= 96) {
            binding.progressMessageTxt.text =
                getString(co.nayan.canvas.R.string.please_wait_extrating_frames)
            binding.downloadingPb.isIndeterminate = true
        }
        if (binding.foregroundDownloadContainer.isVisible)
            binding.downloadingPb.progress = progress
    }

    override fun showMessage(message: String) {}

    companion object {
        const val PARENT_STEP_VIDEO = 1
        const val CHILD_STEP_VIDEO = 2
    }
}