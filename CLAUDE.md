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

- "Man darf die alte Leiste nie sehen" – Stand siehe "Noch offen" weiter unten (26.10.2026, Nachmittag).
  ⋮-Menü und Kamera sind inzwischen sauber.
- App auf dem Pixel zu einem fertigen Produkt machen (Stabilität, Sonderfälle, Startseiten-Regal-Ausblenden
  noch ungetestet). Andere Handys und schöneres Aussehen kommen **später**.

## Neu am 26.10.2026, Nachmittag (am Pixel 9 getestet, YouTube 21.42.243, Version 0.1.2x)

Getestet mit `screenrecord` + ffmpeg, Bild für Bild (Werkzeuge siehe "Testen auf dem Pixel").
Gefunden und behoben:
- **Startseite, unsere Leiste verschwand** nach dem Runterscrollen: (a) YouTube entfernt die weggescrollte
  Leiste manchmal erst Sekunden später (z. B. wenn ein Video in der Liste anläuft); (b) dabei ist die Liste
  `results` kurz nicht abfragbar; (c) YouTube nimmt die Leiste schon beim **Fingerauflegen** weg, vor
  TYPE_VIEW_SCROLLED. Lösung in `keepWhileScrolledAway`: Berührung (`TouchWatcher` → `lastTouchAt`) zählt als
  Scroll-Beginn, `hiddenByScroll` erlaubt späte Suche, `FEED_GRACE_MS` überbrückt kurze Aussetzer.
  Das Protokoll nennt jetzt den Grund ("keine Leiste gefunden (Tipp nach dem Scrollen)" usw.).
- **⋮ bei Werbung** öffnet kein Menü-Fenster, sondern ein Panel im YouTube-Fenster (`app_engagement_panel`,
  "Mein Anzeigen-Center"). Solange es offen ist, gibt YouTube seine Leiste nicht heraus. `handlePanel`:
  unsere weg, solange offen; beim Runterfahren/Zu sofort zeigen (`preShownAt`), sonst 2 Bilder echte Leiste.
- **Normales ⋮** ist in 21.42 ein kleines Popup (`PopupWindow`, ty=APPLICATION_PANEL) in der Bildmitte, deckt
  die Leiste nicht ab → unsere bleibt einfach stehen. (Das Menü von unten mit Abdunkelung gibt es dort nicht
  mehr; `showUnderMenu`/`measureMenuDim` greifen nur noch, falls es zurückkommt.) Achtung: das Popup-Fenster
  reicht mit seinem Schatten bis y=2320 und streift so die Leiste.
- **Premium-Werbung** (eigenes Vollbild-Fenster über der Startseite): unsere lag darüber. Jetzt Regel
  "anderes YouTube-Fenster über der Leiste" (`coverWindowId`), nur wenn das Fenster die **ganze** Leiste enthält.
- **Plus → Kamera öffnen / Tipp auf Werbung → Chrome:** YouTube nahm seine Leiste weg, bevor das neue Fenster
  zu sehen war (1–3 Bilder echte Leiste). `waitingForNewWindow`: Nach einem Tipp (Plus 1,5 s, sonst 0,8 s)
  bleibt unsere, bis das neue Fenster Inhalt hat (Android meldet es schon vorne, bevor es gezeichnet ist).
  Schliessen-Knopf heisst jetzt "Kamera schließen" (`closesBarlessPage`).
- **Suche:** Nach "Suchen" blendet die Tastatur ~0,3 s aus, Android meldet sie bis zum Ende → dahinter sah man
  ~16 Bilder die echte Leiste. `isKeyboardOpen`: Fokus aus dem Suchfeld weg = Tastatur gilt als zu.
- **Verstecken = durchsichtig** (`alpha 0` + nicht berührbar) statt `INVISIBLE`: erscheint beim Wiederzeigen
  ohne Neueinrichten des Fensters (Video verlassen: vorher 1 Bild Rand der echten Leiste).
- Endstand Version 0.1.28, ohne echte Leiste und mit unserer Leiste immer da: Startseite scrollen (inkl. 10 s
  warten, bis ein Video in der Liste anläuft); Kamera 4× öffnen und schliessen (X, Zurück-Geste, Zurück-Taste);
  normales ⋮ 2×; 15 Knopf-Tipps (Abos/Mein YouTube/Startseite je 5×, alle "ausgewählt"); Abos- und
  Mein-YouTube-Seite scrollen; Suche mit "Suchen"; YouTube verlassen zum Startbildschirm.

**Noch offen (gemessen mit 0.1.28):**
- Video verlassen: 1–2 Bilder ein schmaler Streifen (Oberkante der echten Leiste, die von unten hochfährt).
  YouTube meldet während der Animation ~100 ms keine neuen Lagen; Player und Leiste melden gleich spät.
- Tastatur mit **Zurück** schliessen (Fokus bleibt im Suchfeld): ~3 Bilder schimmert die echte Leiste
  schwach durch die ausblendende Tastatur.
- Werbe-Menü (Panel) nach der Korrektur nicht nochmals getestet (oben stand keine Werbung mit ⋮).
- Nach einem Neustart unseres Dienstes (z. B. App-Update) auf weggescrollter Startseite fehlt unsere Leiste,
  bis die echte einmal wieder auftaucht (wir kennen ihre Lage dann noch nicht).

**Test-Fallen (wichtig!):**
- `uiautomator dump` schaltet **andere Bedienungshilfen ab**, solange es läuft (~2 s) → unsere Leiste weg, die
  echte sichtbar. Nie während einer Aufnahme dumpen; Koordinaten vorher holen.
- Das Pixel lag quer mit Auto-Drehen → Videos gingen ins Querformat. Zum Testen `settings put system
  accelerometer_rotation 0` (danach zurückstellen). Wach halten: `svc power stayon usb` (danach `false`).
- Oben auf der Startseite steht oft Werbung; ein Tipp darauf öffnet Chrome oder ein Werbevideo.

### Nachtrag 26.10.2026, ~10:30
- YouTube hell/dunkel wurde nur einmal abgeschaut → `BarStyleSampler` prüft jetzt alle 30 s und nach Rückkehr
  (`recheckSoon`) neu. Am Pixel geprüft (0.1.31): Wechsel auf hell wird erkannt ("YouTube-Design gewechselt"),
  unsere Leiste wird danach hell; bis dahin kurz dunkel. `analyze` in tools/pixel-test sucht helle Pixel und
  taugt im hellen Design NICHT (muss für hell umgebaut werden). Einstellungen-Fehler am Pixel behoben (0.1.31).
- Unsere Leiste lag über den **YouTube-Einstellungen** (`SettingsActivity`, eigenes Fenster mit Liste): Die
  Berührung-als-Scrollen-Regel hielt deren Liste für die Startseite. Jetzt zählt nur eine Liste im Fenster der
  Leiste (`barWindowId`), und das muss vorne sein (`isBarWindowInFront`, kleine Popups ausgenommen).

### Nachtrag 26.10.2026, ~12:20 (Pixel 9, Android 17, YouTube 21.42.243, hell)
- `analyze` (tools/pixel-test/env.sh) taugt jetzt für hell UND dunkel: misst pro Bild den Hintergrund der Leiste
  (B) und zählt dann dunkle bzw. helle Pixel. "~" = Streifen nicht einfarbig (keine Leiste, z. B. Video-Seite).
  Ein schmaler Streifen der echten Leiste fällt nicht auf: immer `strips` ansehen.
- Video verlassen gemessen (hell): Wischen nach unten sauber. **Zurück-Taste: 1 Bild schmaler Streifen** (Icons-
  Oberkante). Protokoll (vorübergehendes Trace): ~65 ms bevor wir zeigen, kommt von YouTube
  TYPE_WINDOW_STATE_CHANGED mit contentChangeTypes=16 (PANE_APPEARED), Player schon 6 px tiefer (173→179, unter
  unserer 4-dp-Schwelle). Danach hängt jede Abfrage ~65 ms (YouTube beschäftigt). Beim Öffnen kommt ch=32.
  **Nächster Schritt:** Bei PANE_APPEARED während Video offen sofort (ohne Abfrage) unsere Leiste zeigen. Vorher
  prüfen, ob Kommentare/Beschreibung auf der Video-Seite auch PANE_APPEARED melden (dann zusätzlich Player-Lage).
- Erster Zurück auf einer Video-Seite mit Werbe-Panel schliesst nur das Panel: dabei keine echte Leiste (ok).

## Neu am 27.10.2026, 13:40–14:35 (Arbeits-PC A023985, Pixel 9, YouTube 21.42.243, hell)

Werkzeuge jetzt ohne Admin unter `C:\Users\A023985\tools`: `jdk-17.0.20.1+1`, `android-sdk` (platforms;android-35,
build-tools;35.0.0), platform-tools, ffmpeg. Lokal bauen: `JAVA_HOME=/c/Users/A023985/tools/jdk-17.0.20.1+1`,
`local.properties` mit `sdk.dir=C:/Users/A023985/tools/android-sdk` (Schrägstriche!). Testordner `C:\Users\A023985\ptest`,
dort `export T=/c/Users/A023985/tools` vor `. env.sh` (HOME ist hier H:). Wegen `MSYS_NO_PATHCONV=1` bei
`adb install` Windows-Pfad nehmen ('C:\Users\...\app-debug.apk'). Handy-PIN kennt der Nutzer (entsperren:
`KEYCODE_WAKEUP`, `KEYCODE_MENU`, `input text <PIN>`, `KEYCODE_ENTER`).

Behoben (am Pixel Bild für Bild geprüft, gepusht):
- **Video verlassen (Punkt 1):** Bei TYPE_WINDOW_STATE_CHANGED mit PANE_APPEARED und offenem Video sofort Player
  nachsehen; steht er ≥2 px tiefer → unsere zeigen (`onWatchPaneAppeared`, Protokoll "Video verlassen (Pane, …)").
  In 3 Verlassen-Fällen (Zurück-Taste, Wisch) kein Streifen der echten Leiste mehr. Rest: Auf *gescrollter*
  Startseite (echte Leiste entfernt) kommt unsere 1–3 Bilder nach dem Wegrutschen der Video-Seite (nur Inhalt
  sichtbar, keine echte Leiste).
- **Lage speichern (Punkt 5):** `saveBarState`/`loadBarState` (Prefs `bar_state`, JSON mit Bildschirmgrösse, Lage,
  Tabs). Nach Neustart (`restoredState`) auf gescrollter Startseite: Liste im vordersten Hauptfenster → unsere
  bleibt. Geprüft: App-Update auf gescrollter Startseite → unsere sofort da ("gespeicherte Lage geladen").
- **Hell↔dunkel umschalten:** `onConfigurationChanged` versteckte unsere Leiste → 6 (dunkel→hell) bzw. 12–15
  (hell→dunkel) Bilder echte Leiste. Jetzt: gleiche Bildschirmgrösse → stehen lassen, neue Farben, und 1,5 s
  (`THEME_HOLD_MS`) nicht verstecken, während YouTube neu aufbaut. Geprüft: beide Richtungen sauber.

Geprüft, in Ordnung (hell): Plus 5× (Kamera, Zurück), alle Klicks ok, keine echte Leiste; alle Knöpfe je 5× auf
gescrollter Startseite (25/25 "ausgewählt", unsere nie weg); Mini-Player auf Abos und Mein YouTube (liegt über
unserer, Knöpfe gehen); ⋮-Menü (`t_menu`), Abos/Mein YouTube scrollen (`t_pages`); Querformat Startseite (unsere
sitzt richtig); Bildschirm aus/an + Entsperren (unsere danach da); YouTube aus App-Übersicht zurück.

Noch offen (gefunden 27.10.):
- **Benachrichtigungen:** Beim *Zuklappen* (Zurück) 5 Bilder echte Leiste MIT Shorts-Knopf scharf (systemui gilt
  noch als vorne, obwohl die Leiste schon weg ist; `special.mp4` Bild 588–592). Beim Aufziehen sieht man die echte
  verschwommen hinter dem Schleier (wir verstecken bei systemui sofort). Ideen: bei systemui nicht verstecken,
  solange YouTube darunter das Hauptfenster ist (aber unsere liegt dann über der Benachrichtigungsleiste) –
  oder Lage des NOTIFICATION_SHADE-Fensters/Inhalts beobachten und beim Zuklappen früh zeigen.
  **Update 14:40: Zuklappen behoben** (`handleShade`): Solange die Benachrichtigungen offen sind, meldet Android
  NUR das systemui-Fenster (Typ 3, YouTube fehlt in `windows`), nach dem Zuklappen ~100 ms gar keins. Zeichen
  fürs Zuklappen: `com.android.systemui:id/notificationShelf` wird unsichtbar und wandert nach oben (~300 ms
  bevor YouTube wieder gemeldet wird) → unsere zeigen, 0,6 s halten (`SHADE_GAP_MS`), auch über die Lücke ohne
  Fenster. Nur Wandern zählt nicht (Scrollen der Liste!). Geprüft: Zurück und Hochwischen sauber (nur der
  verschwommene Schleier, dann unsere). Liste vorher gescrollt + Zurück: Ablage ist weg, aber `notification_stack_scroller` hat dann 0 Kinder
  (~280 ms vor YouTube) → auch das zählt (`shadeHadKids`), geprüft sauber (14:50). Rest: Aufziehen zeigt weiter die echte verschwommen. Sperrbildschirm ausgenommen (`isKeyguardLocked`).
  Versuch 14:25 (verworfen, nicht gepusht): in `scanInternal` bei Vordergrund `com.android.systemui` den untersten
  sichtbaren Knoten der Benachrichtigungen suchen und unsere zeigen, wenn er über der Leiste endet. Griff nie
  (kein Protokoll-Eintrag) – vermutlich `youTubeMainRoot()` null bzw. ≠ `barWindowId`, solange die
  Benachrichtigungen vorne sind. Nächstes Mal zuerst mit Log prüfen, was `windows` dann liefert.
- **App-Übersicht:** Beim Rein-Gehen zeigt die verkleinerte YouTube-Karte 1–2 Bilder die echte Leiste (Vorschau
  ist YouTubes eigenes Bild; schwer lösbar).
- **Tastatur (Punkt 2):** auch nach "Suchen"/Enter 3 Bilder echte Leiste durch die ausblendende Tastatur
  (`pages.mp4` 918–920). Key-Filter (`flagRequestFilterKeyEvents`) bewusst nicht eingebaut: Dienst müsste evtl. neu
  eingeschaltet werden → vorher Nutzer fragen. Andere Idee: Insets/WindowInsetsAnimation in einem eigenen Fenster.
- **Kamera öffnen (Plus):** unsere blitzt nach dem Verstecken 1–2 Bilder nochmals über der Kamera auf (keine
  echte Leiste, nur unsere; `plus5.mp4` 326–327). Selten: 14:46 bei 4 weiteren Versuchen nicht mehr aufgetreten.
- **Drehen:** Zurück ins Hochformat zeigt die Dreh-Animation kurz echte Leiste neben unserer (wir verstecken in
  `onConfigurationChanged` bei Grössenwechsel). Video-Vollbild quer nicht getestet (`videopos` fand kein Video).
- 14:43: Startseite 18× gescrollt, kein einziges Shorts-Regal (kein "Shorts-Regale"-Protokoll) – YouTube zeigt nach
  früherem "Kein Interesse" wohl keine mehr. Regal-Test auf Abos versuchen oder später wiederholen.
- Nicht getestet: Werbe-Panel (Punkt 4, keine Werbung mit ⋮ oben), Shorts-Regal wegklicken, Short per Link,
  Anruf, Split-Screen (Nutzer fragen!).
- Test-Falle: `videopos`/`t_video` erwischt oft eine Werbe-Karte (öffnet Werbe-Video/Panel) → Ergebnis prüfen.
  "ECHT?" schlägt im hellen Design auch bei dunkler Schrift im Inhalt an → immer `strips` ansehen.

## Neu am 27.10.2026, Abend (Heim-PC "Admin", Pixel 9, YouTube 21.42.243, dunkel)

Werkzeuge hier: JDK `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`, SDK `%LOCALAPPDATA%\Android\Sdk`, adb und
ffmpeg über winget (im PATH). Test-Hilfe: `env.sh`/`suite.sh` mit `T=/nonexistent` laden (Werkzeuge sind im PATH).
Alter, nie gepushter WIP-Commit vom 26.10. liegt lokal im Zweig `backup/wip-26-10` (vom gepushten Stand überholt).
Auto-Drehen war an und das Handy lag quer: Zum Testen `accelerometer_rotation 0` + `user_rotation 0` (Nutzer hat
erlaubt), danach wieder `accelerometer_rotation 1`. Drehen testen: `settings put system user_rotation 1/0`.
`analyze`/`gone` taugen im dunklen Design für "unsere weg" NICHT (unsere U≈8.7 < Schwelle 10): Bilder ansehen.
Achtung: Auf dem Handy ist auch **ScreenZen** (Bedienungshilfe) an. Etwas schreibt "shorts " ins leere Suchfeld
(Tastatur geht dabei zu und wieder auf) – nicht unsere App (kein setText im Code), vermutlich ScreenZen.
Ein Video zum Testen öffnen: `am start -a VIEW -d 'https://www.youtube.com/watch?v=6YMh40vQSgU'` (videopos findet
wegen grosser Werbekarten oft nichts). Short-IDs: von `youtube.com/@YouTube/shorts` per curl.

Behoben (Bild für Bild geprüft, gepusht):
- **Tastatur:** Tastatur-Fenster wird beim Zugehen tiefer gemeldet (`imeMinTop`) = zu. Fokus kurz leer oder in der
  Tastatur selbst (Gboard meldet sich als Fokus!) = bleibt offen – vorher lag unsere Leiste einzelne Bilder ÜBER der
  Tastatur. "Suchen": unsere sofort vor jeder Abfrage (`keyboardToPage`, Scan direkt beim STATE_CHANGED). Zurück auf
  den Suchvorschlägen: unsere bleibt weg (`barGoneAfterKeyboard`, vorher stand sie dort wegen der Startseiten-Regel);
  beim Seitenwechsel danach sofort zeigen (vorher 6–18 Bilder echte Leiste). Rest: nach "Suchen" 2 sehr schwache
  Bilder echte Leiste durch die ausblendende Tastatur (Android meldet nichts früher).
- **Drehen:** Lage der Leiste je Bildschirmgrösse (`restBySize`), unsere springt beim Drehen sofort hin.
  Rest (nicht lösbar): Androids Dreh-Animation blendet ein Foto des alten Bildes OHNE unsere Leiste darüber
  (~4 schwache Bilder Shorts-Knopf, in beide Richtungen).
- **Benachrichtigungen aufziehen:** unsere wird zur Fläche in Schleierfarbe (`showVeiled`, Farbe einmal per
  Bildschirmfoto oben gemessen, `measureShadeVeil`), Berührungen gehen durch; weg, sobald Benachrichtigungen unten
  ankommen (`shadeCoversBar`, mind. `SHADE_VEIL_MS`). Keine verwischte echte Leiste mehr beim Aufziehen. Rest: Fläche
  verdeckt ~0,2 s die unterste Symbol-Reihe, die hereinfährt. Zuklappen: ~6–8 Bilder verwischte echte Leiste (Android
  meldet das Verschwinden der Benachrichtigungen ~0,1 s zu spät). Idee: beim Zuklappen sofort Fläche statt nichts.
- **Short per Link:** Collab-Shorts ("Collab-Kanäle" = Gruppe statt Bild) wurden nicht erkannt, der weitergewischte
  galt dann als geteilter. Jetzt: kleines quadratisches Element = Kanalbild. Geprüft: Weiterwischen wird zurückgewischt.

Geprüft, in Ordnung (dunkel): Werbe-Menü (⋮ bei Werbung, Panel hoch/runter, 2×); Short per Link (läuft, unsere über
der echten, Weiterwischen zurück); Suche (`t_pages`) nach dem Tastatur-Umbau.

Nicht lösbar / Systembild: **App-Übersicht** – die Karte ist Androids Foto von YouTube ohne unsere Leiste und zeigt
die echte Leiste die GANZE Zeit (nicht nur 1–2 Bilder). Ginge nur, indem wir in die App-Übersicht zeichnen (Karte
bewegt sich, Knöpfe) – verworfen. Gleiches beim Öffnen per Link: Zoom-Animation zeigt 2 Bilder die kleine echte Leiste.
Direkt nach einer Neuinstallation (`restoredState`) beim Öffnen per Link ~15 Bilder echte Leiste (selten).

## Neu am 27.10.2026, Nacht 21:30–23:35 (Heim-PC "Admin", Pixel 9, YouTube 21.42.243, hell + dunkel)

Neue Test-Hilfe `shortsdet NAME` (in der Sitzung definiert): sucht in jeder Aufnahme das Shorts-Symbol der echten
Leiste (Ausschnitt x 275–325, y 2258–2313 mit starkem Kontrast, daneben x 190–240 einfarbig). Findet echte Leisten,
die `analyze` übersieht (z. B. auf Abos). Treffer immer mit `strips` ansehen (Werbe-Panels, Benachrichtigungs-Inhalt,
Kamera-Knöpfe lösen falsch aus). Video per Link hängt manchmal im Ladeskelett (Pixel 540,1500 = (63,63,63)) → YouTube
neu starten. Achtung: Auto-Drehen wurde während der Tests einmal wieder eingeschaltet (vermutlich Wischen über die
Schnelleinstellungen) – vor Dreh-Tests `accelerometer_rotation` prüfen.

Behoben (Bild für Bild geprüft, gepusht):
- **Video-Vollbild quer und zurück:** kein falsches "Video verlassen" mehr (Vollbild-Player zählt, `watchRelearnUntil`
  nach jeder Grössenänderung, `playerRotating`).
- **Splitscreen** (YouTube oben oder unten, Tauschen, Trenner ziehen): `updateSplit` – YouTubes Fenster gilt als
  `screen` (`screenBounds()`), YouTube gilt als vorne, auch wenn die andere App aktiv ist (`foregroundWindow`).
  Scanner-Schwellen relativ zu `screen.top`. Tastatur der anderen App versteckt unsere nicht.
- **Mini-Player baut um** (z. B. Werbung startet darin): `isBarCovered` erst nach 150 ms (`COVER_HOLD_MS`), vorher
  ~7 Bilder echte Leiste.
- **Video aus weggescrollter Startseite** öffnen und verlassen: vorher gar keine Leiste mehr; jetzt merkt
  `feedBeforeVideo` die Liste, unsere kommt zurück.
- **Benachrichtigungen:** Zuklappen per Finger: Fläche schon bei Berührung (`onShadeTouched`) → keine verwischte
  Leiste mehr; per Zurück-Taste: 48 px Bewegung ohne Berührung = zu (Rest ~2 sehr schwache Bilder). Aufziehen:
  `updateBar` versteckt unsere nicht mehr, wenn systemui (oder kurz nichts) vorne ist (vorher 2 Bilder). Schleierfarbe
  je hell/dunkel gemerkt, heller Standard `DEFAULT_VEIL_LIGHT` (vorher dunkle Fläche im hellen Design).
- **Abos scrollen:** YouTube baut die Leiste neu, unser gemerktes Element ist weg → vorher 1,5 s echte Leiste mit
  Shorts. Jetzt vor dem Verstecken einmal neu suchen (`again` in `updateBar`).
- **Popup-Menüs und Dialoge** (Suche: ⋮ oben rechts, "Suchfilter"): Solange ein Popup vorne ist, gibt Android das
  Hauptfenster nicht heraus. `popupFront`: kleines Popup → unsere bleibt; grosser Dialog → unsere abgedunkelt
  (`DIALOG_DIM` 0.6, gemessen 255→102) mit Loch, wo der Dialog liegt. Erst 1 s nachdem YouTube vorne ist
  (`POPUP_AFTER_MS`: beim Zurückkommen ist kurz ein kleines Startfenster vorne).
- **"Suchen":** jetzt kein einziges Bild echte Leiste (Tastatur offen → alle 16 ms prüfen; vorab gezeigte Leiste wird
  nicht wieder versteckt, solange die echte unter dem Bildschirm ist – `held` in `updateBar`).
- **Erst zeichnen, dann scannen:** `scheduleScan` wartet `DRAW_FIRST_MS` nach `overlay.shownAt`/`movedAt`. Vorher
  blockierte ein 400-ms-Scan direkt nach "Video verlassen" das Zeichnen (z. B. nach Mini-Player aufklappen).
- **Video verlassen** setzt `preShownAt` (unsere bleibt, während die echte reinfährt).

Geprüft, in Ordnung (hell UND dunkel, `shortsdet` + Bilder): Scrollen, ⋮-Menü, Kamera 4×, Video öffnen/verlassen,
Tabs 15/15, Seiten + Suche, Benachrichtigungen auf/zu, Video aus gescrollter Startseite. Dazu: Video-Vollbild drehen,
Startseite drehen, Splitscreen, Short in Suchergebnissen tippen (geht in 58 ms zu), Short per Link + Weiterwischen,
Mini-Player aufklappen/zuklappen/schliessen, Einstellungen, eigener Kanal, Benachrichtigungs-Seite, Posteingang,
App verlassen und zurück, Dialog/Popup in der Suche.

Nicht lösbar (Systembilder ohne unsere Leiste): App-Übersicht-Karte, Dreh-Animation (~4 schwache Bilder), App-
Öffnen-/Schliessen-Zoom (2–4 Bilder), Splitscreen-Grössenänderung.

Noch offen (mit Ideen):
- Video verlassen nach dem Drehen: in ~1 von 5 Fällen ~8 Bilder echte Leiste, weil gerade ein Scan ~150 ms an YouTube
  hängt. Idee: während `watchPageOpen` keinen vollen Scan machen, nur den Player prüfen.
- Kamera (Plus) öffnen: 1–2 Bilder unsere Leiste über der hochfahrenden Kamera (kosmetisch, nicht die echte).
  Idee: nach dem Tipp auf Plus unsere schon nach ~100 ms weg, statt auf das Kamera-Fenster zu warten.
- Shorts-Regal wegklicken: Startseite zeigt keine Regale mehr (nach früherem "Kein Interesse"), Video-Seite nur
  manchmal (Regal "Shorts" unter dem Video in `watch_list`; dort läuft die Regal-Suche gar nicht, Scan endet bei
  "Video offen"); auf Abos gefunden, hat aber kein eigenes ⋮. Ungetestet.
- Dialog zu: unsere ~10 Bilder zu lange abgedunkelt (kosmetisch).
- Anruf: auf Wunsch des Nutzers nicht getestet. Bildschirm aus/an nicht getestet.

## Neu am 28.10.2026, Morgen: Tablet (Galaxy Tab A8 SM-X200, Android 14, 1200x1920, 240 dpi, YouTube 21.42.243, dunkel)
Alles Tablet-Spezifische hängt an `isTablet` (smallestScreenWidthDp >= 600), das Handy läuft wie vorher. Ausnahme:
Suchfilter-Animation (Wunsch des Nutzers, betrifft auch das Handy, dort noch NICHT getestet).
YouTube zeigt auf dem Tablet hoch UND quer die Leiste unten (keine Seitenleiste). Echte Leiste hoch top=1776,
quer top=1056, 72 px hoch. Testwerkzeuge: `tab.sh` im Scratchpad (realdet/oursgone/tstrips, Lagen für P/L).
- **Video runterwischen** (schwebender Mini-Player): YouTube meldet nichts, bis er steht → vorher 1,1 s echte Leiste.
  Jetzt: Berührung bei offenem Video → `startPolling()`; zusätzlich wartet ein Scan, bis unsere wirklich gezeichnet
  ist (`overlay.drawnAt`, max. `TABLET_DRAW_WAIT_MS`) – Scans dauern auf dem Tablet bis 1,2 s und blockieren das
  Zeichnen. Rest: 0–3 Bilder.
- **Kamera nach Plus:** YouTube meldet die Leiste beim Schliessen kurz 32 px zu hoch; das wurde als Ruhelage gelernt
  (auch gespeichert) → unsere zu hoch und hell, darunter "Shorts". `rememberRestPosition` lernt das nicht mehr und
  korrigiert eine falsche Lage (`wrongRest`). Nach der Fotos-Frage nicht mehr vorab über der Kamera (`leftWithoutBar`).
- **Quer, Video-Seite** hat zwei Spalten, Player nur 70 % breit → galt nicht als Video (0,9 s echte Leiste).
  `YouTubeScanner(tablet=true)`: 60 %.
- **Drehen mit offenem Video:** YouTube baut den Player neu → neu suchen statt "verlassen" (`watchStillOpen`);
  versteckte Leiste vorab an die neue Stelle (`overlay.prepare`), sonst 350 ms bis sie wieder gezeichnet war.
- **Splitscreen:** Griff-Menü (Tauschen) ist ein kleines systemui-Fenster → galt als Benachrichtigungen, unsere weg.
  Jetzt "kleines System-Menü vorne, unsere bleibt".
- **Pop-up-Ansicht** (Samsung, schwebendes Fenster) und Splitscreen nebeneinander: Fenster gilt als Bildschirm
  (`updateSplit`), unsere Leiste so breit wie das Fenster (`applyBounds`). Vorher abgedunkelt unten am Bildschirm.
- **Suchfilter-Animation (neu, auch Handy):** Deckt der Dialog die ganze Leiste ab (Handy: Blatt von unten), fährt
  unsere 300 ms nach unten weg (`overlay.slideDown`); Schliessen → sofort ganz da, kein Hochfahren. Jede Berührung
  bringt sie sofort abgedunkelt zurück (`bringBackFromSlide`). Auf dem Tablet ist der Suchfilter ein Dialog in der
  Mitte → bleibt wie bisher abgedunkelt. Animation auf dem Tablet nur erzwungen geprüft: **auf dem Handy testen!**

Tablet geprüft, in Ordnung: Startseite hoch/quer, Scrollen, Tabs (je 6/6), ⋮-Menü, Kamera, Video öffnen/verlassen
(hoch/quer, Zurück und Wischen), Suche 4×, Suchfilter hoch/quer, Benachrichtigungen, Home/zurück, Short per Link
(spielt, Weiterwischen wird zurückgewischt), Bildschirm aus/an, Splitscreen (oben/unten, Tabs, Video), Pop-up-Ansicht
(Tabs 3/3, verschieben).
### Nachmittag 28.10.: Tablet fertig gemacht
- **Jede Sprache:** Knöpfe werden notfalls nach Lage erkannt (`collectByPosition` in `YouTubeScanner`, nur bei
  YouTubes eigener Leiste per ID; Rechts-nach-links gespiegelt). Mit absichtlich leerer Wortliste geprüft (Tabs 3/3,
  Video). Dazu Wörter für ~25 Sprachen in `selectors.json` (Nicht interessiert, Schliessen …), Regal-Wort wird von der
  Leiste gelernt (`learnedShortsWord`). Ersatztext immer Englisch (Home/Subscriptions/You). YouTube folgt nur der
  Systemsprache (App-Sprache wird ignoriert) → andere Sprachen am Gerät nicht geprüft.
- **Keine neue Berechtigung** (Play-Store-Prüfung): Tastenfilter bewusst NICHT eingebaut.
- **Erst zeichnen, dann abfragen (Tablet):** Bei laufendem Video wartet jede Abfrage an YouTube bis 200 ms. Nachführen
  und Meldungen warten jetzt, bis unsere Leiste gezeichnet ist (`drawPending`, `overlay.drawnAt`). Video verlassen
  nach dem Drehen: vorher 0,3–0,9 s, jetzt 0–4 Bilder.
- **Benachrichtigungen quer:** Spalte in der Mitte → Fläche in Schleierfarbe mit Loch (`shadePanel`), Loch erst, wenn
  die Spalte bis zur Leiste reicht. Hochkant unverändert.
- **Dialog (Suchfilter):** Berührung → Bild für Bild nachsehen; nach "Abbrechen" sofort wieder hell.
- **Hell** (YouTube-Design kurz auf "Hell", danach zurück auf "Gerätedesign verwenden"): hoch und quer geprüft.
- **Test-Sicherheit:** `safetap`/`leavevideo` in `tab.sh` – nie auf Feed-Stellen tippen, solange ein Video offen ist
  (ein Tipp landete einmal auf "Melden"; Fenster ohne Auswahl wieder geschlossen, nichts gemeldet).

**Handy danach geprüft (28.10., 13:40, Pixel 9, dunkel), alles in Ordnung:** Scrollen, Tabs 15/15, Kamera 3×, Seiten +
Suche, ⋮-Menü, Video öffnen/Zurück. Suchfilter ist auch auf dem Handy (YouTube 21.42) ein Dialog in der Mitte, der die
Leiste NICHT abdeckt → unsere bleibt abgedunkelt wie bisher, das Wegfahren (`slideDown`) greift nur, wenn YouTube den
Filter einmal als Blatt von unten über die Leiste zeigt. Sonst sähe man beim Wegfahren die echte Leiste.

**Tablet: nichts Lösbares mehr offen.** Nicht lösbar (Android/YouTube), mit Grund:
- Video verlassen (Zurück-Wischen/Taste): 0–4 Bilder, bei laufender Werbung bis ~10. YouTube meldet die neue Seite
  erst so spät; vorher gibt es kein Zeichen (auch kein System-Fenster für die Geste).
- Benachrichtigungen ohne Berührung zugeklappt (nur per Befehl möglich): selten 3 Bilder.
- Suchfilter durch Tipp daneben geschlossen: unsere ~0,3 s zu lange abgedunkelt (Dialog bleibt bis Ende der
  Ausblend-Animation gemeldet). Nur Optik, echte Leiste nie sichtbar.
- System-Animationen: Entsperren, Splitscreen tauschen (0,35 s), Pop-up maximieren (0,6 s), Drehen, App-Übersicht.
- Shorts-Regale: siehe unten (Abos-Regal am Handy jetzt gelöst).

### 28.10. Nachmittag: Rückmeldung vom Handy
- **Lücken** links (1 px) und unten (2 Zeilen): gemerkte Lage war 1 px verschoben (Animation) → `snapToEdges`
  (Rand ±3 px = Rand, auch beim Laden), `applyBounds` nimmt ganze Breite ab `width >= Bildschirm - 6`.
- **Wegfahren statt Verschwinden** (Wunsch): `overlay.slideAway(450 ms)` bei Suche (Start beim Tipp auf die Lupe,
  `SEARCH_LABELS`; die Tastatur meldet Android erst ganz offen, Klick kommt ~0,8 s nach dem Finger) und Video öffnen.
  Während des Wegfahrens kein `show` und keine Abfragen (`drawPending`), sonst stoppte/ruckelte es.
- **Leiste verschwindet beim Scrollen:** 75 s gemischtes Scrollen (2249 Bilder) ohne Fehlen – nicht nachgestellt;
  vermutlich die 1-px-Lage. Beobachten.
- **Regal-Menü** wird jetzt im nächsten Bild bedient (vorher 100–150 ms Abstand), Menü kaum sichtbar.

### Shorts-Regal auf "Abos" (28.10., 14:00, Handy geprüft)
Das Regal auf "Abos" hat kein eigenes ⋮, nur jede Karte ("Mehr Aktionen" → Teilen / **Ausblenden** / Feedback).
Wunsch des Nutzers: jede Karte einzeln ausblenden. `Shelf.cardMenuButtons` (nur ⋮ in der seitlich scrollbaren
Kartenreihe, nie "… abspielen"), alle 1,5 s eine Karte, für Karten NUR `cardHideLabels` ("Ausblenden" in ~25
Sprachen), nie "Kein Interesse". 3 Fehlklicks → Sitzung aus. Geprüft: alle Karten weg, nur "Shorts >" bleibt,
normale Videos unberührt.
Dabei zwei alte Fehler behoben: `findMenuButton` nahm eine Short-Karte rechts als ⋮ (öffnete den Short; jetzt klein
in beiden Richtungen und kein "abspielen"), `shelfFor` stürzte ab, wenn die Liste beim Scrollen Kinder verlor.
Suchfilter-Wegfahren am Handy erzwungen geprüft: 300 ms sanft, unsere stand danach ganz unten weg, die echte blieb
unter dem Dialog-Schleier (sah man nur, weil der Dialog die Leiste nicht abdeckt – darum nur bei `coversBar` aktiv).

### 28.10. Abend (Cloud, NICHT am Handy getestet): Leiste fuhr beim Scrollen manchmal runter
Nutzer meldet: Startseite, einfach scrollen → unsere Leiste fährt manchmal runter. Vermutete Ursache: Jedes
TYPE_WINDOW_STATE_CHANGED von YouTube zählte als "Navigation" (`onNavigation`), auch beim Scrollen (Video in der
Liste läuft an) → "Tipp nach dem Scrollen" bzw. `videoOpening` → `hideSmooth`. Jetzt: STATE_CHANGED zählt nur
nach einem Tipp (`looksLikeTap`: Berührung < 800 ms, seither nicht gescrollt); `videoOpening` nur ohne neue
Berührung nach dem Tipp; Liste kurz nicht abfragbar direkt nach dem Scrollen → 600 ms nachsehen statt verstecken
(`feedMissSince`). **Am Pixel prüfen:** Startseite lange scrollen (`adb logcat -s FuckShorts`, "Leiste nicht mehr
da (…)" darf nicht kommen), Video öffnen (unsere fährt weiter weg), Plus/Kamera, Suche.

### 28.10. Abend (Cloud, NICHT am Handy getestet): Profilbild-Ring, Abos-Auswahl
Video vom Nutzer (YouTube mit neuem Design: oben Reiter "Startseite | Abos | Musik | Live | Mehr"; unsere Leiste
zeigt englische Ersatztexte → echte Leiste hat wohl keinen Text bzw. ist umgebaut):
- Profilbild hatte immer einen weissen Ring (Ausschnitt wurde abgeschaut, als Mein YouTube gewählt war). Jetzt
  Rand immer weggeschnitten (`AVATAR_INSET`), Ring nur selbst gezeichnet, wenn gewählt; Sampler nimmt kein
  gewähltes Profilbild mehr, wenn schon eins da ist.
- Auf Mein YouTube sprang die Auswahl auf Abos und zurück: `selectedTab` – melden mehrere "gewählt", gewinnt
  Mein YouTube, dann Startseite. Abos-Reiter oben gewählt → Abos.
- Tipp auf Abos tat nichts (Seite blieb Startseite): Ersatz `findTopTab` → Reiter "Abos" oben anklicken.
**Am Pixel prüfen + Struktur holen** (`uiautomator dump` auf Startseite und Mein YouTube): Wie sieht die neue Leiste
aus (Texte, `isSelected`)? Logcat "Tab gewählt: … (Leiste meldet …)".

### 30.10. (Cloud, NICHT am Handy getestet): Hinweis für Google (Play Store, Bedienungshilfe)
Unterlagen fürs Play-Console-Formular, Store-Text und Video-Drehbuch: `docs/play-store/bedienungshilfe-erklaerung.md`.
App: `DisclosureCard` vollständig und ehrlich (vorher "liest keine anderen Apps" – stimmte nicht ganz: vordere App,
Lage von Benachrichtigungen/Tastatur, Bildschirmfotos; Regal-Klicks und Fehler-Dateien fehlten), Knöpfe Zustimmen/
Ablehnen, Hinweis kommt auch, wenn der Dienst schon an ist. Neuer Schlüssel `disclosure_accepted_v2` → jeder muss
einmal neu zustimmen (Dienst läuft trotzdem weiter). `a11y_description` angepasst. `leiste-*.png` werden auf 12
Dateien aufgeräumt. **Am Handy prüfen:** Hinweis erscheint einmal, Ablehnen/Zustimmen, Leiste geht wie vorher.
Wichtig: Kommt später Internet dazu (Superwall, Absturzberichte, Online-Selektoren), Hinweis + Formular anpassen!

## Test der letzten Fixes – Tablet und Handy fertig
**Offen:** Regal-Karten auf der Startseite (⋮ → "Not interested"): auf Handy und Tablet kam dort noch kein Regal, also ungeprüft. Sonst nichts.

### Handy (01.11.2026, Heim-PC "Admin", Pixel 9, Android 17, YouTube 21.43.523, lokaler Build 0.1.86)
Am Anfang war Release 0.1.85 (8bf06ef) installiert. Handy-Sprache en-CH, YouTube darum Englisch. Zwei Benutzer auf dem Handy (Leon, Niklas; aktiv ist
Niklas = u10). Systemschrift von Niklas ist **100 %**, nicht 150 % (Leon 130 %). Für den Hinweis kurz auf 150 % gestellt, danach zurück.

| Punkt | Ergebnis Handy |
|---|---|
| 0 Hinweis | OK: kam einmal, Ablehnen → kurze Karte → "Hinweis nochmals zeigen" → Zustimmen → "YouTube öffnen", bleibt nach Neustart weg. Bei 150 % alles lesbar, Leiste lief weiter. Nur Optik: "Zustimmen" ist violett auf rot (`onPrimary` fehlt im dunklen Farbschema). |
| 1 Scrollen | OK: zweimal ~130–170 s (4348 und 5791 Bilder), Videos liefen in der Liste an (geprüft). Leiste weicht in keinem Bild ab, echte nie sichtbar, beim Scrollen kein "Leiste nicht mehr da". Aber: siehe ⋮-Fehler unten (behoben). |
| 2 Profilbild | OK: Ring nur auf You, hell und dunkel. |
| 3 Auswahl | OK: je 12 s auf jeder Seite, hell und dunkel, nichts springt. Kurz sichtbar nur: Tipp auf Home von You aus, wenn YouTube sich Abos gemerkt hat (~0,4 s Abos, dann Home). |
| 4 Abos-Knopf | Ging **nicht** (siehe unten), jetzt OK: 5/5 von Home, 5/5 von You, auch nach Neustart von YouTube und aus gescrollter Liste. |
| 5 Neues Design | **Ist da.** Oben `tabs_bar_text_tab_view` [Home, Subs, Music, Live, More] als Beschreibung, nur auf der Startseite, `selected` stimmt. Nach dem Start und nach dem Scrollen fehlen sie, bis die Liste nach unten gezogen wird. Echte Leiste `pivot_bar` [0,2235][1080,2424]: **4 Knöpfe** Home, Shorts, Create, You, nur Beschreibung, kein Text, kein Abos. `selected` stimmt. Auf Abos meldet die Leiste Home. Unsere zeigt englische Ersatztexte. |
| 6 Tablet-Fixes | OK: (a) Farbe und Höhe hell und dunkel richtig. (b) Suchverlauf mit Zeile "shorts": keine Regal-Meldung, Suchfeld bleibt leer. Suche 3/3 im letzten Stand (vorher 5/5). |
| 7 Regale | Abos: OK, Karten einzeln weg ("Hide"), nur "Shorts >" bleibt, normale Videos unberührt, unsere Leiste bleibt ganz stehen. Startseite und unter Videos kam kein Regal: **⋮ → "Not interested" nicht geprüft.** Letzte Prüfung einen Build vor dem letzten Fix (der betrifft nur das Schliessen des ⋮-Menüs). |
| 8 Rest | OK: Video öffnen/verlassen, Mini-Player mit allen Tabs, Plus → Kamera → X und Zurück, Suche, Benachrichtigungen. Echte nie sichtbar ausser Bekanntem: Kamera mit Zurück-Taste verlassen, 3 Bilder. |

**Behoben am Handy (Tablet noch nicht geprüft):**
- **Abos ging nicht:** `findTopTab` kannte "Subs" nicht und nicht die Beschreibung am Reiter, und von You aus gibt es gar keine Reiter. Jetzt über die ID, "Subs" in `selectors.json`.
  Sind die Reiter nicht da: erst Startseite klicken, nötigenfalls kurz nach unten wischen (`nudgeFeedDown`), dann Reiter (`openSubsFromTop`). Zurück auf Home genauso über den Reiter.
- **Auswahl beim Scrollen auf Abos:** Reiter weg → Auswahl sprang auf Home. Bleibt jetzt auf Abos.
- **Kanal-Seite:** Der Reiter "Shorts" galt als Regal (die Reiter-Leiste meldet sich nicht als scrollbar). Wir tippten den Reiter daneben und drückten Zurück, danach bis zum Neustart kein Regal-Ausblenden. Jetzt nicht mehr.
- **Halbes Profilbild:** Foto beim Video verlassen zeigte die drüberrutschende Video-Seite, blieb bis zum nächsten Foto (bis 30 s). Jetzt 1,5 s Abstand (`SAMPLE_AFTER_VIDEO_MS`).
- **⋮ auf gescrollter Startseite:** Daneben getippt oder Menüpunkt gewählt → unsere Leiste weg, bis die echte wieder auftaucht ("Tipp nach dem Scrollen"). Das war vermutlich das "verschwindet beim Scrollen". Liste wird jetzt gemerkt.
- **Regal-Karten:** (a) Passen die letzten Karten aufs Bild, ist die Reihe nicht scrollbar → nie ausgeblendet (`isNarrowCardList`). (b) Auf stiller Seite kam nach der ersten Karte kein Scan mehr (`shelfRetry`). (c) Menü kommt spät, während ein Video anläuft → 3 s warten, nicht gleich aufgeben. (d) Beim Scrollen galt das Karten-⋮ als Regal-⋮ → "Hide" zählt jetzt auch dort. (e) Unsere fehlte 0,13 s, solange das Menü offen war (`SHELF_MENU_HOLD_MS`).

**Bleibt, nicht behoben:**
- App-Update auf gescrollter Seite: unsere fehlt, bis die echte wieder auftaucht (die Sonderregel `restoredState` gilt nur für Home). Echte ist dabei nicht sichtbar.
- Andere Sprachen mit 4 Knöpfen ungeprüft: `collectByPosition` kennt die Reihe Home, Shorts, Create, You nicht (bricht bei Knopf ohne Text ab). Kurzform des Abos-Reiters kennen wir nur für Deutsch und Englisch.

**Beim Testen beachten:**
- **Nie `am force-stop app.fshorts`:** Android nimmt den Dienst dann aus `enabled_accessibility_services`! Passiert (sofort mit exakt gleicher Liste wiederhergestellt). Zum Neu-Laden nur `am start`.
- `env.sh`-Auswertung (`analyze`, `shortsdet`) taugt fürs neue Design nicht: `tools/pixel-test/phone.sh` nehmen. `barcheck` schlägt falsch an bei der "Short"-Pille der Kamera und beim App-Zoom: Bilder ansehen.
- **Nie blind tippen, auch nicht `input text`/Enter:** Tasten ohne Suchfeld öffneten den eigenen Kanal. Zurück vom Video führt je nach Zustand zu Kommentaren statt in den Mini-Player. Die Liste verschiebt sich nach dem Video und beim Anlaufen: Ein Tipp aufs ⋮ traf so eine Werbung (Chrome ging auf). Immer `bartap`/`tapif`.
- Das Handy wird manchmal nebenbei benutzt (Aufnahme zeigte andere Apps, ein "Save to Watch later" ohne Befehl von uns). Vor Tipps prüfen, was vorne ist.
- Weil die Handy-Sprache Englisch ist, findet `videopos`/`menupos` in `suite.sh` nichts (Wörter Deutsch; ein laufendes Video in der Liste zeigt seine Knöpfe nicht in `uiautomator`).

### Tablet nochmals (01.11.2026, Galaxy Tab A8, Android 14, YouTube 21.43.523, Build 0.1.87 aus `wip/handy-0.1.87`)
Enthält alle Handy-Fixes. Das Tablet hat das neue Design weiterhin NICHT (oben nur Chips, echte Leiste 5 Knöpfe mit Text), die Reiter-Wege laufen dort also nicht.

| Punkt | Ergebnis Tablet |
|---|---|
| Scrollen | OK: 120 s, Leiste weicht in keinem Bild ab, echte nie sichtbar, kein "Leiste nicht mehr da". |
| Abos-Knopf, Auswahl | OK: 10/10 Abos (5 von Startseite, 5 von Mein YouTube). Je 12 Proben auf jeder Seite: Leiste unverändert, Ring nur auf Mein YouTube. Nur dunkel geprüft. |
| Kanal-Seite | OK: 2 Kanäle, Reiter bleibt auf "Übersicht", keine Regal-Meldung. |
| ⋮ auf gescrollter Startseite | OK: 3/3 (daneben, Zurück), unsere bleibt, abgedunkelt solange offen. |
| Profilbild nach Video | OK: Foto erst nach 1,5 s, ganzes Bild. |
| Video verlassen | OK: 4×. Wischen: echte 3 Bilder (bekannte Grenze 0–3). |
| Suche, Plus | OK: Suche 3/3, Plus 3× (Fotos-Frage mit Zurück geschlossen, nichts erlaubt). Echte nie sichtbar. |
| App-Update | OK: Update auf gescrollter Startseite und Abos, unsere sofort da (echte bleibt dort beim Scrollen stehen, also kein Beweis für das Handy). |
| Leere Wortliste | OK: Testbuild ohne Wörter für die Knöpfe, 6/6 Tabs (5-Knopf-Leiste). |
| Regal-Karten | NICHT geprüft: Auf Abos kam in 12 Wischern kein Regal. |
| Hinweis | Erscheint auf dem Tablet, absichtlich nicht zugestimmt (war nicht Teil des Auftrags). |

**Handy-Nachtest (01.11.2026, Pixel 9, Abend, sauberer Build 0.1.87 = dieser Stand, vorher Zweig `wip/handy-0.1.87`, jetzt gemergt):**
| Punkt | Ergebnis Handy |
|---|---|
| "YouTube öffnen" und andere rote Knöpfe | OK: weisse Schrift (`onPrimary`). "Zustimmen" selbst nicht geöffnet (Hinweis ist schon bestätigt), selbe Farbregel. |
| You → Home | OK: 6/6, im Protokoll nie Abos dazwischen; "Reiter oben geklickt" nach 40–130 ms (`leaveSubsTopTabSoon`). |
| Update auf gescrollter Startseite und Abos | OK: `adb install -r -d`, je ~1,7 s keine Leiste (Dienst startet neu), echte nie sichtbar, danach unsere mit richtiger Auswahl. Kein Eintrag verloren in der Bedienungshilfen-Liste. |
| Leere Wortliste (Testbuild, danach wieder sauberer Build) | OK: 11/11 Tipps inkl. aus gescrollter Liste; Kanal-Seite: Reiter bleibt auf Home, Abos von dort geht. |
| Regression Scrollen | OK: 60 s, 2601 Bilder, Abweichung höchstens 1,49 (nicht gemessen über 1,5). |
| Regression Abos | OK: 10/10 (5 von Home, 5 von You), kein Fehler im Protokoll. |
| Regression ⋮ | OK: Normales Menü (Video und Beitrag) auf gescrollter Liste, unsere bleibt; Werbe-⋮ ("My Ad Center") 3/3, Leiste danach da. Nichts gewählt. |
| Regression Video verlassen | OK: 4/4 mit Zurück. `barcheck` meldete 3× "ECHT" 1 Bild: Fehlalarm (heller Inhalt bei Abdunkelung, im Bild geprüft). |
| Regression Suche, Plus | OK: je 3/3, Leiste danach da. |

Test-Fallen: `uiautomator dump` nimmt unsere Leiste für ~2 s weg, ein Foto gleich danach sieht wie ein Fehler aus: ≥6 s warten. `barok` allein sagt nichts über den Vordergrund (auch auf dem Startbildschirm "ok"): immer mit `focus` prüfen. Zurück auf Abos oder Startseite verlässt YouTube schnell. Aufnahmen brauchen viel Platz: Festplatte des Heim-PCs lief dabei voll (C: 223 GB).
Hinweis: `screenrecord` schreibt auf still stehendem Bild manchmal keine Datei; dann mit Einzelfotos prüfen. Lokaler Build 0.1.87 kann neuer sein als die Release-Nummer: `adb install -r -d`.

### Tablet (30.10.)
30.10.2026, Heim-PC "Admin", Galaxy Tab A8 (SM-X200, Android 14), **YouTube 21.43.523**, YouTube dunkel, Gerät hell.
Zuerst Release build-83 geprüft, danach lokaler Build mit den zwei Fixes unten (Stand dieses Commits).

| Punkt | Ergebnis Tablet |
|---|---|
| 1 Scrollen | OK: 170 s Startseite (3973 Bilder), dazu Abos/Startseite (2811 Bilder): echte nie sichtbar, unsere nie weg, kein "Leiste nicht mehr da". **Nicht geprüft: Video in der Liste läuft an** – das gibt es auf dem Tablet nicht (Bild steht in den Pausen still). Am Handy prüfen! |
| 2 Profilbild | OK: Ring nur auf Mein YouTube, hell und dunkel |
| 3 Auswahl | OK: je >10 s auf jeder Seite, kein Springen, Logcat immer nur ein Tab gemeldet |
| 4 Abos-Knopf | OK: 10/10 (5 von Startseite, 5 von Mein YouTube), Seite bleibt offen |
| 5 Neues Design | Auf dem Tablet NICHT vorhanden: oben nur Chips ("Alle, Musik, …"), keine Reiter. Echte Leiste `pivot_bar` [0,1776][1200,1848] mit 5 Knöpfen (Startseite, Shorts, Erstellen, Abos, Mein YouTube), Texte da, `selected` stimmt (Startseite und Mein YouTube geprüft). Ist noch ungetestet, ob `findTopTab` am Handy greift. |
| 6 Rest | OK: Video öffnen/verlassen 4/4, Plus→Kamera→X 5/5, Suche 5/5, Startseite/Abos/Mein YouTube je 5/5 |

**Zwei Fehler behoben (Tablet geprüft, Handy noch nicht):**
- **Leiste weiss auf dunklem YouTube:** Gespeicherte Ruhelage war 144 statt 72 px hoch ([0,1776][1200,1920], Herkunft unklar, wohl ein einzelner
  Messwert). Das Tablet lernte eine kürzere Lage nie (Schutz gegen den Kamera-Fehler), `bar.bounds == restBounds` stimmte nie: keine
  Farbe, kein neues Profilbild, unsere deckte Samsungs Taskleiste ab. `rememberRestPosition`: gleiche Oberkante + kürzer = neu lernen
  (Logcat "Ruhelage zu hoch"). Korrigierte sich sofort beim Start.
- **Suchverlauf "shorts" galt als Regal:** Zeile hiess genau wie die Überschrift. Wir tippten ihren Pfeil (Suchfeld füllte sich mit "shorts"),
  fanden keinen Menüpunkt, drückten Zurück und setzten `shelfHideBroken` (bis zum Neustart keine Regale mehr ausgeblendet). `shelfFor` nimmt
  keine Zeilen mit `edit_suggestion` mehr. Geprüft: Suche 5/5 ohne "Regal"-Meldung. **Regal-Ausblenden selbst nicht neu geprüft:** in diesen
  Tests kam auf dem Tablet kein Regal vor. Am Handy prüfen.

**Bleibt, nicht behoben:**
- Video verlassen per Zurück-Wischen, Werbung lief: echte Leiste 4–5 Bilder (~0,13 s). Bekannte Grenze (siehe oben: 0–4, mit Werbung bis ~10).
- Kamera mit Zurück-Taste verlassen: echte 1–3 Bilder (mit X: keine). Zurück meldet keinen Klick, darum kein Vorab-Zeigen.

**Beim Testen beachten:**
- Keine Tipps ohne Prüfung: Ein blinder Tipp traf eine Premium-Werbekarte, ein Wischen in der Listenmitte öffnete ein Mitglieder-Video mit
  Mitgliedschafts-Blatt. Beides ohne Auswahl geschlossen, nichts gekauft, keine Berechtigung erteilt. Darum neu in `tab.sh`: `tapbar` (bricht ab,
  wenn der Tipp nicht ankommt), `focus`; auf dem Tablet am Rand wischen (x=1188).
- `realdet` schlägt beim Hochfahren der Fotos-Frage falsch an (weisse Fläche): immer Bilder ansehen. Die Frage kommt bei jedem Plus; nur mit Zurück
  schliessen, Berechtigungen bleiben aus.

**Offen:** siehe oben (Regal-Karten auf der Startseite).

## 02.11.2026 (Cloud, NICHT am Handy getestet): Neue App-Oberfläche + Superwall
Nach dem Design-PDF des Nutzers (Onboarding, Status/Settings/Pro, About), aber im Stil der Superwall-Paywall
(Schwarz → Rot, weisse Knöpfe mit roter Schrift im Onboarding). App-Texte jetzt Englisch.
- `ui/Theme.kt` (Farben, `BrandLogo` gezeichnet, `GlassCard`), `ui/Onboarding.kt` (Willkommen → Vorher/Nachher →
  "Turn it on" mit vollständigem Hinweis `Disclosure`, Zustimmen/"Not now", Blatt "Switch locked?"),
  `ui/MainTabs.kt` (Status, Settings mit Theme System/Dark/Light, Debugging, About, Pro). `MainActivity.kt` verbindet.
- **Superwall 2.9.0**: `FShortsApp` (Application) mit `Superwall.configure(pk_… der Android-App; der erste Schlüssel war der der Apple-App → falsche Paywall "Theo")`, Paywall-Activity im Manifest,
  `INTERNET` + `BILLING`, `POST_NOTIFICATIONS` entfernt (`tools:node="remove"`). Paywall (`app_start`) kommt einmal,
  sobald nach dem Onboarding die Bedienungshilfe an ist, und über Pro/"Time saved". Produkte: `fckshorts_pro`
  (Base Plans `monthly` mit 3 Tagen gratis, `yearly`). **Keine Sperre:** Die Leiste funktioniert ohne Abo.
- Hinweis-Schlüssel `disclosure_accepted_v3` (nennt Superwall/Google Play), dazu `onboarding_done`, `theme_mode`.
- Noch nicht: App-Icon-Auswahl aus dem Design (Varianten), neues Launcher-Icon.
**Am Handy prüfen:** Onboarding (alle 3 Seiten, "Switch greyed out?"), nach dem Einschalten kommt die Paywall,
Status an/aus, Theme hell/dunkel, Pro, Debugging-Teilen. Leiste in YouTube unverändert? Superwall-Start im Logcat
(`adb logcat -s Superwall`). Der Dienst läuft im selben Prozess wie Superwall – auf Ruckler achten.

### 02.11.2026, später (Cloud, NICHT am Handy getestet): Startseite und Einstellungen neu
Wunsch des Nutzers: Debugging-Bereich weg, Regal-Schalter und Regal-Zahl weg (Regale werden **immer** im
Hintergrund ausgeblendet, `Prefs.autoHideShelf` gibt fest `true`), keine eigene Pro-Seite (Pro = immer unsere
Superwall-Paywall `app_start`), Hinweis im Onboarding aufklappbar ("Show details", Knöpfe direkt darunter).
Neu: `ui/Home.kt` – eine Startseite (Logo, grosser Status-Kreis, "Shorts closed", "Time saved" mit PRO-Pille →
Paywall, Knopf "Open YouTube"/"Turn on protection"), Zahnrad oben rechts → Settings (Subscription: "Get Pro"-Karte
im Paywall-Stil bzw. "Manage subscription"; Restore; Theme; Rate; Privacy & permissions; Version). `ui/MainTabs.kt`
gelöscht. Fehler-Dateien werden weiter still gespeichert (nur per adb), lange Drücken auf unsere Leiste speichert
weiterhin. Hinweis-Schlüssel bleibt v3 (v3 war nur im Test).

### 02.11.2026, Abend (Cloud, NICHT am Handy getestet): Sperre ohne Abo, Code, Leiste unten zurück
- **Ohne Abo/Testzeit oder Code tut der Dienst nichts** (`Access.unlocked`, geprüft in `onAccessibilityEvent`,
  `scanInternal` und `pollRunnable`; unsere Leiste wird versteckt, YouTube bleibt unverändert). Abo-Stand kommt von
  Superwall (`subscriptionStatus`, Testzeit = Active), wird in `FShortsApp` gesammelt und in Prefs gemerkt
  (`subscribed`), damit der Dienst nach einem Neustart sofort Bescheid weiss.
- **Gratis-Code** in Settings → "Redeem code": "Ningeninge" schaltet frei, "Ningeningestop" nimmt es weg
  (`Access.redeem`, Prefs `promo_unlocked`). Achtung: Der Code steht lesbar in der APK.
- Ablauf: Onboarding → "Agree & open settings" → zurück und eingeschaltet → Startseite + Paywall `app_start`
  (3 Tage gratis). Ohne Freischaltung zeigt die Startseite "off" und "Try 3 days free".
- **Leiste unten wieder da:** Status, Settings, Pro (Pro = Paywall; mit Abo nur Hinweis "You already have Pro").
- **"Run setup again"** ganz unten in Settings startet das Onboarding neu. Ist die Bedienungshilfe schon an, heisst
  der letzte Knopf "Continue".
- Solange die Abos in Google Play nicht live sind, geht die App nur mit dem Code!

### 02.11.2026, Nacht (Cloud, NICHT am Handy getestet): Paywall im Onboarding, Superwall-Testmodus
- Nutzer: Paywall kam nie, Pro-Knopf tat nichts. Vermutlich keine Kampagne mit Placement `app_start` und/oder keine
  Produkte in Google Play. Jetzt: `showPaywall()` mit `PaywallPresentationHandler` → Toast mit Grund
  (PlacementNotFound, NoAudienceMatch, Fehler). **Superwall-Testmodus** `TestModeBehavior.ALWAYS` in Debug-Builds
  (alle GitHub-APKs): Produkte aus dem Dashboard, Kauf nur simuliert (Schublade "Start Free Trial"), beim Start ein
  Test-Modus-Fenster. Release-Builds: normal.
- Onboarding hat Schritt 4 "Last step: start your free trial" (`TrialPage`): Knopf → Paywall, "Restore purchases",
  "Have a code?". Fertig erst, wenn Abo/Testzeit oder Code aktiv (`isPro`), dann automatisch zur Startseite.
- Pro unten: mit Abo → Google-Play-Abo-Seite (Wechsel auf Jährlich), mit Code → Hinweis, sonst Paywall. In Settings
  mit Abo kein "Get Pro" (war schon so).

### 03.11.2026 (Cloud, NICHT am Handy getestet): Schutz pausieren mit Wartezeit, Abo beendet, Icon, Hochformat
- **Hochformat:** `MainActivity` und Paywall `screenOrientation="portrait"` (Overlay unverändert).
- **Onboarding:** Schritt "Last step" aus (`SHOW_TRIAL_STEP = false`, `TrialPage` bleibt im Code). Nach Zustimmen +
  Bedienungshilfe an kommt direkt die Paywall; fertig, sobald Testzeit/Abo/Code aktiv. "Continue" zeigt sonst wieder
  die Paywall.
- **Kein "Try 3 days free"** mehr auf Startseite/Settings (wer dort ist, hat Testzeit/Abo/Code).
- **Abo beendet** (Onboarding fertig, aber kein Abo/Code): `ExpiredScreen` ("Your subscription has ended",
  "Resubscribe" → Paywall, Restore, Code). Danach automatisch Startseite.
- **Schutz ausschalten nur übers Schild auf der Startseite:** Spruch (`SHORTS_QUOTES`) → "Start N min timer"
  (zuerst 15 min, Prefs `unlock_at` als Uhrzeit, läuft bei geschlossener App weiter) → nach Ablauf Schild →
  "Turn off" → `Protection.paused`. Pausiert: Shorts dürfen laufen (`shortAllowed` → true), keine Regale
  ausblenden, **unsere Leiste bleibt**. Wieder einschalten: Wartezeit fürs nächste Mal wählen (5, 10, 15, 20, 30,
  45, 60, 120 oder eigene, mind. 5; Prefs `next_wait_min`).
- **App-Icon:** adaptives Icon, roter Hintergrund #E5172B, weisse Karte mit Schnitt (wie `BrandLogo`), PNGs gelöscht.
- Überblendung beim Wechsel Onboarding → Startseite (`AnimatedContent` in `App()`).

### 03.11.2026, später (Cloud, NICHT am Handy getestet)
- Startseite scrollt nicht mehr (Abstände mit `weight`). Onboarding: Zurück-Geste/Taste → vorige Seite
  (`BackHandler`), Seitenwechsel gleiten seitlich.
- Codes: "Ningeningestop" setzt die **ganze App** zurück (`Prefs.clearAll`, `Superwall.instance.reset()`,
  Onboarding von vorne), "Ningeningetimer" = Wartezeit 5 s (Prefs `fast_timer`).
- Ausschalt-Fenster gross im Paywall-Stil (`ProtectionSheet`), härtere Sprüche (`SHORTS_QUOTES`).

### 03.11.2026, Abend (Cloud, NICHT am Handy getestet)
- Onboarding "Turn it on": Ist alles erledigt ausser der Testzeit, heisst der Knopf "Start 3 days free" (Paywall), dazu
  "Restore purchases" und "Have a code?" (vorher kam man ohne Paywall nicht mehr raus, z. B. bei "no audience match").
- Ausschalt-Fenster zeigen die eigenen Zahlen (`progressLine`), Wieder-Einschalten (`RelockDialog`) jetzt gross im
  Paywall-Stil, kurze Wartezeiten werden madig gemacht.

## Was bis "Overlay KOMPLETT fertig" noch fehlt (Stand 27.10.2026, 23:35)

Siehe "Noch offen" direkt oben. Danach: längerer Alltagstest durch den Nutzer (ein paar Tage), Fehler mit
`adb logcat -s FuckShorts` belegen.

Später (bewusst nach hinten geschoben):
- Aussehen mit Claude Design; andere Handys/Grössen, andere Sprachen (Beschriftungen in `selectors.json`).
- Nach jedem YouTube-Update kurz `suite.sh` laufen lassen (YouTube ändert IDs und Abläufe oft, z. B. 21.42:
  ⋮ als Popup, "Kamera schließen").

## Neu am 26.10.2026, Vormittag (Cloud-Sitzung)

- **⋮-Menü:** Statt unsere Leiste zu verstecken (dann sah man abgedunkelte Wortreste der echten), zeigt
  `BottomBarOverlay.showUnderMenu` sie abgedunkelt (`BarUiState.dim`) mit einem durchsichtigen Loch, wo das
  Menü liegt (`hole`, Ecken 16 dp, `BlendMode.Clear` in Offscreen-Ebene), und lässt Berührungen durch.
  Die Abdunkelung misst `FuckShortsService.measureMenuDim` einmal per `takeScreenshot` (hellstes Pixel der
  echten Leiste neben dem Menü im Icon-/Schriftbereich, Referenz 245) und speichert sie (`Prefs.menuDim`).
  Bis zur ersten Messung: wie früher versteckt. Prüfen: Stimmt die Helligkeit? Sitzt das Loch richtig?
  Falls falsch gemessen: `adb shell pm clear app.fshorts` setzt es zurück (löscht aber alle Einstellungen).
- **Kamera schliessen:** Tipp auf Schliessen/Zurück im Fenster ohne Leiste (`closesBarlessPage`) zeigt
  unsere Leiste sofort und hält sie 0,7 s (`PRE_SHOW_HOLD_MS`). Zurück-Geste löst das nicht aus.
- Regal ausblenden: Menüpunkte jetzt auch "Weniger Shorts anzeigen"/"Show fewer Shorts".
- Startseiten-Regal-Ausblenden weiter ungetestet in dieser Version (in Version 3 hat es einmal geklappt:
  YouTube zeigte "Ok, wir zeigen dir weniger Shorts im Startseitenfeed").

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

Fertige Werkzeuge: `tools/pixel-test/env.sh` (Aufnehmen, Bild-für-Bild-Auswertung mit ffmpeg: erkennt die echte
Leiste an den hellen Stellen von Shorts-/Erstellen-Knopf) und `tools/pixel-test/suite.sh` (Tests `t_scroll`,
`t_menu`, `t_cam`, `t_video`, `t_tabs`, `t_pages`). Verdächtige Bilder immer mit `frames`/`strips` ansehen.

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
Auf dem Arbeits-PC (A023985, 26.10.2026) gibt es kein JDK: dort nur über GitHub bauen. adb und ffmpeg liegen
ohne Admin-Rechte unter `%USERPROFILE%\tools` (platform-tools, ffmpeg-master-latest-win64-gpl).
Lokale Builds haben versionCode 1 → `adb install -r -d`.
Arbeits-Branch: `claude/intelligent-volta-4lochv`.

### 03.11.2026, Nacht (Cloud, NICHT am Handy getestet)
- Erste Wartezeit fürs Ausschalten jetzt **10 min** (`Prefs.nextWaitMinutes` Standard 10).
- Ausschalt-Fenster: Spruch je nach Tag seit Beginn (`progressLine`, Tag 1–6 je eigener, dann Woche, 2 Wochen, Monat,
  Monate, halbes Jahr, Jahr), dazu die eigenen Zahlen. Beginn = Prefs `started_at` (beim ersten Lesen gesetzt,
  "Ningeningestop" setzt zurück).
- Wartezeit abgesessen: "Keep protection on" verschwindet, "You can turn protection off now" gross.
- Superwall-Paywall: Texte je nach `products.hasIntroductoryOffer` (Testzeit schon gebraucht → ohne Testzeit-Text).
- **"Ningeningestop" → keine Paywall ("no audience matches"):** Kampagne "Onboarding" (Projekt 43741, App 58868,
  Placement `app_start`) zeigt die Paywall nur ohne aktives Abo. `Superwall.reset()` behält im Testmodus die gespielte
  Testzeit. Jetzt `FShortsApp.clearTestPurchases()` (per Reflexion: Test-Entitlements leeren, Status Inactive).
  Superwall-CLI in der Cloud: `NODE_USE_ENV_PROXY=1 NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt superwall …`.

### 03.11.2026, spät (Cloud, NICHT am Handy getestet)
- Code **"Ningeningestop" entfernt** (Reset half auch mit `clearTestPurchases` nicht; Nutzer testet neu per Update).
  `Prefs.clearAll` und `clearTestPurchases` gelöscht. Codes jetzt nur "Ningeninge" und "Ningeningetimer".
- Onboarding: Paywall kommt NICHT mehr von selbst, wenn die Bedienungshilfe schon an ist (Seite "Turn it on" wurde
  übersprungen), nur nach Tipp auf "Continue". Knopf heisst "Continue" statt "Start 3 days free", darunter nur noch
  "Have a code?" (Wiederherstellen gibt es in der Paywall).
- "Run setup again" läuft ohne Paywall (`rerunSetup`, "Continue" beendet direkt; `onboarding_done` bleibt gesetzt).

### Nachttest 02./03.11.2026 (Pixel 9, YouTube 21.43, Build 0.1.1 / lokal): Ergebnis
Einzelheiten und Belege: `docs/nachttest.md`.

| Punkt | Ergebnis Handy |
|---|---|
| A1 Onboarding | OK: neuer Nutzer und "Run setup again", Zurück, Show details, "Switch locked?", Continue → Paywall. "Have a code?" steht über "Continue" (nicht darunter) |
| A2/A3 Paywall, Abo beendet | OK mit Testmodus. Ohne Testzeit: Paywall-Text bleibt "Try 3 days free" (Dashboard-Text, nicht der Code) |
| A4 Codes, A5 Start, A7 Settings, A8 Pro, A9 | OK |
| A6 Schutz aus/an | OK, auch echter 10-min-Timer (601 s) |
| B10–B13, B15 | OK hell und dunkel; echte Leiste nie sichtbar (ausser gesperrt: gewollt) |
| B14 Regale | Abos OK. Startseite: nie ein Regal (⛔, ~150 Wischer) |
| Behoben | Statusleiste im Light-Theme (`MainActivity`); Leiste über eigener App bei Bild-in-Bild (`foregroundWindow`) |
| Nicht lösbar | Drehen per Befehl, Paywall-Text ohne Testzeit |
### 03.11.2026 (Cloud): Paywall-Links, Datenschutzerklärung
- Superwall-Paywall 287624: "Terms of Service" + "and" gelöscht (Vorlage zeigte auf Apples Standard-EULA). "Privacy Policy"
  zeigt jetzt auf https://www.freeprivacypolicy.com/live/478be282-9e4c-4797-83de-8e73fbdf6626 (vorher auch Apple-EULA).
  Nutzer muss im Editor Save/Publish drücken. Keine eigenen AGB (Wunsch des Nutzers, nicht Pflicht).
- Datenschutzerklärung: FreePrivacyPolicy (Konto des Nutzers), GDPR + Payments gekauft (48 $), Text wird per "Edit Text"
  korrigiert (keine Cookies/IP/Konten/Marketing, Alter 13, Absatz Bedienungshilfe, Superwall). Anbieter = Elternteil
  (Platzhalter). Prüfung: Gratis-Rechtsauskunft ZAV (15 Min.), HSG Law Clinic.

### 03.11.2026, Nachmittag (Cloud, NICHT am Handy getestet): Datenschutz Version 2 + App
- Erklärung live (FreePrivacyPolicy, 48 $ GDPR+Payments), Version 1 Zeile für Zeile mit der Live-Seite verglichen.
  Zweitmeinungen geprüft → `docs/play-store/privacy-policy-final.md` Version 2 (noch NICHT live): Anbieter = Privatperson,
  Superwall Auftragsverarbeiter laut DPA (SCC Modul 2, UK-Addendum, Schweizer Anpassung; KEIN DPF), Google eigenständig,
  Löschen über Support-ID. Superwall-SDK-Fakten: Android-ID (`Settings.Secure.ANDROID_ID`), IP über superwall-Hosts,
  Install-Referrer, App-Events; MMP-Attribution im Konto aus (`static_config`: mmp.enabled=false), keine Werbe-ID.
- App: `FShortsApp.PRIVACY_POLICY_URL` (Link in Settings "Privacy policy" + im `Disclosure`), `FShortsApp.supportId()`
  (= `Superwall.instance.userId`, Settings "Support ID", Tippen kopiert), `res/xml/data_extraction_rules.xml` (kein
  Cloud-Backup, kein Gerät-zu-Gerät-Umzug; allowBackup=false reicht ab Android 12 nicht), Disclosure: Berührungen,
  Screenshots, Superwall-Daten (IP, Geräte-ID), Ausschalten. Hinweis-Schlüssel bleibt v3.
- Offen für die Rechtsauskunft: EU-/UK-Vertreter (Art. 27), ePrivacy-Einwilligung für Superwall-Statistik.
