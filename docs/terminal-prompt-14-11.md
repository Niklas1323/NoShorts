# Auftrag für Claude im Terminal (14.11.2026): Overlay fertig machen – perfekt, mit Animationen

Du arbeitest am Heim-PC von Niklas. Das Pixel 9 hängt per USB dran. Ziel: **Unsere Leiste (Overlay) wird ein fertiges,
perfektes Produkt.** Die echte YouTube-Leiste darf man **nie** sehen, kein einziges Bild, und alle Übergänge sollen schöne,
flüssige Animationen haben. Teste alles selbst am Handy, Bild für Bild. Nimm dir so viel Zeit, wie du brauchst.

Niklas schreibt Deutsch (Schweiz, "ss" statt "ß") und möchte sehr einfache Erklärungen. Antworte ihm am Schluss kurz und
einfach: was du gemessen hast, was du geändert hast, was jetzt besser ist (mit Zahlen), was noch offen ist.

---

## 0. Zuerst lesen und einrichten

1. `git pull` auf dem Zweig **`claude/intelligent-volta-4lochv`** (nur dort arbeiten, kein Force-Push).
2. **`CLAUDE.md` ganz lesen.** Dort steht alles Wichtige: Aufbau der App, alle bisherigen Messungen, Test-Fallen, Werkzeuge.
   Besonders die Abschnitte "07.11.2026 (Terminal …)", "08.11.2026 (Terminal …)" und ganz unten "14.11.2026".
3. Stand jetzt: **Build 217** (Commit `5b01910`), in der Cloud gebaut, **am Handy noch nicht getestet**.
4. Lokal bauen: `./gradlew assembleDebug`, installieren mit `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`
   (lokale Builds haben versionCode 1, darum `-d`).

### Sicherheitsregeln (von Niklas, gelten immer)
- **Nie `am force-stop app.fshorts`** – Android schaltet sonst die Bedienungshilfe ab. Zum Neuladen nur `am start`.
- **Nichts löschen, nichts posten, kein `pm clear`, nichts kaufen, niemanden anrufen** auf seinem Handy oder Konto.
- **Keine Berechtigungen erteilen**, ohne Niklas zu fragen (die Fotos-Frage nach Plus nur mit Zurück schliessen).
- **Nie blind tippen** (auch nicht `input text`/Enter): Vor jedem Tipp prüfen, was vorne ist. Blinde Tipps haben schon
  Werbung geöffnet, Benachrichtigungen aufgezogen, "Save to Watch later" gedrückt. Werbung nicht anklicken, nichts melden.
- In der Kamera nichts speichern oder hochladen. Test-Clips mit "Neu beginnen" verwerfen.
- Das Handy wird manchmal nebenbei benutzt: vor jedem Test schauen, was vorne ist.
- Am Schluss alles zurückstellen: `accelerometer_rotation`, `user_rotation`, `stay_on_while_plugged_in`, Schriftgrösse.

### Git-Regeln
- Kleine Commits, sofort pushen, Commit-Nachrichten beginnen mit **"Terminal:"**.
- Vor jeder Änderung `git pull` (die Cloud-Sitzung arbeitet evtl. parallel).
- Nach dem Test **CLAUDE.md** ergänzen: neuer Abschnitt mit Datum, Tabelle "Test | vorher | nachher | behoben?".

---

## 1. Gerät und Umgebung

- Pixel 9, Android 17, Benutzer Niklas (u10), Handy-Sprache en-CH → YouTube ist **Englisch**.
- YouTube 21.4x mit **neuem Design**: oben Reiter `Home | Subs | Music | Live | More` (nur auf der Startseite), echte
  Leiste unten mit **4 Knöpfen ohne Text**: Home, Shorts, Create (+), You (rotes F*ck-Shorts-Profilbild).
  **Abos gibt es nicht in der echten Leiste**, nur als Reiter oben auf der Startseite.
- Unsere Leiste: Home, Subscriptions, You (rotes Profilbild), Plus (grauer Kreis), mit Text, Hintergrund #1C1C1C (dunkel).
- Auch **ScreenZen** (andere Bedienungshilfe) ist an.
- Die App ist ohne Abo/Code gesperrt (dann zeichnet sie nichts). Falls gesperrt: In der App Settings → Redeem code
  "Ningeninge" (Test-Code). Nichts kaufen.
- Testwerkzeuge: `tools/pixel-test/phone.sh` (fürs neue Design), `env.sh`/`suite.sh` (alt, für neues Design teils
  ungeeignet). `adb logcat -s FuckShorts` zeigt jeden Grund ("Leiste: …"), warum unsere Leiste steht oder weg ist.

### Bewährte Mess-Methode (bitte so machen)
- `adb shell screenrecord` (60 fps), dann mit ffmpeg **Bild für Bild** auswerten.
- **Echte vs. unsere Leiste im neuen Design sicher erkennen:** am roten Profilbild. Bei **uns** sitzt es an **Platz 3**
  (x≈675, y≈2291), bei der **echten** an **Platz 4** (x≈945, y≈2320). Rote Pixel zählen (R>150, G<90, B<90) in
  x 920–970 / y 2295–2345 (= echte) bzw. x 650–700 / y 2266–2316 (= unsere). Das hat in den Videos vom 14.11.
  zuverlässig funktioniert. Verdächtige Bilder **immer anschauen** (Streifen mit Zeitstempel, z. B. ffmpeg `drawtext`
  + `tile`), Schwellen können bei Werbung oder hellem Design falsch anschlagen.
- Jede Messung mehrmals (mind. 3–5 Läufe), hell UND dunkel, mit und ohne Werbung.
- **Test-Falle:** `uiautomator dump` schaltet andere Bedienungshilfen ~2 s ab (unsere Leiste weg, echte sichtbar). Nie
  während einer Aufnahme dumpen, danach ≥6 s warten. Laufendes Video vorher pausieren (`KEYCODE_MEDIA_PAUSE`).

---

## 2. Was Niklas will (wörtlich zusammengefasst)

- **Die alte (echte) Leiste darf man NIE sehen.** "Manchmal sieht man sie und manchmal nicht" – das muss weg.
- **Animationen behalten und verbessern.** Es soll sich wie ein fertiges, hochwertiges Produkt anfühlen.
- **Video öffnen** (Tipp auf ein Video, unsere Leiste fährt nach unten weg): **perfekt, so lassen.**
- **Video verlassen** (Zurück-Geste, Zurück-Taste, Video nach unten in den Mini-Player ziehen): **"ganz schrecklich"**.
  Unsere Leiste soll im selben Moment da sein, in dem die Startseite sichtbar wird, am besten mit einer schönen
  Animation (z. B. von unten hochfahren, passend zu YouTubes eigener Bewegung). Nie zuerst die echte.
- **Plus (Kamera):** Es soll eine sichtbare, schöne Animation geben, die **sofort** reagiert. Niklas sagt: "Die Animation
  von Plus ist irgendwie gar nicht mehr da." Dabei darf die echte Leiste darunter nicht auftauchen.
- **⋮-Menü (Leiste wird dunkel):** soll animiert dunkel werden, nicht springen. War in Build 216 "ein bisschen zu
  schnell", in 217 langsamer. Prüfen, ob es jetzt schön aussieht (flüssig, kein Flackern), sonst nachstellen.
- **Abos von "You" aus:** geht zuerst auf die Startseite, dann auf Abos. Niklas will **direkt** auf Abos.
- Auch alle anderen Übergänge prüfen und schön machen (siehe Abschnitt 5).

---

## 3. Was in Build 216/217 geändert wurde (Cloud, ungetestet – zuerst prüfen!)

Dateien: `app/src/main/java/app/fshorts/service/BottomBarOverlay.kt`, `FuckShortsService.kt`, `YouTubeScanner.kt`.

1. **⋮-Menü abdunkeln animiert** (`BottomBar` in BottomBarOverlay): Dunkelheit breitet sich wie Tinte vom Menü-Loch her
   aus (`INK_IN_MS` 480), zieht sich beim Schliessen zurück (`INK_OUT_MS` 380); Knöpfe ducken sich auf 94 % und federn
   zurück (`duck`, Feder). War die Leiste versteckt, wird ohne Animation gesetzt (`dimInstant`).
   Hinweis: In YouTube 21.4x dunkelt YouTube beim ⋮-Popup den Rest der Seite gar nicht ab, nur unsere Leiste wird dunkel
   (`popupFront` → grosser Dialog → `DIALOG_DIM`). Prüfen, ob das Abdunkeln dort überhaupt richtig ist oder ob unsere
   Leiste beim ⋮-Popup lieber hell bleiben soll (dann mit Niklas abklären).
2. **Plus am Handy** (`onTabTapped`, `TabKind.CREATE`): Build 216 liess die ganze Leiste nach unten wegfahren → darunter
   4–6 Bilder echte Leiste. Build 217: `overlay.sinkContent()` – nur die Knöpfe sinken weg und blenden aus
   (`SINK_MS` 230), die dunkle Fläche bleibt, bis die Kamera darüber ist; dann versteckt der Scan unsere wie früher.
   Kommt keine Kamera, kommen die Knöpfe nach `CREATE_SINK_RESTORE_MS` (1,5 s) zurück. Tablet: weiter `slideAway`.
   **Niklas findet, die Plus-Animation fehle.** Ziel: eine deutlich sichtbare, schöne Animation ab dem Tipp, ohne echte
   Leiste. Ideen: Knöpfe sinken + Fläche fährt genau in dem Moment nach unten weg, in dem die Kamera die Leiste
   abdeckt (Zeitpunkt messen: Kamera = `CreationModesActivity`, eigenes Fenster, kommt ~20 ms nach dem Klick, ist nach
   ~0,25–0,3 s ganz da). Oder die Fläche mit der hochzoomenden Kamera mitbewegen. Messen, was am besten aussieht.
3. **Werbe-Video** (Tipp auf eine Werbung in der Liste → Seite mit Werbe-Player oben und Blatt "Sponsored … Shop now"
   darunter): Im Video vom 14.11. stand unsere Leiste zeitweise **über** dieser Seite, und beim Verlassen sah man
   ~0,35 s (13–18 Bilder) die echte Leiste. **Vermutung (nicht bewiesen):** Das Blatt ist `app_engagement_panel`, die
   Seite gilt nicht als Video → `handlePanel`; "Panel fährt raus" (Blatt 24 dp tiefer) zeigte unsere; beim Zugehen nur
   Scans (mit Werbung langsam). Build 217: grosses Blatt (Oberkante über 45 % der Höhe, `BIG_SHEET_TOP`) = ganze Seite,
   kein frühes Zeigen; das Panel wird jedes Bild nachgesehen (`checkPanelGone`, `panelNode`). Test-Versionen speichern
   einmal den Aufbau ("panel"-Dump). **Bitte mit `uiautomator dump` (Video pausiert) prüfen, wie die Seite wirklich
   aufgebaut ist** (welche IDs: Player, Blatt), und die Logik danach richtig machen.
4. **Handy: erst zeichnen, dann abfragen**: nach dem Zeigen bis 60 ms keine Abfragen, bis unsere gezeichnet ist
   (`PHONE_DRAW_WAIT_MS`, `drawPending`). Prüfen, dass das nichts verschlechtert.
5. **Abos von You** (`openSubsFromTop` → `waitForTopTabThenClick`): sucht den Reiter oben jetzt jedes Bild (16 ms statt
   50 ms). Trotzdem sieht man kurz die Startseite.

---

## 4. Gefundene Fehler aus den Videos vom 14.11. (Build 216, 52–60 fps)

| Fall | Gemessen | Bemerkung |
|---|---|---|
| Plus (Kamera öffnen) | 2–6 Bilder echte Leiste | durch das Wegfahren in 216; 217 sollte es beheben – prüfen |
| Kamera schliessen per Zurück-Geste | 2–6 Bilder echte Leiste | Android meldet das Hauptfenster spät; früher 1–3 |
| Werbe-Video verlassen (Seite mit "Shop now"-Blatt, Seite blendet aus) | 13–18 Bilder (~0,35 s) echte | unsere kam erst nach ~0,35 s |
| Unsere Leiste ÜBER der Werbe-Seite | mehrmals sichtbar | darf nie sein |
| Normales Video mit Werbe-Kärtchen ("Watch / Learn more") per Zurück verlassen | 15 Bilder (~0,25 s) echte | Seite blendet aus (Predictive Back), Mini-Player fährt runter; Ursache offen |
| ⋮ abdunkeln | sprang in 1 Bild von 40 auf 15 Helligkeit | 216/217 animiert |

Wichtig: Beim Verlassen eines Videos zeigt Android 17 / YouTube eine **Predictive-Back-Animation** (Seite schrumpft/
blendet aus, Startseite kommt dahinter, Mini-Player fährt nach unten). Es reicht nicht, auf den Player "≥ 4 dp tiefer"
zu warten. Finde heraus, welches Zeichen am frühesten kommt (Accessibility-Ereignisse, Fensterliste, Player-Lage,
Mini-Player-IDs `modern_miniplayer_*`, `PANE_APPEARED`), und zeige unsere genau dann – idealerweise mit einer
Animation, die zu YouTubes Bewegung passt. Bei laufender Werbung ist YouTube langsam: Abfragen können 50–150 ms hängen,
blockieren unseren Haupt-Thread und damit auch das Zeichnen unserer Leiste.

---

## 5. Was du testen und perfekt machen sollst (jeweils messen: vorher / nachher)

Jeder Fall: echte Leiste **0 Bilder**, unsere nie über einer Seite, wo sie nicht hingehört, Animation flüssig (keine
Ruckler, kein Flackern, kein Springen). Hell und dunkel. Mit und ohne Werbung.

1. **Video verlassen** – Zurück-Geste (Predictive Back), Zurück-Taste, Video nach unten in den Mini-Player ziehen; normale
   Videos, Videos mit Werbung vorneweg, Werbe-Videos aus der Liste, Videos mit Werbe-Kärtchen, aus gescrollter Startseite.
   Schöne Einfahr-Animation für unsere Leiste (z. B. von unten hoch wie die echte, gleiche Dauer/Kurve – vorher messen,
   wie YouTube seine Leiste einfahren lässt).
2. **Video öffnen** (aus Liste, aus Mini-Player aufklappen, per Link): so lassen, nur prüfen, dass es perfekt bleibt.
3. **Plus → Kamera → X / Zurück-Geste / Zurück-Taste**, Kamera mit Aufnahme (Blatt "Neu beginnen / Entwurf speichern" –
   nur "Neu beginnen" wählen, nichts speichern), Fotos-Frage (mit Zurück schliessen). Sichtbare schöne Animation beim Tipp.
4. **⋮-Menü** auf normalen Videos, auf Werbung (Panel "My Ad Center"), auf gescrollter Startseite; Suchfilter-Dialog.
   Abdunkel-Animation schön, keine Wortreste der echten Leiste.
5. **Tabs:** Home, Subscriptions, You je 5× von jeder Seite; aus gescrollter Liste. **Abos von You direkt** (siehe 6).
6. **Abos direkt:** Mögliche Wege (testen!): (a) YouTube per Link öffnen: Intent `ACTION_VIEW`
   `https://www.youtube.com/feed/subscriptions` mit `setPackage("com.google.android.youtube")` und
   `FLAG_ACTIVITY_NEW_TASK` (Bedienungshilfen dürfen Activities starten). Prüfen: Öffnet YouTube dann wirklich den
   Abos-Reiter, ohne Startseite dazwischen, ohne Flackern, ohne den Zurück-Verlauf kaputt zu machen? Falls nicht oder
   falls nach 600 ms nicht "Abos gewählt": zurück zum alten Weg. (b) Wenn YouTube sich den Reiter merkt: prüfen, ob es
   etwas Sauberes gibt. Wähle, was zuverlässig ist, und miss die Zeit Tipp → Abos sichtbar.
7. **Scrollen** Startseite/Abos/You lange (≥ 60 s), Videos laufen in der Liste an: unsere nie weg, echte nie sichtbar.
8. **Suche** (Lupe, Tastatur, "Suchen", Zurück), Benachrichtigungen auf/zu, App verlassen und zurück, App-Übersicht,
   Bildschirm aus/an, Drehen (Auto-Drehen dafür aus: `accelerometer_rotation 0`, danach zurück), Short antippen
   (muss sofort zugehen), Short per Link + Weiterwischen, Mini-Player auf allen Seiten, Einstellungen, Kanal-Seite.
9. **Regale:** Abos-Regal (Karten einzeln "Hide"), Startseiten-Regal (⋮ → "Not interested") falls eins kommt.
10. Zum Schluss **Regressionstest** über alles mit dem fertigen Build.

Wenn etwas technisch wirklich nicht lösbar ist (Systemanimation von Android, die unsere Leiste nicht enthält), schreib
genau auf, warum, mit Messung – aber versuche vorher alle Ideen. "Ein paar Bilder" ist für Niklas nicht gut genug.

---

## 6. Abschluss

- Alle Änderungen committen ("Terminal: …") und pushen. GitHub baut daraus automatisch die Test-APK (Release
  `build-NNN`). Den fertigen Build am Handy installiert lassen (Dienst an, nicht gesperrt).
- **CLAUDE.md** ergänzen: neuer Abschnitt "15.11.2026 (Terminal, am Pixel getestet)" mit der Ergebnis-Tabelle und den
  wichtigsten Erkenntnissen (z. B. Aufbau der Werbe-Seite, frühestes Zeichen beim Verlassen, Abos-Weg).
- Einstellungen am Handy zurückstellen (Drehen, Wachbleiben, Schrift).
- Niklas eine kurze, einfache Zusammenfassung auf Deutsch geben ("ss"): Was war kaputt, was ist jetzt besser (Zahlen),
  was bleibt und warum.
