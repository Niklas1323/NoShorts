# Terminal-Auftrag: Tablet-Test (Galaxy Tab A8) – ERST NACH `docs/terminal-prompt-scrollen.md`

Niklas schreibt Deutsch (Schweiz, "ss"), will sehr einfache Erklärungen. Zuerst muss der Scroll-Auftrag am Handy fertig
und gepusht sein. Steht in `CLAUDE.md` noch kein Ergebnis dazu: Niklas fragen, bevor du hier anfängst.

## Ziel
Seit dem letzten Tablet-Test (01.11.2026, Build 0.1.87) ist am Handy sehr viel dazugekommen und **nie auf dem Tablet
geprüft** worden. Das Tablet soll danach genauso fertig sein wie das Handy: **echte Leiste nie sichtbar**, alle Knöpfe
gehen, nichts sieht falsch aus. Gefundene Fehler beheben (nur über `isTablet`, das Handy darf sich nicht verändern) und
nachmessen.

## 1. Vorbereitung
1. `git pull` auf `claude/intelligent-volta-4lochv` (kein Force-Push, Commits "Terminal: …", klein und sofort pushen).
2. Lies in `CLAUDE.md` alles mit "Tablet" (28.10., 30.10., 01.11., 08.11.) und alles ab "15.11.2026" (Handy-Änderungen),
   dazu `docs/terminal-bericht-19-11.md` und – falls da – `docs/terminal-bericht-scrollen.md`.
3. Tablet: Galaxy Tab A8 SM-X200, Android 14, 1200x1920, 240 dpi. YouTube-Version notieren. Prüfen: Build, Dienst an,
   App freigeschaltet (sonst zeichnet der Dienst nichts! Code "Ningeninge" in Settings → Redeem code), **unsere** Leiste
   zu sehen (nicht die echte). Hat das Tablet inzwischen YouTubes neues Design (Reiter oben, 4 Knöpfe ohne Text)?
   Notieren, das ändert viele Wege.
4. Test-Hilfen: `tab.sh` (realdet/oursgone/tstrips/tapbar/focus/safetap/leavevideo, Lagen hoch/quer) – falls nicht mehr
   da, aus CLAUDE.md neu bauen. Am Tablet am Rand wischen (x=1188).

## 2. Was seit 01.11. neu ist und am Tablet geprüft werden muss
| Neu (Handy) | Was am Tablet prüfen |
|---|---|
| Scrollen wie das Original (Leiste fährt mit, Richtung im eigenen Thread, Schwellen, Plopp) | Blendet YouTube am Tablet die Leiste beim Scrollen überhaupt aus (früher: blieb stehen)? Unsere darf dann NICHT wegfahren. Hoch/quer, schnell/langsam, Startseite/Abos/Du |
| Moderne Leiste (nur Symbole, 3 Knöpfe) | Gibt es am Tablet noch nie. Sitzt sie richtig (Block in der Mitte wie die klassische, `TABLET_SLOT_DP`)? Grössen, Farben (#0F0F0F), Tippflächen, hoch/quer, hell/dunkel. Wenn sie falsch aussieht: an das Tablet anpassen |
| Spiegel-Leiste (SurfaceControl) | Ist am Tablet aus. Prüfen, dass nichts davon stört (App wechseln, App-Übersicht, Splitscreen) |
| Hereinfahren nach "Video verlassen", Plus `handOver`, Kamera-Fenster-Meldungen, Google-Play-Blatt, Werbe-Panel-Abdunkeln, Tinten-Abdunkeln beim ⋮ | jeweils am Tablet: echte nie sichtbar, Animation flüssig (Scans dauern am Tablet bis 1,2 s!) |
| Plus am Tablet fährt sofort weg (`slideAway`, 08.11.) | noch gewollt? Mit dem neuen Plus-Weg vom Handy vergleichen, Niklas fragen, falls unklar |
| Ruhelage-Lernen (`higherRest`, `shorterRest`, `TOP_EXTRA_DP` 2 dp höher) | Höhe am Tablet = echte (+2 dp), auch nach Drehen und App-Update |
| Querformat ohne flache Leiste (`COMPACT_LANDSCAPE = false`) | quer so hoch wie die echte, Block in der Mitte |
| Beschriftung aus YouTubes Knopf-Beschreibung | richtige Sprache, nicht abgeschnitten |
| App-Oberfläche Design V2 (Startseite, Einstellungen, Einrichtung, Paywall, Abo beendet, Sprachwahl, Update-Hinweis, Ausschalt-Fenster) | NUR Hochformat erlaubt (`screenOrientation="portrait"`): sieht es auf dem grossen Bildschirm gut aus? Nichts zu breit/gestreckt, nichts abgeschnitten, alles bedienbar. Grosse Schrift prüfen |
| Startseiten-Regal (⋮ → "Not interested"/"Kein Interesse") | noch NIE geprüft, auf keinem Gerät. Wenn ein Regal kommt: unbedingt testen |

## 3. Kompletter Test (hoch UND quer, dunkel UND hell, klassisch UND modern)
Scrollen (lang, ≥ 60 s, mit Stillstehen) · Tabs je 5× · Abos von Startseite und von Mein YouTube · Video öffnen/verlassen
(Zurück-Taste, Zurück-Geste, Runterwischen, mit Werbung) · Mini-Player auf/zu/schliessen · Plus → Kamera → X/Zurück
(Fotos-Frage nur mit Zurück schliessen, nichts erlauben) · Suche (Lupe, Text, Suchen, Zurück) · Suchfilter-Dialog ·
⋮-Menü · Werbe-⋮ ("Mein Anzeigen-Center") · Werbung → Google Play / Chrome (nichts installieren) · Benachrichtigungen
auf/zu · Home und zurück · App-Übersicht · Drehen hin und her · Splitscreen (oben/unten, tauschen) · Pop-up-Ansicht ·
Bildschirm aus/an · App-Update auf gescrollter Seite · Short per Link + Weiterwischen · Short antippen · Abos-Regal ·
Einstellungen/Kanal-Seite · Schutz pausieren/wieder an (Timer mit Code "Ningeningetimer").
Jede Aufnahme Bild für Bild auswerten (Treffer immer ansehen, Fehlalarme kommen vor).

## 4. Regeln
- Nie `am force-stop app.fshorts`, kein `pm clear`, nichts löschen/posten/kaufen/melden/installieren, keine
  Berechtigungen ohne Niklas. Nie blind tippen (vorher `focus`, `tapbar`/`safetap`). Nie auf Werbung oder einen
  Mini-Player mit Werbung tippen, ausser genau dafür (und dann nur schliessen). In der Kamera nichts speichern.
- `uiautomator dump` nie während einer Aufnahme. Handy-Code nur ändern, wo es sicher nur das Tablet trifft; trifft eine
  Änderung doch das Handy, am Pixel kurz nachtesten.
- Einstellungen am Tablet (Drehen, Wachbleiben) am Ende zurückstellen.

## 5. Am Schluss
- CLAUDE.md: Abschnitt "Tablet (Datum)" mit Ergebnis-Tabelle (Test | vorher | nachher | behoben?), was nicht lösbar ist
  und warum.
- Bericht für Claude Code Web: `docs/terminal-bericht-tablet.md` (Build-Nummer, Commit, Ergebnis, offene Punkte).
- Fertigen Build auf dem Tablet lassen (Dienst an, freigeschaltet), alles gepusht.
- Niklas kurz und einfach erklären: was am Tablet gut ist, was behoben wurde, was offen bleibt.
