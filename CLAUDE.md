# Fuck Shorts – Übergabe für Claude

Android-App (Kotlin, Compose), die in der echten YouTube-App den Shorts-Knopf versteckt.
Ein `AccessibilityService` legt eine eigene untere Leiste (Startseite, Abos, Mein YouTube, Plus) als
`TYPE_ACCESSIBILITY_OVERLAY` über die echte YouTube-Leiste, schliesst Shorts sofort (Zurück) und deckt
Shorts-Regale ab bzw. klickt sie über ⋮ → "Kein Interesse/Nicht interessiert" weg.

Der Nutzer (Niklas) schreibt Deutsch (Schweiz, "ss" statt "ß") und möchte sehr einfache Erklärungen.
Er arbeitet vom Handy aus; diese Sitzung läuft auf seinem Computer, das Pixel hängt per USB dran.

## Wichtige Dateien

- `app/src/main/java/app/fshorts/service/FuckShortsService.kt` – Logik: wann/wo die Leiste steht, Taps, Shorts schliessen
- `.../service/BottomBarOverlay.kt` – unsere Leiste (Fenster + Compose-Inhalt, Touch über `TapHandler`)
- `.../service/YouTubeScanner.kt` – findet echte Leiste, Tabs, Regale, Shorts-Player, Mini-Player
- `.../service/BarStyle.kt` – Farben, Profilbild (aus Fenster-Screenshot ausgeschnitten)
- `.../service/ShelfCovers.kt` – graue Fläche mit Zitat über Shorts-Regalen
- `app/src/main/assets/selectors.json` – IDs/Beschriftungen der YouTube-Elemente (viele davon geraten!)

## Stand und offene Punkte (Version 16)

Der Nutzer fand Version 14–16 am besten. Gewünschtes Verhalten:
- Leiste mittig, über die ganze Breite, gleiche Abstände, sauber, wie das Vorlagenbild (Hintergrund #1C1C1C).
- **Startseite:** Leiste bleibt immer ganz da. Nur wenn ein Video geöffnet wird, verschwindet sie – aber erst,
  wenn die echte Leiste komplett weg ist. Die echte Leiste darf nie sichtbar werden.
- **Video verlassen:** unsere Leiste muss im selben Moment da sein wie die echte (nie die echte zuerst).
- **Plus → Kamera:** Leiste sofort weg; beim Zurückkommen flüssig, ohne Animation, sofort da.
- **Mini-Player** soll über der Leiste liegen (YouTube setzt ihn selbst darüber, geprüft auf Startseite).
- **Knöpfe** funktionierten nur manchmal – behoben (siehe unten), Protokoll: `tipps.txt` im Dump-Ordner.
- Shorts sollen nach einem Tipp so schnell wie möglich wieder zugehen (~120 ms; unsere Leiste bleibt dabei stehen).

Unsicher/geraten: Shorts-Player-IDs. Mit `adb shell uiautomator dump` den echten Aufbau ansehen und
`selectors.json` korrigieren (bei laufendem Video vorher `adb shell input keyevent KEYCODE_MEDIA_PAUSE`,
sonst "could not get idle state").

## Am Pixel 9 nachgemessen (YouTube 21.41, 25.10.2026)

Mit `adb shell screenrecord` + ffmpeg Bild für Bild geprüft:
- `watch_player` stimmt. Video offen: echte Leiste liegt unverändert dahinter, YouTube meldet ~100 ms nichts.
  Beim Verlassen rutscht die ganze Video-Seite nach unten → wir erkennen das am Player (Oberkante sinkt),
  in der 16-ms-Schleife. Rest: höchstens 1 Bild ein ~1 mm Streifen.
- Mini-Player-IDs: `modern_miniplayer_close`, `modern_miniplayer_overlay_action_button`.
- Startseite: YouTube **entfernt** seine Leiste beim Runterscrollen ganz aus dem Baum. Unsere bleibt, solange
  die Liste (`results`) dieselbe ist (`goneFeed`). Tipp auf unsere Leiste dann: Liste hochscrollen
  (ACTION_SCROLL_BACKWARD), warten bis die echte Leiste da ist, dann klicken.
- Knöpfe: Erst Klick über Bedienungshilfen, Finger-Tipp nur als Notlösung. Der Finger-Tipp landete sonst
  oft auf unserer eigenen Leiste (4 statt 5 Spalten → falscher Tab, z. B. Plus → "Mein YouTube").
- Abfragen an YouTube kosten 30–50 ms, während YouTube umbaut. Darum: sobald unsere Leiste neu erscheinen
  soll, Scan abbrechen (`continueScanSoon`), sonst wird sie erst nach dem Scan gezeichnet.
- Systemschrift ist beim Nutzer 150 %; YouTube vergrössert seine Leistenschrift nicht → unsere in dp.
- Warum die Leiste gerade steht/weg ist: `adb logcat -s FuckShorts` ("Leiste: ...").

## Testen auf dem Pixel (adb)

```
./gradlew assembleDebug            # oder APK aus GitHub-Release nehmen
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity
adb exec-out screencap -p > screen.png   # zeigt auch unsere Overlay-Leiste
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
adb shell input tap X Y / adb shell input swipe X1 Y1 X2 Y2 300
adb logcat -s FuckShorts
```

Der Bedienungshilfen-Dienst bleibt nach `adb install -r` eingeschaltet (gleicher Signaturschlüssel `app/debug.keystore`).
Falls aus: `adb shell settings put secure enabled_accessibility_services app.fshorts/app.fshorts.service.FuckShortsService`
und `adb shell settings put secure accessibility_enabled 1`.

## Bauen

GitHub Actions (`.github/workflows/build-apk.yml`) baut bei jedem Push eine APK und veröffentlicht sie als Release
(`releases/latest/download/FuckShorts.apk`). Lokal: JDK 17 + Android SDK, `./gradlew assembleDebug`.
Auf dem Windows-PC des Nutzers installiert: JDK unter `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`,
SDK unter `%LOCALAPPDATA%\Android\Sdk` (`local.properties`), adb über winget (Google.PlatformTools).
Lokale Builds haben versionCode 1 → `adb install -r -d`.
Arbeits-Branch: `claude/intelligent-volta-4lochv`.
