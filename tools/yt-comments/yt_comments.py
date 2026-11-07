#!/usr/bin/env python3
"""YouTube-Kommentare vom eigenen Kanal lesen und auf "TEST"-Kommentare antworten.

Lesen: API-Schluessel (in der Cloud setzt der Proxy ihn als X-Goog-Api-Key ein, lokal YT_API_KEY).
Antworten: OAuth (YT_CLIENT_ID, YT_CLIENT_SECRET, YT_REFRESH_TOKEN als Umgebungsvariablen, nie im Git).

  python3 yt_comments.py --handle @NAME --save     # Kanal merken (config.json)
  python3 yt_comments.py --hours 48                # Kommentare der letzten 48 h ansehen
  python3 yt_comments.py --reply                   # Probelauf: zeigt, was es antworten WUERDE
  python3 yt_comments.py --reply --send --max 15   # antwortet wirklich (hoechstens 15)

Antwortet nur auf Kommentare mit Test-Wunsch, nur einmal pro Kommentar (auch nicht, wenn der Kanal dort
schon geantwortet hat), nie auf eigene Kommentare, Texte immer abwechselnd. Gemerkt in replied.json.
"""
import argparse, datetime as dt, json, os, random, re, ssl, sys, urllib.parse, urllib.request

API = "https://www.googleapis.com/youtube/v3/"
HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG = os.path.join(HERE, "config.json")
REPLIED = os.path.join(HERE, "replied.json")
TEST_RE = re.compile(r"\b(test\w*|beta|try it|want it|i'?m in|ich will|link pls|link please)\b", re.I)

REPLIES = [
    "Yo! 🔥 Sign up with the link in my bio and you're in!",
    "Let's go! 😤 Link's in my bio, takes 30 seconds to sign up.",
    "You're on the list soon 👀 Just sign up with the link in my bio!",
    "Love it! 🙌 Quick sign-up via the link in my bio and you get early access.",
    "W 🔥 Link in my bio, sign up there and I'll add you!",
    "Perfect! Sign up with the link in my bio, it only takes a few seconds 🚀",
    "Bet 😎 Hit the link in my bio and you're in the test!",
    "Thanks for testing! 🙏 Please sign up through the link in my bio.",
    "Yesss 🔥 One last step: sign up with the link in my bio!",
    "You're early 👀 Grab your spot with the link in my bio!",
    "Let's gooo 🚀 Sign up with the link in my bio and I'll send you the test!",
    "Nice! 😤 Tap the link in my bio to join the test.",
    "You're in? 🔥 Then sign up quickly with the link in my bio!",
    "Appreciate you! 🙌 The sign-up link is in my bio.",
    "Say less 😎 Link in my bio, sign up and you're testing soon!",
]


def ctx():
    for f in (os.environ.get("SSL_CERT_FILE"), "/root/.ccr/ca-bundle.crt"):
        if f and os.path.exists(f):
            return ssl.create_default_context(cafile=f)
    return ssl.create_default_context()


def http(url, data=None, headers=None):
    req = urllib.request.Request(url, data=data, headers=headers or {})
    try:
        with urllib.request.urlopen(req, context=ctx(), timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        return {"_error": e.code, "_body": e.read().decode(errors="replace")[:500]}


def get(path, **params):
    key = os.environ.get("YT_API_KEY")
    if key:
        params["key"] = key
    r = http(API + path + "?" + urllib.parse.urlencode(params),
             headers={"X-Goog-Api-Key": key or "proxy-injects-this"})
    if "_error" in r:
        if path == "commentThreads" and r["_error"] == 403 and "commentsDisabled" in r["_body"]:
            return {"items": []}
        sys.exit(f"YouTube-Fehler {r['_error']} bei {path}: {r['_body']}")
    return r


_token = None
def access_token():
    global _token
    if _token:
        return _token
    need = ["YT_CLIENT_ID", "YT_CLIENT_SECRET", "YT_REFRESH_TOKEN"]
    missing = [n for n in need if not os.environ.get(n)]
    if missing:
        sys.exit("Zum Antworten fehlen: " + ", ".join(missing))
    body = urllib.parse.urlencode({"client_id": os.environ["YT_CLIENT_ID"],
                                   "client_secret": os.environ["YT_CLIENT_SECRET"],
                                   "refresh_token": os.environ["YT_REFRESH_TOKEN"],
                                   "grant_type": "refresh_token"}).encode()
    r = http("https://oauth2.googleapis.com/token", data=body,
             headers={"Content-Type": "application/x-www-form-urlencoded"})
    if "access_token" not in r:
        sys.exit(f"Anmeldung bei Google fehlgeschlagen: {r}")
    _token = r["access_token"]
    return _token


def post_reply(thread_id, text):
    body = json.dumps({"snippet": {"parentId": thread_id, "textOriginal": text}}).encode()
    return http(API + "comments?part=snippet", data=body,
                headers={"Authorization": "Bearer " + access_token(), "Content-Type": "application/json"})


def load(path, default):
    try:
        with open(path) as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def save(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


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


def ts(s):
    return dt.datetime.fromisoformat(s.replace("Z", "+00:00"))


def all_replies(thread):
    got = [x["snippet"] for x in thread.get("replies", {}).get("comments", [])]
    if thread["snippet"].get("totalReplyCount", 0) > len(got):
        got, token = [], None
        while True:
            p = {"part": "snippet", "parentId": thread["id"], "maxResults": 100, "textFormat": "plainText"}
            if token:
                p["pageToken"] = token
            r = get("comments", **p)
            got += [x["snippet"] for x in r.get("items", [])]
            token = r.get("nextPageToken")
            if not token:
                break
    return got


def author_id(s):
    return (s.get("authorChannelId") or {}).get("value")


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
            replies = all_replies(t)
            if ts(top["publishedAt"]) < since and not any(ts(x["publishedAt"]) >= since for x in replies):
                old = True
                continue
            out.append({"thread_id": t["id"], "author": top.get("authorDisplayName"),
                        "author_id": author_id(top), "text": top.get("textDisplay", ""),
                        "at": top["publishedAt"], "likes": top.get("likeCount", 0),
                        "replies": [{"author": x.get("authorDisplayName"), "author_id": author_id(x),
                                     "text": x.get("textDisplay", ""), "at": x["publishedAt"]} for x in replies],
                        "test": bool(TEST_RE.search(top.get("textDisplay", ""))),
                        "url": f"https://www.youtube.com/watch?v={video_id}&lc={t['id']}"})
        token = r.get("nextPageToken")
        if old or not token:
            return out


def pick_text(used_recent):
    choices = [t for t in REPLIES if t not in used_recent] or REPLIES
    return random.choice(choices)


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--find"); a.add_argument("--handle"); a.add_argument("--channel-id")
    a.add_argument("--save", action="store_true")
    a.add_argument("--hours", type=float, default=48); a.add_argument("--videos", type=int, default=30)
    a.add_argument("--json", action="store_true")
    a.add_argument("--reply", action="store_true", help="auf TEST-Kommentare antworten (ohne --send nur Probelauf)")
    a.add_argument("--send", action="store_true"); a.add_argument("--max", type=int, default=15)
    o = a.parse_args()

    if o.find:
        r = get("search", part="snippet", q=o.find, type="channel", maxResults=10)
        for it in r.get("items", []):
            s = it["snippet"]
            print(it["id"]["channelId"], "|", s["channelTitle"], "|", s["description"][:60])
        return

    cfg = load(CONFIG, {})
    ch = channel(o.handle, o.channel_id) if (o.handle or o.channel_id) else (
        channel(cid=cfg["channel_id"]) if cfg.get("channel_id") else sys.exit("Erst --handle @NAME --save"))
    if o.save:
        save(CONFIG, {"channel_id": ch["id"], "title": ch["snippet"]["title"], "handle": ch["snippet"].get("customUrl")})
        print("Gespeichert:", ch["snippet"]["title"], ch["id"])
    me = ch["id"]

    since = dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=o.hours)
    result = []
    for vid, title in uploads(ch, o.videos):
        for c in comments(vid, since):
            c["video"] = title
            result.append(c)
    result.sort(key=lambda c: c["at"], reverse=True)

    if o.reply:
        done = load(REPLIED, {"threads": {}, "recent_texts": []})
        todo = [c for c in result if c["test"] and c["author_id"] != me
                and c["thread_id"] not in done["threads"]
                and not any(r["author_id"] == me for r in c["replies"])]
        print(f"{len(todo)} TEST-Kommentare ohne Antwort" + ("" if o.send else " (Probelauf, nichts gesendet)"))
        sent = 0
        for c in todo[:o.max]:
            text = pick_text(done["recent_texts"][-8:])
            print(f"- {c['author']}: {c['text'][:80]!r}\n  -> {text}")
            if o.send:
                r = post_reply(c["thread_id"], text)
                if "_error" in r:
                    print(f"  FEHLER {r['_error']}: {r['_body'][:200]}")
                    if r["_error"] in (401, 403):
                        break
                    continue
                sent += 1
                done["threads"][c["thread_id"]] = {"at": dt.datetime.now(dt.timezone.utc).isoformat(),
                                                    "author": c["author"], "text": text}
            done["recent_texts"] = (done["recent_texts"] + [text])[-20:]
        if o.send:
            save(REPLIED, done)
            print(f"{sent} Antworten gesendet.")
        return

    if o.json:
        print(json.dumps({"channel": ch["snippet"]["title"], "since": since.isoformat(),
                          "stats": ch.get("statistics", {}), "comments": result}, ensure_ascii=False, indent=2))
        return
    st = ch.get("statistics", {})
    print(f"Kanal {ch['snippet']['title']}: {st.get('subscriberCount','?')} Abos, {st.get('viewCount','?')} Aufrufe")
    print(f"{len(result)} Kommentare seit {since:%d.%m. %H:%M} UTC, davon {sum(c['test'] for c in result)} mit Test-Wunsch\n")
    for c in result:
        mine = any(r["author_id"] == me for r in c["replies"])
        print(("[TEST] " if c["test"] else "") + ("[beantwortet] " if mine else "")
              + f"{c['at'][:16]}  {c['author']}  ({c['video'][:40]})")
        print("   " + c["text"].replace("\n", " ")[:300])
        for r in c["replies"]:
            print(f"   ↳ {r['author']}: {r['text'][:200]}")
        print("   " + c["url"])


if __name__ == "__main__":
    main()
