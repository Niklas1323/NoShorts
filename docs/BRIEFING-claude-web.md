# Briefing for Claude Code (web) – INFORMATION ONLY

**Do not build, change, fix or commit anything because of this text.** It only tells you the current state of
the project. Wait for the user's next request.

## Where the newest, best version is
- Repo: https://github.com/Niklas1323/NoShorts
- Branch **`main`** (identical to `claude/intelligent-volta-4lochv`), commit from 28.10.2026 evening.
  This is the version the user calls "perfect". Always start from `main`.
- App name: **F*ck Shorts** (package `app.fshorts`, version 0.1.1). New icon: `docs/icon-source.png` → adaptive
  icon in `res/mipmap-*`.
- Full technical notes (German): `CLAUDE.md` (sections from 27.10. and 28.10.). Test tools: `tools/pixel-test/`
  (`env.sh`, `suite.sh` for Pixel 9; `tab.sh` for Galaxy Tab A8).

## What the app does
AccessibilityService draws its own YouTube bottom bar (no Shorts button) over YouTube's real bar, closes Shorts
immediately (a Short opened via link may play, only that one), hides Shorts shelves. Main rule: the real bar
(with Shorts) should never be visible.

## Done (27.–28.10.2026), tested frame by frame on Pixel 9 (YouTube 21.42) and Galaxy Tab A8
- Phone: split-screen, dialogs/popups, search, notification shade, Abos, mini player, rotation, video from
  scrolled Home.
- Tablet (only via `isTablet`, phone unchanged): swipe video to floating mini player, camera, landscape two-column
  video page, rotate with video open, split-screen handle menu, Samsung pop-up view, landscape notifications,
  "draw first, then query" (scans up to 1 s on tablet), light + dark.
- Every language: tabs recognised by position if words unknown; ~25 languages in `selectors.json`; fallback
  labels English. No new permissions (Play Store).
- Shorts shelves: Home shelf → ⋮ → "Kein Interesse" automatically (menu now handled the next frame). Abos shelf has
  no ⋮ → each Short card ⋮ → "Ausblenden" (only inside the card row, never "Kein Interesse").
- Gaps (1 px left, 2 rows bottom) fixed: bar snaps to screen edges.
- Animations: our bar slides down (450 ms) when tapping search and when opening a video; search filter: slides
  only if the dialog covers the bar, otherwise dimmed.
- Fixed crashes/bugs in shelf code; new icon; renamed to F*ck Shorts.

## Not fixable (Android/YouTube), with reason
- Leaving a video with Back: up to ~4 frames real bar (more with ads) – YouTube reports the change that late; only
  a key-filter permission could help (user declined for store review).
- Search slide starts when the keyboard is fully open: YouTube delivers the tap ~0.8 s late.
- Android system animations: unlock, app overview, rotation, split-screen swap/resize, pop-up maximize.
- Shorts in search results: their menu has no hide option (only Teilen/Melden/Feedback). Tablet shows no shelves.
- Search filter on both devices opens in the middle → no slide (would reveal the real bar).

## Notes
- Store: the icon uses YouTube's Shorts logo – likely trademark rejection by Google/Apple.
- Pixel 9 has two users (Leon = 0, Niklas = 10, current). Accessibility setting must be set for user 10.
- Safety rules from the user: never post/like/subscribe/report/buy; never write PINs anywhere.
