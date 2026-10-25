package com.example.videoplayer.service

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLEncoder
import java.util.Collections

object LocalHttpShareServer {
    private const val DEFAULT_PORT = 8765
    private var httpServer: HttpServer? = null
    private var currentPort: Int = DEFAULT_PORT
    private var rootDir: File? = null

    fun start(context: Context, chosenPort: Int = DEFAULT_PORT): Boolean {
        if (httpServer != null) return true
        val shareDir = File(context.getExternalFilesDir(null), "http_share").apply { mkdirs() }
        rootDir = shareDir
        currentPort = chosenPort
        return try {
            val server = HttpServer.create(InetSocketAddress(localIpAddress(context), chosenPort), 0)

            server.createContext("/") { exchange ->
                val body = """
                    <html><body>
                    <h2>Nexora Local Share</h2>
                    <p>Use /download/{filename} for direct file transfer.</p>
                    </body></html>
                """.trimIndent()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
                exchange.sendResponseHeaders(200, body.toByteArray(Charsets.UTF_8).size.toLong())
                exchange.responseBody.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }
            }

            server.createContext("/download") { exchange ->
                val fileName = exchange.requestURI.path.removePrefix("/download/")
                val requestedFile = File(rootDir, fileName)
                if (requestedFile.exists() && requestedFile.isFile) {
                    val bytes = requestedFile.readBytes()
                    exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                    exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"${requestedFile.name}\"")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { output -> output.write(bytes) }
                } else {
                    exchange.sendResponseHeaders(404, -1)
                }
            }

            server.start()
            httpServer = server
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        httpServer?.stop(0)
        httpServer = null
    }

    fun isRunning(context: Context): Boolean = httpServer != null

    fun getServerUrl(context: Context): String = "http://${localIpAddress(context)}:$currentPort"

    fun getDownloadUrl(context: Context, file: File): String {
        val name = URLEncoder.encode(file.name, Charsets.UTF_8.name())
        return "${getServerUrl(context)}/download/$name"
    }

    private fun localIpAddress(context: Context): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress
        if (ipAddress != null) {
            @Suppress("DEPRECATION")
            val ip = Formatter.formatIpAddress(ipAddress)
            if (!ip.isNullOrBlank() && ip != "0.0.0.0") return ip
        }

        for (networkInterface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (address in Collections.list(networkInterface.inetAddresses)) {
                if (!address.isLoopbackAddress && address.hostAddress.contains(".")) {
                    val host = address.hostAddress
                    if (host != "0.0.0.0") return host
                }
            }
        }

        return "127.0.0.1"
    }
}
