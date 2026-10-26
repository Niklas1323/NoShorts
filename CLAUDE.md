# Fuck Shorts – Übergabe für Claude

Android-App (Kotlin, Compose), die in der echten YouTube-App den Shorts-Knopf versteckt.
Ein `AccessibilityService` legt eine eigene untere Leiste (Startseite, Abos, Mein YouTube, Plus) als
`TYPE_ACCESSIBILITY_OVERLAY` über die echte YouTube-Leiste, schliesst Shorts sofort (Zurück) und klickt
Shorts-Regale über ⋮ → "Kein Interesse/Nicht interessiert" weg. Regale werden **nicht** abgedeckt (Wunsch des
Nutzers, 26.10.2026 – die frühere graue Fläche `ShelfCovers` mit Zitat ist gelöscht).

Der Nutzer (Niklas) schreibt Deutsch (Schweiz, "ss" statt "ß") und möchte sehr einfache Erklärungen.
Er arbeitet vom Handy aus; diese Sitzung läuft auf seinem Computer, das Pixel hängt per USB dran.

## Wichtige Dateien

- `app/src/main/java/app/fshorts/service/FuckShortsService.kt` – Logik: wann/wo die Leiste steht, Taps, Shorts schliessen
- `.../service/BottomBarOverlay.kt` – unsere Leiste (Fenster + Compose-Inhalt, Touch über `TapHandler`)
- `.../service/YouTubeScanner.kt` – findet echte Leiste, Tabs, Regale, Shorts-Player, Mini-Player
- `.../service/BarStyle.kt` – Farben, Profilbild (aus Fenster-Screenshot ausgeschnitten)
- `.../service/TouchWatcher.kt` – merkt jede Berührung (für "Short per Link")
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

- **Short per Link** (z. B. aus WhatsApp) darf laufen, aber nur genau dieser: Weiterwischen → wir wischen
  zurück, klappt das nicht → Shorts zu.

Shorts-Player-IDs sind am Pixel geprüft (`reel_recycler`, `reel_player_page_container`, ...). Aufbau ansehen:
`adb shell uiautomator dump` (bei laufendem Video vorher `adb shell input keyevent KEYCODE_MEDIA_PAUSE`,
sonst "could not get idle state").

## Nächste Aufgaben (Wunsch des Nutzers, 26.10.2026)

- "Man darf die alte Leiste nie sehen" – noch übrig: abgedunkelte Wortreste hinter offenem ⋮-Menü,
  2 schwache Bilder beim Schliessen der Kamera, evtl. 1 Bild ein 1-mm-Streifen beim Video-Verlassen.
- App auf dem Pixel zu einem fertigen Produkt machen (Stabilität, Sonderfälle, Startseiten-Regal-Ausblenden
  noch ungetestet). Andere Handys und schöneres Aussehen kommen **später**.

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

### Short per Link (FuckShortsService.shortAllowed, TouchWatcher)
- Link = Shorts-Player geht auf, nachdem YouTube aus einer anderen App nach vorne kam, **ohne Berührung**
  seither (max. 8 s). Berührungen meldet `TouchWatcher` (1-Pixel-Fenster mit FLAG_WATCH_OUTSIDE_TOUCH).
  Nur TYPE_VIEW_CLICKED reicht nicht: Ein Tipp auf eine Short-Karte (z. B. im Verlauf) meldet YouTube nicht.
  Tastatur (Suche) zählt nicht als "andere App".
- Welcher Short sichtbar ist: Fingerabdruck = Kanal-Bild ("Zu Kanal „@X“") + Titel darunter
  (`YouTubeScanner.shortFingerprint`, ~20 Ebenen tief). Etiketten wie "Synchronisiert"/"Shop" kommen und gehen.
- `reel_recycler` lässt sich **nicht** per Bedienungshilfe scrollen → Zurück per Wisch-Geste (`swipeFeedDown`).
- YouTube meldet beim Wischen keine Position (fromIndex = -1).
- Test: `adb shell am start -a android.intent.action.VIEW -d "'https://youtube.com/shorts/<id>?is=x'" com.google.android.youtube`
  (jedes Mal anderes `?is=`, sonst holt Android nur die alte Ansicht nach vorne). Kalt gestartet zeigt YouTube
  den Short ohne untere Leiste.
- Im Konto des Nutzers ist YouTubes eigenes "Shorts-Feed-Limit" an (Sperrfenster beim Weiterwischen).
  Schliesst man es mit X, springt YouTube zum nächsten Short → unser Zurückwischen greift.

### Menüs (⋮, Teilen)
- Sind eigene Fenster (`design_bottom_sheet`/`touch_outside`). Solange so ein Menü vorne ist, gibt Android das
  YouTube-Fenster darunter **gar nicht heraus** → nichts suchen, Zustand (Seite, Link-Short) behalten.
- Unsere Leiste steht, solange die Oberkante des Menüs unter der Leiste ist oder es nach unten fährt
  (`menuMinTop` + 24 dp), sonst weg. Hinter dem offenen Menü sieht man von der echten Leiste nur abgedunkelte
  Wortreste (darüber legen ginge nur über das Menü selbst).

### Weiteres
- Regal-Überschrift "Shorts" auf **Abos** hat nur eine Beschreibung → `findAccessibilityNodeInfosByText` findet
  sie nicht, darum `headersIn` (Einträge der Liste durchsehen). Dieses Regal hat kein eigenes ⋮ (nur die Karten);
  das ⋮ muss in der Zeile der Überschrift beginnen, sonst klickt man das Menü der ersten Short-Karte.
- Kamera (Plus) zu: Android meldet das Hauptfenster erst, wenn es schon sichtbar ist → Rest: 2 schwache Bilder
  bei 60 fps. Wir zeigen sofort, sobald ein anderes Fenster als das leistenlose vorne ist (`barlessWindowId`).
- **Mini-Player auf der Startseite:** Scrollt man runter, entfernt YouTube seine Leiste und der Mini-Player rutscht
  nach unten → unsere (die dort immer stehen bleiben muss) deckt seinen unteren Rand ab. Auf anderen Seiten
  fährt unsere mit (Mini-Player bleibt frei). Bekannt und vom Nutzer akzeptiert.

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
