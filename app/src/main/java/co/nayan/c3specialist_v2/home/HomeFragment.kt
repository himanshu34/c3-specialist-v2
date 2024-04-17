package co.nayan.c3specialist_v2.home

import android.os.Bundle
import android.view.View
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.databinding.FragmentHomeBinding
import co.nayan.c3specialist_v2.home.roles.admin.AdminHomeFragment
import co.nayan.c3specialist_v2.home.roles.delhipolice.DelhiPoliceHomeFragment
import co.nayan.c3specialist_v2.home.roles.driver.DriverHomeFragment
import co.nayan.c3specialist_v2.home.roles.leader.LeaderHomeFragment
import co.nayan.c3specialist_v2.home.roles.manager.ManagerHomeFragment
import co.nayan.c3specialist_v2.home.roles.specialist.SpecialistHomeFragment
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.chip.ChipGroup
import com.nayan.nayancamv2.storage.SharedPrefManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : BaseFragment(R.layout.fragment_home) {

    @Inject
    lateinit var userRepository: UserRepository
    private val binding by viewBinding(FragmentHomeBinding::bind)

    private lateinit var sharedPrefManager: SharedPrefManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPrefManager = SharedPrefManager(requireContext())
        setupHomeFragmentWithRoles()
    }

    private fun setupHomeFragmentWithRoles() {
        binding.roleSelectorLayout.rolesSelector.setOnCheckedChangeListener(onCheckedChangedListener)
        val activeRoles = userRepository.getUserRoles()
        sharedPrefManager.setAIPreview(BuildConfig.FLAVOR == "qa" || activeRoles.contains(Role.ADMIN))

        val currentRole = if (activeRoles.contains(Role.DRIVER))
            Role.DRIVER else activeRoles.firstOrNull() ?: Role.DRIVER
        if (activeRoles.isEmpty() || activeRoles.size == 1)
            setupRole(currentRole) else setupRoles(activeRoles)

        setCurrentChip(currentRole)
    }

    private val onCheckedChangedListener = ChipGroup.OnCheckedChangeListener { _, checkedId ->
        val fragment = when (checkedId) {
            R.id.specialistChip -> SpecialistHomeFragment()
            R.id.managerChip -> ManagerHomeFragment()
            R.id.adminChip -> AdminHomeFragment()
            R.id.driverChip -> DriverHomeFragment.newInstance()
            R.id.surveyorChip -> DriverHomeFragment.newInstance()
            R.id.delPChip -> DelhiPoliceHomeFragment.newInstance()
            R.id.leaderChip -> LeaderHomeFragment()
            else -> SpecialistHomeFragment()
        }
        childFragmentManager.beginTransaction().replace(R.id.homeFragmentContainer, fragment)
            .commit()
    }

    private fun setupRole(role: String) {
        binding.roleSelectorLayout.rolesSelector.gone()
        binding.roleTxt.visible()
        binding.roleTxt.text = role.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    private fun setupRoles(roles: List<String>) {
        binding.roleSelectorLayout.rolesSelector.visible()
        binding.roleTxt.gone()

        if (roles.contains(Role.SPECIALIST)) binding.roleSelectorLayout.specialistChip.visible()
        if (roles.contains(Role.MANAGER)) binding.roleSelectorLayout.managerChip.visible()
        if (roles.contains(Role.ADMIN)) binding.roleSelectorLayout.adminChip.visible()
        if (roles.contains(Role.DRIVER)) {
            if (userRepository.isSurveyor()) {
                binding.roleSelectorLayout.surveyorChip.visible()
                binding.roleSelectorLayout.driverChip.gone()
            } else {
                binding.roleSelectorLayout.driverChip.visible()
                binding.roleSelectorLayout.surveyorChip.gone()
            }
        }
        if (roles.contains(Role.DELHI_POLICE)) binding.roleSelectorLayout.delPChip.visible()
        if (roles.contains(Role.LEADER)) binding.roleSelectorLayout.leaderChip.visible()
    }

    private fun setCurrentChip(currentRole: String) {
        when (currentRole) {
            Role.MANAGER -> binding.roleSelectorLayout.managerChip.isChecked = true
            Role.ADMIN -> binding.roleSelectorLayout.adminChip.isChecked = true
            Role.DRIVER -> {
                if (userRepository.isSurveyor())
                    binding.roleSelectorLayout.surveyorChip.isChecked = true
                else binding.roleSelectorLayout.driverChip.isChecked = true
            }

            Role.DELHI_POLICE -> binding.roleSelectorLayout.delPChip.isChecked = true
            Role.LEADER -> binding.roleSelectorLayout.leaderChip.isChecked = true
            else -> binding.roleSelectorLayout.specialistChip.isChecked = true
        }
    }
}