package com.nayan.nayancamv2.extcam.dashcam

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.showToast
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.LayoutDashcamScanBinding
import com.nayan.nayancamv2.BaseActivity
import com.nayan.nayancamv2.extcam.common.ExtCamConnectionActivity
import com.nayan.nayancamv2.model.SocketStatus.Connected
import com.nayan.nayancamv2.model.SocketStatus.Unsuccessful
import com.nayan.nayancamv2.util.Constants.SHOULD_HOVER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class DashcamScanningFragment : Fragment() {

    private val wifiManager: WifiManager by lazy {
        requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val wifiReceiver by lazy {
        WifiReceiver(requireContext(), wifiManager).apply {
            deviceList.observe(viewLifecycleOwner) {
                viewModel.updateWifiConnectionList(it)
            }
        }
    }

    private lateinit var binding: LayoutDashcamScanBinding
    private var receiverRegistered = false
    private val viewModel: DashCamConnectionViewModel by activityViewModels {
        val baseActivity = requireActivity() as BaseActivity
        baseActivity.viewModelFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutDashcamScanBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            SocketServer.stopServer()
            if (viewModel.shouldInitWiFiReceiver.not()) return@launch
            viewModel.connectionLiveData.observe(viewLifecycleOwner, connectionObserver)

            if (!viewModel.getDeviceModel().contains("A5010")) initWifiReceiver()
            else {
                /** bypass wifi receiver for OnePlus 5T due to oxygen OS bug **/
                binding.tvSettings.text = requireContext().getText(R.string.dashcam_manual)
                delay(5000)
                viewModel.retrySocketConnection()
            }
            observeSocket()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun observeSocket() {
        SocketServer.socketStatus.observe(viewLifecycleOwner) {
            when (it) {
                Connected -> {
                    lifecycleScope.launch {
                        viewModel.startReconnectionTimer(TimeUnit.SECONDS.toMillis(10))
                        delay(5000)
                        withContext(Dispatchers.Main) {
                            Intent(requireActivity(), DashcamStreamingActivity::class.java).apply {
                                putExtra(SHOULD_HOVER, viewModel.shouldHover)
                                startActivity(this)
                            }
                            requireActivity().finishAffinity()
                        }
                    }
                }

                Unsuccessful -> {
                    lifecycleScope.launch {
                        delay(3000)
                        viewModel.retrySocketConnection()
                    }
                }

                else -> {

                }
            }
        }
    }

    private fun initWifiReceiver() {

        viewModel.wifiManager = (wifiManager)
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        Timber.tag("WIFI").d("wifiMan object --> ${wifiManager}, wifiReceiver-->$this")
        requireContext().registerReceiver(wifiReceiver, intentFilter)
        receiverRegistered = true

        if (!wifiManager.isWifiEnabled) wifiManager.isWifiEnabled = true
        wifiManager.startScan()
        viewModel.startReconnectionTimer(TimeUnit.SECONDS.toMillis(20))

    }

    override fun onStop() {
        super.onStop()
        viewModel.resetHandler()
        if (receiverRegistered) unregisterWifiReceiver()
    }

    private fun unregisterWifiReceiver() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                requireContext().unregisterReceiver(viewModel.wifiConnectionReceiver)
            else requireContext().unregisterReceiver(wifiReceiver)
            receiverRegistered = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        SocketServer.socketStatus.removeObservers(this@DashcamScanningFragment)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private val connectionObserver =
        Observer<DashCamConnectionViewModel.ConnectionState> { connectionState ->
            when (connectionState) {
                DashCamConnectionViewModel.ConnectionState.ProgressState -> {
                    lifecycleScope.launch(Dispatchers.Main) {
                        viewModel.checkWiFiConnection()
                    }
                }

                DashCamConnectionViewModel.ConnectionState.UnsuccessfulState -> {
                    parentFragmentManager.popBackStack()
                }

                DashCamConnectionViewModel.ConnectionState.WiFiOff -> {

                    // parentFragmentManager.popBackStack()
                }

                DashCamConnectionViewModel.ConnectionState.ConnectToSocketState -> {
                    lifecycleScope.launch(Dispatchers.Default) {
                        async { SocketServer.startServer() }.await()
                        viewModel.startReconnectionTimer(TimeUnit.SECONDS.toMillis(10))
                    }
                }

                DashCamConnectionViewModel.ConnectionState.StopScanning -> {
                    requireContext().unregisterReceiver(wifiReceiver)
                    receiverRegistered = false
                }

                DashCamConnectionViewModel.ConnectionState.RegisterReceiver -> {
                    val intentFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    requireContext().registerReceiver(
                        viewModel.wifiConnectionReceiver,
                        intentFilter
                    )
                    receiverRegistered = true
                }

                DashCamConnectionViewModel.ConnectionState.RestartActivity -> {
                    restartDashCamScanning()
                }

                DashCamConnectionViewModel.ConnectionState.Init -> {}
            }
        }

    private fun restartDashCamScanning() = lifecycleScope.launch(Dispatchers.Main) {
        requireContext().showToast("Re-attempting DashCam Connection")
        Intent(requireActivity(), ExtCamConnectionActivity::class.java).apply {
            putExtra("selected", "dashcam")
            startActivity(this)
        }
        requireActivity().finish()
    }
}