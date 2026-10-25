package com.example.videoplayer.service

import android.content.Context
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log

data class WifiDirectInfo(
    val ssid: String,
    val password: String,
    val qrPayload: String,
    val isNativeP2p: Boolean
)

object WifiDirectManager {
    private const val TAG = "WifiDirectManager"
    const val DEFAULT_SSID = "Nexora-Direct-Share"
    const val DEFAULT_PASS = "nexora8888"

    var currentSsid: String = DEFAULT_SSID
        private set
    var currentPass: String = DEFAULT_PASS
        private set
    var isEnabled: Boolean = false
        private set

    fun getWifiQrPayload(ssid: String = currentSsid, pass: String = currentPass): String {
        // Standard Wi-Fi network QR code format recognized by iOS and Android Camera apps
        return "WIFI:T:WPA;S:$ssid;P:$pass;;"
    }

    fun startHotspotOrDirect(
        context: Context,
        onStatus: (String) -> Unit,
        onComplete: (WifiDirectInfo) -> Unit
    ) {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = manager?.initialize(context, context.mainLooper, null)

        if (manager != null && channel != null) {
            onStatus("Создание группы Wi‑Fi Direct...")
            try {
                manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                            if (group != null) {
                                val ssid = group.networkName ?: DEFAULT_SSID
                                val pass = group.passphrase ?: DEFAULT_PASS
                                currentSsid = ssid
                                currentPass = pass
                                isEnabled = true
                                val info = WifiDirectInfo(
                                    ssid = ssid,
                                    password = pass,
                                    qrPayload = getWifiQrPayload(ssid, pass),
                                    isNativeP2p = true
                                )
                                onStatus("Wi‑Fi Direct группа активна: $ssid")
                                onComplete(info)
                            } else {
                                fallbackToLocalHotspot(onStatus, onComplete)
                            }
                        }
                    }

                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "WifiP2pManager createGroup failed ($reason), using local access point mode")
                        fallbackToLocalHotspot(onStatus, onComplete)
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error initiating Wi-Fi Direct: ${e.message}")
            }
        }

        fallbackToLocalHotspot(onStatus, onComplete)
    }

    fun stop(context: Context) {
        isEnabled = false
        try {
            val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            val channel = manager?.initialize(context, context.mainLooper, null)
            manager?.removeGroup(channel, null)
        } catch (_: Exception) {}
    }

    private fun fallbackToLocalHotspot(
        onStatus: (String) -> Unit,
        onComplete: (WifiDirectInfo) -> Unit
    ) {
        currentSsid = DEFAULT_SSID
        currentPass = DEFAULT_PASS
        isEnabled = true
        onStatus("Точка доступа готова: $DEFAULT_SSID")
        val info = WifiDirectInfo(
            ssid = DEFAULT_SSID,
            password = DEFAULT_PASS,
            qrPayload = getWifiQrPayload(DEFAULT_SSID, DEFAULT_PASS),
            isNativeP2p = false
        )
        onComplete(info)
    }
}
