package com.example.videoplayer.player

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

object LinuxInstaller {

    fun getRootFsDir(context: Context): File {
        return File(context.filesDir, "rootfs")
    }

    fun isInstalled(context: Context): Boolean {
        val binSh = File(getRootFsDir(context), "bin/sh")
        return binSh.exists()
    }

    fun isBootstrapDone(context: Context): Boolean {
        return File(getRootFsDir(context), "root/.bootstrap_done").exists()
    }

    fun ensureBootstrapScript(context: Context): File {
        val rootDir = File(getRootFsDir(context), "root")
        if (!rootDir.exists()) rootDir.mkdirs()
        val scriptFile = File(rootDir, "bootstrap.sh")
        scriptFile.writeText(BOOTSTRAP_SCRIPT)
        scriptFile.setExecutable(true, false)
        return scriptFile
    }

    suspend fun installAlpine(context: Context, onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val rootFs = getRootFsDir(context)
        if (!rootFs.exists()) rootFs.mkdirs()

        val arch = resolveAlpineArch()
        onProgress("Загрузка образа Alpine Linux ($arch)...")
        val alpineUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/$arch/alpine-minirootfs-3.20.0-$arch.tar.gz"
        val tarGzFile = File(context.cacheDir, "alpine.tar.gz")

        try {
            val connection = (URL(alpineUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code при загрузке образа Alpine ($arch)")
            }
            connection.inputStream.use { input ->
                FileOutputStream(tarGzFile).use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()

            onProgress("Распаковка корневой файловой системы...")
            GZIPInputStream(BufferedInputStream(tarGzFile.inputStream())).use { gzipInput ->
                unTar(gzipInput, rootFs)
            }

            // Создаем необходимые системные папки внутри rootfs
            File(rootFs, "dev").mkdirs()
            File(rootFs, "proc").mkdirs()
            File(rootFs, "sys").mkdirs()
            File(rootFs, "sdcard").mkdirs()
            File(rootFs, "app_data").mkdirs()
            File(rootFs, "root").mkdirs()

            // Прописываем DNS для работы интернета через PRoot
            val etcDir = File(rootFs, "etc")
            if (!etcDir.exists()) etcDir.mkdirs()
            File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

            // Сохраняем скрипт первичной настройки Python3 и yt-dlp
            ensureBootstrapScript(context)

            onProgress("Linux-окружение успешно установлено!")
        } finally {
            tarGzFile.delete()
        }
    }

    private fun resolveAlpineArch(): String {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            primaryAbi.contains("arm64") -> "aarch64"
            primaryAbi.contains("armeabi") -> "armv7"
            primaryAbi.contains("x86_64") -> "x86_64"
            primaryAbi.contains("x86") -> "x86"
            else -> "aarch64"
        }
    }

    // Полноценный распаковщик POSIX/USTAR .tar с поддержкой симлинков, прав доступа и PAX/GNU длинных имён
    private fun unTar(inputStream: InputStream, outputDir: File) {
        val buffered = BufferedInputStream(inputStream)
        val rootCanonical = outputDir.canonicalFile
        val header = ByteArray(512)
        val copyBuffer = ByteArray(16 * 1024)
        var nextLongName: String? = null
        var nextLongLink: String? = null
        var paxPath: String? = null
        var paxLinkPath: String? = null
        var paxSize: Long? = null

        val deferredLinks = mutableListOf<Triple<File, String, Char>>()

        while (true) {
            val readCount = readFully(buffered, header)
            if (readCount == 0 || isAllZero(header)) break
            if (readCount < 512) throw IOException("Повреждённый заголовок TAR-архива")

            val typeFlagByte = header[156].toInt() and 0xFF
            val typeFlag = if (typeFlagByte == 0) '0' else typeFlagByte.toChar()

            val rawName = extractString(header, 0, 100)
            val prefix = extractString(header, 345, 155)
            val defaultName = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            val mode = parseOctal(header, 100, 8)
            val entrySize = paxSize ?: parseOctal(header, 124, 12)
            val defaultLink = extractString(header, 157, 100)

            // Служебные мета-заголовки GNU/PAX
            if (typeFlag == 'L' || typeFlag == 'K') {
                val data = readDataBlock(buffered, entrySize)
                val str = String(data, StandardCharsets.UTF_8).trimEnd('\u0000', '\n', '\r')
                if (typeFlag == 'L') nextLongName = str else nextLongLink = str
                continue
            }
            if (typeFlag == 'x' || typeFlag == 'g') {
                val data = readDataBlock(buffered, entrySize)
                if (typeFlag == 'x') {
                    val attrs = parsePaxAttributes(data)
                    paxPath = attrs["path"]
                    paxLinkPath = attrs["linkpath"]
                    paxSize = attrs["size"]?.toLongOrNull()
                }
                continue
            }

            val entryName = (paxPath ?: nextLongName ?: defaultName).removePrefix("./").trimStart('/')
            val linkTarget = paxLinkPath ?: nextLongLink ?: defaultLink
            nextLongName = null
            nextLongLink = null
            paxPath = null
            paxLinkPath = null
            paxSize = null

            if (entryName.isBlank()) {
                skipDataBlock(buffered, entrySize)
                continue
            }

            val destFile = File(rootCanonical, entryName)
            val destCanonical = destFile.canonicalFile
            val rootPathWithSep = rootCanonical.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
            if (destCanonical.path != rootCanonical.path && !destCanonical.path.startsWith(rootPathWithSep)) {
                skipDataBlock(buffered, entrySize)
                continue
            }

            when (typeFlag) {
                '5' -> {
                    if (!destCanonical.exists()) destCanonical.mkdirs()
                    skipDataBlock(buffered, entrySize)
                }
                '2', '1' -> {
                    destCanonical.parentFile?.mkdirs()
                    skipDataBlock(buffered, entrySize)
                    deferredLinks.add(Triple(destCanonical, linkTarget, typeFlag))
                }
                '0' -> {
                    destCanonical.parentFile?.mkdirs()
                    if (destCanonical.exists()) destCanonical.delete()
                    FileOutputStream(destCanonical).use { out ->
                        var remaining = entrySize
                        while (remaining > 0L) {
                            val toRead = minOf(remaining, copyBuffer.size.toLong()).toInt()
                            val n = buffered.read(copyBuffer, 0, toRead)
                            if (n <= 0) throw IOException("Неожиданный конец TAR-файла для $entryName")
                            out.write(copyBuffer, 0, n)
                            remaining -= n
                        }
                    }
                    skipPadding(buffered, entrySize)
                    if ((mode and 73L) != 0L) { // 0111 octal = executable bits
                        destCanonical.setExecutable(true, false)
                    }
                }
                else -> {
                    skipDataBlock(buffered, entrySize)
                }
            }
        }

        // Создаем симлинки и жёсткие ссылки после распаковки файлов
        for ((destFile, target, kind) in deferredLinks) {
            try {
                if (destFile.exists()) destFile.delete()
                if (kind == '2') {
                    Os.symlink(target, destFile.absolutePath)
                } else {
                    val targetClean = target.removePrefix("./").trimStart('/')
                    val resolvedTarget = File(rootCanonical, targetClean).canonicalFile
                    if (resolvedTarget.exists()) {
                        try {
                            Os.link(resolvedTarget.absolutePath, destFile.absolutePath)
                        } catch (_: Throwable) {
                            resolvedTarget.copyTo(destFile, overwrite = true)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Игнорируем ссылки, которые не позволяет создать конкретная ФС
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun isAllZero(block: ByteArray): Boolean {
        for (b in block) {
            if (b.toInt() != 0) return false
        }
        return true
    }

    private fun extractString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && buffer[end].toInt() != 0) end++
        return String(buffer, offset, end - offset, StandardCharsets.UTF_8)
    }

    private fun parseOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        var i = offset
        val end = offset + length
        while (i < end && (buffer[i] == ' '.code.toByte() || buffer[i] == 0.toByte())) i++
        while (i < end) {
            val b = buffer[i]
            if (b == 0.toByte() || b == ' '.code.toByte()) break
            if (b in '0'.code.toByte()..'7'.code.toByte()) {
                result = (result shl 3) or (b - '0'.code.toByte()).toLong()
            }
            i++
        }
        return result
    }

    private fun readDataBlock(input: InputStream, size: Long): ByteArray {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var remaining = size
        while (remaining > 0L) {
            val toRead = minOf(remaining, buf.size.toLong()).toInt()
            val n = input.read(buf, 0, toRead)
            if (n <= 0) break
            baos.write(buf, 0, n)
            remaining -= n
        }
        skipPadding(input, size)
        return baos.toByteArray()
    }

    private fun skipDataBlock(input: InputStream, size: Long) {
        if (size <= 0L) return
        val total = size + paddingFor(size)
        skipExact(input, total)
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val pad = paddingFor(size)
        if (pad > 0L) skipExact(input, pad)
    }

    private fun paddingFor(size: Long): Long {
        val rem = size % 512L
        return if (rem == 0L) 0L else 512L - rem
    }

    private fun skipExact(input: InputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(4096)
        while (remaining > 0L) {
            val toRead = minOf(remaining, buf.size.toLong()).toInt()
            val n = input.read(buf, 0, toRead)
            if (n <= 0) break
            remaining -= n
        }
    }

    private fun parsePaxAttributes(data: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val text = String(data, StandardCharsets.UTF_8)
        var idx = 0
        while (idx < text.length) {
            val spaceIdx = text.indexOf(' ', idx)
            if (spaceIdx <= idx) break
            val recLen = text.substring(idx, spaceIdx).toIntOrNull() ?: break
            val recEnd = (idx + recLen).coerceAtMost(text.length)
            val record = text.substring(spaceIdx + 1, recEnd).trimEnd('\n')
            val eqIdx = record.indexOf('=')
            if (eqIdx > 0) {
                result[record.substring(0, eqIdx)] = record.substring(eqIdx + 1)
            }
            idx = recEnd
        }
        return result
    }

    private val BOOTSTRAP_SCRIPT = """
        #!/bin/sh
        export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        if [ ! -f /root/.bootstrap_done ]; then
            echo "[*] Первый запуск: установка python3, ffmpeg и yt-dlp..."
            apk update
            apk add --no-cache python3 py3-pip curl ca-certificates ffmpeg
            curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
            chmod +x /usr/local/bin/yt-dlp
            touch /root/.bootstrap_done
            echo "[*] Готово! Теперь можно использовать yt-dlp и python3."
        fi
    """.trimIndent() + "\n"
}
