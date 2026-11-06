# F*ck Shorts – To-do bis zum Release

Stand: 03.11.2026. ⬜ offen · ✅ erledigt · 👤 machst du/deine Eltern · 🤖 macht Claude

## 1. Datenschutzerklärung (jetzt)
- ✅ Gratis-Version bei FreePrivacyPolicy erstellt, E-Mail bestätigt
- ✅ Paywall: "Terms of Service" (Apple) entfernt, "Privacy Policy" zeigt auf unsere Erklärung, gespeichert
- ⬜ 👤 In Superwall auch **Publish** drücken (falls der Knopf da ist)
- ⬜ 👤 48 $ bezahlen: nur **GDPR (34 $) + Payments (14 $)**. Vorher prüfen lassen, ob danach "Edit Text" geht
- ⬜ 👤 Claude in Chrome korrigiert den Text: keine Cookies/IP/Konten/Marketing-Mails, Alter 13,
      Absatz Bedienungshilfe, Superwall, **Satz zum Löschen der Daten** ("App deinstallieren löscht alles,
      Fragen per E-Mail")
- ✅ 48 $ bezahlt, Text per "Edit Text" bearbeitet (03.11.)
- ✅ 🤖 Text gegen App und Superwall-SDK geprüft → 12 Korrekturen: `docs/play-store/privacy-policy-korrekturen.md`
- ✅ 🤖 Fertiger Text mit allen Korrekturen: `docs/play-store/privacy-policy-final.md` (gegen Code und Superwall-SDK
      geprüft: IP + ungefährer Ort, Android-ID, Installationsquelle, App öffnen/schliessen; MMP-Werbe-Zuordnung im
      Superwall-Konto aus; Superwall = Nest 22, Inc., USA)
- ✅ Live-Seite = `privacy-policy-final.md` (03.11., von Claude direkt von der Live-Seite geladen und Zeile für Zeile
      verglichen: alle 153 Zeilen da, kein alter Text übrig, nur der Seitentitel kommt dazu)
- ✅ 🤖 Zweitmeinungen (Gemini, Claude, ChatGPT) geprüft → Version 2 der Erklärung (`privacy-policy-final.md`):
      Privatperson als Anbieter, Superwall = Auftragsverarbeiter (DPA), Google eigenständig, Standardvertragsklauseln
      statt DPF, Aufbewahrung, Löschen mit Support-ID, Sicherheit, Gmail als E-Mail-Anbieter
- ✅ 🤖 Zweite Runde (Claude) geprüft → Version 3: IDs sind pseudonym (nicht "anonym"), "nötig" nur für IDs/Abo,
      Absturzberichte als Zweck, Gmail als Empfänger, Cloudflare weltweit + Liste der Sub-Prozessoren, Löschen: erst
      Support-ID kopieren, dann deinstallieren, Abo bleibt; Kinder-Geräte; alle Aktionen genannt (auch Tipp auf Leiste)
- ⬜ 👤 Version 3 mit echten Daten (Datei `privacy-policy-live.md`, NICHT im Git) per Claude in Chrome komplett ersetzen,
      dann Endvergleich durch Claude (Gemini-Runde: Hinweis vor dem Einschalten ergänzt)
- ✅ 🤖 App: Link zur Datenschutzerklärung (Einstellungen + Hinweis), Support-ID in Einstellungen, kein Backup/Umzug
      (`data_extraction_rules.xml`), Hinweis angeglichen (IP, Geräte-ID, Ausschalten) – am Handy noch NICHT getestet
- ⬜ 👤 Falls die App-E-Mail NICHT Gmail ist: "Google (Gmail)" in der Erklärung anpassen (2 Stellen)
- ⬜ 🤖 Formular "Datensicherheit": IP-Adresse/Geräte-IDs, Kaufverlauf, App-Interaktionen (Paywall) durch Superwall
- ⬜ 👤 Eigene E-Mail für die App anlegen (z. B. fckshorts.support@gmail.com)
- ⬜ 👤 Name/Kontakt eines Elternteils eintragen (erst nach der Rechtsauskunft)

## 2. Rechtliche Fragen (gratis)
- ⬜ 👤 Rechtsschutzversicherung der Eltern fragen (oft Gratis-Rechtsauskunft)
- ⬜ 👤 15 Min. Gratis-Rechtsauskunft Zürcher Anwaltsverband (Strassburgstrasse 9, Zürich, zav.ch),
      Fragen: Adresse nötig? Anmeldung/Steuern? Google-Play-/YouTube-Regeln (App verändert YouTube)?
- ⬜ 👤 Optional: HSG Law Clinic (gratis, für alle) Text kurz anschauen lassen
- ⬜ 👤 Rechtsauskunft zusätzlich fragen: EU-/UK-Vertreter nötig (Art. 27, KIs uneinig)? Einwilligung für Superwall-Statistik in der EU (ePrivacy)?
- ⬜ 👤 Später, wenn Geld reinkommt: Treuhänder (Steuern), evtl. volle Anwaltsprüfung, AGB

## 3. Google Play
- ⬜ 👤 Google-Play-Entwicklerkonto auf einen Elternteil (25 $, Ausweis, ab 18)
- ⬜ 👤 "Trader"-Angaben für die EU (Name, Adresse, Telefon, E-Mail werden in der EU angezeigt)
- ⬜ 👤 Geschlossener Test: **12 Tester, 14 Tage** (dauert am längsten → früh starten!)
- ⬜ 👤 Abo `fckshorts_pro` anlegen und aktivieren (monatlich mit 3 Tagen gratis, jährlich)
- ⬜ 👤🤖 Superwall mit Google Play verbinden (Dienstkonto, Anleitung von Claude)
- ⬜ 🤖 Anleitung für die Eltern schreiben (Konto, App anlegen, Test starten)

## 4. App (Release-Version)
- ⬜ 🤖 Signierte AAB-Datei (eigener Schlüssel, erstellt der Nutzer selbst, als GitHub-Secret)
- ✅ 🤖 "Ningeningetimer" und Protokolle mit YouTube-Texten im Release aus
- ⬜ 🤖 In den Einstellungen: "Not affiliated with YouTube or Google"
- ⬜ 🤖 Optional: Bedienbarkeit (Beschreibungen für Knöpfe, Kontrast) prüfen
- ⬜ 🤖 Sicherheit Release (aus Video-Checkliste): Release-Schlüssel **nie** ins Git (nur GitHub-Secret),
      `debuggable` aus, Superwall-Testmodus aus (schon nur in Debug), Logs aus, R8/Minify prüfen.
      Geprüft 03.11.: im Git nur `app/debug.keystore` (Absicht, Test-Schlüssel) und der öffentliche
      Superwall-Schlüssel `pk_…` (darf in die App), keine geheimen Schlüssel.

## 5. Store-Eintrag
- ⬜ 🤖 Texte (Englisch + Deutsch) **ehrlich**: keine übertriebenen Versprechen ("100 %"), "Time saved" ist geschätzt,
      Ausschalt-Wartezeit erwähnen
- ⬜ 👤 Screenshots nur aus der eigenen App, **kein YouTube-Logo**; Icon 512×512, Titelbild 1024×500
- ⬜ 👤🤖 Formulare: Datensicherheit (inkl. Daten löschen), Bedienungshilfe (`docs/play-store/`), Altersfreigabe,
      Werbung (keine)
- ⬜ 🤖 Video für die Bedienungshilfe-Prüfung (Claude im Terminal nimmt auf)
- ⬜ 👤 Prüfer-Zugang: Code "Ningeninge" angeben
- ⬜ 👤 Ersatznamen bereithalten, falls "F*ck Shorts" abgelehnt wird

## 6. Nicht nötig (geprüft, TikTok-Liste)
Cookie-Banner/Cookie-Policy, Refund-Policy (macht Google), Formular-Zustimmung, Abmelde-Link in E-Mails,
Kinder-Einwilligung (App sammelt keine Kinderdaten).

## 7. Aus der dritten Prüfung (Claude, 03.11.) – noch offen
- ⬜ 👤 Rechtsauskunft: EU-/UK-Vertreter (3 von 4 KI-Prüfungen: "wahrscheinlich nötig"; Dienste ca. 100–300 €/Jahr)
- ⬜ 👤 Rechtsauskunft: Einwilligung für Superwall-Statistik in der EU (ePrivacy) bzw. Hinweis nach Schweizer FMG
- ⬜ 👤 Optional: Support-E-Mail über Google Workspace (mit Auftragsverarbeitungsvertrag) statt Gratis-Gmail
- ⬜ 👤 Play Console: Entwicklername = "Lucien Rahm" (muss zur Datenschutzerklärung passen), Zielgruppe nur 13+
- ✅ 🤖 Superwall löscht inaktive Nutzer NICHT automatisch (DPA Annex: Daten bis Vertragsende). Löschen auf Anfrage:
      Superwall-API "App User Erasure" (läuft 25 Tage nach Anfrage) oder compliance@superwall.com. Optional in die
      Erklärung: "gelöscht spätestens 30 Tage nach deiner Anfrage".
- ✅ 🤖 Version 4: Einwilligung vs. Vertrag erklärt, Widerspruchsrecht eigener Absatz, Kinder-Abschnitt ohne
      Widerspruch, Gmail-Rolle, keine Übertreibungen, Schweizer Pflichten, Install-Info. Play-Store-Version schreibt
      KEINE Fehler-Dateien mehr (nur Test-Versionen) → Erklärung ohne Fehler-Dateien.
- ✅ Version 4 mit echten Daten LIVE (03.11., von Claude Zeile für Zeile mit der Live-Seite verglichen; E-Mail-Adressen
      sind auf der Seite per Spam-Schutz verschlüsselt, im Browser normal sichtbar). EU-Vertreter: Variante C gewählt
      (überall verkaufen, ohne Vertreter; bei Brief einer Behörde sofort einen beauftragen).

## 8. Stand 05.11.2026 (Cloud)
- ✅ 🤖 Release-Version: GitHub baut bei jedem Push zusätzlich die Play-Store-Datei (AAB). Ohne Schlüssel unsigniert
      (nur Kontrolle, Build-122+ grün), mit Secrets signiert als privates Artefakt. "Ningeningetimer" nur in Test-Versionen.
- ⬜ 👤 Release-Schlüssel am PC erstellen + 4 GitHub-Secrets: `docs/play-store/release-schluessel.md`
- ✅ 🤖 Store-Texte EN/DE + Antworten Datensicherheit/Zielgruppe/Werbung/Altersfreigabe/Prüfer-Zugang:
      `docs/play-store/store-eintrag.md`
- ✅ 🤖 Anleitung Google-Play-Konto: `docs/play-store/anleitung-google-play-konto.md`
- ⬜ 👤 12–15 Tester (Android + Gmail) sammeln; Entwicklername prüfen (Arbeitstitel "Brightmind Apps")
- ✅ 🤖 Logcat-Ausgaben in der Play-Store-Version entfernt (`app/proguard-release.pro`, R8 entfernt alle `Log.*`-Aufrufe;
      Test-Versionen behalten sie). Geprüft im fertigen Release-Code: kein Log-Aufruf aus `app.fshorts`.

## 9. Stand 05.11.2026, Nachmittag (Cloud): Sprachen
- ✅ 🤖 App in 6 Sprachen: Englisch, Deutsch (Schweiz mit "ss"), Französisch, Italienisch, Spanisch, Portugiesisch.
      Sprachwahl mit Flaggen auf der 1. Onboarding-Seite (oben rechts) und in Settings → Language. Am Handy NICHT getestet.
- ✅ 👤 Am Handy getestet: kein Text abgeschnitten (06.11.)
- ⬜ 👤 Optional: Übersetzungen von jemandem mit Muttersprache FR/IT/ES/PT kurz lesen lassen
- ⬜ 🤖 **Leiste übersetzen** (Ersatztexte "Home/Subscriptions/You" in der YouTube-Leiste) – erst mit Claude im Terminal,
      der direkt am Handy testet
- ✅ 🤖 App kleiner (06.11.): R8 + Ressourcen-Shrink in Test UND Release, nur ARM-Prozessoren. Test-APK 84 → 20 MB,
      Play-Datei (AAB) 9,6 MB. Am Handy NOCH NICHT getestet (R8 kann Reflexion brechen: Paywall, Leiste prüfen!)
- ⬜ 🤖👤 Superwall-Paywall in den 6 Sprachen übersetzen (im Superwall-Dashboard; die App gibt die Sprache schon weiter)
- ⬜ 🤖 Store-Texte zusätzlich auf Französisch, Italienisch, Spanisch, Portugiesisch (`store-eintrag.md`)

## 10. Stand 06.11.2026 (Cloud)
- ✅ 🤖 Türkisch in der App (7 Sprachen). Am Handy noch NICHT getestet.
- ✅ 🤖 Paywall in 6 Sprachen (de, fr, it, es, pt, tr): jeder Text hat Fälle nach `device.deviceLanguageCode`
      (mit/ohne Testzeit), Englisch bleibt Standard. "AI localize" ging nicht (Paywall nicht im Simple-Modus).
- ⬜ 👤 Im Superwall-Editor **Publish** drücken; am Handy mit Deutsch prüfen, ob die Paywall deutsch erscheint
- ✅ 🤖 Rechtsfragen im Internet recherchiert (EU-Vertreter, Steuern/AHV, Google-Play-Regeln) – siehe Chat 06.11.
- ⬜ 👤 Optional statt Anwalt: Gratis-Auskunft beim Steueramt/der AHV-Ausgleichskasse (wer versteuert die Abo-Einnahmen?)

## 11. Später: iPhone (entschieden 06.11.: erst Android fertig)
- Gleiche App ist auf iOS unmöglich (keine Bedienungshilfe für fremde Apps, veränderte/nachgebaute YouTube-App nicht
  erlaubt). Möglich: Safari-Erweiterung (Shorts auf youtube.com ausblenden) + YouTube-App per Bildschirmzeit-API sperren
  (Family-Controls-Erlaubnis bei Apple beantragen). Braucht Mac, Apple-Konto 99 $/Jahr (auf Lucien), iPhone zum Testen.
