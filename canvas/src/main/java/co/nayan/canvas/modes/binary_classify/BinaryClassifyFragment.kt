package co.nayan.canvas.modes.binary_classify

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentBinaryClassifyBinding
import co.nayan.canvas.databinding.QuestionContainerBinding
import co.nayan.canvas.viewBinding
import co.nayan.canvas.views.getUserCategoryDrawable
import com.michaelflisar.dragselectrecyclerview.DragSelectTouchListener
import com.michaelflisar.dragselectrecyclerview.DragSelectionProcessor
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BinaryClassifyFragment : AnnotationCanvasFragment(R.layout.fragment_binary_classify) {

    private val selectionMode = DragSelectionProcessor.Mode.Simple
    private var currentSpanCount = 4
    private val templates = mutableListOf<Template>()
    private val binding by viewBinding(FragmentBinaryClassifyBinding::bind)
    private lateinit var questionnaireBinding: QuestionContainerBinding

    private val onItemClickListener = object : DragAndSelectItemClickListener {
        override fun onItemLongClick(position: Int): Boolean {
            mDragSelectTouchListener.startDragSelection(position)
            binding.submitBtnContainer.enabled()
            binding.junkBtnContainer.disabled()
            return true
        }

        override fun onItemClick(recordUrl: String?) {
            //populate single record
        }
    }
    private val recordsAdapter: BinaryRecordsAdapter = BinaryRecordsAdapter(onItemClickListener)

    private val dragSelectionProcessor =
        DragSelectionProcessor(object : DragSelectionProcessor.ISelectionHandler {
            override fun getSelection(): MutableSet<Int> {
                return recordsAdapter.selectedPositions
            }

            override fun isSelected(index: Int): Boolean {
                return recordsAdapter.selectedPositions.contains(index)
            }

            override fun updateSelection(
                start: Int, end: Int, isSelected: Boolean, calledFromOnStart: Boolean
            ) {
                recordsAdapter.selectRange(start, end, isSelected)
            }
        }).withMode(selectionMode)

    private val mDragSelectTouchListener =
        DragSelectTouchListener().withSelectListener(dragSelectionProcessor)

    override fun resetViews(applicationMode: String?, correctAnnotationList: List<AnnotationData>) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val questionnaireLayout = binding.root.findViewById<ConstraintLayout>(R.id.questionLayout)
        questionnaireBinding = DataBindingUtil.bind(questionnaireLayout)!!

        setupViews()
        setupTemplateObserver()
        fetchTemplates()
        binding.recordsView.addOnItemTouchListener(mDragSelectTouchListener)
        setupClicks()
        if (canvasViewModel.isSandbox() && binding.helpBtnContainer.isVisible &&
            canvasViewModel.shouldPlayHelpVideo(canvasViewModel.applicationMode))
            binding.helpBtnContainer.performClick()
    }

    override fun addTemplates(templates: List<Template>) {
        this.templates.clear()
        this.templates.addAll(templates)
    }

    override fun populateAllRecords(records: List<Record>) {
        setRecords(records)
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
        binding.junkBtnContainer.setOnClickListener { showJunkRecordDialog(getString(R.string.these_records_are_junk)) }
        binding.submitBtnContainer.setOnClickListener { showTemplatesDialog() }
        questionnaireBinding.textToSpeechIv.setOnClickListener {
            speakOut(questionnaireBinding.questionTxt.text.toString())
        }
        questionnaireBinding.contrastIv.setOnClickListener(contrastClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        questionnaireBinding.backIv.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.helpBtnContainer.setOnClickListener { moveToLearningVideoScreen() }
    }

    private fun setRecords(records: List<Record>) {
        binding.submitBtnContainer.disabled()
        binding.junkBtnContainer.enabled()
        questionnaireBinding.questionTxt.text = canvasViewModel.question
        questionnaireBinding.tvUserCategoryMedal.apply {
            text = canvasViewModel.userCategory
            val drawable = getUserCategoryDrawable(canvasViewModel.userCategory)
            if (drawable != null) {
                setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0)
                visible()
            } else gone()
        }
        binding.recordsView.visible()
        recordsAdapter.selectionMode = false
        recordsAdapter.addAll(records)
        recordsAdapter.notifyDataSetChanged()
    }

    private val templateConfirmationListener =
        object : TemplateConfirmationListener {
            override fun onConfirm(templateName: String) {
                val selectedRecords = recordsAdapter.getSelectedItems()
                canvasViewModel.submitAnnotationsForBNC(selectedRecords, templateName)
            }
        }

    private fun showTemplatesDialog() {
        childFragmentManager.let {
            ClassSelectionFragment.newInstance(
                templates.distinct(), templateConfirmationListener
            ).show(it, "Templates Dialog")
        }
    }

    fun onBackPressed(): Boolean {
        val isSelectionEnabled = recordsAdapter.onBackPressed()
        if (isSelectionEnabled) {
            binding.submitBtnContainer.disabled()
            binding.junkBtnContainer.enabled()
        }
        return isSelectionEnabled.not()
    }

    override fun enabledJudgmentButtons() {
        binding.junkBtnContainer.enabled()
        binding.submitBtnContainer.enabled()
    }

    override fun disabledJudgmentButtons() {
        binding.junkBtnContainer.disabled()
        binding.submitBtnContainer.disabled()
    }

    override fun setupContrastSlider(progress: Int) {}

    override fun setupViews() {
        super.setupViews()
        binding.submitBtnContainer.disabled()
        questionnaireBinding.prevRecordContainer.gone()
        val gridLayoutManager = GridLayoutManager(context, currentSpanCount)
        binding.recordsView.apply {
            layoutManager = gridLayoutManager
            adapter = recordsAdapter
        }
    }

    override fun setupSandboxView(shouldEnableHintBtn: Boolean) {}

    override fun setupCanvasView() {}

    override fun observeAllRecords(): Boolean {
        return true
    }

    override fun setupHelpButton(applicationMode: String?) {
        if (applicationMode.isNullOrEmpty())
            binding.helpBtnContainer.disabled()
        else binding.helpBtnContainer.enabled()
    }
}