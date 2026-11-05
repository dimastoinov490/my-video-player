package com.example.videoplayer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.videoplayer.service.CertificateManager
import java.security.MessageDigest

class BrowserActivity : FragmentActivity() {

    private lateinit var webView: WebView
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnExternal: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var ivLock: ImageView
    private lateinit var tvUrl: TextView
    private lateinit var progressBar: ProgressBar

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0F141C"))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 20, 16, 20)
            setBackgroundColor(android.graphics.Color.parseColor("#151D28"))
        }

        btnBack = createIconButton("◀", 40) {
            if (webView.canGoBack()) webView.goBack()
        }
        btnForward = createIconButton("▶", 40) {
            if (webView.canGoForward()) webView.goForward()
        }
        btnRefresh = createIconButton("⟳", 40) {
            webView.reload()
        }

        val urlBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(20, 10, 20, 10)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#0A0D12"))
                cornerRadius = 24f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
                marginEnd = 12
            }
        }

        ivLock = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(36, 36).apply { marginEnd = 12 }
            setColorFilter(android.graphics.Color.GRAY)
        }

        tvUrl = TextView(this).apply {
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        urlBox.addView(ivLock)
        urlBox.addView(tvUrl)

        btnExternal = createIconButton("↗", 40) {
            val curUrl = webView.url ?: intent.getStringExtra("url")
            if (!curUrl.isNullOrBlank()) {
                try {
                    val extIntent = Intent(Intent.ACTION_VIEW, Uri.parse(curUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(extIntent)
                } catch (e: Exception) {
                    Toast.makeText(this@BrowserActivity, "Не удалось открыть: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnClose = createIconButton("✕", 40) {
            finish()
        }

        topBar.addView(btnBack)
        topBar.addView(btnForward)
        topBar.addView(btnRefresh)
        topBar.addView(urlBox)
        topBar.addView(btnExternal)
        topBar.addView(btnClose)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                6
            )
            max = 100
            visibility = View.GONE
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(android.graphics.Color.parseColor("#0A0D12"))
        }

        root.addView(topBar)
        root.addView(progressBar)
        root.addView(webView)

        setContentView(root)

        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val startUrl = intent.getStringExtra("url") ?: "http://127.0.0.1:8765/"
        webView.loadUrl(startUrl)
    }

    private fun createIconButton(text: String, sizeDp: Int, onClick: () -> Unit): ImageButton {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val btn = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }
        val tv = TextView(this).apply {
            setText(text)
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#9AA4B2"))
            gravity = android.view.Gravity.CENTER
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        tv.layout(0, 0, sizePx, sizePx)
        tv.draw(canvas)
        btn.setImageBitmap(bmp)
        return btn
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                updateUrlBar(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                updateUrlBar(url)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (handler == null) return
                val serverCert = error?.certificate
                if (serverCert != null) {
                    val trustedFingerprint = CertificateManager.getFingerprint(this@BrowserActivity)
                    val errorFingerprint = extractCertFingerprint(serverCert)
                    if (trustedFingerprint.isNotBlank() && errorFingerprint.equals(trustedFingerprint, ignoreCase = true)) {
                        handler.proceed()
                        return
                    }
                }
                val url = error?.url ?: ""
                if (url.contains("192.168.") || url.contains("127.0.0.1") || url.contains("localhost")) {
                    handler.proceed()
                } else {
                    super.onReceivedSslError(view, handler, error)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val audioRequested = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (audioRequested) {
                    if (ContextCompat.checkSelfPermission(this@BrowserActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        pendingPermissionRequest = request
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    request.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@BrowserActivity.filePathCallback?.onReceiveValue(null)
                this@BrowserActivity.filePathCallback = filePathCallback

                val pickIntent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    filePickerLauncher.launch(pickIntent)
                    return true
                } catch (e: Exception) {
                    this@BrowserActivity.filePathCallback = null
                    return false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    setTitle(filename)
                    setDescription("Загрузка через Nexora...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Загрузка началась: ${URLUtil.guessFileName(url, contentDisposition, mimetype)}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUrlBar(url: String?) {
        val u = url ?: ""
        tvUrl.text = u
        val isHttps = u.startsWith("https://", ignoreCase = true)
        val lockColor = if (isHttps) android.graphics.Color.parseColor("#4ECCA3") else android.graphics.Color.parseColor("#8E99A8")
        ivLock.setColorFilter(lockColor)
        btnBack.alpha = if (webView.canGoBack()) 1.0f else 0.4f
        btnForward.alpha = if (webView.canGoForward()) 1.0f else 0.4f
    }

    private fun extractCertFingerprint(sslCert: SslCertificate): String {
        return try {
            val bundle = SslCertificate.saveState(sslCert)
            val bytes = bundle.getByteArray("x509-certificate") ?: return ""
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(bytes).joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
