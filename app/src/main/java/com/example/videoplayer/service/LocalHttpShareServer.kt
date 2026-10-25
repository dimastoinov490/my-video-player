package com.example.videoplayer.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import java.io.*
import java.net.*
import java.util.Collections
import java.util.concurrent.Executors

object LocalHttpShareServer {
    private const val TAG = "LocalHttpShareServer"
    const val DEFAULT_PORT = 8765

    private var serverSocket: ServerSocket? = null
    var isRunning: Boolean = false
        private set
    var currentPort: Int = DEFAULT_PORT
        private set
    var lastErrorMessage: String? = null
        private set

    private val threadPool = Executors.newCachedThreadPool()

    @Synchronized
    fun start(context: Context, chosenPort: Int = DEFAULT_PORT): Boolean {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
            return true
        }
        stop()

        return try {
            val shareDir = File(context.getExternalFilesDir(null), "http_share").apply { mkdirs() }

            // Port retry logic: chosenPort -> 8766 -> 8767 -> 8080 -> 0 (any free port)
            var socket: ServerSocket? = null
            var boundPort = chosenPort
            val portsToTry = listOf(chosenPort, 8766, 8767, 8080, 0)

            for (p in portsToTry) {
                try {
                    val s = if (p == 0) ServerSocket(0) else ServerSocket(p, 50, InetAddress.getByName("0.0.0.0"))
                    socket = s
                    boundPort = s.localPort
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Port $p unavailable, trying next: ${e.message}")
                }
            }

            if (socket == null) {
                throw IOException("Не удалось занять свободный сетевой порт")
            }

            serverSocket = socket
            currentPort = boundPort
            isRunning = true
            lastErrorMessage = null

            // Start accept loop in background
            threadPool.execute {
                acceptClientsLoop(socket, shareDir, context)
            }

            Log.i(TAG, "HTTP Server started on 0.0.0.0:$boundPort")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start HTTP server", e)
            lastErrorMessage = e.localizedMessage ?: e.javaClass.simpleName
            isRunning = false
            serverSocket = null
            false
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        } finally {
            serverSocket = null
        }
    }

    fun isRunning(context: Context): Boolean = isRunning && serverSocket != null && !serverSocket!!.isClosed

    fun getServerUrl(context: Context): String = "http://${localIpAddress(context)}:$currentPort"

    fun getDownloadUrl(context: Context, file: File): String {
        return try {
            val name = URLEncoder.encode(file.name, Charsets.UTF_8.name())
            "${getServerUrl(context)}/download/$name"
        } catch (_: Throwable) {
            "${getServerUrl(context)}/download/${file.name}"
        }
    }

    private fun acceptClientsLoop(socket: ServerSocket, shareDir: File, context: Context) {
        while (isRunning && !socket.isClosed) {
            try {
                val client = socket.accept()
                threadPool.execute {
                    handleClient(client, shareDir, context)
                }
            } catch (e: SocketException) {
                // Expected when socket is closed
                break
            } catch (e: Exception) {
                Log.e(TAG, "Exception in client accept loop", e)
            }
        }
    }

    private fun handleClient(client: Socket, shareDir: File, context: Context) {
        try {
            client.soTimeout = 15000
            val input = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val output = BufferedOutputStream(client.getOutputStream())

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val pathWithQuery = parts[1]
            val rawPath = pathWithQuery.substringBefore("?")
            val decodedPath = URLDecoder.decode(rawPath, Charsets.UTF_8.name())

            if (method.equals("GET", ignoreCase = true)) {
                when {
                    decodedPath == "/" || decodedPath == "/index.html" -> {
                        sendHtmlIndex(output, shareDir)
                    }
                    decodedPath.startsWith("/download/") -> {
                        val fileName = decodedPath.removePrefix("/download/").removePrefix("/")
                        serveFile(output, fileName, shareDir, context)
                    }
                    decodedPath == "/api/status" -> {
                        val json = "{\"status\":\"running\",\"port\":$currentPort}"
                        sendResponse(output, 200, "application/json", json.toByteArray(Charsets.UTF_8))
                    }
                    else -> {
                        val notFound = "404 Not Found"
                        sendResponse(output, 404, "text/plain", notFound.toByteArray(Charsets.UTF_8))
                    }
                }
            } else {
                val methodNotAllowed = "405 Method Not Allowed"
                sendResponse(output, 405, "text/plain", methodNotAllowed.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client connection: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun serveFile(output: OutputStream, fileName: String, shareDir: File, context: Context) {
        // Search file in shareDir, Movies, Downloads or public dirs
        val candidateFiles = listOfNotNull(
            File(shareDir, fileName),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), fileName),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fileName),
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { File(it, fileName) }
        )

        val targetFile = candidateFiles.find { it.exists() && it.isFile }

        if (targetFile != null) {
            val length = targetFile.length()
            val mimeType = when (targetFile.extension.lowercase()) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }

            val header = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: $mimeType\r\n")
                .append("Content-Length: $length\r\n")
                .append("Content-Disposition: attachment; filename=\"${targetFile.name}\"\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Connection: close\r\n\r\n")

            output.write(header.toString().toByteArray(Charsets.UTF_8))

            // Stream file contents in 64KB buffers
            FileInputStream(targetFile).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
            output.flush()
        } else {
            val msg = "Файл '$fileName' не найден на сервере"
            sendResponse(output, 404, "text/plain; charset=UTF-8", msg.toByteArray(Charsets.UTF_8))
        }
    }

    private fun sendHtmlIndex(output: OutputStream, shareDir: File) {
        val files = shareDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val fileItems = if (files.isEmpty()) {
            "<p style='color:#888;'>В папке обмена пока нет файлов. Выберите видео в приложении для быстрой передачи.</p>"
        } else {
            files.joinToString("") { f ->
                val mb = "%.1f MB".format(f.length() / (1024.0 * 1024.0))
                "<li style='margin-bottom:10px;'><a style='color:#ff8a00; font-size:16px; text-decoration:none;' href='/download/${f.name}'>📥 <b>${f.name}</b></a> <span style='color:#aaa;'>($mb)</span></li>"
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Nexora Local Share</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #121212; color: #fff; padding: 24px; margin: 0; }
                    .card { background: #1e1e1e; border-radius: 16px; padding: 20px; max-width: 600px; margin: 20px auto; border: 1px solid #33ff8a00; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
                    h1 { color: #ff8a00; font-size: 22px; margin-top: 0; }
                    ul { padding-left: 20px; }
                    .badge { background: #ff8a00; color: #000; padding: 4px 10px; border-radius: 12px; font-weight: bold; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>⚡ Nexora Local Share</h1>
                    <p>Локальный сервер обмена видеофайлами активен в сети Wi-Fi.</p>
                    <p><span class="badge">Сервер работает на порту $currentPort</span></p>
                    <hr style="border:0; border-top:1px solid #333; margin:16px 0;">
                    <h3>Доступные для скачивания файлы:</h3>
                    <ul>$fileItems</ul>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendResponse(output, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
    }

    private fun sendResponse(output: OutputStream, code: Int, contentType: String, data: ByteArray) {
        val statusText = if (code == 200) "OK" else if (code == 404) "Not Found" else "Error"
        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(data)
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
