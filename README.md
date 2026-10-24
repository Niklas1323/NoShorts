# Fuck Shorts

Eine Android-App, die in der echten YouTube-App den Shorts-Knopf versteckt und Shorts sofort wieder schliesst.
Lange Videos bleiben ganz normal.

Stand: **Meilenstein 1** (Test-Version nur fürs eigene Handy).

## Was die App gerade kann

- Legt unten eine eigene Leiste über die YouTube-Leiste: Startseite, Abos, Mein YouTube, Plus. **Kein Shorts-Knopf.**
- Tippst du auf unsere Leiste, drückt die App den echten Knopf darunter.
- Geht trotzdem ein Short auf (z. B. über einen Link), drückt die App sofort „Zurück“.
- Findet die App die YouTube-Leiste nicht, speichert sie den Aufbau der Seite, damit wir die Regeln anpassen können.

Noch nicht dabei: Ersatz für das Shorts-Regal auf der Startseite, eigener Feed, Abo/Bezahlen.

## Installieren (Pixel)

1. Auf dem Handy diesen Link öffnen:
   https://github.com/niklas1323/noshorts/releases/latest/download/FuckShorts.apk
2. Die Datei herunterladen und antippen. Chrome fragt, ob es Apps installieren darf: erlauben.
3. „Fuck Shorts“ öffnen, „Verstanden, weiter“ tippen.
4. „Bedienungshilfen öffnen“ → „Fuck Shorts“ → einschalten.
5. Ist der Schalter gesperrt („Eingeschränkte Einstellung“)? Dann in der App auf „App-Info öffnen“ →
   oben rechts ⋮ → „Eingeschränkte Einstellungen zulassen“ → Schritt 4 nochmal.
6. YouTube öffnen.

Neue Test-Versionen installierst du einfach über die alte (gleicher Link). Die Einstellungen bleiben.

## Wenn etwas nicht passt

- Lange auf unsere Leiste drücken: speichert den aktuellen Bildschirm.
- In der App „Struktur teilen“ → mir die Datei schicken.

## Technik

- Kotlin, Jetpack Compose, `AccessibilityService` mit `TYPE_ACCESSIBILITY_OVERLAY`.
- Such-Regeln für die YouTube-Oberfläche: `app/src/main/assets/selectors.json`.
- Jeder Push baut über GitHub Actions eine neue APK und veröffentlicht sie als Release.
