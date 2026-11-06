#!/usr/bin/env python3
"""Kleines Kommandozeilen-Werkzeug für die offizielle TikTok-API (Content Posting API, OAuth 2.0 mit PKCE).

Kein Passwort nötig und nie gespeichert: Man meldet sich einmal im Browser bei TikTok an und erlaubt der eigenen
Entwickler-App den Zugriff. Gespeichert wird nur der Zugriffs-Token (Datei ausserhalb des Git, siehe TOKEN_FILE).

Was die offizielle API kann:
  - Video als Entwurf in die TikTok-Inbox laden (man macht in der App fertig: Sound, Text, Posten)  -> `draft`
  - Video direkt posten (bis TikTok die App geprüft hat, nur als "Nur ich" sichtbar)                -> `post`
  - Status eines Uploads abfragen                                                                     -> `status`
Was sie NICHT kann: Nachrichten (DMs) lesen/beantworten, Kommentare beantworten, Liken.

Einrichtung (einmalig): siehe README.md im selben Ordner.
Umgebungsvariablen: TIKTOK_CLIENT_KEY, TIKTOK_CLIENT_SECRET, TIKTOK_REDIRECT_URI
"""
import argparse
import base64
import hashlib
import json
import math
import os
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://open.tiktokapis.com/v2"
AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/"
SCOPES = "user.info.basic,video.upload,video.publish"
TOKEN_FILE = os.path.expanduser(os.environ.get("TIKTOK_TOKEN_FILE", "~/.config/fckshorts/tiktok_token.json"))
PKCE_FILE = os.path.expanduser("~/.config/fckshorts/tiktok_pkce.json")

MIN_CHUNK = 5 * 1024 * 1024
MAX_CHUNK = 64 * 1024 * 1024


def env(name):
    value = os.environ.get(name)
    if not value:
        sys.exit(f"Fehlt: Umgebungsvariable {name} (siehe README.md)")
    return value


def save_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
    os.chmod(path, 0o600)


def load_json(path):
    try:
        with open(path) as f:
            return json.load(f)
    except FileNotFoundError:
        return None


def request(method, url, *, data=None, form=None, headers=None, token=None, raw=None):
    headers = dict(headers or {})
    body = None
    if form is not None:
        body = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif data is not None:
        body = json.dumps(data).encode()
        headers["Content-Type"] = "application/json; charset=UTF-8"
    elif raw is not None:
        body = raw
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            text = resp.read().decode() or "{}"
            return resp.status, json.loads(text) if text.strip().startswith("{") else {"raw": text}
    except urllib.error.HTTPError as e:
        text = e.read().decode(errors="replace")
        try:
            return e.code, json.loads(text)
        except json.JSONDecodeError:
            return e.code, {"raw": text}


def check(status, resp, what):
    err = resp.get("error") if isinstance(resp, dict) else None
    code = err.get("code") if isinstance(err, dict) else err
    if status >= 400 or (code not in (None, "ok")):
        sys.exit(f"{what} fehlgeschlagen (HTTP {status}): {json.dumps(resp, ensure_ascii=False)}")
    return resp


# ------------------------------------------------------------------------------------------- Anmeldung

def cmd_login_url(_args):
    verifier = secrets.token_urlsafe(64)[:96]
    # TikTok erwartet die Challenge als Hex-SHA256 (nicht Base64URL wie im Standard)
    challenge = hashlib.sha256(verifier.encode()).hexdigest()
    state = secrets.token_urlsafe(16)
    save_json(PKCE_FILE, {"verifier": verifier, "state": state})
    params = {
        "client_key": env("TIKTOK_CLIENT_KEY"),
        "scope": SCOPES,
        "response_type": "code",
        "redirect_uri": env("TIKTOK_REDIRECT_URI"),
        "state": state,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    }
    print("1. Diesen Link im Browser öffnen, bei TikTok anmelden und 'Erlauben' tippen:\n")
    print(AUTH_URL + "?" + urllib.parse.urlencode(params))
    print("\n2. Danach landet man auf der Weiterleitungs-Seite. Die ganze Adresse kopieren und:")
    print('   python3 tiktok_cli.py login "<kopierte Adresse>"')


def store_token(resp):
    resp["obtained_at"] = int(time.time())
    save_json(TOKEN_FILE, resp)


def cmd_login(args):
    pkce = load_json(PKCE_FILE) or sys.exit("Zuerst: python3 tiktok_cli.py login-url")
    query = urllib.parse.parse_qs(urllib.parse.urlparse(args.redirected_url).query)
    if "error" in query:
        sys.exit(f"TikTok meldet: {query.get('error')} {query.get('error_description')}")
    if query.get("state", [None])[0] != pkce["state"]:
        sys.exit("state passt nicht (alter Link?). Nochmals login-url ausführen.")
    code = query.get("code", [None])[0] or sys.exit("Kein code in der Adresse gefunden.")
    status, resp = request("POST", f"{API}/oauth/token/", form={
        "client_key": env("TIKTOK_CLIENT_KEY"),
        "client_secret": env("TIKTOK_CLIENT_SECRET"),
        "code": code,
        "grant_type": "authorization_code",
        "redirect_uri": env("TIKTOK_REDIRECT_URI"),
        "code_verifier": pkce["verifier"],
    })
    check(status, resp, "Anmeldung")
    store_token(resp)
    os.remove(PKCE_FILE)
    print(f"Angemeldet. Token gespeichert in {TOKEN_FILE} (nicht im Git).")


def access_token():
    tok = load_json(TOKEN_FILE) or sys.exit("Nicht angemeldet: python3 tiktok_cli.py login-url")
    if time.time() > tok["obtained_at"] + tok.get("expires_in", 0) - 300:
        status, resp = request("POST", f"{API}/oauth/token/", form={
            "client_key": env("TIKTOK_CLIENT_KEY"),
            "client_secret": env("TIKTOK_CLIENT_SECRET"),
            "grant_type": "refresh_token",
            "refresh_token": tok["refresh_token"],
        })
        check(status, resp, "Token erneuern")
        store_token(resp)
        tok = resp
    return tok["access_token"]


def cmd_logout(_args):
    tok = load_json(TOKEN_FILE)
    if tok:
        request("POST", f"{API}/oauth/revoke/", form={
            "client_key": env("TIKTOK_CLIENT_KEY"),
            "client_secret": env("TIKTOK_CLIENT_SECRET"),
            "token": tok["access_token"],
        })
        os.remove(TOKEN_FILE)
    print("Abgemeldet, Token gelöscht.")


# ------------------------------------------------------------------------------------------- Infos

def cmd_me(_args):
    status, resp = request("GET", f"{API}/user/info/?fields=open_id,display_name,avatar_url", token=access_token())
    print(json.dumps(check(status, resp, "Profil"), indent=2, ensure_ascii=False))


def cmd_creator(_args):
    status, resp = request("POST", f"{API}/post/publish/creator_info/query/", data={}, token=access_token())
    print(json.dumps(check(status, resp, "Creator-Info"), indent=2, ensure_ascii=False))


# ------------------------------------------------------------------------------------------- Upload

def chunk_plan(size):
    if size <= MAX_CHUNK:
        return size, 1  # kleine Videos: ein einziges Stück
    chunk = MAX_CHUNK
    return chunk, size // chunk  # letztes Stück nimmt den Rest mit (max. 128 MB)


def upload_file(upload_url, path):
    size = os.path.getsize(path)
    chunk, count = chunk_plan(size)
    with open(path, "rb") as f:
        for i in range(count):
            start = i * chunk
            end = size - 1 if i == count - 1 else start + chunk - 1
            f.seek(start)
            part = f.read(end - start + 1)
            status, resp = request("PUT", upload_url, raw=part, headers={
                "Content-Type": "video/mp4",
                "Content-Length": str(len(part)),
                "Content-Range": f"bytes {start}-{end}/{size}",
            })
            if status >= 400:
                sys.exit(f"Upload Teil {i + 1}/{count} fehlgeschlagen (HTTP {status}): {resp}")
            print(f"  hochgeladen {end + 1}/{size} Bytes")


def source_info(path):
    size = os.path.getsize(path)
    chunk, count = chunk_plan(size)
    return {"source": "FILE_UPLOAD", "video_size": size, "chunk_size": chunk, "total_chunk_count": count}


def cmd_draft(args):
    """Video in die TikTok-Inbox legen. Fertig machen (Sound wählen, Text, Posten) in der TikTok-App."""
    status, resp = request("POST", f"{API}/post/publish/inbox/video/init/",
                           data={"source_info": source_info(args.video)}, token=access_token())
    data = check(status, resp, "Entwurf anlegen")["data"]
    upload_file(data["upload_url"], args.video)
    print(f"Fertig. publish_id={data['publish_id']}")
    print("In der TikTok-App: Benachrichtigung 'Bearbeiten' öffnen, Sound wählen, Text einfügen, posten.")


def cmd_post(args):
    """Direkt posten. Solange die App von TikTok nicht geprüft ist, geht nur privacy SELF_ONLY ('Nur ich')."""
    caption = args.caption
    if args.caption_file:
        with open(args.caption_file) as f:
            caption = f.read().strip()
    post_info = {
        "title": caption or "",
        "privacy_level": args.privacy,
        "disable_comment": False,
        "disable_duet": False,
        "disable_stitch": False,
        "video_cover_timestamp_ms": int(args.cover * 1000),
    }
    status, resp = request("POST", f"{API}/post/publish/video/init/",
                           data={"post_info": post_info, "source_info": source_info(args.video)}, token=access_token())
    data = check(status, resp, "Post anlegen")["data"]
    upload_file(data["upload_url"], args.video)
    print(f"Hochgeladen. publish_id={data['publish_id']} (Status: python3 tiktok_cli.py status {data['publish_id']})")


def cmd_status(args):
    status, resp = request("POST", f"{API}/post/publish/status/fetch/", data={"publish_id": args.publish_id},
                           token=access_token())
    print(json.dumps(check(status, resp, "Status"), indent=2, ensure_ascii=False))


def main():
    p = argparse.ArgumentParser(description="F*ck Shorts – TikTok über die offizielle API")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("login-url", help="Anmelde-Link erzeugen").set_defaults(fn=cmd_login_url)
    s = sub.add_parser("login", help="Nach dem Erlauben: Weiterleitungs-Adresse einfügen")
    s.add_argument("redirected_url")
    s.set_defaults(fn=cmd_login)
    sub.add_parser("logout", help="Zugriff zurückziehen und Token löschen").set_defaults(fn=cmd_logout)
    sub.add_parser("me", help="Wer ist angemeldet?").set_defaults(fn=cmd_me)
    sub.add_parser("creator", help="Erlaubte Sichtbarkeit, max. Videolänge usw.").set_defaults(fn=cmd_creator)
    s = sub.add_parser("draft", help="Video als Entwurf in die TikTok-App schicken (empfohlen)")
    s.add_argument("video")
    s.set_defaults(fn=cmd_draft)
    s = sub.add_parser("post", help="Video direkt posten")
    s.add_argument("video")
    s.add_argument("--caption", default="")
    s.add_argument("--caption-file")
    s.add_argument("--privacy", default="SELF_ONLY",
                   choices=["SELF_ONLY", "MUTUAL_FOLLOW_FRIENDS", "FOLLOWER_OF_CREATOR", "PUBLIC_TO_EVERYONE"])
    s.add_argument("--cover", type=float, default=1.0, help="Titelbild bei Sekunde X")
    s.set_defaults(fn=cmd_post)
    s = sub.add_parser("status", help="Status eines Uploads")
    s.add_argument("publish_id")
    s.set_defaults(fn=cmd_status)
    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
