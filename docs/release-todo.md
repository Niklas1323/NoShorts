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
- ⬜ 👤 Claude in Chrome ersetzt den Text in "Edit Text" komplett durch `privacy-policy-final.md`, danach kurzer Blick von Claude
- ⬜ 🤖 Hinweis in der App (Onboarding `Disclosure`) angleichen: Superwall bekommt auch IP-Adresse und anonyme IDs
- ⬜ 🤖 Formular "Datensicherheit": IP-Adresse/Geräte-IDs, Kaufverlauf, App-Interaktionen (Paywall) durch Superwall
- ⬜ 👤 Eigene E-Mail für die App anlegen (z. B. fckshorts.support@gmail.com)
- ⬜ 👤 Name/Kontakt eines Elternteils eintragen (erst nach der Rechtsauskunft)

## 2. Rechtliche Fragen (gratis)
- ⬜ 👤 Rechtsschutzversicherung der Eltern fragen (oft Gratis-Rechtsauskunft)
- ⬜ 👤 15 Min. Gratis-Rechtsauskunft Zürcher Anwaltsverband (Strassburgstrasse 9, Zürich, zav.ch),
      Fragen: Adresse nötig? Anmeldung/Steuern? Google-Play-/YouTube-Regeln (App verändert YouTube)?
- ⬜ 👤 Optional: HSG Law Clinic (gratis, für alle) Text kurz anschauen lassen
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
- ⬜ 🤖 "Ningeningetimer" und Protokolle mit YouTube-Texten im Release aus
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
