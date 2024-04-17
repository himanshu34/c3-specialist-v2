package co.nayan.review.recordsreview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.appsession.SessionActivity
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.ImageUtils
import co.nayan.c3v2.core.utils.OnSeekBarChangeListener
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.core.widgets.CustomAlertDialogFragment
import co.nayan.c3v2.core.widgets.CustomAlertDialogListener
import co.nayan.c3v2.core.widgets.ProgressDialogFragment
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.question
import co.nayan.review.R
import co.nayan.review.config.Extras
import co.nayan.review.config.Tag
import co.nayan.review.databinding.ActivityReviewModeBinding
import co.nayan.review.incorrectreviews.IncorrectReviewsActivity
import co.nayan.review.models.ReviewIntentInputData
import co.nayan.review.recordsgallery.RecordItem
import co.nayan.review.recordsgallery.ReviewActivity
import co.nayan.review.recordsgallery.ReviewViewModel
import co.nayan.review.utils.CardSwipeListener
import co.nayan.review.utils.CustomCardStackLayoutManager
import co.nayan.review.utils.TextToSpeechConstants
import co.nayan.review.utils.TextToSpeechUtils
import co.nayan.review.utils.disableSwipe
import co.nayan.review.utils.enableSwipe
import co.nayan.review.utils.getUserCategoryDrawable
import co.nayan.review.utils.swipeLeft
import co.nayan.review.utils.swipeRight
import co.nayan.review.viewBinding
import co.nayan.review.widgets.ManagerDisabledAlertFragment
import co.nayan.review.widgets.ManagerDisabledDialogListener
import co.nayan.review.widgets.RecordInfoDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.yuyakaido.android.cardstackview.Direction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ReviewModeActivity : SessionActivity() {

    private val binding: ActivityReviewModeBinding by viewBinding(ActivityReviewModeBinding::inflate)
    private var currentCardPosition = 0
    private val reviewViewModel: ReviewViewModel by viewModels()

    @Inject
    lateinit var errorUtils: ErrorUtils
    private lateinit var textToSpeech: TextToSpeech
    private val cardRecordListener = object : CardRecordListener {
        override fun setupQuestion(answer: String?) {
            binding.questionLayout.questionTxt.text = reviewViewModel.question?.question(answer)
        }

        override fun toggleContrast(status: Boolean?) {
            if (status == true) binding.questionLayout.contrastIv.visible()
            else binding.questionLayout.contrastIv.gone()
        }
    }
    private lateinit var cardStackAdapter: CardStackAdapter
    private var labels: List<Template> = listOf()
    private var labelAdapter: LabelsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        textToSpeech = TextToSpeech(this, textToSpeechListener, "com.google.android.tts")

        setupExtras()
        initViews()
        setupViews()
        setupClicks()
        reviewViewModel.canUndo.observe(this, undoRecordObserver)
        reviewViewModel.contrastValue.observe(this, contrastObserver)
        reviewViewModel.state.observe(this, stateObserver)
        reviewViewModel.records.observe(this, recordsObserver)
        reviewViewModel.fetchRecords()
    }

    private fun setupExtras() {
        reviewViewModel.appFlavor = intent.getStringExtra(APP_FLAVOR)
        val workAssignment = intent.parcelable<WorkAssignment>(WORK_ASSIGNMENT)
        if (workAssignment != null) {
            reviewViewModel.workAssignmentId = workAssignment.id
            reviewViewModel.wfStepId = workAssignment.wfStep?.id
            reviewViewModel.applicationMode = workAssignment.applicationMode
            reviewViewModel.userCategory = workAssignment.userCategory
            reviewViewModel.question = workAssignment.wfStep?.question
            setMetaData(
                workAssignment.id,
                workAssignment.wfStep?.id,
                workAssignment.workType,
                reviewViewModel.currentRole()
            )
        } else this@ReviewModeActivity.finish()
    }

    private fun setupViews() {
        if (reviewViewModel.applicationMode == Mode.MCML) {
            labels = listOf()
            binding.recyclerView.visible()
            reviewViewModel.templateState.observe(this, templateObserver)
            reviewViewModel.fetchTemplates()
        } else binding.recyclerView.gone()
    }

    override fun onResume() {
        super.onResume()
        supportFragmentManager.findFragmentByTag(getString(R.string.warning))?.let {
            if (it is SniffingIncorrectWarningDialogFragment)
                it.setViewModel(reviewViewModel)
        }
    }

    private val templateObserver: Observer<ActivityState> = Observer {
        when (it) {
            is ReviewViewModel.TemplatesSuccessState -> {
                addTemplates(it.templates)
            }
        }
    }

    private val undoRecordObserver: Observer<Boolean> = Observer {
        binding.undoBtn.isEnabled = it
    }

    private val contrastObserver: Observer<Int> = Observer {
        if (::cardStackAdapter.isInitialized) {
            binding.contrastSlider.progress = it
            cardStackAdapter.colorFilter = ImageUtils.getColorMatrix(it)
            cardStackAdapter.notifyItemChanged(currentCardPosition)
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> showProgressDialog(getString(R.string.fetching_records))
            InitialState -> hideProgressDialog()
            ReviewViewModel.SniffingIncorrectWarningState -> {
                showSniffingIncorrectWarningDialog()
            }

            ReviewViewModel.RecordsFinishedState -> {
                hideProgressDialog()
                val message = getString(R.string.no_more_records)
                showAlert(
                    message = message,
                    shouldFinish = true,
                    tag = Tag.RECORDS_FINISHED,
                    showPositiveBtn = true,
                    isCancelable = false
                )
            }

            ReviewViewModel.RecordSubmissionProgressState -> {
                showProgressDialog(getString(R.string.submitting_records_message))
            }

            ReviewViewModel.FailureState -> {
                hideProgressDialog()
                showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }

            ReviewViewModel.UserAccountLockedState -> {
                hideProgressDialog()
                showAccountLockedDialog()
            }

            is ErrorState -> {
                hideProgressDialog()
                showAlert(
                    message = errorUtils.parseExceptionMessage(it.exception),
                    shouldFinish = true,
                    tag = Tag.ERROR,
                    showPositiveBtn = true
                )
            }
        }
    }

    private fun showSniffingIncorrectWarningDialog() {
        supportFragmentManager.fragments.forEach {
            if (it is SniffingIncorrectWarningDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            }
        }

        val sniffingWarningDialog =
            SniffingIncorrectWarningDialogFragment.newInstance(getString(R.string.incorrect_sniffing_warning))
        sniffingWarningDialog.setViewModel(reviewViewModel)
        sniffingWarningDialog.show(
            supportFragmentManager.beginTransaction(),
            getString(R.string.warning)
        )
        reviewViewModel.submitIncorrectSniffingRecords()
    }

    private val recordsObserver: Observer<List<RecordItem>> = Observer {
        reviewViewModel.setupUndoRecordState()
        populateAllRecords(it)
    }

    private val labelSelectionListener = object : LabelSelectionListener {
        override fun onSelect(template: Template) {
            val templatePosition = labels.indexOf(template)
            if (labelAdapter?.selectedPosition == templatePosition) {
                labelAdapter?.selectedPosition = RecyclerView.NO_POSITION
                cardStackAdapter.toggleView(null)
                cardStackAdapter.notifyItemChanged(currentCardPosition)
            } else {
                labelAdapter?.selectedPosition = templatePosition
                cardStackAdapter.toggleView(template)
                cardStackAdapter.notifyItemChanged(currentCardPosition)
            }
            labelAdapter?.notifyDataSetChanged()
        }
    }

    private fun addTemplates(templates: List<Template>) {
        labels = templates
        labelAdapter = LabelsAdapter(labelSelectionListener)
        binding.recyclerView.apply {
            layoutManager =
                LinearLayoutManager(this@ReviewModeActivity, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = labelAdapter
        }
        labelAdapter?.addAll(labels)
    }

    private fun initViews() {
        cardStackAdapter = CardStackAdapter(
            reviewViewModel.appFlavor,
            reviewViewModel.applicationMode,
            cardRecordListener
        ) { record ->
            record?.let { setupRecordInfoDialog(it) }
        }
        binding.cardStackView.apply {
            layoutManager = CustomCardStackLayoutManager(this@ReviewModeActivity, cardSwipeListener)
            adapter = cardStackAdapter
            reviewViewModel._contrastValue.value?.let {
                cardStackAdapter.colorFilter = ImageUtils.getColorMatrix(it)
            }
        }
    }

    private fun setupRecordInfoDialog(record: Record) = lifecycleScope.launch {
        supportFragmentManager.fragments.forEach {
            if (it is RecordInfoDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val recordInfoDialogFragment = RecordInfoDialogFragment.newInstance(record)
        recordInfoDialogFragment.show(
            supportFragmentManager.beginTransaction(),
            getString(R.string.record_info)
        )
    }

    private val cardSwipeListener = object : CardSwipeListener() {
        override fun onCardAppeared(view: View?, position: Int) {
            currentCardPosition = position
            cardStackAdapter.toggleView(null)
            updateContrast(cardStackAdapter.colorFilter)
            labelAdapter?.resetViews()
            val currentItem = cardStackAdapter.getItem(currentCardPosition)
            labelAdapter?.updateAnnotationsForValidation(currentItem?.record.annotations())
        }

        override fun onCardSwiped(direction: Direction?) {
            val isTrue = direction == Direction.Right
            reviewViewModel.submitReview(isTrue)
            reviewViewModel.processNextRecord()
            reviewViewModel.setupUndoRecordState()
        }
    }

    private fun populateAllRecords(records: List<RecordItem>) {
        enabledJudgmentButtons()
        binding.questionLayout.prevRecordContainer.gone()
        binding.questionLayout.recordIdTxt.gone()
        binding.questionLayout.overlayTransparentIv.gone()
        binding.questionLayout.tvUserCategoryMedal.apply {
            text = reviewViewModel.userCategory
            val drawable = getUserCategoryDrawable(reviewViewModel.userCategory)
            if (drawable != null) {
                setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0)
                visible()
            } else gone()
        }
        labelAdapter?.resetViews()
        labelAdapter?.updateAnnotationsForValidation(records.first().record.annotations())
        if (isCardStackAdapterInitialized()) {
            val previousCount = cardStackAdapter.itemCount
            val addCount = cardStackAdapter.addAll(records)
            if (binding.cardStackView.isComputingLayout.not()) {
                cardStackAdapter.notifyItemRangeChanged(previousCount, addCount)
            }
        }
    }

    private val contrastClickListener = View.OnClickListener {
        if (binding.contrastSlider.isVisible) {
            binding.contrastSlider.gone()
            binding.questionLayout.contrastIv.unSelected()
        } else {
            binding.contrastSlider.visible()
            binding.questionLayout.contrastIv.selected()
        }
    }

    private fun setupClicks() {
        binding.questionLayout.textToSpeechIv.setOnClickListener {
            speakOut(binding.questionLayout.questionTxt.text.toString())
        }
        binding.questionLayout.contrastIv.setOnClickListener(contrastClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangeListener)
        binding.trueBtn.setOnClickListener { binding.cardStackView.swipeRight() }
        binding.falseBtn.setOnClickListener { binding.cardStackView.swipeLeft() }
        binding.questionLayout.backIv.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.undoBtn.setOnClickListener {
            if (reviewViewModel.isSubmittingRecords) return@setOnClickListener
            labelAdapter?.resetViews()
            undoReview()
        }
    }

    private fun speakOut(text: String?) {
        textToSpeech.stop()
        if (text.isNullOrEmpty()) {
            return
        } else {
            val updatedText = TextToSpeechUtils.getSeparatedTextByNumbers(text)
            textToSpeech.speak(updatedText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private val textToSpeechListener = TextToSpeech.OnInitListener {
        if (it == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.getDefault()
            textToSpeech.setPitch(TextToSpeechConstants.PITCH)
            textToSpeech.setSpeechRate(TextToSpeechConstants.SPEED_RATE)
        }
    }

    private val onSeekBarChangeListener = object : OnSeekBarChangeListener() {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            reviewViewModel.saveContrastValue(progress)
        }
    }

    private fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        if (isCardStackAdapterInitialized()) {
            val isSameColorFilter = (colorFilter == cardStackAdapter.colorFilter)
            if (isSameColorFilter.not() && binding.cardStackView.isComputingLayout.not() &&
                currentCardPosition < cardStackAdapter.itemCount
            ) {
                cardStackAdapter.colorFilter = colorFilter
                cardStackAdapter.notifyItemChanged(currentCardPosition)
            } else return
        } else return
    }

    private fun undoReview() {
        undo(currentCardPosition - 1)
        reviewViewModel.undoReview()
        reviewViewModel.setupUndoRecordState()
    }

    private fun undo(i: Int) {
        currentCardPosition = i
        binding.cardStackView.scrollToPosition(i)
        reviewViewModel._contrastValue.value?.let {
            updateContrast(ImageUtils.getColorMatrix(it))
        }
        try {
            val currentItem = cardStackAdapter.getItem(currentCardPosition)
            labelAdapter?.updateAnnotationsForValidation(currentItem?.record.annotations())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun disableJudgementButtons() {
        binding.trueBtn.disabled()
        binding.falseBtn.disabled()
        binding.cardStackView.disableSwipe()
    }

    private fun enabledJudgmentButtons() {
        binding.trueBtn.enabled()
        binding.falseBtn.enabled()
        binding.cardStackView.enableSwipe()
    }

    private fun isCardStackAdapterInitialized() =
        this@ReviewModeActivity::cardStackAdapter.isInitialized

    private fun showProgressDialog(message: String) {
        hideProgressDialog()
        val progressDialog = ProgressDialogFragment()
        progressDialog.setMessage(message)
        progressDialog.show(supportFragmentManager.beginTransaction(), message)
    }

    private fun hideProgressDialog() {
        supportFragmentManager.fragments.forEach {
            if (it is ProgressDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.rootView, message, Snackbar.LENGTH_SHORT).show()
    }

    private val customAlertDialogListener = object : CustomAlertDialogListener {
        override fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?) {
            when (tag) {
                Tag.SUBMIT_RECORDS -> {
                    reviewViewModel.submitRecords()
                }
            }
            if (shouldFinish) {
                this@ReviewModeActivity.finish()
            }
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?) {}
    }

    private fun showAlert(
        message: String,
        shouldFinish: Boolean,
        tag: String,
        title: String? = null,
        positiveText: String? = null,
        negativeText: String? = null,
        showPositiveBtn: Boolean = false,
        showNegativeBtn: Boolean = false,
        isCancelable: Boolean = true
    ) {
        supportFragmentManager.fragments.forEach {
            if (it is CustomAlertDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }

        val customAlertDialogFragment =
            CustomAlertDialogFragment.newInstance(customAlertDialogListener).apply {
                setTitle(title)
                setMessage(message)
                showNegativeBtn(showNegativeBtn)
                showPositiveBtn(showPositiveBtn)
                shouldFinish(shouldFinish)
                if (positiveText != null)
                    setPositiveBtnText(positiveText)
                if (negativeText != null)
                    setNegativeBtnText(negativeText)
            }
        customAlertDialogFragment.isCancelable = isCancelable
        customAlertDialogFragment.show(supportFragmentManager.beginTransaction(), tag)
    }

    private val managerDisabledDialogListener = object : ManagerDisabledDialogListener {
        override fun onPositiveBtnClick() {
            setResult(
                Activity.RESULT_OK,
                Intent().apply { putExtra(ACCOUNT_LOCKED, true) }
            )
            this@ReviewModeActivity.finish()
        }

        override fun onNegativeBtnClick() {
            moveToIncorrectReviewsScreen()
        }
    }

    private fun moveToIncorrectReviewsScreen() {
        Intent(this@ReviewModeActivity, IncorrectReviewsActivity::class.java).apply {
            putExtra(Extras.APPLICATION_MODE, reviewViewModel.applicationMode)
            putExtra(Extras.CONTRAST_VALUE, reviewViewModel.contrastValue.value)
            putParcelableArrayListExtra(
                Extras.RECORDS,
                reviewViewModel.getIncorrectSniffingRecords()
            )
            putExtra(Extras.QUESTION, reviewViewModel.question)
            putExtra(ReviewActivity.APP_FLAVOR, intent.getStringExtra(ReviewActivity.APP_FLAVOR))
            startActivity(this)
        }
    }

    private fun showAccountLockedDialog() {
        ManagerDisabledAlertFragment.newInstance(
            managerDisabledDialogListener,
            getString(R.string.your_account_is_locked_due_to_wrong_submission_of_records)
        ).show(supportFragmentManager.beginTransaction(), "Account Locked")
    }

    class ResultCallback : ActivityResultContract<ReviewIntentInputData, Boolean>() {
        override fun createIntent(context: Context, input: ReviewIntentInputData): Intent {
            return Intent(context, ReviewModeActivity::class.java).apply {
                putExtra(WORK_ASSIGNMENT, input.workAssignment)
                putExtra(APP_FLAVOR, input.appFlavor)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == Activity.RESULT_OK &&
                    intent?.getBooleanExtra(ACCOUNT_LOCKED, false) ?: false
        }
    }

    companion object {
        const val APP_FLAVOR = "app_flavor"
        const val WORK_ASSIGNMENT = "work_assignment"
        const val ACCOUNT_LOCKED = "account_locked"
    }
}