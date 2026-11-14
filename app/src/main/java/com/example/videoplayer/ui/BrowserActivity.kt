package com.example.videoplayer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.example.videoplayer.R
import com.example.videoplayer.service.CertificateManager
import java.net.URLEncoder
import java.security.MessageDigest

class BrowserActivity : FragmentActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var urlCapsule: LinearLayout
    private lateinit var ivStatus: ImageView
    private lateinit var etUrl: EditText
    private lateinit var btnPillAction: ImageView
    private lateinit var btnMore: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var webView: WebView
    private lateinit var customViewContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var currentUrl: String = ""
    private var isLoading: Boolean = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val req = pendingPermissionRequest
        pendingPermissionRequest = null
        if (req != null) {
            if (isGranted) {
                req.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                req.deny()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationHelper.apply(this)

        // Status bar matching theme #120A05 with light status bar icons
        window.statusBarColor = Color.parseColor("#120A05")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val density = resources.displayMetrics.density

        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#120A05"))
        }

        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#120A05"))
        }

        // Top bar: height 56dp, color #120A05
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#120A05"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * density).toInt()
            )
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
        }

        // Left button: Back / Close arrow (touch target 48x48dp)
        btnBack = ImageButton(this).apply {
            setImageResource(R.drawable.ic_browser_back)
            background = createRippleDrawable(Color.TRANSPARENT, Color.parseColor("#33FFFFFF"), (24 * density).toInt())
            layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt())
            contentDescription = "Назад"
            setOnClickListener {
                if (etUrl.hasFocus()) {
                    etUrl.clearFocus()
                    hideKeyboard()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        }
        topBar.addView(btnBack)

        // Center: Address bar capsule (pill): height 40dp, radius 24dp, fill #1F1A17, h-pad 14dp, no border
        urlCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F1A17"))
                cornerRadius = 24 * density
            }
            val padH = (14 * density).toInt()
            setPadding(padH, 0, padH, 0)
            layoutParams = LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (4 * density).toInt()
            }
        }

        // Status icon on left inside pill (20x20 dp)
        ivStatus = ImageView(this).apply {
            setImageResource(R.drawable.ic_browser_lock)
            layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()).apply {
                marginEnd = (8 * density).toInt()
            }
            contentDescription = "Статус соединения"
        }
        urlCapsule.addView(ivStatus)

        // Address bar text / input: 15 sp, single line, ellipsize end
        etUrl = EditText(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#9D8F82"))
            hint = "Поиск или веб-адрес"
            background = null
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        urlCapsule.addView(etUrl)

        // Right button inside pill: refresh / stop (unfocused) or clear 'X' (focused)
        btnPillAction = ImageView(this).apply {
            setImageResource(R.drawable.ic_browser_refresh)
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply {
                marginStart = (6 * density).toInt()
            }
            contentDescription = "Действие с адресом"
            setOnClickListener {
                if (etUrl.hasFocus()) {
                    etUrl.setText("")
                } else {
                    if (isLoading) {
                        webView.stopLoading()
                    } else {
                        webView.reload()
                    }
                }
            }
        }
        urlCapsule.addView(btnPillAction)
        topBar.addView(urlCapsule)

        // Three dots menu button (touch target 48x48 dp)
        btnMore = ImageButton(this).apply {
            setImageResource(R.drawable.ic_browser_more)
            background = createRippleDrawable(Color.TRANSPARENT, Color.parseColor("#33FFFFFF"), (24 * density).toInt())
            layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt())
            contentDescription = "Меню"
            setOnClickListener { showBrowserMenu(it) }
        }
        topBar.addView(btnMore)

        mainContent.addView(topBar)

        // Progress bar: thin 3dp line under top bar (#FF8A00)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (3 * density).toInt()
            )
            max = 100
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#FF8A00"))
            }
            visibility = View.GONE
        }
        mainContent.addView(progressBar)

        // WebView
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.parseColor("#120A05"))
        }
        setupWebView()
        mainContent.addView(webView)

        rootContainer.addView(mainContent)

        // Fullscreen video container overlay
        customViewContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        rootContainer.addView(customViewContainer)

        setContentView(rootContainer)

        setupUrlBarLogic()

        // Handle Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                } else if (etUrl.hasFocus()) {
                    etUrl.clearFocus()
                    hideKeyboard()
                    updateUrlDisplay(currentUrl)
                } else if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        })

        // Initial URL loading
        val initialUrl = intent.getStringExtra("url") ?: "https://www.google.com"
        loadAddress(initialUrl)
    }

    private fun setupUrlBarLogic() {
        etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Address bar focused: shows full URL, selects all, keyboard open, shows 'X' button
                etUrl.setText(currentUrl)
                etUrl.selectAll()
                ivStatus.setImageResource(R.drawable.ic_browser_search)
                btnPillAction.setImageResource(R.drawable.ic_browser_close)
                btnPillAction.contentDescription = "Очистить"
            } else {
                hideKeyboard()
                updateUrlDisplay(currentUrl)
                updateActionButtons()
            }
        }

        etUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val input = etUrl.text.toString().trim()
                etUrl.clearFocus()
                hideKeyboard()
                if (input.isNotEmpty()) {
                    loadAddress(input)
                }
                true
            } else {
                false
            }
        }
    }

    private fun loadAddress(rawInput: String) {
        val trimmed = rawInput.trim()
        val urlToLoad = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                trimmed
            }
            isLocalHost(trimmed) -> {
                "http://$trimmed"
            }
            isValidHostOrUrl(trimmed) -> {
                "https://$trimmed"
            }
            else -> {
                "https://www.google.com/search?q=" + try {
                    URLEncoder.encode(trimmed, "UTF-8")
                } catch (_: Exception) {
                    trimmed
                }
            }
        }
        webView.loadUrl(urlToLoad)
    }

    private fun isLocalHost(input: String): Boolean {
        val host = input.split("/")[0].split(":")[0].lowercase()
        return host == "localhost" ||
                host == "nexora.local" ||
                host.endsWith(".local") ||
                host.startsWith("192.168.") ||
                host.startsWith("10.") ||
                host.startsWith("127.") ||
                (host.startsWith("172.") && isPrivate172(host))
    }

    private fun isPrivate172(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size >= 2) {
            val second = parts[1].toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }

    private fun isValidHostOrUrl(input: String): Boolean {
        if (input.contains(" ")) return false
        val domain = input.split("/")[0].split(":")[0]
        if (Patterns.IP_ADDRESS.matcher(domain).matches()) return true
        if (Patterns.DOMAIN_NAME.matcher(domain).matches()) return true
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".")
    }

    private fun updateUrlDisplay(url: String) {
        currentUrl = url
        if (etUrl.hasFocus()) return

        if (url.isBlank()) {
            etUrl.setText("")
            ivStatus.setImageResource(R.drawable.ic_browser_search)
            return
        }

        try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host ?: url
            val port = if (uri.port != -1) ":${uri.port}" else ""

            if (scheme == "https") {
                ivStatus.setImageResource(R.drawable.ic_browser_lock)
            } else if (scheme == "http") {
                ivStatus.setImageResource(R.drawable.ic_browser_unsecure)
            } else {
                ivStatus.setImageResource(R.drawable.ic_browser_search)
            }

            // Unfocused: shows only domain or IP with port (white domain, #9D8F82 port/rest, 15 sp)
            val fullText = "$host$port"
            val span = SpannableString(fullText)
            span.setSpan(ForegroundColorSpan(Color.WHITE), 0, host.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (port.isNotEmpty()) {
                span.setSpan(
                    ForegroundColorSpan(Color.parseColor("#9D8F82")),
                    host.length,
                    fullText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            etUrl.setText(span)
        } catch (_: Exception) {
            etUrl.setText(url)
        }
    }

    private fun updateActionButtons() {
        if (etUrl.hasFocus()) {
            btnPillAction.setImageResource(R.drawable.ic_browser_close)
            btnPillAction.contentDescription = "Очистить"
        } else if (isLoading) {
            btnPillAction.setImageResource(R.drawable.ic_browser_close)
            btnPillAction.contentDescription = "Остановить"
        } else {
            btnPillAction.setImageResource(R.drawable.ic_browser_refresh)
            btnPillAction.contentDescription = "Обновить"
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etUrl.windowToken, 0)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        configureFullChromiumSettings(webView)
        webView.settings.userAgentString = webView.settings.userAgentString.replace("; wv", "")

        setupDownloadListener(this, webView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 5
                if (url != null) {
                    updateUrlDisplay(url)
                }
                updateActionButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                progressBar.visibility = View.GONE
                if (url != null) {
                    updateUrlDisplay(url)
                }
                updateActionButtons()
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (handler == null) return
                val cert = error?.certificate
                if (cert != null) {
                    try {
                        val bundle = SslCertificate.saveState(cert)
                        val bytes: ByteArray? = bundle.getByteArray("x509-certificate")
                        if (bytes != null) {
                            val md = MessageDigest.getInstance("SHA-256")
                            val digest = md.digest(bytes)
                            val fp = digest.joinToString(":") { String.format("%02X", it) }
                            val myFp = CertificateManager.getFingerprint(this@BrowserActivity)
                            if (myFp.isNotBlank() && fp.equals(myFp, ignoreCase = true)) {
                                handler.proceed()
                                return
                            }
                        }
                    } catch (_: Exception) {}
                }
                super.onReceivedSslError(view, handler, error)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    updateActionButtons()
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setTaskDescription(ActivityManager.TaskDescription(title))
                    }
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }
                customView = view
                customViewCallback = callback
                topBar.visibility = View.GONE
                webView.visibility = View.GONE
                customViewContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                customViewContainer.visibility = View.VISIBLE

                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }

            override fun onHideCustomView() {
                hideCustomView()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@BrowserActivity.filePathCallback?.onReceiveValue(null)
                this@BrowserActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    filePickerLauncher.launch(intent)
                } catch (_: Exception) {
                    this@BrowserActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    pendingPermissionRequest = request
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    request.deny()
                }
            }
        }
    }

    private fun hideCustomView() {
        if (customView == null) return
        customViewContainer.removeView(customView)
        customView = null
        customViewContainer.visibility = View.GONE
        topBar.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun showBrowserMenu(anchor: View) {
        val density = resources.displayMetrics.density
        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1310"))
                cornerRadius = 16 * density
            }
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            layoutParams = ViewGroup.LayoutParams(
                (240 * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val popupWindow = PopupWindow(
            popupView,
            (240 * density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16 * density
            setBackgroundDrawable(null)
        }

        val canForward = webView.canGoForward()
        addMenuItem(popupView, "Вперёд", enabled = canForward) {
            popupWindow.dismiss()
            if (webView.canGoForward()) webView.goForward()
        }
        addMenuItem(popupView, "Обновить") {
            popupWindow.dismiss()
            webView.reload()
        }
        addMenuItem(popupView, "Копировать ссылку") {
            popupWindow.dismiss()
            val url = webView.url ?: currentUrl
            if (url.isNotBlank()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("URL", url))
                Toast.makeText(this@BrowserActivity, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
            }
        }
        addMenuItem(popupView, "Поделиться") {
            popupWindow.dismiss()
            val url = webView.url ?: currentUrl
            if (url.isNotBlank()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                startActivity(Intent.createChooser(shareIntent, "Поделиться ссылкой"))
            }
        }
        addMenuItem(popupView, "Открыть во внешнем браузере") {
            popupWindow.dismiss()
            val url = webView.url ?: currentUrl
            if (url.isNotBlank()) {
                try {
                    val extIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(extIntent)
                } catch (e: Exception) {
                    Toast.makeText(this@BrowserActivity, "Не удалось открыть: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        addMenuItem(popupView, "Выйти") {
            popupWindow.dismiss()
            finish()
        }

        popupWindow.showAsDropDown(anchor, (-180 * density).toInt(), 0)
    }

    private fun addMenuItem(
        container: LinearLayout,
        title: String,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(if (enabled) Color.WHITE else Color.parseColor("#66FFFFFF"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            )
            background = if (enabled) {
                createRippleDrawable(Color.TRANSPARENT, Color.parseColor("#26FFFFFF"), 0)
            } else null
            isEnabled = enabled
            if (enabled) {
                setOnClickListener { onClick() }
            }
        }
        container.addView(tv)
    }

    private fun createRippleDrawable(normalColor: Int, rippleColor: Int, cornerRadiusPx: Int): RippleDrawable {
        val content = GradientDrawable().apply {
            setColor(normalColor)
            if (cornerRadiusPx > 0) cornerRadius = cornerRadiusPx.toFloat()
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            if (cornerRadiusPx > 0) cornerRadius = cornerRadiusPx.toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
fun configureFullChromiumSettings(webView: WebView) {
    with(webView.settings) {
        // Производительность и движок
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        // Do not allow scripts to spawn unsolicited windows.
        javaScriptCanOpenWindowsAutomatically = false

        // Отрендерить страницы как на ПК/телефоне с полным масштабом
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false

        // Кэш и работа в офлайне
        cacheMode = WebSettings.LOAD_DEFAULT

        // Безопасность и контент
        // HTTPS pages must not silently load insecure HTTP subresources.
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // The browser does not need direct file:// or content:// access for normal browsing.
        allowFileAccess = false
        allowContentAccess = false

        // Мультимедиа
        mediaPlaybackRequiresUserGesture = false

        // Агент (чтобы сайты отдавали полную версию)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }
    }
    // Аппаратное ускорение рендеринга (как в чистом Chrome)
    webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
}

@Suppress("DEPRECATION")
fun setupDownloadListener(context: Context, webView: WebView) {
    webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setTitle(fileName)
                setDescription("Загрузка файла...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    allowScanningByMediaScanner()
                }
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "Загрузка файла начата: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка скачивания: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
