# TikTok-Werkzeug (offizielle API)

`tiktok_cli.py` lädt unsere Videos über die **offizielle TikTok-API** hoch. Es gibt **kein Passwort**: Man meldet sich
einmal im Browser bei TikTok an und erlaubt den Zugriff. Gespeichert wird nur ein Zugriffs-Token unter
`~/.config/fckshorts/tiktok_token.json` (nicht im Git). Abmelden: `python3 tiktok_cli.py logout`.

## Was geht, was nicht
| Geht | Geht nicht (keine offizielle API) |
|---|---|
| Video als **Entwurf** in die TikTok-App schicken (`draft`), dann in der App Sound wählen und posten | Nachrichten (DMs) lesen oder beantworten |
| Video **direkt posten** (`post`), bis zur Prüfung durch TikTok nur als „Nur ich“ | Kommentare beantworten, liken |
| Upload-Status abfragen (`status`) | |

Für Video 3 ist `draft` ideal: Der Sound muss sowieso in der App gewählt werden.

## Einrichtung (einmalig, ca. 15 Minuten, macht Niklas)
1. **developers.tiktok.com** → mit dem TikTok-Konto anmelden → „Manage apps“ → „Connect an app“.
2. App-Name z. B. „F*ck Shorts Uploader“, Kategorie „Productivity“, Beschreibung: „Uploads our own marketing videos
   to our own account.“ Als Webseite/Datenschutz die Datenschutzerklärung der App eintragen.
3. Produkte hinzufügen: **Login Kit** und **Content Posting API** (bei Content Posting „Direct Post“ einschalten).
4. Scopes: `user.info.basic`, `video.upload`, `video.publish`.
5. **Redirect URI** im Login Kit: z. B. `https://www.freeprivacypolicy.com/live/478be282-9e4c-4797-83de-8e73fbdf6626`
   (irgendeine eigene https-Seite; nach dem Erlauben kopiert man einfach die Adresse aus dem Browser).
6. **Client Key** und **Client Secret** kopieren. Das Secret gehört **nicht in den Chat und nicht ins Git**:
   in der Claude-Cloud als Umgebungs-Geheimnis speichern (Claude fragt danach über das sichere Formular) oder am PC
   als Umgebungsvariablen setzen:
   ```
   TIKTOK_CLIENT_KEY=...
   TIKTOK_CLIENT_SECRET=...
   TIKTOK_REDIRECT_URI=<genau die Adresse aus Schritt 5>
   ```
7. Solange die App „in Entwicklung“ ist: das eigene TikTok-Konto unter „Sandbox → Target users“ eintragen.

## Benutzen
```
python3 tiktok_cli.py login-url                 # Link öffnen, anmelden, "Erlauben"
python3 tiktok_cli.py login "<Adresse nach dem Erlauben>"
python3 tiktok_cli.py me                        # prüfen, wer angemeldet ist
python3 tiktok_cli.py draft ../video1/out/FckShorts-Ad3-ohneTon.mp4
python3 tiktok_cli.py post video.mp4 --caption-file caption.txt --privacy SELF_ONLY
python3 tiktok_cli.py status <publish_id>
```
Öffentlich direkt posten (`--privacy PUBLIC_TO_EVERYONE`) erlaubt TikTok erst, wenn die App geprüft ist
(„Audit“ in der Developer-Konsole beantragen). Bis dahin: `draft` und in der App posten.
