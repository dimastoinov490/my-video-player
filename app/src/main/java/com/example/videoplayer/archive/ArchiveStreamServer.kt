package com.example.videoplayer.archive

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ArchiveStreamServer {
    private const val TAG = "ArchiveStreamServer"
    private const val CACHE_DIR_NAME = "archive_stream_cache"

    private val sessions = ConcurrentHashMap<String, StreamSession>()
    private val serverLock = Any()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var listenPort: Int = -1

    @Volatile
    private var acceptThread: Thread? = null

    private val clientExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "ArchiveHttpClient").apply { isDaemon = true }
    }

    data class StreamHandle(
        val sessionId: String,
        val streamUrl: String
    )

    class StreamSession(
        val sessionId: String,
        val spec: ArchivePlaybackSpec,
        val tempFile: File,
        val sessionDir: File
    ) {
        val writtenBytes = AtomicLong(0L)
        val totalSize = AtomicLong(spec.entrySize.coerceAtLeast(0L))
        val isComplete = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val error = AtomicReference<Throwable?>(null)
        val dataLock = Object()

        @Volatile
        var extractorThread: Thread? = null

        fun cancelAndDelete() {
            cancelled.set(true)
            synchronized(dataLock) {
                dataLock.notifyAll()
            }
            try {
                extractorThread?.interrupt()
            } catch (_: Throwable) {}
            try {
                if (sessionDir.exists()) {
                    sessionDir.deleteRecursively()
                } else if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (_: Throwable) {}
        }
    }

    fun shouldPreferDirectZip(spec: ArchivePlaybackSpec): Boolean {
        return spec.source is ArchiveSource.FilePath &&
            !spec.isEncrypted &&
            !spec.isMultiVolume &&
            spec.source.path.endsWith(".zip", ignoreCase = true)
    }

    fun buildDirectZipUri(archivePath: String, entryPath: String): Uri {
        val cleanArchive = archivePath.trimStart('/')
        val cleanEntry = entryPath.trimStart('/')
        val encodedArchive = Uri.encode("/$cleanArchive", "/")
        val encodedEntry = Uri.encode(cleanEntry, "/")
        return Uri.parse("zip://$encodedArchive!/$encodedEntry")
    }

    fun isArchiveStreamUrl(url: String): Boolean {
        val port = listenPort
        if (port <= 0) return false
        return url.startsWith("http://127.0.0.1:$port/stream/") ||
            url.startsWith("http://localhost:$port/stream/")
    }

    fun cleanupStaleCache(context: Context) {
        if (sessions.isNotEmpty()) return
        try {
            val root = File(context.cacheDir, CACHE_DIR_NAME)
            if (root.exists()) {
                root.deleteRecursively()
            }
        } catch (_: Throwable) {}
    }

    fun startSession(context: Context, spec: ArchivePlaybackSpec): StreamHandle {
        ensureServerStarted()
        cleanupStaleCache(context)

        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val rootDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val sessionDir = File(rootDir, sessionId).apply { mkdirs() }
        val safeName = spec.entryName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "video.bin" }
        val tempFile = File(sessionDir, safeName)

        val session = StreamSession(sessionId, spec, tempFile, sessionDir)
        sessions[sessionId] = session

        val extractor = Thread({
            try {
                ArchiveManager.extractSingleEntryToFile(
                    context = context.applicationContext,
                    source = spec.source,
                    entryIndex = spec.entryIndex,
                    outputFile = tempFile,
                    password = spec.password,
                    isCancelled = { session.cancelled.get() },
                    onBytesWritten = { bytes ->
                        session.writtenBytes.set(bytes)
                        synchronized(session.dataLock) {
                            session.dataLock.notifyAll()
                        }
                    }
                )
                val finalLen = tempFile.length()
                if (finalLen > 0L) {
                    session.writtenBytes.set(finalLen)
                    session.totalSize.set(finalLen)
                }
                session.isComplete.set(true)
            } catch (t: Throwable) {
                if (!session.cancelled.get()) {
                    Log.e(TAG, "Stream extraction error for ${spec.entryName}", t)
                    session.error.set(t)
                }
            } finally {
                synchronized(session.dataLock) {
                    session.dataLock.notifyAll()
                }
            }
        }, "ArchiveExtract-$sessionId")
        session.extractorThread = extractor
        extractor.isDaemon = true
        extractor.start()

        // Дожидаемся начального буфера (до 512 КБ или окончания) либо ранней ошибки (например, неверный пароль)
        val targetPrebuffer = if (spec.entrySize > 0L) minOf(spec.entrySize, 256L * 1024L) else 128L * 1024L
        val deadline = System.currentTimeMillis() + 1500L
        synchronized(session.dataLock) {
            while (!session.cancelled.get() &&
                !session.isComplete.get() &&
                session.error.get() == null &&
                session.writtenBytes.get() < targetPrebuffer &&
                System.currentTimeMillis() < deadline
            ) {
                val rem = deadline - System.currentTimeMillis()
                if (rem <= 0L) break
                try {
                    session.dataLock.wait(minOf(rem, 150L))
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

        val err = session.error.get()
        if (err != null) {
            stopSession(sessionId)
            throw err
        }

        val encodedName = Uri.encode(spec.entryName.ifBlank { "video.mp4" })
        val url = "http://127.0.0.1:$listenPort/stream/$sessionId/$encodedName"
        return StreamHandle(sessionId, url)
    }

    fun stopSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        sessions.remove(sessionId)?.cancelAndDelete()
    }

    fun stopAll() {
        val keys = sessions.keys.toList()
        for (k in keys) {
            sessions.remove(k)?.cancelAndDelete()
        }
        synchronized(serverLock) {
            try {
                serverSocket?.close()
            } catch (_: Throwable) {}
            serverSocket = null
            listenPort = -1
            acceptThread = null
        }
    }

    private fun ensureServerStarted() {
        val existing = serverSocket
        if (existing != null && !existing.isClosed && listenPort > 0) return

        synchronized(serverLock) {
            val current = serverSocket
            if (current != null && !current.isClosed && listenPort > 0) return

            val socket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            listenPort = socket.localPort

            val thread = Thread({
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        clientExecutor.execute {
                            handleClient(client)
                        }
                    } catch (_: IOException) {
                        break
                    } catch (t: Throwable) {
                        Log.w(TAG, "Accept error", t)
                    }
                }
            }, "ArchiveHttpServerAccept").apply {
                isDaemon = true
                start()
            }
            acceptThread = thread
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 30_000
                val input = BufferedInputStream(client.getInputStream(), 8192)
                val out = BufferedOutputStream(client.getOutputStream(), 64 * 1024)

                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase(Locale.US)
                val rawPath = parts[1].substringBefore('?')

                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase(Locale.US)] =
                            line.substring(colon + 1).trim()
                    }
                }

                if (rawPath == "/api/monitor/beat") {
                    writeSimpleResponse(out, 204, "No Content")
                    return
                }

                if (!rawPath.startsWith("/stream/")) {
                    writeSimpleResponse(out, 404, "Not Found")
                    return
                }

                val pathSegments = rawPath.removePrefix("/stream/").split('/')
                val sessionId = pathSegments.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
                val session = sessions[sessionId]
                if (session == null || session.cancelled.get()) {
                    writeSimpleResponse(out, 404, "Session Closed")
                    return
                }

                if (session.totalSize.get() <= 0L) {
                    waitForCompletionIfUnknownSize(session)
                }

                val totalLen = session.totalSize.get().takeIf { it > 0L }
                    ?: session.writtenBytes.get().takeIf { session.isComplete.get() && it > 0L }

                if (session.error.get() != null) {
                    writeSimpleResponse(out, 500, "Extraction Failed")
                    return
                }

                val mime = ArchiveFileUtils.guessVideoMimeType(session.spec.entryName)
                val rangeHeader = headers["range"]

                if (totalLen != null && totalLen > 0L) {
                    serveKnownLength(out, session, method, totalLen, mime, rangeHeader)
                } else {
                    serveUnknownLength(out, session, method, mime)
                }
            } catch (_: IOException) {
                // Клиент (libVLC) сам закрыл сокет при перемотке или закрытии плеера — нормальное поведение
            } catch (t: Throwable) {
                Log.w(TAG, "Client handler error", t)
            }
        }
    }

    private fun waitForCompletionIfUnknownSize(session: StreamSession) {
        val deadline = System.currentTimeMillis() + 5000L
        synchronized(session.dataLock) {
            while (!session.cancelled.get() &&
                !session.isComplete.get() &&
                session.error.get() == null &&
                session.totalSize.get() <= 0L &&
                System.currentTimeMillis() < deadline
            ) {
                try {
                    session.dataLock.wait(150L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun serveKnownLength(
        out: OutputStream,
        session: StreamSession,
        method: String,
        totalLen: Long,
        mime: String,
        rangeHeader: String?
    ) {
        var start = 0L
        var end = totalLen - 1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val spec = rangeHeader.substring(6).substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val startStr = spec.substring(0, dash).trim()
                val endStr = spec.substring(dash + 1).trim()
                if (startStr.isEmpty()) {
                    val suffix = endStr.toLongOrNull() ?: 0L
                    start = (totalLen - suffix).coerceAtLeast(0L)
                    end = totalLen - 1L
                } else {
                    start = startStr.toLongOrNull() ?: 0L
                    end = endStr.toLongOrNull()?.coerceAtMost(totalLen - 1L) ?: (totalLen - 1L)
                }
                isPartial = true
            }
        }

        if (start >= totalLen || start > end || start < 0L) {
            val resp = buildString {
                append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                append("Content-Range: bytes */$totalLen\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(resp.toByteArray(Charsets.US_ASCII))
            out.flush()
            return
        }

        val contentLen = (end - start) + 1L
        val header = buildString {
            if (isPartial) {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Range: bytes $start-$end/$totalLen\r\n")
            } else {
                append("HTTP/1.1 200 OK\r\n")
            }
            append("Content-Type: $mime\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: $contentLen\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.flush()

        if (method == "HEAD") return

        streamFileRange(out, session, start, contentLen)
    }

    private fun serveUnknownLength(
        out: OutputStream,
        session: StreamSession,
        method: String,
        mime: String
    ) {
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Accept-Ranges: none\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.flush()

        if (method == "HEAD") return

        streamFileRange(out, session, 0L, Long.MAX_VALUE)
    }

    private fun streamFileRange(
        out: OutputStream,
        session: StreamSession,
        startOffset: Long,
        bytesToSend: Long
    ) {
        // Ждём появления файла на диске
        while (!session.tempFile.exists() && !session.cancelled.get() && session.error.get() == null) {
            if (session.isComplete.get()) break
            synchronized(session.dataLock) {
                try {
                    session.dataLock.wait(100L)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
        if (session.cancelled.get() || session.error.get() != null || !session.tempFile.exists()) return

        RandomAccessFile(session.tempFile, "r").use { raf ->
            var pos = startOffset
            var remaining = bytesToSend
            val buffer = ByteArray(64 * 1024)

            while (remaining > 0L && !session.cancelled.get()) {
                val available = maxOf(session.writtenBytes.get(), raf.length())
                if (pos >= available) {
                    if (session.isComplete.get() || session.error.get() != null) {
                        break
                    }
                    synchronized(session.dataLock) {
                        val checkNow = maxOf(session.writtenBytes.get(), raf.length())
                        if (pos >= checkNow && !session.isComplete.get() && !session.cancelled.get() && session.error.get() == null) {
                            try {
                                session.dataLock.wait(150L)
                            } catch (_: InterruptedException) {
                                return
                            }
                        }
                    }
                    continue
                }

                val toRead = minOf(buffer.size.toLong(), remaining, available - pos).toInt()
                if (toRead <= 0) continue
                raf.seek(pos)
                val read = raf.read(buffer, 0, toRead)
                if (read <= 0) {
                    if (session.isComplete.get()) break
                    continue
                }
                out.write(buffer, 0, read)
                pos += read
                remaining -= read
            }
            out.flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                sb.append(b.toChar())
            }
            if (sb.length > 8192) break
            b = input.read()
        }
        return sb.toString()
    }

    private fun writeSimpleResponse(out: OutputStream, code: Int, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val resp = buildString {
            append("HTTP/1.1 $code $text\r\n")
            append("Content-Length: ${if (code == 204) 0 else body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(resp.toByteArray(Charsets.US_ASCII))
        if (code != 204) out.write(body)
        out.flush()
    }
}
