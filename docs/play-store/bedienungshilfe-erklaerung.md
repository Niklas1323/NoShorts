# F*ck Shorts – Erklärung für Google zur Bedienungshilfe (Play Store)

Stand: 30.10.2026. Grundlage: Google-Play-Richtlinie "Use of the AccessibilityService API" (strengere Prüfung seit
28.01.2026) und "Permissions and APIs that Access Sensitive Information". Die genauen Fragen im Formular können sich
ändern – vor dem Ausfüllen in der Play Console nachsehen und die Antworten unten anpassen.

## Was Google verlangt (kurz)

| Pflicht | Wo | Stand bei uns |
|---|---|---|
| Hinweis in der App mit Daten und Zweck, **bevor** man einschaltet | App, Startseite | ✅ `DisclosureCard` (MainActivity.kt), seit dieser Version vollständig |
| Aktive Zustimmung (Knopf), Ablehnen möglich, Weg-Navigieren zählt nicht | App | ✅ "Zustimmen" / "Ablehnen" |
| Text in den Bedienungshilfen-Einstellungen ersetzt den Hinweis NICHT | – | ✅ eigener Hinweis in der App |
| Formular "Accessibility" in der Play Console | Play Console → App-Inhalte | ⬜ Antworten unten |
| Video, das den Hinweis in der App vollständig zeigt | Link im Formular | ⬜ Drehbuch unten |
| Store-Beschreibung nennt die Bedienungshilfen-Nutzung | Store-Eintrag | ⬜ Text unten |
| Kein selbständiges Planen/Handeln (KI-Agent); feste Regeln erlaubt | Richtlinie | ✅ nur feste Regeln |
| Bei jeder Änderung der Nutzung: Formular neu abgeben | Play Console | merken |

`isAccessibilityTool="false"` bleibt so (`res/xml/accessibility_service_config.xml`): Die App ist kein Hilfsmittel
für Menschen mit Behinderung. Mit `true` zu lügen wäre ein Grund zum Sperren.

## Was die App wirklich tut (geprüft im Code, Version build-84)

- **Liest:** Aufbau der YouTube-App (Knöpfe, Texte, IDs, Positionen) – `YouTubeScanner.kt`.
- **Merkt, welche App vorne ist** (kein Paket-Filter in der Config, damit die Leiste beim App-Wechsel sofort weg ist).
- **Benachrichtigungen/Tastatur:** nur Lage und ob sichtbar (`com.android.systemui:id/notificationShelf`,
  `notification_stack_scroller`, Tastatur-Fenster). Keine Texte daraus.
- **Bildschirmfotos:** vom YouTube-Fenster (Profilbild, hell/dunkel) – `BarStyle.kt`; vom ganzen Bildschirm nur zum
  Messen einer Farbe (Benachrichtigungs-Schleier, Menü-Abdunkelung), danach sofort verworfen.
- **Handelt in YouTube nach festen Regeln:** eigene Leiste darüber, Tipp darauf → Klick auf den echten Knopf;
  Short geht auf → "Zurück"; Shorts-Regal → ⋮ → "Nicht interessiert" / auf Abos "Ausblenden" (immer an, seit 02.11. kein Schalter mehr);
  Short per Link: Weiterwischen → zurückwischen.
- **Speichert nur auf dem Gerät:** Profilbild-Ausschnitt, Lage der Leiste, Einstellungen, max. 12 Fehler-Dateien
  (Struktur der YouTube-Seite, Bild der Leiste). Seit 02.11. nicht mehr in der App teilbar (nur per USB/adb).
- **Internet nur für Abos (seit 02.11.2026, Superwall):** Die App hat jetzt `INTERNET` und `BILLING`. Superwall
  (Paywall, Abo-Status) und Google Play bekommen Kauf- und einfache Geräte-Infos (Modell, Sprache, App-Version).
  Was der Dienst aus YouTube liest, geht weiterhin nirgendwohin (kein Netzwerk-Code im Dienst).
  `POST_NOTIFICATIONS` von Superwall ist im Manifest entfernt. Hinweis in der App ist angepasst (`Disclosure` in
  `ui/Onboarding.kt`, Schlüssel `disclosure_accepted_v3`).

## Antworten fürs Formular (Englisch zum Einfügen)

**Is your app an accessibility tool (isAccessibilityTool)?**
No.

**Describe your app's core functionality and why it needs the AccessibilityService API.**
> F*ck Shorts is a digital wellbeing app that removes YouTube Shorts from the official YouTube app so users can
> watch long-form videos without being pulled into short-form content. The AccessibilityService API is the only
> Android API that lets an app (1) detect which screen of the YouTube app is open, (2) find the Shorts button,
> Shorts player and Shorts shelves, (3) draw a replacement navigation bar without the Shorts button over YouTube's
> bar (TYPE_ACCESSIBILITY_OVERLAY) and forward the user's taps to the matching YouTube tab, and (4) leave a Short
> immediately by performing the Back action. Without this API the app cannot work at all.

**What data does your app access via the API, and is it collected or shared?**
> The app reads the on-screen UI structure of the YouTube app (view IDs, button labels, text such as video titles,
> and positions) and which app is currently in the foreground. For the notification shade and the keyboard it only
> reads their position on screen, not their content. It takes screenshots of the YouTube window to match the colors
> and copy the user's profile picture into the replacement bar; full-screen screenshots are only used to measure a
> single color and are discarded immediately. All of this is processed on the device only and is never
> transmitted or shared. A small cropped profile picture, the bar position and up to 12 local troubleshooting files
> are stored on the device only (deleted when the app is uninstalled or its data is cleared). The app's only network use is for
> subscriptions: Google Play Billing and our paywall provider Superwall receive purchase and basic device
> information (device model, language, app version). No data obtained through the AccessibilityService API is
> ever sent to them or anywhere else.

**Does the app perform actions on behalf of the user? / Autonomous behavior?**
> The app does not autonomously plan or make decisions and contains no AI agent. It only follows fixed, deterministic
> rules that serve the single purpose the user installed it for: when a Short opens, it performs Back; when the user
> taps our replacement bar, it clicks the corresponding YouTube tab; when a Short opened from a shared link is swiped
> away, it swipes back to that Short; and when a Shorts shelf appears, it opens the shelf's menu and selects
> "Not interested" (or "Hide" on Shorts cards in Subscriptions). All of this is described in the in-app disclosure
> the user agrees to before turning the service on.

**Prominent disclosure – where is it shown?**
> On the last onboarding screen ("Turn it on"), before the user is sent to the Accessibility settings. A short
> summary is always visible; "Show details" expands the full text in place, right above the buttons. It explains
> what data is accessed, what the app does with it, and that nothing it reads leaves the device. The user must tap
> "Agree & open settings"; "Not now" is available. The full text is also in Settings → Privacy & permissions.

**Video link:** (nicht gelisteter YouTube-Link, siehe Drehbuch)

### Deutsch zum Verstehen
- Kein Hilfsmittel für Behinderung → Nein.
- Kernfunktion: Shorts in der YouTube-App entfernen (digitales Wohlbefinden). Ohne Bedienungshilfe geht es nicht.
- Daten: Aufbau von YouTube, vordere App, Bildschirmfotos – alles nur auf dem Handy; Internet nur fürs Abo.
- Keine KI, keine eigenen Entscheidungen: nur feste Regeln (Short → Zurück; Tipp → echter Knopf; Regal → "Nicht
  interessiert").

## Text für die Store-Beschreibung

**Deutsch**
> Bedienungshilfen: F*ck Shorts nutzt die Android-Bedienungshilfen (AccessibilityService). Nur so kann die App in
> der YouTube-App erkennen, wo Shorts sind, eine eigene Leiste ohne Shorts-Knopf darüberlegen, Shorts sofort
> schliessen und auf Wunsch Shorts-Regale ausblenden. Die App liest dafür den Aufbau der YouTube-App. Alles bleibt
> auf deinem Handy: Was die App aus YouTube liest, verlässt nie dein Handy. Internet braucht sie nur für das Abo.

**English**
> Accessibility: F*ck Shorts uses Android's AccessibilityService API. This is the only way for the app to detect
> Shorts in the YouTube app, place its own navigation bar without the Shorts button on top, close Shorts instantly
> and, if you want, hide Shorts shelves. To do this the app reads the screen structure of the YouTube app.
> Everything it reads from YouTube stays on your phone. Internet is only used for subscriptions.

## Drehbuch fürs Video (1–2 Minuten)

Aufnahme durch Claude im Terminal mit `adb shell screenrecord` (Hochformat, Handy-Sprache wie im Store).
Danach auf YouTube als **"Nicht gelistet"** hochladen, Link ins Formular.

1. **Vorbereitung:** App neu installieren *oder* App-Daten der Test-App zurücksetzen – nur mit Erlaubnis von
   Niklas (`pm clear` löscht alle Einstellungen!). Bedienungshilfe vorher ausschalten.
2. App öffnen, Onboarding bis "Turn it on". **"Show details"** aufklappen, den ganzen Hinweis langsam zeigen (~20 s).
3. Auf **"Agree & open settings"** tippen.
4. "Bedienungshilfen öffnen" → F*ck Shorts **einschalten**, Android-Bestätigung zeigen.
5. "YouTube öffnen": unten unsere Leiste **ohne Shorts-Knopf**; Startseite, Abos, Mein YouTube antippen.
6. Einen Short öffnen (z. B. aus der Suche) → er geht **sofort zu**.
7. Zurück in F*ck Shorts: Startseite "Protection on", Settings → "Privacy & permissions" (Hinweis nachlesbar).
8. Optional: Bildtext/Untertitel auf Englisch ("The app shows this disclosure before the user enables the service").

## Checkliste vor der Abgabe

- [ ] Hinweis in der App am Handy geprüft (erscheint, Ablehnen/Zustimmen gehen, Einrichten erst danach).
- [ ] APK-Berechtigungen geprüft: nur `INTERNET`/`BILLING` dazu (Superwall), kein `POST_NOTIFICATIONS`.
- [ ] Video aufgenommen und als "Nicht gelistet" hochgeladen.
- [ ] Formular "Accessibility" ausgefüllt (Antworten oben).
- [ ] Store-Beschreibung mit dem Absatz oben.
- [ ] Datenschutzerklärung (eigene Aufgabe) sagt dasselbe wie der Hinweis.
- [ ] Formular "Datensicherheit" (Data safety): Kaufverlauf und App-Interaktionen/Geräte-IDs durch Superwall angeben
      (genaue Liste in der Superwall-Doku "Google Play Data Safety" nachsehen).
- [ ] Hinweis auch auf Englisch, falls die App ausserhalb des deutschen Sprachraums angeboten wird.

## Risiken (ehrlich)

- **App-Name:** "F*ck Shorts" kann wegen Schimpfwort abgelehnt werden → sauberen Namen bereithalten.
- **Regal wegklicken:** Die App tippt in YouTube für den Nutzer. Das ist eine feste Regel (erlaubt), Google könnte es
  trotzdem als "Handeln für den Nutzer" kritisch sehen. Gut erklärt im Formular; notfalls wieder einen Schalter einbauen (standardmässig aus).
- **Fehler-Dateien:** Schreiben YouTube-Texte (z. B. Videotitel) auch ins Android-Protokoll (logcat). Für die
  Release-Version abschalten oder kürzen.
- **Prüfung dauert:** Accessibility-Apps werden genau angeschaut; mit Rückfragen oder Ablehnung rechnen.
