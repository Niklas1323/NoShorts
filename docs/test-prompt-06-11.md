# Prompt für Claude Code im Terminal (Test vom 06.11.2026)

Alles ab "---" in Claude Code am Heim-PC einfügen (Pixel per USB angeschlossen).

---

Du testest die App "F*ck Shorts" (Repo NoShorts, Zweig `claude/intelligent-volta-4lochv`) am angeschlossenen Pixel 9.
Lies zuerst `CLAUDE.md` (vor allem "Beim Testen beachten" und die Abschnitte vom 05.11. und 06.11.). Antworte mir am
Schluss auf Deutsch (Schweiz, "ss"), sehr einfach.

## Sicherheitsregeln (immer)
- Mein Handy und Konto: nichts löschen, nichts posten, nichts kaufen, niemanden anrufen oder anschreiben, kein `pm clear`.
- **Nie `am force-stop app.fshorts`** (nimmt den Dienst aus den Bedienungshilfen).
- Keine Berechtigungen erteilen, ohne mich zu fragen.
- Nie blind tippen: vor jedem Tipp prüfen, was vorne ist (siehe Werkzeuge in `tools/pixel-test/phone.sh`).
- `uiautomator dump` nimmt unsere Leiste ~2 s weg: danach ≥6 s warten, nie während einer Aufnahme.
- Paywall: im Testmodus nur öffnen und anschauen, **keinen Kauf antippen**.
- Am Schluss alles zurückstellen (App-Sprache auf "wie das Handy", Drehen/Wachhalten wie vorher).

## Vorbereitung
1. `git pull`, dann neuste APK holen: `releases/latest/download/FuckShorts.apk` (Build 145 oder neuer) oder lokal
   `./gradlew assembleDebug`. Installieren mit `adb install -r -d`. Version notieren.
2. Prüfen, dass der Dienst noch in `enabled_accessibility_services` steht.
3. Während des ganzen Tests mitschreiben: `adb logcat -s FuckShorts AndroidRuntime Superwall` (in eine Datei).

## A. Neu seit dem letzten Test: App ist mit R8 verkleinert (84 → 20 MB)
R8 kann Code entfernen, der über Reflexion gebraucht wird. Achte auf Abstürze und auf `ClassNotFoundException`,
`NoSuchMethodError`, `NoSuchFieldException`, `UnsatisfiedLinkError` (JNA/supercel) im Logcat.
1. App starten: Startseite, Settings, Datenschutz-Seite, Support-ID kopieren.
2. Paywall über "Pro" öffnen: erscheint sie? Stehen Preise drin? Schliessen (nichts kaufen).
3. Code einlösen: "Ningeninge" (falls nicht schon frei) und "Ningeningetimer" (Wartezeit 5 s).
4. Schutz ausschalten und wieder einschalten (Schild → Spruch → Timer → ausschalten → wieder einschalten).
5. Overlay in YouTube, kurz alle Hauptfälle (wie im Nachttest, `tools/pixel-test`): Startseite scrollen (60 s),
   Tabs je 3×, Video öffnen/verlassen 3×, Plus → Kamera → X, Suche, ⋮-Menü, Short antippen (geht zu?),
   Short per Link + Weiterwischen, Benachrichtigungen auf/zu. Echte Leiste nie sichtbar (ausser Bekanntem)?
6. Dienst-Neustart: App-Update per `adb install -r -d` auf der Startseite, Leiste danach wieder da?

## B. Sprachen (neu: 7 Sprachen inkl. Türkisch)
1. In der App: Willkommensseite (Settings → "Run setup again") → oben rechts die Flagge → jede Sprache einmal
   wählen: English, Deutsch, Français, Italiano, Español, Português, Türkçe.
2. In jeder Sprache Fotos machen von: Onboarding Seite 1–3 (mit "Show details" offen), Startseite, Settings,
   Ausschalt-Fenster (Spruch), Wieder-Einschalten-Fenster. Prüfen: Text abgeschnitten? Überlappt etwas? Knöpfe zu
   klein? Seite bleibt beim Wechsel dieselbe?
3. Android-Einstellungen → Apps → F*ck Shorts → Sprache: stehen dort die 7 Sprachen?
4. Am Schluss in der App die Sprache wählen, die das Handy hat (dann folgt die App wieder dem Handy).

## C. Paywall in anderen Sprachen (neu, nur Texte im Superwall-Editor)
1. App-Sprache auf Deutsch → Paywall öffnen: Sind die Texte deutsch ("Sei produktiver…", "3 Tage gratis testen")?
2. Dasselbe auf Türkisch und Französisch.
3. Falls die Paywall englisch bleibt: im Logcat nachsehen, welche Sprache Superwall meldet
   (`deviceLanguageCode`/`preferredLanguageCode`/`deviceLocale`), und mir sagen, welcher Wert der App-Sprache folgt.
   Nichts im Superwall-Dashboard ändern.

## D. Bericht
- Ergebnisse als Tabelle in `docs/test-06-11.md` (Punkt, Ergebnis, Beleg-Datei), dazu kurz in `CLAUDE.md`.
- Gefundene Fehler direkt beheben, wenn sie klar sind (z. B. fehlende R8-keep-Regel in `app/proguard-rules.pro`,
  Text zu lang → kürzere Übersetzung in `tools/i18n/gen_strings.py` bzw. `tools/i18n/tr.py`, danach
  `python3 tools/i18n/gen_strings.py`). Nach jedem Fix kurz nachtesten.
- Commit + Push auf `claude/intelligent-volta-4lochv`.
- Mir am Schluss in einfachem Deutsch sagen: was geht, was nicht, was du behoben hast.
