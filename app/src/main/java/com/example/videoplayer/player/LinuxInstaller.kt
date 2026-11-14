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

    fun ensureAiEngineScript(context: Context): File {
        val rootDir = File(getRootFsDir(context), "root")
        if (!rootDir.exists()) rootDir.mkdirs()

        val webDir = File(context.filesDir, "web")
        if (!webDir.exists()) webDir.mkdirs()

        val engineContent = try {
            context.assets.open("ai_engine.py").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            AI_ENGINE_FALLBACK
        }
        val appDataEngine = File(context.filesDir, "ai_engine.py")
        appDataEngine.writeText(engineContent)
        File(rootDir, "ai_engine.py").writeText(engineContent)

        val serverContent = try {
            context.assets.open("ai_server.py").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            AI_SERVER_FALLBACK
        }
        File(context.filesDir, "ai_server.py").writeText(serverContent)
        File(rootDir, "ai_server.py").writeText(serverContent)

        ensureWebFilesSeeded(context)

        return appDataEngine
    }

    fun ensureWebFilesSeeded(context: Context) {
        val webDir = File(context.filesDir, "web")
        if (!webDir.exists()) webDir.mkdirs()

        val webAssets = listOf(
            "web/index.html",
            "web/nexora_media/index.html",
            "web/nexora_media/style.css",
            "web/nexora_media/stage.css",
            "web/nexora_media/app.js"
        )
        for (assetPath in webAssets) {
            val target = File(context.filesDir, assetPath)
            if (!target.exists()) {
                try {
                    target.parentFile?.mkdirs()
                    context.assets.open(assetPath).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
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

            // Сохраняем скрипт первичной настройки Python3 и yt-dlp, а также AI Engine скрипт
            ensureBootstrapScript(context)
            ensureAiEngineScript(context)

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

    private val AI_ENGINE_FALLBACK = """
        import json
        import os
        from llama_cpp import Llama

        config_path = "/app_data/config.json"
        context_size = 2048
        n_threads = 4
        if os.path.exists(config_path):
            with open(config_path, "r") as f:
                config = json.load(f)
                model_path = config.get("active_model_path") or "/app_data/models/default.gguf"
                context_size = int(config.get("context_size", 2048))
                n_threads = int(config.get("n_threads", 4))
        else:
            model_path = "/app_data/models/default.gguf"

        llm = Llama(
            model_path=model_path,
            n_ctx=context_size,
            n_threads=n_threads,
            verbose=False
        )
        print(f"[AI Engine] Успешно подключена модель из пути: {model_path}")
    """.trimIndent() + "\n"

    private val AI_SERVER_FALLBACK = """
        import http.server
        import json
        import os
        from llama_cpp import Llama

        PORT = 8080
        CONFIG_PATH = "/app_data/config.json"
        WEB_ROOT = os.path.realpath("/app_data/web")
        _llm_instance = None
        _loaded_model_path = None

        def get_ai_model():
            global _llm_instance, _loaded_model_path
            if not os.path.exists(CONFIG_PATH):
                return None
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                config = json.load(f)
                if not config.get("ai_assistant_enabled", False):
                    return None
                model_path = config.get("active_model_path", "/app_data/models/qwen1.5b.gguf")
                context_size = int(config.get("context_size", 2048))
                n_threads = int(config.get("n_threads", 4))
            if _llm_instance is not None and _loaded_model_path == model_path:
                return _llm_instance
            if not model_path or not os.path.exists(model_path):
                return None
            _llm_instance = Llama(model_path=model_path, n_ctx=context_size, n_threads=n_threads, verbose=False)
            _loaded_model_path = model_path
            return _llm_instance

        class AIHandler(http.server.BaseHTTPRequestHandler):
            def _send_cors_headers(self):
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
                self.send_header("Access-Control-Allow-Headers", "Content-Type")

            def _send_json(self, status_code, payload):
                body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
                self.send_response(status_code)
                self._send_cors_headers()
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_OPTIONS(self):
                self.send_response(204)
                self._send_cors_headers()
                self.send_header("Content-Length", "0")
                self.end_headers()

            def do_POST(self):
                if self.path != "/api/fix-ui":
                    self._send_json(404, {"error": "Endpoint not found"})
                    return
                content_length = int(self.headers.get("Content-Length", "0"))
                post_data = json.loads(self.rfile.read(content_length).decode("utf-8"))
                file_rel_path = str(post_data.get("file", "index.html")).lstrip("/\\")
                user_instruction = str(post_data.get("prompt", ""))
                full_file_path = os.path.realpath(os.path.join(WEB_ROOT, file_rel_path))
                if full_file_path != WEB_ROOT and not full_file_path.startswith(WEB_ROOT + os.sep):
                    self._send_json(403, {"error": "Access outside /app_data/web is forbidden"})
                    return
                if not os.path.exists(full_file_path):
                    self._send_json(404, {"error": "File not found"})
                    return
                llm = get_ai_model()
                if not llm:
                    self._send_json(403, {"error": "AI Assistant is disabled in settings or model missing"})
                    return
                with open(full_file_path, "r", encoding="utf-8") as f:
                    source_code = f.read()
                prompt = f"System: Ты эксперт по веб-верстке. Исправь код по инструкции пользователя. Верни ТОЛЬКО готовый исправленный код файла.\n\nИнструкция: {user_instruction}\n\nИсходный код:\n{source_code}\n\nИсправленный код:"
                response = llm(prompt, max_tokens=1500, temperature=0.1)
                fixed_code = response["choices"][0]["text"].strip()
                if fixed_code:
                    with open(full_file_path, "w", encoding="utf-8") as f:
                        f.write(fixed_code)
                self._send_json(200, {"status": "success", "message": "File updated"})

        if __name__ == "__main__":
            os.makedirs(WEB_ROOT, exist_ok=True)
            server = http.server.HTTPServer(("127.0.0.1", PORT), AIHandler)
            print(f"[AI Server] Запущен на порту {PORT}")
            server.serve_forever()
    """.trimIndent() + "\n"
}
