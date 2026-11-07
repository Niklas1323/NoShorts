# Prompt für Claude Code im Terminal (Overlay-Test + Fixes, 07.11.2026)

Alles ab "---" in Claude Code einfügen. Das Handy (Pixel 9) muss per adb erreichbar sein (siehe "Verbindung").

---

Du testest und reparierst das Overlay der App "F*ck Shorts" (Repo NoShorts, Zweig `claude/intelligent-volta-4lochv`)
am Pixel 9 des Nutzers. Lies zuerst `CLAUDE.md` ganz, vor allem "Beim Testen beachten" und alle Abschnitte vom
06.11.2026 (Kamera-Menü, Test-Protokoll "Ningelog", Video-Lade-Seite). Antworte am Schluss auf Deutsch (Schweiz,
"ss" statt "ß"), sehr einfach.

## WICHTIG: Zusammenarbeit mit Claude in der Cloud (nicht verpassen!)
Eine zweite Claude-Sitzung (Cloud) arbeitet am selben Zweig. Damit keine Version verloren geht:
1. **Vor jeder Änderung** `git pull origin claude/intelligent-volta-4lochv`. Nie einen älteren Stand testen.
2. Nur auf diesem Zweig arbeiten. **Kein Force-Push, kein Rebase von fremden Commits.** Bei Konflikten mergen.
3. **Jeder Fix = eigener kleiner Commit + sofort pushen.** Commit-Text beginnt mit "Terminal:" (Beispiel
   "Terminal: bar hides on video loading page earlier"). So sieht die Cloud-Sitzung sofort, was neu ist.
4. Nach jedem Fix in `CLAUDE.md` einen kurzen Eintrag unter einem neuen Abschnitt
   "### 07.11.2026 (Terminal, am Pixel getestet)" ergänzen: was gefunden, was geändert, wie geprüft, Build-Nummer.
5. Am Schluss schreibe in `docs/release-todo.md` ganz oben: "Neuster getesteter Stand: <Commit-Hash>, Build <Nr>"
   und pushe. Der Nutzer installiert danach NUR diesen Stand (GitHub baut ihn automatisch als Release).

## Sicherheitsregeln (immer)
- Handy und Konto: nichts löschen, nichts posten, nichts kaufen, niemanden anrufen oder anschreiben, kein `pm clear`.
- **Nie `am force-stop app.fshorts`** (nimmt den Dienst aus den Bedienungshilfen).
- Keine Berechtigungen erteilen, ohne den Nutzer zu fragen.
- Nie blind tippen: vor jedem Tipp prüfen, was vorne ist (`tools/pixel-test/phone.sh`). Kein `input text`/Enter ohne
  Suchfeld. Tipps auf Werbung vermeiden (öffnen Chrome oder Werbe-Videos).
- `uiautomator dump` nimmt unsere Leiste ~2 s weg: danach ≥6 s warten, nie während einer Aufnahme.
- Paywall nur anschauen, nie einen Kauf antippen.
- Das Handy wird manchmal nebenbei benutzt: vor jedem Schritt prüfen, ob YouTube vorne ist.
- Am Schluss alles zurückstellen (Drehen, Wachhalten, App-Sprache).

## Verbindung (Schul-Laptop ohne Admin-Rechte)
- adb ohne Installation: "platform-tools" als ZIP von developer.android.com entpacken, `adb.exe` direkt starten.
- Klappt USB nicht (fehlender Treiber braucht Admin): **Kabelloses Debugging** nehmen (Android 11+):
  Handy → Entwickleroptionen → "Debugging über WLAN" → "Gerät über Kopplungscode koppeln", dann
  `adb pair IP:PORT` (Code eingeben) und `adb connect IP:PORT`. Handy und Laptop müssen im selben WLAN sein
  (Schul-WLAN blockiert das oft – dann Hotspot vom Handy nehmen, der Laptop verbindet sich damit).
- ffmpeg ebenfalls als portable ZIP. Bauen geht nur mit JDK 17 + Android SDK; fehlt das, baue nicht lokal,
  sondern pushe und nimm die APK von GitHub (`releases/latest/download/FuckShorts.apk`, ~3–4 Min nach dem Push).
- Kommt gar keine Verbindung zustande: sag es dem Nutzer klar und hör auf (nichts raten).

## Vorbereitung
1. `git pull`, neuste APK installieren (`adb install -r -d`), Version und Build notieren.
2. Dienst in `enabled_accessibility_services`? App freigeschaltet (Startseite zeigt "on")?
3. Mitschreiben: `adb logcat -s FuckShorts AndroidRuntime` in eine Datei.
4. Aufnahmen mit `screenrecord`, Auswertung Bild für Bild (`tools/pixel-test/phone.sh`, `barcheck`, Bilder ansehen).

## Tests (jeweils mehrmals, Bild für Bild prüfen: echte Leiste darf NIE sichtbar sein)
1. **Video aus der Liste öffnen** (neu in Build 158: `isWatchPageLoading`, "Video-Seite lädt"): fährt unsere Leiste
   sofort weg, wenn die graue Lade-Seite kommt? Mit und ohne Werbung vorneweg, je 5×. Auch Mini-Player aufklappen.
2. **Regression Scrollen:** Startseite 90 s scrollen inkl. Videos, die in der Liste anlaufen. Unsere darf nie
   wegfahren (kein "Video-Seite lädt" ohne Video im Logcat!). Auch Abos und Mein YouTube scrollen.
3. **Video verlassen** (Zurück-Taste, Zurück-Geste, runterwischen) – besonders **mit laufender Werbung**: vorher
   sah man ~0,5 s die echte Leiste mit Shorts. Ursache finden (Logcat) und beheben.
4. **Werbung → Chrome Custom Tab:** unsere stand ~0,2–0,4 s über dem schon offenen Tab und kam beim Schliessen zu
   früh. Verbessern, ohne dass die echte Leiste sichtbar wird.
5. **Plus → Kamera:** öffnen/schliessen (X, Zurück); mit Aufnahme: Blatt "Delete / Save and exit / Cancel" – unsere
   darf nie darüber erscheinen (`menuOverBarless`). Ohne Aufnahme: unsere sofort da nach X.
6. **Mini-Player:** Video runterwischen auf gescrollter Startseite (vorher ~0,36 s gar keine Leiste); schliessen.
7. Kurz-Regression: Tabs (Home/Abos/You je 5×), Suche, ⋮-Menü, Short antippen (geht zu?), Benachrichtigungen,
   Hell/Dunkel.
8. **Lücke unter der Leiste** nach Hochscrollen (Fix vom 06.11., `BOTTOM_SNAP_DP`): Unterkante = Bildschirmrand?

## Fixen
- Jeden Fehler zuerst mit Aufnahme + Logcat belegen, dann im Code beheben, neu bauen/installieren und **denselben
  Test wiederholen**, bis er sauber ist. Dann Regression (Test 2) nochmals kurz.
- Kleine, gezielte Änderungen. Nichts umbauen, was funktioniert.
- Was nicht lösbar ist (System-Animationen usw.), mit Grund in `CLAUDE.md` notieren.

## Bericht an den Nutzer (am Schluss)
Tabelle: Test | Ergebnis | behoben? | Build. Darunter: was offen bleibt, und **welche Version er jetzt installieren
soll** (Build-Nummer + Commit).
