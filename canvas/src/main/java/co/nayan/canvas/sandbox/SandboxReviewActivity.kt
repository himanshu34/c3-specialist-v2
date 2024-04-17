package co.nayan.canvas.sandbox

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import co.nayan.appsession.SessionActivity
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.config.Thresholds.CROP_ERROR_IGNORANCE_THRESHOLD
import co.nayan.canvas.databinding.ActivitySandboxReviewBinding
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.canvas.interfaces.SandboxRepositoryInterface
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.ImageCachingManager
import co.nayan.canvas.viewBinding
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import co.nayan.canvas.viewmodels.CanvasViewModelFactory
import co.nayan.canvas.viewmodels.VideoDownloadProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SandboxReviewActivity : SessionActivity() {

    private lateinit var sandboxViewModel: BaseCanvasViewModel
    private val binding: ActivitySandboxReviewBinding by viewBinding(ActivitySandboxReviewBinding::inflate)

    @Inject
    lateinit var canvasRepositoryInterface: CanvasRepositoryInterface

    @Inject
    lateinit var sandboxRepositoryInterface: SandboxRepositoryInterface

    @Inject
    lateinit var imageCachingManager: ImageCachingManager

    @Inject
    lateinit var videoDownloadProvider: VideoDownloadProvider

    @Inject
    lateinit var ffmPegExtraction: FFMPegExtraction

    private lateinit var sandboxRecordsAdapter: SandboxRecordsAdapter
    private var spanCount = 2
    private var wfStep: WfStep? = null

    private fun isAdapterInitialized() =
        this@SandboxReviewActivity::sandboxRecordsAdapter.isInitialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.sandbox_records)

        initViewModel()
        setupData()
        setupViewsAndData()
        sandboxViewModel.state.observe(this, stateObserver)
        if (sandboxViewModel.areRecordsFetched.not())
            sandboxViewModel.fetchRecordsFirstTime()
        setupClicks()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_item, menu)
        val menuItem = menu.findItem(R.id.search)
        val searchView = menuItem?.actionView as SearchView
        searchView.inputType = InputType.TYPE_CLASS_NUMBER
        searchView.queryHint = getString(R.string.search_by_record_id)
        searchView.setOnQueryTextListener(queryTextListener)
        return super.onPrepareOptionsMenu(menu)
    }

    private val queryTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            val items = sandboxRecordsAdapter.filterListItems(query)
            updateRecordsView(items)
            return false
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            val items = sandboxRecordsAdapter.filterListItems(newText)
            updateRecordsView(items)
            return false
        }
    }

    private fun updateRecordsView(items: List<Record>) {
        if (items.isNullOrEmpty()) {
            binding.recordsView.gone()
            binding.noRecordsContainer.visible()
        } else {
            binding.recordsView.visible()
            binding.noRecordsContainer.gone()
        }
    }

    private fun initViewModel() {
        val isSandbox = this::class.java.name == SandboxReviewActivity::class.java.name
        sandboxViewModel = ViewModelProvider(
            this,
            CanvasViewModelFactory(
                canvasRepositoryInterface,
                sandboxRepositoryInterface,
                isSandbox,
                imageCachingManager,
                videoDownloadProvider,
                ffmPegExtraction
            )
        )[BaseCanvasViewModel::class.java]
    }

    private fun setupData() {
        sandboxViewModel.workType = WorkType.ANNOTATION
        wfStep = intent.parcelable("wfStep")
        sandboxViewModel.setTrainingId(wfStep?.sandboxId ?: 0)
        sandboxViewModel.question = wfStep?.question ?: ""
        sandboxViewModel.role = Role.ADMIN
        sandboxViewModel.applicationMode = wfStep?.applicationModeName
        sandboxViewModel.wfStepId = wfStep?.id
        sandboxViewModel.mediaType = wfStep?.mediaType
        sandboxViewModel.annotationVariationThreshold =
            wfStep?.annotationVariationThreshold ?: CROP_ERROR_IGNORANCE_THRESHOLD
        setMetaData(
            null,
            wfStep?.id,
            sandboxViewModel.workType,
            sandboxViewModel.role
        )
    }

    private fun setupClicks() {
        binding.hideOverlayIv.setOnTouchListener(hideOverlayIvTouchListener)
        binding.gridSelectorIv.setOnClickListener {
            if (it.isSelected) {
                it.unSelected()
                spanCount -= 1
            } else {
                it.selected()
                spanCount += 1
            }
            sandboxViewModel.saveSpanCount(spanCount)
            setupRecordsView()
        }

        binding.contrastIv.setOnClickListener {
            if (it.isSelected) {
                it.unSelected()
                binding.contrastSlider.gone()
            } else {
                it.selected()
                binding.contrastSlider.visible()
            }
        }

        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangeListener)
    }

    private val onSeekBarChangeListener = object : OnSeekBarChangeListener() {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            sandboxViewModel.saveContrastValue(progress)
            sandboxRecordsAdapter.contrast = ImageUtils.getColorMatrix(progress)
            sandboxRecordsAdapter.notifyDataSetChanged()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val hideOverlayIvTouchListener = View.OnTouchListener { _, event ->
        if (isAdapterInitialized()) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sandboxRecordsAdapter.showOverlay = false
                    sandboxRecordsAdapter.notifyDataSetChanged()
                }

                MotionEvent.ACTION_UP -> {
                    sandboxRecordsAdapter.showOverlay = true
                    sandboxRecordsAdapter.notifyDataSetChanged()
                }
            }
        }
        true
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {
                binding.progressOverlay.gone()
                sandboxViewModel.setupUndoRecordState()
                populateAllRecords(sandboxViewModel.records.value ?: emptyList())
            }

            ProgressState -> binding.progressOverlay.visible()
        }
    }

    private fun populateAllRecords(records: List<Record>) {
        if (records.isNullOrEmpty()) {
            binding.recordsView.gone()
            binding.noRecordsContainer.visible()
        } else {
            binding.recordsView.visible()
            binding.noRecordsContainer.gone()
            if (isAdapterInitialized()) {
                sandboxRecordsAdapter.addAll(records)
            }
        }
    }

    private fun setupViewsAndData() {
        spanCount = sandboxViewModel.getSpanCount()
        binding.gridSelectorIv.isSelected = (spanCount == 2)
        binding.contrastSlider.progress = sandboxViewModel.getContrast()
        sandboxRecordsAdapter = SandboxRecordsAdapter(
            sandboxViewModel.appFlavor,
            sandboxViewModel.applicationMode,
            ImageUtils.getColorMatrix(sandboxViewModel.getContrast())
        ) { sandboxResultCallback.launch(SandboxCallbackInput(wfStep, it)) }
        setupRecordsView()
        binding.recordsView.adapter = sandboxRecordsAdapter
    }

    private fun setupRecordsView() {
        if (isAdapterInitialized()) {
            val gridLayoutManager = GridLayoutManager(this, spanCount)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return 1
                }
            }
            binding.recordsView.layoutManager = gridLayoutManager
        }
    }

    private val sandboxResultCallback =
        registerForActivityResult(SandboxResultCallback()) { dataRequest ->
            // If sandbox submitted successfully refresh the view
            dataRequest?.let {
                sandboxRecordsAdapter.refreshRecord(dataRequest.recordId, dataRequest.annotation)
            }
        }
}