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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RomBookmark(
    val name: String,
    val url: String,
    val categoryBadge: String
)

// Hanya situs yang terverifikasi HIDUP (cek live 10 Nov 2026) yang ditampilkan,
// langsung ke halaman katalog game-nya — bukan homepage. Yang MATI sudah dihapus:
// CDRomance (DNS gagal, down sejak ~31 Okt 2026) dan Myrient (tutup permanen
// 31 Mar 2026, yang tersisa hanya halaman perpisahan).
val DEFAULT_ROM_BOOKMARKS = listOf(
    RomBookmark(
        name = "Vimm's Lair",
        url = "https://vimm.net/vault/PS1",
        categoryBadge = "Vault PS1 / PSP / GBA / 3DS"
    ),
    RomBookmark(
        name = "NoPayStation",
        url = "https://nopaystation.com/browse",
        categoryBadge = "PS3 & PSP Direct PKG"
    ),
    RomBookmark(
        name = "RomsFun",
        url = "https://romsfun.com/roms/",
        categoryBadge = "Login Google diperlukan"
    ),
    RomBookmark(
        name = "Archive.org",
        url = "https://archive.org/details/software",
        categoryBadge = "Internet Archive Software"
    )
)

private enum class GameSearchSystem(val label: String, val vimmCode: String?) {
    PS1("PS1", "PS1"),
    PSP("PSP", "PSP"),
    GBA("GBA", "GBA"),
    NDS("NDS", null),
    THREE_DS("3DS", null)
}

private data class GameSearchResult(
    val title: String,
    val url: String,
    val detail: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NexusWebBrowser(
    onDownloadIntercepted: (SniffedDownload) -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String = "https://vimm.net/vault/PS1"
) {
    val focusManager = LocalFocusManager.current
    val searchScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrlText by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Web Explorer") }
    var pageProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var blockedAdCount by remember { mutableIntStateOf(0) }
    var gameQuery by remember { mutableStateOf("") }
    var selectedGameSystem by remember { mutableStateOf(GameSearchSystem.PS1) }
    var gameSearchResults by remember { mutableStateOf<List<GameSearchResult>>(emptyList()) }
    var isSearchingGames by remember { mutableStateOf(false) }
    val searchCache = remember { LinkedHashMap<String, List<GameSearchResult>>() }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun searchGames(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || isSearchingGames) return
        gameQuery = trimmed
        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val system = selectedGameSystem
        val cacheKey = "${system.name}:$trimmed"
        val vimmUrl = system.vimmCode?.let { code ->
            "https://vimm.net/vault/?p=list&system=$code&q=$encodedQuery"
        } ?: "https://vimm.net/vault/?p=search"
        val immediateResults = listOf(
            GameSearchResult("Vimm's Lair", vimmUrl, "Hasil pencarian ${system.label}"),
            GameSearchResult("RomsFun", "https://romsfun.com/?s=$encodedQuery", "Hasil web"),
            GameSearchResult(
                "NoPayStation — tidak ada pencarian",
                "https://nopaystation.com/browse",
                "Buka halaman browse"
            )
        )
        val cachedResults = searchCache[cacheKey]
        if (cachedResults != null) {
            gameSearchResults = cachedResults
            return
        }

        gameSearchResults = immediateResults
        isSearchingGames = true
        searchScope.launch {
            val archiveResults = withContext(Dispatchers.IO) {
                fetchArchiveResults(trimmed)
            }
            val combinedResults = buildList {
                addAll(immediateResults)
                addAll(archiveResults)
            }
            if (searchCache.size >= 10) {
                searchCache.remove(searchCache.keys.first())
            }
            searchCache[cacheKey] = combinedResults
            gameSearchResults = combinedResults
            isSearchingGames = false
        }
    }

    fun navigateTo(destination: String) {
        val trimmed = destination.trim()
        val finalUrl = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> {
                searchGames(trimmed)
                return
            }
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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = gameQuery,
                        onValueChange = { gameQuery = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Cari game di situs terverifikasi") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF13121A),
                            unfocusedContainerColor = Color(0xFF13121A),
                            focusedBorderColor = NexusPalette.PrimaryViolet,
                            unfocusedBorderColor = NexusPalette.CardBorder,
                            cursorColor = NexusPalette.SecondaryPurple
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari game",
                                tint = NexusPalette.SecondaryPurple
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { searchGames(gameQuery) }
                        )
                    )
                    IconButton(onClick = { searchGames(gameQuery) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GameSearchSystem.values().forEach { system ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedGameSystem == system) {
                                NexusPalette.PrimaryViolet
                            } else {
                                NexusPalette.DeepDarkBlack
                            },
                            border = BorderStroke(1.dp, NexusPalette.CardBorder),
                            modifier = Modifier.clickable { selectedGameSystem = system }
                        ) {
                            Text(
                                text = system.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSearchingGames) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = NexusPalette.SecondaryPurple
                    )
                }
                if (gameSearchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        gameSearchResults.forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { navigateTo(result.url) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = result.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusPalette.TextSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Buka hasil",
                                    tint = NexusPalette.SecondaryPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

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
                            imageVector = Icons.Default.ArrowForward,
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
                            imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
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
                                imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Search,
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
                            cacheMode = WebSettings.LOAD_DEFAULT

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

                    }
                },
                update = { webView ->
                    webViewRef = webView
                }
            )
        }
    }
}

private fun fetchArchiveResults(query: String): List<GameSearchResult> {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val apiUrl = "https://archive.org/advancedsearch.php?q=title%3A($encodedQuery)" +
        "&fl%5B%5D=identifier&fl%5B%5D=title&rows=10&output=json"
    return try {
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        connection.inputStream.bufferedReader().use { reader ->
            val docs = JSONObject(reader.readText())
                .optJSONObject("response")
                ?.optJSONArray("docs")
                ?: return emptyList()
            buildList {
                for (index in 0 until docs.length()) {
                    val item = docs.optJSONObject(index) ?: continue
                    val identifier = item.optString("identifier")
                    if (identifier.isBlank()) continue
                    val title = item.optString("title", identifier)
                    add(
                        GameSearchResult(
                            title = "Archive.org — $title",
                            url = "https://archive.org/details/$identifier",
                            detail = "Halaman detail hasil Archive.org"
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
