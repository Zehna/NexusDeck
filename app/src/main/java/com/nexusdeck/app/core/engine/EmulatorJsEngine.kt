package com.nexusdeck.app.core.engine

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import java.util.Locale

/**
 * In-app emulator engine for NexusDeck, powered by the real IodineGBA core
 * (MIT, Grant Galitz 2010-2017) vendored under
 * file:///android_asset/retro/iodine/ and loaded by retro_player.html.
 *
 * v1.1.5 replaces the previous fake canvas engine (retro_engine.js drew a
 * gradient/starfield with "CORE: MGBA (OFFLINE)" and beeped on button
 * presses; it never emulated anything). That file is deleted.
 *
 * Scope: IodineGBA emulates Game Boy Advance only. SNES / NES / GB / NDS /
 * N64 ROMs are NOT in-app playable in v1.1.5 — resolveCore() reports the
 * platform's external engine for them instead of claiming an in-app core.
 * The ROM itself is never embedded in generated HTML; InAppGameScreen
 * injects it in 256 KiB Base64 chunks via window.NexusIodine.
 *
 * Known limitations (documented, not faked): no audio (XAudioJS is an empty
 * submodule upstream), saves live only in memory, and no BIOS is bundled —
 * upstream IodineGBA cannot initialise its core without a valid 16 KiB
 * user-supplied BIOS (see nexus_iodine_glue.js).
 */
object EmulatorJsEngine {

    const val OFFLINE_ASSET_BASE_URL = "file:///android_asset/retro/"

    const val IODINE_BASE_URL = "file:///android_asset/retro/iodine/"

    const val IODINE_PLAYER_URL = "file:///android_asset/retro/retro_player.html"

    /** Minimum valid GBA ROM size enforced before injection (256 KiB). */
    const val MIN_GBA_ROM_BYTES = 256L * 1024L

    /**
     * IodineGBA core files in upstream index.html load order. The order
     * matters: TypedArrayShim defines the typed-array helpers and
     * __VIEWS_SUPPORTED__ that the core and GfxGlueCode rely on.
     */
    val IODINE_SCRIPT_PATHS: List<String> = listOf(
        "IodineGBA/includes/TypedArrayShim.js",
        "IodineGBA/core/Cartridge.js",
        "IodineGBA/core/DMA.js",
        "IodineGBA/core/Emulator.js",
        "IodineGBA/core/Graphics.js",
        "IodineGBA/core/RunLoop.js",
        "IodineGBA/core/Memory.js",
        "IodineGBA/core/IRQ.js",
        "IodineGBA/core/JoyPad.js",
        "IodineGBA/core/Serial.js",
        "IodineGBA/core/Sound.js",
        "IodineGBA/core/Timer.js",
        "IodineGBA/core/Wait.js",
        "IodineGBA/core/CPU.js",
        "IodineGBA/core/Saves.js",
        "IodineGBA/core/sound/FIFO.js",
        "IodineGBA/core/sound/Channel1.js",
        "IodineGBA/core/sound/Channel2.js",
        "IodineGBA/core/sound/Channel3.js",
        "IodineGBA/core/sound/Channel4.js",
        "IodineGBA/core/CPU/ARM.js",
        "IodineGBA/core/CPU/THUMB.js",
        "IodineGBA/core/CPU/CPSR.js",
        "IodineGBA/core/graphics/Renderer.js",
        "IodineGBA/core/graphics/RendererShim.js",
        "IodineGBA/core/graphics/RendererProxy.js",
        "IodineGBA/core/graphics/BGTEXT.js",
        "IodineGBA/core/graphics/BG2FrameBuffer.js",
        "IodineGBA/core/graphics/BGMatrix.js",
        "IodineGBA/core/graphics/AffineBG.js",
        "IodineGBA/core/graphics/ColorEffects.js",
        "IodineGBA/core/graphics/Mosaic.js",
        "IodineGBA/core/graphics/OBJ.js",
        "IodineGBA/core/graphics/OBJWindow.js",
        "IodineGBA/core/graphics/Window.js",
        "IodineGBA/core/graphics/Compositor.js",
        "IodineGBA/core/memory/DMA0.js",
        "IodineGBA/core/memory/DMA1.js",
        "IodineGBA/core/memory/DMA2.js",
        "IodineGBA/core/memory/DMA3.js",
        "IodineGBA/core/cartridge/SaveDeterminer.js",
        "IodineGBA/core/cartridge/SRAM.js",
        "IodineGBA/core/cartridge/FLASH.js",
        "IodineGBA/core/cartridge/EEPROM.js",
        "IodineGBA/core/cartridge/GPIO.js",
        "user_scripts/GfxGlueCode.js",
        "nexus_iodine_glue.js"
    )

    /** The <script> tags for the IodineGBA core, in load order. */
    fun iodineScriptTags(): String {
        return IODINE_SCRIPT_PATHS.joinToString("\n") { path ->
            """<script src="$IODINE_BASE_URL$path"></script>"""
        }
    }

    /**
     * IodineGBA JoyPad indices: 0=A, 1=B, 2=Select, 3=Start, 4=Right,
     * 5=Left, 6=Up, 7=Down, 8=R, 9=L. Returns -1 for unknown buttons.
     */
    fun gbaKeyIndex(button: String): Int {
        return when (button.trim().uppercase(Locale.ROOT)) {
            "A" -> 0
            "B" -> 1
            "SELECT" -> 2
            "START" -> 3
            "RIGHT" -> 4
            "LEFT" -> 5
            "UP" -> 6
            "DOWN" -> 7
            "R", "R1" -> 8
            "L", "L1" -> 9
            else -> -1
        }
    }

    /** True only for ROMs the vendored IodineGBA core can actually run. */
    fun isSupportedInAppRom(romPath: String): Boolean {
        val ext = romPath.substringAfterLast(".", "").lowercase(Locale.ROOT)
        return ext in listOf("gba", "agb")
    }

    /**
     * Resolves the engine for a ROM. Returns "mgba" (IodineGBA, in-app)
     * only for .gba/.agb. Every other extension returns the platform's
     * external engine — v1.1.5 does not claim in-app cores it does not
     * ship (the old code claimed snes/nes/gb/nds/n64 cores that did not
     * exist).
     */
    fun resolveCore(platform: GamePlatform, romPath: String): String {
        val ext = romPath.substringAfterLast(".", "").lowercase(Locale.ROOT)
        return when {
            ext in listOf("gba", "agb") -> "mgba"
            ext in listOf("snes", "smc", "sfc") -> "snes9x"
            ext in listOf("nes", "fds") -> "fceumm"
            ext in listOf("gbc", "gb") -> "gambatte"
            ext in listOf("nds") -> "melonds"
            ext in listOf("n64", "z64") -> "mupen64plus"
            platform == GamePlatform.PSP || ext in listOf("iso", "cso", "chd") -> "ppsspp"
            else -> "external"
        }
    }

    /**
     * Returns the real IodineGBA loader page for a GBA game. The ROM is
     * deliberately NOT embedded here: InAppGameScreen injects it separately
     * in 256 KiB Base64 chunks via window.NexusIodine.appendRomChunk() /
     * finishRomLoad(), because embedding a whole ROM in one
     * evaluateJavascript string would hit the Binder transaction limit.
     * Prefer loading retro_player.html from assets instead of this HTML;
     * this is kept for tests and for callers that load HTML directly.
     */
    fun generateHtml(game: GameItem, romPath: String): String {
        val core = resolveCore(game.platform, romPath)
        val safeTitle = game.title.replace("\"", "\\\"").replace("'", "\\'")
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover" />
                <title>$safeTitle - NexusDeck IodineGBA</title>
                ${iodineScriptTags()}
                <style>
                    html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: #020617; }
                    #nexus-screen-container { flex: 1; display: flex; align-items: center; justify-content: center; }
                    #nexus-screen { image-rendering: pixelated; width: min(100%, 720px); aspect-ratio: 3 / 2; }
                    #nexus-status { color: #94A3B8; font: 11px monospace; padding: 6px 12px; }
                </style>
            </head>
            <body>
                <div id="nexus-status">IodineGBA — waiting for ROM.</div>
                <div id="nexus-screen-container">
                    <canvas id="nexus-screen" width="240" height="160"></canvas>
                </div>
                <script>
                    var NEXUSDECK_CORE = '$core';
                    window.addEventListener('DOMContentLoaded', function() {
                        if (window.NexusIodine) {
                            window.NexusIodine.init('nexus-screen');
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
