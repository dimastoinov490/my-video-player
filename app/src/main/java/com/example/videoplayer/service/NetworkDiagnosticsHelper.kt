package com.example.videoplayer.service

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object NetworkDiagnosticsHelper {

    const val MAX_SPEED_TEST_BYTES: Long = 64L * 1024L * 1024L
    const val DEFAULT_DOWN_BYTES: Long = 8L * 1024L * 1024L
    private const val RATE_LIMIT_MS: Long = 10_000L

    private val COMMON_LOCAL_PORTS = listOf(80, 443, 8765, 2121, 8080)
    private val IPV4_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    private val speedRateMap = ConcurrentHashMap<String, Long>()
    private val activeDiagnosticsTransfers = AtomicInteger(0)

    /**
     * Checks and consumes rate-limit slot (1 per 10s per user+direction).
     * Returns 0 if allowed, or remaining cooldown seconds (>0) if limited.
     */
    fun checkAndConsumeSpeedLimit(userKey: String, direction: String): Int {
        val key = "$userKey:$direction"
        val now = System.currentTimeMillis()
        var retryAfter = 0
        speedRateMap.compute(key) { _, prev ->
            if (prev != null && now - prev < RATE_LIMIT_MS) {
                val remMs = RATE_LIMIT_MS - (now - prev)
                retryAfter = ((remMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                prev
            } else {
                now
            }
        }
        return retryAfter
    }

    fun streamDownloadSpeed(
        output: OutputStream,
        requestedSize: Long,
        isHead: Boolean,
        isHttps: Boolean,
        needDevCookie: String?
    ) {
        val size = requestedSize.coerceIn(1L, MAX_SPEED_TEST_BYTES)
        val header = StringBuilder("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: application/octet-stream\r\n")
            .append("Content-Length: ").append(size).append("\r\n")
            .append("Cache-Control: no-store, no-transform\r\n")
            .append("Content-Encoding: identity\r\n")
        if (isHttps) {
            header.append("Strict-Transport-Security: max-age=31536000; includeSubDomains\r\n")
            header.append("X-Content-Type-Options: nosniff\r\n")
        }
        if (needDevCookie != null) {
            val secureFlag = if (isHttps) "; Secure" else ""
            header.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly$secureFlag\r\n")
        }
        header.append("Connection: close\r\n\r\n")
        output.write(header.toString().toByteArray(Charsets.UTF_8))

        if (isHead) {
            output.flush()
            return
        }

        activeDiagnosticsTransfers.incrementAndGet()
        try {
            val chunk = ByteArray(64 * 1024)
            var state = 0x5A17C9E3.toInt() xor System.nanoTime().toInt()
            var remaining = size
            while (remaining > 0L) {
                val len = minOf(chunk.size.toLong(), remaining).toInt()
                var i = 0
                while (i + 3 < len) {
                    state = state xor (state shl 13)
                    state = state xor (state ushr 17)
                    state = state xor (state shl 5)
                    chunk[i] = (state ushr 24).toByte()
                    chunk[i + 1] = (state ushr 16).toByte()
                    chunk[i + 2] = (state ushr 8).toByte()
                    chunk[i + 3] = state.toByte()
                    i += 4
                }
                while (i < len) {
                    state = state xor (state shl 13)
                    chunk[i] = state.toByte()
                    i++
                }
                output.write(chunk, 0, len)
                remaining -= len.toLong()
            }
            output.flush()
        } finally {
            activeDiagnosticsTransfers.decrementAndGet()
        }
    }

    fun handleUploadSpeed(rawInput: InputStream, contentLength: Long): JSONObject {
        activeDiagnosticsTransfers.incrementAndGet()
        try {
            val buf = ByteArray(64 * 1024)
            var remaining = contentLength.coerceIn(0L, MAX_SPEED_TEST_BYTES)
            var totalRead = 0L
            val t0 = System.nanoTime()
            while (remaining > 0L) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                val r = rawInput.read(buf, 0, toRead)
                if (r <= 0) break
                totalRead += r.toLong()
                remaining -= r.toLong()
            }
            val elapsedMs = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            return JSONObject().apply {
                put("ok", true)
                put("bytes", totalRead)
                put("ms", elapsedMs)
            }
        } finally {
            activeDiagnosticsTransfers.decrementAndGet()
        }
    }

    fun getLoadJson(activeThreads: Int, activeSessions: Int): JSONObject {
        val mediaTransfers = ActivityTracker.getActiveTransfersCount()
        val diagTransfers = activeDiagnosticsTransfers.get().coerceAtLeast(0)
        return JSONObject().apply {
            put("activeTransfers", mediaTransfers + diagTransfers)
            put("downBps", ActivityTracker.getTotalDownBps())
            put("upBps", ActivityTracker.getTotalUpBps())
            put("threads", activeThreads.coerceAtLeast(0))
            put("sessions", activeSessions.coerceAtLeast(0))
        }
    }

    /**
     * Strictly accepts only RFC1918 private IPv4 literals (10/8, 172.16/12, 192.168/16).
     * Rejects domains, loopback, link-local, multicast, or public IPs without DNS lookups.
     */
    fun isValidPrivateLanIpv4(host: String): InetAddress? {
        val m = IPV4_REGEX.matchEntire(host.trim()) ?: return null
        val o0 = m.groupValues[1].toIntOrNull() ?: return null
        val o1 = m.groupValues[2].toIntOrNull() ?: return null
        val o2 = m.groupValues[3].toIntOrNull() ?: return null
        val o3 = m.groupValues[4].toIntOrNull() ?: return null
        if (o0 !in 0..255 || o1 !in 0..255 || o2 !in 0..255 || o3 !in 0..255) return null
        val isRfc1918 = o0 == 10 || (o0 == 172 && o1 in 16..31) || (o0 == 192 && o1 == 168)
        if (!isRfc1918) return null
        return try {
            InetAddress.getByAddress(byteArrayOf(o0.toByte(), o1.toByte(), o2.toByte(), o3.toByte()))
        } catch (_: Throwable) {
            null
        }
    }

    fun checkPrivateHost(host: String): JSONObject? {
        val addr = isValidPrivateLanIpv4(host) ?: return null
        val canonicalHost = addr.hostAddress ?: host.trim()

        var icmpReachable = false
        var bestRttMs: Long? = null
        try {
            val t0 = System.nanoTime()
            icmpReachable = addr.isReachable(900)
            if (icmpReachable) {
                bestRttMs = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            }
        } catch (_: Throwable) {}

        val openPorts = ArrayList<Int>()
        val portLock = Any()
        val pool = Executors.newFixedThreadPool(COMMON_LOCAL_PORTS.size)
        try {
            for (port in COMMON_LOCAL_PORTS) {
                pool.execute {
                    val t0 = System.nanoTime()
                    try {
                        Socket().use { sock ->
                            sock.connect(InetSocketAddress(addr, port), 400)
                            val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
                            synchronized(portLock) {
                                openPorts.add(port)
                                if (bestRttMs == null || ms < bestRttMs!!) {
                                    bestRttMs = ms
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
            pool.shutdown()
            pool.awaitTermination(1200, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
        } finally {
            pool.shutdownNow()
        }

        openPorts.sort()
        val portsArr = JSONArray()
        openPorts.forEach { portsArr.put(it) }

        val reachable = icmpReachable || openPorts.isNotEmpty()
        return JSONObject().apply {
            put("ok", true)
            put("host", canonicalHost)
            put("reachable", reachable)
            if (bestRttMs != null) put("rttMs", bestRttMs) else put("rttMs", JSONObject.NULL)
            put("openPorts", portsArr)
        }
    }
}
