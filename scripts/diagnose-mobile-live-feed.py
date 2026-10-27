#!/usr/bin/env python3
"""Probe the dedicated Live playlist from GitHub Actions without printing stream URLs."""
from datetime import datetime
from pathlib import Path
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo
import re
import time

BASE_URL = "https://thethaonm7.phuongnm7-iptv.workers.dev/playlist.m3u"
MAX_BYTES = 8 * 1024 * 1024
TIME_DATE = re.compile(r"(?<!\d)([01]?\d|2[0-3]):([0-5]\d)\s+(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?")
DATE_TIME = re.compile(r"(?<!\d)(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\s+([01]?\d|2[0-3]):([0-5]\d)")
ZONE = ZoneInfo("Asia/Ho_Chi_Minh")
report = []

def say(line):
    report.append(line)
    print(line, flush=True)

def name_from_extinf(line):
    quoted = False
    quote_char = ""
    for i, char in enumerate(line):
        if char in ("'", '"') and (not quoted or quote_char == char):
            quoted = not quoted
            quote_char = char if quoted else ""
        elif char == "," and not quoted:
            return line[i + 1:].strip()
    return ""

def parse_time(name, now):
    match = TIME_DATE.search(name)
    reverse = False
    if not match:
        match = DATE_TIME.search(name)
        reverse = True
    if not match:
        return None
    try:
        if reverse:
            day, month, year_raw, hour, minute = match.groups()
        else:
            hour, minute, day, month, year_raw = match.groups()
        year = int(year_raw) if year_raw else now.year
        if year_raw and len(year_raw) == 2:
            year += 2000 if year < 70 else 1900
        years = [year] if year_raw else [now.year, now.year - 1, now.year + 1]
        candidates = []
        for y in years:
            try:
                candidates.append(datetime(y, int(month), int(day), int(hour), int(minute), tzinfo=ZONE))
            except ValueError:
                pass
        return min(candidates, key=lambda dt: abs((dt-now).total_seconds())) if candidates else None
    except (ValueError, TypeError):
        return None

def main():
    try:
        query = "_nm7_probe=" + str(int(time.time() * 1000))
        url = BASE_URL + ("&" if "?" in BASE_URL else "?") + query
        req = Request(url, headers={
            "User-Agent": "NM7-IPTV-Mobile-Live-Diagnostic/1.10.134",
            "Accept": "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*",
            "Cache-Control": "no-cache, no-store, max-age=0",
            "Pragma": "no-cache",
        })
        with urlopen(req, timeout=25) as response:
            status = response.status
            ctype = response.headers.get("Content-Type", "")
            payload = response.read(MAX_BYTES + 1)
        say("Dedicated Live feed probe: HTTP " + str(status) + "; content-type=" + ctype + "; bytes=" + str(len(payload)))
        if len(payload) > MAX_BYTES:
            say("ERROR: feed exceeds 8 MiB safety limit")
        decoded = payload.decode("utf-8-sig", errors="replace")
        if not decoded.strip().startswith("#EXTM3U"):
            say("ERROR: response does not begin with #EXTM3U")
        else:
            names = [name_from_extinf(line) for line in decoded.splitlines() if line.lstrip().upper().startswith("#EXTINF:")]
            names = [name for name in names if name]
            now = datetime.now(ZONE)
            timed, active, ended = [], [], []
            for name in names:
                schedule = parse_time(name, now)
                live = ("🟢" in name or "🟩" in name or re.search(r"\[(?:live)\]", name, re.I)
                        or re.search(r"\b(?:live now|currently live|đang live|trực tiếp)\b", name, re.I))
                red = ("🔴" in name or "🟥" in name)
                if schedule:
                    timed.append((name, schedule, live, red))
                    age = (now-schedule).total_seconds()
                    if live and -15*60 <= age <= 8*60*60:
                        active.append(name)
                if red:
                    ended.append(name)
            say("Feed entries: " + str(len(names)))
            say("Entries with parseable schedule: " + str(len(timed)))
            say("Entries marked live and within -15m/+8h: " + str(len(active)))
            say("Entries marked ended/red: " + str(len(ended)))
            say("Now in Asia/Ho_Chi_Minh: " + now.isoformat(timespec="seconds"))
            say("First 120 active-looking titles (titles only; no stream URLs):")
            for name in active[:120]:
                say("  - " + name[:240])
            if not active:
                say("No active-looking entries matched expected markers and times. First 30 titles:")
                for name in names[:30]:
                    say("  - " + name[:240])
    except Exception as error:
        say("Dedicated Live feed probe failed: " + type(error).__name__ + ": " + str(error))
        say("Build continues; inspect this diagnostic before concluding whether source or parser is at fault.")
    finally:
        target = Path("dist/mobile-diagnostics/dedicated-live-feed.txt")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("\n".join(report) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
