# Nachttest 02./03.11.2026 (Pixel 9, Handy u10)

Status: ⬜ offen · 🔧 in Arbeit · ✅ ok (am Handy gesehen) · ⛔ nicht lösbar (Grund + Beweis)

## Stand (wird jede Runde aktualisiert)
- Start: 02.11.2026 ~21:00. Phase 0 (Vorbereitung) läuft.

## Ausgangszustand (gesichert in `C:\Users\Admin\ptest\baseline`)
- Bedienungshilfen: `com.screenzen/com.screenzen.service.MyAccessibilityService:app.fshorts/app.fshorts.service.FuckShortsService`, `accessibility_enabled=1`
- `accelerometer_rotation=0`, `user_rotation=0`, `screen_brightness=8` (Modus 1 = automatisch), `stay_on_while_plugged_in=2`, Dark mode: yes, Sprache en-CH
- Prefs: `subscribed=false`, `protection_paused=false`, `next_wait_min=5`, `unlock_at=0`, `started_at=1793645733170`, `fast_timer=true`, `disclosure_accepted_v3=true`, `onboarding_done=true` (kein `promo_unlocked`) = **App gesperrt**
- Endzustand (Auftrag): freigeschaltet mit "Ningeninge", `protection_paused=false`, `unlock_at=0`, `fast_timer=false`, `next_wait_min=10`, `onboarding_done=true`, `disclosure_accepted_v3=true`, `started_at` unverändert, Bedienungshilfen-Liste identisch, Schutz AN.

## A. App
| # | Punkt | Status | Beleg / Notiz |
|---|---|---|---|
| A1 | Onboarding komplett (Willkommen → Vorher/Nachher → "Turn it on", Zurück-Geste, "Show details", "Not now", "Switch locked?", Seite nicht übersprungen wenn Dienst an, Paywall erst nach "Continue", Knopf "Continue" + nur "Have a code?") – erstes Onboarding (neuer Nutzer) UND "Run setup again" | ⬜ | |
| A2 | Paywall (Testmodus): Texte (monatlich, "Or go yearly"), Kauf simulieren → App frei → Startseite; Variante ohne Testzeit ("Subscribe now") | ⬜ | Testmodus-Fenster kommt beim Start (Bild baseline/start.png) |
| A3 | "Abo beendet" (ExpiredScreen): Resubscribe, Restore ("No active subscription found"/"Purchases restored"), "Have a code?" | ⬜ | |
| A4 | Codes: "Ningeninge", "Ningeningetimer" (5 s), falscher Code → Meldung | ⬜ | |
| A5 | Startseite: Status, Zahlen, "Open YouTube", nichts scrollt, hell + dunkel | ⬜ | |
| A6a | Schutz ausschalten: Schild → Spruch → Timer, läuft bei geschlossener App, "Keep protection on" | ⬜ | |
| A6b | Echter 10-min-Timer EINMAL | ⬜ | |
| A6c | Nach Ablauf: "Turn off" → pausiert (Shorts laufen, Leiste bleibt, Regale nicht ausgeblendet) | ⬜ | |
| A6d | Wieder einschalten mit Wartezeit (5…120, eigene ≥5), Tages-Spruch "Day 1" | ⬜ | |
| A7 | Settings: Theme System/Dark/Light, Restore, Manage subscription (nur öffnen), Rate (nur öffnen), Privacy & permissions, Redeem code, Run setup again (ohne Paywall, "Continue" zurück), Version | ⬜ | |
| A8 | Pro-Knopf unten: mit Abo → Play-Abo-Seite (nur ansehen), mit Code → Hinweis | ⬜ | |
| A9 | Nur Hochformat, Neustart behält alles, keine Abstürze (`logcat -b crash`) | ⬜ | |

## B. Overlay (hell + dunkel, `tools/pixel-test/phone.sh`)
| # | Punkt | Status | Beleg / Notiz |
|---|---|---|---|
| B10 | Startseite lange scrollen, Tabs 5×, Abos-Knopf von Home und You, Auswahl/Profilbild-Ring | ⬜ | |
| B11 | Video öffnen/verlassen (Zurück, Wischen), Mini-Player alle Tabs, ⋮-Menüs, Werbe-⋮, Suche + Suchfilter | ⬜ | |
| B12 | Plus → Kamera → X / Zurück-Geste / Zurück-Taste | ⬜ | |
| B13 | Benachrichtigungen auf/zu, Bildschirm aus/an, App-Wechsel, Drehen, Short per Link + Weiterwischen, Short in Suchergebnissen | ⬜ | |
| B14 | Shorts-Regale: Abos (Karten "Hide") und Startseite (⋮ → "Not interested"), bis ~45 min suchen | ⬜ | |
| B15 | App gesperrt: Dienst tut nichts, YouTube unverändert (echte Leiste mit Shorts sichtbar = gewollt) | ⬜ | |

## C. Zusätzlich aufgefallen
(noch nichts)

## Entscheidungen
- **Speicher C:** Nach `./gradlew clean` nur 7,2 GB frei (Grenze laut Auftrag 8 GB). `ptest` war fast leer, `.gradle` hat nur 1,5 GB; mehr durfte ich nicht löschen. Ich arbeite trotzdem weiter und setze die Notbremse auf 5 GB: Aufnahmen nur kurz, sofort nach dem Auswerten löschen, vorwiegend Einzelbilder. Unter 5 GB: Pause + Benachrichtigung.
- Akku 29 % beim Start (lädt per USB, Stand 20:58).

## Hinweise (nicht behoben, nicht von uns)
(noch nichts)
