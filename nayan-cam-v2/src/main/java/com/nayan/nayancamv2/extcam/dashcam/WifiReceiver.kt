package com.nayan.nayancamv2.extcam.dashcam

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class WifiReceiver(
    private val context: Context,
    private val wifiManager: WifiManager
) : BroadcastReceiver() {

    private var _deviceList = MutableLiveData<List<ScanResult>>()
    val deviceList: LiveData<List<ScanResult>> = _deviceList
    private val wifiFamilyList = listOf("DDPAI", "WIFI")

    override fun onReceive(context: Context, intent: Intent) {
        val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
        if (success) scanSuccess()
    }

    private fun scanSuccess() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val wifiList = wifiManager.scanResults
        val resultList = wifiList.filter {
            val wifiSSID = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                it.SSID else it.wifiSsid.toString()
            wifiFamilyList.any { family -> wifiSSID.contains(family, ignoreCase = true) }
        }
        _deviceList.postValue(resultList)
    }
}