package com.example.videoplayer.service

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

object ServerInfoHelper {

    @Volatile
    private var cachedMediaBytes: Long = -1L
    @Volatile
    private var cachedMediaAtMs: Long = 0L

    fun getAppVersion(context: Context): String {
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pi.versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }
    }

    fun getUptimeSec(startedAtMs: Long): Long {
        if (startedAtMs <= 0L) return 0L
        return ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(0L)
    }

    fun getCachedMediaBytes(context: Context, forceRefresh: Boolean = false): Long {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedMediaBytes >= 0L && (now - cachedMediaAtMs) < 30_000L) {
            return cachedMediaBytes
        }
        val bytes = try {
            LocalHttpShareServer.getAllMediaTotalBytes(context)
        } catch (_: Throwable) {
            cachedMediaBytes.coerceAtLeast(0L)
        }
        cachedMediaBytes = bytes
        cachedMediaAtMs = now
        return bytes
    }

    fun getVlcVersion(): String {
        return try {
            val cls = Class.forName("org.videolan.libvlc.LibVLC")
            val m = cls.methods.firstOrNull { it.name == "version" && it.parameterTypes.isEmpty() }
            val v = m?.invoke(null)?.toString()?.trim()
            if (!v.isNullOrBlank()) v else "3.6.0-eap13"
        } catch (_: Throwable) {
            "3.6.0-eap13"
        }
    }

    fun getStoragePair(): Pair<Long, Long> {
        return try {
            val s = StatFs(Environment.getExternalStorageDirectory().path)
            Pair(s.blockCountLong * s.blockSizeLong, s.availableBlocksLong * s.blockSizeLong)
        } catch (_: Throwable) {
            Pair(0L, 0L)
        }
    }

    fun getAdminServerInfoJson(
        context: Context,
        protocol: String,
        port: Int,
        address: String,
        startedAtMs: Long,
        activeThreads: Int,
        activeSessions: Int,
        devicesCount: Int,
        onlineDevicesCount: Int,
        polledSignal: WifiSignal
    ): JSONObject {
        val appCtx = context.applicationContext
        val root = JSONObject()

        // 1. Server
        val serverObj = JSONObject().apply {
            put("version", getAppVersion(appCtx))
            put("protocol", protocol.lowercase())
            put("port", port)
            put("address", address)
            put("startedAt", startedAtMs)
            put("uptimeSec", getUptimeSec(startedAtMs))
            put("threads", activeThreads)
            put("activeSessions", activeSessions)
            put("devicesCount", devicesCount)
            put("onlineDevicesCount", onlineDevicesCount)
        }
        root.put("server", serverObj)

        // 2. Device & OS
        val abis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: ""
        val devObj = JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER ?: "")
            put("model", Build.MODEL ?: "")
            put("device", Build.DEVICE ?: "")
            put("androidVersion", Build.VERSION.RELEASE ?: "")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("abis", abis)
            put("vlcVersion", getVlcVersion())
        }
        root.put("device", devObj)

        // 3. Wi-Fi & Interfaces (safely permission-guarded)
        var ssid = ""
        var wifiIp = ""
        var linkSpeedMbps = 0
        var frequencyMhz = 0
        var rssi = polledSignal.rssi ?: -127
        var level = polledSignal.level ?: -1
        try {
            val hasWifiState = ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.ACCESS_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED
            val hasFineLoc = ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasWifiState) {
                val wm = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val info = wm?.connectionInfo
                if (info != null && info.networkId != -1) {
                    if (hasFineLoc) {
                        val rawSsid = info.ssid?.trim()?.trim('"') ?: ""
                        if (rawSsid.isNotBlank() && !rawSsid.equals("<unknown ssid>", ignoreCase = true)) {
                            ssid = rawSsid
                        }
                    }
                    if (info.linkSpeed > 0) linkSpeedMbps = info.linkSpeed
                    if (info.frequency > 0) frequencyMhz = info.frequency
                    if (info.rssi > -127) {
                        rssi = info.rssi
                        @Suppress("DEPRECATION")
                        level = WifiManager.calculateSignalLevel(info.rssi, 5).coerceIn(0, 4)
                    }
                    @Suppress("DEPRECATION")
                    val ipInt = info.ipAddress
                    if (ipInt != 0) {
                        wifiIp = String.format(
                            "%d.%d.%d.%d",
                            ipInt and 0xff,
                            (ipInt shr 8) and 0xff,
                            (ipInt shr 16) and 0xff,
                            (ipInt shr 24) and 0xff
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }
        val wifiObj = JSONObject().apply {
            put("ssid", ssid)
            put("ip", wifiIp)
            put("linkSpeedMbps", linkSpeedMbps)
            put("frequencyMhz", frequencyMhz)
            put("rssi", if (rssi > -127) rssi else JSONObject.NULL)
            put("level", if (level >= 0) level else JSONObject.NULL)
        }
        root.put("wifi", wifiObj)

        val ifacesArr = JSONArray()
        try {
            for ((label, ip) in LocalHttpShareServer.getAvailableDeviceIps()) {
                ifacesArr.put(JSONObject().apply {
                    put("label", label)
                    put("ip", ip)
                })
            }
        } catch (_: Throwable) {
        }
        root.put("interfaces", ifacesArr)

        // 4. Storage
        val (diskTotal, diskFree) = getStoragePair()
        val mediaBytes = getCachedMediaBytes(appCtx, forceRefresh = true)
        root.put("storage", JSONObject().apply {
            put("total", diskTotal)
            put("free", diskFree)
            put("used", (diskTotal - diskFree).coerceAtLeast(0L))
            put("mediaBytes", mediaBytes)
        })

        // 5. RAM
        var ramTotal = 0L
        var ramAvail = 0L
        var lowMem = false
        try {
            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                ramTotal = mi.totalMem
                ramAvail = mi.availMem
                lowMem = mi.lowMemory
            }
        } catch (_: Throwable) {
        }
        root.put("memory", JSONObject().apply {
            put("total", ramTotal)
            put("avail", ramAvail)
            put("used", (ramTotal - ramAvail).coerceAtLeast(0L))
            put("lowMemory", lowMem)
        })

        // 6. Battery
        var batLevel = -1
        var batCharging = false
        try {
            val batIntent = appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batIntent != null) {
                val rawLevel = batIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (rawLevel >= 0 && scale > 0) {
                    batLevel = ((rawLevel * 100f) / scale).toInt().coerceIn(0, 100)
                }
                val status = batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = batIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                batCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged > 0
            }
        } catch (_: Throwable) {
        }
        root.put("battery", JSONObject().apply {
            put("level", if (batLevel >= 0) batLevel else JSONObject.NULL)
            put("charging", batCharging)
        })

        // 7. Traffic & load
        val (downBytes, upBytes) = ActivityTracker.getTotalTrafficBytes()
        root.put("traffic", JSONObject().apply {
            put("downBytes", downBytes)
            put("upBytes", upBytes)
            put("totalBytes", downBytes + upBytes)
            put("downBps", ActivityTracker.getTotalDownBps())
            put("upBps", ActivityTracker.getTotalUpBps())
            put("activeTransfers", ActivityTracker.getActiveTransfersCount())
        })

        // 8. FTP server
        val ftpRunning = LocalFtpServer.isRunning
        val ftpPort = LocalFtpServer.port
        val ftpIp = wifiIp.ifBlank { LocalFtpServer.getLocalIpAddress() }
        root.put("ftp", JSONObject().apply {
            put("running", ftpRunning)
            put("port", ftpPort)
            put("address", "ftp://$ftpIp:$ftpPort")
        })

        return root
    }
}
