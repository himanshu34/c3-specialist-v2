package com.nayan.nayancamv2.extcam.drone

import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.enabled
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.LayoutDroneConnectionBinding
import com.nayan.nayancamv2.extcam.common.DJIXNayan
import com.nayan.nayancamv2.extcam.drone.DroneConnectionViewModel.ConnectionState.PRODUCT_CHANGED
import com.nayan.nayancamv2.extcam.drone.DroneConnectionViewModel.ConnectionState.PRODUCT_CONNECTED
import com.nayan.nayancamv2.extcam.drone.DroneConnectionViewModel.ConnectionState.PRODUCT_DISCONNECTED
import com.nayan.nayancamv2.extcam.drone.DroneConnectionViewModel.ConnectionState.REGISTRATION_FAILURE
import com.nayan.nayancamv2.extcam.drone.DroneConnectionViewModel.ConnectionState.REGISTRATION_SUCCESS
import com.nayan.nayancamv2.extcam.drone.DroneConnectionViewModel.ConnectionState.START_REGISTRATION
import com.nayan.nayancamv2.util.Constants.SHOULD_HOVER
import dji.common.error.DJIError
import dji.common.useraccount.UserAccountState
import dji.common.util.CommonCallbacks
import dji.keysdk.KeyManager
import dji.sdk.sdkmanager.DJISDKManager
import dji.sdk.useraccount.UserAccountManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DroneConnectionFragment : Fragment() {

    private lateinit var binding: LayoutDroneConnectionBinding
    private val viewModel: DroneConnectionViewModel by activityViewModels()
    private var tooltipPopup: PopupWindow? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutDroneConnectionBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.startRecordingDrone.setOnClickListener {
            val intent = Intent(activity, DroneStreamingActivity::class.java)
            intent.putExtra(SHOULD_HOVER, true)
            startActivity(intent)
            requireActivity().finish()
        }
        viewModel.connectionStatus.observe(viewLifecycleOwner, connectionStateObserver)
        viewModel.productInfo.observe(viewLifecycleOwner) { productInfo ->
            binding.tvSubHeader.text = productInfo
        }
        viewModel.firmwareVersion.observe(viewLifecycleOwner) { version ->
            binding.tvDescription.text = version
        }
        binding.parent.setOnClickListener {
            if (tooltipPopup?.isShowing == true) tooltipPopup?.dismiss()
        }
        binding.productsInfo.setOnClickListener {
            if (tooltipPopup == null) {
                val tooltipView =
                    LayoutInflater.from(requireContext()).inflate(R.layout.tooltip_layout, null)
                val tooltipTextView: TextView = tooltipView.findViewById(R.id.tooltipText)
                val droneNames = resources.getStringArray(R.array.drone_names)
                tooltipTextView.text = droneNames.joinToString("\n")
                tooltipPopup = PopupWindow(
                    tooltipView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                tooltipPopup?.animationStyle = android.R.style.Animation_Activity
            }
            if (tooltipPopup?.isShowing == true) {
                tooltipPopup?.dismiss()
                return@setOnClickListener
            }
            tooltipPopup?.showAsDropDown(it, 0, 0, Gravity.END)
        }
    }

    private fun loginDJIUserAccount() {
        UserAccountManager.getInstance().logIntoDJIUserAccount(
            requireContext(),
            userAccountCompletionCallback
        )
    }

    private fun updateViewsForConnectedState() {
        setSubHeaderText()
        binding.tvDescription.text =
            DJIXNayan.productInstance?.firmwarePackageVersion ?: "Unknown Firmware"
        binding.startRecordingDrone.enabled()
        if (KeyManager.getInstance() != null)
            KeyManager.getInstance().addListener(viewModel.firmKey, viewModel.firmVersionListener)
    }

    private fun setSubHeaderText() {
        val modelName = DJIXNayan.productInstance?.model?.displayName ?: "Unknown Aircraft"
        val status = ": Connected"
        binding.tvSubHeader.text = SpannableStringBuilder().apply {
            append(modelName)
            append(status)
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.colorBlack)),
                0,
                modelName.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.grey_color)),
                modelName.length,
                modelName.length + status.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private val connectionStateObserver = Observer<DroneConnectionViewModel.ConnectionState> {
        when (it) {
            REGISTRATION_SUCCESS -> {
                // loginDJIUserAccount()
                lifecycleScope.launch {
                    requireContext().let { context ->
                        val attachedIntent = Intent().apply {
                            action = DJISDKManager.USB_ACCESSORY_ATTACHED
                        }
                        context.sendBroadcast(attachedIntent)
                        context.showToast("Registration Successful")
                    }
                }
            }

            REGISTRATION_FAILURE -> {
                binding.tvSubHeader.text = resources.getString(R.string.registration_failed)
            }

            PRODUCT_CONNECTED, PRODUCT_CHANGED -> {
                updateViewsForConnectedState()
            }

            PRODUCT_DISCONNECTED -> {
                parentFragmentManager.popBackStack()
            }

            START_REGISTRATION -> {
                startSDKRegistration()
            }
        }
    }

    private fun startSDKRegistration() = lifecycleScope.launch(Dispatchers.IO) {
        if (viewModel.isRegistrationInProgress.compareAndSet(false, true)) {
            try {
                DJISDKManager.getInstance().registerApp(requireContext(), viewModel)
                viewModel.isRegistrationInProgress.set(false)
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.isRegistrationInProgress.set(false)
            }
        }
    }

    private val userAccountCompletionCallback =
        object : CommonCallbacks.CompletionCallbackWith<UserAccountState> {
            override fun onSuccess(userAccountState: UserAccountState) {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.tvLoginState.text = "DJI Account: ${userAccountState.name}"
                }
            }

            override fun onFailure(error: DJIError) {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.tvLoginState.text = "DJI Account: ${error.description}"
                }
            }
        }
}
