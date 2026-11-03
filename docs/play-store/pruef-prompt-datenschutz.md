# Prüf-Prompt für eine zweite Meinung (Claude / ChatGPT / Gemini)

Alles im grauen Kasten kopieren und in einen neuen Chat einfügen. Danach die Antwort an Claude Code zurückgeben.

```
You are an experienced data protection reviewer (GDPR, Swiss FADP/revDSG, Google Play policies).
Please review the privacy policy of my Android app very carefully. I am not asking for a rewrite;
I want a list of concrete problems.

## The privacy policy
Live link: https://www.freeprivacypolicy.com/live/478be282-9e4c-4797-83de-8e73fbdf6626
(If you cannot open links, I will paste the full text below this prompt.)

## Facts about the app (verified in the source code — treat these as true)
App: "F*ck Shorts", Android only, distributed via Google Play. Paid subscription (monthly with 3-day free trial,
yearly) through Google Play Billing. Without a subscription (or a free promo code) the app does nothing.
Provider: a private individual in Switzerland (a parent, because the developer is a minor). No company.
Users: worldwide (EU, Switzerland, UK, USA, Australia, ...). Not directed to children under 13.

What the app does: it uses Android's AccessibilityService to hide the Shorts button in the official YouTube app
(draws its own navigation bar over YouTube's bar), closes Shorts immediately (performs "Back"), and hides Shorts
shelves by selecting "Not interested" / "Hide" in YouTube's menus. A Short opened from a shared link may play,
but swiping to other Shorts is undone. Users can pause protection after a waiting timer.

Data processed ON THE DEVICE ONLY (never sent anywhere, the app has no own server and no own network code):
- on-screen structure of the YouTube app (buttons, labels, text on screen, positions), foreground app,
  positions of other windows; for notification shade and keyboard only their position (not content)
- that the screen is touched (not where)
- screenshots of the YouTube window or the screen, only to match colors, copy the profile picture into the bar
  and measure a single color; processed in memory and discarded
- stored locally: profile picture crop, bar position, settings, counters (e.g. Shorts closed), up to 12
  troubleshooting files with YouTube screen layout (may contain on-screen text like video titles) and an image
  of YouTube's bar. All deleted on uninstall. Not shareable from the app.

Data that leaves the device (only via third parties):
- Superwall (Nest 22, Inc., USA) — paywall + subscription status SDK. Receives: app-specific Android ID and
  anonymous Superwall IDs, IP address (approximate location can be derived), device model, OS version, app
  version, language/region/currency/time zone, dark mode, font size, network type, power-saving mode, install
  date, Google Play install referrer (if available), app open/close events, paywall views and interactions,
  purchase and subscription status. Superwall's ad attribution (MMP) is switched OFF. No advertising ID.
- Google Play: payments/subscriptions; gives the developer order data, aggregated stats and crash reports.
- Email: if a user writes to the support email address.
No user accounts, no ads, no analytics SDK besides Superwall's paywall statistics, no cookies, no location
services, no contacts/photos access. Permissions: INTERNET, BILLING (notification permission removed).
Before enabling the AccessibilityService, the app shows an in-app disclosure that the user must accept.

## What I want you to check
1. Does the policy match the facts above? List anything that is wrong, missing, or claims more/less than the
   app does.
2. GDPR Art. 13 and Swiss FADP Art. 19: is every required item there (controller identity/contact, purposes,
   legal bases, recipients, transfers incl. countries and safeguards, retention, rights, complaint, withdrawal
   of consent, whether data is required)?
3. Google Play: User Data policy, Accessibility API policy (prominent disclosure), Data safety consistency.
   What would a Google reviewer object to?
4. Does a Swiss private individual selling to EU users need an EU representative (GDPR Art. 27)?
5. Is "not directed to children under 13" fine, given EU ages of digital consent up to 16?
6. Anything risky or misleading in wording (e.g. "We will ask Superwall to delete data")?

## Answer format
A table with: # | Section of the policy | Problem | Why (law/policy, article if possible) | Suggested fix
(exact replacement text) | Severity (must fix / should fix / optional).
Then a short overall verdict. Be precise; do not invent requirements. If you are unsure, say so.
```
