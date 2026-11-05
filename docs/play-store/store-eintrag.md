# Store-Eintrag und Play-Console-Formulare (Entwurf 05.11.2026)

Alles zum Kopieren in die Play Console. Texte ehrlich: keine "100 %"-Versprechen, kein YouTube-Logo, "YouTube" nur
beschreibend. Bei jeder Änderung der App diese Datei mitpflegen.

---

## 1. Store-Eintrag

**App-Name (max. 30 Zeichen):** `F*ck Shorts – Shorts Blocker`
Ersatzname, falls Google den Namen ablehnt: `NoShorts – Shorts Blocker`

**Kategorie:** Produktivität (Productivity) · **Tags:** Digital wellbeing, Focus
**Kontakt-E-Mail:** fckshorts.support@gmail.com
**Datenschutzerklärung:** https://www.freeprivacypolicy.com/live/478be282-9e4c-4797-83de-8e73fbdf6626

### Englisch (Standard)

**Kurzbeschreibung (max. 80 Zeichen):**
> Hide Shorts in the YouTube app and close them instantly. Watch what you chose.

**Lange Beschreibung:**
> Shorts are built to keep you scrolling. F*ck Shorts takes them out of the YouTube app so you can watch the
> videos you actually came for.
>
> WHAT IT DOES
> • Replaces YouTube's bottom bar with its own bar without the Shorts button
> • Closes a Short as soon as one opens
> • Hides Shorts shelves in your feeds by tapping "Not interested" / "Hide" for you
> • A Short someone sends you as a link still plays — but swiping on to more Shorts is blocked
> • Turning protection off takes a waiting timer, so you can't give in on impulse
> • Shows how many Shorts were closed and an estimate of the time you got back
>
> HOW IT WORKS (ACCESSIBILITY SERVICE)
> F*ck Shorts uses Android's Accessibility Service API. This is the only way for the app to see where Shorts
> appear in the YouTube app, draw its own navigation bar over YouTube's bar and close Shorts. The app reads the
> screen structure of the YouTube app for this. Everything it reads stays on your phone: nothing from YouTube is
> ever sent to us or anyone else. Before you turn the service on, the app explains exactly what it does and asks
> for your agreement. You can turn it off at any time in Android Settings > Accessibility.
>
> SUBSCRIPTION
> F*ck Shorts needs a subscription (monthly with a free trial for new users, or yearly). Payment is handled by
> Google Play. The subscription renews automatically until you cancel it in Google Play.
>
> F*ck Shorts works with the official YouTube app on Android. It is not affiliated with YouTube or Google.
> YouTube may change its app at any time; we update F*ck Shorts when that happens.

### Deutsch

**Kurzbeschreibung:**
> Versteckt Shorts in der YouTube-App und schliesst sie sofort. Schau, was du willst.

**Lange Beschreibung:**
> Shorts sind gemacht, damit du nie aufhörst zu scrollen. F*ck Shorts nimmt sie aus der YouTube-App, damit du die
> Videos schaust, für die du gekommen bist.
>
> WAS DIE APP MACHT
> • Ersetzt die untere Leiste von YouTube durch eine eigene Leiste ohne Shorts-Knopf
> • Schliesst einen Short, sobald er aufgeht
> • Blendet Shorts-Regale aus, indem sie für dich "Kein Interesse" / "Ausblenden" antippt
> • Ein Short, den dir jemand als Link schickt, läuft trotzdem – aber Weiterwischen zu anderen Shorts geht nicht
> • Ausschalten geht nur nach einer Wartezeit, damit du nicht aus einem Impuls heraus aufgibst
> • Zeigt, wie viele Shorts geschlossen wurden, und schätzt die gewonnene Zeit
>
> SO FUNKTIONIERT ES (BEDIENUNGSHILFE)
> F*ck Shorts nutzt die Android-Bedienungshilfen (Accessibility Service). Nur so kann die App erkennen, wo in der
> YouTube-App Shorts sind, eine eigene Leiste darüberlegen und Shorts schliessen. Dafür liest sie den Aufbau der
> YouTube-App. Alles, was sie liest, bleibt auf deinem Handy: Nichts davon geht an uns oder an andere. Bevor du die
> Bedienungshilfe einschaltest, erklärt die App genau, was sie tut, und fragt nach deiner Zustimmung. Ausschalten
> kannst du sie jederzeit unter Einstellungen > Bedienungshilfen.
>
> ABO
> F*ck Shorts braucht ein Abo (monatlich mit Gratis-Testzeit für neue Nutzer, oder jährlich). Bezahlt wird über
> Google Play. Das Abo verlängert sich automatisch, bis du es in Google Play kündigst.
>
> F*ck Shorts funktioniert mit der offiziellen YouTube-App für Android. Keine Verbindung zu YouTube oder Google.
> YouTube kann seine App jederzeit ändern; wir passen F*ck Shorts dann an.

**Grafiken (macht ihr, Claude hilft):** App-Icon 512×512 (PNG), Titelbild 1024×500, mind. 2 Handy-Screenshots
(nur aus der eigenen App und unserer Leiste, **kein YouTube-Logo** gross im Bild, keine fremden Videos im Fokus).

---

## 2. Datensicherheit (Data safety)

| Frage | Antwort |
|---|---|
| Erhebt oder teilt die App Nutzerdaten? | **Ja** (durch Superwall, siehe unten) |
| Werden alle Daten bei der Übertragung verschlüsselt? | **Ja** |
| Können Nutzer das Löschen ihrer Daten verlangen? | **Ja** – per E-Mail mit Support-ID (steht in der Datenschutzerklärung) |

**Erhobene Datentypen** (alle: *erhoben, nicht geteilt* – Superwall ist Dienstleister/Auftragsverarbeiter;
*nicht optional*; Zweck in Klammern):
- **Geräte- oder andere IDs** (App-Funktionen, Analysen)
- **App-Aktivität → App-Interaktionen** (Analysen) – Paywall angezeigt/benutzt, App geöffnet/geschlossen
- **Finanzinformationen → Kaufverlauf** (App-Funktionen)
- **Standort → Ungefährer Standort** (Analysen) – nur ankreuzen, wenn Superwall den aus der IP abgeleiteten Ort
  speichert. Im Zweifel ankreuzen (ehrlicher als weglassen).

**Nicht** angeben: Daten aus der Bedienungshilfe (bleiben auf dem Gerät, gelten nicht als "erhoben"), Name, E-Mail
(nur wenn jemand selbst schreibt – das ist Kommunikation ausserhalb der App), Fotos, Kontakte, Standort per GPS.

---

## 3. Bedienungshilfe-Erklärung (Accessibility declaration)
Antworten und Video-Drehbuch: `docs/play-store/bedienungshilfe-erklaerung.md`. Kurz:
- isAccessibilityTool: **Nein**
- Zweck: Shorts in der YouTube-App ausblenden/schliessen (digitales Wohlbefinden)
- Aktionen: nur Zurück bei Shorts, Zurückwischen bei Link-Short, "Not interested"/"Hide" bei Regalen,
  Tipp auf unsere Leiste → passender YouTube-Knopf. **Keine anderen Aktionen.**
- Daten via API: werden **nicht** erhoben oder geteilt (bleiben auf dem Gerät)
- Video: Hinweis in der App komplett zeigen, Zustimmen UND Ablehnen ("Not now") zeigen, dann einschalten

---

## 4. Weitere Formulare

| Formular | Antwort |
|---|---|
| **Werbung** | Enthält keine Werbung |
| **Zielgruppe** | 13–15, 16–17, 18+ (NICHT unter 13 → sonst gelten die Familien-Regeln) |
| **App-Zugang für Prüfer** | "Alle Funktionen brauchen ein Abo. Zum Testen in Settings → Redeem code den Code **Ningeninge** eingeben." |
| **Altersfreigabe (Fragebogen)** | Kategorie "Dienstprogramm/Produktivität". Gewalt, Sex, Drogen, Glücksspiel: nein. **Sprache/Schimpfwörter: ja, mild** (der Name "F*ck Shorts" und Sprüche in der App). Nutzer interagieren nicht miteinander, keine Standortweitergabe, In-App-Käufe: ja. |
| **Behörden-App / Nachrichten / Gesundheit / Finanzen** | Nein |
| **Länder** | Alle (Entscheid 03.11.: Variante C) |
| **Preis** | Kostenlos (Abo als In-App-Produkt `fckshorts_pro`) |
