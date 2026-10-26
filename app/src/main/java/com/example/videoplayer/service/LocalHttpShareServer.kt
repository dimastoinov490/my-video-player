package com.example.videoplayer.service

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import java.io.*
import java.net.*
import java.util.Collections
import java.util.concurrent.Executors

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
            val portsToTry = listOf(chosenPort, 8766, 8767, 8080, 0)
            var socket: ServerSocket? = null
            var boundPort = chosenPort

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

            threadPool.execute {
                acceptClientsLoop(socket, context.applicationContext)
            }

            Log.i(TAG, "HTTP Server running on 0.0.0.0:$boundPort")
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
            "${getServerUrl(context)}/download?id=$name"
        } catch (_: Throwable) {
            "${getServerUrl(context)}/download?id=${file.name}"
        }
    }

    private fun acceptClientsLoop(socket: ServerSocket, context: Context) {
        while (isRunning && !socket.isClosed) {
            try {
                val client = socket.accept()
                threadPool.execute {
                    handleClient(client, context)
                }
            } catch (e: SocketException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Exception in client accept loop", e)
            }
        }
    }

    private fun handleClient(client: Socket, context: Context) {
        try {
            client.soTimeout = 30000
            val rawInput = client.getInputStream()
            val input = BufferedReader(InputStreamReader(rawInput, Charsets.UTF_8))
            val output = BufferedOutputStream(client.getOutputStream())

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val pathWithQuery = parts[1]
            val rawPath = pathWithQuery.substringBefore("?")
            val queryString = if (pathWithQuery.contains("?")) pathWithQuery.substringAfter("?") else ""
            val decodedPath = URLDecoder.decode(rawPath, Charsets.UTF_8.name())

            // Read headers
            var authHeader: String? = null
            var rangeHeader: String? = null
            var line: String? = input.readLine()
            while (!line.isNullOrBlank()) {
                val lower = line.lowercase()
                if (lower.startsWith("authorization:")) {
                    authHeader = line.substringAfter(":").trim()
                } else if (lower.startsWith("range:")) {
                    rangeHeader = line.substringAfter(":").trim()
                }
                line = input.readLine()
            }

            // Check Basic Authentication if enabled
            if (HttpServerConfig.isAuthRequired(context)) {
                if (!checkBasicAuth(authHeader, context)) {
                    send401Unauthorized(output)
                    return
                }
            }

            if (method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)) {
                val isHead = method.equals("HEAD", ignoreCase = true)
                when {
                    decodedPath == "/" || decodedPath == "/index.html" -> {
                        val mediaList = loadMediaList(context)
                        sendHtmlIndex(output, mediaList, context, isHead)
                    }
                    decodedPath == "/stream" || decodedPath.startsWith("/stream/") -> {
                        val idParam = extractQueryParam(queryString, "id")
                            ?: decodedPath.removePrefix("/stream/").removePrefix("/")
                        serveMediaContent(output, idParam, rangeHeader, context, asDownload = false, isHead = isHead)
                    }
                    decodedPath == "/download" || decodedPath.startsWith("/download/") -> {
                        val idParam = extractQueryParam(queryString, "id")
                            ?: decodedPath.removePrefix("/download/").removePrefix("/")
                        serveMediaContent(output, idParam, rangeHeader, context, asDownload = true, isHead = isHead)
                    }
                    decodedPath == "/api/status" -> {
                        val json = "{\"status\":\"running\",\"port\":$currentPort,\"auth\":${HttpServerConfig.isAuthRequired(context)}}"
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

    private fun checkBasicAuth(authHeader: String?, context: Context): Boolean {
        if (authHeader == null || !authHeader.startsWith("Basic ", ignoreCase = true)) {
            return false
        }
        val encoded = authHeader.substringAfter("Basic ").trim()
        val decoded = try {
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            return false
        }
        val expectedUser = HttpServerConfig.getUsername(context)
        val expectedPass = HttpServerConfig.getPassword(context)

        val colonIndex = decoded.indexOf(':')
        if (colonIndex == -1) return false
        val user = decoded.substring(0, colonIndex)
        val pass = decoded.substring(colonIndex + 1)
        return user == expectedUser && pass == expectedPass
    }

    private fun send401Unauthorized(output: OutputStream) {
        val body = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>401 Требуется авторизация</title></head>
            <body style="background:#121212; color:#fff; font-family:sans-serif; text-align:center; padding:50px;">
                <h2 style="color:#ff8a00;">🔒 Требуется авторизация</h2>
                <p>Введите логин и пароль, настроенные в приложении Nexora Video Player.</p>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val header = "HTTP/1.1 401 Unauthorized\r\n" +
                "WWW-Authenticate: Basic realm=\"Nexora Media Server\"\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun extractQueryParam(query: String, paramName: String): String? {
        if (query.isBlank()) return null
        return query.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2 && kv[0] == paramName) URLDecoder.decode(kv[1], Charsets.UTF_8.name()) else null
        }.firstOrNull()
    }

    fun loadMediaList(context: Context): List<HttpMediaFile> {
        val result = mutableListOf<HttpMediaFile>()
        val folderSetting = HttpServerConfig.getBroadcastFolder(context)
        val specificDir = HttpServerConfig.getFolderDirectory(context, folderSetting)

        // If specific folder is chosen, scan that folder directly
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
            // ALL Media: Query MediaStore Video
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

            // Query MediaStore Audio
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

            // Also include any files in the dedicated app share directory
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
        isHead: Boolean
    ) {
        val mediaList = loadMediaList(context)
        val item = mediaList.find { it.id == mediaId || it.fileName == mediaId }

        if (item == null) {
            val msg = "Медиафайл не найден"
            sendResponse(output, 404, "text/plain; charset=UTF-8", msg.toByteArray(Charsets.UTF_8))
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
        val disposition = if (asDownload) "attachment; filename=\"${item.fileName}\"" else "inline"

        val headerBuilder = StringBuilder()
            .append("HTTP/1.1 $status\r\n")
            .append("Content-Type: $mime\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Disposition: $disposition\r\n")

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

        // Stream content
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
        } catch (e: Exception) {
            // Client socket disconnect or video seek abort - standard behavior
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun sendHtmlIndex(output: OutputStream, files: List<HttpMediaFile>, context: Context, isHead: Boolean) {
        val totalVideos = files.count { it.isVideo }
        val totalAudio = files.count { !it.isVideo }
        val folderSetting = HttpServerConfig.getBroadcastFolder(context)

        val filesJson = StringBuilder("[")
        files.forEachIndexed { i, f ->
            val sizeMb = "%.1f MB".format(f.sizeBytes / (1024.0 * 1024.0))
            val dur = if (f.durationMs > 0) formatDuration(f.durationMs) else ""
            filesJson.append("{\"id\":\"${f.id}\",\"title\":${jsonEscape(f.title)},\"name\":${jsonEscape(f.fileName)},\"size\":\"$sizeMb\",\"dur\":\"$dur\",\"isVideo\":${f.isVideo},\"mime\":\"${f.mimeType}\"}")
            if (i < files.size - 1) filesJson.append(",")
        }
        filesJson.append("]")

        val html = """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Nexora Web Streamer & Share</title>
    <style>
        :root { --neon: #ff8a00; --bg: #0d0d0d; --card: #191c24; --text: #f0f0f0; --accent: #ff9500; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); padding: 16px; margin: 0; line-height: 1.5; }
        .container { max-width: 900px; margin: 0 auto; }
        .header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid rgba(255,138,0,0.25); padding-bottom: 14px; margin-bottom: 16px; }
        .logo { font-size: 22px; font-weight: 800; color: var(--neon); letter-spacing: 0.5px; }
        .badge { background: rgba(255,138,0,0.15); border: 1px solid var(--neon); color: var(--neon); padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; }
        
        /* Player Card */
        .player-container { background: #000; border-radius: 18px; overflow: hidden; border: 1px solid rgba(255,138,0,0.4); box-shadow: 0 10px 30px rgba(0,0,0,0.8); margin-bottom: 20px; }
        video, audio { width: 100%; display: block; max-height: 480px; outline: none; background: #000; }
        .player-bar { padding: 12px 18px; background: var(--card); display: flex; align-items: center; justify-content: space-between; border-top: 1px solid rgba(255,255,255,0.06); }
        .now-playing-title { font-weight: 700; color: #fff; font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 70%; }
        
        /* Controls & Filter */
        .search-box { width: 100%; box-sizing: border-box; background: var(--card); border: 1px solid rgba(255,255,255,0.15); padding: 12px 16px; border-radius: 12px; color: #fff; font-size: 14px; outline: none; margin-bottom: 12px; transition: border 0.2s; }
        .search-box:focus { border-color: var(--neon); }
        .tabs { display: flex; gap: 8px; margin-bottom: 16px; }
        .tab-btn { background: var(--card); color: #aaa; border: 1px solid rgba(255,255,255,0.08); padding: 8px 14px; border-radius: 10px; cursor: pointer; font-size: 13px; font-weight: 600; }
        .tab-btn.active { background: var(--neon); color: #000; border-color: var(--neon); }

        /* Media List */
        .file-list { display: flex; flex-direction: column; gap: 10px; }
        .file-item { background: var(--card); border-radius: 14px; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; border: 1px solid rgba(255,255,255,0.05); transition: background 0.2s, transform 0.1s; }
        .file-item:hover { background: #222632; border-color: rgba(255,138,0,0.3); }
        .file-info { display: flex; align-items: center; gap: 12px; overflow: hidden; }
        .file-icon { font-size: 24px; min-width: 32px; }
        .file-meta { overflow: hidden; }
        .file-name { font-weight: 600; font-size: 14px; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .file-sub { font-size: 11px; color: #888; display: flex; gap: 10px; margin-top: 2px; }
        .actions { display: flex; gap: 8px; flex-shrink: 0; }
        .btn-stream { background: var(--neon); color: #000; border: none; padding: 7px 14px; border-radius: 8px; font-weight: 700; font-size: 12px; cursor: pointer; }
        .btn-download { background: rgba(255,255,255,0.08); color: #fff; border: 1px solid rgba(255,255,255,0.15); padding: 7px 12px; border-radius: 8px; font-weight: 600; font-size: 12px; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-download:hover { background: rgba(255,255,255,0.18); }
        .empty { text-align: center; color: #777; padding: 40px 10px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <div class="logo">⚡ Nexora Media Server</div>
                <div style="font-size:11px; color:#aaa;">Вещание: ${folderSetting.title} • Порт $currentPort</div>
            </div>
            <div class="badge">Видео: $totalVideos | Аудио: $totalAudio</div>
        </div>

        <!-- Embedded Web Player -->
        <div class="player-container" id="playerWrap">
            <video id="webVideoPlayer" controls playsinline preload="metadata" style="display:none;"></video>
            <audio id="webAudioPlayer" controls style="display:none; padding:20px;"></audio>
            <div class="player-bar" id="playerBar" style="display:none;">
                <div class="now-playing-title" id="nowPlayingText">Готов к воспроизведению</div>
                <div style="font-size:11px; color:var(--neon); font-weight:bold;">▶ ПРЯМОЙ ПОТОК (RANGE 206)</div>
            </div>
        </div>

        <input type="text" id="searchInput" class="search-box" placeholder="🔍 Быстрый поиск видео или музыки..." oninput="filterMedia()">

        <div class="tabs">
            <button class="tab-btn active" id="tabAll" onclick="setTab('all')">Все ($filesJson.count { it == '{' })</button>
            <button class="tab-btn" id="tabVideos" onclick="setTab('video')">🎬 Видео ($totalVideos)</button>
            <button class="tab-btn" id="tabAudio" onclick="setTab('audio')">🎵 Аудио ($totalAudio)</button>
        </div>

        <div id="mediaList" class="file-list"></div>
    </div>

    <script>
        const mediaData = $filesJson;
        let currentTab = 'all';

        function setTab(tab) {
            currentTab = tab;
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            if (tab === 'all') document.getElementById('tabAll').classList.add('active');
            if (tab === 'video') document.getElementById('tabVideos').classList.add('active');
            if (tab === 'audio') document.getElementById('tabAudio').classList.add('active');
            filterMedia();
        }

        function filterMedia() {
            const query = document.getElementById('searchInput').value.toLowerCase().trim();
            const list = document.getElementById('mediaList');
            list.innerHTML = '';

            const filtered = mediaData.filter(item => {
                const matchesTab = currentTab === 'all' || (currentTab === 'video' && item.isVideo) || (currentTab === 'audio' && !item.isVideo);
                const matchesQuery = !query || item.title.toLowerCase().includes(query) || item.name.toLowerCase().includes(query);
                return matchesTab && matchesQuery;
            });

            if (filtered.length === 0) {
                list.innerHTML = '<div class="empty">Файлы не найдены. Убедитесь, что на устройстве есть видео или музыка.</div>';
                return;
            }

            filtered.forEach(item => {
                const el = document.createElement('div');
                el.className = 'file-item';
                const icon = item.isVideo ? '🎬' : '🎵';
                const playLabel = item.isVideo ? '▶ Смотреть' : '▶ Слушать';
                const durBadge = item.dur ? `<span>⏱ ${'$'}{item.dur}</span>` : '';

                el.innerHTML = `
                    <div class="file-info">
                        <div class="file-icon">${'$'}{icon}</div>
                        <div class="file-meta">
                            <div class="file-name" title="${'$'}{item.name}">${'$'}{item.title}</div>
                            <div class="file-sub">
                                <span>💾 ${'$'}{item.size}</span>
                                ${'$'}{durBadge}
                            </div>
                        </div>
                    </div>
                    <div class="actions">
                        <button class="btn-stream" onclick="playMedia('${'$'}{item.id}', '${'$'}{item.name.replace(/'/g, "\\'")}', ${'$'}{item.isVideo})">${'$'}{playLabel}</button>
                        <a class="btn-download" href="/download?id=${'$'}{item.id}" download="${'$'}{item.name}">📥</a>
                    </div>
                `;
                list.appendChild(el);
            });
        }

        function playMedia(id, name, isVideo) {
            const videoPlayer = document.getElementById('webVideoPlayer');
            const audioPlayer = document.getElementById('webAudioPlayer');
            const playerBar = document.getElementById('playerBar');
            const nowPlaying = document.getElementById('nowPlayingText');

            const streamUrl = '/stream?id=' + encodeURIComponent(id);
            nowPlaying.innerText = (isVideo ? '🎬 ' : '🎵 ') + name;
            playerBar.style.display = 'flex';

            if (isVideo) {
                audioPlayer.pause();
                audioPlayer.style.display = 'none';
                videoPlayer.style.display = 'block';
                videoPlayer.src = streamUrl;
                videoPlayer.load();
                videoPlayer.play().catch(e => console.log('Autoplay blocked', e));
            } else {
                videoPlayer.pause();
                videoPlayer.style.display = 'none';
                audioPlayer.style.display = 'block';
                audioPlayer.src = streamUrl;
                audioPlayer.load();
                audioPlayer.play().catch(e => console.log('Autoplay blocked', e));
            }

            document.getElementById('playerWrap').scrollIntoView({ behavior: 'smooth' });
        }

        filterMedia();
    </script>
</body>
</html>
        """.trimIndent()

        val data = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.UTF_8))
        if (!isHead) {
            output.write(data)
        }
        output.flush()
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
