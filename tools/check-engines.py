#!/usr/bin/env python3
"""check-engines.py — pemeriksaan kesehatan registry engine NexusDeck (v1.2.0).

Memeriksa setiap engine di `EngineManager.kt` dengan aturan yang SAMA
dengan pemikiran LinkVerifier di aplikasi:

- GITHUB_APK: HEAD ke downloadUrl. 200 + tipe APK (atau octet-stream) +
  ukuran >= 1 MiB -> ALIVE. 404/410 atau halaman HTML -> DEAD (bukti keras).
  429/5xx/timeout/401/403 -> UNKNOWN (tidak ada bukti ke arah mana pun).
- PLAY_STORE: HEAD ke halaman Play Store. 200 -> ALIVE; 404 -> DEAD;
  lainnya -> UNKNOWN.
- UNAVAILABLE: tidak diperiksa lewat jaringan — status dicatat apa adanya
  (engine memang sengaja tidak ditawarkan).

Hanya putusan keras yang menggerakkan engine ke DEAD, dan itupun setelah
2 kegagalan keras berturut-turut (aturan hysteresis yang sama dengan
`mergeHealth` di aplikasi dan tools/check-catalog.py).

Sebagai pemeriksaan silang, skrip juga menanyakan GitHub Releases API untuk
setiap engine GITHUB_APK dan melaporkan bila aset APK terbaik di rilis
terbaru BERBEDA dari downloadUrl registry (sinyal nama aset berubah atau
rilis baru — registry perlu diperbarui manual). API tanpa token dibatasi
60 permintaan/jam; 403 hanya dicatat, tidak pernah menggagalkan run.

Hasilnya ditulis ke `app/src/main/assets/engine-health.json` — snapshot
yang didokumentasikan bersama registry. Skrip ini TIDAK PERNAH membuat
build gagal: kegagalan sesaat hanya tercatat sebagai UNKNOWN, dan exit
code selalu 0.

Pemakaian:
    python3 tools/check-engines.py
"""

import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, wait

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENGINE_FILE = os.path.join(
    REPO_ROOT, "app", "src", "main", "java", "com", "nexusdeck", "app",
    "core", "engine", "EngineManager.kt",
)
SNAPSHOT_PATH = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "engine-health.json")

UA = "NexusDeck-EngineHealth/1.2.0"
MIN_APK_BYTES = 1 * 1024 * 1024
CONFIRMATIONS_REQUIRED = 2
SWEEP_DEADLINE_S = 300

# Skor nama aset — salinan aturan EngineAssetPicker.scoreApkAssetName.
POSITIVE = [("android", 3), ("arm64", 2), ("v8a", 2), ("universal", 1), ("vanilla", 1)]
NEGATIVE = [("legacy", -10), ("xr", -10), ("googleplay", -5), ("debug", -10),
            ("symbols", -10), ("test", -10), ("source", -10)]


def score_asset(name):
    low = name.lower()
    score = 0
    for token, pts in POSITIVE:
        if token in low:
            score += pts
    for token, pts in NEGATIVE:
        if token in low:
            score += pts
    return score


def best_apk_asset(assets):
    best = None
    best_score = None
    for asset in assets:
        name = asset.get("name", "")
        url = asset.get("browser_download_url", "")
        if name.lower().endswith(".apk") and url:
            score = score_asset(name)
            if best_score is None or score > best_score:
                best_score = score
                best = (name, url)
    return best


def extract_engine_blocks(source):
    """Ambil blok EngineInfo(...) dari EngineManager.kt dengan pemindai
    kedalaman kurung yang sadar-string (pola yang sama seperti
    check-catalog.py untuk blok GameItem)."""
    blocks = []
    idx = 0
    while True:
        start = source.find("EngineInfo(", idx)
        if start == -1:
            break
        depth = 0
        i = start + len("EngineInfo(")
        in_str = False
        escaped = False
        while i < len(source):
            ch = source[i]
            if in_str:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_str = False
            else:
                if ch == '"':
                    in_str = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    if depth == 0:
                        blocks.append(source[start:i + 1])
                        break
                    depth -= 1
            i += 1
        idx = i + 1
    return blocks


def parse_engines(source):
    engines = []
    for block in extract_engine_blocks(source):
        def field(pattern, default=None):
            m = re.search(pattern, block)
            return m.group(1) if m else default

        engine_id = field(r'id\s*=\s*"([^"]+)"')
        if not engine_id:
            continue
        kind = field(r'downloadKind\s*=\s*EngineDownloadKind\.(\w+)', "GITHUB_APK")
        url = field(r'downloadUrl\s*=\s*"([^"]*)"', "")
        repo = field(r'githubRepo\s*=\s*"([^"]+)"')
        engines.append({
            "id": engine_id,
            "kind": kind,
            "downloadUrl": url,
            "githubRepo": repo,
        })
    return engines


def head_request(url):
    """HEAD dengan redirect manual (maks 5). Mengembalikan
    (status, content_type, content_length, final_url) atau
    ('ERR', pesan, None, url)."""
    current = url
    for _ in range(6):
        req = urllib.request.Request(current, method="HEAD", headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                return (resp.status,
                        resp.headers.get("Content-Type"),
                        resp.headers.get("Content-Length"),
                        resp.geturl())
        except urllib.error.HTTPError as e:
            if e.code in (301, 302, 303, 307, 308):
                loc = e.headers.get("Location")
                if not loc:
                    return (e.code, None, None, current)
                current = urllib.parse.urljoin(current, loc)
                continue
            return (e.code,
                    e.headers.get("Content-Type") if e.headers else None,
                    e.headers.get("Content-Length") if e.headers else None,
                    current)
        except Exception as e:
            return ("ERR", str(e), None, current)
    return ("ERR", "too many redirects", None, current)


def verdict_for_head(status, content_type, content_length, url, kind="GITHUB_APK"):
    """Putusan dari hasil HEAD. Mengembalikan (verdict, http_status, reason).

    Perbedaan penting antar jenis:
    - GITHUB_APK: tautan harus menunjuk ke file biner; halaman HTML berarti
      DEAD (soft-404 / landing page).
    - PLAY_STORE: halaman listing memang berupa HTML; 200 berarti listing
      ada (ALIVE), 404 berarti listing hilang (DEAD)."""
    if status == "ERR":
        return ("Unknown", -1, "tidak-terjangkau")
    if status in (404, 410):
        return ("Dead", status, "http-%d" % status)
    if status in (401, 403):
        return ("Unknown", status, "http-%d" % status)
    if status == 429:
        return ("Unknown", status, "rate-limited")
    if isinstance(status, int) and status >= 500:
        return ("Unknown", status, "server-error")
    if status not in (200, 206):
        return ("Unknown", status, "http-%d" % status)
    if kind == "PLAY_STORE":
        return ("Alive", status, None)
    ctype = (content_type or "").lower()
    if ctype.startswith("text/") or "text/html" in ctype:
        return ("Dead", status, "html-landing")
    if status == 200 and content_length is not None:
        try:
            if int(content_length) < MIN_APK_BYTES:
                return ("Dead", status, "file-terlalu-kecil")
        except ValueError:
            pass
    return ("Alive", status, None)


def check_engine(engine):
    engine_id = engine["id"]
    kind = engine["kind"]
    url = engine["downloadUrl"]
    now_ms = int(time.time() * 1000)

    if kind == "UNAVAILABLE":
        return {
            "id": engine_id, "verdict": "Unavailable", "httpStatus": -1,
            "finalUrl": "", "reason": "unavailable-by-design",
            "checkedAtEpochMs": now_ms,
        }

    if not url:
        return {
            "id": engine_id, "verdict": "Dead", "httpStatus": -1,
            "finalUrl": "", "reason": "url-kosong",
            "checkedAtEpochMs": now_ms,
        }

    status, ctype, clen, final_url = head_request(url)
    verdict, http_status, reason = verdict_for_head(status, ctype, clen, url, kind)

    # Pemeriksaan silang API (informatif saja; 403 tidak menggagalkan run).
    api_note = ""
    if kind == "GITHUB_APK" and engine.get("githubRepo"):
        api_url = "https://api.github.com/repos/%s/releases/latest" % engine["githubRepo"]
        req = urllib.request.Request(api_url, headers={
            "User-Agent": UA, "Accept": "application/vnd.github.v3+json",
        })
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                data = json.load(resp)
                best = best_apk_asset(data.get("assets", []))
                if best is None:
                    api_note = "API: rilis terbaru '%s' TIDAK berisi aset .apk" % data.get("tag_name")
                elif best[1] != url:
                    api_note = "API: aset terbaik rilis terbaru berbeda: %s" % best[0]
        except urllib.error.HTTPError as e:
            api_note = "API: HTTP %d (kemungkinan rate limit)" % e.code
        except Exception as e:
            api_note = "API: %s" % e

    return {
        "id": engine_id, "verdict": verdict, "httpStatus": http_status,
        "finalUrl": final_url, "reason": reason,
        "checkedAtEpochMs": now_ms, "apiNote": api_note,
    }


def merge_health(existing, engine_id, result, now_ms):
    base = existing.get(engine_id, {
        "status": "NOT_CHECKED", "httpStatus": -1, "checkedAtEpochMs": now_ms,
        "lastAliveAtEpochMs": None, "consecutiveFailures": 0,
        "finalUrl": "", "reason": None,
    })
    verdict = result["verdict"]
    entry = dict(base)
    entry["checkedAtEpochMs"] = result["checkedAtEpochMs"]
    entry["httpStatus"] = result["httpStatus"]
    entry["finalUrl"] = result["finalUrl"]
    entry["reason"] = result["reason"]
    if verdict == "Alive":
        entry["status"] = "ALIVE"
        entry["consecutiveFailures"] = 0
        entry["lastAliveAtEpochMs"] = result["checkedAtEpochMs"]
    elif verdict == "Dead":
        failures = base.get("consecutiveFailures", 0) + 1
        entry["consecutiveFailures"] = failures
        entry["status"] = "DEAD" if failures >= CONFIRMATIONS_REQUIRED else base["status"]
    elif verdict == "Unavailable":
        entry["status"] = "UNAVAILABLE"
        entry["consecutiveFailures"] = 0
    else:  # Unknown: jangan ubah status, hanya perbarui pemeriksaan terakhir.
        pass
    return entry


def main():
    with open(ENGINE_FILE, "r", encoding="utf-8") as f:
        source = f.read()
    engines = parse_engines(source)
    if not engines:
        print("Tidak ada engine yang ditemukan di EngineManager.kt", file=sys.stderr)
        return 0

    try:
        with open(SNAPSHOT_PATH, "r", encoding="utf-8") as f:
            snapshot = json.load(f)
        existing = snapshot.get("entries", {})
    except (FileNotFoundError, json.JSONDecodeError):
        snapshot = {}
        existing = {}

    now_ms = int(time.time() * 1000)
    results = {}
    lock = threading.Lock()

    def run(engine):
        result = check_engine(engine)
        with lock:
            results[engine["id"]] = result

    with ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(run, e) for e in engines]
        done, _ = wait(futures, timeout=SWEEP_DEADLINE_S)
        for fut in futures:
            if fut not in done:
                print("peringatan: pemeriksaan melewati batas waktu sweep", file=sys.stderr)

    entries = {}
    counts = {"ALIVE": 0, "DEAD": 0, "UNKNOWN": 0, "UNAVAILABLE": 0, "NOT_CHECKED": 0}
    newly_dead = []
    for engine in engines:
        result = results.get(engine["id"])
        if result is None:
            result = {"id": engine["id"], "verdict": "Unknown", "httpStatus": -1,
                      "finalUrl": engine["downloadUrl"], "reason": "sweep-timeout",
                      "checkedAtEpochMs": now_ms, "apiNote": ""}
        before = existing.get(engine["id"], {}).get("status", "NOT_CHECKED")
        entry = merge_health(existing, engine["id"], result, now_ms)
        entries[engine["id"]] = entry
        counts[entry["status"]] = counts.get(entry["status"], 0) + 1
        if entry["status"] == "DEAD" and before != "DEAD":
            newly_dead.append(engine["id"])
        note = result.get("apiNote", "")
        print("%-12s %-12s http=%s reason=%s %s" % (
            engine["id"], result["verdict"], result["httpStatus"],
            result["reason"], ("| " + note) if note else ""))

    out = {
        "version": 1,
        "generatedAtEpochMs": now_ms,
        "generatedBy": "tools/check-engines.py (v1.2.0)",
        "entries": entries,
    }
    with open(SNAPSHOT_PATH, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, sort_keys=True)
        f.write("\n")

    print("Ringkasan: %d engine — ALIVE=%d DEAD=%d UNKNOWN=%d UNAVAILABLE=%d" % (
        len(engines), counts["ALIVE"], counts["DEAD"],
        counts["UNKNOWN"], counts["UNAVAILABLE"]))
    if newly_dead:
        print("BARU DEAD: %s" % ", ".join(newly_dead))
    print("Snapshot ditulis ke %s" % SNAPSHOT_PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())
