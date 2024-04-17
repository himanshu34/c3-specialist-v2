package co.nayan.c3specialist_v2.home.roles.delhipolice

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.databinding.FragmentDelhiPoliceHomeBinding
import co.nayan.c3specialist_v2.home.roles.driver.DriverHomeViewModel
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import com.nayan.nayancamv2.ai.AIModelManager
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.StorageUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A simple [Fragment] subclass.
 * Use the [DelhiPoliceHomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
@AndroidEntryPoint
class DelhiPoliceHomeFragment : BaseFragment(R.layout.fragment_delhi_police_home) {

    private val driverHomeViewModel: DriverHomeViewModel by viewModels()
    private val binding by viewBinding(FragmentDelhiPoliceHomeBinding::bind)
    private lateinit var storageUtil: StorageUtil
    private lateinit var aiModelManager: AIModelManager

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPrefManager = SharedPrefManager(requireContext())
        storageUtil = StorageUtil(requireContext(), sharedPrefManager, nayanCamModuleInteractor)
        aiModelManager = AIModelManager(requireContext(), sharedPrefManager)

        binding.userEmailTxt.text = driverHomeViewModel.getUserEmail()
        binding.homeMessageTxt.text = String.format(
            getString(R.string.police_home_screen_message), driverHomeViewModel.getUserName()
        )
        setUpClicks()
    }

    private fun setUpClicks() {
        binding.startRecordingContainer.setOnClickListener {
            moveToNayanDriver()
        }
    }

    private fun moveToNayanDriver() {
        nayanCamModuleInteractor.moveToDriverApp(
            requireActivity(),
            Role.DELHI_POLICE,
            storageUtil.isDefaultHoverMode()
        )
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment DelhiPoliceHomeFragment.
         */
        @JvmStatic
        fun newInstance() = DelhiPoliceHomeFragment()
    }
}