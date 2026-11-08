package com.nexusdeck.app.core.engine

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import java.io.File
import java.util.Base64
import java.util.Locale

/**
 * Real In-App Embedded Emulator Engine yang ditenagai oleh runtime retro offline mandiri (HTML5/Canvas).
 * 100% Bebas dari dependensi CDN Cloudflare eksternal (cdn.emulatorjs.org) yang menyebabkan error 'site owner'.
 * Berjalan sepenuhnya dari assets lokal aplikasi: file:///android_asset/retro/
 */
object EmulatorJsEngine {

    const val OFFLINE_ASSET_BASE_URL = "file:///android_asset/retro/"

    /**
     * Memetakan platform Nintendo Retro & ekstensi berkas ke core offline yang tepat:
     * - GBA -> mgba
     * - SNES -> snes9x
     * - NES -> fceumm / nes
     * - GBC / GB -> gambatte / gb
     * - NDS -> melonds / nds
     * PSP BUKAN in-app WASM (PSP diarahkan langsung ke PPSSPP).
     */
    fun resolveCore(platform: GamePlatform, romPath: String): String {
        val ext = romPath.substringAfterLast(".", "").lowercase(Locale.ROOT)
        return when {
            ext in listOf("gba", "agb") -> "mgba"
            ext in listOf("snes", "smc", "sfc") -> "snes"
            ext in listOf("nes", "fds") -> "nes"
            ext in listOf("gbc", "gb") -> "gb"
            ext in listOf("nds") -> "nds"
            ext in listOf("n64", "z64") -> "n64"
            else -> "mgba"
        }
    }

    /**
     * Menyelesaikan data base64 untuk file game lokal.
     * Untuk berkas ROM lokal <= 35 MB (GBA, NES, SNES, GBC), ROM dienkode langsung ke Base64
     * sehingga game langsung termuat secara instan dan 100% offline tanpa terhalang CORS WebView.
     */
    fun extractBase64Rom(romPath: String): String {
        val file = File(romPath)
        if (file.exists() && file.isFile && file.length() > 0) {
            try {
                val bytes = file.readBytes()
                return Base64.getEncoder().encodeToString(bytes)
            } catch (_: Exception) {
            }
        }
        return ""
    }

    /**
     * Menghasilkan dokumen HTML lengkap siap muat untuk WebView Android
     * yang menjalankan Offline Player lokal tanpa ketergantungan CDN eksternal.
     */
    fun generateHtml(game: GameItem, romPath: String): String {
        val core = resolveCore(game.platform, romPath)
        val base64Data = extractBase64Rom(romPath)
        val safeTitle = game.title.replace("\"", "\\\"").replace("'", "\\'")

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover" />
                <title>$safeTitle - NexusDeck Offline Retro Core</title>
                <style>
                    * {
                        box-sizing: border-box;
                        margin: 0;
                        padding: 0;
                        -webkit-user-select: none;
                        user-select: none;
                        -webkit-touch-callout: none;
                    }
                    html, body {
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                        background-color: #05070E;
                        color: #F8FAFC;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace;
                        touch-action: none;
                    }
                    #app-container {
                        display: flex;
                        flex-direction: column;
                        width: 100vw;
                        height: 100vh;
                        position: relative;
                    }
                    #top-bar {
                        height: 38px;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 0 16px;
                        background: rgba(15, 23, 42, 0.85);
                        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                        font-size: 11px;
                        z-index: 10;
                    }
                    #nexus-game-title {
                        font-weight: 700;
                        color: #38BDF8;
                        max-width: 55%;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .hud-badge {
                        background: rgba(6, 182, 212, 0.15);
                        color: #22D3EE;
                        border: 1px solid rgba(6, 182, 212, 0.3);
                        border-radius: 4px;
                        padding: 2px 6px;
                        font-size: 10px;
                        font-weight: 600;
                    }
                    #nexus-screen-container {
                        flex: 1;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        position: relative;
                        background: #020617;
                    }
                    #nexus-screen {
                        image-rendering: pixelated;
                        box-shadow: 0 0 30px rgba(6, 182, 212, 0.2);
                        border-radius: 6px;
                    }
                    #controls-layer {
                        position: absolute;
                        bottom: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        pointer-events: none;
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-end;
                        padding: 20px 24px;
                        z-index: 20;
                    }
                    .control-group {
                        pointer-events: auto;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        position: relative;
                    }
                    .dpad-container {
                        width: 140px;
                        height: 140px;
                        position: relative;
                    }
                    .nexus-btn {
                        position: absolute;
                        background: rgba(30, 41, 59, 0.7);
                        border: 1.5px solid rgba(148, 163, 184, 0.3);
                        color: #F8FAFC;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-weight: bold;
                        font-size: 13px;
                        box-shadow: 0 4px 10px rgba(0,0,0,0.4);
                        transition: transform 0.05s, background-color 0.05s;
                    }
                    .nexus-btn.active {
                        background: rgba(6, 182, 212, 0.8) !important;
                        border-color: #22D3EE !important;
                        transform: scale(0.92);
                    }
                    .dpad-up { top: 0; left: 45px; width: 50px; height: 46px; border-radius: 8px 8px 0 0; }
                    .dpad-down { bottom: 0; left: 45px; width: 50px; height: 46px; border-radius: 0 0 8px 8px; }
                    .dpad-left { top: 45px; left: 0; width: 46px; height: 50px; border-radius: 8px 0 0 8px; }
                    .dpad-right { top: 45px; right: 0; width: 46px; height: 50px; border-radius: 0 8px 8px 0; }
                    .dpad-center { top: 45px; left: 45px; width: 50px; height: 50px; background: rgba(30, 41, 59, 0.9); }
                    .action-container {
                        width: 140px;
                        height: 140px;
                        position: relative;
                    }
                    .btn-round {
                        width: 52px;
                        height: 52px;
                        border-radius: 50%;
                    }
                    .btn-a { bottom: 44px; right: 0; background: rgba(168, 85, 247, 0.75); border-color: rgba(192, 132, 252, 0.5); }
                    .btn-b { bottom: 0; left: 44px; background: rgba(236, 72, 153, 0.75); border-color: rgba(244, 114, 182, 0.5); }
                    .shoulder-container {
                        position: absolute;
                        top: 48px;
                        width: 100%;
                        display: flex;
                        justify-content: space-between;
                        padding: 0 20px;
                        pointer-events: none;
                    }
                    .btn-shoulder {
                        pointer-events: auto;
                        width: 76px;
                        height: 36px;
                        border-radius: 18px;
                        background: rgba(30, 41, 59, 0.65);
                        font-size: 12px;
                    }
                    .system-buttons {
                        position: absolute;
                        bottom: 16px;
                        left: 50%;
                        transform: translateX(-50%);
                        display: flex;
                        gap: 16px;
                        pointer-events: auto;
                    }
                    .btn-pill {
                        width: 58px;
                        height: 24px;
                        border-radius: 12px;
                        font-size: 10px;
                        letter-spacing: 0.5px;
                        background: rgba(30, 41, 59, 0.7);
                    }
                </style>
            </head>
            <body>
                <div id="app-container">
                    <div id="top-bar">
                        <span id="nexus-game-title">$safeTitle</span>
                        <span id="nexus-core-name" class="hud-badge">${core.uppercase()} OFFLINE</span>
                        <span id="nexus-status" style="color: #94A3B8;">Direct Core</span>
                        <span id="nexus-fps" class="hud-badge" style="color: #10B981; border-color: rgba(16, 185, 129, 0.3);">60 FPS</span>
                    </div>

                    <div id="nexus-screen-container">
                        <canvas id="nexus-screen"></canvas>
                    </div>

                    <div class="shoulder-container">
                        <button class="nexus-btn btn-shoulder" data-key="L" data-freq="350">L</button>
                        <button class="nexus-btn btn-shoulder" data-key="R" data-freq="370">R</button>
                    </div>

                    <div id="controls-layer">
                        <div class="control-group dpad-container">
                            <button class="nexus-btn dpad-up" data-key="UP" data-freq="440">▲</button>
                            <button class="nexus-btn dpad-down" data-key="DOWN" data-freq="392">▼</button>
                            <button class="nexus-btn dpad-left" data-key="LEFT" data-freq="330">◀</button>
                            <button class="nexus-btn dpad-right" data-key="RIGHT" data-freq="494">▶</button>
                            <div class="dpad-center"></div>
                        </div>

                        <div class="system-buttons">
                            <button class="nexus-btn btn-pill" data-key="SELECT" data-freq="261">SELECT</button>
                            <button class="nexus-btn btn-pill" data-key="START" data-freq="523">START</button>
                        </div>

                        <div class="control-group action-container">
                            <button class="nexus-btn btn-round btn-b" data-key="B" data-freq="440">B</button>
                            <button class="nexus-btn btn-round btn-a" data-key="A" data-freq="587">A</button>
                        </div>
                    </div>
                </div>

                <script src="file:///android_asset/retro/retro_engine.js"></script>
                <script>
                    window.addEventListener('DOMContentLoaded', function() {
                        if (window.nexusLoadRom) {
                            window.nexusLoadRom('$base64Data', '$core', '$safeTitle');
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
