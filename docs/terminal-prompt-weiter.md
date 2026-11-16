# Weitermachen nach Abbruch (Terminal, Pixel 9)

Deine letzte Sitzung wurde mitten in der Arbeit unterbrochen (Laptop ist ausgegangen, Terminal zu). Finde selbst
heraus, wo du stehst, und mach dann weiter. Niklas schreibt Deutsch (Schweiz, "ss"), will sehr einfache Erklärungen.

## 1. Lage feststellen (nichts ändern, bevor das klar ist)

1. **Lokalen Stand sichern:** `git status` und `git diff` im Repo. Gibt es nicht committete Änderungen oder einen halb
   fertigen Commit/Merge? Nichts wegwerfen! Falls Änderungen da sind: anschauen, verstehen, was sie tun sollten, und
   entweder fertig machen oder in einen Zweig `backup/abbruch-16-11` committen (nicht pushen, ausser es ist fertig).
2. `git pull` auf `claude/intelligent-volta-4lochv` (nur dort arbeiten, kein Force-Push, Commits beginnen mit "Terminal:").
3. **Lies `docs/terminal-prompt-14-11.md`** – das ist der ursprüngliche Auftrag (Overlay fertig, perfekt, echte Leiste
   nie sichtbar, schöne Animationen, alles am Handy testen). Er gilt weiter.
4. **Lies in `CLAUDE.md` die Abschnitte "15.11.2026 (Terminal, am Pixel getestet)" und "15.11.2026, Nachmittag"** –
   dort steht, was du schon gemessen und gelöst hast, und was Niklas dazu gesagt hat (z. B. ⋮-Abdunkeln ist
   "perfekt, genau so behalten"; Vorhang bei Abos auf Wunsch entfernt; Querformat flache Leiste).
5. **Diese Commits kamen NACH dem letzten CLAUDE.md-Eintrag (effcd8d, 15.11. 13:12) und sind dort nicht beschrieben:**
   - `6e43fec` (15.11. 13:43) Leiste hochkant 2 dp höher als die echte (Wunsch), Lage-Lernen nach 1,5 s
   - `adf681a` (15.11. 14:10) Werbe-Menü: unsere verschwindet schneller, hellt beim Schliessen mit YouTube auf
   - `a1cbad7` (15.11. 22:16) **Scrollen wie das Original:** unsere fährt mit der echten runter und beim Hochwischen
     vor ihr herein (Richtung im eigenen Thread)
   - `a10dff7` (16.11. 00:10) Scrollen: Liste beim Wegfahren im Hintergrund merken
   Mit `git show <commit>` ansehen. **Achtung:** Im CLAUDE.md steht, dass "Scrollen wie das Original" einmal verworfen
   wurde, weil YouTube 21.45 die Leiste nach 1–2 s manchmal **ohne Meldung** wieder zeigt (dann war die echte mit Shorts
   ~2 s zu sehen). Die zwei Scroll-Commits sind danach entstanden und wurden vermutlich durch den Abbruch nicht mehr
   fertig getestet. **Zuerst gründlich prüfen** (lange Aufnahmen: schnell/langsam scrollen, stehen bleiben, Video in der
   Liste läuft an, Mini-Player, Abos/You): Sieht man jemals die echte Leiste? Fehlt unsere jemals? Wenn es nicht
   zuverlässig ist: zurück auf "unsere bleibt beim Scrollen stehen" (Commit rückgängig, nicht Force-Push) und Niklas
   erklären, warum.
6. Falls es geht: `claude --resume` zeigt die abgebrochene Sitzung; dort steht, was du zuletzt gemacht hast. Nicht nötig,
   wenn die Punkte oben reichen.
7. Prüfe am Handy, was installiert ist (`adb shell dumpsys package app.fshorts | grep versionName`), ob der Dienst läuft
   (`adb shell dumpsys accessibility`), ob die App freigeschaltet ist und ob **unsere** Leiste zu sehen ist – nicht die
   echte (unsere: Text unter den Symbolen, rotes Profilbild an Platz 3, grauer Plus-Kreis rechts; echte: 4 Symbole ohne
   Text, Shorts-Knopf, Profilbild an Platz 4). Auf welchem Benutzer warst du (Niklas u10 oder Leon)? Steht im CLAUDE.md.
   Einstellungen am Handy (Drehen, Wachbleiben, Schrift) evtl. noch verstellt → am Schluss zurückstellen.

## 2. Weitermachen

- Zuerst den unterbrochenen Punkt (vermutlich Scrollen) sauber fertig machen und messen.
- Dann die noch offenen Punkte aus `docs/terminal-prompt-14-11.md` (Abschnitt 5) abarbeiten, die laut CLAUDE.md noch
  nicht erledigt sind (z. B. Video verlassen mit Werbung/Predictive Back, Google Play schliessen 2–7 Bilder, Abos direkt).
- Danach ein **Regressionstest über alles** mit dem fertigen Build (Scrollen, Tabs, Video öffnen/verlassen, Plus/Kamera,
  ⋮, Werbe-Menü, Suche, Benachrichtigungen, Drehen, App-Übersicht), hell und dunkel, Bild für Bild.

## 3. Regeln (unverändert)
- Nie `am force-stop app.fshorts`. Nichts löschen, posten, kaufen, melden, kein `pm clear`. Keine Berechtigungen ohne
  Niklas. Nie blind tippen, vorher prüfen, was vorne ist. In der Kamera nichts speichern.
- `uiautomator dump` nie während einer Aufnahme (schaltet unsere Leiste ~2 s ab).
- Kleine Commits sofort pushen. **CLAUDE.md nachführen** (auch die 4 Commits oben + Ergebnis-Tabelle), damit ein
  weiterer Abbruch nichts mehr kostet: nach jedem fertigen Punkt einen kurzen Eintrag committen.

## 4. Am Schluss
Fertigen Build am Handy installiert lassen (Dienst an, freigeschaltet), Einstellungen zurückstellen, alles gepusht.
Niklas kurz und einfach erklären: was schon fertig war, was du nach dem Abbruch geprüft/geändert hast (mit Zahlen), was
offen bleibt und warum.
