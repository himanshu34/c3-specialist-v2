package com.nayan.nayancamv2.extcam.dashcam

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import com.nayan.nayancamv2.requiresPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject

class DashCamConnectionViewModel @Inject constructor(
    val nayanCamModuleInteractor: NayanCamModuleInteractor
) : ViewModel(), LifecycleObserver {

    private val connectivityManager: ConnectivityManager by lazy {
        mContext.get()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val reconnectionHandler = Handler(Looper.getMainLooper())
    private val reconnectionRunnable = Runnable {
        _connectionLiveData.postValue(ConnectionState.RestartActivity)
    }

    enum class ConnectionState { ProgressState, UnsuccessfulState, WiFiOff, ConnectToSocketState, StopScanning, RegisterReceiver, RestartActivity, Init }

    private val _connectionLiveData = MutableLiveData<ConnectionState>()
    val connectionLiveData: LiveData<ConnectionState> = _connectionLiveData

    lateinit var wifiManager: WifiManager
    lateinit var mContext: WeakReference<Context>
    lateinit var wifiConnectionReceiver: WifiConnectionReceiver
    var shouldInitWiFiReceiver = true
    var shouldHover = false

    fun getDeviceModel() = nayanCamModuleInteractor.getDeviceModel()


    fun updateWifiConnectionList(
        wifiConnectionList: List<ScanResult>
    ) = viewModelScope.launch(Dispatchers.Default) {
        val signalOfInterest = wifiConnectionList.maxByOrNull { it.level }
        signalOfInterest?.let {
            _connectionLiveData.postValue(ConnectionState.StopScanning)
            if (SDK_INT < Build.VERSION_CODES.Q)
                connectToWifiDeprecated(signalOfInterest, "1234567890")
            else connectToWifi(signalOfInterest, "1234567890")
        } ?: run { notifyNoConnectionsAvailable() }
    }

    private fun notifyNoConnectionsAvailable() {
        _connectionLiveData.postValue(ConnectionState.UnsuccessfulState)
    }

    fun checkWiFiConnection() = viewModelScope.launch {
        delay(3000)
        _connectionLiveData.postValue(ConnectionState.ConnectToSocketState)
    }

    private suspend fun connectToWifiDeprecated(
        networkToConnect: ScanResult,
        password: String?
    ) = viewModelScope.launch {
        _connectionLiveData.postValue(ConnectionState.RegisterReceiver)
        wifiConnectionReceiver =
            WifiConnectionReceiver(object : WifiConnectionReceiver.WifiConnectionCallback {
                override fun onWifiConnected() {
                    viewModelScope.launch(Dispatchers.Default) {
                        val network = getNetworkForSSID(networkToConnect.SSID)
                        async { connectivityManager.bindProcessToNetwork(network) }.await()
                        delay(3000)
                        _connectionLiveData.postValue(ConnectionState.ProgressState)
                    }
                }
            })
        val wifiConfig = WifiConfiguration().apply {
            SSID = "\"" + networkToConnect.SSID + "\""
            if (networkToConnect.requiresPassword()) preSharedKey = "\"" + password + "\""
            else allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }
        wifiManager.run {
            val networkId = addNetwork(wifiConfig)
            enableNetwork(networkId, true)
            reconnect()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToWifi(networkToConnect: ScanResult, password: String?) {
        val ssid = if (SDK_INT < Build.VERSION_CODES.TIRAMISU) networkToConnect.SSID
        else networkToConnect.wifiSsid.toString().replace("\"", "")
        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password ?: "")
            .build()
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()
        connectivityManager.requestNetwork(networkRequest, networkCallback, 60000)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // when Wifi is on
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            connectivityManager.bindProcessToNetwork(network)
            _connectionLiveData.postValue(ConnectionState.ProgressState)
        }

        // when Wifi 【turns off]
        override fun onLost(network: Network) {
            super.onLost(network)
            _connectionLiveData.postValue(ConnectionState.WiFiOff)
        }

        override fun onUnavailable() {
            super.onUnavailable()
            // unsuccessful connection
            _connectionLiveData.postValue(ConnectionState.UnsuccessfulState)
        }
    }

    private fun getNetworkForSSID(ssid: String): Network? {
        return connectivityManager.allNetworks.find {
            connectivityManager.getNetworkCapabilities(it)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    && connectivityManager.getNetworkInfo(it)?.extraInfo == ssid
        }
    }

    fun retrySocketConnection() = viewModelScope.launch(Dispatchers.Default) {
        _connectionLiveData.postValue(ConnectionState.ConnectToSocketState)
    }

    fun startReconnectionTimer(delayMs: Long) {
        resetHandler()
        reconnectionHandler.postDelayed(reconnectionRunnable, delayMs)
    }

    fun resetHandler() {
        reconnectionHandler.removeCallbacksAndMessages(null)
    }
}