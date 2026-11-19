# Bericht Terminal-Sitzung 17.–19.11.2026 (an Claude Code Web)

Hallo Claude Code Web. Die Terminal-Sitzung (Arbeits-PC, Pixel 9, Benutzer Leon) ist fertig. Bitte zuerst `git pull`
auf `claude/intelligent-volta-4lochv`. Alle Einzelheiten und Messwerte stehen in `CLAUDE.md`, letzter Abschnitt
"17.–19.11.2026 … Scrollen wie YouTube, Plus, App-Design V2".

## Bitte als Erstes
Schick Niklas die neuste APK **direkt hier im Chat als Datei** (nicht nur als Link):
- **Build 254** = Commit `d7226f0` (neuster Stand, alles drin):
  https://github.com/Niklas1323/NoShorts/releases/download/build-254/FuckShorts.apk
  (Release "Fuck Shorts Test 254", veröffentlicht 18.11.2026, 23:38 UTC)

## Was gemacht wurde
**Leiste in YouTube (Overlay):**
- **Hochscrollen:** Wie YouTube 21.45 kommt die Leiste nur bei schnellem Hochwischen (gemessen: YouTube erst ab ~1,1 px/ms).
  Langsames oder leichtes Hochscrollen lässt sie unten.
- **Animation wie YouTubes neue Leiste:** Hochfahren stark abbremsend und die Knöpfe ploppen auf. Wegfahren
  beschleunigt, die Knöpfe werden kleiner.
- **"Geht nach viel Hoch/Runter nicht mehr runter":** behoben.
- **Plus:** Plus dreht nicht mehr zum ×. Der Spiegel übernimmt, und die Kamera fährt darüber wie beim Original.
- **Moderne Leiste:** Symbole 14 % grösser und genau mittig.
- **Klassische Leiste:** Beschriftung in YouTubes Sprache ("Startseite", "Abos", "Mein YouTube") statt Englisch.
- **Zusätzlich behoben** (vorher kurz echte Leiste, jetzt 0 Bilder): YouTube mit Zurück schliessen, "Suchen"
  (Tastatur geht zu), Querformat gleich nach dem Drehen.
- **App-Übersicht:** Die Leiste bleibt dort sichtbar (Niklas: "PERFEKT"). Das ist unverändert geblieben.
- **Getestet mit beiden Leisten:**
  - Scrollen (schnell, langsam, gemischt, lang)
  - Tabs, App wechseln, App-Übersicht, Home
  - Benachrichtigungen, ⋮
  - Video verlassen und Mini-Player
  - Suche, Plus/Kamera, quer
  - Google Play (Werbung)
  - Short per Link

  Überall 0 Bilder echte Leiste.

**App-Oberfläche: Design V2 (neu, Standard):**
- **Stil:** flaches YouTube-Schwarz bzw. Weiss, graue Flächen. YouTube-Rot `#FF0033` nur als Akzent, keine Verläufe.
- **Startseite:** grösseres grünes Schild, Zahlen-Kacheln, Leisten-Wahl mit Bildchen.
- **Alles in V2:** Einstellungen, Einrichtung, "Abo beendet", Sprache, Code, Update-Hinweis und Dialoge.
- **Support-ID:** bleibt, weil die Datenschutzerklärung sie braucht. Sie ist jetzt ein Feld zum Aufklappen, und
  Antippen kopiert sie.
- **Dateien:** `app/src/main/java/app/fshorts/ui/HomeV2.kt`, `ui/OnboardingV2.kt`

## Wo die alte Oberfläche (V1) ist
- **In der App:** Test-Versionen (also auch die GitHub-APKs) haben ganz unten in den Einstellungen
  **"Design V1 (Test)"**. Damit wechselt die ganze App sofort auf das alte Design. In V1 steht dort
  **"Design V2 (Test)"** für den Rückweg. Die Play-Store-Version (Release) zeigt immer V2.
- **Im Code:** V1 ist unverändert in `ui/Home.kt` und `ui/Onboarding.kt`. Der letzte Stand nur mit V1 ist Commit
  `0494956`.
- **Als APK nur mit V1 (älterer Stand der Leiste):** Build 248 = Commit `0494956`:
  https://github.com/Niklas1323/NoShorts/releases/download/build-248/FuckShorts.apk
- **Lokal auf dem Arbeits-PC:** `C:\Users\A023985\FShorts-Versionen\fshorts-design-v1-0494956.apk` (V1) und
  `fshorts-design-v2-1330faa.apk` (V2). Die Cloud kommt da nicht dran, darum die GitHub-Links oben.
- **Zurück zu V1 als Standard:** In `MainActivity.kt` ist `designV1` der Schalter (Prefs `design_v1`). Ganz zurück
  auf den alten Code: `git checkout 0494956 -- app/src/main/java/app/fshorts/ui`. Achtung: Danach fehlen die V2-Dateien,
  und `MainActivity`/`Prefs`/`Language`/`UpdateDialog` müssen angepasst werden.

## Wo alles ist
- **Überblick, Messwerte, Tabellen:** `CLAUDE.md` (letzter Abschnitt).
- **Leiste:**
  - `service/BottomBarOverlay.kt`: Animationen, Spiegel, Plus
  - `service/FuckShortsService.kt`: Wischrichtung und -tempo (`startDirectionProbe`/`probeLoop`)
  - `service/YouTubeScanner.kt`: Beschriftungen
- **Neue Texte in 8 Sprachen** (`bar_label_*`, `stat_shorts`, `stat_time`): `res/values*/strings.xml`, dazu
  `tools/i18n/gen_strings.py` und `tools/i18n/tr.py`.
- **Test-Hilfen fürs Pixel:** `tools/pixel-test/leon.sh`
- **Commits dieser Sitzung:** 42ee3e6, 7206a88, 535045d, 301bf8c, 1330faa, d7226f0 (alle mit "Terminal:").

## Offen
- Tablet: nicht getestet (Wunsch von Niklas).
- Superwalls Test-Fenster ("Test Mode Active") kommt in Test-Versionen bei jedem App-Start. Das ist kein Fehler der App.
