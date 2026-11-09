/**
 * NexusIodine — NexusDeck glue between the Android WebView and the real
 * IodineGBA core (MIT, Grant Galitz 2010-2017), vendored under
 * file:///android_asset/retro/iodine/.
 *
 * Real upstream API used here (no invented methods):
 *   IodineGUI-style on-thread instance: new GameBoyAdvanceEmulator()
 *   new GfxGlueCode(240, 160); Blitter.attachCanvas(canvas)
 *   Iodine.attachGraphicsFrameHandler(Blitter)
 *   Iodine.attachROM(new Uint8Array(bytes))
 *   Iodine.attachBIOS(new Uint8Array(bytes))   // optional, user-supplied only
 *   Iodine.play() / Iodine.pause()
 *   Iodine.setIntervalRate(8) + setInterval(() => Iodine.timerCallback(...), 8)
 *   Iodine.keyDown(index) / Iodine.keyUp(index)
 *     0=A, 1=B, 2=Select, 3=Start, 4=Right, 5=Left, 6=Up, 7=Down, 8=R, 9=L
 *
 * Known limitations in v1.1.5 (deliberate, not faked):
 * - NO audio: XAudioJS is an empty git submodule upstream, so no audio
 *   handler is attached. The emulator runs silent.
 * - NO save persistence: no save import/export handlers are attached, so
 *   saves live only in memory for the session.
 * - NO BIOS is bundled. Upstream Memory.js loadBIOS() returns failure
 *   (allowInit = 0) when the attached BIOS is not exactly 0x4000 bytes,
 *   which makes Iodine.play() pause the core again. A game therefore only
 *   starts when the user supplies their own 16 KiB GBA BIOS via
 *   attachBiosBase64() before finishRomLoad(), or when a future build adds
 *   a user BIOS picker. finishRomLoad() reports this honestly through
 *   #nexus-status instead of drawing a fake "running" frame.
 * - No Worker shims: WebView file:// pages do not provide the
 *   SharedArrayBuffer/Atomics the off-thread path needs, so the core always
 *   runs on-thread.
 */
(function () {
    'use strict';

    var TIMER_RATE_MS = 8;
    var emulator = null;
    var blitter = null;
    var romChunks = [];
    var romBytes = null;
    var romTitle = '';
    var biosAttached = false;
    var timerId = null;
    var startTime = 0;
    var frameCount = 0;
    var lastFpsAt = 0;
    var running = false;

    function el(id) {
        return document.getElementById(id);
    }

    function setStatus(text) {
        var node = el('nexus-status');
        if (node) {
            node.textContent = text;
        }
        // Also surface fatal states in the note banner if the page has one.
        var note = el('nexus-note');
        if (note) {
            note.textContent = text;
        }
        if (typeof console !== 'undefined' && console.log) {
            console.log('[NexusIodine] ' + text);
        }
    }

    function setFps(text) {
        var node = el('nexus-fps');
        if (node) {
            node.textContent = text;
        }
    }

    function base64ToBytes(b64) {
        var binary = atob(b64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i) & 0xFF;
        }
        return bytes;
    }

    function startTimerIfNeeded() {
        if (timerId !== null || !emulator) {
            return;
        }
        startTime = Date.now();
        emulator.setIntervalRate(TIMER_RATE_MS);
        timerId = setInterval(function () {
            try {
                emulator.timerCallback(((Date.now() - startTime) >>> 0));
            } catch (e) {
                // A faulted core pauses itself (timerCallback default branch);
                // never let the interval throw and spin.
                if (typeof console !== 'undefined' && console.warn) {
                    console.warn('[NexusIodine] timerCallback error', e);
                }
            }
        }, TIMER_RATE_MS);
    }

    function checkStarted() {
        // Iodine.play() pauses the core again when initializeCore() fails
        // (e.g. no valid 0x4000-byte BIOS attached). The paused flag is
        // bit 0x10 of emulatorStatus.
        if (!emulator) {
            return;
        }
        var paused = (emulator.emulatorStatus & 0x10) !== 0;
        if (paused) {
            running = false;
            setStatus(
                'BIOS diperlukan: pasang BIOS GBA milikmu (16 KiB) sebelum memuat ROM. ' +
                'Tanpa BIOS yang valid, IodineGBA tidak bisa menginisialisasi core.'
            );
            setFps('0 FPS');
        } else {
            running = true;
            setStatus('Running — ' + romTitle + ' (audio off, saves in-memory)');
        }
    }

    window.NexusIodine = {
        /**
         * Create the on-thread emulator and bind it to the canvas.
         * Must be called once, after the DOM (and the canvas) exists.
         * Safe to call again: re-initialises the emulator instance.
         */
        init: function (canvasId) {
            var canvas = el(canvasId || 'nexus-screen');
            if (!canvas) {
                setStatus('Error: canvas #' + (canvasId || 'nexus-screen') + ' tidak ditemukan.');
                return false;
            }
            try {
                emulator = new GameBoyAdvanceEmulator();
                blitter = new GfxGlueCode(240, 160);
                blitter.attachCanvas(canvas);
                emulator.attachGraphicsFrameHandler(blitter);
                // play()/pause() invoke this callback unconditionally, so a
                // handler must be attached before the first play() call.
                emulator.attachPlayStatusHandler(function (isPlaying) {
                    running = !!isPlaying;
                });
                // FPS counter from the blitter's post-draw callback.
                frameCount = 0;
                lastFpsAt = Date.now();
                blitter.attachGfxPostCallback(function () {
                    frameCount++;
                    var now = Date.now();
                    if (now - lastFpsAt >= 1000) {
                        setFps(Math.round((frameCount * 1000) / (now - lastFpsAt)) + ' FPS');
                        frameCount = 0;
                        lastFpsAt = now;
                    }
                });
                romChunks = [];
                romBytes = null;
                biosAttached = false;
                setStatus('IodineGBA ready — waiting for ROM.');
                setFps('-- FPS');
                return true;
            } catch (e) {
                setStatus('Error: gagal menginisialisasi IodineGBA: ' + (e && e.message ? e.message : e));
                return false;
            }
        },

        /**
         * Append one Base64 chunk of the ROM. Kotlin sends the ROM in
         * 256 KiB chunks so no single evaluateJavascript call approaches
         * the Binder transaction limit.
         */
        appendRomChunk: function (base64Chunk) {
            if (!emulator) {
                return false;
            }
            try {
                romChunks.push(base64ToBytes(base64Chunk || ''));
                return true;
            } catch (e) {
                setStatus('Error: chunk ROM tidak valid.');
                return false;
            }
        },

        /**
         * Optional: attach a USER-SUPPLIED GBA BIOS (must be exactly
         * 0x4000 = 16384 bytes). NexusDeck never bundles a BIOS.
         * Returns true only when the BIOS was accepted.
         */
        attachBiosBase64: function (base64Bios) {
            if (!emulator) {
                return false;
            }
            try {
                var bios = base64ToBytes(base64Bios || '');
                if (bios.length !== 0x4000) {
                    setStatus('BIOS ditolak: harus tepat 16384 byte.');
                    return false;
                }
                emulator.attachBIOS(bios);
                biosAttached = true;
                return true;
            } catch (e) {
                return false;
            }
        },

        /**
         * Concatenate the received chunks, attach the ROM and start the
         * core. Returns false when there is no ROM data at all.
         */
        finishRomLoad: function (title) {
            if (!emulator) {
                setStatus('Error: IodineGBA belum diinisialisasi.');
                return false;
            }
            romTitle = title || 'GBA Game';
            var total = 0;
            for (var i = 0; i < romChunks.length; i++) {
                total += romChunks[i].length;
            }
            if (total === 0) {
                setStatus('Error: tidak ada data ROM yang diterima.');
                return false;
            }
            var bytes = new Uint8Array(total);
            var offset = 0;
            for (var j = 0; j < romChunks.length; j++) {
                bytes.set(romChunks[j], offset);
                offset += romChunks[j].length;
            }
            romChunks = [];
            romBytes = bytes;
            try {
                emulator.attachROM(bytes);
                startTimerIfNeeded();
                emulator.play();
                setStatus('Starting ' + romTitle + '...');
                // play() fails synchronously (core pauses itself) when the
                // core cannot initialise; confirm the real state shortly
                // after instead of assuming success.
                setTimeout(checkStarted, 500);
                return true;
            } catch (e) {
                setStatus('Error: gagal memuat ROM: ' + (e && e.message ? e.message : e));
                return false;
            }
        },

        keyDown: function (gbaIndex) {
            if (!emulator) {
                return;
            }
            var index = gbaIndex | 0;
            if (index < 0 || index > 9) {
                return;
            }
            try {
                emulator.keyDown(index);
            } catch (e) { /* core not running yet */ }
        },

        keyUp: function (gbaIndex) {
            if (!emulator) {
                return;
            }
            var index = gbaIndex | 0;
            if (index < 0 || index > 9) {
                return;
            }
            try {
                emulator.keyUp(index);
            } catch (e) { /* core not running yet */ }
        },

        pause: function () {
            if (!emulator) {
                return;
            }
            try {
                emulator.pause();
                running = false;
                setStatus('Paused — ' + romTitle);
            } catch (e) { /* ignore */ }
        },

        resume: function () {
            if (!emulator || !romBytes) {
                return;
            }
            try {
                emulator.play();
                setTimeout(checkStarted, 300);
            } catch (e) { /* ignore */ }
        },

        isReady: function () {
            return !!emulator && !!romBytes && running &&
                (emulator.emulatorStatus & 0x10) === 0;
        },

        isRomLoaded: function () {
            return !!romBytes;
        },

        isBiosAttached: function () {
            return biosAttached;
        }
    };
})();
