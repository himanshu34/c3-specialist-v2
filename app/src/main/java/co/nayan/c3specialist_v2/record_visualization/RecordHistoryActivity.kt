package co.nayan.c3specialist_v2.record_visualization

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.MediaController
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityRecordHistoryBinding
import co.nayan.c3specialist_v2.datarecords.DataRecordsViewModel
import co.nayan.c3specialist_v2.record_visualization.video_type_record.VideoTypeRecordActivity
import co.nayan.c3specialist_v2.utils.createHistoryRecord
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.RecordAnnotationHistory
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.utils.isVideo
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RecordHistoryActivity : BaseActivity() {

    private val dataRecordViewMode: DataRecordsViewModel by viewModels()
    private val binding: ActivityRecordHistoryBinding by viewBinding(
        ActivityRecordHistoryBinding::inflate
    )

    @Inject
    lateinit var errorUtils: ErrorUtils

    private var record: Record? = null

    private val onRecordHistoryClickListener = object : RecordHistoryClickListener {
        override fun onItemClicked(recordAnnotationHistory: RecordAnnotationHistory) {
            if (record?.mediaType == MediaType.VIDEO) {
                moveToVideoRecordScreen(recordAnnotationHistory)
            }
        }
    }
    private val recordHistoryAdapter = RecordHistoryAdapter(onRecordHistoryClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.appToolbar)

        setupView()
        dataRecordViewMode.state.observe(this, stateObserver)
        setupExtras()
    }

    private fun setupView() {
        binding.historyView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recordHistoryAdapter
        }
    }

    private fun setupExtras() {
        val recordId = intent.getIntExtra(Extras.RECORD_ID, -1)
        if (recordId == -1) showMessage(getString(R.string.record_id_cant_be_null))
        else {
            title = "Record ID: $recordId"
            dataRecordViewMode.fetchRecord(recordId)
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                showProgressDialog(getString(R.string.please_wait_fetching_records))
                binding.historyView.gone()
            }

            is DataRecordsViewModel.FetchDataRecordSuccessState -> {
                hideProgressDialog()
                binding.historyView.visible()
                setupRecord(it.record)
            }

            is ErrorState -> {
                hideProgressDialog()
                binding.historyView.gone()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupRecord(toSet: Record?) {
        toSet?.let {
            record = it
            binding.annotationQuestionTxt.text =
                it.questionAnnotation?.hi ?: it.questionAnnotation?.en
            binding.judgmentQuestionTxt.text =
                it.questionValidation?.hi ?: it.questionValidation?.en
            if (it.recordAnnotations.isNullOrEmpty()) {
                binding.historyView.gone()
                binding.noAnnotationHistoryContainer.noAnnotationHistoryLayout.visible()
                loadNoAnnotationData(it)
            } else {
                binding.historyView.visible()
                binding.noAnnotationHistoryContainer.noAnnotationHistoryLayout.gone()
                recordHistoryAdapter.apply {
                    applicationMode = it.applicationMode
                    displayUrl = if (it.mediaType == MediaType.VIDEO)
                        it.mediaUrl else it.displayImage
                    mediaType = it.mediaType
                    addAll(it.recordAnnotations ?: emptyList())
                }
            }
        }
    }

    private fun loadNoAnnotationData(record: Record) {
        if (record.mediaType == MediaType.VIDEO) {
            binding.noAnnotationHistoryContainer.playerView.gone()
            binding.noAnnotationHistoryContainer.videoViewContainer.visible()
            binding.noAnnotationHistoryContainer.imageContainer.gone()
            binding.noAnnotationHistoryContainer.videoViewContainer.setOnClickListener {
                moveToVideoRecordScreen()
            }
        } else {
            binding.noAnnotationHistoryContainer.loaderIV.gone()
            binding.noAnnotationHistoryContainer.reloadIV.gone()
            binding.noAnnotationHistoryContainer.videoViewContainer.gone()
            binding.noAnnotationHistoryContainer.imageContainer.visible()
            binding.noAnnotationHistoryContainer.playerView.gone()
            record.displayImage?.let { mediaUrl ->
                try {
                    when {
                        mediaUrl.isVideo() -> {
                            binding.noAnnotationHistoryContainer.playerView.visible()
                            binding.noAnnotationHistoryContainer.videoViewContainer.gone()
                            binding.noAnnotationHistoryContainer.imageContainer.gone()
                            try {
                                val mediaController = MediaController(this)
                                binding.noAnnotationHistoryContainer.playerView.apply {
                                    setBackgroundColor(Color.TRANSPARENT)
                                    setVideoURI(Uri.parse(mediaUrl))
                                    setOnPreparedListener {
                                        val mediaProportion: Float =
                                            it.videoHeight.toFloat() / it.videoWidth.toFloat()
                                        layoutParams = this.layoutParams.apply {
                                            width = ViewGroup.LayoutParams.MATCH_PARENT
                                            height =
                                                (this.width.toFloat() * mediaProportion).toInt()
                                        }
                                        it.start()
                                        it.isLooping = true
                                        it.setOnVideoSizeChangedListener { _, _, _ ->
                                            this.setMediaController(mediaController)
                                            mediaController.setAnchorView(this)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        mediaUrl.contains("gif") -> {
                            binding.noAnnotationHistoryContainer.videoViewContainer.gone()
                            binding.noAnnotationHistoryContainer.imageContainer.visible()
                            binding.noAnnotationHistoryContainer.imageContainer.apply {
                                Glide.with(context)
                                    .asGif()
                                    .load(record.displayImage)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .listener(gifRequestListener)
                                    .into(this)
                            }
                        }

                        else -> {
                            binding.noAnnotationHistoryContainer.videoViewContainer.gone()
                            binding.noAnnotationHistoryContainer.imageContainer.visible()
                            binding.noAnnotationHistoryContainer.imageContainer.apply {
                                Glide.with(context)
                                    .asBitmap()
                                    .load(record.displayImage)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .listener(requestListener)
                                    .into(this)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Firebase.crashlytics.recordException(e)
                    Timber.e("${e.printStackTrace()}")
                }
            }
        }
    }

    private val gifRequestListener = object : RequestListener<GifDrawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<GifDrawable>,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapLoadFailed() }
            return false
        }

        override fun onResourceReady(
            resource: GifDrawable,
            model: Any,
            target: Target<GifDrawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapReady() }
            return false
        }
    }

    private val requestListener = object : RequestListener<Bitmap> {
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapLoadFailed() }
            return false
        }

        override fun onResourceReady(
            resource: Bitmap,
            model: Any,
            target: Target<Bitmap>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapReady() }
            return false
        }
    }

    private suspend fun onBitmapLoadFailed() {
        withContext(Dispatchers.Main) {
            binding.noAnnotationHistoryContainer.imageContainer.gone()
            binding.noAnnotationHistoryContainer.reloadIV.visible()
            binding.noAnnotationHistoryContainer.loaderIV.gone()
        }
    }

    private suspend fun onBitmapReady() {
        withContext(Dispatchers.Main) {
            binding.noAnnotationHistoryContainer.imageContainer.visible()
            binding.noAnnotationHistoryContainer.reloadIV.gone()
            binding.noAnnotationHistoryContainer.loaderIV.gone()
        }
    }

    private fun moveToVideoRecordScreen(recordHistory: RecordAnnotationHistory? = null) {
        record?.let {
            Intent(this@RecordHistoryActivity, VideoTypeRecordActivity::class.java).apply {
                putExtra(Extras.APPLICATION_MODE, it.applicationMode)
                putExtra(Extras.RECORD, it.createHistoryRecord(recordHistory))
                putExtra(
                    Extras.QUESTION,
                    "Annotation ${it.questionAnnotation?.en}\nValidation: ${it.questionValidation?.hi}"
                )
                startActivity(this)
            }
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.rootContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}