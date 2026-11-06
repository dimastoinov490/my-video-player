package com.example.videoplayer.service

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.net.ssl.SSLServerSocket

data class HttpMediaFile(
    val id: String,
    val title: String,
    val fileName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val contentUri: Uri?,
    val directFile: File?
)

data class FileUploadMeta(
    val name: String,
    val mime: String,
    val dur: Int,
    val size: Long
)

data class SessionInfo(
    val token: String,
    val username: String,
    val role: String, // "admin" or "user"
    val deviceId: String
)

data class AuthResult(
    val username: String,
    val role: String
)

data class RequestAuth(
    val isAuthorized: Boolean,
    val username: String,
    val role: String,
    val sessionToken: String?
)

data class AdminAccount(
    val name: String,
    val salt: String,
    val hash: String,
    val role: String = "admin",
    val created: Long
)

data class DeviceRecord(
    val id: String,
    var ip: String,
    var ua: String,
    var account: String,
    var role: String,
    val firstSeen: Long,
    var lastLogin: Long,
    var lastSeen: Long,
    var sessionStart: Long,
    var blocked: Boolean
) {
    val name: String
        get() = LocalHttpShareServer.parseBrowserAndOs(ua)
    val kind: String
        get() = LocalHttpShareServer.parseDeviceKind(ua)
    fun isOnline(): Boolean = (System.currentTimeMillis() - lastSeen) <= 15000L
}

typealias DeviceSession = DeviceRecord

data class WifiSignal(val level: Int?, val rssi: Int?)

class RateLimiter(private val maxFailures: Int, private val windowMs: Long) {
    private val attempts = ConcurrentHashMap<String, MutableList<Long>>()

    fun isRateLimited(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val list = attempts[ip] ?: return false
        synchronized(list) {
            list.removeAll { now - it > windowMs }
            return list.size >= maxFailures
        }
    }

    fun recordFailure(ip: String) {
        val now = System.currentTimeMillis()
        val list = attempts.computeIfAbsent(ip) { Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            list.removeAll { now - it > windowMs }
            list.add(now)
        }
    }

    fun recordSuccess(ip: String) {
        attempts.remove(ip)
    }
}

object LocalHttpShareServer {
    private const val TAG = "LocalHttpShareServer"
    const val DEFAULT_PORT = 8765

    private val activeSessions = ConcurrentHashMap<String, SessionInfo>()
    private val loginRateLimiter = RateLimiter(maxFailures = 10, windowMs = 5 * 60 * 1000L)
    private val setupPasswordRateLimiter = RateLimiter(maxFailures = 5, windowMs = 10 * 60 * 1000L)

    private val devices = ConcurrentHashMap<String, DeviceRecord>()
    @Volatile private var devicesLoaded = false
    @Volatile private var lastDevicesSaveTime = 0L

    @Volatile private var wifiLevel: Int? = null
    @Volatile private var wifiRssi: Int? = null
    @Volatile private var wifiMeasurementTime: Long = 0L
    @Volatile private var wifiPollerRunning = false
    private var wifiThread: Thread? = null

    private var requestedManageStorageOnce = false

    fun clearSessions() {
        activeSessions.clear()
    }

    private var serverSocket: ServerSocket? = null
    var isRunning: Boolean = false
        private set
    var currentPort: Int = DEFAULT_PORT
        private set
    var lastErrorMessage: String? = null
        private set

    @Volatile
    var currentBoundIp: String? = null
        private set

    val pendingUploads = ConcurrentHashMap<String, FileUploadMeta>()

    private val threadPool = Executors.newCachedThreadPool()

    @Synchronized
    fun start(context: Context, chosenPort: Int = DEFAULT_PORT): Boolean {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
            return true
        }
        stop()

        loadDevicesIfNeeded(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (!requestedManageStorageOnce) {
                requestedManageStorageOnce = true
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }

        return try {
            val protocol = HttpServerConfig.getProtocol(context)
            val ipMode = HttpServerConfig.getIpMode(context)
            val manualIp = HttpServerConfig.getManualIp(context)
            val portMode = HttpServerConfig.getPortMode(context)
            val configuredPort = HttpServerConfig.getPort(context)

            val availableIps = getAvailableDeviceIps().map { it.second }.toSet()
            val targetIp = if (ipMode == "manual" && manualIp.isNotBlank() && (availableIps.contains(manualIp) || manualIp == "127.0.0.1")) {
                manualIp
            } else {
                localIpAddress(context)
            }
            currentBoundIp = targetIp

            val bindAddr = if (ipMode == "manual" && manualIp.isNotBlank()) {
                InetAddress.getByName(targetIp)
            } else {
                InetAddress.getByName("0.0.0.0")
            }

            val portsToTry = if (portMode == "manual" && configuredPort in 1024..65535) {
                listOf(configuredPort)
            } else {
                listOf(chosenPort, 8765, 8766, 8767, 8080, 0)
            }

            var socket: ServerSocket? = null
            var boundPort = chosenPort

            if (protocol.equals("https", ignoreCase = true)) {
                val sslContext = CertificateManager.getOrCreateSslContext(context, targetIp)
                val ssf = sslContext.serverSocketFactory
                for (p in portsToTry) {
                    try {
                        val ss = (if (p == 0) ssf.createServerSocket(0, 50, bindAddr) else ssf.createServerSocket(p, 50, bindAddr)) as SSLServerSocket
                        ss.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
                        val supported = ss.supportedCipherSuites
                        val aeadCiphers = supported.filter { c ->
                            (c.contains("GCM") || c.contains("CHACHA20_POLY1305")) &&
                            !c.contains("CBC") && !c.contains("RC4") && !c.contains("3DES") && !c.contains("NULL")
                        }
                        if (aeadCiphers.isNotEmpty()) {
                            ss.enabledCipherSuites = aeadCiphers.toTypedArray()
                        }
                        socket = ss
                        boundPort = ss.localPort
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "HTTPS port $p unavailable: ${e.message}")
                    }
                }
            } else {
                for (p in portsToTry) {
                    try {
                        val s = if (p == 0) ServerSocket(0, 50, bindAddr) else ServerSocket(p, 50, bindAddr)
                        socket = s
                        boundPort = s.localPort
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Port $p unavailable, trying next: ${e.message}")
                    }
                }
            }

            if (socket == null) {
                throw IOException("Не удалось занять сетевой порт")
            }

            serverSocket = socket
            currentPort = boundPort
            isRunning = true
            lastErrorMessage = null

            startWifiPoller(context)

            threadPool.execute {
                acceptClientsLoop(socket, context.applicationContext)
            }

            Log.i(TAG, "$protocol Server running on $bindAddr:$boundPort")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start server", e)
            lastErrorMessage = e.localizedMessage ?: e.javaClass.simpleName
            isRunning = false
            serverSocket = null
            false
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        stopWifiPoller()
        LanScanner.stopScan()
        clearSessions()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        } finally {
            serverSocket = null
        }
    }

    fun isRunning(context: Context): Boolean = isRunning && serverSocket != null && !serverSocket!!.isClosed

    fun getServerUrl(context: Context): String {
        val proto = HttpServerConfig.getProtocol(context)
        val ip = currentBoundIp ?: localIpAddress(context)
        val port = currentPort
        val isDefault = (proto == "http" && port == 80) || (proto == "https" && port == 443)
        return if (isDefault) "$proto://$ip" else "$proto://$ip:$port"
    }

    fun getAvailableDeviceIps(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return list
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                val label = when {
                    name.startsWith("wlan") -> "Wi-Fi (${intf.name})"
                    name.startsWith("ap") || name.startsWith("softap") -> "Точка доступа (${intf.name})"
                    name.startsWith("eth") -> "Ethernet (${intf.name})"
                    name.startsWith("rndis") || name.startsWith("usb") -> "USB-модем (${intf.name})"
                    name.startsWith("p2p") -> "Wi-Fi Direct (${intf.name})"
                    else -> "Сеть (${intf.name})"
                }
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!list.any { it.second == ip }) {
                            list.add(Pair(label, ip))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun getCandidatePorts(context: Context): List<Int> {
        val candidateList = listOf(currentPort, 8765, 8080, 8000, 8888, 9000, 5000, 3000)
        return candidateList.distinct().filter { it == currentPort || isPortAvailable(it) }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    fun getDownloadUrl(context: Context, file: File): String {
        return try {
            val name = URLEncoder.encode(file.name, Charsets.UTF_8.name())
            "${getServerUrl(context)}/download?id=$name"
        } catch (_: Throwable) {
            "${getServerUrl(context)}/download?id=${file.name}"
        }
    }

    private fun startWifiPoller(context: Context) {
        stopWifiPoller()
        wifiPollerRunning = true
        wifiThread = Thread({
            val appContext = context.applicationContext
            while (wifiPollerRunning && isRunning) {
                try {
                    val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val info = wm?.connectionInfo
                    if (info != null) {
                        val rssi = info.rssi
                        if (info.networkId == -1 || rssi <= -127) {
                            wifiLevel = null
                            wifiRssi = null
                        } else {
                            @Suppress("DEPRECATION")
                            val lvl = WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
                            wifiLevel = lvl
                            wifiRssi = rssi
                        }
                    } else {
                        wifiLevel = null
                        wifiRssi = null
                    }
                    wifiMeasurementTime = System.currentTimeMillis()
                } catch (_: Exception) {
                    wifiLevel = null
                    wifiRssi = null
                }
                try {
                    Thread.sleep(4000L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "Nexora-WifiPoller").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopWifiPoller() {
        wifiPollerRunning = false
        wifiThread?.interrupt()
        wifiThread = null
    }

    private fun getWifiSignal(): WifiSignal {
        val now = System.currentTimeMillis()
        val isStale = (now - wifiMeasurementTime) > 12000L
        return if (isStale) {
            WifiSignal(level = null, rssi = null)
        } else {
            WifiSignal(level = wifiLevel, rssi = wifiRssi)
        }
    }

    private fun acceptClientsLoop(socket: ServerSocket, context: Context) {
        while (isRunning && !socket.isClosed) {
            try {
                val client = socket.accept()
                threadPool.execute {
                    handleClient(client, context)
                }
            } catch (_: SocketException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Exception in client accept loop", e)
            }
        }
    }

    private fun readHttpHeaderLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (baos.size() == 0) return null
                break
            }
            if (b == '\n'.code) {
                break
            } else if (b != '\r'.code) {
                baos.write(b)
            }
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    private fun handleClient(client: Socket, context: Context) {
        try {
            client.soTimeout = 30000
            val clientIp = client.inetAddress?.hostAddress ?: "127.0.0.1"
            val rawInput = client.getInputStream()
            val output = BufferedOutputStream(client.getOutputStream())

            val requestLine = readHttpHeaderLine(rawInput) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val pathWithQuery = parts[1]
            val rawPath = pathWithQuery.substringBefore("?")
            val queryString = if (pathWithQuery.contains("?")) pathWithQuery.substringAfter("?") else ""
            val decodedPath = URLDecoder.decode(rawPath, Charsets.UTF_8.name())

            // Read headers
            var authHeader: String? = null
            var rangeHeader: String? = null
            var cookieHeader: String? = null
            var contentLengthHeader: Long? = null
            var xNexoraHeader: String? = null
            var originHeader: String? = null
            var hostHeader: String? = null
            var userAgentHeader: String? = null
            var acceptHeader: String? = null

            var line = readHttpHeaderLine(rawInput)
            while (!line.isNullOrBlank()) {
                val lower = line.lowercase()
                if (lower.startsWith("authorization:")) {
                    authHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("range:")) {
                    rangeHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("cookie:")) {
                    cookieHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("content-length:")) {
                    contentLengthHeader = line.substringAfter(":").trim().toLongOrNull()
                } else if (lower.startsWith("x-nexora:")) {
                    xNexoraHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("origin:")) {
                    originHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("host:")) {
                    hostHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("user-agent:")) {
                    userAgentHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("accept:")) {
                    acceptHeader = line.substringAfter(":").trim()
                }
                line = readHttpHeaderLine(rawInput)
            }

            val userAgent = userAgentHeader ?: ""
            val existingDevId = extractCookie(cookieHeader, "nx_dev")
            val clientDevId = if (existingDevId.isNullOrBlank()) UUID.randomUUID().toString() else existingDevId
            val needDevCookie = if (existingDevId.isNullOrBlank()) clientDevId else null

            // Check if device or IP is blocked
            if (isDeviceOrIpBlocked(context, clientDevId, clientIp)) {
                val isHtml = acceptHeader?.contains("text/html") == true ||
                        decodedPath == "/" || decodedPath == "/index.html" || decodedPath == "/login"
                sendBlockedResponse(output, isHtml, needDevCookie)
                return
            }

            val authRequired = HttpServerConfig.isAuthRequired(context)
            val sessionToken = extractCookie(cookieHeader, "nx_session")
            val reqAuth = resolveRequestAuth(sessionToken, authHeader, authRequired, context)

            // Touch device if authorized
            if (reqAuth.isAuthorized) {
                touchDevice(context, clientDevId, clientIp, userAgent, reqAuth.username, reqAuth.role, isLogin = false)
            }

            if (method == "GET" || method == "HEAD") {
                val isHead = method == "HEAD"

                when {
                    decodedPath == "/api/login" -> {
                        if (loginRateLimiter.isRateLimited(clientIp)) {
                            sendResponse(
                                output, 429, "application/json; charset=UTF-8",
                                "{\"error\":\"rate_limited\"}".toByteArray(Charsets.UTF_8),
                                isHead = isHead,
                                extraHeaders = listOf("Cache-Control: no-store"),
                                needDevCookie = needDevCookie
                            )
                            return
                        }

                        val authResult = if (!authRequired) {
                            AuthResult(username = HttpServerConfig.getUsername(context), role = "user")
                        } else {
                            checkCredentials(authHeader, context)
                        }

                        if (authResult != null) {
                            loginRateLimiter.recordSuccess(clientIp)
                            val token = UUID.randomUUID().toString().replace("-", "")
                            activeSessions[token] = SessionInfo(
                                token = token,
                                username = authResult.username,
                                role = authResult.role,
                                deviceId = clientDevId
                            )
                            touchDevice(context, clientDevId, clientIp, userAgent, authResult.username, authResult.role, isLogin = true)

                            val json = "{\"ok\":true,\"role\":${jsonEscape(authResult.role)},\"name\":${jsonEscape(authResult.username)}}".toByteArray(Charsets.UTF_8)
                            val headers = mutableListOf(
                                "Set-Cookie: nx_session=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400",
                                "Cache-Control: no-store"
                            )
                            sendResponse(output, 200, "application/json; charset=UTF-8", json, isHead = isHead, extraHeaders = headers, needDevCookie = needDevCookie)
                        } else {
                            loginRateLimiter.recordFailure(clientIp)
                            val json = "{\"ok\":false}".toByteArray(Charsets.UTF_8)
                            sendResponse(output, 401, "application/json; charset=UTF-8", json, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    decodedPath == "/logout" -> {
                        if (!sessionToken.isNullOrEmpty()) {
                            activeSessions.remove(sessionToken)
                        }
                        sendRedirect(output, "/", needDevCookie = needDevCookie, extraHeaders = listOf("Set-Cookie: nx_session=; Path=/; Max-Age=0"))
                    }
                    decodedPath == "/login" -> {
                        if (!authRequired || reqAuth.isAuthorized) {
                            sendRedirect(output, "/", needDevCookie = needDevCookie)
                        } else {
                            sendHtmlLogin(output, context, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/" || decodedPath == "/index.html" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            sendHtmlLogin(output, context, isHead, needDevCookie)
                        } else {
                            val mediaList = loadMediaList(context)
                            sendHtmlIndex(output, mediaList, context, isHead, reqAuth, needDevCookie)
                        }
                    }
                    decodedPath == "/stream" || decodedPath.startsWith("/stream/") -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithAuth(output, needDevCookie)
                        } else {
                            val idParam = extractQueryParam(queryString, "id")
                                ?: decodedPath.removePrefix("/stream/").removePrefix("/")
                            serveMediaContent(output, idParam, rangeHeader, context, asDownload = false, isHead = isHead, needDevCookie = needDevCookie)
                        }
                    }
                    decodedPath == "/download" || decodedPath.startsWith("/download/") -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithAuth(output, needDevCookie)
                        } else {
                            val idParam = extractQueryParam(queryString, "id")
                                ?: decodedPath.removePrefix("/download/").removePrefix("/")
                            serveMediaContent(output, idParam, rangeHeader, context, asDownload = true, isHead = isHead, needDevCookie = needDevCookie)
                        }
                    }
                    decodedPath == "/thumb" || decodedPath.startsWith("/thumb/") -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val idParam = extractQueryParam(queryString, "id")
                                ?: decodedPath.removePrefix("/thumb/").removePrefix("/")
                            serveThumbnail(output, idParam, context, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/api/photos" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val albumParam = extractQueryParam(queryString, "album")
                            serveApiPhotos(output, albumParam, context, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/photo" -> {
                        val isThumb = extractQueryParam(queryString, "thumb") == "1"
                        val idParam = extractQueryParam(queryString, "id")
                        val isDl = extractQueryParam(queryString, "dl") == "1"
                        if (isThumb) {
                            if (authRequired && !reqAuth.isAuthorized) {
                                send401WithoutAuth(output, needDevCookie)
                            } else {
                                servePhotoThumbnail(output, idParam, context, isHead, needDevCookie)
                            }
                        } else {
                            if (authRequired && !reqAuth.isAuthorized) {
                                send401WithAuth(output, needDevCookie)
                            } else {
                                servePhotoOriginal(output, idParam, isDl, context, isHead, needDevCookie)
                            }
                        }
                    }
                    decodedPath == "/api/fs" -> {
                        if (!authRequired) {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"auth_required\"}".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
                        } else if (!reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val pathParam = extractQueryParam(queryString, "path")
                            serveApiFs(output, pathParam, context, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/file" -> {
                        if (!authRequired) {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"auth_required\"}".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
                        } else if (!reqAuth.isAuthorized) {
                            send401WithAuth(output, needDevCookie)
                        } else {
                            val pathParam = extractQueryParam(queryString, "path")
                            val isDl = extractQueryParam(queryString, "dl") == "1"
                            serveFsFile(output, pathParam, rangeHeader, isDl, context, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/api/status" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            serveApiStatus(output, reqAuth, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/api/devices" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            serveApiDevices(output, context, clientDevId, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/api/accounts" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else if (reqAuth.role != "admin") {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"admin_required\"}".toByteArray(Charsets.UTF_8), isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else {
                            serveApiAccounts(output, context, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/cert.crt" -> {
                        val certBytes = CertificateManager.getCertificateDer(context)
                        if (certBytes != null) {
                            val headers = listOf(
                                "Content-Disposition: attachment; filename=\"nexora.crt\"",
                                "Cache-Control: public, max-age=86400"
                            )
                            sendResponse(output, 200, "application/x-x509-ca-cert", certBytes, isHead, extraHeaders = headers, needDevCookie = needDevCookie)
                        } else {
                            sendResponse(output, 500, "text/plain", "Certificate not available".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
                        }
                    }
                    decodedPath == "/api/net" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            serveApiNet(output, context, isHead, needDevCookie)
                        }
                    }
                    decodedPath == "/api/lan/devices" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val connectedIps = devices.values.map { it.ip }.toSet()
                            val json = LanScanner.getScanResultJson(clientIp, currentBoundIp ?: localIpAddress(context), connectedIps)
                            val data = json.toString().toByteArray(Charsets.UTF_8)
                            sendResponse(output, 200, "application/json; charset=UTF-8", data, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    decodedPath == "/api/mail/peers" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val blockedIds = devices.values.filter { it.blocked }.map { it.id }.toSet()
                            val json = MailManager.getPeers(context, clientDevId, devices, blockedIds)
                            val data = json.toString().toByteArray(Charsets.UTF_8)
                            sendResponse(output, 200, "application/json; charset=UTF-8", data, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    decodedPath == "/api/mail/messages" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val peerId = extractQueryParam(queryString, "peer") ?: ""
                            val afterId = extractQueryParam(queryString, "after")?.toLongOrNull() ?: 0L
                            val json = MailManager.getMessages(context, clientDevId, peerId, afterId)
                            val data = json.toString().toByteArray(Charsets.UTF_8)
                            sendResponse(output, 200, "application/json; charset=UTF-8", data, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    decodedPath == "/api/mail/file" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val idParam = extractQueryParam(queryString, "id") ?: ""
                            val isDl = extractQueryParam(queryString, "dl") == "1"
                            val encFile = MailManager.getAttachmentFile(context, idParam)
                            if (!encFile.exists()) {
                                sendResponse(output, 404, "text/plain", "Not Found".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
                            } else {
                                val meta = MailManager.getAttachmentMeta(context, idParam)
                                val fileName = meta?.first ?: "file"
                                val fileSize = meta?.second ?: encFile.length()
                                val mimeType = meta?.third ?: "application/octet-stream"
                                serveMailFile(output, idParam, fileName, fileSize, mimeType, rangeHeader, isDl, context, isHead, needDevCookie)
                            }
                        }
                    }
                    decodedPath.startsWith("/api/") -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            sendResponse(output, 404, "application/json; charset=UTF-8", "{\"error\":\"not_found\"}".toByteArray(Charsets.UTF_8), isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    else -> {
                        val notFound = "404 Not Found"
                        sendResponse(output, 404, "text/plain; charset=UTF-8", notFound.toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
                    }
                }
            } else if (method == "POST") {
                // Must check X-Nexora header
                if (xNexoraHeader != "1") {
                    sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"bad_request\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                    return
                }

                // If Origin header is present, it must match Host
                if (!originHeader.isNullOrBlank()) {
                    val originHost = originHeader
                        .removePrefix("http://")
                        .removePrefix("https://")
                        .substringBefore("/")
                        .trim()
                    val hostValue = (hostHeader ?: "").trim()
                    if (originHost.isNotEmpty() && hostValue.isNotEmpty() && !originHost.equals(hostValue, ignoreCase = true)) {
                        sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"bad_request\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        return
                    }
                }

                if (decodedPath == "/api/mail/upload") {
                    if (authRequired && !reqAuth.isAuthorized) {
                        send401WithoutAuth(output, needDevCookie)
                        return
                    }
                    if (isDeviceOrIpBlocked(context, clientDevId, clientIp)) {
                        sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"blocked\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        return
                    }
                    if (!MailManager.checkUploadRateLimit(clientIp)) {
                        sendResponse(output, 429, "application/json; charset=UTF-8", "{\"error\":\"rate_limited\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        return
                    }
                    val contentLen = contentLengthHeader ?: 0L
                    if (contentLen > MailManager.MAX_FILE_SIZE) {
                        sendResponse(output, 413, "application/json; charset=UTF-8", "{\"error\":\"too_large\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        return
                    }
                    if (!MailManager.checkQuota(context, contentLen)) {
                        sendResponse(output, 400, "application/json; charset=UTF-8", "{\"error\":\"quota\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        return
                    }

                    val name = extractQueryParam(queryString, "name") ?: "file"
                    val mime = extractQueryParam(queryString, "mime") ?: "application/octet-stream"
                    val dur = extractQueryParam(queryString, "dur")?.toIntOrNull() ?: 0

                    val boundedStream = object : InputStream() {
                        private var remaining = contentLen
                        override fun read(): Int {
                            if (remaining <= 0) return -1
                            val b = rawInput.read()
                            if (b != -1) remaining--
                            return b
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (remaining <= 0) return -1
                            val toRead = minOf(len.toLong(), remaining).toInt()
                            val r = rawInput.read(b, off, toRead)
                            if (r != -1) remaining -= r
                            return r
                        }
                    }

                    try {
                        val fileId = MailManager.saveAttachmentStream(context, boundedStream, contentLen)
                        pendingUploads[fileId] = FileUploadMeta(name, mime, dur, contentLen)
                        val resp = JSONObject()
                        resp.put("fileId", fileId)
                        val data = resp.toString().toByteArray(Charsets.UTF_8)
                        sendResponse(output, 200, "application/json; charset=UTF-8", data, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                    } catch (e: Exception) {
                        val err = if (e.message == "too_large") "too_large" else "upload_failed"
                        sendResponse(output, 400, "application/json; charset=UTF-8", "{\"error\":\"$err\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                    }
                    return
                }

                val contentLength = (contentLengthHeader ?: 0L).toInt()
                if (contentLength > 64 * 1024) {
                    sendResponse(output, 413, "application/json; charset=UTF-8", "{\"error\":\"bad_request\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                    return
                }

                val bodyBytes = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = rawInput.read(bodyBytes, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                val bodyString = String(bodyBytes, 0, readTotal, Charsets.UTF_8)
                val bodyJson = try {
                    JSONObject(bodyString)
                } catch (_: Exception) {
                    JSONObject()
                }

                when (decodedPath) {
                    "/api/net" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else if (reqAuth.role != "admin") {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"ok\":false,\"code\":\"admin_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else {
                            handleNetPost(output, context, bodyJson, needDevCookie)
                        }
                    }
                    "/api/lan/scan" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else if (reqAuth.role != "admin") {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"admin_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else if (LanScanner.isScanning) {
                            sendResponse(output, 409, "application/json; charset=UTF-8", "{\"error\":\"busy\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else {
                            val connectedIps = devices.values.map { it.ip }.toSet()
                            LanScanner.startScan(context, currentBoundIp ?: localIpAddress(context), connectedIps)
                            sendResponse(output, 200, "application/json; charset=UTF-8", "{\"ok\":true}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    "/api/mail/send" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            handleMailSend(output, context, bodyJson, clientDevId, clientIp, needDevCookie)
                        }
                    }
                    "/api/mail/read" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val peer = bodyJson.optString("peer")
                            if (peer.isNotBlank()) {
                                MailManager.markRead(context, clientDevId, peer)
                            }
                            sendResponse(output, 200, "application/json; charset=UTF-8", "{\"ok\":true}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    "/api/mail/delete" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val msgId = bodyJson.optLong("id", 0L)
                            val success = MailManager.deleteMessage(context, clientDevId, msgId, reqAuth.role == "admin")
                            if (success) {
                                sendResponse(output, 200, "application/json; charset=UTF-8", "{\"ok\":true}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                            } else {
                                sendResponse(output, 403, "application/json; charset=UTF-8", "{\"ok\":false,\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                            }
                        }
                    }
                    "/api/mail/profile" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            val name = bodyJson.optString("name")
                            if (name.isNotBlank()) {
                                MailManager.saveProfile(context, clientDevId, name)
                            }
                            sendResponse(output, 200, "application/json; charset=UTF-8", "{\"ok\":true}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        }
                    }
                    "/api/accounts/create-admin" -> {
                        if (!authRequired) {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"auth_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else if (!reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else {
                            handleCreateAdmin(output, context, bodyJson, clientIp, needDevCookie)
                        }
                    }
                    "/api/accounts/delete" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else if (reqAuth.role != "admin") {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"admin_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else {
                            handleDeleteAdmin(output, context, bodyJson, reqAuth.username, needDevCookie)
                        }
                    }
                    "/api/devices/block" -> {
                        if (authRequired && !reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else if (reqAuth.role != "admin") {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"admin_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else {
                            handleBlockDevice(output, context, bodyJson, clientDevId, needDevCookie)
                        }
                    }
                    "/api/fs/delete" -> {
                        if (!authRequired) {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"auth_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else if (!reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else if (reqAuth.role != "admin") {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"admin_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else {
                            handleFsDelete(output, context, bodyJson, needDevCookie)
                        }
                    }
                    "/api/fs/rename" -> {
                        if (!authRequired) {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"auth_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else if (!reqAuth.isAuthorized) {
                            send401WithoutAuth(output, needDevCookie)
                        } else if (reqAuth.role != "admin") {
                            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"admin_required\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                        } else {
                            handleFsRename(output, context, bodyJson, needDevCookie)
                        }
                    }
                    else -> {
                        if (decodedPath.startsWith("/api/")) {
                            if (authRequired && !reqAuth.isAuthorized) {
                                send401WithoutAuth(output, needDevCookie)
                            } else {
                                sendResponse(output, 404, "application/json; charset=UTF-8", "{\"error\":\"not_found\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                            }
                        } else {
                            sendResponse(output, 404, "text/plain; charset=UTF-8", "404 Not Found".toByteArray(Charsets.UTF_8), needDevCookie = needDevCookie)
                        }
                    }
                }
            } else {
                val methodNotAllowed = "405 Method Not Allowed"
                sendResponse(output, 405, "text/plain; charset=UTF-8", methodNotAllowed.toByteArray(Charsets.UTF_8), needDevCookie = needDevCookie)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client connection: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun resolveRequestAuth(
        sessionToken: String?,
        authHeader: String?,
        authRequired: Boolean,
        context: Context
    ): RequestAuth {
        if (!authRequired) {
            if (!sessionToken.isNullOrEmpty()) {
                val s = activeSessions[sessionToken]
                if (s != null) {
                    return RequestAuth(isAuthorized = true, username = s.username, role = s.role, sessionToken = s.token)
                }
            }
            return RequestAuth(isAuthorized = true, username = "гость", role = "user", sessionToken = null)
        }

        if (!sessionToken.isNullOrEmpty()) {
            val s = activeSessions[sessionToken]
            if (s != null) {
                return RequestAuth(isAuthorized = true, username = s.username, role = s.role, sessionToken = s.token)
            }
        }

        val cred = checkCredentials(authHeader, context)
        if (cred != null) {
            return RequestAuth(isAuthorized = true, username = cred.username, role = cred.role, sessionToken = null)
        }

        return RequestAuth(isAuthorized = false, username = "", role = "user", sessionToken = null)
    }

    private fun checkCredentials(authHeader: String?, context: Context): AuthResult? {
        if (authHeader == null || !authHeader.startsWith("Basic ", ignoreCase = true)) {
            return null
        }
        val encoded = authHeader.substringAfter("Basic ").trim()
        val decoded = try {
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val colonIndex = decoded.indexOf(':')
        if (colonIndex == -1) return null
        val user = decoded.substring(0, colonIndex).trim()
        val pass = decoded.substring(colonIndex + 1)

        // 1. Check admin accounts first
        val admin = findAdmin(context, user)
        if (admin != null) {
            val salt = HttpServerConfig.hexToBytes(admin.salt)
            val expectedHash = HttpServerConfig.hexToBytes(admin.hash)
            val computedHash = HttpServerConfig.hashPassword(pass.toCharArray(), salt)
            if (java.security.MessageDigest.isEqual(computedHash, expectedHash)) {
                return AuthResult(username = admin.name, role = "admin")
            }
            return null
        }

        // 2. Check regular user
        val expectedUser = HttpServerConfig.getUsername(context)
        val expectedPass = HttpServerConfig.getPassword(context)
        if (user == expectedUser && pass == expectedPass) {
            return AuthResult(username = expectedUser, role = "user")
        }

        return null
    }

    private fun getAdminAccounts(context: Context): MutableList<AdminAccount> {
        val prefs = context.getSharedPreferences("http_accounts", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("accounts", "[]") ?: "[]"
        val list = mutableListOf<AdminAccount>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AdminAccount(
                        name = obj.getString("name"),
                        salt = obj.getString("salt"),
                        hash = obj.getString("hash"),
                        role = obj.optString("role", "admin"),
                        created = obj.optLong("created", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveAdminAccounts(context: Context, list: List<AdminAccount>) {
        val arr = JSONArray()
        for (a in list) {
            val obj = JSONObject()
            obj.put("name", a.name)
            obj.put("salt", a.salt)
            obj.put("hash", a.hash)
            obj.put("role", a.role)
            obj.put("created", a.created)
            arr.put(obj)
        }
        context.getSharedPreferences("http_accounts", Context.MODE_PRIVATE)
            .edit()
            .putString("accounts", arr.toString())
            .apply()
    }

    private fun findAdmin(context: Context, name: String): AdminAccount? {
        val trimmed = name.trim()
        return getAdminAccounts(context).find { it.name.equals(trimmed, ignoreCase = true) }
    }

    private fun handleCreateAdmin(
        output: OutputStream,
        context: Context,
        body: JSONObject,
        clientIp: String,
        needDevCookie: String?
    ) {
        val name = body.optString("name", "").trim()
        val password = body.optString("password", "")
        val setupPassword = body.optString("setupPassword", "")

        if (setupPasswordRateLimiter.isRateLimited(clientIp)) {
            sendResponse(output, 429, "application/json; charset=UTF-8", "{\"error\":\"rate_limited\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        if (!HttpServerConfig.verifyAdminSetupPassword(context, setupPassword)) {
            setupPasswordRateLimiter.recordFailure(clientIp)
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"bad_setup_password\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val nameRegex = Regex("^[A-Za-z0-9._-]{3,32}$")
        if (!name.matches(nameRegex)) {
            sendResponse(output, 400, "application/json; charset=UTF-8", "{\"error\":\"bad_name\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        if (password.length < 6) {
            sendResponse(output, 400, "application/json; charset=UTF-8", "{\"error\":\"weak_password\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val existingUser = HttpServerConfig.getUsername(context)
        if (name.equals(existingUser, ignoreCase = true) || findAdmin(context, name) != null) {
            sendResponse(output, 409, "application/json; charset=UTF-8", "{\"error\":\"exists\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        setupPasswordRateLimiter.recordSuccess(clientIp)
        val salt = HttpServerConfig.generateSalt()
        val hash = HttpServerConfig.hashPassword(password.toCharArray(), salt)
        val list = getAdminAccounts(context)
        list.add(
            AdminAccount(
                name = name,
                salt = HttpServerConfig.bytesToHex(salt),
                hash = HttpServerConfig.bytesToHex(hash),
                role = "admin",
                created = System.currentTimeMillis()
            )
        )
        saveAdminAccounts(context, list)

        sendResponse(output, 200, "application/json; charset=UTF-8", "{\"ok\":true}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun handleDeleteAdmin(
        output: OutputStream,
        context: Context,
        body: JSONObject,
        currentUsername: String,
        needDevCookie: String?
    ) {
        val name = body.optString("name", "").trim()
        if (name.equals(currentUsername, ignoreCase = true)) {
            sendResponse(output, 400, "application/json; charset=UTF-8", "{\"error\":\"self\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val list = getAdminAccounts(context)
        val removed = list.removeAll { it.name.equals(name, ignoreCase = true) }
        if (removed) {
            saveAdminAccounts(context, list)
            // Close sessions of this user
            activeSessions.values.filter { it.username.equals(name, ignoreCase = true) }.forEach {
                activeSessions.remove(it.token)
            }
        }

        sendResponse(output, 200, "application/json; charset=UTF-8", "{\"ok\":true}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun handleBlockDevice(
        output: OutputStream,
        context: Context,
        body: JSONObject,
        clientDevId: String,
        needDevCookie: String?
    ) {
        val targetId = body.optString("id", "").trim()
        val targetBlocked = body.optBoolean("blocked", false)

        if (targetId == clientDevId) {
            sendResponse(output, 400, "application/json; charset=UTF-8", "{\"error\":\"self\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        loadDevicesIfNeeded(context)
        val dev = devices[targetId]
        if (dev != null) {
            dev.blocked = targetBlocked
            saveDevicesToDisk(context, force = true)
            if (targetBlocked) {
                activeSessions.values.filter { it.deviceId == targetId }.forEach {
                    activeSessions.remove(it.token)
                }
            }
        }

        sendResponse(output, 200, "application/json; charset=UTF-8", "{\"ok\":true}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun handleFsDelete(
        output: OutputStream,
        context: Context,
        body: JSONObject,
        needDevCookie: String?
    ) {
        val pathsArray = body.optJSONArray("paths") ?: JSONArray()
        val pathsList = mutableListOf<String>()
        val maxPaths = minOf(pathsArray.length(), 200)
        for (i in 0 until maxPaths) {
            pathsList.add(pathsArray.optString(i))
        }

        val canonicalRoot = try {
            Environment.getExternalStorageDirectory().canonicalFile
        } catch (_: Exception) {
            File("/storage/emulated/0").canonicalFile
        }
        val canonicalRootPath = canonicalRoot.canonicalPath
        val androidDirPath = File(canonicalRoot, "Android").canonicalPath

        var deletedCount = 0
        val failedList = mutableListOf<String>()
        val scannedPaths = mutableListOf<String>()

        for (p in pathsList) {
            val f = try { File(p).canonicalFile } catch (_: Exception) { null }
            if (f == null) {
                failedList.add(p)
                continue
            }
            val cp = f.canonicalPath
            val isUnderRoot = cp != canonicalRootPath && cp.startsWith(canonicalRootPath + File.separator)
            val isAndroid = cp == androidDirPath || cp.startsWith(androidDirPath + File.separator)
            if (!isUnderRoot || isAndroid || !f.exists()) {
                failedList.add(p)
                continue
            }
            val ok = f.deleteRecursively()
            if (ok) {
                deletedCount++
                scannedPaths.add(f.absolutePath)
            } else {
                failedList.add(p)
            }
        }

        if (scannedPaths.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(context, scannedPaths.toTypedArray(), null, null)
            } catch (_: Exception) {}
        }

        val resp = "{\"deleted\":$deletedCount,\"failed\":[${failedList.joinToString(",") { jsonEscape(it) }}]}"
        sendResponse(output, 200, "application/json; charset=UTF-8", resp.toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun handleFsRename(
        output: OutputStream,
        context: Context,
        body: JSONObject,
        needDevCookie: String?
    ) {
        val path = body.optString("path", "").trim()
        val name = body.optString("name", "").trim()

        if (name.isEmpty() || name.length > 120 || name.contains('/') || name.contains('\\') || name.contains('\u0000') || name == "." || name == "..") {
            sendResponse(output, 400, "application/json; charset=UTF-8", "{\"error\":\"bad_name\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val canonicalRoot = try {
            Environment.getExternalStorageDirectory().canonicalFile
        } catch (_: Exception) {
            File("/storage/emulated/0").canonicalFile
        }
        val canonicalRootPath = canonicalRoot.canonicalPath
        val androidDirPath = File(canonicalRoot, "Android").canonicalPath

        val srcFile = try { File(path).canonicalFile } catch (_: Exception) { null }
        if (srcFile == null || !srcFile.exists()) {
            sendResponse(output, 404, "application/json; charset=UTF-8", "{\"error\":\"not_found\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val srcPath = srcFile.canonicalPath
        val isUnderRoot = srcPath != canonicalRootPath && srcPath.startsWith(canonicalRootPath + File.separator)
        val isAndroid = srcPath == androidDirPath || srcPath.startsWith(androidDirPath + File.separator)
        if (!isUnderRoot || isAndroid) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val parentDir = srcFile.parentFile
        if (parentDir == null) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val destFile = File(parentDir, name).canonicalFile
        val destPath = destFile.canonicalPath
        if (destFile.exists()) {
            sendResponse(output, 409, "application/json; charset=UTF-8", "{\"error\":\"exists_target\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        val renamed = srcFile.renameTo(destFile)
        if (!renamed) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"no_access\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        try {
            MediaScannerConnection.scanFile(context, arrayOf(srcFile.absolutePath, destFile.absolutePath), null, null)
        } catch (_: Exception) {}

        val resp = "{\"ok\":true,\"path\":${jsonEscape(destPath)}}"
        sendResponse(output, 200, "application/json; charset=UTF-8", resp.toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun loadDevicesIfNeeded(context: Context) {
        if (devicesLoaded) return
        synchronized(devices) {
            if (devicesLoaded) return
            val prefs = context.getSharedPreferences("http_devices", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("devices_json", "[]") ?: "[]"
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rec = DeviceRecord(
                        id = obj.getString("id"),
                        ip = obj.optString("ip", ""),
                        ua = obj.optString("ua", ""),
                        account = obj.optString("account", ""),
                        role = obj.optString("role", "user"),
                        firstSeen = obj.optLong("firstSeen", System.currentTimeMillis()),
                        lastLogin = obj.optLong("lastLogin", System.currentTimeMillis()),
                        lastSeen = obj.optLong("lastSeen", System.currentTimeMillis()),
                        sessionStart = obj.optLong("sessionStart", System.currentTimeMillis()),
                        blocked = obj.optBoolean("blocked", false)
                    )
                    devices[rec.id] = rec
                }
            } catch (_: Exception) {}
            devicesLoaded = true
        }
    }

    private fun saveDevicesToDisk(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDevicesSaveTime < 30000L) {
            return
        }
        lastDevicesSaveTime = now
        // Cleanup: remove unblocked devices older than 30 days
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)
        devices.entries.removeIf { !it.value.blocked && it.value.lastSeen < thirtyDaysAgo }

        // Max 100 devices
        if (devices.size > 100) {
            val unblocked = devices.values.filter { !it.blocked }.sortedBy { it.lastSeen }
            val toRemoveCount = devices.size - 100
            for (i in 0 until minOf(toRemoveCount, unblocked.size)) {
                devices.remove(unblocked[i].id)
            }
        }

        val arr = JSONArray()
        for (d in devices.values) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("ip", d.ip)
            obj.put("ua", d.ua)
            obj.put("account", d.account)
            obj.put("role", d.role)
            obj.put("firstSeen", d.firstSeen)
            obj.put("lastLogin", d.lastLogin)
            obj.put("lastSeen", d.lastSeen)
            obj.put("sessionStart", d.sessionStart)
            obj.put("blocked", d.blocked)
            arr.put(obj)
        }
        context.getSharedPreferences("http_devices", Context.MODE_PRIVATE)
            .edit()
            .putString("devices_json", arr.toString())
            .apply()
    }

    private fun isDeviceOrIpBlocked(context: Context, devId: String, ip: String): Boolean {
        loadDevicesIfNeeded(context)
        val dev = devices[devId]
        if (dev?.blocked == true) return true
        if (ip.isNotBlank() && ip != "127.0.0.1" && ip != "unknown") {
            if (devices.values.any { it.blocked && it.ip == ip }) {
                return true
            }
        }
        return false
    }

    private fun touchDevice(
        context: Context,
        devId: String,
        ip: String,
        ua: String,
        account: String?,
        role: String?,
        isLogin: Boolean = false
    ) {
        loadDevicesIfNeeded(context)
        val now = System.currentTimeMillis()
        val rec = devices.computeIfAbsent(devId) { id ->
            DeviceRecord(
                id = id,
                ip = ip,
                ua = ua,
                account = account ?: "",
                role = role ?: "user",
                firstSeen = now,
                lastLogin = now,
                lastSeen = now,
                sessionStart = now,
                blocked = false
            )
        }
        synchronized(rec) {
            if (now - rec.lastSeen > 2 * 60 * 1000L) {
                rec.sessionStart = now
            }
            if (isLogin) {
                rec.lastLogin = now
                if (!account.isNullOrBlank()) rec.account = account
                if (!role.isNullOrBlank()) rec.role = role
            }
            if (isLogin || now - rec.lastSeen >= 5000L) {
                rec.lastSeen = now
                if (ip.isNotBlank()) rec.ip = ip
                if (ua.isNotBlank()) rec.ua = ua
            }
        }
        saveDevicesToDisk(context, force = isLogin)
    }

    fun parseBrowserAndOs(ua: String): String {
        val browser = when {
            ua.contains("SamsungBrowser", ignoreCase = true) -> "Samsung Internet"
            ua.contains("Edg/", ignoreCase = true) || ua.contains("Edge/", ignoreCase = true) -> "Edge"
            ua.contains("OPR/", ignoreCase = true) || ua.contains("Opera", ignoreCase = true) -> "Opera"
            ua.contains("Chrome", ignoreCase = true) || ua.contains("CriOS", ignoreCase = true) -> "Chrome"
            ua.contains("Firefox", ignoreCase = true) || ua.contains("FxiOS", ignoreCase = true) -> "Firefox"
            ua.contains("Safari", ignoreCase = true) && !ua.contains("Chrome", ignoreCase = true) -> "Safari"
            else -> null
        }
        val os = when {
            ua.contains("CrOS", ignoreCase = true) -> "ChromeOS"
            ua.contains("Android", ignoreCase = true) -> "Android"
            ua.contains("iPhone", ignoreCase = true) || ua.contains("iPad", ignoreCase = true) || ua.contains("iPod", ignoreCase = true) -> "iOS"
            ua.contains("Windows", ignoreCase = true) -> "Windows"
            ua.contains("Macintosh", ignoreCase = true) || ua.contains("Mac OS", ignoreCase = true) -> "macOS"
            ua.contains("Linux", ignoreCase = true) -> "Linux"
            else -> null
        }
        return when {
            browser != null && os != null -> "$browser · $os"
            browser != null -> browser
            os != null -> os
            else -> "Устройство"
        }
    }

    fun parseDeviceKind(ua: String): String {
        val isIpad = ua.contains("iPad", ignoreCase = true)
        val isAndroid = ua.contains("Android", ignoreCase = true)
        val hasMobile = ua.contains("Mobile", ignoreCase = true) || ua.contains("Mobi", ignoreCase = true) || ua.contains("iPhone", ignoreCase = true)
        return when {
            isIpad || (isAndroid && !hasMobile) -> "tablet"
            hasMobile -> "phone"
            else -> "pc"
        }
    }

    private fun extractCookie(cookieHeader: String?, cookieName: String): String? {
        if (cookieHeader.isNullOrBlank()) return null
        val cookies = cookieHeader.split(";")
        for (cookie in cookies) {
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == cookieName) {
                return parts[1].trim()
            }
        }
        return null
    }

    private fun sendRedirect(
        output: OutputStream,
        location: String,
        needDevCookie: String? = null,
        extraHeaders: List<String> = emptyList()
    ) {
        val sb = StringBuilder("HTTP/1.1 302 Found\r\n")
        sb.append("Location: $location\r\n")
        if (needDevCookie != null) {
            sb.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        for (h in extraHeaders) {
            sb.append(h).append("\r\n")
        }
        sb.append("Content-Length: 0\r\n")
        sb.append("Connection: close\r\n\r\n")
        output.write(sb.toString().toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun send401WithAuth(output: OutputStream, needDevCookie: String? = null) {
        val body = "401 Unauthorized\r\n".toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 401 Unauthorized\r\n")
        sb.append("WWW-Authenticate: Basic realm=\"Nexora Media\"\r\n")
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n")
        sb.append("Content-Length: ${body.size}\r\n")
        if (needDevCookie != null) {
            sb.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        sb.append("Connection: close\r\n\r\n")
        output.write(sb.toString().toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun send401WithoutAuth(output: OutputStream, needDevCookie: String? = null) {
        val body = "401 Unauthorized\r\n".toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 401 Unauthorized\r\n")
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n")
        sb.append("Content-Length: ${body.size}\r\n")
        if (needDevCookie != null) {
            sb.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        sb.append("Connection: close\r\n\r\n")
        output.write(sb.toString().toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun sendBlockedResponse(output: OutputStream, isHtml: Boolean, needDevCookie: String?) {
        if (isHtml) {
            val html = """
                <!DOCTYPE html>
                <html lang="ru">
                <head><meta charset="utf-8"><title>Доступ запрещён</title></head>
                <body style="background:#080604; color:#ff8a78; font-family:sans-serif; text-align:center; padding:50px;">
                    <h2>Доступ запрещён</h2>
                    <p>Устройство заблокировано администратором.</p>
                </body>
                </html>
            """.trimIndent().toByteArray(Charsets.UTF_8)
            sendResponse(output, 403, "text/html; charset=UTF-8", html, needDevCookie = needDevCookie)
        } else {
            val json = "{\"error\":\"blocked\"}".toByteArray(Charsets.UTF_8)
            sendResponse(output, 403, "application/json; charset=UTF-8", json, needDevCookie = needDevCookie, extraHeaders = listOf("Cache-Control: no-store"))
        }
    }

    private fun extractQueryParam(query: String, paramName: String): String? {
        if (query.isBlank()) return null
        return query.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2 && kv[0] == paramName) {
                try {
                    URLDecoder.decode(kv[1], Charsets.UTF_8.name())
                } catch (_: Exception) {
                    kv[1]
                }
            } else null
        }.firstOrNull()
    }

    fun loadMediaList(context: Context): List<HttpMediaFile> {
        val result = mutableListOf<HttpMediaFile>()
        val folderSetting = HttpServerConfig.getBroadcastFolder(context)
        val specificDir = HttpServerConfig.getFolderDirectory(context, folderSetting)

        if (specificDir != null && specificDir.exists()) {
            specificDir.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { file ->
                val ext = file.extension.lowercase()
                val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts")
                val isAudio = ext in listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
                if (isVideo || isAudio) {
                    val mime = when {
                        ext == "mp4" -> "video/mp4"
                        ext == "mkv" -> "video/x-matroska"
                        ext == "webm" -> "video/webm"
                        ext == "mp3" -> "audio/mpeg"
                        ext == "wav" -> "audio/wav"
                        ext == "ogg" -> "audio/ogg"
                        isVideo -> "video/*"
                        else -> "audio/*"
                    }
                    result.add(
                        HttpMediaFile(
                            id = "file_" + file.absolutePath.hashCode().toString(),
                            title = file.nameWithoutExtension,
                            fileName = file.name,
                            sizeBytes = file.length(),
                            durationMs = 0L,
                            mimeType = mime,
                            isVideo = isVideo,
                            contentUri = null,
                            directFile = file
                        )
                    )
                }
            }
        } else {
            try {
                val videoProjection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATA
                )
                val cursor: Cursor? = context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoProjection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "Video_$id"
                        val title = c.getString(titleCol) ?: name
                        val size = c.getLong(sizeCol)
                        val dur = c.getLong(durCol)
                        val mime = c.getString(mimeCol) ?: "video/mp4"
                        val path = c.getString(dataCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        val directFile = path?.let { File(it) }?.takeIf { it.exists() }

                        result.add(
                            HttpMediaFile(
                                id = "vid_$id",
                                title = title,
                                fileName = name,
                                sizeBytes = size,
                                durationMs = dur,
                                mimeType = mime,
                                isVideo = true,
                                contentUri = uri,
                                directFile = directFile
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying MediaStore Video: ${e.message}")
            }

            try {
                val audioProjection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.DATA
                )
                val cursor: Cursor? = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    audioProjection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "Audio_$id"
                        val title = c.getString(titleCol) ?: name
                        val size = c.getLong(sizeCol)
                        val dur = c.getLong(durCol)
                        val mime = c.getString(mimeCol) ?: "audio/mpeg"
                        val path = c.getString(dataCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val directFile = path?.let { File(it) }?.takeIf { it.exists() }

                        result.add(
                            HttpMediaFile(
                                id = "aud_$id",
                                title = title,
                                fileName = name,
                                sizeBytes = size,
                                durationMs = dur,
                                mimeType = mime,
                                isVideo = false,
                                contentUri = uri,
                                directFile = directFile
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying MediaStore Audio: ${e.message}")
            }

            val shareDir = File(context.getExternalFilesDir(null), "http_share")
            if (shareDir.exists()) {
                shareDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                    val ext = f.extension.lowercase()
                    val isVid = ext in listOf("mp4", "mkv", "avi", "mov", "webm")
                    result.add(
                        HttpMediaFile(
                            id = "share_" + f.name.hashCode(),
                            title = f.nameWithoutExtension,
                            fileName = f.name,
                            sizeBytes = f.length(),
                            durationMs = 0L,
                            mimeType = if (isVid) "video/mp4" else "application/octet-stream",
                            isVideo = isVid,
                            contentUri = null,
                            directFile = f
                        )
                    )
                }
            }
        }

        return result
    }

    private fun serveMediaContent(
        output: OutputStream,
        mediaId: String,
        rangeHeader: String?,
        context: Context,
        asDownload: Boolean,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val mediaList = loadMediaList(context)
        val item = mediaList.find { it.id == mediaId || it.fileName == mediaId }

        if (item == null) {
            val msg = "Медиафайл не найден"
            sendResponse(output, 404, "text/plain; charset=UTF-8", msg.toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val totalLength: Long = if (item.directFile != null && item.directFile.exists()) {
            item.directFile.length()
        } else if (item.sizeBytes > 0) {
            item.sizeBytes
        } else {
            -1L
        }

        var startByte = 0L
        var endByte = if (totalLength > 0) totalLength - 1 else Long.MAX_VALUE
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").trim()
            val dashIdx = rangeVal.indexOf('-')
            if (dashIdx != -1) {
                val startStr = rangeVal.substring(0, dashIdx).trim()
                val endStr = rangeVal.substring(dashIdx + 1).trim()
                try {
                    if (startStr.isNotEmpty()) startByte = startStr.toLong()
                    if (endStr.isNotEmpty()) endByte = endStr.toLong()
                    if (totalLength > 0 && endByte >= totalLength) endByte = totalLength - 1
                    isPartial = true
                } catch (_: Exception) {}
            }
        }

        val contentLength = if (totalLength > 0) (endByte - startByte + 1).coerceAtLeast(0) else -1L
        val status = if (isPartial && totalLength > 0) "206 Partial Content" else "200 OK"
        val mime = item.mimeType
        val disposition = if (asDownload) "attachment; filename=\"${item.fileName.replace("\"", "\\\"")}\"" else "inline"

        val headerBuilder = StringBuilder()
            .append("HTTP/1.1 $status\r\n")
            .append("Content-Type: $mime\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Disposition: $disposition\r\n")

        if (needDevCookie != null) {
            headerBuilder.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }

        if (totalLength > 0) {
            headerBuilder.append("Content-Length: $contentLength\r\n")
            if (isPartial) {
                headerBuilder.append("Content-Range: bytes $startByte-$endByte/$totalLength\r\n")
            }
        }
        headerBuilder.append("Connection: keep-alive\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
        output.flush()

        if (isHead) return

        var inputStream: InputStream? = null
        try {
            inputStream = if (item.directFile != null && item.directFile.exists()) {
                FileInputStream(item.directFile)
            } else if (item.contentUri != null) {
                context.contentResolver.openInputStream(item.contentUri)
            } else null

            if (inputStream == null) return

            if (startByte > 0) {
                var skipped = 0L
                while (skipped < startByte) {
                    val s = inputStream.skip(startByte - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }

            val buffer = ByteArray(64 * 1024)
            var bytesToRead = if (totalLength > 0) contentLength else Long.MAX_VALUE
            while (bytesToRead > 0) {
                val toRead = if (bytesToRead > buffer.size) buffer.size else bytesToRead.toInt()
                val read = inputStream.read(buffer, 0, toRead)
                if (read == -1) break
                output.write(buffer, 0, read)
                bytesToRead -= read
            }
            output.flush()
        } catch (_: Exception) {
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun serveThumbnail(
        output: OutputStream,
        mediaId: String,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val safeFileName = mediaId.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".jpg"
        val thumbsDir = File(context.cacheDir, "http_thumbs")
        val cachedFile = File(thumbsDir, safeFileName)

        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                val bytes = cachedFile.readBytes()
                val header = StringBuilder("HTTP/1.1 200 OK\r\n")
                    .append("Content-Type: image/jpeg\r\n")
                    .append("Content-Length: ${bytes.size}\r\n")
                    .append("Cache-Control: max-age=86400\r\n")
                if (needDevCookie != null) {
                    header.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
                }
                header.append("Connection: close\r\n\r\n")
                output.write(header.toString().toByteArray(Charsets.UTF_8))
                if (!isHead) output.write(bytes)
                output.flush()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error reading cached thumbnail: ${e.message}")
            }
        }

        val mediaList = loadMediaList(context)
        val item = mediaList.find { it.id == mediaId || it.fileName == mediaId }

        if (item == null || !item.isVideo) {
            val notFound = "Thumbnail not found".toByteArray(Charsets.UTF_8)
            sendResponse(output, 404, "text/plain; charset=UTF-8", notFound, isHead, needDevCookie = needDevCookie)
            return
        }

        var bitmap: Bitmap? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.contentUri != null) {
            try {
                bitmap = context.contentResolver.loadThumbnail(item.contentUri, Size(480, 270), null)
            } catch (e: Throwable) {
                Log.w(TAG, "contentResolver.loadThumbnail failed for ${item.id}: ${e.message}")
            }
        }

        if (bitmap == null && item.contentUri != null) {
            val numericId = item.id.removePrefix("vid_").toLongOrNull()
            if (numericId != null) {
                try {
                    @Suppress("DEPRECATION")
                    bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        numericId,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "MediaStore.Video.Thumbnails failed: ${e.message}")
                }
            }
        }

        if (bitmap == null) {
            val retriever = MediaMetadataRetriever()
            try {
                if (item.directFile != null && item.directFile.exists()) {
                    retriever.setDataSource(item.directFile.absolutePath)
                } else if (item.contentUri != null) {
                    retriever.setDataSource(context, item.contentUri)
                }
                val durUs = if (item.durationMs > 0) item.durationMs * 1000L else 10_000_000L
                val frameTimeUs = (durUs * 0.1).toLong().coerceAtLeast(1_000_000L)
                bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Throwable) {
                Log.w(TAG, "MediaMetadataRetriever failed for ${item.id}: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (_: Throwable) {}
            }
        }

        if (bitmap == null) {
            val notFound = "Thumbnail generation failed".toByteArray(Charsets.UTF_8)
            sendResponse(output, 404, "text/plain; charset=UTF-8", notFound, isHead, needDevCookie = needDevCookie)
            return
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val bytes = baos.toByteArray()

        try {
            if (!thumbsDir.exists()) thumbsDir.mkdirs()
            cachedFile.writeBytes(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing thumb cache: ${e.message}")
        }

        val header = StringBuilder("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: image/jpeg\r\n")
            .append("Content-Length: ${bytes.size}\r\n")
            .append("Cache-Control: max-age=86400\r\n")
        if (needDevCookie != null) {
            header.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        header.append("Connection: close\r\n\r\n")
        output.write(header.toString().toByteArray(Charsets.UTF_8))
        if (!isHead) output.write(bytes)
        output.flush()
    }

    private fun serveApiPhotos(
        output: OutputStream,
        albumParam: String?,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        if (albumParam.isNullOrBlank()) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

            data class AlbumEntry(
                val id: String,
                val name: String,
                var count: Int,
                val cover: Long,
                val date: Long
            )

            val albumMap = linkedMapOf<String, AlbumEntry>()

            try {
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dateModCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val dateAddCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                    while (c.moveToNext()) {
                        val photoId = c.getLong(idCol)
                        val bucketId = c.getString(bucketIdCol) ?: "0"
                        val bucketName = c.getString(bucketNameCol) ?: "Фото"
                        val dateMod = c.getLong(dateModCol)
                        val dateAdd = c.getLong(dateAddCol)
                        val date = maxOf(dateMod, dateAdd)

                        val existing = albumMap[bucketId]
                        if (existing == null) {
                            albumMap[bucketId] = AlbumEntry(bucketId, bucketName, 1, photoId, date)
                        } else {
                            existing.count++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying MediaStore photos: ${e.message}")
            }

            val sortedAlbums = albumMap.values.sortedByDescending { it.date }
            val json = StringBuilder("{\"albums\":[")
            sortedAlbums.forEachIndexed { i, a ->
                json.append("{")
                json.append("\"id\":${jsonEscape(a.id)},")
                json.append("\"name\":${jsonEscape(a.name)},")
                json.append("\"count\":${a.count},")
                json.append("\"cover\":${a.cover}")
                json.append("}")
                if (i < sortedAlbums.size - 1) json.append(",")
            }
            json.append("]}")
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            sendResponse(output, 200, "application/json; charset=UTF-8", bytes, isHead, needDevCookie = needDevCookie)
        } else {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_ADDED
            )
            val json = StringBuilder("{\"items\":[")
            var first = true

            try {
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Images.Media.BUCKET_ID} = ?",
                    arrayOf(albumParam),
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val dateModCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val dateAddCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "IMG_$id.jpg"
                        val size = c.getLong(sizeCol)
                        val dateMod = c.getLong(dateModCol)
                        val dateAdd = c.getLong(dateAddCol)
                        val dateMs = maxOf(dateMod, dateAdd) * 1000L

                        if (!first) json.append(",")
                        first = false
                        json.append("{")
                        json.append("\"id\":\"$id\",")
                        json.append("\"name\":${jsonEscape(name)},")
                        json.append("\"size\":$size,")
                        json.append("\"date\":$dateMs")
                        json.append("}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying album photos: ${e.message}")
            }

            json.append("]}")
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            sendResponse(output, 200, "application/json; charset=UTF-8", bytes, isHead, needDevCookie = needDevCookie)
        }
    }

    private fun servePhotoOriginal(
        output: OutputStream,
        idParam: String?,
        isDl: Boolean,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val id = idParam?.toLongOrNull()
        if (id == null) {
            sendResponse(output, 404, "text/plain; charset=UTF-8", "Photo not found".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        var fileName = "photo_$id.jpg"
        var mimeType = "image/jpeg"
        var fileSize = -1L

        try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                    val sizeIdx = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                    if (nameIdx >= 0) c.getString(nameIdx)?.let { fileName = it }
                    if (mimeIdx >= 0) c.getString(mimeIdx)?.let { mimeType = it }
                    if (sizeIdx >= 0) fileSize = c.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }

        if (inputStream == null) {
            sendResponse(output, 404, "text/plain; charset=UTF-8", "Photo not found".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val disposition = if (isDl) "attachment; filename=\"${fileName.replace("\"", "\\\"")}\"" else "inline"
        val headerBuilder = StringBuilder()
            .append("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: $mimeType\r\n")
            .append("Content-Disposition: $disposition\r\n")
            .append("Cache-Control: max-age=3600\r\n")
        if (needDevCookie != null) {
            headerBuilder.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        if (fileSize > 0) {
            headerBuilder.append("Content-Length: $fileSize\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
        output.flush()

        if (isHead) {
            inputStream.close()
            return
        }

        try {
            inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                }
                output.flush()
            }
        } catch (_: Exception) {}
    }

    private fun servePhotoThumbnail(
        output: OutputStream,
        idParam: String?,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val id = idParam?.toLongOrNull()
        if (id == null) {
            sendResponse(output, 404, "text/plain; charset=UTF-8", "Photo thumbnail not found".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val safeId = idParam.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val thumbsDir = File(context.cacheDir, "http_thumbs")
        val cachedFile = File(thumbsDir, "p_$safeId.jpg")

        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                val bytes = cachedFile.readBytes()
                val header = StringBuilder("HTTP/1.1 200 OK\r\n")
                    .append("Content-Type: image/jpeg\r\n")
                    .append("Content-Length: ${bytes.size}\r\n")
                    .append("Cache-Control: max-age=86400\r\n")
                if (needDevCookie != null) {
                    header.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
                }
                header.append("Connection: close\r\n\r\n")
                output.write(header.toString().toByteArray(Charsets.UTF_8))
                if (!isHead) output.write(bytes)
                output.flush()
                return
            } catch (_: Exception) {}
        }

        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        var bitmap: Bitmap? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bitmap = context.contentResolver.loadThumbnail(uri, Size(360, 360), null)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed loading image thumbnail via loadThumbnail: ${e.message}")
            }
        }

        if (bitmap == null) {
            try {
                @Suppress("DEPRECATION")
                bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver,
                    id,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Failed loading image thumbnail via MediaStore: ${e.message}")
            }
        }

        if (bitmap == null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    var sampleSize = 1
                    val maxDim = maxOf(options.outWidth, options.outHeight)
                    while (maxDim / (sampleSize * 2) >= 360) {
                        sampleSize *= 2
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream2 ->
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        bitmap = BitmapFactory.decodeStream(stream2, null, decodeOpts)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "BitmapFactory fallback failed: ${e.message}")
            }
        }

        if (bitmap == null) {
            sendResponse(output, 404, "text/plain; charset=UTF-8", "Photo thumbnail not found".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val bytes = baos.toByteArray()

        try {
            if (!thumbsDir.exists()) thumbsDir.mkdirs()
            cachedFile.writeBytes(bytes)
        } catch (_: Exception) {}

        val header = StringBuilder("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: image/jpeg\r\n")
            .append("Content-Length: ${bytes.size}\r\n")
            .append("Cache-Control: max-age=86400\r\n")
        if (needDevCookie != null) {
            header.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        header.append("Connection: close\r\n\r\n")
        output.write(header.toString().toByteArray(Charsets.UTF_8))
        if (!isHead) output.write(bytes)
        output.flush()
    }

    private fun serveApiFs(
        output: OutputStream,
        pathParam: String?,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val canonicalRoot = try {
            Environment.getExternalStorageDirectory().canonicalFile
        } catch (_: Exception) {
            File("/storage/emulated/0").canonicalFile
        }
        val canonicalRootPath = canonicalRoot.canonicalPath

        val targetFile = try {
            if (pathParam.isNullOrBlank()) canonicalRoot else File(pathParam).canonicalFile
        } catch (_: Exception) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }
        val targetPath = targetFile.canonicalPath

        val isUnderRoot = targetPath == canonicalRootPath || targetPath.startsWith(canonicalRootPath + File.separator)
        if (!isUnderRoot) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val androidDir = File(canonicalRoot, "Android").canonicalPath
        val isAndroidDir = targetPath == androidDir || targetPath.startsWith(androidDir + File.separator)
        if (isAndroidDir) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        if (!targetFile.exists() || !targetFile.isDirectory) {
            sendResponse(output, 404, "application/json; charset=UTF-8", "{\"error\":\"not_found\"}".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val fileList = try {
            targetFile.listFiles()
        } catch (_: Exception) {
            null
        }
        if (fileList == null) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"no_access\"}".toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
            return
        }

        val visibleList = fileList.filter { !it.name.startsWith(".") }
        val dirs = visibleList.filter { it.isDirectory }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val files = visibleList.filter { it.isFile }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val allItems = dirs + files

        val parentPath = if (targetPath == canonicalRootPath) null else targetFile.parentFile?.canonicalPath

        val json = StringBuilder()
        json.append("{")
        json.append("\"root\":${jsonEscape(canonicalRootPath)},")
        json.append("\"path\":${jsonEscape(targetPath)},")
        json.append("\"parent\":${if (parentPath == null) "null" else jsonEscape(parentPath)},")
        json.append("\"items\":[")
        allItems.forEachIndexed { idx, it ->
            val isDir = it.isDirectory
            val size = if (isDir) 0L else it.length()
            val mtime = it.lastModified()
            val kind = if (isDir) "other" else getFileKind(it.extension)
            val count = if (isDir) {
                try {
                    it.listFiles()?.count { child -> !child.name.startsWith(".") }
                } catch (_: Exception) {
                    null
                }
            } else null
            json.append("{")
            json.append("\"name\":${jsonEscape(it.name)},")
            json.append("\"path\":${jsonEscape(it.canonicalPath)},")
            json.append("\"dir\":$isDir,")
            json.append("\"size\":$size,")
            json.append("\"mtime\":$mtime,")
            json.append("\"kind\":${jsonEscape(kind)},")
            json.append("\"count\":${count ?: "null"}")
            json.append("}")
            if (idx < allItems.size - 1) json.append(",")
        }
        json.append("]")
        json.append("}")

        sendResponse(output, 200, "application/json; charset=UTF-8", json.toString().toByteArray(Charsets.UTF_8), isHead, needDevCookie = needDevCookie)
    }

    private fun serveFsFile(
        output: OutputStream,
        pathParam: String?,
        rangeHeader: String?,
        isDl: Boolean,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        if (pathParam.isNullOrBlank()) {
            sendResponse(output, 404, "text/plain; charset=UTF-8", "Path missing".toByteArray(Charsets.UTF_8), needDevCookie = needDevCookie)
            return
        }
        val canonicalRoot = try {
            Environment.getExternalStorageDirectory().canonicalFile
        } catch (_: Exception) {
            File("/storage/emulated/0").canonicalFile
        }
        val canonicalRootPath = canonicalRoot.canonicalPath

        val targetFile = try {
            File(pathParam).canonicalFile
        } catch (_: Exception) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), needDevCookie = needDevCookie)
            return
        }
        val targetPath = targetFile.canonicalPath

        val isUnderRoot = targetPath == canonicalRootPath || targetPath.startsWith(canonicalRootPath + File.separator)
        if (!isUnderRoot) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), needDevCookie = needDevCookie)
            return
        }

        val androidDir = File(canonicalRoot, "Android").canonicalPath
        val isAndroidDir = targetPath == androidDir || targetPath.startsWith(androidDir + File.separator)
        if (isAndroidDir) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".toByteArray(Charsets.UTF_8), needDevCookie = needDevCookie)
            return
        }

        if (!targetFile.exists() || !targetFile.isFile) {
            sendResponse(output, 404, "text/plain; charset=UTF-8", "File not found".toByteArray(Charsets.UTF_8), needDevCookie = needDevCookie)
            return
        }

        val totalLength = targetFile.length()
        var startByte = 0L
        var endByte = if (totalLength > 0) totalLength - 1 else Long.MAX_VALUE
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").trim()
            val dashIdx = rangeVal.indexOf('-')
            if (dashIdx != -1) {
                val startStr = rangeVal.substring(0, dashIdx).trim()
                val endStr = rangeVal.substring(dashIdx + 1).trim()
                try {
                    if (startStr.isNotEmpty()) startByte = startStr.toLong()
                    if (endStr.isNotEmpty()) endByte = endStr.toLong()
                    if (totalLength > 0 && endByte >= totalLength) endByte = totalLength - 1
                    isPartial = true
                } catch (_: Exception) {}
            }
        }

        val contentLength = if (totalLength > 0) (endByte - startByte + 1).coerceAtLeast(0) else -1L
        val status = if (isPartial && totalLength > 0) "206 Partial Content" else "200 OK"
        val mime = getMimeTypeByExtension(targetFile.extension)
        val disposition = if (isDl) "attachment; filename=\"${targetFile.name.replace("\"", "\\\"")}\"" else "inline"

        val headerBuilder = StringBuilder()
            .append("HTTP/1.1 $status\r\n")
            .append("Content-Type: $mime\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Disposition: $disposition\r\n")

        if (needDevCookie != null) {
            headerBuilder.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }

        if (totalLength > 0) {
            headerBuilder.append("Content-Length: $contentLength\r\n")
            if (isPartial) {
                headerBuilder.append("Content-Range: bytes $startByte-$endByte/$totalLength\r\n")
            }
        }
        headerBuilder.append("Connection: keep-alive\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
        output.flush()

        if (isHead) return

        var inputStream: InputStream? = null
        try {
            inputStream = FileInputStream(targetFile)
            if (startByte > 0) {
                var skipped = 0L
                while (skipped < startByte) {
                    val s = inputStream.skip(startByte - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }
            val buffer = ByteArray(64 * 1024)
            var bytesToRead = if (totalLength > 0) contentLength else Long.MAX_VALUE
            while (bytesToRead > 0) {
                val toRead = if (bytesToRead > buffer.size) buffer.size else bytesToRead.toInt()
                val read = inputStream.read(buffer, 0, toRead)
                if (read == -1) break
                output.write(buffer, 0, read)
                bytesToRead -= read
            }
            output.flush()
        } catch (_: Exception) {
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun serveApiStatus(
        output: OutputStream,
        reqAuth: RequestAuth,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val signal = getWifiSignal()
        val timeMs = System.currentTimeMillis()
        val wifiJson = if (signal.level != null) {
            "{\"level\":${signal.level},\"rssi\":${signal.rssi}}"
        } else {
            "{\"level\":null,\"rssi\":null}"
        }
        val meJson = "{\"role\":${jsonEscape(reqAuth.role)},\"name\":${jsonEscape(reqAuth.username)}}"
        val stat = try {
            val s = android.os.StatFs(Environment.getExternalStorageDirectory().path)
            Pair(s.blockCountLong * s.blockSizeLong, s.availableBlocksLong * s.blockSizeLong)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
        val diskJson = "{\"total\":${stat.first},\"free\":${stat.second}}"
        val json = "{\"ok\":true,\"online\":true,\"time\":$timeMs,\"wifi\":$wifiJson,\"me\":$meJson,\"disk\":$diskJson}"
        val data = json.toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "application/json; charset=UTF-8", data, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun serveApiDevices(
        output: OutputStream,
        context: Context,
        clientDevId: String,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        loadDevicesIfNeeded(context)
        val now = System.currentTimeMillis()
        val sorted = devices.values.sortedWith(
            compareByDescending<DeviceRecord> { (now - it.lastSeen) <= 15000L }
                .thenByDescending { it.lastSeen }
        )
        val sb = StringBuilder("{\"now\":$now,\"devices\":[")
        sorted.forEachIndexed { i, d ->
            val online = (now - d.lastSeen) <= 15000L
            val isMe = (d.id == clientDevId)
            val name = parseBrowserAndOs(d.ua)
            val kind = parseDeviceKind(d.ua)
            sb.append("{")
            sb.append("\"id\":${jsonEscape(d.id)},")
            sb.append("\"name\":${jsonEscape(name)},")
            sb.append("\"kind\":${jsonEscape(kind)},")
            sb.append("\"ip\":${jsonEscape(d.ip)},")
            sb.append("\"ua\":${jsonEscape(d.ua)},")
            sb.append("\"account\":${jsonEscape(d.account)},")
            sb.append("\"role\":${jsonEscape(d.role)},")
            sb.append("\"online\":$online,")
            sb.append("\"me\":$isMe,")
            sb.append("\"blocked\":${d.blocked},")
            sb.append("\"sessionStart\":${d.sessionStart},")
            sb.append("\"lastLogin\":${d.lastLogin},")
            sb.append("\"firstSeen\":${d.firstSeen},")
            sb.append("\"lastSeen\":${d.lastSeen}")
            sb.append("}")
            if (i < sorted.size - 1) sb.append(",")
        }
        sb.append("]}")
        val data = sb.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "application/json; charset=UTF-8", data, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun serveApiAccounts(
        output: OutputStream,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val list = getAdminAccounts(context)
        val sb = StringBuilder("{\"accounts\":[")
        list.forEachIndexed { i, a ->
            sb.append("{")
            sb.append("\"name\":${jsonEscape(a.name)},")
            sb.append("\"created\":${a.created}")
            sb.append("}")
            if (i < list.size - 1) sb.append(",")
        }
        sb.append("]}")
        val data = sb.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "application/json; charset=UTF-8", data, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun sendHtmlIndex(
        output: OutputStream,
        files: List<HttpMediaFile>,
        context: Context,
        isHead: Boolean,
        reqAuth: RequestAuth,
        needDevCookie: String? = null
    ) {
        val totalVideos = files.count { it.isVideo }
        val totalAudio = files.count { !it.isVideo }
        val totalPhotos = getPhotoCount(context)
        val folderSetting = HttpServerConfig.getBroadcastFolder(context)

        val totalMediaBytes = getAllMediaTotalBytes(context)
        val totalMb = totalMediaBytes / (1024.0 * 1024.0)
        val storageStr = if (totalMb >= 1024.0) {
            String.format(java.util.Locale.US, "%.1f GB", totalMb / 1024.0)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", totalMb)
        }

        val stat = try {
            val s = android.os.StatFs(Environment.getExternalStorageDirectory().path)
            Pair(s.blockCountLong * s.blockSizeLong, s.availableBlocksLong * s.blockSizeLong)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
        val diskJson = "{\"total\":${stat.first},\"free\":${stat.second}}"

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }

        val filesJson = StringBuilder("[")
        files.forEachIndexed { i, f ->
            val sizeMb = String.format(java.util.Locale.US, "%.1f MB", f.sizeBytes / (1024.0 * 1024.0))
            val dur = if (f.durationMs > 0) formatDuration(f.durationMs) else ""
            filesJson.append("{")
            filesJson.append("\"id\":${jsonEscape(f.id)},")
            filesJson.append("\"title\":${jsonEscape(f.title)},")
            filesJson.append("\"name\":${jsonEscape(f.fileName)},")
            filesJson.append("\"size\":${jsonEscape(sizeMb)},")
            filesJson.append("\"dur\":${jsonEscape(dur)},")
            filesJson.append("\"isVideo\":${f.isVideo},")
            filesJson.append("\"mime\":${jsonEscape(f.mimeType)}")
            filesJson.append("}")
            if (i < files.size - 1) filesJson.append(",")
        }
        filesJson.append("]")

        val wifiSignal = getWifiSignal()
        val wifiJson = if (wifiSignal.level != null) {
            "{\"level\":${wifiSignal.level},\"rssi\":${wifiSignal.rssi}}"
        } else {
            "{\"level\":null,\"rssi\":null}"
        }
        val meJson = "{\"role\":${jsonEscape(reqAuth.role)},\"name\":${jsonEscape(reqAuth.username)}}"

        val jsonBuilder = StringBuilder("{")
        jsonBuilder.append("\"version\":${jsonEscape(versionName)},")
        jsonBuilder.append("\"address\":${jsonEscape(getServerUrl(context))},")
        jsonBuilder.append("\"port\":$currentPort,")
        jsonBuilder.append("\"online\":true,")
        jsonBuilder.append("\"authRequired\":${HttpServerConfig.isAuthRequired(context)},")
        jsonBuilder.append("\"folder\":${jsonEscape(folderSetting.title)},")
        jsonBuilder.append("\"features\":{\"photos\":true,\"folders\":true},")
        jsonBuilder.append("\"wifi\":$wifiJson,")
        jsonBuilder.append("\"me\":$meJson,")
        jsonBuilder.append("\"stats\":{")
        jsonBuilder.append("\"videos\":$totalVideos,")
        jsonBuilder.append("\"audio\":$totalAudio,")
        jsonBuilder.append("\"photos\":$totalPhotos,")
        jsonBuilder.append("\"storage\":${jsonEscape(storageStr)},")
        jsonBuilder.append("\"storageBytes\":$totalMediaBytes")
        jsonBuilder.append("},")
        jsonBuilder.append("\"disk\":$diskJson,")
        jsonBuilder.append("\"files\":$filesJson")
        jsonBuilder.append("}")

        val jsonString = jsonBuilder.toString().replace("</", "<\\/")

        val rawHtml = try {
            context.assets.open("web/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading web/index.html from assets", e)
            null
        }

        val html = if (rawHtml != null && rawHtml.contains("<!--NEXORA_DATA-->")) {
            rawHtml.replaceFirst("<!--NEXORA_DATA-->", "<script>window.NEXORA = $jsonString;</script>")
        } else {
            fallbackIndexHtml(files, context)
        }

        val data = html.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: text/html; charset=UTF-8\r\n")
            .append("Content-Length: ${data.size}\r\n")
        if (needDevCookie != null) {
            sb.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        sb.append("Connection: close\r\n\r\n")

        output.write(sb.toString().toByteArray(Charsets.UTF_8))
        if (!isHead) {
            output.write(data)
        }
        output.flush()
    }

    private fun sendHtmlLogin(
        output: OutputStream,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val rawHtml = try {
            context.assets.open("web/login.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading web/login.html from assets", e)
            null
        }

        val html = rawHtml ?: fallbackLoginHtml()
        val data = html.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: text/html; charset=UTF-8\r\n")
            .append("Content-Length: ${data.size}\r\n")
        if (needDevCookie != null) {
            sb.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }
        sb.append("Connection: close\r\n\r\n")

        output.write(sb.toString().toByteArray(Charsets.UTF_8))
        if (!isHead) {
            output.write(data)
        }
        output.flush()
    }

    private fun fallbackLoginHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head><meta charset="utf-8"><title>Nexora Media — Вход</title></head>
            <body style="background:#070403; color:#fff; font-family:sans-serif; text-align:center; padding:50px;">
                <h1 style="color:#ff8a00;">Nexora Media</h1>
                <p>Вход в медиа-сервер</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun fallbackIndexHtml(files: List<HttpMediaFile>, context: Context): String {
        val totalVideos = files.count { it.isVideo }
        val totalAudio = files.count { !it.isVideo }
        val folderSetting = HttpServerConfig.getBroadcastFolder(context)

        val filesJson = StringBuilder("[")
        files.forEachIndexed { i, f ->
            val sizeMb = String.format(java.util.Locale.US, "%.1f MB", f.sizeBytes / (1024.0 * 1024.0))
            val dur = if (f.durationMs > 0) formatDuration(f.durationMs) else ""
            filesJson.append("{\"id\":\"${f.id}\",\"title\":${jsonEscape(f.title)},\"name\":${jsonEscape(f.fileName)},\"size\":\"$sizeMb\",\"dur\":\"$dur\",\"isVideo\":${f.isVideo},\"mime\":\"${f.mimeType}\"}")
            if (i < files.size - 1) filesJson.append(",")
        }
        filesJson.append("]")

        return """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Nexora Web Streamer & Share</title>
</head>
<body style="background:#080604; color:#fff; font-family:sans-serif; padding:20px;">
    <h1>Nexora Media Server</h1>
    <p>Вещание: ${folderSetting.title} • Порт $currentPort</p>
    <p>Видео: $totalVideos | Аудио: $totalAudio</p>
</body>
</html>
        """.trimIndent()
    }

    private fun getPhotoCount(context: Context): Int {
        return try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null
            )
            cursor?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun getAllMediaTotalBytes(context: Context): Long {
        var sum = 0L
        val uris = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        for (uri in uris) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.SIZE),
                    null,
                    null,
                    null
                )
                cursor?.use { c ->
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (sizeCol >= 0) {
                        while (c.moveToNext()) {
                            val s = c.getLong(sizeCol)
                            if (s > 0) sum += s
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying size for $uri", e)
            }
        }
        return sum
    }

    private fun getFileKind(ext: String): String {
        val e = ext.lowercase()
        return when (e) {
            in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts") -> "video"
            in listOf("mp3", "m4a", "flac", "wav", "ogg", "aac", "opus") -> "audio"
            in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") -> "photo"
            else -> "other"
        }
    }

    private fun getMimeTypeByExtension(ext: String): String {
        return when (ext.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "m4v" -> "video/x-m4v"
            "ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "opus" -> "audio/opus"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain; charset=UTF-8"
            else -> "application/octet-stream"
        }
    }

    private fun jsonEscape(str: String): String {
        val out = StringBuilder("\"")
        for (ch in str) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (ch.code < 32) {
                        out.append(String.format("\\u%04x", ch.code))
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        out.append("\"")
        return out.toString()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSecs = durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val hours = mins / 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, mins % 60, secs)
        } else {
            String.format("%d:%02d", mins, secs)
        }
    }

    private fun serveApiNet(
        output: OutputStream,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        val protocol = HttpServerConfig.getProtocol(context)
        val ipMode = HttpServerConfig.getIpMode(context)
        val manualIp = HttpServerConfig.getManualIp(context)
        val curIp = if (ipMode == "manual" && manualIp.isNotBlank()) manualIp else (currentBoundIp ?: localIpAddress(context))
        val portMode = HttpServerConfig.getPortMode(context)
        val strict = HttpServerConfig.isStrict(context)

        val ips = getAvailableDeviceIps()
        val ipsJson = JSONArray()
        for ((lbl, ip) in ips) {
            val o = JSONObject()
            o.put("label", lbl)
            o.put("ip", ip)
            ipsJson.put(o)
        }

        val ports = getCandidatePorts(context)
        val portsJson = JSONArray()
        for (p in ports) portsJson.put(p)

        val certObj = JSONObject()
        certObj.put("fingerprint", CertificateManager.getFingerprint(context))
        certObj.put("expires", CertificateManager.getExpirationDate(context))

        val root = JSONObject()
        root.put("protocol", protocol)
        root.put("ipMode", ipMode)
        root.put("ip", curIp)
        root.put("portMode", portMode)
        root.put("port", currentPort)
        root.put("strict", strict)
        root.put("ips", ipsJson)
        root.put("ports", portsJson)
        root.put("cert", certObj)

        val data = root.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "application/json; charset=UTF-8", data, isHead = isHead, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun handleNetPost(
        output: OutputStream,
        context: Context,
        bodyJson: JSONObject,
        needDevCookie: String? = null
    ) {
        val proto = bodyJson.optString("protocol", "http").lowercase()
        val ipVal = bodyJson.optString("ip", "auto")
        val portVal = bodyJson.opt("port")
        val strictVal = bodyJson.optBoolean("strict", false)

        val portInt: Int? = when (portVal) {
            is Number -> portVal.toInt()
            is String -> if (portVal == "auto") null else portVal.toIntOrNull()
            else -> null
        }

        val availableIps = getAvailableDeviceIps().map { it.second }.toSet()
        if (ipVal != "auto" && !availableIps.contains(ipVal) && ipVal != "127.0.0.1") {
            sendResponse(output, 400, "application/json; charset=UTF-8", "{\"ok\":false,\"code\":\"bad_ip\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        if (portInt != null) {
            if (portInt < 1024 || portInt > 65535) {
                sendResponse(output, 400, "application/json; charset=UTF-8", "{\"ok\":false,\"code\":\"bad_port\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                return
            }
            if (portInt != currentPort && !isPortAvailable(portInt)) {
                sendResponse(output, 400, "application/json; charset=UTF-8", "{\"ok\":false,\"code\":\"port_busy\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
                return
            }
        }

        HttpServerConfig.setProtocol(context, proto)
        HttpServerConfig.setStrict(context, strictVal)
        if (ipVal == "auto") {
            HttpServerConfig.setIpMode(context, "auto")
            HttpServerConfig.setManualIp(context, "")
        } else {
            HttpServerConfig.setIpMode(context, "manual")
            HttpServerConfig.setManualIp(context, ipVal)
        }

        if (portInt == null) {
            HttpServerConfig.setPortMode(context, "auto")
        } else {
            HttpServerConfig.setPortMode(context, "manual")
            HttpServerConfig.setPort(context, portInt)
        }

        val targetIp = if (ipVal != "auto") ipVal else localIpAddress(context)
        val targetPort = portInt ?: currentPort
        val isDefault = (proto == "http" && targetPort == 80) || (proto == "https" && targetPort == 443)
        val newUrl = if (isDefault) "$proto://$targetIp/" else "$proto://$targetIp:$targetPort/"

        val resp = JSONObject()
        resp.put("ok", true)
        resp.put("url", newUrl)
        val data = resp.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "application/json; charset=UTF-8", data, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)

        threadPool.execute {
            try {
                Thread.sleep(700)
                start(context, targetPort)
            } catch (_: Exception) {}
        }
    }

    private fun handleMailSend(
        output: OutputStream,
        context: Context,
        bodyJson: JSONObject,
        clientDevId: String,
        clientIp: String,
        needDevCookie: String? = null
    ) {
        val toPeer = bodyJson.optString("to")
        val kind = bodyJson.optString("kind", "text")
        val text = bodyJson.optString("text", "")
        val fileId = bodyJson.optString("fileId", "").ifBlank { null }

        val peerDev = devices[toPeer]
        if (peerDev?.blocked == true || isDeviceOrIpBlocked(context, clientDevId, clientIp)) {
            sendResponse(output, 403, "application/json; charset=UTF-8", "{\"ok\":false,\"error\":\"blocked\",\"code\":\"blocked\"}".toByteArray(Charsets.UTF_8), extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
            return
        }

        var fileName: String? = null
        var fileSize: Long? = null
        var fileMime: String? = null
        var fileDur: Int? = null

        if (fileId != null) {
            val meta = pendingUploads.remove(fileId)
            if (meta != null) {
                fileName = meta.name
                fileSize = meta.size
                fileMime = meta.mime
                fileDur = meta.dur
            } else {
                val dbMeta = MailManager.getAttachmentMeta(context, fileId)
                if (dbMeta != null) {
                    fileName = dbMeta.first
                    fileSize = dbMeta.second
                    fileMime = dbMeta.third
                } else {
                    val f = MailManager.getAttachmentFile(context, fileId)
                    if (f.exists()) {
                        fileName = if (kind == "voice") "voice.webm" else "attachment"
                        fileSize = f.length()
                        fileMime = if (kind == "voice") "audio/webm" else "application/octet-stream"
                    }
                }
            }
        }

        val msgId = MailManager.sendMessage(
            context = context,
            senderId = clientDevId,
            recipientId = toPeer,
            kind = kind,
            text = text,
            fileId = fileId,
            fileName = fileName,
            fileSize = fileSize,
            fileMime = fileMime,
            fileDur = fileDur
        )

        val resp = JSONObject()
        resp.put("ok", true)
        resp.put("id", msgId)
        val data = resp.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "application/json; charset=UTF-8", data, extraHeaders = listOf("Cache-Control: no-store"), needDevCookie = needDevCookie)
    }

    private fun serveMailFile(
        output: OutputStream,
        fileId: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        rangeHeader: String?,
        isDl: Boolean,
        context: Context,
        isHead: Boolean,
        needDevCookie: String? = null
    ) {
        var rangeStart: Long = 0
        var rangeEnd: Long = fileSize - 1
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
            val parts = rangeSpec.split("-")
            try {
                if (parts[0].isNotEmpty()) {
                    rangeStart = parts[0].toLong()
                }
                if (parts.size > 1 && parts[1].isNotEmpty()) {
                    rangeEnd = parts[1].toLong()
                }
                if (rangeEnd >= fileSize) {
                    rangeEnd = fileSize - 1
                }
                if (rangeStart <= rangeEnd) {
                    isPartial = true
                }
            } catch (_: Exception) {}
        }

        val contentLen = if (isPartial) (rangeEnd - rangeStart + 1) else fileSize
        val code = if (isPartial) 206 else 200
        val statusText = if (isPartial) "Partial Content" else "OK"

        val header = StringBuilder("HTTP/1.1 $code $statusText\r\n")
            .append("Content-Type: $mimeType\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Length: $contentLen\r\n")

        if (isPartial) {
            header.append("Content-Range: bytes $rangeStart-$rangeEnd/$fileSize\r\n")
        }

        if (isDl) {
            val encodedName = URLEncoder.encode(fileName, Charsets.UTF_8.name()).replace("+", "%20")
            header.append("Content-Disposition: attachment; filename=\"$fileName\"; filename*=UTF-8''$encodedName\r\n")
        } else {
            header.append("Content-Disposition: inline\r\n")
        }

        if (needDevCookie != null) {
            header.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly\r\n")
        }

        header.append("Connection: close\r\n\r\n")
        output.write(header.toString().toByteArray(Charsets.UTF_8))
        output.flush()

        if (!isHead) {
            MailManager.streamDecryptedAttachment(context, fileId, output, rangeStart, rangeEnd)
            output.flush()
        }
    }

    private fun sendResponse(
        output: OutputStream,
        code: Int,
        contentType: String,
        data: ByteArray,
        isHead: Boolean = false,
        extraHeaders: List<String> = emptyList(),
        needDevCookie: String? = null
    ) {
        val statusText = when (code) {
            200 -> "OK"
            206 -> "Partial Content"
            302 -> "Found"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        val isHttps = (serverSocket is SSLServerSocket)

        val header = StringBuilder("HTTP/1.1 $code $statusText\r\n")
            .append("Content-Type: $contentType\r\n")
            .append("Content-Length: ${data.size}\r\n")

        if (isHttps) {
            header.append("Strict-Transport-Security: max-age=31536000; includeSubDomains\r\n")
            header.append("X-Content-Type-Options: nosniff\r\n")
        }

        if (needDevCookie != null) {
            val secureFlag = if (isHttps) "; Secure" else ""
            header.append("Set-Cookie: nx_dev=$needDevCookie; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly$secureFlag\r\n")
        }
        for (h in extraHeaders) {
            val finalH = if (isHttps && h.startsWith("Set-Cookie: nx_session=", ignoreCase = true) && !h.contains("Secure", ignoreCase = true)) {
                "$h; Secure"
            } else {
                h
            }
            header.append(finalH).append("\r\n")
        }
        header.append("Connection: close\r\n\r\n")
        output.write(header.toString().toByteArray(Charsets.UTF_8))
        if (!isHead) {
            output.write(data)
        }
        output.flush()
    }

    fun localIpAddress(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (networkInterface in Collections.list(interfaces)) {
                    if (!networkInterface.isUp || networkInterface.isLoopback) continue
                    for (address in Collections.list(networkInterface.inetAddresses)) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val host = address.hostAddress
                            if (!host.isNullOrBlank() && host != "0.0.0.0") return host
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipAddress = wifiManager?.connectionInfo?.ipAddress
            if (ipAddress != null && ipAddress != 0) {
                @Suppress("DEPRECATION")
                val ip = Formatter.formatIpAddress(ipAddress)
                if (!ip.isNullOrBlank() && ip != "0.0.0.0") return ip
            }
        } catch (_: Throwable) {}

        return "127.0.0.1"
    }
}
