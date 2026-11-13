package com.example.videoplayer.service

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var hotspotCallback: WifiManager.LocalOnlyHotspotCallback? = null
    private var activeInfo: WifiDirectInfo? = null
    private var isStarting = false
    private var operationGeneration = 0
    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null

    fun getWifiQrPayload(ssid: String = currentSsid, pass: String = currentPass): String {
        // Standard Wi-Fi network QR code format recognized by iOS and Android Camera apps.
        val escapedSsid = ssid.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
        val escapedPass = pass.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
        return "WIFI:T:WPA;S:$escapedSsid;P:$escapedPass;;"
    }

    fun startHotspotOrDirect(
        context: Context,
        onStatus: (String) -> Unit,
        onComplete: (WifiDirectInfo) -> Unit
    ) {
        if (isEnabled && activeInfo != null) {
            onComplete(activeInfo!!)
            return
        }
        if (isStarting) {
            onStatus("Подключение Wi‑Fi уже запускается...")
            return
        }

        isStarting = true
        val generation = ++operationGeneration
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = manager?.initialize(appContext, Looper.getMainLooper(), null)

        if (manager != null && channel != null) {
            p2pManager = manager
            p2pChannel = channel
            onStatus("Создание группы Wi‑Fi Direct...")
            try {
                manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                            if (generation != operationGeneration) {
                                try { manager.removeGroup(channel, null) } catch (_: Exception) { }
                                return@requestGroupInfo
                            }
                            if (group != null && !group.networkName.isNullOrBlank() && !group.passphrase.isNullOrBlank()) {
                                val info = WifiDirectInfo(
                                    ssid = group.networkName,
                                    password = group.passphrase,
                                    qrPayload = getWifiQrPayload(group.networkName, group.passphrase),
                                    isNativeP2p = true
                                )
                                currentSsid = info.ssid
                                currentPass = info.password
                                activeInfo = info
                                isStarting = false
                                isEnabled = true
                                onStatus("Wi‑Fi Direct группа активна: ${info.ssid}")
                                onComplete(info)
                            } else {
                                Log.w(TAG, "Wi-Fi Direct group did not provide usable credentials")
                                try { manager.removeGroup(channel, null) } catch (_: Exception) { }
                                fallbackToLocalHotspot(appContext, generation, onStatus, onComplete)
                            }
                        }
                    }

                    override fun onFailure(reason: Int) {
                        if (generation != operationGeneration) return
                        Log.w(TAG, "WifiP2pManager createGroup failed ($reason), trying local-only hotspot")
                        fallbackToLocalHotspot(appContext, generation, onStatus, onComplete)
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error initiating Wi-Fi Direct", e)
            }
        }

        fallbackToLocalHotspot(appContext, onStatus, onComplete)
    }

    fun stop(context: Context) {
        operationGeneration++
        isStarting = false
        isEnabled = false
        activeInfo = null

        hotspotCallback = null
        hotspotReservation?.let { reservation ->
            try {
                reservation.close()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to close local-only hotspot reservation", e)
            }
        }
        hotspotReservation = null

        try {
            val manager = p2pManager
                ?: (context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager)
            val channel = p2pChannel
                ?: manager?.initialize(context.applicationContext, Looper.getMainLooper(), null)
            if (manager != null && channel != null) {
                manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = Unit
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "Unable to remove Wi-Fi Direct group ($reason)")
                    }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to stop Wi-Fi Direct group", e)
        } finally {
            p2pManager = null
            p2pChannel = null
        }
    }

    private fun fallbackToLocalHotspot(
        context: Context,
        generation: Int,
        onStatus: (String) -> Unit,
        onComplete: (WifiDirectInfo) -> Unit
    ) {
        if (generation != operationGeneration) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            isStarting = false
            isEnabled = false
            onStatus("Wi‑Fi Direct недоступен, а системная точка доступа требует Android 8.0 или новее")
            return
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            isStarting = false
            isEnabled = false
            onStatus("Служба Wi‑Fi недоступна на этом устройстве")
            return
        }

        onStatus("Запуск системной локальной точки доступа...")
        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                if (generation != operationGeneration) {
                    reservation.close()
                    return
                }
                hotspotCallback = this
                hotspotReservation = reservation

                try {
                    val ssid: String?
                    val password: String?
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val config = reservation.softApConfiguration
                        ssid = config.ssid
                        password = config.passphrase
                    } else {
                        @Suppress("DEPRECATION")
                        val config = reservation.wifiConfiguration
                        ssid = config?.SSID
                        password = config?.preSharedKey
                    }

                    if (ssid.isNullOrBlank() || password.isNullOrBlank()) {
                        reservation.close()
                        hotspotReservation = null
                        hotspotCallback = null
                        isStarting = false
                        isEnabled = false
                        activeInfo = null
                        onStatus("Android запустил точку доступа, но не предоставил её данные для подключения")
                        return
                    }

                    val cleanSsid = ssid.removeSurrounding("\"")
                    val cleanPassword = password.removeSurrounding("\"")
                    val info = WifiDirectInfo(
                        ssid = cleanSsid,
                        password = cleanPassword,
                        qrPayload = getWifiQrPayload(cleanSsid, cleanPassword),
                        isNativeP2p = false
                    )
                    currentSsid = info.ssid
                    currentPass = info.password
                    activeInfo = info
                    isStarting = false
                    isEnabled = true
                    onStatus("Локальная точка доступа активна: ${info.ssid}")
                    onComplete(info)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to read local-only hotspot credentials", e)
                    try {
                        reservation.close()
                    } catch (_: Exception) {
                    }
                    hotspotReservation = null
                    hotspotCallback = null
                    activeInfo = null
                    isStarting = false
                    isEnabled = false
                    onStatus("Не удалось получить данные локальной точки доступа")
                }
            }

            override fun onStopped() {
                if (generation != operationGeneration) return
                hotspotReservation = null
                hotspotCallback = null
                activeInfo = null
                isStarting = false
                isEnabled = false
                onStatus("Локальная точка доступа остановлена")
            }

            override fun onFailed(reason: Int) {
                if (generation != operationGeneration) return
                hotspotReservation = null
                hotspotCallback = null
                activeInfo = null
                isStarting = false
                isEnabled = false
                Log.w(TAG, "Local-only hotspot failed ($reason)")
                onStatus("Не удалось создать точку доступа Wi‑Fi (ошибка $reason)")
            }
        }

        hotspotCallback = callback
        try {
            wifiManager.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            hotspotCallback = null
            isStarting = false
            isEnabled = false
            Log.e(TAG, "Missing permission to start local-only hotspot", e)
            onStatus("Нет разрешения Android для создания точки доступа Wi‑Fi")
        } catch (e: Exception) {
            hotspotCallback = null
            isStarting = false
            isEnabled = false
            Log.e(TAG, "Unable to start local-only hotspot", e)
            onStatus("Не удалось запустить точку доступа Wi‑Fi: ${e.message ?: "неизвестная ошибка"}")
        }
    }
}
