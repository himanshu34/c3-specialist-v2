package co.nayan.canvas.modes.validate

import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.question
import co.nayan.canvas.JudgmentCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentValidateBinding
import co.nayan.canvas.databinding.QuestionContainerBinding
import co.nayan.canvas.modes.crop.LabelSelectionListener
import co.nayan.canvas.modes.crop.LabelsAdapter
import co.nayan.canvas.utils.*
import co.nayan.canvas.viewBinding
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import co.nayan.canvas.views.getUserCategoryDrawable
import co.nayan.canvas.widgets.RecordInfoDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.yuyakaido.android.cardstackview.Direction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ValidateFragment : JudgmentCanvasFragment(R.layout.fragment_validate) {

    private val cardRecordListener = object : CardRecordListener {
        override fun setupQuestion(answer: String?) {
            questionnaireBinding.questionTxt.text = canvasViewModel.question?.question(answer)
        }

        override fun toggleContrast(status: Boolean?) {
            if (status == true) questionnaireBinding.contrastIv.visible()
            else questionnaireBinding.contrastIv.gone()
        }
    }

    private lateinit var cardStackAdapter: CardStackAdapter
    private val binding by viewBinding(FragmentValidateBinding::bind)
    private lateinit var questionnaireBinding: QuestionContainerBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val questionnaireLayout = binding.root.findViewById<ConstraintLayout>(R.id.questionLayout)
        questionnaireBinding = DataBindingUtil.bind(questionnaireLayout)!!
        cardStackAdapter =
            CardStackAdapter(canvasViewModel.applicationMode, cardRecordListener) { record ->
                record?.let { setupRecordInfoDialog(it) }
            }
        setupViews()
        initViews()
        setupClicks()
        if (canvasViewModel.isSandbox() && binding.helpBtn.isVisible &&
            canvasViewModel.shouldPlayHelpVideo(canvasViewModel.applicationMode)
        ) binding.helpBtn.performClick()
    }

    private fun setupViews() {
        questionnaireBinding.prevRecordContainer.gone()
        questionnaireBinding.recordIdTxt.gone()
        questionnaireBinding.overlayTransparentIv.gone()
        questionnaireBinding.tvUserCategoryMedal.apply {
            text = canvasViewModel.userCategory
            val drawable = getUserCategoryDrawable(canvasViewModel.userCategory)
            if (drawable != null) {
                setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0)
                visible()
            } else gone()
        }
        if (canvasViewModel.applicationMode == Mode.MCML) {
            canvasViewModel.labelList = listOf()
            binding.recyclerView.visible()
            setupTemplateObserver()
            canvasViewModel.fetchTemplates()
        } else binding.recyclerView.gone()
    }

    private fun setupTemplateObserver() {
        canvasViewModel.templateState.observe(viewLifecycleOwner) {
            when (it) {
                is BaseCanvasViewModel.TemplatesSuccessState -> {
                    canvasViewModel.labelList = it.templates.sortedBy { t -> t.templateName }
                    addTemplates(canvasViewModel.labelList)
                }

                is BaseCanvasViewModel.TemplatesFailedState -> {
                    it.message?.let { it1 -> showMessage(it1) }
                }
            }
        }
    }

    private val labelSelectionListener = object : LabelSelectionListener {
        override fun onSelect(template: Template) {
            val templatePosition = canvasViewModel.labelList.indexOf(template)
            canvasViewModel.labelAdapter?.apply {
                if (selectedPosition == templatePosition) {
                    selectedPosition = RecyclerView.NO_POSITION
                    cardStackAdapter.toggleView(null)
                    cardStackAdapter.notifyItemChanged(currentCardPosition)
                } else {
                    selectedPosition = templatePosition
                    cardStackAdapter.toggleView(template)
                    cardStackAdapter.notifyItemChanged(currentCardPosition)
                }
                notifyDataSetChanged()
            }
        }
    }

    private fun addTemplates(templates: List<Template>) = lifecycleScope.launch {
        canvasViewModel.labelAdapter = LabelsAdapter(labelSelectionListener)
        binding.recyclerView.apply {
            layoutManager =
                LinearLayoutManager(requireActivity(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = canvasViewModel.labelAdapter
        }
        canvasViewModel.labelAdapter?.addAll(templates)
    }

    private fun initViews() {
        binding.cardStackView.layoutManager =
            CustomCardStackLayoutManager(context, cardSwipeListener)
        cardStackAdapter.colorFilter = ImageUtils.getColorMatrix(canvasViewModel.getContrast())
        cardStackAdapter.appFlavor = appFlavor()
        binding.cardStackView.adapter = cardStackAdapter
    }

    private val cardSwipeListener = object : CardSwipeListener() {
        override fun onCardAppeared(view: View?, position: Int) {
            currentCardPosition = position
            cardStackAdapter.toggleView(null)
            updateContrast(cardStackAdapter.colorFilter)
            canvasViewModel.labelAdapter?.apply {
                resetViews()
                val currentItem = cardStackAdapter.getItem(currentCardPosition)
                updateAnnotationsForValidation(currentItem.annotations())
            }
        }

        override fun onCardSwiped(direction: Direction?) {
            val isTrue = direction == Direction.Right
            submitJudgement(isTrue)
        }
    }

    override fun populateAllRecords(records: List<Record>) {
        canvasViewModel.labelAdapter?.apply {
            resetViews()
            updateAnnotationsForValidation(records.first().annotations())
        }
        val previousCount = cardStackAdapter.itemCount
        val addCount = cardStackAdapter.addAll(records)
        if (binding.cardStackView.isComputingLayout.not()) {
            cardStackAdapter.notifyItemRangeChanged(previousCount, addCount)
        }
    }

    override fun populateRecord(record: Record) {}

    private fun setupRecordInfoDialog(record: Record) = lifecycleScope.launch {
        childFragmentManager.fragments.forEach {
            if (it is RecordInfoDialogFragment) {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val recordInfoDialogFragment = RecordInfoDialogFragment.newInstance(record)
        recordInfoDialogFragment.show(
            childFragmentManager.beginTransaction(),
            getString(R.string.record_info)
        )
    }

    private val contrastClickListener = View.OnClickListener {
        if (binding.contrastSlider.isVisible) {
            binding.contrastSlider.gone()
            questionnaireBinding.contrastIv.unSelected()
        } else {
            binding.contrastSlider.visible()
            questionnaireBinding.contrastIv.selected()
        }
    }

    private fun setupClicks() {
        questionnaireBinding.textToSpeechIv.setOnClickListener { speakOut(questionnaireBinding.questionTxt.text.toString()) }
        questionnaireBinding.contrastIv.setOnClickListener(contrastClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        binding.trueBtn.setOnClickListener { binding.cardStackView.swipeRight() }
        binding.falseBtn.setOnClickListener { binding.cardStackView.swipeLeft() }
        questionnaireBinding.backIv.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.undoBtn.setOnClickListener {
            if (canvasViewModel.isSubmittingRecords) return@setOnClickListener
            canvasViewModel.labelAdapter?.resetViews()
            undoJudgment()
        }
        binding.helpBtn.setOnClickListener { moveToLearningVideoScreen() }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        val isSameColorFilter = colorFilter == cardStackAdapter.colorFilter
        if (isSameColorFilter.not() && binding.cardStackView.isComputingLayout.not() &&
            currentCardPosition < cardStackAdapter.itemCount
        ) {
            cardStackAdapter.colorFilter = colorFilter
            cardStackAdapter.notifyItemChanged(currentCardPosition)
        } else return
    }

    override fun enableUndo(isEnabled: Boolean) {
        binding.undoBtn.isEnabled = isEnabled
    }

    override fun undo(i: Int): Int {
        currentCardPosition = i
        binding.cardStackView.scrollToPosition(i)
        updateContrast(ImageUtils.getColorMatrix(canvasViewModel.getContrast()))
        if (::cardStackAdapter.isInitialized) {
            try {
                canvasViewModel.labelAdapter?.apply {
                    val currentItem = cardStackAdapter.getItem(currentCardPosition)
                    updateAnnotationsForValidation(currentItem?.annotations() ?: emptyList())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return super.undo(i)
    }

    override fun enabledJudgmentButtons() {
        binding.trueBtn.enabled()
        binding.falseBtn.enabled()
        binding.cardStackView.enableSwipe()
    }

    override fun disabledJudgmentButtons() {
        binding.trueBtn.disabled()
        binding.falseBtn.disabled()
        binding.cardStackView.disableSwipe()
    }

    override fun setupHelpButton(applicationMode: String?) {
        if (applicationMode.isNullOrEmpty()) binding.helpBtn.disabled()
        else binding.helpBtn.enabled()
    }

    override fun observeAllRecords(): Boolean {
        return true
    }

    override fun setupContrastSlider(progress: Int) {
        binding.contrastSlider.progress = progress
    }
}