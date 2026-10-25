package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import app.fshorts.Prefs
import app.fshorts.R
import kotlin.math.roundToInt

/**
 * Das Herz der App. Läuft im Hintergrund, sobald du Fuck Shorts in den Bedienungshilfen einschaltest.
 *
 * - Ist YouTube offen, legt er unsere Leiste (ohne Shorts) über die echte Leiste.
 * - Geht ein Short auf, drückt er sofort "Zurück".
 * - Bei jeder anderen App versteckt er die Leiste und liest nichts.
 */
class FuckShortsService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var selectors: Selectors
    private lateinit var scanner: YouTubeScanner
    private lateinit var overlay: BottomBarOverlay
    private lateinit var covers: ShelfCovers
    private lateinit var dumper: TreeDumper
    private lateinit var sampler: BarStyleSampler

    private var connected = false
    private var scanPending = false
    private var lastScanAt = 0L
    private var lastBackAt = 0L
    private val recentBacks = ArrayDeque<Long>()
    private var lastHintAt = 0L
    private var youTubeSince = 0L
    private var youTubeActive = false
    private var barSeenOnce = false
    private var lastAutoDumpAt = 0L
    private var lastDebugDumpAt = 0L
    private var debugDumpWanted = false
    private var nightMode = 0
    private var screen = Rect()

    // Leiste: YouTube schiebt seine Leiste beim Runterscrollen raus und beim Hochscrollen wieder rein.
    private var restBounds: Rect? = null
    private var lastBar: FoundBar? = null
    private var lastUiState: BarUiState? = null
    private var hiddenByScroll = false
    private var navFromRest = false
    private var watchPageOpen = false
    private var watchNode: AccessibilityNodeInfo? = null
    private var watchTop = 0
    /** Der letzte volle Scan hat unsere Leiste absichtlich versteckt (Shorts, verdeckt): nicht vorschnell zeigen. */
    private var hiddenOnPurpose = false
    /**
     * YouTube nimmt seine Leiste beim Runterscrollen (z. B. Startseite) ganz aus der Oberfläche. Dann merken wir
     * uns die Liste der Seite: Solange es sie gibt, sind wir noch auf derselben Seite und unsere Leiste bleibt.
     */
    private var goneFeed: AccessibilityNodeInfo? = null
    private var lastScrollAt = 0L
    private var tapSeq = 0
    private var foregroundNullSince = 0L
    private var miniPlayerOpen = false
    private var lastMiniCheckAt = 0L
    private var lastMiniWalkAt = 0L
    private var lastNavAt = 0L
    private var restSince = 0L
    private var fastUntil = 0L
    private var lastObservedTop = -1
    private var pollUntil = 0L
    private var polling = false

    // Shorts-Regale und automatisches "Nicht interessiert".
    private var shelves: List<Shelf> = emptyList()
    private var shelfMenuOpenedAt = 0L
    private var lastShelfHideAt = 0L
    private var shelfHideBroken = false

    private val scanRunnable = Runnable {
        scanPending = false
        lastScanAt = SystemClock.uptimeMillis()
        scan()
    }

    // Sicherheitsnetz: Solange etwas von uns sichtbar ist, prüfen wir regelmässig, ob YouTube noch vorne ist.
    private val watchdog = Runnable { scheduleScan() }

    // Während gescrollt wird: jede 16 ms die Lage von Leiste und Regal nachführen.
    private val pollRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            // Solange unsere Leiste weg ist, die echte aber noch existiert (z. B. Video offen), weiter nachsehen:
            // So sind wir sofort da, wenn die echte wieder reinfährt.
            val waitingForBar = !overlay.isShowing && (lastBar != null || watchPageOpen)
            if (!connected || !youTubeActive || (now > pollUntil && !waitingForBar && now > fastUntil)) {
                polling = false
                return
            }
            try {
                updateBar(now, null)
                updateShelfPositions()
                if (now < fastUntil) scheduleScan()
            } catch (e: Exception) {
                Log.w(TAG, "Nachführen fehlgeschlagen", e)
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        selectors = Selectors.load(this)
        scanner = YouTubeScanner(selectors, resources.displayMetrics.density)
        covers = ShelfCovers(this)
        overlay = BottomBarOverlay(this, ::onTabTapped, ::onBarLongPressed)
        dumper = TreeDumper(this)
        sampler = BarStyleSampler(this)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        connected = true
        running = true
        scheduleScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!connected) return
        val pkg = event.packageName?.toString()
        if (pkg == packageName) return
        when {
            pkg == selectors.youtubePackage -> {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        debugDumpWanted = true
                        onNavigation()
                    }
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        onNavigation()
                        // Ein Tipp kann einen Short öffnen: die nächsten 1,5 s ganz oft nachsehen, damit er sofort zugeht.
                        fastUntil = SystemClock.uptimeMillis() + FAST_WINDOW_MS
                        startPolling()
                    }
                    AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled()
                }
                // Jede Änderung kann heissen, dass die Leiste gerade rein- oder rausfährt: Bild für Bild nachführen.
                if (lastBar != null) startPolling()
                scheduleScan()
            }
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED -> scheduleScan()
            overlay.isShowing || covers.anyVisible || shelfMenuOpenedAt != 0L -> scheduleScan()
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!connected) return
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night != nightMode) {
            nightMode = night
            sampler.reset()
        }
        restBounds = null
        hideAll()
        scheduleScan()
    }

    override fun onDestroy() {
        running = false
        connected = false
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.destroy()
        if (::covers.isInitialized) covers.destroy()
        super.onDestroy()
    }

    private fun scheduleScan() {
        if (scanPending) return
        scanPending = true
        val now = SystemClock.uptimeMillis()
        val interval = if (now < fastUntil) POLL_MS else MIN_SCAN_INTERVAL_MS
        val delay = (lastScanAt + interval - now).coerceAtLeast(0)
        handler.postDelayed(scanRunnable, delay)
    }

    /** Nächster Scan erst nach ein paar Bildern, damit Android unsere Leiste vorher zeichnen kann. */
    private fun continueScanSoon() {
        handler.postDelayed({ scheduleScan() }, DRAW_FIRST_MS)
    }

    private fun startPolling() {
        pollUntil = SystemClock.uptimeMillis() + POLL_DURATION_MS
        if (!polling) {
            polling = true
            handler.post(pollRunnable)
        }
    }

    /** Beim Scrollen fährt YouTube seine Leiste raus/rein. Wir prüfen ab jetzt jedes Bild, damit unsere stehen bleibt. */
    private fun onScrolled() {
        lastScrollAt = SystemClock.uptimeMillis()
        if (youTubeActive) updateBar(SystemClock.uptimeMillis(), null)
        startPolling()
    }

    /**
     * Ein Tipp in YouTube. Nur wenn die echte Leiste dabei ganz da war, darf unsere danach mit ihr verschwinden
     * (z. B. Video geöffnet). War sie weggescrollt (z. B. Tipp auf ⋮), bleibt unsere stehen.
     */
    private fun onNavigation() {
        lastNavAt = SystemClock.uptimeMillis()
        navFromRest = !hiddenByScroll
    }

    private fun scan() {
        handler.removeCallbacks(watchdog)
        val started = SystemClock.uptimeMillis()
        try {
            scanInternal()
            val took = SystemClock.uptimeMillis() - started
            if (took > SLOW_SCAN_MS) Log.d(TAG, "Scan dauerte $took ms")
        } catch (e: Exception) {
            // Lieber nichts anzeigen als YouTube blockieren.
            Log.w(TAG, "Scan fehlgeschlagen", e)
            hideAll()
        }
        if (overlay.isShowing || covers.anyVisible) handler.postDelayed(watchdog, WATCHDOG_MS)
        // Unsere Leiste ist weg, die echte gibt es noch (z. B. Video offen): laufend nachsehen.
        if (youTubeActive && !overlay.isShowing && (lastBar != null || watchPageOpen)) startPolling()
    }

    private fun hideAll() {
        overlay.hide()
        covers.hideAll()
    }

    private var lastReason = ""

    /** Schreibt ins Protokoll (adb logcat -s FuckShorts), warum die Leiste gerade steht oder weg ist. Nur bei Wechsel. */
    private fun reason(text: String) {
        if (text == lastReason) return
        lastReason = text
        Log.d(TAG, "Leiste: $text")
    }

    private fun scanInternal() {
        val now = SystemClock.uptimeMillis()
        if (shelfMenuOpenedAt != 0L) {
            handleShelfMenu(now)
            return
        }
        val foreground = foregroundWindow()?.root
        if (foreground == null && youTubeActive) {
            // Beim Fensterwechsel (z. B. Plus -> Kamera) meldet Android kurz gar kein Fenster. Nicht gleich alles
            // verstecken, sonst sieht man die echte Leiste, bis das neue Fenster da ist.
            if (foregroundNullSince == 0L) foregroundNullSince = now
            if (now - foregroundNullSince < FOREGROUND_GAP_MS) {
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
        } else {
            foregroundNullSince = 0L
        }
        if (foreground == null || foreground.packageName?.toString() != selectors.youtubePackage || isKeyboardOpen()) {
            reason("YouTube nicht vorne (${foreground?.packageName})")
            youTubeActive = false
            youTubeSince = 0L
            hideAll()
            return
        }
        val root = youTubeMainRoot() ?: foreground
        if (!youTubeActive) {
            // YouTube kommt gerade nach vorne: unsere Leiste sofort an der gemerkten Stelle zeigen,
            // nicht erst nach dem Suchen. Die normale Logik übernimmt ab dem nächsten Durchgang.
            val rest = restBounds
            val state = lastUiState
            if (rest != null && state != null && !watchPageOpen) {
                youTubeActive = true
                youTubeSince = now
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                continueScanSoon()
                return
            }
        }
        youTubeActive = true
        if (youTubeSince == 0L) youTubeSince = now
        val newScreen = screenBounds()
        if (newScreen != screen) {
            screen = newScreen
            restBounds = null
        }
        // Gerade getippt: Zuerst nur nachsehen, ob ein Short aufgeht, damit er so schnell wie möglich wieder zugeht.
        if (now < fastUntil && scanner.isShortsPlayerOpen(root, screen)) {
            onShortsOpen()
            return
        }

        // Video offen: nur den Player nachsehen (eine Abfrage). Eine volle Suche dauert, während YouTube die
        // Video-Seite wegschiebt, bis zu 500 ms und hält so die Bild-für-Bild-Prüfung auf.
        val openWatch = watchNode
        if (watchPageOpen && openWatch != null) {
            if (scanner.isStillLargePlayer(openWatch, watchTop, screen)) {
                // Beim Öffnen fährt der Player noch hoch: immer die höchste Lage merken.
                watchTop = minOf(watchTop, scanner.topOf(openWatch))
                reason("Video offen")
                hideAll()
                return
            }
            // Video geht gerade zu: unsere Leiste sofort zeigen, die volle Suche unten prüft danach alles.
            watchPageOpen = false
            watchNode = null
            val rest = restBounds
            val state = lastUiState
            if (rest != null && state != null) {
                reason("Video verlassen (Scan)")
                hiddenByScroll = false
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                // Erst zeichnen lassen, dann weitersuchen: Die volle Suche blockiert sonst das Zeichnen.
                continueScanSoon()
                return
            }
        }

        // Reihenfolge ist wichtig: Erst alles, was nötig ist, damit unsere Leiste richtig steht. Langsames
        // (Mini-Player, Profilbild, Regale) danach, sonst sieht man beim Verlassen eines Videos die echte Leiste.
        val bar = scanner.findBar(root, screen)
        watchNode = scanner.findWatchPlayer(root, screen)?.also { watchTop = scanner.topOf(it) }
        watchPageOpen = watchNode != null

        // Echte Leiste ist wieder da (z. B. zurück aus der Kamera), unsere noch nicht: sofort zeigen. Jede weitere
        // Abfrage kostet jetzt 30–50 ms, weil YouTube gerade umbaut. Shorts & Co. prüft der nächste Scan.
        val rest = restBounds
        val state = lastUiState
        if (bar != null && !overlay.isShowing && !watchPageOpen && !hiddenOnPurpose && rest != null && state != null &&
            bar.tabs[TabKind.SHORTS]?.selected != true && bar.bounds.top < screen.bottom - 2
        ) {
            lastBar = bar
            lastObservedTop = bar.bounds.top
            reason("echte wieder da (Scan, top=${bar.bounds.top})")
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            continueScanSoon()
            return
        }

        if (scanner.isShortsScreen(root, bar, screen)) {
            onShortsOpen()
            return
        }

        if (bar != null) {
            barSeenOnce = true
            goneFeed = null
            lastBar = bar
            rememberRestPosition(bar)
            if (scanner.isBarCovered(root, bar)) {
                reason("verdeckt ${bar.bounds.toShortString()}")
                hiddenOnPurpose = true
                hideAll()
                return
            }
            hiddenOnPurpose = false
            // Positionen nur von der ganz eingefahrenen Leiste übernehmen, sonst nur Auswahl und roten Punkt.
            val atRest = bar.bounds == restBounds
            if (!atRest) restSince = 0L else if (restSince == 0L) restSince = now
            val previous = lastUiState
            lastUiState = if (atRest || previous == null) {
                buildUiState(bar)
            } else {
                previous.copy(
                    selected = bar.tabs.values.firstOrNull { it.selected }?.kind ?: previous.selected,
                    subscriptionsBadge = bar.tabs[TabKind.SUBSCRIPTIONS]?.badge ?: previous.subscriptionsBadge,
                )
            }
        } else if (lastBar == null) {
            if (rest != null && state != null && keepWhileScrolledAway(now)) {
                reason("echte weggescrollt und entfernt, unsere bleibt (Scan)")
                hiddenByScroll = true
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                return
            }
            reason("keine Leiste gefunden")
            hideAll()
            val stuck = !barSeenOnce && now - youTubeSince > AUTO_DUMP_AFTER_MS
            if (stuck && now - lastAutoDumpAt > AUTO_DUMP_INTERVAL_MS) {
                lastAutoDumpAt = now
                dumper.dump(root, windows, "leiste-nicht-gefunden")
            }
            return
        }

        val wasShowing = overlay.isShowing
        updateBar(now, bar)
        if (!wasShowing && overlay.isShowing) {
            // Unsere Leiste kommt gerade: sofort zeichnen lassen, Mini-Player, Profilbild und Regale danach.
            continueScanSoon()
            return
        }

        if (now - lastMiniCheckAt > MINI_CHECK_MS) {
            lastMiniCheckAt = now
            val walk = now - lastMiniWalkAt > MINI_WALK_MS
            if (walk) lastMiniWalkAt = now
            miniPlayerOpen = scanner.isMiniPlayerVisible(root, screen, walk)
        }

        if (debugDumpWanted) {
            debugDumpWanted = false
            if (Prefs.debugDumps(this) && now - lastDebugDumpAt > DEBUG_DUMP_INTERVAL_MS) {
                lastDebugDumpAt = now
                dumper.dump(root, windows, "bildschirmwechsel")
            }
        }

        // Icons und Profilbild von der echten Leiste abschauen, sobald sie ganz still steht.
        if (bar != null && bar.bounds == restBounds && sampler.needsCapture(bar)) {
            if (now - restSince < REST_STABLE_MS) {
                handler.postDelayed({ scheduleScan() }, REST_STABLE_MS)
            } else if (sampler.canCaptureBehindOverlay) {
                sampler.capture(bar, root.windowId, windowBounds(root.windowId)) { scheduleScan() }
            } else if (!overlay.isShowing) {
                // Ältere Android-Versionen: nur ganzer Bildschirm, darum vorher nichts von uns zeigen.
                sampler.capture(bar, root.windowId, Rect(screen)) { scheduleScan() }
                return
            }
        }

        shelves = scanner.findShelves(root, screen, coverLimit())
        covers.show(shelves.map { it.bounds }, currentStyle())
        if (shelves.isNotEmpty()) tryHideShelf(shelves, now)
    }

    /** Merkt sich, wo die echte Leiste sitzt, wenn sie ganz eingefahren ist (höchste Lage, volle Höhe). */
    private fun rememberRestPosition(bar: FoundBar) {
        val rest = restBounds
        val b = bar.bounds
        if (rest == null || b.height() > rest.height() + 2 || (b.height() >= rest.height() - 2 && b.top < rest.top)) {
            restBounds = Rect(b)
        }
    }

    /**
     * Setzt unsere Leiste auf die richtige Höhe.
     * Fährt YouTube seine Leiste wegen Scrollen raus, bleibt unsere einfach stehen. Ein Fenster einer anderen
     * App lässt sich nie Bild für Bild genau mitbewegen; so sieht man die echte Leiste dafür nie.
     * Verschwindet die Leiste aus einem anderen Grund (z. B. Video geöffnet), verschwindet unsere mit.
     */
    private fun updateBar(now: Long, fresh: FoundBar?) {
        val rest = restBounds
        val state = lastUiState
        if (rest == null || state == null) {
            overlay.hide()
            return
        }
        // Video offen: Die echte Leiste liegt unverändert hinter dem Video (oder fehlt ganz, wenn die Startseite
        // weggescrollt war). Das Verlassen merkt man darum nicht an ihr, sondern daran, dass der grosse Player
        // nach unten wegrutscht. YouTube meldet dabei ~100 ms lang nichts; darum bei jedem Bild selbst nachsehen.
        if (watchPageOpen && fresh == null) {
            val node = watchNode
            if (node != null && scanner.isStillLargePlayer(node, watchTop, screen)) {
                watchTop = minOf(watchTop, scanner.topOf(node))
                reason("Video offen")
                overlay.hide()
                return
            }
            watchPageOpen = false
            watchNode = null
            reason("Video verlassen")
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            scheduleScan()
            return
        }
        val observedTop = when {
            fresh != null -> fresh.bounds.top
            else -> lastBar?.let { scanner.liveTop(it.container, screen) }
        }
        if (observedTop == null) {
            if (keepWhileScrolledAway(now)) {
                reason("echte weggescrollt und entfernt, unsere bleibt")
                hiddenByScroll = true
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                return
            }
            // Die Leiste gibt es nicht mehr (anderer Bildschirm, z. B. Kamera nach Plus): sofort weg.
            reason("Leiste nicht mehr da")
            lastBar = null
            lastObservedTop = -1
            overlay.hide()
            return
        }
        // Taucht die echte Leiste gerade wieder auf oder fährt hoch (z. B. Video verlassen), steht unsere
        // sofort ganz da. So kommt die echte nie zuerst.
        val visibleNow = observedTop < screen.bottom - 2
        val wasHidden = lastObservedTop < 0 || lastObservedTop >= screen.bottom - 2
        val appearing = visibleNow && wasHidden
        val movingUp = visibleNow && lastObservedTop >= 0 && observedTop < lastObservedTop - 1
        lastObservedTop = observedTop
        if (appearing || movingUp) {
            reason("echte taucht auf (top=$observedTop)")
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            return
        }
        if (watchPageOpen) {
            reason("Video offen")
            overlay.hide()
            return
        }
        reason("normal (top=$observedTop)")
        // Startseite: unsere Leiste bleibt immer ganz da und fährt nie halb runter. Sie verschwindet erst,
        // wenn nach einem Tipp (Video geöffnet) die echte Leiste schon komplett weg ist.
        val onHome = state.selected == TabKind.HOME
        val videoOpening = now - lastNavAt < NAV_LINK_MS && navFromRest
        val fullyGone = observedTop >= screen.bottom - 2
        if (onHome) {
            if (observedTop <= rest.top + 2) hiddenByScroll = false
            if (videoOpening && fullyGone) {
                overlay.hide()
            } else {
                if (!fullyGone || overlay.isShowing) hiddenByScroll = observedTop > rest.top + 2
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            }
            return
        }
        // Andere Seiten: Mini-Player offen oder Video geöffnet -> mit der echten Leiste mitfahren
        // (YouTube setzt den Mini-Player direkt über seine Leiste). Sonst stehen bleiben.
        val follow = videoOpening || miniPlayerOpen
        if (observedTop <= rest.top + 2) {
            hiddenByScroll = false
        } else if (!follow && overlay.isShowing) {
            hiddenByScroll = true
        } else if (follow) {
            hiddenByScroll = false
        }
        val top = if (hiddenByScroll) rest.top else observedTop.coerceAtLeast(rest.top)
        if (top >= screen.bottom - 2) {
            overlay.hide()
        } else {
            overlay.show(top, rest, state.copy(style = currentStyle()))
        }
    }

    /** Echte Leiste ist weg: Nur weil gescrollt wurde (dieselbe Liste ist noch da)? Dann bleibt unsere stehen. */
    private fun keepWhileScrolledAway(now: Long): Boolean {
        if (watchPageOpen) {
            goneFeed = null
            return false
        }
        val known = goneFeed
        if (known != null && scanner.isFeedStillThere(known, screen)) return true
        // Noch keine Liste gemerkt, oder sie war beim schnellen Wischen kurz nicht abfragbar: neu suchen,
        // aber nur direkt nach dem Scrollen und ohne Tipp dazwischen (sonst ist es wohl eine andere Seite).
        goneFeed = null
        if (lastNavAt > lastScrollAt) return false
        if (known == null && now - lastScrollAt > SCROLL_GONE_MS) return false
        val root = youTubeMainRoot() ?: return false
        goneFeed = scanner.findFeed(root, screen)
        return goneFeed != null
    }

    private fun updateShelfPositions() {
        if (shelves.isEmpty()) return
        val limit = coverLimit()
        covers.show(shelves.mapNotNull { scanner.refreshShelf(it, limit) }, currentStyle())
    }

    private fun coverLimit(): Int = if (overlay.isShowing) overlay.top else screen.bottom

    private fun currentStyle(): BarStyle = sampler.style(nightMode == Configuration.UI_MODE_NIGHT_YES)

    private fun windowBounds(windowId: Int): Rect =
        windows.firstOrNull { it.id == windowId }?.let { w -> Rect().also { w.getBoundsInScreen(it) } } ?: Rect(screen)

    private fun buildUiState(bar: FoundBar): BarUiState {
        // Feste Abstände wie im Vorlagenbild (ab Oberkante der Leiste).
        val density = resources.displayMetrics.density
        val rest = restBounds ?: bar.bounds
        return BarUiState(
            selected = bar.tabs.values.firstOrNull { it.selected }?.kind,
            labels = bar.tabs.mapNotNull { (kind, tab) -> tab.label?.let { kind to it } }.toMap(),
            subscriptionsBadge = bar.tabs[TabKind.SUBSCRIPTIONS]?.badge == true,
            hasCreate = TabKind.CREATE in bar.tabs,
            barHeightPx = rest.height(),
            iconCenterPx = (ICON_CENTER_DP * density).roundToInt(),
            labelTopPx = (LABEL_TOP_DP * density).roundToInt(),
            showHandle = rest.bottom >= screen.bottom - 2,
            style = currentStyle(),
        )
    }

    /** Tippt auf ⋮ beim Shorts-Regal. Den Menüpunkt "Nicht interessiert" wählt [handleShelfMenu]. */
    private fun tryHideShelf(shelves: List<Shelf>, now: Long) {
        if (shelfHideBroken || !Prefs.autoHideShelf(this)) return
        if (now - lastShelfHideAt < SHELF_HIDE_INTERVAL_MS) return
        val button = shelves.firstNotNullOfOrNull { it.menuButton } ?: return
        lastShelfHideAt = now
        if (button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            shelfMenuOpenedAt = now
            handler.postDelayed({ scheduleScan() }, 150)
        }
    }

    private fun handleShelfMenu(now: Long) {
        val roots = windows.mapNotNull { it.root }.filter { it.packageName?.toString() == selectors.youtubePackage }
        val item = scanner.findMenuItem(roots, selectors.shelfHideLabels)
        if (item != null) {
            shelfMenuOpenedAt = 0L
            if (click(item)) Prefs.incrementShelvesHidden(this)
            scheduleScan()
            return
        }
        if (now - shelfMenuOpenedAt < SHELF_MENU_TIMEOUT_MS) {
            handler.postDelayed({ scheduleScan() }, 100)
            return
        }
        // Menüpunkt nicht gefunden: Menü schliessen, Aufbau speichern, in dieser Sitzung nicht mehr probieren.
        shelfMenuOpenedAt = 0L
        shelfHideBroken = true
        val active = foregroundWindow()?.root
        if (active != null && active.packageName?.toString() == selectors.youtubePackage) {
            dumper.dump(active, windows, "regal-menue")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        scheduleScan()
    }

    /**
     * Ein Short ist offen: sofort zurück. Unsere Leiste bleibt dabei stehen, sonst sieht man, bis der Short
     * zu ist, die echte Leiste mit dem Shorts-Knopf.
     */
    private fun onShortsOpen() {
        reason("Shorts offen")
        hiddenOnPurpose = true
        covers.hideAll()
        leaveShorts()
    }

    private fun leaveShorts() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < BACK_COOLDOWN_MS) return
        lastBackAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        Prefs.incrementShortsBlocked(this)

        recentBacks.addLast(now)
        while (recentBacks.isNotEmpty() && now - recentBacks.first() > HINT_WINDOW_MS) recentBacks.removeFirst()
        if (recentBacks.size >= 2 && now - lastHintAt > HINT_WINDOW_MS) {
            lastHintAt = now
            Toast.makeText(this, R.string.shorts_hidden_toast, Toast.LENGTH_SHORT).show()
        }
        scheduleScan()
    }

    /**
     * Tipp auf unsere Leiste. Zuerst klicken wir den echten Knopf über die Bedienungshilfen und prüfen danach,
     * ob es geklappt hat; nur sonst ein künstlicher Finger-Tipp. Der Finger-Tipp allein war unzuverlässig:
     * Ist das Handy beschäftigt, landet er auf unserer eigenen Leiste statt auf der echten (Protokoll: tipps.txt).
     */
    private fun onTabTapped(kind: TabKind) {
        tapSeq++
        val root = youTubeMainRoot() ?: run {
            tapLog("$kind: kein YouTube-Fenster gefunden")
            return
        }
        if (kind == TabKind.CREATE) {
            // Plus öffnet die Kamera. Die erscheint erst ~300 ms später; bis dahin bleibt unsere Leiste stehen,
            // sonst sieht man so lange die echte mit Shorts-Knopf. Sie verschwindet, sobald die echte Leiste
            // aus YouTube verschwindet (siehe updateBar). Dafür ab jetzt jedes Bild prüfen.
            fastUntil = SystemClock.uptimeMillis() + FAST_WINDOW_MS
            startPolling()
        }
        // Auch bei weggescrollter Leiste: ihre Knöpfe neu einlesen und über die Bedienungshilfen klicken.
        val fresh = scanner.findBar(root, screenBounds())
        val bar = fresh ?: lastBar?.let { scanner.rebuildBar(it.container) }
        val node = bar?.nodes?.get(kind)
        if (node == null && fresh == null) {
            // YouTube hat seine Leiste beim Scrollen ganz entfernt: Liste ein Stück hochscrollen, dann kommt
            // sie zurück, und wir klicken den echten Knopf.
            revealBarThenClick(kind, root)
            return
        }
        if (node == null) {
            tapLog("$kind: Tab nicht gefunden (Leiste da)")
            dumper.dump(root, windows, "tab-$kind-nicht-gefunden")
            tapThroughIfVisible(kind)
            return
        }
        val clicked = click(node)
        tapLog("$kind: Klick ${if (clicked) "ok" else "fehlgeschlagen"}")
        if (!clicked) {
            tapThroughIfVisible(kind)
        } else if (kind != TabKind.CREATE) {
            verifyLater(kind)
        }
        scheduleScan()
    }

    /** Prüft nach einer Weile, ob der Tab gewählt ist – aber nur, wenn du seither nicht schon weitergetippt hast. */
    private fun verifyLater(kind: TabKind) {
        val seq = tapSeq
        handler.postDelayed({ if (seq == tapSeq) verifyTab(kind) }, VERIFY_MS)
    }

    private fun revealBarThenClick(kind: TabKind, root: AccessibilityNodeInfo) {
        val feed = goneFeed ?: scanner.findFeed(root, screen)
        val scrolled = feed?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        tapLog("$kind: echte Leiste weg, scrolle hoch (${if (scrolled) "ok" else "fehlgeschlagen"})")
        if (!scrolled) swipeFeedDown()
        waitForBarThenClick(kind, tapSeq, 0, swiped = !scrolled)
    }

    /** Wartet, bis die echte Leiste wieder da ist, und klickt dann ihren Knopf. */
    private fun waitForBarThenClick(kind: TabKind, seq: Int, attempt: Int, swiped: Boolean) {
        handler.postDelayed({
            if (seq != tapSeq) return@postDelayed
            val root = youTubeMainRoot() ?: return@postDelayed
            val node = scanner.findBar(root, screenBounds())?.nodes?.get(kind)
            when {
                node != null -> {
                    val clicked = click(node)
                    tapLog("$kind: Leiste wieder da, Klick ${if (clicked) "ok" else "fehlgeschlagen"}")
                    if (clicked && kind != TabKind.CREATE) verifyLater(kind)
                    scheduleScan()
                }
                // Hochscrollen über die Bedienungshilfen hat die Leiste nicht zurückgebracht: echt wischen.
                attempt == REVEAL_TRIES / 2 && !swiped -> {
                    swipeFeedDown()
                    waitForBarThenClick(kind, seq, attempt + 1, swiped = true)
                }
                attempt < REVEAL_TRIES -> waitForBarThenClick(kind, seq, attempt + 1, swiped)
                else -> tapLog("$kind: echte Leiste kam nicht zurück")
            }
        }, REVEAL_POLL_MS)
    }

    /** Künstliches Wischen nach unten mitten im Bild (= Liste hochscrollen), weit weg von unserer Leiste. */
    private fun swipeFeedDown() {
        val x = screen.exactCenterX()
        val path = Path().apply {
            moveTo(x, screen.height() * 0.35f)
            lineTo(x, screen.height() * 0.65f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 120))
            .build()
        dispatchGesture(gesture, null, handler)
    }

    private fun isAtRest(bar: FoundBar): Boolean {
        val rest = restBounds ?: return false
        return kotlin.math.abs(bar.bounds.top - rest.top) <= 2 && kotlin.math.abs(bar.bounds.bottom - rest.bottom) <= 2
    }

    private fun verifyTab(kind: TabKind) {
        val root = youTubeMainRoot() ?: return
        val bar = scanner.findBar(root, screen) ?: return
        if (bar.tabs[kind]?.selected == true) {
            tapLog("$kind: ausgewählt")
            return
        }
        tapLog("$kind: nach Klick nicht ausgewählt, versuche Finger-Tipp")
        tapThroughIfVisible(kind)
    }

    /** Finger-Tipp auf den echten Tab, aber nur wenn die echte Leiste ganz da ist (sonst träfe er ein Video). */
    private fun tapThroughIfVisible(kind: TabKind) {
        val root = youTubeMainRoot() ?: return
        val bar = scanner.findBar(root, screen)
        val bounds = bar?.tabs?.get(kind)?.bounds
        if (bar == null || bounds == null || !isAtRest(bar)) {
            tapLog("$kind: echte Leiste nicht ganz sichtbar, kein Finger-Tipp")
            return
        }
        tapThrough(bounds)
    }

    private fun tapLog(line: String) {
        Log.i(TAG, "Tipp: $line")
        runCatching {
            val file = java.io.File(TreeDumper.dumpDir(this).apply { mkdirs() }, "tipps.txt")
            if (file.length() > 20_000) file.delete()
            file.appendText("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())} $line\n")
        }
    }

    private fun onBarLongPressed() {
        val root = youTubeMainRoot() ?: return
        val file = dumper.dump(root, windows, "manuell")
        val text = if (file != null) "Struktur gespeichert" else "Struktur konnte nicht gespeichert werden"
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    /** Klick über die Bedienungshilfen: am Tab selbst, an einem Kind oder an einem Eltern-Element. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        var parent = node.parent
        repeat(3) {
            val p = parent ?: return false
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = p.parent
        }
        return false
    }

    /** Fallback: echter Finger-Tipp auf die Stelle. Unsere Leiste lässt den Tipp dafür kurz durch. */
    private fun tapThrough(bounds: Rect) {
        overlay.setPassThrough(true)
        handler.postDelayed({
            val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            val done = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) = release()
                override fun onCancelled(gestureDescription: GestureDescription) = release()
                private fun release() {
                    handler.postDelayed({ overlay.setPassThrough(false) }, 80)
                }
            }
            if (!dispatchGesture(gesture, done, handler)) overlay.setPassThrough(false)
        }, PASS_THROUGH_DELAY_MS)
    }

    /**
     * Das Fenster, das gerade vorne ist. Unsere eigenen Fenster zählen nicht: Tippt man auf unsere Leiste,
     * hält Android sonst unsere Leiste für das aktive Fenster.
     */
    private fun foregroundWindow(): AccessibilityWindowInfo? {
        val all = windows
        val active = all.firstOrNull { it.isActive }
        if (active != null && !isOurs(active)) return active
        return all.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !isOurs(it) }.maxByOrNull { it.layer }
    }

    private fun isOurs(window: AccessibilityWindowInfo): Boolean =
        window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
            window.root?.packageName?.toString() == packageName

    /** Das Hauptfenster von YouTube (das grösste). Dort liegen Leiste und Startseite, auch wenn ein Menü offen ist. */
    private fun youTubeMainRoot(): AccessibilityNodeInfo? {
        val r = Rect()
        return windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { w -> w.root?.takeIf { it.packageName?.toString() == selectors.youtubePackage }?.let { w to it } }
            .maxByOrNull { (w, _) -> w.getBoundsInScreen(r); r.width().toLong() * r.height() }
            ?.second
    }

    private fun isKeyboardOpen(): Boolean =
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    private fun screenBounds(): Rect {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    companion object {
        private const val TAG = "FuckShorts"
        private const val MIN_SCAN_INTERVAL_MS = 40L
        private const val POLL_MS = 16L
        private const val POLL_DURATION_MS = 500L
        private const val NAV_LINK_MS = 1_500L
        private const val VERIFY_MS = 400L
        private const val FAST_WINDOW_MS = 1_500L
        private const val MINI_CHECK_MS = 400L
        private const val MINI_WALK_MS = 5_000L
        private const val DRAW_FIRST_MS = 50L
        private const val SCROLL_GONE_MS = 1_500L
        private const val FOREGROUND_GAP_MS = 500L
        private const val SLOW_SCAN_MS = 150L
        private const val REVEAL_POLL_MS = 50L
        private const val REVEAL_TRIES = 20
        // Android braucht ein paar Bilder, bis unsere Leiste Berührungen durchlässt.
        private const val PASS_THROUGH_DELAY_MS = 80L
        private const val ICON_CENTER_DP = 20f
        private const val LABEL_TOP_DP = 32.5f
        private const val REST_STABLE_MS = 300L
        private const val WATCHDOG_MS = 400L
        private const val BACK_COOLDOWN_MS = 700L
        private const val HINT_WINDOW_MS = 10_000L
        private const val AUTO_DUMP_AFTER_MS = 2_000L
        private const val AUTO_DUMP_INTERVAL_MS = 20_000L
        private const val DEBUG_DUMP_INTERVAL_MS = 3_000L
        private const val SHELF_HIDE_INTERVAL_MS = 60_000L
        private const val SHELF_MENU_TIMEOUT_MS = 1_500L

        @Volatile
        var running = false
            private set
    }
}
