package com.example.videoplayer.player

import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File

object CpuMonitor {

    data class CoreUsage(
        val coreId: Int,
        val usagePercent: Int,
        val freqMhz: Int? = null
    )

    private data class CpuStatSample(
        val total: Long,
        val idle: Long
    )

    private val lastProcSamples = HashMap<Int, CpuStatSample>()
    private var lastProcessCpuMs: Long = -1L
    private var lastWallClockMs: Long = -1L

    fun coreUsageFlow(intervalMs: Long = 1000L): Flow<List<CoreUsage>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(getSnapshot())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    @Synchronized
    fun getSnapshot(): List<CoreUsage> {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val procSamples = readProcStatCores()
        if (procSamples.isNotEmpty()) {
            val result = ArrayList<CoreUsage>(procSamples.size)
            for ((coreId, current) in procSamples.toSortedMap()) {
                val prev = lastProcSamples[coreId]
                val usage = if (prev != null) {
                    val totalDelta = (current.total - prev.total).coerceAtLeast(0L)
                    val idleDelta = (current.idle - prev.idle).coerceAtLeast(0L)
                    if (totalDelta > 0L) {
                        (((totalDelta - idleDelta) * 100L) / totalDelta).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                } else {
                    readCoreFreqPercent(coreId) ?: 0
                }
                lastProcSamples[coreId] = current
                result.add(
                    CoreUsage(
                        coreId = coreId,
                        usagePercent = usage,
                        freqMhz = readCoreCurrentFreqMhz(coreId)
                    )
                )
            }
            return result
        }

        // Fallback when /proc/stat is blocked by SELinux on Android 8+:
        // Combine per-core cpufreq scaling (when readable) with real process CPU utilization.
        val processUsage = readProcessCpuPercent(coreCount)
        val result = ArrayList<CoreUsage>(coreCount)
        for (coreId in 0 until coreCount) {
            val freqMhz = readCoreCurrentFreqMhz(coreId)
            val freqPercent = readCoreFreqPercent(coreId)
            val usage = freqPercent ?: processUsage
            result.add(
                CoreUsage(
                    coreId = coreId,
                    usagePercent = usage.coerceIn(0, 100),
                    freqMhz = freqMhz
                )
            )
        }
        return result
    }

    private fun readProcStatCores(): Map<Int, CpuStatSample> {
        val file = File("/proc/stat")
        if (!file.canRead()) return emptyMap()
        return try {
            val map = HashMap<Int, CpuStatSample>()
            file.forEachLine { line ->
                if (line.startsWith("cpu") && line.length > 3 && line[3].isDigit()) {
                    val parts = line.trim().split(Regex("\\s+"))
                    val coreId = parts[0].removePrefix("cpu").toIntOrNull() ?: return@forEachLine
                    if (parts.size >= 5) {
                        val user = parts[1].toLongOrNull() ?: 0L
                        val nice = parts[2].toLongOrNull() ?: 0L
                        val system = parts[3].toLongOrNull() ?: 0L
                        val idle = parts[4].toLongOrNull() ?: 0L
                        val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                        val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                        val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                        val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L
                        val idleTotal = idle + iowait
                        val total = user + nice + system + idle + iowait + irq + softirq + steal
                        map[coreId] = CpuStatSample(total = total, idle = idleTotal)
                    }
                }
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun readCoreCurrentFreqMhz(coreId: Int): Int? {
        return readLongFromFile("/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_cur_freq")
            ?.let { (it / 1000L).toInt().takeIf { mhz -> mhz > 0 } }
    }

    private fun readCoreFreqPercent(coreId: Int): Int? {
        val cur = readLongFromFile("/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_cur_freq") ?: return null
        val max = readLongFromFile("/sys/devices/system/cpu/cpu$coreId/cpufreq/cpuinfo_max_freq") ?: return null
        if (max <= 0L) return null
        return ((cur * 100L) / max).toInt().coerceIn(0, 100)
    }

    private fun readProcessCpuPercent(coreCount: Int): Int {
        val nowWall = SystemClock.elapsedRealtime()
        val nowCpu = Process.getElapsedCpuTime()
        val prevWall = lastWallClockMs
        val prevCpu = lastProcessCpuMs
        lastWallClockMs = nowWall
        lastProcessCpuMs = nowCpu
        if (prevWall < 0L || prevCpu < 0L) return 0
        val wallDelta = (nowWall - prevWall).coerceAtLeast(1L)
        val cpuDelta = (nowCpu - prevCpu).coerceAtLeast(0L)
        return ((cpuDelta * 100L) / (wallDelta * coreCount.coerceAtLeast(1))).toInt().coerceIn(0, 100)
    }

    private fun readLongFromFile(path: String): Long? {
        return try {
            val file = File(path)
            if (!file.canRead()) return null
            file.readText().trim().toLongOrNull()
        } catch (_: Throwable) {
            null
        }
    }
}
