package com.nexusdeck.app.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexusdeck.app.core.downloader.SniffedDownload
import com.nexusdeck.app.core.downloader.WebAdBlocker
import com.nexusdeck.app.core.downloader.WebDownloadSniffer
import com.nexusdeck.app.ui.theme.NexusPalette
import java.net.URLEncoder

val BrowserForwardIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BrowserForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val path = PathBuilder().apply {
            moveTo(12f, 4f)
            lineTo(10.59f, 5.41f)
            lineTo(16.17f, 11f)
            horizontalLineTo(4f)
            verticalLineTo(13f)
            horizontalLineTo(16.17f)
            lineTo(10.59f, 18.59f)
            lineTo(12f, 20f)
            lineTo(20f, 12f)
            close()
        }.getNodes()
        addPath(path, fill = SolidColor(Color.White))
    }.build()
}

val BrowserRefreshIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BrowserRefresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val path = PathBuilder().apply {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            close()
        }.getNodes()
        addPath(path, fill = SolidColor(Color.White))
    }.build()
}

val BrowserLockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BrowserLock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val path = PathBuilder().apply {
            moveTo(18f, 8f)
            horizontalLineTo(17f)
            verticalLineTo(6f)
            curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
            curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(6f)
            curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(10f)
            curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
            close()
            moveTo(9f, 6f)
            curveTo(9f, 4.34f, 10.34f, 3f, 12f, 3f)
            curveTo(13.66f, 3f, 15f, 4.34f, 15f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(9f)
            verticalLineTo(6f)
            close()
        }.getNodes()
        addPath(path, fill = SolidColor(Color.White))
    }.build()
}

data class RomBookmark(
    val name: String,
    val url: String,
    val categoryBadge: String
)

val DEFAULT_ROM_BOOKMARKS = listOf(
    RomBookmark(
        name = "Myrient",
        url = "https://myrient.erista.me/files/",
        categoryBadge = "Verified No-Intro & Redump"
    ),
    RomBookmark(
        name = "CDRomance",
        url = "https://cdromance.org/",
        categoryBadge = "PS2 / PSP / Translations"
    ),
    RomBookmark(
        name = "Vimm's Lair",
        url = "https://vimm.net/vault/",
        categoryBadge = "Emulation Vault"
    ),
    RomBookmark(
        name = "NoPayStation",
        url = "https://nopaystation.com/",
        categoryBadge = "PS3 & PSP Direct PKG"
    ),
    RomBookmark(
        name = "Archive.org",
        url = "https://archive.org/details/software",
        categoryBadge = "Internet Archive Software"
    )
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NexusWebBrowser(
    onDownloadIntercepted: (SniffedDownload) -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String = "https://myrient.erista.me/files/"
) {
    val focusManager = LocalFocusManager.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrlText by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Web Explorer") }
    var pageProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var blockedAdCount by remember { mutableIntStateOf(0) }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun navigateTo(destination: String) {
        val trimmed = destination.trim()
        val finalUrl = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        inputUrlText = finalUrl
        webViewRef?.loadUrl(finalUrl)
        focusManager.clearFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusPalette.DeepDarkBlack)
    ) {
        // 1. Quick ROM Bookmark Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DEFAULT_ROM_BOOKMARKS.forEach { bookmark ->
                val isCurrent = currentUrl.contains(bookmark.name.lowercase())
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) NexusPalette.PrimaryViolet.copy(alpha = 0.25f) else NexusPalette.SurfaceCard,
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) NexusPalette.PrimaryViolet else NexusPalette.CardBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { navigateTo(bookmark.url) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) NexusPalette.SecondaryPurple else NexusPalette.TextMuted)
                        )
                        Text(
                            text = bookmark.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isCurrent) Color.White else NexusPalette.TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Browser Navigation & URL Input Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (webViewRef?.canGoBack() == true) {
                                webViewRef?.goBack()
                            }
                        },
                        enabled = canGoBack,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) Color.White else NexusPalette.TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (webViewRef?.canGoForward() == true) {
                                webViewRef?.goForward()
                            }
                        },
                        enabled = canGoForward,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = BrowserForwardIcon,
                            contentDescription = "Forward",
                            tint = if (canGoForward) Color.White else NexusPalette.TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isLoading) webViewRef?.stopLoading()
                            else webViewRef?.reload()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoading) Icons.Default.Close else BrowserRefreshIcon,
                            contentDescription = if (isLoading) "Stop" else "Reload",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { navigateTo(initialUrl) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home ROM Bookmark",
                            tint = NexusPalette.SecondaryPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Kolom Teks URL / Search Bar
                    OutlinedTextField(
                        value = inputUrlText,
                        onValueChange = { inputUrlText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontSize = 12.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF13121A),
                            unfocusedContainerColor = Color(0xFF13121A),
                            focusedBorderColor = NexusPalette.PrimaryViolet,
                            unfocusedBorderColor = NexusPalette.CardBorder,
                            cursorColor = NexusPalette.SecondaryPurple
                        ),
                        leadingIcon = {
                            val isHttps = currentUrl.startsWith("https://", ignoreCase = true)
                            Icon(
                                imageVector = if (isHttps) BrowserLockIcon else Icons.Default.Search,
                                contentDescription = "Security",
                                tint = if (isHttps) Color(0xFF10B981) else NexusPalette.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { navigateTo(inputUrlText) }
                        )
                    )
                }

                // Status Bar kecil: Judul Halaman & Indikator AdBlock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = NexusPalette.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NexusPalette.DeepDarkBlack.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, NexusPalette.CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🛡️ AdBlock: $blockedAdCount diblokir",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }
            }
        }

        // 3. Web Loading Progress Bar
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = NexusPalette.SecondaryPurple,
                trackColor = Color.Transparent
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. AndroidView hosting the WebView with Sniffer & AdBlocker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color(0xFF13121A))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false

                            // Mencegah pop-up tab iklan otomatis
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false

                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 NexusDeck/3.0"
                        }

                        // Mengaktifkan cookie support
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        // 1. DownloadListener untuk menangkap event unduhan
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                            val sniffed = WebDownloadSniffer.sniffDownload(
                                url = url,
                                userAgent = userAgent,
                                contentDisposition = contentDisposition,
                                mimeType = mimetype,
                                contentLength = contentLength,
                                cookies = CookieManager.getInstance().getCookie(url)
                            )
                            if (sniffed != null) {
                                onDownloadIntercepted(sniffed)
                            }
                        }

                        // 2. WebChromeClient untuk pembacaan progress & title
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }
                        }

                        // 3. WebViewClient dengan AdBlocker & Game URL Sniffer
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let {
                                    currentUrl = it
                                    inputUrlText = it
                                }
                                canGoBack = canGoBack()
                                canGoForward = canGoForward()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                url?.let {
                                    currentUrl = it
                                    inputUrlText = it
                                }
                                canGoBack = canGoBack()
                                canGoForward = canGoForward()
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString().orEmpty()
                                if (WebAdBlocker.isAdUrl(reqUrl)) {
                                    post { blockedAdCount++ }
                                    return WebAdBlocker.createEmptyResourceResponse()
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString().orEmpty()

                                // Cek apakah URL ini merupakan file game (.iso, .cso, .zip, dll.)
                                if (WebDownloadSniffer.isGameDownload(reqUrl)) {
                                    val sniffed = WebDownloadSniffer.sniffDownload(
                                        url = reqUrl,
                                        userAgent = settings.userAgentString,
                                        cookies = CookieManager.getInstance().getCookie(reqUrl)
                                    )
                                    if (sniffed != null) {
                                        onDownloadIntercepted(sniffed)
                                        return true // Tahan navigasi browser
                                    }
                                }

                                // Blokir navigasi ke domain iklan atau skema popup mencurigakan
                                if (WebAdBlocker.isAdUrl(reqUrl)) {
                                    post { blockedAdCount++ }
                                    return true
                                }

                                if (!reqUrl.startsWith("http://", ignoreCase = true) &&
                                    !reqUrl.startsWith("https://", ignoreCase = true)
                                ) {
                                    return true // Blokir skema eksternal (intent://, market://)
                                }

                                return false
                            }
                        }

                        loadUrl(initialUrl)
                    }
                },
                update = { webView ->
                    webViewRef = webView
                }
            )
        }
    }
}
