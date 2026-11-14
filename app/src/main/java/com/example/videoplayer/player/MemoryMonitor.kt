package com.example.videoplayer.player

import android.app.ActivityManager
import android.content.Context

object MemoryMonitor {

    data class RamInfo(
        val totalMb: Long,
        val availableMb: Long,
        val usedMb: Long,
        val usagePercent: Int
    )

    fun getRamInfo(context: Context): RamInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return RamInfo(0L, 0L, 0L, 0)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMb = (memoryInfo.totalMem / (1024L * 1024L)).coerceAtLeast(0L)
        val availableMb = (memoryInfo.availMem / (1024L * 1024L)).coerceIn(0L, totalMb)
        val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
        val usagePercent = if (totalMb > 0L) ((usedMb * 100L) / totalMb).toInt().coerceIn(0, 100) else 0

        return RamInfo(totalMb, availableMb, usedMb, usagePercent)
    }
}
