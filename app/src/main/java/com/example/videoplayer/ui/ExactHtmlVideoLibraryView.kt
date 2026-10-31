package com.example.videoplayer.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
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
import com.example.videoplayer.model.AudioItem
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.Playlist
import com.example.videoplayer.model.PlaylistManager
import com.example.videoplayer.model.VideoItem
import com.example.videoplayer.model.VideoThumbnailLoader
import com.example.videoplayer.service.HttpBroadcastFolder
import com.example.videoplayer.service.HttpServerConfig
import com.example.videoplayer.service.LocalHttpShareServer
import com.example.videoplayer.service.WifiDirectManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    currentNavIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val latestRefresh by rememberUpdatedState(onRefreshVideos)
    val latestVideos by rememberUpdatedState(videos)
    val sentThumbs = remember { java.util.Collections.synchronizedSet(HashSet<String>()) }
    val wifiStatusRef = remember { AtomicReference("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentBrowsePath by remember {
        mutableStateOf(
            try {
                Environment.getExternalStorageDirectory().absolutePath
            } catch (_: Exception) {
                "/storage/emulated/0"
            }
        )
    }

    fun sendThumbsToWeb(webView: WebView, list: List<VideoItem>) {
        coroutineScope.launch(Dispatchers.IO) {
            for (v in list) {
                val uriStr = v.uri.toString()
                if (!sentThumbs.add(uriStr)) continue
                val d = VideoThumbnailLoader.getDataUri(context, v) ?: continue
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "if (window.setVideoThumb) { window.setVideoThumb(${JSONObject.quote(uriStr)}, ${JSONObject.quote(d)}); }",
                        null
                    )
                }
            }
        }
    }

    fun sendVideosToWeb(webView: WebView, vList: List<VideoItem>) {
        if (vList.isEmpty()) return
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
        val js = "if (window.setVideos) { window.setVideos(${jsonArray.toString()}); }"
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript(js, null)
        }
        sendThumbsToWeb(webView, vList)
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

    fun sendFoldersToWeb(webView: WebView, path: String) {
        coroutineScope.launch {
            val dir = File(path)
            val jsonArray = JSONArray()
            var pathTitle = dir.name.ifBlank { "Хранилище" }

            val items: Array<File> = withContext(Dispatchers.IO) {
                try {
                    val files = dir.listFiles() ?: emptyArray<File>()
                    files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).toTypedArray()
                } catch (_: Exception) {
                    emptyArray<File>()
                }
            }

            // Up folder if not at root
            val parent = dir.parentFile
            if (parent != null && parent.canRead()) {
                val upObj = JSONObject()
                upObj.put("name", ".. [Назад в ${parent.name}]")
                upObj.put("path", parent.absolutePath)
                upObj.put("isDir", true)
                upObj.put("count", 0)
                jsonArray.put(upObj)
            }

            for (f in items) {
                if (f.name.startsWith(".")) continue
                val obj = JSONObject()
                obj.put("name", f.name)
                obj.put("path", f.absolutePath)
                obj.put("isDir", f.isDirectory)
                if (f.isDirectory) {
                    val count = try { f.list()?.size ?: 0 } catch (_: Exception) { 0 }
                    obj.put("count", count)
                } else {
                    val sizeMb = String.format(Locale.US, "%.1f MB", f.length() / (1024.0 * 1024.0))
                    obj.put("sizeStr", sizeMb)
                }
                jsonArray.put(obj)
            }

            val escapedTitle = JSONObject.quote(pathTitle)
            val js = "if (window.setFolders) { window.setFolders(${jsonArray.toString()}, $escapedTitle); }"
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
        sendVideosToWeb(webView, videos)
        sendAudiosToWeb(webView)
        sendFoldersToWeb(webView, currentBrowsePath)
        sendPlaylistsToWeb(webView)
    }

    LaunchedEffect(videos) {
        webViewRef?.let { sendVideosToWeb(it, videos) }
    }

    LaunchedEffect(currentNavIndex) {
        webViewRef?.evaluateJavascript(
            "if (window.setActiveNavTab) { window.setActiveNavTab($currentNavIndex); }",
            null
        )
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
                    setBackgroundColor(android.graphics.Color.parseColor("#020306"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.let {
                                sentThumbs.clear()
                                syncAllData(it)
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
                                val target = latestVideos.firstOrNull()?.uri?.toString() ?: "sample://western_full"
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
                                val wifiEnabled = WifiDirectManager.isEnabled
                                val wifiSsid = WifiDirectManager.currentSsid
                                val wifiPass = WifiDirectManager.currentPass
                                val wifiQr = WifiDirectManager.getWifiQrPayload(wifiSsid, wifiPass)

                                val json = JSONObject()
                                json.put("running", isRunning)
                                json.put("url", url)
                                json.put("port", port)
                                json.put("error", err)
                                json.put("authRequired", authReq)
                                json.put("username", user)
                                json.put("password", pass)
                                json.put("folder", folder)

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
                            return try {
                                val qrSize = if (size <= 0) 512 else size
                                val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, qrSize, qrSize)
                                val pixels = IntArray(qrSize * qrSize)
                                for (y in 0 until qrSize) {
                                    val offset = y * qrSize
                                    for (x in 0 until qrSize) {
                                        pixels[offset + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                                    }
                                }
                                val bitmap = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.ARGB_8888)
                                bitmap.setPixels(pixels, 0, qrSize, 0, 0, qrSize, qrSize)
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                "data:image/png;base64,$b64"
                            } catch (e: Exception) {
                                ""
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
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
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
                                val bg = prefs.getBoolean("bg_play", false)
                                val buff = prefs.getBoolean("show_buffering_status", true)
                                val rot = prefs.getBoolean("auto_rotate", true)

                                val json = JSONObject()
                                json.put("hw_accel", hw)
                                json.put("bg_play", bg)
                                json.put("show_buffering_status", buff)
                                json.put("auto_rotate", rot)
                                json.toString()
                            } catch (e: Exception) {
                                "{}"
                            }
                        }

                        @JavascriptInterface
                        fun setSetting(key: String, value: String) {
                            Handler(Looper.getMainLooper()).post {
                                val validKeys = setOf("hw_accel", "bg_play", "show_buffering_status", "auto_rotate")
                                if (key in validKeys) {
                                    val prefs = context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE)
                                    prefs.edit().putBoolean(key, value.toBoolean()).apply()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun requestInitialData() {
                            Handler(Looper.getMainLooper()).post {
                                syncAllData(this@apply)
                            }
                        }
                    }

                    addJavascriptInterface(bridge, "AndroidBridge")
                    loadUrl("file:///android_asset/player_ui.html")
                    webViewRef = this
                }
            },
            update = { webView ->
                sendVideosToWeb(webView, videos)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
