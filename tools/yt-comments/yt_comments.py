#!/usr/bin/env python3
"""Liest Kommentare vom eigenen YouTube-Kanal (nur lesen, antwortet nie).

YouTube Data API v3 mit API-Schluessel. Der Schluessel steht NICHT hier: in der Cloud setzt der
Netzwerk-Proxy ihn in den Header X-Goog-Api-Key ein. Lokal: Umgebungsvariable YT_API_KEY.

  python3 tools/yt-comments/yt_comments.py --find "fck shorts"     # Kanal suchen (kostet 100 Einheiten)
  python3 tools/yt-comments/yt_comments.py --handle @NAME --save   # Kanal merken (config.json)
  python3 tools/yt-comments/yt_comments.py --hours 48              # Kommentare der letzten 48 h
  python3 tools/yt-comments/yt_comments.py --hours 48 --json       # dasselbe als JSON

Kommentare, die nach Test-Wunsch aussehen ("test", "tester", "beta", ...), sind mit [TEST] markiert.
"""
import argparse, datetime as dt, json, os, re, ssl, sys, urllib.parse, urllib.request

API = "https://www.googleapis.com/youtube/v3/"
HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG = os.path.join(HERE, "config.json")
TEST_RE = re.compile(r"\b(test\w*|beta|try it|want it|i'?m in|ich will|link pls|link please)\b", re.I)


def ctx():
    for f in (os.environ.get("SSL_CERT_FILE"), "/root/.ccr/ca-bundle.crt"):
        if f and os.path.exists(f):
            return ssl.create_default_context(cafile=f)
    return ssl.create_default_context()


def get(path, **params):
    key = os.environ.get("YT_API_KEY")
    if key:
        params["key"] = key
    req = urllib.request.Request(API + path + "?" + urllib.parse.urlencode(params),
                                 headers={"X-Goog-Api-Key": key or "proxy-injects-this"})
    try:
        with urllib.request.urlopen(req, context=ctx(), timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")[:400]
        if path == "commentThreads" and e.code == 403 and "commentsDisabled" in body:
            return {"items": []}
        sys.exit(f"YouTube-Fehler {e.code} bei {path}: {body}")


def load_config():
    try:
        with open(CONFIG) as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def channel(handle=None, cid=None):
    p = {"part": "snippet,contentDetails,statistics"}
    if cid:
        p["id"] = cid
    else:
        p["forHandle"] = handle if handle.startswith("@") else "@" + handle
    items = get("channels", **p).get("items", [])
    if not items:
        sys.exit("Kanal nicht gefunden.")
    return items[0]


def uploads(ch, limit):
    pl = ch["contentDetails"]["relatedPlaylists"]["uploads"]
    vids, token = [], None
    while len(vids) < limit:
        p = {"part": "snippet", "playlistId": pl, "maxResults": 50}
        if token:
            p["pageToken"] = token
        r = get("playlistItems", **p)
        for it in r.get("items", []):
            s = it["snippet"]
            vids.append((s["resourceId"]["videoId"], s["title"]))
        token = r.get("nextPageToken")
        if not token:
            break
    return vids[:limit]


def comments(video_id, since):
    out, token = [], None
    while True:
        p = {"part": "snippet,replies", "videoId": video_id, "maxResults": 100,
             "order": "time", "textFormat": "plainText"}
        if token:
            p["pageToken"] = token
        r = get("commentThreads", **p)
        old = False
        for t in r.get("items", []):
            top = t["snippet"]["topLevelComment"]["snippet"]
            when = dt.datetime.fromisoformat(top["publishedAt"].replace("Z", "+00:00"))
            replies = [x["snippet"] for x in t.get("replies", {}).get("comments", [])]
            if when < since and not any(
                    dt.datetime.fromisoformat(x["publishedAt"].replace("Z", "+00:00")) >= since for x in replies):
                old = True
                continue
            out.append({"author": top.get("authorDisplayName"), "text": top.get("textDisplay", ""),
                        "at": top["publishedAt"], "likes": top.get("likeCount", 0),
                        "replies": [{"author": x.get("authorDisplayName"), "text": x.get("textDisplay", ""),
                                     "at": x["publishedAt"]} for x in replies],
                        "test": bool(TEST_RE.search(top.get("textDisplay", ""))),
                        "url": f"https://www.youtube.com/watch?v={video_id}&lc={t['id']}"})
        token = r.get("nextPageToken")
        if old or not token:
            return out


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--find"); a.add_argument("--handle"); a.add_argument("--channel-id")
    a.add_argument("--save", action="store_true")
    a.add_argument("--hours", type=float, default=48); a.add_argument("--videos", type=int, default=30)
    a.add_argument("--json", action="store_true")
    o = a.parse_args()

    if o.find:
        r = get("search", part="snippet", q=o.find, type="channel", maxResults=10)
        for it in r.get("items", []):
            s = it["snippet"]
            print(it["id"]["channelId"], "|", s["channelTitle"], "|", s["description"][:60])
        return

    cfg = load_config()
    ch = channel(o.handle, o.channel_id) if (o.handle or o.channel_id) else (
        channel(cid=cfg["channel_id"]) if cfg.get("channel_id") else sys.exit("Erst --handle @NAME --save"))
    if o.save:
        with open(CONFIG, "w") as f:
            json.dump({"channel_id": ch["id"], "title": ch["snippet"]["title"],
                       "handle": ch["snippet"].get("customUrl")}, f, indent=2)
        print("Gespeichert:", ch["snippet"]["title"], ch["id"])

    since = dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=o.hours)
    result = []
    for vid, title in uploads(ch, o.videos):
        for c in comments(vid, since):
            c["video"] = title
            result.append(c)
    result.sort(key=lambda c: c["at"], reverse=True)

    if o.json:
        print(json.dumps({"channel": ch["snippet"]["title"], "since": since.isoformat(),
                          "stats": ch.get("statistics", {}), "comments": result}, ensure_ascii=False, indent=2))
        return
    st = ch.get("statistics", {})
    print(f"Kanal {ch['snippet']['title']}: {st.get('subscriberCount','?')} Abos, {st.get('viewCount','?')} Aufrufe")
    print(f"{len(result)} Kommentare seit {since:%d.%m. %H:%M} UTC, davon {sum(c['test'] for c in result)} mit Test-Wunsch\n")
    for c in result:
        print(("[TEST] " if c["test"] else "") + f"{c['at'][:16]}  {c['author']}  ({c['video'][:40]})")
        print("   " + c["text"].replace("\n", " ")[:300])
        for r in c["replies"]:
            print(f"   ↳ {r['author']}: {r['text'][:200]}")
        print("   " + c["url"])


if __name__ == "__main__":
    main()
