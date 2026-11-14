package com.example.videoplayer.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.archive.ArchiveFileUtils
import com.example.videoplayer.archive.ArchiveSource
import com.example.videoplayer.model.AudioItem
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.Playlist
import com.example.videoplayer.model.PlaylistManager
import com.example.videoplayer.model.VideoItem
import com.example.videoplayer.model.VideoThumbnailLoader
import com.example.videoplayer.player.AppPreferences
import com.example.videoplayer.player.CpuMonitor
import com.example.videoplayer.player.DeviceInfoHelper
import com.example.videoplayer.player.DiagnosticManager
import com.example.videoplayer.player.MemoryMonitor
import com.example.videoplayer.player.ScreenMetricsHelper
import com.example.videoplayer.player.VlcEngineConfigurator
import com.example.videoplayer.service.HttpBroadcastFolder
import com.example.videoplayer.service.HttpServerConfig
import com.example.videoplayer.service.LocalHttpShareServer
import com.example.videoplayer.service.WifiDirectManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 1-в-1 рендерер утверждённого HTML/CSS/SVG интерфейса видеоплеера v3 со всеми 5 вкладками.
 * Сохраняет дизайн символ в символ без упрощений.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExactHtmlVideoLibraryView(
    videos: List<VideoItem>,
    onVideoSelected: (String) -> Unit,
    onNavTab: (tabName: String, index: Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCustomUrl: () -> Unit,
    onRefreshVideos: () -> Unit,
    onOpenArchive: (ArchiveSource, File?) -> Unit = { _, _ -> },
    onOpenArchivePicker: () -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    browseRefreshTick: Int = 0,
    currentNavIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val latestRefresh by rememberUpdatedState(onRefreshVideos)
    val latestVideos by rememberUpdatedState(videos)
    val latestOpenArchive by rememberUpdatedState(onOpenArchive)
    val latestOpenArchivePicker by rememberUpdatedState(onOpenArchivePicker)
    val latestOpenBrowser by rememberUpdatedState(onOpenBrowser)
    val latestOpenTerminal by rememberUpdatedState(onOpenTerminal)
    val sentThumbs = remember { java.util.Collections.synchronizedSet(HashSet<String>()) }
    val wifiStatusRef = remember { AtomicReference("") }
    val initialSyncDone = remember { AtomicBoolean(false) }
    val lastSentVideosRef = remember { AtomicReference<List<VideoItem>?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var thumbsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val rootBrowsePath = remember {
        try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0"
        }
    }
    var currentBrowsePath by remember { mutableStateOf(rootBrowsePath) }

    fun sendThumbsToWeb(webView: WebView, list: List<VideoItem>) {
        if (list.isEmpty()) return
        thumbsJob?.cancel()
        thumbsJob = coroutineScope.launch(Dispatchers.IO) {
            for (v in list) {
                if (webViewRef !== webView) break
                val uriStr = v.uri.toString()
                if (!sentThumbs.add(uriStr)) continue

                // Thumbnail extraction can fail transiently while MediaStore is indexing
                // or while a provider is opening the content URI. Retry a few times.
                var dataUri: String? = null
                repeat(3) { attempt ->
                    if (dataUri == null && webViewRef === webView) {
                        dataUri = VideoThumbnailLoader.getDataUri(context, v)
                        if (dataUri == null && attempt < 2) {
                            kotlinx.coroutines.delay(350L * (attempt + 1))
                        }
                    }
                }

                val thumbnailData = dataUri
                if (thumbnailData == null) {
                    // Keep the item eligible for a later sync/refresh retry.
                    sentThumbs.remove(uriStr)
                    continue
                }

                withContext(Dispatchers.Main) {
                    if (webViewRef === webView && webView.isAttachedToWindow) {
                        val js = """
                            (function() {
                                if (typeof window.setVideoThumb !== 'function') return false;
                                window.setVideoThumb(${JSONObject.quote(uriStr)}, ${JSONObject.quote(thumbnailData)});
                                return true;
                            })();
                        """.trimIndent()
                        webView.evaluateJavascript(js) { result ->
                            if (result != "true") sentThumbs.remove(uriStr)
                        }
                    } else {
                        sentThumbs.remove(uriStr)
                    }
                }
            }
        }
    }

    fun sendVideosToWeb(webView: WebView, vList: List<VideoItem>, force: Boolean = false) {
        if (!force && lastSentVideosRef.get() == vList) {
            if (thumbsJob?.isActive != true) {
                sendThumbsToWeb(webView, vList)
            }
            return
        }
        lastSentVideosRef.set(vList)
        coroutineScope.launch {
            val jsPayload = withContext(Dispatchers.Default) {
                val jsonArray = JSONArray()
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                for (v in vList) {
                    val obj = JSONObject()
                    obj.put("title", v.displayName)
                    val sizeMb = String.format(Locale.US, "%.1f MB", v.size / (1024.0 * 1024.0))
                    val dateStr = try {
                        dateFormat.format(Date(v.dateAdded * 1000L))
                    } catch (_: Exception) {
                        "19.09.2026"
                    }
                    obj.put("meta", "$sizeMb • $dateStr")
                    obj.put("badge", if (v.width >= 1920 || v.height >= 1080) "HD" else "SD")
                    val totalSec = v.duration / 1000
                    val m = totalSec / 60
                    val s = totalSec % 60
                    val h = m / 60
                    val durStr = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m % 60, s) else String.format(Locale.US, "%d:%02d", m, s)
                    obj.put("dur", durStr)
                    obj.put("uri", v.uri.toString())
                    jsonArray.put(obj)
                }
                "if (window.setVideos) { window.setVideos(${jsonArray.toString()}); }"
            }
            if (webViewRef === webView) {
                webView.evaluateJavascript(jsPayload, null)
                sendThumbsToWeb(webView, vList)
            }
        }
    }

    fun sendAudiosToWeb(webView: WebView) {
        coroutineScope.launch {
            val audios = withContext(Dispatchers.IO) {
                MediaStoreHelper.getAudios(context)
            }
            val jsonArray = JSONArray()
            for (a in audios) {
                val obj = JSONObject()
                obj.put("title", a.title)
                obj.put("artist", "${a.artist} • ${a.album}")
                val totalSec = a.duration / 1000
                val m = totalSec / 60
                val s = totalSec % 60
                val durStr = String.format(Locale.US, "%02d:%02d", m, s)
                val sizeMb = String.format(Locale.US, "%.1f MB", a.size / (1024.0 * 1024.0))
                obj.put("meta", "$durStr • $sizeMb")
                obj.put("uri", a.uri.toString())
                jsonArray.put(obj)
            }
            val js = "if (window.setAudios) { window.setAudios(${jsonArray.toString()}); }"
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    fun buildFolderListing(path: String): Pair<JSONArray, Pair<String, String>> {
        val dir = File(path)
        val jsonArray = JSONArray()
        val pathTitle = dir.name.ifBlank { "Хранилище" }
        val parent = dir.parentFile
        val parentPath = if (parent != null && parent.canRead()) parent.absolutePath else ""

        val items: Array<File> = try {
            val files = dir.listFiles() ?: emptyArray<File>()
            files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).toTypedArray()
        } catch (_: Exception) {
            emptyArray<File>()
        }

        if (parentPath.isNotEmpty()) {
            val upObj = JSONObject()
            upObj.put("name", ".. [Назад в ${parent!!.name}]")
            upObj.put("path", parentPath)
            upObj.put("isDir", true)
            upObj.put("isUp", true)
            upObj.put("count", 0)
            jsonArray.put(upObj)
        }

        for (f in items) {
            if (f.name.startsWith(".")) continue
            val obj = JSONObject()
            val isDir = f.isDirectory
            val isArchive = !isDir && ArchiveFileUtils.isArchiveFileName(f.name)
            obj.put("name", f.name)
            obj.put("path", f.absolutePath)
            obj.put("isDir", isDir)
            obj.put("isArchive", isArchive)
            obj.put("isUp", false)
            if (isDir) {
                val count = try { f.list()?.size ?: 0 } catch (_: Exception) { 0 }
                obj.put("count", count)
            } else {
                obj.put("sizeStr", ArchiveFileUtils.formatSize(f.length()))
            }
            jsonArray.put(obj)
        }
        return jsonArray to (pathTitle to parentPath)
    }

    fun sendFoldersToWeb(webView: WebView, path: String) {
        coroutineScope.launch {
            val (jsonArray, meta) = withContext(Dispatchers.IO) { buildFolderListing(path) }
            val escapedTitle = JSONObject.quote(meta.first)
            val escapedPath = JSONObject.quote(path)
            val js = "if (window.setFolders) { window.setFolders(${jsonArray.toString()}, $escapedTitle, $escapedPath); }"
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    fun sendPlaylistsToWeb(webView: WebView) {
        coroutineScope.launch {
            val playlists = withContext(Dispatchers.IO) {
                PlaylistManager.getPlaylists(context)
            }
            val jsonArray = JSONArray()
            for (p in playlists) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("title", p.name)
                obj.put("desc", p.description)
                obj.put("count", "${p.itemUris.size} медиафайлов")
                val icon = when (p.iconType) {
                    "heart", "fav" -> "heart"
                    "history", "hist" -> "hist"
                    "movies", "clapf" -> "clapf"
                    "clips", "note1" -> "note1"
                    else -> "list"
                }
                obj.put("icon", icon)
                obj.put("uri", if (p.itemUris.isNotEmpty()) p.itemUris.first() else "")
                jsonArray.put(obj)
            }
            val js = "if (window.setPlaylists) { window.setPlaylists(${jsonArray.toString()}); }"
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    fun syncAllData(webView: WebView) {
        sendVideosToWeb(webView, latestVideos, force = true)
        sendAudiosToWeb(webView)
        sendFoldersToWeb(webView, currentBrowsePath)
        sendPlaylistsToWeb(webView)
    }

    LaunchedEffect(videos, isPageReady) {
        if (isPageReady) {
            webViewRef?.let { sendVideosToWeb(it, videos) }
        }
    }

    LaunchedEffect(currentNavIndex, isPageReady) {
        if (isPageReady) {
            webViewRef?.evaluateJavascript(
                "if (window.setActiveNavTab) { window.setActiveNavTab($currentNavIndex); }",
                null
            )
        }
    }

    LaunchedEffect(browseRefreshTick, isPageReady) {
        if (isPageReady && browseRefreshTick > 0) {
            webViewRef?.let { sendFoldersToWeb(it, currentBrowsePath) }
        }
    }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(backDispatcher, webViewRef) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewRef
                if (wv != null) {
                    wv.evaluateJavascript("window.nexoraBack ? window.nexoraBack() : false") { result ->
                        val handled = result?.trim()?.removeSurrounding("\"")?.toBoolean() == true
                        if (!handled) {
                            isEnabled = false
                            backDispatcher?.onBackPressed()
                            isEnabled = true
                        }
                    }
                } else {
                    isEnabled = false
                    backDispatcher?.onBackPressed()
                    isEnabled = true
                }
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose {
            callback.remove()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF020306))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.parseColor("#020306"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // This WebView loads trusted app assets; keep file access only for those assets.
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = false

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: android.webkit.JsResult?
                        ): Boolean {
                            android.app.AlertDialog.Builder(context)
                                .setMessage(message)
                                .setPositiveButton("Да") { _, _ -> result?.confirm() }
                                .setNegativeButton("Отмена") { _, _ -> result?.cancel() }
                                .setOnCancelListener { result?.cancel() }
                                .show()
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val target = request?.url ?: return true
                            val targetUrl = target.toString()
                            // Keep the privileged JavaScript bridge on the bundled UI origin only.
                            if (target.scheme == "file" && targetUrl.startsWith("file:///android_asset/")) {
                                return false
                            }
                            if (target.scheme == "about" && targetUrl == "about:blank") {
                                return false
                            }
                            // External navigation must leave this WebView so remote pages never
                            // receive access to AndroidBridge.
                            if (target.scheme == "https" || target.scheme == "http") {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, target))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                                }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.let {
                                isPageReady = true
                                if (initialSyncDone.compareAndSet(false, true)) {
                                    sentThumbs.clear()
                                    syncAllData(it)
                                }
                                if (currentNavIndex != 0) {
                                    it.evaluateJavascript(
                                        "if (window.setActiveNavTab) { window.setActiveNavTab($currentNavIndex); }",
                                        null
                                    )
                                }
                            }
                        }
                    }

                    val bridge = object {
                        @JavascriptInterface
                        fun playVideo(uri: String) {
                            Handler(Looper.getMainLooper()).post {
                                onVideoSelected(uri)
                            }
                        }

                        @JavascriptInterface
                        fun refreshVideos() {
                            Handler(Looper.getMainLooper()).post {
                                latestRefresh()
                            }
                        }

                        @JavascriptInterface
                        fun playLastVideo() {
                            Handler(Looper.getMainLooper()).post {
                                val target = latestVideos.firstOrNull()?.uri?.toString()
                                if (target.isNullOrBlank()) {
                                    Toast.makeText(context, "Видеотека пуста", Toast.LENGTH_SHORT).show()
                                    return@post
                                }
                                onVideoSelected(target)
                            }
                        }

                        @JavascriptInterface
                        fun onNavTab(tabName: String, index: Int) {
                            Handler(Looper.getMainLooper()).post {
                                onNavTab(tabName, index)
                            }
                        }

                        @JavascriptInterface
                        fun openHistory() {
                            Handler(Looper.getMainLooper()).post {
                                onOpenHistory()
                            }
                        }

                        @JavascriptInterface
                        fun openMenu() {
                            Handler(Looper.getMainLooper()).post {
                                onOpenMenu()
                            }
                        }

                        @JavascriptInterface
                        fun openFolder(path: String) {
                            Handler(Looper.getMainLooper()).post {
                                currentBrowsePath = path
                                sendFoldersToWeb(this@apply, path)
                            }
                        }

                        @JavascriptInterface
                        fun openArchive(path: String) {
                            Handler(Looper.getMainLooper()).post {
                                val file = File(path)
                                val baseName = file.name.substringBeforeLast('.').ifBlank { "Archive" }
                                val destDir = File(currentBrowsePath, baseName)
                                latestOpenArchive(ArchiveSource.FilePath(path, file.name), destDir)
                            }
                        }

                        @JavascriptInterface
                        fun openArchivePicker() {
                            Handler(Looper.getMainLooper()).post {
                                latestOpenArchivePicker()
                            }
                        }

                        private fun requestAllFilesPermissionIfNeeded(): Boolean {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                Toast.makeText(
                                    context,
                                    "Разрешите доступ ко всем файлам для операций с файлами",
                                    Toast.LENGTH_LONG
                                ).show()
                                try {
                                    val permIntent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(permIntent)
                                } catch (_: Exception) {}
                                return true
                            }
                            return false
                        }

                        private fun parsePathList(pathsJson: String): List<String> {
                            return try {
                                val arr = JSONArray(pathsJson)
                                (0 until arr.length()).mapNotNull { idx ->
                                    arr.optString(idx).takeIf { it.isNotBlank() }
                                }
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }

                        private fun performTransferBatch(sourcePaths: List<String>, targetDirPath: String, isCopy: Boolean) {
                            if (sourcePaths.isEmpty()) return
                            coroutineScope.launch {
                                val (statusMsg, needsPerm) = withContext(Dispatchers.IO) {
                                    val dstDir = File(targetDirPath)
                                    if (!dstDir.exists() || !dstDir.isDirectory) {
                                        return@withContext ("Целевая папка недоступна" to false)
                                    }
                                    var okCount = 0
                                    var skippedSame = 0
                                    var skippedExists = 0
                                    var skippedSelf = 0
                                    var failedPerm = false
                                    val scanPaths = mutableListOf<String>()

                                    for (srcPath in sourcePaths) {
                                        try {
                                            val src = File(srcPath)
                                            if (!src.exists()) continue
                                            if (src.parentFile?.canonicalPath == dstDir.canonicalPath) {
                                                skippedSame++
                                                continue
                                            }
                                            if (src.isDirectory && (dstDir.canonicalPath == src.canonicalPath ||
                                                    dstDir.canonicalPath.startsWith(src.canonicalPath + File.separator))) {
                                                skippedSelf++
                                                continue
                                            }
                                            val dst = File(dstDir, src.name)
                                            if (dst.exists()) {
                                                skippedExists++
                                                continue
                                            }

                                            val success = if (isCopy) {
                                                if (src.isDirectory) {
                                                    val copied = src.copyRecursively(dst, overwrite = false)
                                                    if (!copied && dst.exists()) dst.deleteRecursively()
                                                    copied
                                                } else {
                                                    src.inputStream().use { input ->
                                                        dst.outputStream().use { output ->
                                                            input.copyTo(output)
                                                        }
                                                    }
                                                    dst.exists() && dst.length() == src.length()
                                                }
                                            } else {
                                                var moved = src.renameTo(dst)
                                                if (!moved) {
                                                    if (src.isFile) {
                                                        src.inputStream().use { input ->
                                                            dst.outputStream().use { output ->
                                                                input.copyTo(output)
                                                            }
                                                        }
                                                        if (dst.exists() && dst.length() == src.length()) {
                                                            moved = src.delete()
                                                        } else {
                                                            dst.delete()
                                                        }
                                                    } else if (src.isDirectory) {
                                                        if (src.copyRecursively(dst, overwrite = false)) {
                                                            moved = src.deleteRecursively()
                                                        } else if (dst.exists()) {
                                                            dst.deleteRecursively()
                                                        }
                                                    }
                                                }
                                                moved
                                            }

                                            if (success) {
                                                okCount++
                                                scanPaths.add(dst.absolutePath)
                                                if (!isCopy) scanPaths.add(src.absolutePath)
                                            } else {
                                                failedPerm = true
                                            }
                                        } catch (_: Exception) {
                                            failedPerm = true
                                        }
                                    }

                                    if (scanPaths.isNotEmpty()) {
                                        MediaScannerConnection.scanFile(
                                            context,
                                            scanPaths.toTypedArray(),
                                            null,
                                            null
                                        )
                                    }

                                    val verb = if (isCopy) "Скопировано" else "Перемещено"
                                    val msg = when {
                                        okCount > 0 && sourcePaths.size == 1 -> "$verb в «${dstDir.name}»"
                                        okCount > 0 -> "$verb ($okCount из ${sourcePaths.size}) в «${dstDir.name}»"
                                        skippedSame > 0 -> "Файлы уже находятся в этой папке"
                                        skippedExists > 0 -> "В папке уже есть файл с таким именем"
                                        skippedSelf > 0 -> "Нельзя перенести папку внутрь самой себя"
                                        failedPerm -> "NEEDS_STORAGE_PERMISSION"
                                        else -> "Не удалось выполнить операцию"
                                    }
                                    msg to (okCount == 0 && failedPerm)
                                }

                                if (statusMsg == "NEEDS_STORAGE_PERMISSION" || needsPerm) {
                                    if (!requestAllFilesPermissionIfNeeded()) {
                                        Toast.makeText(context, "Нет доступа на запись в выбранную папку", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
                                }

                                sendFoldersToWeb(this@apply, currentBrowsePath)
                            }
                        }

                        @JavascriptInterface
                        fun moveFileToFolder(sourcePath: String, targetDirPath: String) {
                            performTransferBatch(listOf(sourcePath), targetDirPath, isCopy = false)
                        }

                        @JavascriptInterface
                        fun copyFilesToFolder(sourcePathsJson: String, targetDirPath: String) {
                            val list = parsePathList(sourcePathsJson)
                            performTransferBatch(list, targetDirPath, isCopy = true)
                        }

                        @JavascriptInterface
                        fun moveFilesToFolder(sourcePathsJson: String, targetDirPath: String) {
                            val list = parsePathList(sourcePathsJson)
                            performTransferBatch(list, targetDirPath, isCopy = false)
                        }

                        @JavascriptInterface
                        fun deleteFiles(pathsJson: String) {
                            val list = parsePathList(pathsJson)
                            if (list.isEmpty()) return
                            coroutineScope.launch {
                                val (msg, failedPerm) = withContext(Dispatchers.IO) {
                                    var deleted = 0
                                    var failed = false
                                    val scanPaths = mutableListOf<String>()
                                    for (p in list) {
                                        try {
                                            val f = File(p)
                                            if (!f.exists()) continue
                                            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                                            if (ok) {
                                                deleted++
                                                scanPaths.add(f.absolutePath)
                                            } else {
                                                failed = true
                                            }
                                        } catch (_: Exception) {
                                            failed = true
                                        }
                                    }
                                    if (scanPaths.isNotEmpty()) {
                                        MediaScannerConnection.scanFile(context, scanPaths.toTypedArray(), null, null)
                                    }
                                    val text = if (deleted > 0) "Удалено: $deleted" else "NEEDS_STORAGE_PERMISSION"
                                    text to (deleted == 0 && failed)
                                }
                                if (msg == "NEEDS_STORAGE_PERMISSION" || failedPerm) {
                                    if (!requestAllFilesPermissionIfNeeded()) {
                                        Toast.makeText(context, "Не удалось удалить выбранные файлы", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                sendFoldersToWeb(this@apply, currentBrowsePath)
                            }
                        }

                        @JavascriptInterface
                        fun renameFile(path: String, newName: String) {
                            val cleanName = newName.trim()
                            if (cleanName.isEmpty() || cleanName.contains("/") || cleanName.contains("\\") || cleanName == "." || cleanName == "..") {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, "Некорректное имя", Toast.LENGTH_SHORT).show()
                                }
                                return
                            }
                            coroutineScope.launch {
                                val (msg, needsPerm) = withContext(Dispatchers.IO) {
                                    try {
                                        val src = File(path)
                                        if (!src.exists()) return@withContext ("Объект не найден" to false)
                                        val parent = src.parentFile ?: return@withContext ("Не удалось определить папку" to false)
                                        val dst = File(parent, cleanName)
                                        if (dst.absolutePath == src.absolutePath) {
                                            return@withContext ("Имя не изменилось" to false)
                                        }
                                        if (dst.exists()) {
                                            return@withContext ("Объект «$cleanName» уже существует" to false)
                                        }
                                        val ok = src.renameTo(dst)
                                        if (ok) {
                                            MediaScannerConnection.scanFile(
                                                context,
                                                arrayOf(src.absolutePath, dst.absolutePath),
                                                null,
                                                null
                                            )
                                            "Переименовано в «$cleanName»" to false
                                        } else {
                                            "NEEDS_STORAGE_PERMISSION" to true
                                        }
                                    } catch (_: Exception) {
                                        "NEEDS_STORAGE_PERMISSION" to true
                                    }
                                }
                                if (msg == "NEEDS_STORAGE_PERMISSION" || needsPerm) {
                                    if (!requestAllFilesPermissionIfNeeded()) {
                                        Toast.makeText(context, "Не удалось переименовать", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                sendFoldersToWeb(this@apply, currentBrowsePath)
                            }
                        }

                        private fun formatBytes(bytes: Long): String {
                            if (bytes < 1024) return "$bytes Б"
                            val kb = bytes / 1024.0
                            if (kb < 1024.0) return String.format(Locale.US, "%.1f КБ", kb)
                            val mb = kb / 1024.0
                            if (mb < 1024.0) return String.format(Locale.US, "%.2f МБ", mb)
                            val gb = mb / 1024.0
                            return String.format(Locale.US, "%.2f ГБ", gb)
                        }

                        @JavascriptInterface
                        fun getFileInfo(path: String) {
                            coroutineScope.launch {
                                val infoJson = withContext(Dispatchers.IO) {
                                    val f = File(path)
                                    val obj = JSONObject()
                                    obj.put("name", f.name.ifBlank { path })
                                    obj.put("path", f.absolutePath)
                                    obj.put("isDir", f.isDirectory)
                                    val typeLabel = when {
                                        f.isDirectory -> "Папка"
                                        f.extension.lowercase() in setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "3gp") -> "Видео (${f.extension.uppercase()})"
                                        f.extension.lowercase() in setOf("mp3", "m4a", "aac", "flac", "ogg", "wav", "opus") -> "Аудио (${f.extension.uppercase()})"
                                        f.extension.isNotBlank() -> "Файл (${f.extension.uppercase()})"
                                        else -> "Файл"
                                    }
                                    obj.put("type", typeLabel)
                                    var totalBytes = 0L
                                    var filesCount = 0
                                    var dirsCount = 0
                                    if (f.isDirectory) {
                                        try {
                                            f.walkTopDown().maxDepth(6).take(5000).forEach { child ->
                                                if (child.absolutePath != f.absolutePath) {
                                                    if (child.isDirectory) dirsCount++
                                                    else {
                                                        filesCount++
                                                        totalBytes += child.length()
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {}
                                        obj.put("containsStr", "$dirsCount папок, $filesCount файлов")
                                    } else {
                                        totalBytes = try { f.length() } catch (_: Exception) { 0L }
                                        obj.put("containsStr", "")
                                    }
                                    obj.put("sizeStr", "${formatBytes(totalBytes)} ($totalBytes Б)")
                                    val modDate = try {
                                        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(f.lastModified()))
                                    } catch (_: Exception) { "—" }
                                    obj.put("modifiedStr", modDate)
                                    val perms = buildString {
                                        append(if (f.canRead()) "Чтение" else "Нет чтения")
                                        append(if (f.canWrite()) " / Запись" else " / Только чтение")
                                    }
                                    obj.put("permissions", perms)
                                    obj.toString()
                                }
                                withContext(Dispatchers.Main) {
                                    evaluateJavascript("if (window.showFileInfoModal) { window.showFileInfoModal($infoJson); }", null)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun createPlaylist(name: String) {
                            Handler(Looper.getMainLooper()).post {
                                PlaylistManager.createPlaylist(context, name)
                                Toast.makeText(context, "Плейлист \"$name\" создан", Toast.LENGTH_SHORT).show()
                                sendPlaylistsToWeb(this@apply)
                            }
                        }

                        @JavascriptInterface
                        fun addToPlaylist(uri: String, title: String) {
                            Handler(Looper.getMainLooper()).post {
                                val playlists = PlaylistManager.getPlaylists(context)
                                if (playlists.isNotEmpty()) {
                                    val target = playlists.first()
                                    PlaylistManager.addItemToPlaylist(context, target.id, uri)
                                    Toast.makeText(context, "Добавлено в \"${target.name}\"", Toast.LENGTH_SHORT).show()
                                    sendPlaylistsToWeb(this@apply)
                                } else {
                                    val newPl = PlaylistManager.createPlaylist(context, "Избранное")
                                    PlaylistManager.addItemToPlaylist(context, newPl.id, uri)
                                    Toast.makeText(context, "Создан плейлист \"Избранное\" и добавлен трек", Toast.LENGTH_SHORT).show()
                                    sendPlaylistsToWeb(this@apply)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun openPlaylist(id: String) {
                            Handler(Looper.getMainLooper()).post {
                                val playlists = PlaylistManager.getPlaylists(context)
                                val found = playlists.find { it.id == id }
                                if (found != null && found.itemUris.isNotEmpty()) {
                                    onVideoSelected(found.itemUris.first())
                                } else {
                                    Toast.makeText(context, "В плейлисте пока нет медиафайлов", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onMoreItemClick(id: String) {
                            Handler(Looper.getMainLooper()).post {
                                when (id) {
                                    "url" -> onOpenCustomUrl()
                                    "browser" -> latestOpenBrowser("https://google.com")
                                    "terminal" -> latestOpenTerminal()
                                    "settings" -> { /* В v5 настройки открываются прямо в HTML */ }
                                    "share" -> { /* В v5 раздача открывается прямо в HTML */ }
                                    else -> Toast.makeText(context, "Выбран пункт: $id", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun shareGetState(): String {
                            return try {
                                val isRunning = LocalHttpShareServer.isRunning
                                val url = if (isRunning) (LocalHttpShareServer.getServerUrl(context) ?: "") else ""
                                val port = LocalHttpShareServer.currentPort
                                val err = LocalHttpShareServer.lastErrorMessage ?: ""
                                val authReq = HttpServerConfig.isAuthRequired(context)
                                val user = HttpServerConfig.getUsername(context)
                                val pass = HttpServerConfig.getPassword(context)
                                val folder = HttpServerConfig.getBroadcastFolder(context).id
                                val serverStyle = HttpServerConfig.getServerStyle(context)
                                val wifiEnabled = WifiDirectManager.isEnabled
                                val wifiSsid = WifiDirectManager.currentSsid
                                val wifiPass = WifiDirectManager.currentPass
                                val wifiQr = WifiDirectManager.getWifiQrPayload(wifiSsid, wifiPass)

                                val adminUser = HttpServerConfig.getAdminUsername(context)
                                val adminDefault = HttpServerConfig.isAdminPasswordDefault(context)
                                val adminPass = if (adminDefault) HttpServerConfig.DEFAULT_ADMIN_PASSWORD else null

                                val json = JSONObject()
                                json.put("running", isRunning)
                                json.put("url", url)
                                json.put("port", port)
                                json.put("error", err)
                                json.put("authRequired", authReq)
                                json.put("username", user)
                                json.put("password", pass)
                                json.put("adminUsername", adminUser)
                                json.put("adminPassword", adminPass ?: JSONObject.NULL)
                                json.put("adminDefault", adminDefault)
                                json.put("folder", folder)
                                json.put("serverStyle", serverStyle)

                                val wifiJson = JSONObject()
                                wifiJson.put("enabled", wifiEnabled)
                                wifiJson.put("ssid", wifiSsid)
                                wifiJson.put("pass", wifiPass)
                                wifiJson.put("qrPayload", wifiQr)
                                val currentWifiStatus = wifiStatusRef.get()
                                val statusDisplay = if (!currentWifiStatus.isNullOrBlank()) {
                                    currentWifiStatus
                                } else if (wifiEnabled) {
                                    "Точка активна: $wifiSsid"
                                } else {
                                    "Прямое подключение по QR с паролем от Wi‑Fi Direct"
                                }
                                wifiJson.put("status", statusDisplay)
                                json.put("wifi", wifiJson)

                                json.toString()
                            } catch (e: Exception) {
                                "{}"
                            }
                        }

                        @JavascriptInterface
                        fun shareResetAdmin() {
                            Handler(Looper.getMainLooper()).post {
                                HttpServerConfig.resetAdmin(context)
                                LocalHttpShareServer.closeAdminSessions()
                                Toast.makeText(context, "Администратор сброшен (admin / admin)", Toast.LENGTH_SHORT).show()
                                webViewRef?.evaluateJavascript("if (window.onShareState) { window.onShareState(); }", null)
                            }
                        }

                        @JavascriptInterface
                        fun shareSetServer(on: Boolean) {
                            Handler(Looper.getMainLooper()).post {
                                coroutineScope.launch {
                                    if (on) {
                                        val port = HttpServerConfig.getPort(context)
                                        val started = withContext(Dispatchers.IO) {
                                            LocalHttpShareServer.start(context, port)
                                        }
                                        if (started) {
                                            val serverUrl = LocalHttpShareServer.getServerUrl(context)
                                            Toast.makeText(context, "Сервер запущен: $serverUrl", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = LocalHttpShareServer.lastErrorMessage ?: "Не удалось занять порт"
                                            Toast.makeText(context, "Ошибка запуска сервера: $err", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            LocalHttpShareServer.stop()
                                        }
                                        Toast.makeText(context, "Сервер остановлен", Toast.LENGTH_SHORT).show()
                                    }
                                    webViewRef?.evaluateJavascript("if (window.onShareState) { window.onShareState(); }", null)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun shareSetConfig(key: String, value: String) {
                            Handler(Looper.getMainLooper()).post {
                                when (key) {
                                    "auth_required" -> HttpServerConfig.setAuthRequired(context, value.toBoolean())
                                    "username" -> HttpServerConfig.setUsername(context, value)
                                    "password" -> HttpServerConfig.setPassword(context, value)
                                    "folder" -> {
                                        val folder = HttpBroadcastFolder.values().find { it.id == value } ?: HttpBroadcastFolder.ALL
                                        HttpServerConfig.setBroadcastFolder(context, folder)
                                    }
                                    "server_style" -> {
                                        HttpServerConfig.setServerStyle(context, value)
                                    }
                                }
                            }
                        }

                        @JavascriptInterface
                        fun shareWifi(on: Boolean) {
                            Handler(Looper.getMainLooper()).post {
                                if (on) {
                                    WifiDirectManager.startHotspotOrDirect(
                                        context = context,
                                        onStatus = { st ->
                                            wifiStatusRef.set(st)
                                            Handler(Looper.getMainLooper()).post {
                                                webViewRef?.evaluateJavascript("if (window.onShareState) { window.onShareState(); }", null)
                                            }
                                        },
                                        onComplete = {
                                            Handler(Looper.getMainLooper()).post {
                                                webViewRef?.evaluateJavascript("if (window.onShareState) { window.onShareState(); }", null)
                                            }
                                        }
                                    )
                                } else {
                                    WifiDirectManager.stop(context)
                                    wifiStatusRef.set("")
                                    webViewRef?.evaluateJavascript("if (window.onShareState) { window.onShareState(); }", null)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun getQrDataUri(text: String, size: Int): String {
                            // This bridge method allocates a bitmap and a pixel buffer. Bound
                            // both dimensions and input size so malformed calls cannot trigger
                            // excessive allocations from the WebView bridge thread.
                            if (text.isBlank() || text.length > 4096) return ""
                            val qrSize = if (size <= 0) 512 else size.coerceIn(128, 1024)
                            var bitmap: Bitmap? = null
                            return try {
                                val matrix: BitMatrix = MultiFormatWriter().encode(
                                    text,
                                    BarcodeFormat.QR_CODE,
                                    qrSize,
                                    qrSize
                                )
                                val pixels = IntArray(qrSize * qrSize)
                                for (y in 0 until qrSize) {
                                    val offset = y * qrSize
                                    for (x in 0 until qrSize) {
                                        pixels[offset + x] = if (matrix[x, y]) {
                                            0xFF000000.toInt()
                                        } else {
                                            0xFFFFFFFF.toInt()
                                        }
                                    }
                                }
                                val createdBitmap = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.ARGB_8888)
                                bitmap = createdBitmap
                                createdBitmap.setPixels(pixels, 0, qrSize, 0, 0, qrSize, qrSize)
                                ByteArrayOutputStream().use { baos ->
                                    createdBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                    "data:image/png;base64,$b64"
                                }
                            } catch (e: Exception) {
                                ""
                            } finally {
                                bitmap?.recycle()
                            }
                        }

                        @JavascriptInterface
                        fun copyText(text: String) {
                            Handler(Looper.getMainLooper()).post {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Nexora", text)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Скопировано в буфер", Toast.LENGTH_SHORT).show()
                            }
                        }

                        @JavascriptInterface
                        fun openExternal(url: String) {
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    val prefs = context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE)
                                    val openInApp = prefs.getBoolean("open_in_app_browser", true)
                                    val uri = Uri.parse(url)
                                    val scheme = uri.scheme?.lowercase()
                                    if (openInApp && (scheme == "http" || scheme == "https")) {
                                        val intent = Intent(context, BrowserActivity::class.java).apply {
                                            putExtra("url", url)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Не удалось открыть браузер: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun getSettings(): String {
                            return try {
                                val prefs = context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE)
                                val hw = prefs.getBoolean("hw_accel", true)
                                val forceVulkan = AppPreferences.isForceVulkanEnabled(context)
                                val renderEngine = AppPreferences.getRenderEngine(context).value
                                val bg = prefs.getBoolean("bg_play", false)
                                val buff = prefs.getBoolean("show_buffering_status", true)
                                val oriMode = OrientationHelper.getMode(context)
                                val rot = oriMode == OrientationHelper.MODE_SENSOR || oriMode == OrientationHelper.MODE_SYSTEM
                                val openInApp = prefs.getBoolean("open_in_app_browser", true)
                                val profile = VlcEngineConfigurator.getHardwareProfile(context)
                                val ram = MemoryMonitor.getRamInfo(context)
                                val dev = DeviceInfoHelper.getDetails(context)

                                val json = JSONObject()
                                json.put("hw_accel", hw)
                                json.put("force_vulkan", forceVulkan)
                                json.put("render_engine_mode", renderEngine)
                                json.put("cpu_cores", profile.cpuCores)
                                json.put("vulkan_supported", profile.isVulkanSupported)
                                json.put("vulkan_used", profile.isVulkanUsed)
                                json.put("ram_total_mb", ram.totalMb)
                                json.put("ram_available_mb", ram.availableMb)
                                json.put("ram_used_mb", ram.usedMb)
                                json.put("ram_usage_percent", ram.usagePercent)
                                json.put("device_summary", "${dev.manufacturer} ${dev.model} • Android ${dev.androidVersion}")
                                json.put("bg_play", bg)
                                json.put("show_buffering_status", buff)
                                json.put("auto_rotate", rot)
                                json.put("orientation_mode", oriMode)
                                json.put("open_in_app_browser", openInApp)
                                json.toString()
                            } catch (e: Exception) {
                                "{}"
                            }
                        }

                        @JavascriptInterface
                        fun getRamInfo(): String {
                            return try {
                                val ram = MemoryMonitor.getRamInfo(context)
                                JSONObject().apply {
                                    put("totalMb", ram.totalMb)
                                    put("availableMb", ram.availableMb)
                                    put("usedMb", ram.usedMb)
                                    put("usagePercent", ram.usagePercent)
                                }.toString()
                            } catch (e: Exception) {
                                "{}"
                            }
                        }

                        @JavascriptInterface
                        fun getCpuInfo(): String {
                            return try {
                                val cores = CpuMonitor.getSnapshot()
                                val avg = if (cores.isNotEmpty()) cores.sumOf { it.usagePercent } / cores.size else 0
                                val coresArr = JSONArray()
                                for (c in cores) {
                                    coresArr.put(JSONObject().apply {
                                        put("coreId", c.coreId)
                                        put("usagePercent", c.usagePercent)
                                        if (c.freqMhz != null) put("freqMhz", c.freqMhz)
                                    })
                                }
                                JSONObject().apply {
                                    put("avgUsagePercent", avg)
                                    put("coreCount", cores.size)
                                    put("cores", coresArr)
                                }.toString()
                            } catch (e: Exception) {
                                "{}"
                            }
                        }

                        @JavascriptInterface
                        fun getDeviceInfo(): String {
                            return try {
                                val d = DeviceInfoHelper.getDetails(context)
                                JSONObject().apply {
                                    put("manufacturer", d.manufacturer)
                                    put("model", d.model)
                                    put("androidVersion", d.androidVersion)
                                    put("sdkLevel", d.sdkLevel)
                                    put("securityPatch", d.securityPatch)
                                    put("buildId", d.buildId)
                                    put("screenResolution", d.screenResolution)
                                    put("screenDensity", d.screenDensity)
                                    put("refreshRate", d.refreshRate.toDouble())
                                    put("batteryLevel", d.batteryLevel)
                                    put("batteryVoltage", d.batteryVoltage.toDouble())
                                    put("batteryTemp", d.batteryTemp.toDouble())
                                    put("batteryTech", d.batteryTech)
                                    put("storageTotalGb", d.storageTotalGb.toDouble())
                                    put("storageFreeGb", d.storageFreeGb.toDouble())
                                }.toString()
                            } catch (e: Exception) {
                                "{}"
                            }
                        }

                        @JavascriptInterface
                        fun runDiagnostics(): String {
                            return try {
                                val items = runBlocking {
                                    DiagnosticManager.runFullDiagnostics(context)
                                }
                                val arr = JSONArray()
                                for (item in items) {
                                    arr.put(JSONObject().apply {
                                        put("id", item.id)
                                        put("title", item.title)
                                        put("status", item.status.name)
                                        put("description", item.description)
                                    })
                                }
                                arr.toString()
                            } catch (e: Exception) {
                                "[]"
                            }
                        }

                        @JavascriptInterface
                        fun getScreenMetrics(): String {
                            return try {
                                val params = ScreenMetricsHelper.getScreenParams(context)
                                JSONObject().apply {
                                    params.forEach { (k, v) -> put(k, v) }
                                }.toString()
                            } catch (e: Exception) {
                                "{}"
                            }
                        }

                        @JavascriptInterface
                        fun setSetting(key: String, value: String) {
                            Handler(Looper.getMainLooper()).post {
                                if (key == "orientation_mode") {
                                    val saved = OrientationHelper.setMode(context, value)
                                    (context as? android.app.Activity)?.let { OrientationHelper.apply(it, saved) }
                                    webViewRef?.evaluateJavascript("if (window.onSettingsState) { window.onSettingsState(); }", null)
                                    return@post
                                }
                                if (key == "auto_rotate") {
                                    val saved = OrientationHelper.setMode(
                                        context,
                                        if (value.toBoolean()) OrientationHelper.MODE_SENSOR else OrientationHelper.MODE_PORTRAIT
                                    )
                                    (context as? android.app.Activity)?.let { OrientationHelper.apply(it, saved) }
                                    webViewRef?.evaluateJavascript("if (window.onSettingsState) { window.onSettingsState(); }", null)
                                    return@post
                                }
                                if (key == "render_engine_mode") {
                                    val chosen = AppPreferences.RenderEngine.entries.find { it.value == value }
                                        ?: AppPreferences.RenderEngine.AUTO
                                    AppPreferences.setRenderEngine(context, chosen)
                                    webViewRef?.evaluateJavascript("if (window.onSettingsState) { window.onSettingsState(); }", null)
                                    return@post
                                }
                                if (key == "force_vulkan") {
                                    AppPreferences.setForceVulkanEnabled(context, value.toBoolean())
                                    webViewRef?.evaluateJavascript("if (window.onSettingsState) { window.onSettingsState(); }", null)
                                    return@post
                                }
                                val validKeys = setOf("hw_accel", "bg_play", "show_buffering_status", "open_in_app_browser")
                                if (key in validKeys) {
                                    val prefs = context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE)
                                    prefs.edit().putBoolean(key, value.toBoolean()).apply()
                                }
                                webViewRef?.evaluateJavascript("if (window.onSettingsState) { window.onSettingsState(); }", null)
                            }
                        }

                        @JavascriptInterface
                        fun requestInitialData() {
                            Handler(Looper.getMainLooper()).post {
                                isPageReady = true
                                if (initialSyncDone.compareAndSet(false, true)) {
                                    syncAllData(this@apply)
                                }
                            }
                        }
                    }

                    addJavascriptInterface(bridge, "AndroidBridge")
                    loadUrl("file:///android_asset/player_ui.html")
                    webViewRef = this
                }
            },
            update = {},
            onRelease = { webView ->
                thumbsJob?.cancel()
                if (webViewRef === webView) webViewRef = null
                webView.stopLoading()
                webView.removeJavascriptInterface("AndroidBridge")
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
