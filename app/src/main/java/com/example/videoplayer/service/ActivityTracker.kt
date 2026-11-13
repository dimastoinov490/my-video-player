package com.example.videoplayer.service

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object ActivityTracker {

    private class SlidingWindowCounter {
        @Volatile
        private var curSec: Long = 0L
        private var curBytes: Long = 0L
        private var prevSec: Long = 0L
        private var prevBytes: Long = 0L

        @Synchronized
        fun add(bytes: Long, nowMs: Long = System.currentTimeMillis()) {
            if (bytes <= 0L) return
            val sec = nowMs / 1000L
            if (sec == curSec) {
                curBytes += bytes
            } else {
                prevSec = curSec
                prevBytes = curBytes
                curSec = sec
                curBytes = bytes
            }
        }

        @Synchronized
        fun bps(nowMs: Long = System.currentTimeMillis()): Long {
            val sec = nowMs / 1000L
            return when {
                sec == curSec -> {
                    val prev = if (prevSec == sec - 1L) prevBytes else 0L
                    maxOf(curBytes, prev)
                }
                curSec == sec - 1L -> curBytes
                else -> 0L
            }
        }
    }

    private class Entry(
        val key: String,
        @Volatile var sessionToken: String?,
        val deviceId: String,
        @Volatile var login: String,
        @Volatile var role: String,
        @Volatile var device: String,
        @Volatile var ip: String,
        val since: Long
    ) {
        @Volatile var state: String = "browsing"
        @Volatile var file: String = ""
        @Volatile var watchedSec: Int = 0
        @Volatile var posSec: Double = 0.0
        @Volatile var durSec: Double = 0.0
        @Volatile var totalBytes: Long = 0L
        @Volatile var lastActivityMs: Long = since
        @Volatile var lastBeatMs: Long = 0L
        @Volatile var lastStreamRangeMs: Long = 0L
        @Volatile var lastTransferMs: Long = 0L
        val downWindow = SlidingWindowCounter()
        val upWindow = SlidingWindowCounter()
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val totalDownBytes = AtomicLong(0L)
    private val totalUpBytes = AtomicLong(0L)

    fun makeKey(sessionToken: String?, accountId: String, deviceId: String): String {
        val sess = if (!sessionToken.isNullOrBlank()) sessionToken else "acc_$accountId"
        return "$sess:$deviceId"
    }

    private fun getOrCreate(
        key: String,
        sessionToken: String?,
        deviceId: String,
        login: String,
        role: String,
        device: String,
        ip: String,
        now: Long
    ): Entry {
        val existing = entries[key]
        if (existing != null) {
            if (sessionToken != null) existing.sessionToken = sessionToken
            if (login.isNotBlank()) existing.login = login
            if (role.isNotBlank()) existing.role = role
            if (device.isNotBlank()) existing.device = device
            if (ip.isNotBlank()) existing.ip = ip
            return existing
        }
        val created = Entry(
            key = key,
            sessionToken = sessionToken,
            deviceId = deviceId,
            login = login.ifBlank { "гость" },
            role = role.ifBlank { "user" },
            device = device.ifBlank { "Устройство" },
            ip = ip,
            since = now
        )
        return entries.putIfAbsent(key, created) ?: created
    }

    fun onBrowse(
        key: String,
        sessionToken: String?,
        deviceId: String,
        login: String,
        role: String,
        device: String,
        ip: String
    ) {
        val now = System.currentTimeMillis()
        val e = getOrCreate(key, sessionToken, deviceId, login, role, device, ip, now)
        synchronized(e) {
            e.lastActivityMs = now
            val activeMedia = (e.state == "watching" || e.state == "listening") &&
                (now - maxOf(e.lastBeatMs, e.lastStreamRangeMs) <= 15_000L)
            val activeXfer = (e.state == "downloading" || e.state == "uploading") &&
                (now - e.lastTransferMs <= 4_000L)
            if (!activeMedia && !activeXfer) {
                e.state = "browsing"
            }
        }
    }

    fun recordStreamBytes(
        key: String,
        sessionToken: String?,
        deviceId: String,
        login: String,
        role: String,
        device: String,
        ip: String,
        fileName: String,
        isVideo: Boolean,
        bytesSent: Long
    ) {
        val now = System.currentTimeMillis()
        val e = getOrCreate(key, sessionToken, deviceId, login, role, device, ip, now)
        if (bytesSent > 0L) {
            totalDownBytes.addAndGet(bytesSent)
            e.downWindow.add(bytesSent, now)
        }
        synchronized(e) {
            e.lastActivityMs = now
            e.lastStreamRangeMs = now
            if (bytesSent > 0L) e.totalBytes += bytesSent
            if (fileName.isNotBlank()) e.file = fileName
            // Keep watching/listening unless client explicitly sent heartbeat specifying audio
            if (e.state != "watching" && e.state != "listening") {
                e.state = if (isVideo) "watching" else "listening"
            } else {
                e.state = if (isVideo) "watching" else "listening"
            }
        }
    }

    fun recordDownloadBytes(
        key: String,
        sessionToken: String?,
        deviceId: String,
        login: String,
        role: String,
        device: String,
        ip: String,
        fileName: String,
        isExplicitDownload: Boolean,
        bytesSent: Long
    ) {
        val now = System.currentTimeMillis()
        val e = getOrCreate(key, sessionToken, deviceId, login, role, device, ip, now)
        if (bytesSent > 0L) {
            totalDownBytes.addAndGet(bytesSent)
            e.downWindow.add(bytesSent, now)
        }
        synchronized(e) {
            e.lastActivityMs = now
            if (bytesSent > 0L) e.totalBytes += bytesSent
            if (isExplicitDownload) {
                e.lastTransferMs = now
                e.state = "downloading"
                if (fileName.isNotBlank()) e.file = fileName
            }
        }
    }

    fun recordUploadBytes(
        key: String,
        sessionToken: String?,
        deviceId: String,
        login: String,
        role: String,
        device: String,
        ip: String,
        fileName: String,
        bytesRead: Long
    ) {
        val now = System.currentTimeMillis()
        val e = getOrCreate(key, sessionToken, deviceId, login, role, device, ip, now)
        if (bytesRead > 0L) {
            totalUpBytes.addAndGet(bytesRead)
            e.upWindow.add(bytesRead, now)
        }
        synchronized(e) {
            e.lastActivityMs = now
            e.lastTransferMs = now
            if (bytesRead > 0L) e.totalBytes += bytesRead
            e.state = "uploading"
            if (fileName.isNotBlank()) e.file = fileName
        }
    }

    fun onHeartbeat(
        key: String,
        sessionToken: String?,
        deviceId: String,
        login: String,
        role: String,
        device: String,
        ip: String,
        file: String,
        posSec: Double,
        durSec: Double,
        isAudio: Boolean
    ) {
        val now = System.currentTimeMillis()
        val e = getOrCreate(key, sessionToken, deviceId, login, role, device, ip, now)
        synchronized(e) {
            val prevBeat = e.lastBeatMs
            e.lastActivityMs = now
            e.lastBeatMs = now
            e.state = if (isAudio) "listening" else "watching"
            if (file.isNotBlank()) e.file = file
            if (posSec >= 0.0) e.posSec = posSec
            if (durSec >= 0.0) e.durSec = durSec
            if (prevBeat > 0L) {
                val deltaSec = ((now - prevBeat) / 1000L).toInt().coerceIn(0, 10)
                e.watchedSec += deltaSec
            } else {
                e.watchedSec += 5
            }
        }
    }

    fun removeBySession(sessionToken: String?) {
        if (sessionToken.isNullOrBlank()) return
        entries.values.filter { it.sessionToken == sessionToken }.forEach { entries.remove(it.key) }
    }

    fun removeByDevice(deviceId: String?) {
        if (deviceId.isNullOrBlank()) return
        entries.values.filter { it.deviceId == deviceId }.forEach { entries.remove(it.key) }
    }

    fun clearAll() {
        entries.clear()
    }

    fun resetTotals() {
        totalDownBytes.set(0L)
        totalUpBytes.set(0L)
        entries.clear()
    }

    fun getTotalTrafficBytes(): Pair<Long, Long> = Pair(totalDownBytes.get(), totalUpBytes.get())

    fun getTotalDownBps(): Long {
        val now = System.currentTimeMillis()
        var sum = 0L
        for (e in entries.values) sum += e.downWindow.bps(now)
        return sum
    }

    fun getTotalUpBps(): Long {
        val now = System.currentTimeMillis()
        var sum = 0L
        for (e in entries.values) sum += e.upWindow.bps(now)
        return sum
    }

    fun getActiveTransfersCount(): Int {
        val now = System.currentTimeMillis()
        var count = 0
        for (e in entries.values) {
            synchronized(e) {
                val mediaActive = (e.state == "watching" || e.state == "listening") &&
                    (now - maxOf(e.lastBeatMs, e.lastStreamRangeMs) <= 15_000L)
                val xferActive = (e.state == "downloading" || e.state == "uploading") &&
                    (now - e.lastTransferMs <= 5_000L)
                if (mediaActive || xferActive) count++
            }
        }
        return count
    }

    fun getSnapshotJson(): JSONObject {
        val now = System.currentTimeMillis()
        // Prune stale entries (> 30 min idle)
        entries.entries.removeIf { (_, e) -> now - e.lastActivityMs > 30L * 60_000L }

        val list = ArrayList<JSONObject>()
        var totalDownBps = 0L
        var totalUpBps = 0L
        var activeCount = 0

        val sorted = entries.values.sortedByDescending { it.lastActivityMs }
        for (e in sorted) {
            val downBps = e.downWindow.bps(now)
            val upBps = e.upWindow.bps(now)
            var computedState: String
            var fileName: String
            var watched: Int
            var pos: Double
            var dur: Double
            var bytes: Long
            synchronized(e) {
                computedState = e.state
                if ((computedState == "watching" || computedState == "listening") &&
                    (now - maxOf(e.lastBeatMs, e.lastStreamRangeMs) > 15_000L)
                ) {
                    computedState = "browsing"
                    e.state = "browsing"
                } else if ((computedState == "downloading" || computedState == "uploading") &&
                    (now - e.lastTransferMs > 5_000L)
                ) {
                    computedState = "browsing"
                    e.state = "browsing"
                }
                if (now - e.lastActivityMs > 30_000L) {
                    computedState = "idle"
                }
                fileName = if (computedState == "browsing" || computedState == "idle") "" else e.file
                watched = e.watchedSec
                pos = e.posSec
                dur = e.durSec
                bytes = e.totalBytes
            }

            val speedBps = if (computedState == "idle") 0L else (downBps + upBps)
            if (computedState != "idle") {
                activeCount++
                totalDownBps += downBps
                totalUpBps += upBps
            }

            val obj = JSONObject()
            obj.put("login", e.login)
            obj.put("role", e.role)
            obj.put("device", e.device)
            obj.put("ip", e.ip)
            obj.put("state", computedState)
            obj.put("file", fileName)
            obj.put("watchedSec", watched)
            obj.put("posSec", Math.round(pos * 10.0) / 10.0)
            obj.put("durSec", Math.round(dur * 10.0) / 10.0)
            obj.put("speedBps", speedBps)
            obj.put("downBps", downBps)
            obj.put("upBps", upBps)
            obj.put("bytes", bytes)
            obj.put("since", e.since)
            list.add(obj)
        }

        val usersArr = JSONArray()
        list.forEach { usersArr.put(it) }

        val totals = JSONObject()
        totals.put("downBps", totalDownBps)
        totals.put("upBps", totalUpBps)
        totals.put("active", activeCount)
        totals.put("totalDownBytes", totalDownBytes.get())
        totals.put("totalUpBytes", totalUpBytes.get())

        val root = JSONObject()
        root.put("users", usersArr)
        root.put("totals", totals)
        return root
    }
}
