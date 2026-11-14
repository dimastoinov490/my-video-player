package com.example.videoplayer.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullBrowserScreen(
    initialUrl: String = "https://google.com",
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var addressValue by remember { mutableStateOf(TextFieldValue(initialUrl)) }
    var isAddressFocused by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    fun hideAddressKeyboard() {
        focusManager.clearFocus()
        keyboard?.hide()
        isAddressFocused = false
        addressValue = TextFieldValue(currentUrl)
    }

    fun loadAddress(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return
        val host = input.substringBefore('/').substringBefore(':')
        val localHost = host.equals("localhost", true) || host == "127.0.0.1" ||
            host.startsWith("192.168.") || host.startsWith("10.") ||
            (host.startsWith("172.") && (host.substringAfter('.', "").substringBefore('.').toIntOrNull()?.let { it in 16..31 } == true)) ||
            host.endsWith(".local", true)
        val target = when {
            input.startsWith("http://", true) || input.startsWith("https://", true) -> input
            localHost -> "http://$input"
            input.contains(" ") || !host.contains('.') ->
                "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
            else -> "https://$input"
        }
        hideAddressKeyboard()
        currentUrl = target
        addressValue = TextFieldValue(target)
        webViewInstance?.loadUrl(target)
    }

    fun updateNavigation(view: WebView?) {
        canGoBack = view?.canGoBack() ?: false
        canGoForward = view?.canGoForward() ?: false
    }

    BackHandler {
        when {
            isAddressFocused -> hideAddressKeyboard()
            webViewInstance?.canGoBack() == true -> webViewInstance?.goBack()
            else -> onClose()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF120A05))) {
        Surface(
            color = Color(0xFF120A05),
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack() else onClose() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                IconButton(
                    onClick = { webViewInstance?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Вперёд", tint = if (canGoForward) Color.White else Color(0xFF756B62))
                }

                Surface(
                    color = Color(0xFF1F1A17),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isAddressFocused || currentUrl.isBlank()) Icons.Default.Search else Icons.Default.Lock,
                            contentDescription = "Адрес",
                            tint = Color(0xFFB7A99D),
                            modifier = Modifier.size(18.dp)
                        )
                        BasicTextField(
                            value = addressValue,
                            onValueChange = { addressValue = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { loadAddress(addressValue.text) }),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).onFocusChanged { state ->
                                if (state.isFocused && !isAddressFocused) {
                                    isAddressFocused = true
                                    addressValue = TextFieldValue(currentUrl, selection = TextRange(0, currentUrl.length))
                                } else if (!state.isFocused && isAddressFocused) {
                                    isAddressFocused = false
                                    addressValue = TextFieldValue(currentUrl)
                                }
                            },
                            decorationBox = { innerTextField ->
                                if (addressValue.text.isEmpty()) Text("Поиск или веб-адрес", color = Color(0xFF9D8F82), fontSize = 14.sp)
                                innerTextField()
                            }
                        )
                        IconButton(
                            onClick = {
                                if (isAddressFocused) addressValue = TextFieldValue("")
                                else if (isLoading) webViewInstance?.stopLoading()
                                else webViewInstance?.reload()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isAddressFocused) Icons.Default.Close else if (isLoading) Icons.Default.Stop else Icons.Default.Refresh,
                                contentDescription = if (isAddressFocused) "Очистить" else if (isLoading) "Остановить" else "Обновить",
                                tint = Color(0xFFE8A45C)
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Вперёд") }, enabled = canGoForward, onClick = { showMenu = false; webViewInstance?.goForward() })
                        DropdownMenuItem(text = { Text("Обновить") }, onClick = { showMenu = false; webViewInstance?.reload() })
                        DropdownMenuItem(
                            text = { Text("Копировать ссылку") },
                            onClick = {
                                showMenu = false
                                val url = webViewInstance?.url ?: currentUrl
                                if (url.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                                    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться") },
                            onClick = {
                                showMenu = false
                                val url = webViewInstance?.url ?: currentUrl
                                if (url.isNotBlank()) {
                                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }
                                    context.startActivity(Intent.createChooser(send, "Поделиться ссылкой"))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Открыть во внешнем браузере") },
                            onClick = {
                                showMenu = false
                                val url = webViewInstance?.url ?: currentUrl
                                if (url.isNotBlank()) runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure { Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
                            }
                        )
                        DropdownMenuItem(text = { Text("Выйти") }, onClick = { showMenu = false; onClose() })
                    }
                }
            }
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = Color(0xFFFF8A00),
                trackColor = Color(0xFF332820),
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    configureFullChromiumSettings(this)
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    setupDownloadListener(ctx, this)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            progress = 5
                            url?.let {
                                currentUrl = it
                                if (!isAddressFocused) addressValue = TextFieldValue(it)
                            }
                            updateNavigation(view)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            progress = 100
                            url?.let {
                                currentUrl = it
                                if (!isAddressFocused) addressValue = TextFieldValue(it)
                            }
                            updateNavigation(view)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            isLoading = newProgress < 100
                            updateNavigation(view)
                        }
                    }

                    loadUrl(initialUrl)
                    webViewInstance = this
                }
            },
            update = { webViewInstance = it },
            onRelease = { view ->
                if (webViewInstance === view) webViewInstance = null
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.removeAllViews()
                view.destroy()
            }
        )
    }
}
