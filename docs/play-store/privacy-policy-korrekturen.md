# Datenschutzerklärung – Korrekturen (Prüfung 03.11.2026)

Geprüft gegen den Code (Stand Branch `claude/intelligent-volta-4lochv`) und die Superwall-SDK 2.9.0.
Den Block unten 1:1 an Claude in Chrome geben (Modus "Edit Text"), danach den vollen Text nochmals zur Prüfung.

```
Please make these exact changes in "Edit Text" (the policy is already in Edit Text mode). Change nothing else.
Don't pay anything. After saving, regenerate the files and paste the FULL new text here.

1) In "Definitions": DELETE the whole "Affiliate" bullet (it is no longer used).

2) In "Types of Data Collected", REPLACE the first paragraph with:
We do not ask You for Your name, email address, phone number or postal address, and the Application does not access Your location, contacts or photos. The Service has no user accounts, does not use advertising services, and does not track You across other apps or websites.

3) REPLACE the whole "Device Data" subsection (heading stays "#### Device Data") with:
When You use the Service, Our subscription provider Superwall (see "Subscription Management") automatically receives technical information about Your Device and Your use of the paywall, in particular:
- anonymous identifiers created for Your installation of the Application (no name or email address)
- Your IP address
- device model, operating system version and application version
- language, region, currency and time zone settings
- technical settings such as dark mode, font size, network type and power-saving mode
- the installation date and when and how often the paywall was shown and used

4) AFTER the "Purchase and Subscription Data" subsection, ADD two new subsections:
#### Messages You Send Us
If You contact Us by email, We process Your email address and the content of Your message to answer You.

#### Information from Google Play
Google Play provides Us with information about purchases (for example order number, product, date, country and subscription status) and with aggregated statistics and technical crash reports about the Application.

5) REPLACE the whole text of the "Accessibility Service" section (heading stays) with:
F*ck Shorts uses Android's Accessibility Service API only to detect and hide YouTube Shorts in the YouTube app. To do this, the Application reads the on-screen structure of the YouTube app (for example buttons, labels, text shown on screen and their positions), which app is in the foreground, and the position of other windows on the screen. For the notification shade and the keyboard it only reads their position, not their content. It notices when the screen is touched, but not where. The Application also performs actions in the YouTube app for You: it leaves Shorts, keeps a Short opened from a link from moving on to other Shorts, and selects "Not interested" or "Hide" on Shorts shelves.

The Application may take screenshots of the YouTube window (or, if this is not possible, of the screen) to match colors and to show Your profile picture in its replacement navigation bar, and screenshots of the screen to measure a single color. Screenshots are processed in memory and discarded right away.

All of this is processed on Your Device only. It is never sent to Us or to any third party. The Application stores on Your Device only: a small crop of Your profile picture, the position of the YouTube navigation bar, Your settings and counters (for example how many Shorts were closed), and up to 12 small troubleshooting files about the YouTube screen layout (which may contain text shown on screen, such as video titles, and an image of the YouTube navigation bar). These stay on Your Device and are deleted when You uninstall the Application.

6) In "Use of Your Personal Data", ADD this bullet after "To provide and maintain Our Service":
- __To understand how the paywall performs:__ Superwall provides Us with statistics about how often the paywall is shown and how many purchases are made.

7) In "Retention of Your Personal Data", ADD these two bullets after the "Payment information" bullet:
- Data received by Superwall: retained as described in Superwall's Privacy Policy. On request, We will ask Superwall to delete data linked to Your installation.
- Emails You send Us: kept as long as needed to handle Your request and any follow-up, then deleted.

8) In "Delete Your Personal Data", REPLACE the sentence "You can delete all data stored by the app by uninstalling it. For questions or requests, contact [OWNER EMAIL]." with:
All data that the Application stores on Your Device is deleted when You uninstall the Application. To request deletion of data held by Superwall in connection with Your installation, or of emails You sent Us, contact Us at [OWNER EMAIL]. Purchase records kept by Google are governed by Google's Privacy Policy.

9) In "Subscription Management", REPLACE "Superwall receives purchase and subscription information and basic Device Data (device model, language, application version)." with:
Superwall receives purchase and subscription information and the Device Data described in "Device Data" above, including Your IP address. Superwall is a company based in the United States.

10) In "Legal Basis for Processing Personal Data under GDPR", in the second list:
- "Performance of a contract:" REPLACE its text with: providing and maintaining the Service, displaying the paywall, processing purchases and subscriptions, and providing support.
- "Legitimate interests:" REPLACE its text with: securing the Service and preventing fraud and abuse; understanding how the paywall performs; answering Your messages; enforcing Our agreements and establishing, exercising, or defending legal claims; and evaluating or completing business transfers. Where We rely on legitimate interests, We balance that interest against Your rights and freedoms.
Then, directly before "If You have questions about the legal basis…", ADD:
Providing purchase information is necessary to buy a subscription; without it, You cannot subscribe.

11) In "International Transfer of Personal Data", ADD after the paragraph that starts "We transfer Personal Data internationally only as needed…":
In particular, Personal Data is transferred to the United States (Superwall) and may be processed by Google in the United States and other countries. For transfers from Switzerland, the EEA or the UK, We rely on the EU-U.S. Data Privacy Framework and its Swiss and UK extensions where the recipient is certified, or on standard contractual clauses recognized by the competent authorities, including the Swiss Federal Data Protection and Information Commissioner (FDPIC).

12) In "Your Rights under the GDPR", ADD after the list of rights (before "Exercising of Your GDPR Data Protection Rights"):
If You are in Switzerland, You have corresponding rights under the Swiss Federal Act on Data Protection (FADP), in particular the right to access, correction, deletion and data portability.
```

## Warum (kurz)
- Superwall bekommt laut SDK-Code viel mehr als "Modell, Sprache, App-Version": anonyme IDs, **IP-Adresse**,
  Android-Version, Region/Währung/Zeitzone, Dark Mode, Schriftgrösse, Netzwerktyp, Installationsdatum, Paywall-Statistik.
- "keine Tracking-Technologien / keine Analyse" stimmte so nicht (Superwall-IDs, Paywall-Statistik).
- Die App speichert auch bis zu 12 Fehler-Dateien (mit Bildschirmtexten wie Videotiteln) – steht schon im
  Hinweis in der App, fehlte in der Erklärung.
- Bildschirmfotos auch vom ganzen Bildschirm (Farbe messen), Berührungen werden bemerkt, App handelt in YouTube.
- E-Mails an euch und Daten von Google Play (Bestellungen, Absturzberichte) fehlten.
- Schweizer Gesetz (DSG): Länder der Übermittlung nennen (USA) und Schweizer Rechte erwähnen.
- "Daten löschen" durch Deinstallieren gilt nur fürs Handy, nicht für Superwall.

## Fragen für die Rechtsauskunft (ZAV / HSG)
- Braucht es als Anbieter aus der Schweiz einen EU-Vertreter (DSGVO Art. 27)?
- Reicht "nicht für Kinder unter 13"? In der EU gilt für Einwilligungen teils 16.
- Darf ein Elternteil als Anbieter mit Name + Adresse stehen, reicht eine App-E-Mail?
