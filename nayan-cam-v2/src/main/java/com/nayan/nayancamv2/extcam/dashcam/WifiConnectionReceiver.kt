package com.nayan.nayancamv2.extcam.dashcam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager

/**This is for check connection state for devices with api level less than 29 **/
class WifiConnectionReceiver(private val callback: WifiConnectionCallback) : BroadcastReceiver() {

    private val wifiFamilyList = listOf("ddpai", "wifi")

    interface WifiConnectionCallback {
        fun onWifiConnected()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
            val networkInfo: NetworkInfo? =
                intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            val ifContainsFamily = wifiFamilyList.any { family ->
                connectionInfo.ssid.contains(family, ignoreCase = true)
            }
            if (ifContainsFamily && networkInfo?.state == NetworkInfo.State.CONNECTED) {
                // Wi-Fi is connected
                callback.onWifiConnected()
            }
        }
    }
}
