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
    private var miniPlayerOpen = false
    private var lastMiniCheckAt = 0L
    private var lastNavAt = 0L
    private var restSince = 0L
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
            if (!connected || !youTubeActive || SystemClock.uptimeMillis() > pollUntil) {
                polling = false
                return
            }
            try {
                updateBar(SystemClock.uptimeMillis(), null)
                updateShelfPositions()
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
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> onNavigation()
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
        val delay = (lastScanAt + MIN_SCAN_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(scanRunnable, delay)
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
        try {
            scanInternal()
        } catch (e: Exception) {
            // Lieber nichts anzeigen als YouTube blockieren.
            Log.w(TAG, "Scan fehlgeschlagen", e)
            hideAll()
        }
        if (overlay.isShowing || covers.anyVisible) handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun hideAll() {
        overlay.hide()
        covers.hideAll()
    }

    private fun scanInternal() {
        val now = SystemClock.uptimeMillis()
        if (shelfMenuOpenedAt != 0L) {
            handleShelfMenu(now)
            return
        }
        val foreground = foregroundWindow()?.root
        if (foreground == null || foreground.packageName?.toString() != selectors.youtubePackage || isKeyboardOpen()) {
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
            if (rest != null && state != null) overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        }
        youTubeActive = true
        if (youTubeSince == 0L) youTubeSince = now
        val newScreen = screenBounds()
        if (newScreen != screen) {
            screen = newScreen
            restBounds = null
        }
        val bar = scanner.findBar(root, screen)
        watchPageOpen = scanner.isWatchPageOpen(root, screen)
        if (now - lastMiniCheckAt > MINI_CHECK_MS) {
            lastMiniCheckAt = now
            miniPlayerOpen = scanner.isMiniPlayerVisible(root, screen)
        }

        if (debugDumpWanted) {
            debugDumpWanted = false
            if (Prefs.debugDumps(this) && now - lastDebugDumpAt > DEBUG_DUMP_INTERVAL_MS) {
                lastDebugDumpAt = now
                dumper.dump(root, windows, "bildschirmwechsel")
            }
        }

        if (scanner.isShortsScreen(root, bar, screen)) {
            hideAll()
            leaveShorts()
            return
        }

        if (bar != null) {
            barSeenOnce = true
            lastBar = bar
            rememberRestPosition(bar)
            if (scanner.isBarCovered(root, bar)) {
                hideAll()
                return
            }
            // Icons und Profilbild von der echten Leiste abschauen, sobald sie ganz still steht.
            val atRest = bar.bounds == restBounds
            if (!atRest) restSince = 0L else if (restSince == 0L) restSince = now
            if (atRest && sampler.needsCapture(bar)) {
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
            // Positionen nur von der ganz eingefahrenen Leiste übernehmen, sonst nur Auswahl und roten Punkt.
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
            hideAll()
            val stuck = !barSeenOnce && now - youTubeSince > AUTO_DUMP_AFTER_MS
            if (stuck && now - lastAutoDumpAt > AUTO_DUMP_INTERVAL_MS) {
                lastAutoDumpAt = now
                dumper.dump(root, windows, "leiste-nicht-gefunden")
            }
            return
        }

        updateBar(now, bar)

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
        val observedTop = when {
            fresh != null -> fresh.bounds.top
            else -> lastBar?.let { scanner.liveTop(it.container, screen) }
        }
        if (watchPageOpen) {
            overlay.hide()
            return
        }
        // Grundregel: Unsere Leiste bleibt stehen, egal was die echte macht (Scrollen, Menüs ...).
        // Ausnahme: Kurz nach einem Tipp fährt die ganz sichtbare echte Leiste raus (Video geöffnet): dann mit.
        // Mit Mini-Player fahren wir immer mit der echten Leiste mit: YouTube setzt den Mini-Player direkt
        // über seine Leiste. Bliebe unsere stehen, läge der Mini-Player darunter.
        val followAfterTap = (now - lastNavAt < NAV_LINK_MS && navFromRest) || miniPlayerOpen
        if (observedTop != null && observedTop <= rest.top + 2) {
            hiddenByScroll = false
        } else if (!followAfterTap && overlay.isShowing) {
            hiddenByScroll = true
        } else if (miniPlayerOpen) {
            hiddenByScroll = false
        }
        if (observedTop == null && !hiddenByScroll) {
            // Die Leiste gibt es nicht mehr (anderer Bildschirm).
            lastBar = null
            overlay.hide()
            return
        }
        // Fährt die echte Leiste gerade rein (z. B. nach dem Verlassen eines Videos), springt unsere sofort
        // an ihren Platz. So liegt unsere immer über der echten und die echte ist nie zu sehen.
        val visibleNow = observedTop != null && observedTop < screen.bottom - 2
        val slidingIn = visibleNow && (!overlay.isShowing || (lastObservedTop >= 0 && observedTop!! < lastObservedTop - 1))
        lastObservedTop = observedTop ?: -1
        val top = if (hiddenByScroll || observedTop == null || slidingIn) rest.top else observedTop.coerceAtLeast(rest.top)
        if (top >= screen.bottom - 2) {
            overlay.hide()
        } else {
            overlay.show(top, rest, state.copy(style = currentStyle()))
        }
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

    private fun onTabTapped(kind: TabKind) {
        val root = youTubeMainRoot() ?: run {
            tapLog("$kind: kein YouTube-Fenster gefunden")
            return
        }
        // Ist die echte Leiste gerade weggescrollt, nehmen wir die zuletzt gefundene: Klicks gehen trotzdem.
        val fresh = scanner.findBar(root, screenBounds())
        val bar = fresh ?: lastBar
        val node = bar?.nodes?.get(kind)
        if (node == null) {
            tapLog("$kind: Tab nicht gefunden (Leiste ${if (fresh == null) "weg" else "da"})")
            dumper.dump(root, windows, "tab-$kind-nicht-gefunden")
            return
        }
        node.refresh()
        val clicked = click(node)
        tapLog("$kind: Klick ${if (clicked) "ok" else "fehlgeschlagen"}")
        if (!clicked) {
            tapThroughIfVisible(kind)
        } else if (kind != TabKind.CREATE) {
            // Nachprüfen: Ist der Tab jetzt wirklich ausgewählt? Sonst echter Finger-Tipp.
            handler.postDelayed({ verifyTab(kind) }, VERIFY_MS)
        }
        scheduleScan()
    }

    private fun verifyTab(kind: TabKind) {
        val root = youTubeMainRoot() ?: return
        val bar = scanner.findBar(root, screen) ?: return
        if (bar.tabs[kind]?.selected == true) return
        tapLog("$kind: nach Klick nicht ausgewählt, versuche Finger-Tipp")
        tapThroughIfVisible(kind)
    }

    /** Finger-Tipp auf den echten Tab, aber nur wenn die echte Leiste ganz da ist (sonst träfe er ein Video). */
    private fun tapThroughIfVisible(kind: TabKind) {
        val root = youTubeMainRoot() ?: return
        val bar = scanner.findBar(root, screen)
        val bounds = bar?.tabs?.get(kind)?.bounds
        if (bar == null || bounds == null || bar.bounds != restBounds) {
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
        }, 40)
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
        private const val MINI_CHECK_MS = 400L
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
