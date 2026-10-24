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
    private lateinit var dumper: TreeDumper
    private lateinit var sampler: BarStyleSampler

    private var connected = false
    private var scanPending = false
    private var lastScanAt = 0L
    private var lastBackAt = 0L
    private val recentBacks = ArrayDeque<Long>()
    private var lastHintAt = 0L
    private var youTubeSince = 0L
    private var barSeenOnce = false
    private var lastAutoDumpAt = 0L
    private var lastDebugDumpAt = 0L
    private var debugDumpWanted = false
    private var nightMode = 0

    private val scanRunnable = Runnable {
        scanPending = false
        lastScanAt = SystemClock.uptimeMillis()
        scan()
    }

    // Sicherheitsnetz: Solange unsere Leiste sichtbar ist, prüfen wir regelmässig, ob YouTube noch vorne ist.
    private val watchdog = Runnable { scheduleScan() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        selectors = Selectors.load(this)
        scanner = YouTubeScanner(selectors, resources.displayMetrics.density)
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
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) debugDumpWanted = true
                scheduleScan()
            }
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED -> scheduleScan()
            overlay.isShowing -> scheduleScan()
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
        overlay.hide()
        scheduleScan()
    }

    override fun onDestroy() {
        running = false
        connected = false
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.destroy()
        super.onDestroy()
    }

    private fun scheduleScan() {
        if (scanPending) return
        scanPending = true
        val delay = (lastScanAt + MIN_SCAN_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(scanRunnable, delay)
    }

    private fun scan() {
        handler.removeCallbacks(watchdog)
        try {
            scanInternal()
        } catch (e: Exception) {
            // Lieber nichts anzeigen als YouTube blockieren.
            Log.w(TAG, "Scan fehlgeschlagen", e)
            overlay.hide()
        }
        if (overlay.isShowing) handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun scanInternal() {
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != selectors.youtubePackage || isKeyboardOpen()) {
            youTubeSince = 0L
            overlay.hide()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (youTubeSince == 0L) youTubeSince = now
        val screen = screenBounds()
        val bar = scanner.findBar(root, screen)

        if (debugDumpWanted) {
            debugDumpWanted = false
            if (Prefs.debugDumps(this) && now - lastDebugDumpAt > DEBUG_DUMP_INTERVAL_MS) {
                lastDebugDumpAt = now
                dumper.dump(root, windows, "bildschirmwechsel")
            }
        }

        if (scanner.isShortsScreen(root, bar, screen)) {
            overlay.hide()
            leaveShorts()
            return
        }

        if (bar == null) {
            overlay.hide()
            val stuck = !barSeenOnce && now - youTubeSince > AUTO_DUMP_AFTER_MS
            if (stuck && now - lastAutoDumpAt > AUTO_DUMP_INTERVAL_MS) {
                lastAutoDumpAt = now
                dumper.dump(root, windows, "leiste-nicht-gefunden")
            }
            return
        }
        barSeenOnce = true

        if (bar.bounds.top >= screen.bottom - 4 || scanner.isBarCovered(root, bar)) {
            overlay.hide()
            return
        }

        // Beim allerersten Mal Farbe und Profilbild von der echten Leiste abschauen.
        if (sampler.style == null && !sampler.attempted) {
            overlay.hide()
            sampler.sample(bar) { scheduleScan() }
            return
        }

        val style = sampler.style ?: BarStyle.default(nightMode == Configuration.UI_MODE_NIGHT_YES)
        val state = BarUiState(
            selected = bar.tabs.values.firstOrNull { it.selected }?.kind,
            labels = bar.tabs.mapNotNull { (kind, tab) -> tab.label?.let { kind to it } }.toMap(),
            subscriptionsBadge = bar.tabs[TabKind.SUBSCRIPTIONS]?.badge == true,
            hasCreate = TabKind.CREATE in bar.tabs,
            style = style,
        )
        overlay.show(bar.bounds, state)
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
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != selectors.youtubePackage) return
        val bar = scanner.findBar(root, screenBounds())
        val node = bar?.nodes?.get(kind)
        val bounds = bar?.tabs?.get(kind)?.bounds
        if (node == null || bounds == null) {
            Log.w(TAG, "Tab $kind nicht gefunden")
            dumper.dump(root, windows, "tab-$kind-nicht-gefunden")
            return
        }
        if (!click(node)) tapThrough(bounds)
        scheduleScan()
    }

    private fun onBarLongPressed() {
        val root = rootInActiveWindow ?: return
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
        private const val MIN_SCAN_INTERVAL_MS = 16L
        private const val WATCHDOG_MS = 400L
        private const val BACK_COOLDOWN_MS = 700L
        private const val HINT_WINDOW_MS = 10_000L
        private const val AUTO_DUMP_AFTER_MS = 2_000L
        private const val AUTO_DUMP_INTERVAL_MS = 20_000L
        private const val DEBUG_DUMP_INTERVAL_MS = 3_000L

        @Volatile
        var running = false
            private set
    }
}
