package com.example.videoplayer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onOpenProtectedVault: () -> Unit,
    onOpenTerminal: () -> Unit,
    currentNavIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
            val thumb = when {
                v.displayName.contains("cyber", ignoreCase = true) -> "thumb_cyberpunk.jpg"
                v.displayName.contains("scifi", ignoreCase = true) || v.displayName.contains("star", ignoreCase = true) || v.displayName.contains("izgoi", ignoreCase = true) -> "thumb_scifi.jpg"
                v.displayName.contains("nature", ignoreCase = true) || v.displayName.contains("природа", ignoreCase = true) -> "thumb_nature.jpg"
                else -> "thumb_western.jpg"
            }
            obj.put("thumb", thumb)
            jsonArray.put(obj)
        }
        val js = "if (window.setVideos) { window.setVideos(${jsonArray.toString()}); }"
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript(js, null)
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
                        fun playLastVideo() {
                            Handler(Looper.getMainLooper()).post {
                                val target = videos.firstOrNull()?.uri?.toString() ?: "sample://western_full"
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
                                    "settings" -> onOpenSettings()
                                    "terminal" -> onOpenTerminal()
                                    "url" -> onOpenCustomUrl()
                                    "vault" -> onOpenProtectedVault()
                                    "share" -> onNavTab("more", 4)
                                    else -> Toast.makeText(context, "Выбран пункт: $id", Toast.LENGTH_SHORT).show()
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
