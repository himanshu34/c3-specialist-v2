package co.nayan.c3specialist_v2.applanguage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.AppLanguage
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.LayoutLanguageSelectorBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LanguageSelectionDialogFragment : BottomSheetDialogFragment() {

    private var currentLanguage: String? = null
    private val appLanguageViewModel: AppLanguageViewModel by viewModels()
    private lateinit var binding: LayoutLanguageSelectorBinding

    @Inject
    lateinit var errorUtils: ErrorUtils

    companion object {
        private const val IS_CANCELABLE = "isCancelable"
        private const val CURRENT_LANGUAGE = "currentLanguage"

        fun newInstance(
            isCancelable: Boolean,
            currentLanguage: String?
        ): LanguageSelectionDialogFragment {
            val fragment = LanguageSelectionDialogFragment()
            val args = Bundle()
            args.apply { putBoolean(IS_CANCELABLE, isCancelable) }
            args.apply { putString(CURRENT_LANGUAGE, currentLanguage) }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        arguments?.let {
            isCancelable = it.getBoolean(IS_CANCELABLE)
            currentLanguage = it.getString(CURRENT_LANGUAGE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutLanguageSelectorBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appLanguageViewModel.state.observe(viewLifecycleOwner, stateObserver)
        setupLanguage(appLanguageViewModel.getAppLanguage(), true)
        binding.hindiContainer.setOnClickListener { setupLanguage(AppLanguage.HINDI) }
        binding.englishContainer.setOnClickListener { setupLanguage(AppLanguage.ENGLISH) }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressBar.visible()
                binding.englishContainer.disabled()
                binding.hindiContainer.disabled()
            }

            AppLanguageViewModel.LanguageUpdateDuplicateState -> {
                binding.englishContainer.enabled()
                binding.hindiContainer.enabled()
                binding.progressBar.gone()
                dismiss()
            }

            AppLanguageViewModel.LanguageUpdateSuccessState -> {
                binding.englishContainer.enabled()
                binding.hindiContainer.enabled()
                binding.progressBar.gone()
                restartActivity()
                dismiss()
            }

            is ErrorState -> {
                binding.englishContainer.enabled()
                binding.hindiContainer.enabled()
                if (binding.progressBar.isVisible) binding.progressBar.gone()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupLanguage(
        language: String?,
        isFirstTime: Boolean = false
    ) = lifecycleScope.launch {
        currentLanguage = language
        if (isFirstTime.not() && language != null)
            appLanguageViewModel.updateLanguage(language)
        setupView()
    }

    private fun setupView() = lifecycleScope.launch {
        when (currentLanguage) {
            AppLanguage.ENGLISH -> {
                binding.englishContainer.selected()
                binding.hindiContainer.unSelected()
                binding.hindiCheck.gone()
                binding.englishCheck.visible()
            }

            AppLanguage.HINDI -> {
                binding.englishContainer.unSelected()
                binding.hindiContainer.selected()
                binding.hindiCheck.visible()
                binding.englishCheck.gone()
            }

            else -> {
                binding.englishContainer.unSelected()
                binding.hindiContainer.unSelected()
                binding.hindiCheck.gone()
                binding.englishCheck.gone()
            }
        }
    }

    private fun restartActivity() {
        startActivity(Intent(requireActivity(), DashboardActivity::class.java))
        requireActivity().finish()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }
}
