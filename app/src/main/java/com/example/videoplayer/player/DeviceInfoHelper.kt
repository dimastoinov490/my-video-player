package com.example.videoplayer.player

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager

object DeviceInfoHelper {

    data class DeviceDetails(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val sdkLevel: Int,
        val securityPatch: String,
        val buildId: String,
        val screenResolution: String,
        val screenDensity: String,
        val refreshRate: Float,
        val batteryLevel: Int,
        val batteryVoltage: Float,
        val batteryTemp: Float,
        val batteryTech: String,
        val storageTotalGb: Float,
        val storageFreeGb: Float
    )

    fun getDetails(context: Context): DeviceDetails {
        // Устройство и система
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
        val sdkLevel = Build.VERSION.SDK_INT
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"
        val buildId = Build.ID ?: "N/A"

        // Дисплей (безопасно для Activity и Application контекстов)
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

        var width = 0
        var height = 0
        var densityDpi = context.resources.displayMetrics.densityDpi

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            try {
                val bounds = windowManager.maximumWindowMetrics.bounds
                width = bounds.width()
                height = bounds.height()
            } catch (_: Throwable) {}
        }
        if (width <= 0 || height <= 0) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            val targetDisplay = defaultDisplay ?: windowManager?.defaultDisplay
            if (targetDisplay != null) {
                @Suppress("DEPRECATION")
                targetDisplay.getRealMetrics(metrics)
                width = metrics.widthPixels
                height = metrics.heightPixels
                densityDpi = metrics.densityDpi
            } else {
                val resMetrics = context.resources.displayMetrics
                width = resMetrics.widthPixels
                height = resMetrics.heightPixels
                densityDpi = resMetrics.densityDpi
            }
        }

        val refreshRate = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                defaultDisplay?.refreshRate ?: try {
                    context.display?.refreshRate
                } catch (_: Throwable) {
                    null
                } ?: 60.0f
            } else {
                @Suppress("DEPRECATION")
                (defaultDisplay ?: windowManager?.defaultDisplay)?.refreshRate ?: 60.0f
            }
        } catch (_: Throwable) {
            60.0f
        }

        // Батарея (через системный Intent)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else 0
        val voltage = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val temp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val tech = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { "Li-ion" } ?: "Li-ion"

        // Внутреннее хранилище (ПЗУ / Flash)
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val freeBlocks = stat.availableBlocksLong
        val totalStorageGb = (totalBlocks * blockSize) / (1024f * 1024f * 1024f)
        val freeStorageGb = (freeBlocks * blockSize) / (1024f * 1024f * 1024f)

        return DeviceDetails(
            manufacturer = manufacturer,
            model = model,
            androidVersion = androidVersion,
            sdkLevel = sdkLevel,
            securityPatch = securityPatch,
            buildId = buildId,
            screenResolution = "$width x $height px",
            screenDensity = "$densityDpi DPI",
            refreshRate = refreshRate,
            batteryLevel = batteryPct,
            batteryVoltage = voltage,
            batteryTemp = temp,
            batteryTech = tech,
            storageTotalGb = totalStorageGb,
            storageFreeGb = freeStorageGb
        )
    }
}
