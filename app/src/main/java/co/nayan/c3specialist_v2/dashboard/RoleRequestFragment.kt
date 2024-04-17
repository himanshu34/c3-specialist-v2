package co.nayan.c3specialist_v2.dashboard

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.databinding.RoleRequestFragmentBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.Role.DRIVER
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RoleRequestFragment : BaseFragment(R.layout.role_request_fragment) {

    private val roleRequestViewModel: RoleRequestViewModel by viewModels()
    private val binding by viewBinding(RoleRequestFragmentBinding::bind)

    @Inject
    lateinit var errorUtils: ErrorUtils

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {
                hideProgressDialog()
                binding.buttonSubmit.enabled()
            }

            ProgressState -> {
                showProgressDialog()
                binding.buttonSubmit.disabled()
            }

            is RoleRequestViewModel.CrateRoleRequestState -> {
                hideProgressDialog()
                if (it.isRoleCreated) {
                    binding.inputFieldsContainer.gone()
                    binding.requestSubmitted.visible()
                } else {
                    binding.inputFieldsContainer.visible()
                    binding.requestSubmitted.gone()
                }
            }

            is RoleRequestViewModel.GetRoleRequestState -> {
                hideProgressDialog()
                binding.buttonSubmit.enabled()
                if (it.isAlreadyRequested) {
                    binding.inputFieldsContainer.gone()
                    binding.requestSubmitted.visible()
                } else {
                    binding.inputFieldsContainer.visible()
                    binding.requestSubmitted.gone()
                }
            }

            is ErrorState -> {
                hideProgressDialog()
                binding.buttonSubmit.enabled()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        roleRequestViewModel.state.observe(viewLifecycleOwner, stateObserver)
        roleRequestViewModel.getRolesRequest()
        setUpClick()

        if (activity is DashboardActivity) {
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.ROLEREQUEST)
        }
    }

    private fun setUpClick() {
        binding.buttonSubmit.setOnClickListener {
            val roleList = mutableListOf<String>()
            if (binding.checkBoxDriver.isChecked) {
                roleList.add(DRIVER)
            }
            if (binding.checkBoxSpecialist.isChecked) {
                roleList.add(SPECIALIST)
            }
            if (roleList.isEmpty()) {
                showMessage(getString(R.string.please_select_role))
            } else {
                roleRequestViewModel.createRoles(roleList)
            }
        }

        binding.checkBoxSpecialist.setOnCheckedChangeListener { _, isChecked ->
            binding.specialistSelected.isVisible = isChecked
        }

        binding.checkBoxDriver.setOnCheckedChangeListener { _, isChecked ->
            binding.driverSelected.isVisible = isChecked
        }

        binding.specialistLayout.setOnClickListener {
            binding.checkBoxSpecialist.isChecked = !binding.checkBoxSpecialist.isChecked
        }

        binding.driverLayout.setOnClickListener {
            binding.checkBoxDriver.isChecked = !binding.checkBoxDriver.isChecked
        }
    }

    private fun showProgressDialog() {
        binding.progressBar.visible()
    }

    private fun hideProgressDialog() {
        binding.progressBar.invisible()
    }

    private fun showMessage(string: String) {
        Snackbar.make(binding.root, string, Snackbar.LENGTH_LONG).show()
    }
}