#!/usr/bin/env python3
"""check-catalog.py — pemeriksaan kesehatan tautan katalog NexusDeck (v1.1.9).

Memeriksa setiap `downloadUrl` statis di katalog (MockGames, FreewarePcSource,
MyrientMirrorSource) dengan tabel putusan yang SAMA dengan
`LinkVerifier.evaluateHeadersDetailed` di aplikasi:

- 404/410, halaman HTML terkonfirmasi, atau file di bawah batas minimum
  -> DEAD (bukti keras).
- 429/5xx/timeout/401/403 -> UNKNOWN (tidak ada bukti ke arah mana pun).
- Hanya putusan keras yang menggerakkan game ke DEAD, dan itupun setelah
  2 kegagalan keras berturut-turut (aturan hysteresis yang sama dengan
  `mergeHealth` di aplikasi).

Hasilnya ditulis ke `app/src/main/assets/catalog-health.json` — snapshot yang
dimuat aplikasi saat pertama kali jalan. Skrip ini TIDAK PERNAH membuat build
gagal: kegagalan sesaat hanya tercatat sebagai UNKNOWN, dan exit code selalu 0.

Pemakaian:
    python3 tools/check-catalog.py
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
SNAPSHOT_PATH = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "catalog-health.json")
CATALOG_FILES = [
    os.path.join(REPO_ROOT, "app", "src", "main", "java", "com", "nexusdeck", "app",
                 "data", "MockGames.kt"),
    os.path.join(REPO_ROOT, "app", "src", "main", "java", "com", "nexusdeck", "app",
                 "core", "repository", "sources", "FreewarePcSource.kt"),
    os.path.join(REPO_ROOT, "app", "src", "main", "java", "com", "nexusdeck", "app",
                 "core", "repository", "sources", "MyrientMirrorSource.kt"),
]

USER_AGENT = "NexusDeck-CatalogHealth/1.1.9"
CONNECT_TIMEOUT_S = 15
MAX_REDIRECTS = 5
PROBE_BYTES = 512
MAX_CONCURRENT = 4
MAX_PER_HOST_ARCHIVE_ORG = 2
SWEEP_TIMEOUT_S = 300
CONFIRMATIONS_REQUIRED = 2

MIN_BYTES_GBA = 256 * 1024
MIN_BYTES_DEFAULT = 1024 * 1024


def extract_game_items(path):
    """Ambil (id, platform, downloadUrl) dari setiap blok GameItem(...) di file Kotlin.

    Pemindaian sadar-string: tanda kurung di dalam string literal (mis.
    judul "(USA)") tidak dihitung sebagai kurung blok.
    """
    with open(path, encoding="utf-8") as f:
        text = f.read()
    items = []
    search_from = 0
    while True:
        start = text.find("GameItem(", search_from)
        if start == -1:
            break
        i = start + len("GameItem(")
        depth = 1
        in_string = False
        escaped = False
        while i < len(text) and depth > 0:
            ch = text[i]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
            else:
                if ch == '"':
                    in_string = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
            i += 1
        block = text[start:i]
        id_m = re.search(r'\bid\s*=\s*"([^"]+)"', block)
        url_m = re.search(r'\bdownloadUrl\s*=\s*"([^"]+)"', block)
        platform_m = re.search(r'\bplatform\s*=\s*GamePlatform\.([A-Z0-9_]+)', block)
        # Lewati blok hasil pencarian dinamis: id/url-nya template Kotlin
        # ("${slug}") bukan nilai literal — bukan entri katalog statis.
        if id_m and url_m and "${" not in id_m.group(1) and "${" not in url_m.group(1):
            items.append({
                "id": id_m.group(1),
                "platform": platform_m.group(1) if platform_m else "",
                "downloadUrl": url_m.group(1),
                "source": os.path.basename(path),
            })
        search_from = i
    return items


def is_html_sample(data):
    """Cerminan RealGameDownloader.isHtmlRedirectHeader: 512 byte pertama."""
    if not data:
        return False
    text = data[:512].decode("utf-8", errors="ignore").strip().lower()
    return (
        text.startswith("<!doctype html")
        or text.startswith("<html")
        or text.startswith("<?xml")
        or ("<head>" in text and "<body>" in text)
    )


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_OPENER = urllib.request.build_opener(NoRedirect)


def _request(url, method, range_bytes=False):
    req = urllib.request.Request(url, method=method)
    req.add_header("User-Agent", USER_AGENT)
    if range_bytes:
        req.add_header("Range", "bytes=0-%d" % (PROBE_BYTES - 1))
    return _OPENER.open(req, timeout=CONNECT_TIMEOUT_S)


def _read_headers(resp):
    headers = resp.headers
    content_type = headers.get("Content-Type")
    try:
        content_length = int(headers.get("Content-Length", -1))
    except (TypeError, ValueError):
        content_length = -1
    retry_after = None
    raw_retry = headers.get("Retry-After")
    if raw_retry is not None:
        try:
            retry_after = int(str(raw_retry).strip())
        except ValueError:
            retry_after = None
    return content_type, content_length, retry_after


def check_url(url, min_bytes):
    """Satu kali pemeriksaan satu URL. Mengembalikan dict verdict."""
    current_url = url
    status = -1
    content_type = None
    content_length = -1
    retry_after = None
    try:
        # Putaran 1: HEAD, ikuti redirect secara manual (maks. 5).
        # NoRedirect membuat urllib menaikkan HTTPError untuk 3xx, jadi
        # Location dibaca dari header error itu — tanpa request tambahan.
        redirects = 0
        while redirects <= MAX_REDIRECTS:
            location = None
            try:
                resp = _request(current_url, "HEAD")
                status = resp.status
                content_type, content_length, retry_after = _read_headers(resp)
                resp.close()
            except urllib.error.HTTPError as e:
                status = e.code
                content_type, content_length, retry_after = _read_headers(e)
                location = e.headers.get("Location")
                e.close()
            if status in (301, 302, 303, 307, 308):
                if not location:
                    break
                current_url = urllib.parse.urljoin(current_url, location)
                redirects += 1
                continue
            break
        # Server menolak HEAD (405/501): coba GET Range.
        if status in (405, 501):
            try:
                resp = _request(current_url, "GET", range_bytes=True)
                status = resp.status
                content_type, content_length, retry_after = _read_headers(resp)
                resp.close()
            except urllib.error.HTTPError as e:
                status = e.code
                content_type, content_length, retry_after = _read_headers(e)
                e.close()
        # Putaran 2: probe 512 byte pertama bila server mengklaim 200/206.
        html_confirmed = False
        if status in (200, 206):
            try:
                resp = _request(current_url, "GET", range_bytes=True)
                if resp.status in (200, 206):
                    html_confirmed = is_html_sample(resp.read(PROBE_BYTES))
                resp.close()
            except Exception:
                html_confirmed = False
        return verdict_from_headers(
            current_url, status, content_type, content_length,
            min_bytes, html_confirmed, retry_after,
        )
    except (urllib.error.URLError, OSError, ValueError) as e:
        return {"kind": "UNKNOWN", "httpStatus": None,
                "reason": "tidak-terjangkau", "finalUrl": url,
                "retryAfterSeconds": None, "error": str(e)}


def verdict_from_headers(url, status, content_type, content_length,
                         min_bytes, html_confirmed, retry_after):
    """Cerminan LinkVerifier.evaluateHeadersDetailed (Kotlin)."""
    if not url:
        return {"kind": "DEAD", "httpStatus": status, "reason": "url-kosong",
                "finalUrl": None, "retryAfterSeconds": None}
    path = urllib.parse.urlparse(url).path
    if path.endswith("/"):
        return {"kind": "DEAD", "httpStatus": status, "reason": "direktori",
                "finalUrl": url, "retryAfterSeconds": None}
    if status == -1:
        return {"kind": "UNKNOWN", "httpStatus": -1,
                "reason": "tidak-terjangkau", "finalUrl": url,
                "retryAfterSeconds": None}
    if status in (404, 410):
        return {"kind": "DEAD", "httpStatus": status,
                "reason": "http-%d" % status, "finalUrl": url,
                "retryAfterSeconds": None}
    if status == 429 or 500 <= status <= 599:
        return {"kind": "UNKNOWN", "httpStatus": status,
                "reason": "http-%d" % status, "finalUrl": url,
                "retryAfterSeconds": retry_after}
    if status in (401, 403):
        return {"kind": "UNKNOWN", "httpStatus": status,
                "reason": "http-%d" % status, "finalUrl": url,
                "retryAfterSeconds": None}
    if status in (405, 501):
        return {"kind": "UNKNOWN", "httpStatus": status,
                "reason": "head-tidak-didukung", "finalUrl": url,
                "retryAfterSeconds": None}
    if status not in (200, 206):
        return {"kind": "UNKNOWN", "httpStatus": status,
                "reason": "http-%d" % status, "finalUrl": url,
                "retryAfterSeconds": None}
    ctype = None
    if content_type:
        ctype = content_type.strip().lower().split(";", 1)[0].strip()
    if ctype and (ctype == "text/html" or ctype.startswith("text/")):
        if html_confirmed:
            return {"kind": "DEAD", "httpStatus": status,
                    "reason": "html-landing", "finalUrl": url,
                    "retryAfterSeconds": None}
        return {"kind": "UNKNOWN", "httpStatus": status,
                "reason": "html-perlu-konfirmasi", "finalUrl": url,
                "retryAfterSeconds": None}
    if status == 200 and content_length >= 0 and content_length < min_bytes:
        return {"kind": "DEAD", "httpStatus": status,
                "reason": "file-terlalu-kecil", "finalUrl": url,
                "retryAfterSeconds": None}
    binary_types = {
        "application/octet-stream", "application/zip",
        "application/x-zip-compressed", "application/x-iso9660-image",
        "application/x-cd-image", "application/x-chd", "application/x-cso",
    }
    if ctype and ctype not in binary_types and not ctype.startswith("application/"):
        return {"kind": "UNKNOWN", "httpStatus": status,
                "reason": "tipe-tidak-dikenali", "finalUrl": url,
                "retryAfterSeconds": None}
    return {"kind": "ALIVE", "httpStatus": status,
            "reason": None, "finalUrl": url,
            "retryAfterSeconds": None,
            "contentLength": content_length if status == 200 and content_length >= 0 else None}


def merge_health(existing, game_id, verdict, now_ms):
    """Cerminan mergeHealth (Kotlin): hysteresis 2 kegagalan keras."""
    base = existing or {
        "status": "NOT_CHECKED", "httpStatus": None, "checkedAtEpochMs": 0,
        "lastAliveAtEpochMs": None, "consecutiveFailures": 0,
        "finalUrl": None, "reason": None,
    }
    kind = verdict["kind"]
    if kind == "ALIVE":
        return {
            "status": "ALIVE", "httpStatus": verdict["httpStatus"],
            "checkedAtEpochMs": now_ms, "lastAliveAtEpochMs": now_ms,
            "consecutiveFailures": 0,
            "finalUrl": verdict.get("finalUrl"), "reason": None,
        }
    if kind == "DEAD":
        failures = base.get("consecutiveFailures", 0) + 1
        status = "DEAD" if failures >= CONFIRMATIONS_REQUIRED else base["status"]
        return {
            "status": status, "httpStatus": verdict["httpStatus"],
            "checkedAtEpochMs": now_ms,
            "lastAliveAtEpochMs": base.get("lastAliveAtEpochMs"),
            "consecutiveFailures": failures,
            "finalUrl": verdict.get("finalUrl"),
            "reason": verdict.get("reason"),
        }
    # UNKNOWN: status, kegagalan, dan lastAliveAt tidak berubah.
    merged = dict(base)
    merged["httpStatus"] = verdict["httpStatus"]
    merged["checkedAtEpochMs"] = now_ms
    merged["reason"] = verdict.get("reason")
    return merged


def main():
    now_ms = int(time.time() * 1000)
    items = []
    seen_ids = set()
    for path in CATALOG_FILES:
        for item in extract_game_items(path):
            if item["id"] in seen_ids:
                continue
            seen_ids.add(item["id"])
            items.append(item)
    print("Katalog: %d game unik dari %d file" % (len(items), len(CATALOG_FILES)))

    previous = None
    if os.path.exists(SNAPSHOT_PATH):
        try:
            with open(SNAPSHOT_PATH, encoding="utf-8") as f:
                previous = json.load(f)
        except Exception as e:
            print("Snapshot lama tidak bisa dibaca (%s) — mulai dari kosong." % e)
    prev_entries = {}
    if previous and previous.get("version") == 1:
        prev_entries = previous.get("entries", {})

    host_semaphores = {}
    host_lock = threading.Lock()

    def check_one(item):
        host = urllib.parse.urlparse(item["downloadUrl"]).hostname or ""
        min_bytes = MIN_BYTES_GBA if item["platform"] == "NINTENDO" else MIN_BYTES_DEFAULT
        if "archive.org" in host:
            with host_lock:
                sem = host_semaphores.setdefault(host, threading.Semaphore(MAX_PER_HOST_ARCHIVE_ORG))
            with sem:
                return item["id"], check_url(item["downloadUrl"], min_bytes)
        return item["id"], check_url(item["downloadUrl"], min_bytes)

    verdicts = {}
    with ThreadPoolExecutor(max_workers=MAX_CONCURRENT) as pool:
        futures = {pool.submit(check_one, item): item for item in items}
        done, not_done = wait(futures.keys(), timeout=SWEEP_TIMEOUT_S)
        for fut in done:
            game_id, verdict = fut.result()
            verdicts[game_id] = verdict
        for fut in not_done:
            item = futures[fut]
            fut.cancel()
            verdicts[item["id"]] = {"kind": "UNKNOWN", "httpStatus": None,
                                     "reason": "sweep-timeout",
                                     "finalUrl": item["downloadUrl"],
                                     "retryAfterSeconds": None}

    entries = {}
    newly_dead = []
    for item in items:
        game_id = item["id"]
        verdict = verdicts[game_id]
        merged = merge_health(prev_entries.get(game_id), game_id, verdict, now_ms)
        if merged["status"] == "DEAD" and (prev_entries.get(game_id) or {}).get("status") != "DEAD":
            newly_dead.append(game_id)
        entries[game_id] = merged

    snapshot = {
        "version": 1,
        "generatedAtEpochMs": now_ms,
        "generatedBy": "ci",
        "entries": entries,
    }
    os.makedirs(os.path.dirname(SNAPSHOT_PATH), exist_ok=True)
    with open(SNAPSHOT_PATH, "w", encoding="utf-8") as f:
        json.dump(snapshot, f, indent=2, sort_keys=True)
        f.write("\n")

    counts = {"ALIVE": 0, "DEAD": 0, "UNKNOWN": 0, "NOT_CHECKED": 0}
    for entry in entries.values():
        counts[entry["status"]] = counts.get(entry["status"], 0) + 1
    print("Hasil: ALIVE=%d DEAD=%d UNKNOWN=%d NOT_CHECKED=%d"
          % (counts["ALIVE"], counts["DEAD"], counts["UNKNOWN"], counts["NOT_CHECKED"]))
    if newly_dead:
        print("Baru jadi DEAD pada sapuan ini: %s" % ", ".join(newly_dead))
    for game_id, entry in sorted(entries.items()):
        if entry["status"] in ("DEAD", "UNKNOWN"):
            print("  %-40s %-10s http=%s reason=%s"
                  % (game_id, entry["status"], entry.get("httpStatus"), entry.get("reason")))
    print("Snapshot ditulis ke %s" % SNAPSHOT_PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())
