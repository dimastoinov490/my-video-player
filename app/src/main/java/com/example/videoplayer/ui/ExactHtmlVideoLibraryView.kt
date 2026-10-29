package com.example.videoplayer.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1-в-1 рендерер утверждённого HTML/CSS/SVG интерфейса видеоплеера.
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
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun sendVideos(webView: WebView) {
        if (videos.isEmpty()) return
        val jsonArray = JSONArray()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        for (v in videos) {
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

    LaunchedEffect(videos) {
        webViewRef?.let { sendVideos(it) }
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
                            view?.let { sendVideos(it) }
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
                        fun requestVideos() {
                            Handler(Looper.getMainLooper()).post {
                                sendVideos(this@apply)
                            }
                        }
                    }

                    addJavascriptInterface(bridge, "AndroidBridge")
                    loadUrl("file:///android_asset/player_ui.html")
                    webViewRef = this
                }
            },
            update = { webView ->
                sendVideos(webView)
                webView.evaluateJavascript("if (window.setActiveNavTab) { window.setActiveNavTab(0); }", null)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
