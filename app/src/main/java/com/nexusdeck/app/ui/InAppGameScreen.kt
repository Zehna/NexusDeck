package com.nexusdeck.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Base64
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexusdeck.app.core.downloader.RealGameDownloader
import com.nexusdeck.app.core.engine.EmulatorJsEngine
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.ui.components.platformBadgeColor
import com.nexusdeck.app.ui.controls.VirtualGamepad
import com.nexusdeck.app.ui.controls.VirtualGamepadButton
import com.nexusdeck.app.ui.theme.NexusPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * In-app GBA player powered by the real IodineGBA core (vendored in
 * app/src/main/assets/retro/iodine/, loaded from retro_player.html).
 *
 * v1.1.5 replaces the fake canvas engine: the WebView now loads the real
 * player page with loadUrl(), and the ROM is read from disk in Kotlin and
 * injected in 256 KiB Base64 chunks via window.NexusIodine — never as one
 * giant evaluateJavascript string (Binder limit) and never embedded in
 * generated HTML.
 *
 * Known limitations: no audio (XAudioJS is an empty submodule upstream),
 * saves live only in memory, and upstream IodineGBA cannot initialise
 * without a valid 16 KiB user-supplied BIOS — the page reports that
 * honestly in #nexus-status instead of drawing a fake frame.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppGameScreen(
    game: GameItem,
    romPath: String,
    engineConfig: EngineConfig,
    onExitToDashboard: () -> Unit,
    onLaunchExternalEmulator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }
    var isHudVisible by remember { mutableStateOf(true) }
    var romError by remember { mutableStateOf<String?>(null) }
    var romLoading by remember { mutableStateOf(true) }
    var romInjected by remember { mutableStateOf(false) }
    var injectionStarted by remember { mutableStateOf(false) }

    // Reference to the WebView, for JS calls from the Compose gamepad.
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    // GBA direction keys currently held down via the analog stick.
    val pressedDirections = remember { mutableStateOf<Set<Int>>(emptySet()) }

    fun js(script: String) {
        webViewHolder.value?.evaluateJavascript(script, null)
    }

    fun jsKey(gbaIndex: Int, down: Boolean) {
        if (gbaIndex < 0) return
        val call = if (down) "keyDown" else "keyUp"
        js("if(window.NexusIodine){window.NexusIodine.$call($gbaIndex)}")
    }

    /** Apply a new set of held direction keys, releasing keys that left it. */
    fun applyDirections(next: Set<Int>) {
        val current = pressedDirections.value
        (current - next).forEach { jsKey(it, down = false) }
        (next - current).forEach { jsKey(it, down = true) }
        pressedDirections.value = next
    }

    fun injectRom(webView: WebView) {
        if (injectionStarted) return
        injectionStarted = true
        scope.launch {
            val error: String?
            val bytes: ByteArray?
            val ext = romPath.substringAfterLast(".", "").lowercase(Locale.ROOT)
            if (!EmulatorJsEngine.isSupportedInAppRom(romPath)) {
                error = "ROM .$ext tidak didukung in-app di v1.1.5 — hanya .gba/.agb " +
                    "(IodineGBA). Buka game ini di engine eksternal."
                bytes = null
            } else {
                val file = File(romPath)
                val read = withContext(Dispatchers.IO) {
                    runCatching { if (file.exists() && file.isFile) file.readBytes() else null }.getOrNull()
                }
                when {
                    read == null -> {
                        error = "File ROM tidak ditemukan: $romPath"
                        bytes = null
                    }
                    !RealGameDownloader.isRomBinaryValid(read) -> {
                        error = "File ROM tidak valid (terdeteksi sebagai halaman HTML redirect)."
                        bytes = null
                    }
                    read.size < EmulatorJsEngine.MIN_GBA_ROM_BYTES -> {
                        error = "Download gagal: File corrupt/dummy " +
                            "(ROM GBA hanya ${read.size} byte, minimum 256 KiB)."
                        bytes = null
                    }
                    else -> {
                        error = null
                        bytes = read
                    }
                }
            }

            if (error != null || bytes == null) {
                romError = error
                romLoading = false
                return@launch
            }

            // Probe that the Iodine glue actually loaded before sending data.
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    "(typeof window.NexusIodine !== 'undefined')",
                    ValueCallback { probe ->
                        if (probe != "true") {
                            romError = "Core IodineGBA gagal dimuat dari assets."
                            romLoading = false
                            return@ValueCallback
                        }
                        val safeTitle = game.title
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", " ")
                        val chunkBytes = 256 * 1024
                        val totalChunks = (bytes.size + chunkBytes - 1) / chunkBytes
                        fun sendChunk(index: Int) {
                            if (index >= totalChunks) {
                                webView.evaluateJavascript(
                                    "window.NexusIodine.finishRomLoad('$safeTitle')",
                                    null
                                )
                                romInjected = true
                                romLoading = false
                                return
                            }
                            val start = index * chunkBytes
                            val end = minOf(start + chunkBytes, bytes.size)
                            val b64 = Base64.encodeToString(bytes, start, end - start, Base64.NO_WRAP)
                            webView.evaluateJavascript(
                                "window.NexusIodine.appendRomChunk('$b64')",
                                ValueCallback { sendChunk(index + 1) }
                            )
                        }
                        sendChunk(0)
                    }
                )
            }
        }
    }

    DisposableEffect(game, romPath) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    BackHandler {
        showExitDialog = true
    }

    val platformAccent = platformBadgeColor(game.platform)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Real IodineGBA player page, loaded from assets (not generated HTML).
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            if (!romInjected) injectRom(view)
                        }
                    }
                    webViewHolder.value = this
                    loadUrl(EmulatorJsEngine.IODINE_PLAYER_URL)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Compose VirtualGamepad overlay, wired to IodineGBA JoyPad keys.
        // GBA indices: 0=A, 1=B, 2=Select, 3=Start, 4=Right, 5=Left,
        // 6=Up, 7=Down, 8=R, 9=L. X/Y are alternates for B/A.
        if (romError == null) {
            VirtualGamepad(
                onJoystickMove = { x, y ->
                    val next = buildSet {
                        if (x > 0.4f) add(EmulatorJsEngine.gbaKeyIndex("RIGHT"))
                        if (x < -0.4f) add(EmulatorJsEngine.gbaKeyIndex("LEFT"))
                        if (y > 0.4f) add(EmulatorJsEngine.gbaKeyIndex("DOWN"))
                        if (y < -0.4f) add(EmulatorJsEngine.gbaKeyIndex("UP"))
                    }
                    applyDirections(next)
                },
                onButtonPress = { button, isPressed ->
                    val gbaIndex = when (button) {
                        VirtualGamepadButton.A -> EmulatorJsEngine.gbaKeyIndex("A")
                        VirtualGamepadButton.B -> EmulatorJsEngine.gbaKeyIndex("B")
                        VirtualGamepadButton.X -> EmulatorJsEngine.gbaKeyIndex("B")
                        VirtualGamepadButton.Y -> EmulatorJsEngine.gbaKeyIndex("A")
                        VirtualGamepadButton.L1 -> EmulatorJsEngine.gbaKeyIndex("L")
                        VirtualGamepadButton.R1 -> EmulatorJsEngine.gbaKeyIndex("R")
                    }
                    jsKey(gbaIndex, isPressed)
                },
                onPauseMenuClick = {
                    js("if(window.NexusIodine){window.NexusIodine.pause()}")
                    showExitDialog = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Loading / error state — a real message, never a fake canvas.
        if (romLoading || romError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = romError ?: "Memuat ${game.title} ke IodineGBA...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = if (romError != null) Color(0xFFF87171) else Color.White,
                        textAlign = TextAlign.Center
                    )
                    if (romError == null) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "IodineGBA • Audio nonaktif di v1.1.5 • Saves hanya di memori",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                    if (romError != null) {
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(
                            onClick = onExitToDashboard,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Keluar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Floating Minimal HUD Overlay Bar (Dapat di-toggle oleh pengguna)
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xCC090A0F),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Tombol Keluar Cepat
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x22FFFFFF),
                            modifier = Modifier.size(32.dp)
                        ) {
                            IconButton(
                                onClick = { showExitDialog = true },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit Game",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = platformAccent.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = game.platform.displayName.uppercase(Locale.ROOT),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        ),
                                        color = platformAccent,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Core: IODINEGBA • Audio off",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // Aksi Handoff ke Emulator Luar & Sembunyikan HUD
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onLaunchExternalEmulator,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x22FFFFFF),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = platformAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Buka di Engine",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(
                            onClick = { isHudVisible = false },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "Sembunyikan",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // Tombol Mikro untuk Memunculkan Kembali HUD jika Disembunyikan
        if (!isHudVisible) {
            Surface(
                shape = CircleShape,
                color = Color(0x66000000),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(30.dp)
            ) {
                IconButton(
                    onClick = { isHudVisible = true },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Show Menu",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 5. Exit Confirmation Dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                containerColor = NexusPalette.SurfaceCard,
                title = {
                    Text(
                        text = "Keluar dari Game?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Progress game saat ini akan dihentikan. Saves di v1.1.5 hanya " +
                            "tersimpan di memori dan akan hilang saat keluar.",
                        color = Color(0xFFCCCCCC)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            onExitToDashboard()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Keluar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        js("if(window.NexusIodine){window.NexusIodine.resume()}")
                    }) {
                        Text("Lanjutkan Main", color = Color.White)
                    }
                }
            )
        }
    }
}
