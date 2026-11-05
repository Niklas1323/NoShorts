# Release-Schlüssel erstellen (einmalig, am PC, ca. 10 Minuten)

Mit diesem Schlüssel wird die Play-Store-Version unterschrieben. **Ohne ihn gibt es nie mehr Updates für die App.**
Darum: gut aufbewahren (z. B. auf einem USB-Stick UND in einem Passwort-Manager), nie ins Git, nie in einen Chat.
Claude sieht ihn nie.

Tipp: Bei Google Play "Play App Signing" einschalten (Standard). Dann ist der Schlüssel nur ein "Upload-Schlüssel":
Geht er verloren, kann Google einen neuen freischalten.

## 1. Schlüssel erstellen (PowerShell am Heim-PC)
```
cd $HOME\Documents
& "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\keytool.exe" -genkeypair -v -keystore fckshorts-release.jks -alias fckshorts -keyalg RSA -keysize 4096 -validity 10000
```
- Es fragt nach einem **Passwort** (zweimal). Ein langes, eigenes Passwort ausdenken und aufschreiben.
- Danach Name usw.: Name eingeben (z. B. Lucien Rahm), der Rest darf leer bleiben, am Schluss mit `yes` bestätigen.
- Bei "Key password" einfach Enter drücken (= gleiches Passwort).

## 2. Schlüssel in Text umwandeln (für GitHub)
```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Documents\fckshorts-release.jks")) | Set-Clipboard
```
Jetzt ist der Schlüssel als langer Text in der Zwischenablage.

## 3. In GitHub als Geheimnis speichern
github.com/Niklas1323/NoShorts → Settings → Secrets and variables → Actions → "New repository secret", viermal:

| Name | Wert |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | einfügen (Zwischenablage aus Schritt 2) |
| `RELEASE_STORE_PASSWORD` | das Passwort aus Schritt 1 |
| `RELEASE_KEY_PASSWORD` | das gleiche Passwort |
| `RELEASE_KEY_ALIAS` | `fckshorts` |

## 4. Fertig
Beim nächsten Push baut GitHub automatisch die **signierte** Play-Store-Datei. Sie liegt dann im Build unter
"Artifacts" als `FuckShorts-play-aab` (nicht öffentlich). Diese Datei lädt man in der Play Console hoch.
Danach die Zwischenablage mit irgendeinem anderen Text überschreiben.
