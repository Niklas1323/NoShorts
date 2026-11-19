# Terminal-Auftrag: Hoch- und Runterfahren der Leiste wie das Original (Pixel 9, ZUERST machen)

Niklas schreibt Deutsch (Schweiz, "ss"), will sehr einfache Erklärungen. Dieser Auftrag kommt VOR dem Tablet-Test
(`docs/terminal-prompt-tablet.md`).

## Ziel (Wunsch von Niklas, wörtlich sinngemäss)
Unsere Leiste soll beim Scrollen **zu 99 % gleich hoch- und runterfahren wie YouTubes echte Leiste**: gleicher
Zeitpunkt, gleiche Geschwindigkeit, gleiche Kurve, gleiches Verhalten der Knöpfe (Plopp/Schrumpfen), gleiche Regeln
(wann sie kommt, wann sie bleibt). **Die echte Leiste darf man dabei nie sehen.**
**Das Aussehen (Design) unserer Leiste NICHT ändern** – klassisch und modern bleiben genau wie jetzt. Nur die Bewegung.

## 1. Vorbereitung
1. `git pull` auf `claude/intelligent-volta-4lochv` (nur dort, kein Force-Push, Commits beginnen mit "Terminal:",
   kleine Commits sofort pushen). Cloud-Sitzung arbeitet parallel: vor jedem Commit `git pull`.
2. Lies in `CLAUDE.md` die Abschnitte "15./16.11.2026 … Scrollen wie das Original" und "17.–19.11.2026 … Scrollen wie
   YouTube" und `docs/terminal-bericht-19-11.md`. Dort steht, was schon gemessen ist (Schwelle ~1,1–1,2 px/ms,
   Hochfahren ab ~30 % mit starkem Abbremsen ~190 ms, Plopp, `scrollRise`, `runDirectionProbe`/`probeLoop`,
   `startPresenceProbe`, Mess-Modi `FShortsGhost`, `FShortsLab`, `FShortsMeasure`, `FShortsTrace`).
3. Handy: Pixel 9, **Benutzer Leon (u0)**, nie `am switch-user`. Prüfe, welcher Build läuft, ob der Dienst an und die App
   freigeschaltet ist und dass du **unsere** Leiste siehst (nicht die echte).

## 2. Messen: Wie bewegt sich die ECHTE Leiste genau?
Mit `FShortsGhost` (unsere unsichtbar) YouTube allein aufnehmen (`screenrecord`, 60 fps, Bild für Bild mit ffmpeg) und
für jede Situation eine **Lage-Kurve pro Bild** aufschreiben (Oberkante in px, Grösse/Deckkraft der Knöpfe,
Zeit ab Fingerauflegen bzw. ab Beginn der Listenbewegung):
- schnell runter, mittel runter, langsam runter (fährt sie bei langsamem Runterscrollen auch weg? ab welchem Tempo/Weg?)
- schnell hoch, mittel hoch, langsam hoch, kurzer Ruck hoch nach langem Runterscrollen
- Richtungswechsel mitten in der Animation (runter → sofort hoch, und umgekehrt)
- Finger bleibt liegen / Fling läuft aus / Liste stösst oben an (ganz oben angekommen)
- Startseite, Abos, Mein YouTube (dort blendet YouTube nicht aus – nachprüfen), Suchergebnisse, mit Mini-Player
- hell und dunkel, klassische und moderne Leiste (die echte ist immer gleich, unsere muss bei beiden stimmen)
Folgt die echte Leiste beim langsamen Ziehen dem Finger Pixel für Pixel oder spielt sie eine feste Animation ab? Das
genau herausfinden – das ist der wichtigste Unterschied für "fühlt sich gleich an".
Ergebnis als kleine Tabelle in `CLAUDE.md` festhalten (Bild → Lage), bevor du etwas änderst.

## 3. Nachbauen
- Unsere Bewegung an die gemessenen Kurven anpassen (Zeitpunkt, Dauer, Kurve, Knopf-Plopp/Schrumpfen, Schwellen).
  Wenn die echte dem Finger folgt, folgt unsere auch (aus der Listenlage, im eigenen Thread wie `probeLoop`).
- **Sicherheitsregel bleibt:** Wo YouTube die Leiste zeigt und wir es nicht sicher wissen, steht unsere lieber zu früh
  als zu spät. Die echte darf nie durchscheinen. Kann eine Bewegung nicht 1:1 nachgemacht werden, ohne die echte zu
  zeigen, nimm die nächstbeste sichere Variante und schreib auf, warum.
- Keine Abfragen an YouTube während der Animation (Haupt-Thread frei halten, siehe CLAUDE.md).
- Nichts am Aussehen ändern (Farben, Grössen, Symbole, Texte, Abstände). Nur Lage/Bewegung/Plopp-Timing.

## 4. Vergleichen (Bild für Bild)
- Gleiche Wisch-Befehle (`adb shell input swipe` mit festen Werten) einmal mit `FShortsGhost` (nur echte) und einmal
  normal (unsere) aufnehmen und die Kurven übereinanderlegen. Ziel: pro Bild höchstens ~1 Bild Zeitversatz und wenige
  Pixel Abweichung, gleiche Entscheidung (kommt/kommt nicht) in **jedem** Lauf.
- Danach die Kontrolle "echte nie sichtbar" (`rbar`, `avs`, Bilder ansehen) über lange gemischte Aufnahmen
  (je ≥ 60 s Startseite, Abos, Du, mit Mini-Player, hell + dunkel, klassisch + modern).
- Regression kurz: Tabs, Video öffnen/verlassen, Plus/Kamera, Suche, ⋮, Benachrichtigungen, App wechseln, Drehen.

## 5. Regeln (unverändert)
- Nie `am force-stop app.fshorts`, kein `pm clear`, nichts löschen/posten/kaufen/melden, keine Berechtigungen ohne
  Niklas. Nie blind tippen (vorher prüfen, was vorne ist; Wischer am Rand x 120–300). Nie auf einen Mini-Player mit
  Werbung tippen. In der Kamera nichts speichern. `uiautomator dump` nie während einer Aufnahme.
- Mess-Schalter (`setprop log.tag.… VERBOSE`) am Ende wieder auf `INFO`.

## 6. Am Schluss
- CLAUDE.md-Eintrag mit Ergebnis-Tabelle (Situation | echte | unsere vorher | unsere nachher | echte sichtbar?).
- Fertigen Build am Handy lassen (Dienst an, freigeschaltet), Einstellungen zurückstellen, alles gepusht.
- Kurzer Bericht für Claude Code Web in `docs/terminal-bericht-scrollen.md` (Build-Nummer, Commit, was gleich ist, was
  nicht geht und warum).
- Niklas kurz und einfach erklären, was jetzt gleich ist wie das Original und was (falls) nicht.
