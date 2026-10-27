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

In Arbeit (nicht fertig getestet): **Video-Vollbild quer und zurück** – unsere blitzte nach dem Zurückdrehen über
der Video-Seite auf / Video galt als verlassen. Ursachen: (1) Vollbild-Player füllt den Bildschirm, `isLargePlayer`
verlangte ≤70 % Höhe → jetzt `fullscreen` erlaubt; (2) YouTube meldet beim Drehen Zwischenlagen (-740, 46) und den
Player schon gedreht, bevor Android die neue Grösse meldet → `onScreenSizeChanged` (Scan, Bild-für-Bild, Android,
was zuerst kommt) + `playerRotating` (Player so breit wie Bildschirm nach dem Drehen) → `watchRelearnUntil`
(1,5 s nur sinnvolle Lagen lernen, nie "verlassen"). Noch ein "DBG leave"-Log drin. Testen mit Video per Link,
`user_rotation 1`, `0`, dann Wisch nach unten.

Noch nicht getestet: Shorts-Regal wegklicken (Startseite 35× gescrollt: kein einziges Regal, im Baum kein "Shorts";
in Suchergebnissen gibt es Shorts-Raster mit anderer Überschrift, z. B. "Welpe" – die erkennt `findShelves` nicht),
Anruf, Split-Screen (Nutzer fragen), Kamera-Aufblitzen, dunkles Design für die übrigen Fälle, `suite.sh` gesamt.

## Was bis "Overlay KOMPLETT fertig" noch fehlt (Stand 26.10.2026, Nachtrag 27.10. oben)

Muss:
1. Video verlassen: 1–2 Bilder schmaler Streifen der echten Leiste (siehe "Noch offen"). Idee: während ein Video
   offen ist, unsere Leiste unsichtbar *bereithalten* und schon beim ersten Zeichen (Berührung + Wisch nach unten
   auf dem Player, oder TYPE_WINDOW_STATE_CHANGED) zeigen; oder Leiste der Video-Seite selbst beobachten.
2. Tastatur mit Zurück schliessen: ~3 Bilder echte Leiste durch die ausblendende Tastatur. Idee: Lage des
   Tastatur-Fensters / Insets beobachten statt nur "Fenster da".
3. Helles Design komplett testen (alle Tests aus `tools/pixel-test/suite.sh`), auch Umschalten hell↔dunkel.
4. Werbe-Menü (⋮ bei Werbung, `app_engagement_panel`) nach der Korrektur testen.
5. Nach Dienst-Neustart (App-Update) auf gescrollter Startseite fehlt unsere Leiste: `restBounds` und
   letzten Zustand in `Prefs` speichern und beim Start laden.
6. Plus 5× hintereinander und alle Knöpfe auch auf gescrollter Startseite (Leiste entfernt) 5× testen.
7. Mini-Player: auf Abos/Mein YouTube und gescrollter Startseite prüfen (darf teilweise unter der Leiste sein).
8. Querformat (Auto-Drehen an): Leiste, Video-Vollbild, Zurückdrehen.
9. Sonderfälle: Benachrichtigungen runterziehen, App-Wechsel (Recents), Bildschirm aus/an, Anruf, Split-Screen,
   Short per Link aus WhatsApp (unsere Leiste darf dabei nicht falsch stehen).
10. Längerer Alltagstest durch den Nutzer (ein paar Tage), Fehler mit `adb logcat -s FuckShorts` belegen.

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
