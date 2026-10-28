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
    private lateinit var dumper: TreeDumper
    private lateinit var sampler: BarStyleSampler
    private lateinit var touchWatcher: TouchWatcher

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
    private var feedKeptAt = 0L
    private var lastTouchAt = 0L

    /** Tablet (ab 600 dp): Ein paar Abläufe sind dort anders als auf dem Handy, z. B. der schwebende Mini-Player. */
    private val isTablet get() = resources.configuration.smallestScreenWidthDp >= 600
    private var createTappedAt = 0L
    /** Die offene Tastatur hatte ein Eingabefeld mit Fokus (siehe isKeyboardOpen). */
    private var imeFocusSeen = false
    /** Höchste Lage des Tastatur-Fensters, seit es da ist. Rutscht es tiefer, geht sie zu. */
    private var imeMinTop = Int.MAX_VALUE
    /** Die Tastatur hat YouTube verdeckt: Danach nicht blind an der alten Stelle zeigen (Seite vielleicht ohne Leiste). */
    private var keyboardWasOpen = false
    /** Die Tastatur ging zu, weil der Fokus auf eine neue Seite sprang ("Suchen" → Ergebnisse mit Leiste). */
    private var keyboardToPage = false
    /**
     * Tastatur mit Zurück geschlossen, darunter die Suchvorschläge: Die echte Leiste liegt dort ganz unten raus.
     * Unsere bleibt weg, bis die echte wieder hereinkommt (die Startseiten-Regel "immer da" gilt hier nicht).
     */
    private var barGoneAfterKeyboard = false
    /** Fenster, in dem die echte Leiste zuletzt gefunden wurde. */
    private var barWindowId = -1
    /** Warum die Liste zuletzt nicht als "dieselbe Seite" galt (nur fürs Protokoll). */
    private var keepFail = ""
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
    private var savedBarState: String? = null
    /** Lage der Leiste kommt aus dem Speicher (Neustart), die echte wurde seither noch nicht gesehen. */
    private var restoredState = false
    /** Lage und Aussehen der ruhenden Leiste je Bildschirmgrösse (Hoch-/Querformat), fürs Drehen. */
    private val restBySize = HashMap<String, Pair<Rect, BarUiState>>()
    /** Bis dann nach dem Drehen nur die neue Lage des Players lernen, nicht "Video verlassen" melden. */
    private var watchRelearnUntil = 0L
    /** YouTube im Splitscreen (oben oder unten, volle Breite): sein Fenster. Sonst null (ganzer Bildschirm). */
    private var splitBounds: Rect? = null
    /** Die weggescrollte Liste, aus der ein Video geöffnet wurde (unsere Leiste stand dort). */
    private var feedBeforeVideo: AccessibilityNodeInfo? = null
    /** Unsere Leiste wurde eben an eine neue Stelle gesetzt (Drehen): erst zeichnen lassen, dann scannen. */
    private var movedAt = 0L
    /** Zuletzt war unsere Leiste zu sehen (Dialog: nur dann abgedunkelt zeigen). */
    private var ourShownAt = 0L
    /** Unsere Leiste ist gerade wie hinter einem Dialog abgedunkelt. */
    private var dialogDimmed = false
    /** Unsere ist unter einem Dialog weggefahren, der die ganze Leiste abdeckt (siehe slideDown). */
    private var dialogSlid = false
    /** Nach einer Berührung im Dialog nicht nochmals wegfahren, bis er zu ist. */
    private var dialogNoSlide = false
    /** Seit wann der Player die echte Leiste überdeckt (0 = nicht). */
    private var coveredSince = 0L
    private var themeChangedAt = 0L
    /** Benachrichtigungen wurden über YouTube aufgezogen (siehe handleShade). */
    private var shadeOverYouTube = false
    private var shadeMaxTop = -1
    private var shadeHadKids = false
    private var shadeClosingAt = 0L
    private var shadeOpenedAt = 0L
    /** Letzte Berührung, während die Benachrichtigungen über YouTube offen waren. */
    private var shadeTouchAt = 0L
    /** Farbe des Benachrichtigungs-Schleiers über der Leiste, gemessen (siehe measureShadeVeil). */
    private var shadeVeil: Int? = null
    /** Gemessene Schleierfarbe je hell/dunkel (bleibt beim Wechseln erhalten). */
    private val shadeVeils = HashMap<Int, Int>()
    private var veilMeasuring = false

    // Short per Link (z. B. aus WhatsApp): Genau dieser eine darf laufen, weiterwischen nicht.
    // Erkennung: YouTube kommt aus einer anderen App nach vorne und der Short geht auf, ohne dass in YouTube
    // getippt wurde. Die Tastatur (Suche) zählt nicht als andere App.
    private var leftYouTube = true
    private var enteredYouTubeAt = 0L
    private var touchedSinceEnter = false
    private var linkShortActive = false
    private var linkShort: String? = null
    private var notShortsSince = 0L
    private var lastLinkFixAt = 0L
    private var lastFingerprint: String? = null
    private var menuOpen = false
    private var menuFront = false
    private var menuWindowId = -1
    private var menuMinTop = Int.MAX_VALUE
    /** Wie stark YouTube hinter einem Menü abdunkelt (0..1), einmal gemessen. null = noch unbekannt. */
    private var menuDim: Float? = null
    private var dimMeasuring = false
    private val menuWindows = HashMap<Int, Boolean>()
    /** YouTube-Fenster, in dem es keine Leiste gab (z. B. Kamera). -1 = keins. */
    private var barlessWindowId = -1
    /** YouTube ging nach hinten, während eine Seite ohne Leiste offen war (nur Tablet, siehe scanInternal). */
    private var leftWithoutBar = false
    /** Anderes YouTube-Fenster (z. B. Premium-Werbung) über der Seite mit der Leiste. -1 = keins. */
    private var coverWindowId = -1
    /** Höchste Lage eines offenen Panels im YouTube-Fenster (z. B. Werbe-Menü). MAX_VALUE = keins offen. */
    private var panelMinTop = Int.MAX_VALUE
    private var linkShortAt = 0L
    /** Unsere Leiste wurde vor dem Schliessen der Kamera schon gezeigt: kurz nicht wieder verstecken. */
    private var preShownAt = 0L
    private var lastFingerprintAt = 0L
    private var linkFixTries = 0

    // Shorts-Regale und automatisches "Nicht interessiert".
    private var shelves: List<Shelf> = emptyList()
    private var shelfMenuOpenedAt = 0L
    private var lastShelfHideAt = 0L
    private var shelfHideBroken = false

    private val scanRunnable = Runnable {
        scanPending = false
        // Unsere fährt gerade unter einen Dialog weg: erst fertig animieren lassen (ein Scan liesse sie ruckeln).
        if (overlay.isSliding) {
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return@Runnable
        }
        // Tablet: Ein Scan dauert dort bis 1 s und blockiert das Zeichnen. War er schon eingeplant, als unsere
        // Leiste eben gezeigt wurde, sah man so lange die echte (z. B. Video runtergewischt, zurück zu YouTube).
        // Erst warten, bis Android sie wirklich gezeichnet hat (höchstens TABLET_DRAW_WAIT_MS), dann noch kurz.
        if (isTablet) {
            val now = SystemClock.uptimeMillis()
            val shown = maxOf(overlay.shownAt, movedAt)
            val drawn = overlay.drawnAt > shown
            val wait = if (drawn) overlay.drawnAt + DRAW_FIRST_MS - now else shown + TABLET_DRAW_WAIT_MS - now
            if (overlay.isShowing && wait > 0) {
                handler.postDelayed({ scheduleScan() }, if (drawn) wait else minOf(wait, POLL_MS))
                return@Runnable
            }
        }
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
        scanner = YouTubeScanner(selectors, resources.displayMetrics.density, isTablet)
        overlay = BottomBarOverlay(this, ::onTabTapped, ::onBarLongPressed)
        dumper = TreeDumper(this)
        sampler = BarStyleSampler(this)
        touchWatcher = TouchWatcher(this) {
            if (!touchedSinceEnter && youTubeActive) Log.d(TAG, "Berührung in YouTube")
            touchedSinceEnter = true
            lastTouchAt = SystemClock.uptimeMillis()
            if (shadeOverYouTube) onShadeTouched()
            // Tablet: Beim Runterwischen des Videos meldet YouTube nichts, bis der Mini-Player steht (~1 s echte
            // Leiste). Nach jeder Berührung darum Bild für Bild nachsehen, ob der Player wegrutscht.
            if (isTablet && watchPageOpen && youTubeActive) startPolling()
            // Unter einem Dialog weggefahren: Jede Berührung kann ihn schliessen (Abbrechen, daneben, Zurück-Geste).
            // Das merkt der Scan erst ~0,3 s später, so lange sähe man die echte. Darum sofort abgedunkelt zurück.
            if (dialogSlid) bringBackFromSlide()
        }.also { it.start() }
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        menuDim = Prefs.menuDim(this)
        loadBarState()
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
                        // Tastatur offen und YouTube wechselt die Seite ("Suchen"): sofort nachsehen, nicht erst
                        // beim nächsten Durchgang. Die echte Leiste kommt sonst 1–2 Bilder vor unserer hervor.
                        if (keyboardWasOpen && !youTubeActive) {
                            handler.removeCallbacks(scanRunnable)
                            scanPending = false
                            lastScanAt = SystemClock.uptimeMillis()
                            scan()
                        }
                        // Seitenwechsel nach Zurück von den Suchvorschlägen: Die Seite darunter (Ergebnisse, Startseite)
                        // hat eine Leiste und fährt mit ihr herein. Unsere sofort zeigen, die Suche danach ist zu langsam.
                        if (barGoneAfterKeyboard && youTubeActive) {
                            barGoneAfterKeyboard = false
                            val rest = restBounds
                            val state = lastUiState
                            if (rest != null && state != null && !watchPageOpen && !overlay.isShowing) {
                                reason("Seitenwechsel nach Tastatur, unsere vorab zeigen")
                                preShownAt = SystemClock.uptimeMillis()
                                hiddenByScroll = false
                                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                            }
                        }
                        if (watchPageOpen && youTubeActive &&
                            event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED != 0
                        ) onWatchPaneAppeared()
                        onNavigation()
                    }
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        if (closesBarlessPage(event)) showBeforeBarlessCloses()
                        touchedSinceEnter = true
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
            overlay.isShowing || shelfMenuOpenedAt != 0L -> scheduleScan()
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
            shadeVeil = shadeVeils[night]
            themeChangedAt = SystemClock.uptimeMillis()
        }
        // Nur hell/dunkel gewechselt (gleiche Bildschirmgrösse): YouTube baut sich neu auf und blendet über.
        // Unsere Leiste bleibt dabei stehen und nimmt nur die neuen Farben, sonst sah man 6–15 Bilder die echte.
        val rest = restBounds
        val state = lastUiState
        if (screenBounds() == screen && rest != null && state != null) {
            if (overlay.isShowing) overlay.show(overlay.top, rest, state.copy(style = currentStyle()))
            scheduleScan()
            return
        }
        onScreenSizeChanged(screenBounds())
        scheduleScan()
    }

    /**
     * Gedreht (gemerkt beim Scan oder von Android, was zuerst kommt). Kennen wir die Lage der Leiste in der neuen
     * Ausrichtung schon, springt unsere sofort dorthin. Versteckt man sie bis zur neuen Suche, zeigt die
     * Dreh-Animation 4–5 Bilder die echte Leiste.
     */
    private fun onScreenSizeChanged(newScreen: Rect) {
        if (newScreen == screen) return
        val state = lastUiState
        // Auch wenn gerade kein Video als offen gilt: Der volle Scan findet es vielleicht gleich an einer
        // Zwischen-Lage der Dreh-Animation.
        watchRelearnUntil = SystemClock.uptimeMillis() + WATCH_RELEARN_MS
        val known = restBySize[sizeKey(newScreen)]
        val wasShowing = overlay.isShowing
        screen = newScreen
        if (known != null && state != null) {
            restBounds = Rect(known.first)
            lastUiState = known.second.copy(
                selected = state.selected,
                subscriptionsBadge = state.subscriptionsBadge,
                style = currentStyle(),
            )
            lastObservedTop = -1
            goneFeed = null
            if (wasShowing && !watchPageOpen) {
                reason("gedreht, unsere an bekannter Stelle")
                movedAt = SystemClock.uptimeMillis()
                preShownAt = SystemClock.uptimeMillis()
                hiddenByScroll = false
                overlay.show(known.first.top, known.first, lastUiState!!)
                return
            }
            if (isTablet) overlay.prepare(known.first, lastUiState!!)
        } else {
            restBounds = null
        }
        hideAll()
    }

    private fun bringBackFromSlide() {
        dialogSlid = false
        dialogNoSlide = true
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        val popup = popupFront(foregroundWindow()?.root)
        if (popup != null) {
            reason("Berührung im Dialog, unsere abgedunkelt zurück")
            overlay.showUnderMenu(rest, state.copy(style = currentStyle()), DIALOG_DIM, popup)
        } else {
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        }
    }

    private fun sizeKey(r: Rect) = "${r.left},${r.top},${r.width()}x${r.height()}"

    /**
     * Beim Drehen meldet YouTube den Player schon in der neuen Ausrichtung, Android aber noch die alte
     * Bildschirmgrösse: Ist der Player genau so breit wie der Bildschirm *nach* dem Drehen, wird gerade gedreht.
     * (Der Mini-Player beim Verlassen ist viel schmaler.)
     */
    private fun playerRotating(node: AccessibilityNodeInfo): Boolean {
        if (!node.refresh()) return false
        val w = Rect().also { node.getBoundsInScreen(it) }.width()
        return kotlin.math.abs(w - screen.width()) > 4 && kotlin.math.abs(w - screen.height()) <= 4
    }

    /**
     * Ist das Video noch offen? Nach dem Drehen (Vollbild quer ↔ hoch) steht der Player woanders, das ist kein
     * Verlassen: eine Weile nur seine neue Lage lernen (sonst blitzte unsere Leiste über der Video-Seite auf).
     */
    private fun watchStillOpen(node: AccessibilityNodeInfo, now: Long): Boolean? {
        if (isTablet && !node.refresh()) {
            // Tablet: Beim Drehen baut YouTube den Player neu (quer zwei Spalten). Neu suchen statt "verlassen",
            // sonst stand unsere ~0,3 s über dem Video. Nach Zurück ist keiner mehr da.
            val again = youTubeMainRoot()?.let { scanner.findWatchPlayer(it, screen) } ?: return false
            watchNode = again
            watchTop = scanner.topOf(again)
            return true
        }
        if (playerRotating(node)) watchRelearnUntil = now + WATCH_RELEARN_MS
        if (now < watchRelearnUntil) {
            // null = YouTube hat den Player neu gebaut: der volle Scan sucht ihn neu.
            if (!node.refresh()) return null
            val top = scanner.topOf(node)
            // Während der Dreh-Animation meldet YouTube unsinnige Lagen (z. B. -740): nur sinnvolle lernen.
            if (top >= 0 && top < screen.height() * 0.35f) watchTop = top
            return true
        }
        return scanner.isStillLargePlayer(node, watchTop, screen)
    }

    override fun onDestroy() {
        running = false
        connected = false
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.destroy()
        if (::touchWatcher.isInitialized) touchWatcher.stop()
        super.onDestroy()
    }

    private fun scheduleScan() {
        if (scanPending) return
        scanPending = true
        val now = SystemClock.uptimeMillis()
        val interval = if (now < fastUntil) POLL_MS else MIN_SCAN_INTERVAL_MS
        // Unsere Leiste ist eben erst sichtbar geworden: erst zeichnen lassen. Ein Scan blockiert sonst bis 500 ms
        // (Video verlassen: so lange sah man die echte Leiste).
        val drawFirst = maxOf(overlay.shownAt, movedAt) + DRAW_FIRST_MS - now
        val delay = maxOf(lastScanAt + interval - now, drawFirst, 0)
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

    /**
     * Video verlassen: YouTube meldet PANE_APPEARED ~65 ms bevor die Lage-Abfragen wieder antworten, der Player
     * steht dann erst ein paar Pixel tiefer (unter der 4-dp-Schwelle). Ist er schon tiefer, sofort zeigen –
     * sonst sah man 1 Bild die Oberkante der echten Leiste. Kommentare/Beschreibung bewegen den Player nicht.
     */
    private fun onWatchPaneAppeared() {
        val node = watchNode ?: return
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        if (overlay.isShowing || SystemClock.uptimeMillis() < watchRelearnUntil || !node.refresh()) return
        if (playerRotating(node)) {
            watchRelearnUntil = SystemClock.uptimeMillis() + WATCH_RELEARN_MS
            return
        }
        val top = scanner.topOf(node)
        if (top <= watchTop + 1) return
        watchPageOpen = false
        watchNode = null
        reason("Video verlassen (Pane, top $watchTop→$top)")
        preShownAt = SystemClock.uptimeMillis()
        hiddenByScroll = false
        overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        continueScanSoon()
    }

    /** Tipp auf "Schliessen" in einem YouTube-Fenster ohne Leiste (z. B. Kamera nach Plus)? */
    private fun closesBarlessPage(event: AccessibilityEvent): Boolean {
        if (event.windowId == -1 || (event.windowId != barlessWindowId && event.windowId != coverWindowId)) return false
        val label = (event.contentDescription ?: event.text.firstOrNull())?.toString()?.trim()?.lowercase() ?: return false
        // YouTube 21.42: "Kamera schließen"
        return CLOSE_LABELS.any { label == it || label.startsWith("$it ") || label.endsWith(" $it") }
    }

    /**
     * Die Kamera geht gleich zu. Android meldet die Seite darunter erst, wenn sie schon zu sehen ist (2 Bilder mit
     * der echten Leiste). Darum unsere schon jetzt zeigen. Geht die Kamera doch nicht zu, versteckt der nächste
     * Scan sie wieder.
     */
    private fun showBeforeBarlessCloses() {
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        if (watchPageOpen || overlay.isShowing) return
        reason("Kamera wird geschlossen, unsere vorab zeigen")
        preShownAt = SystemClock.uptimeMillis()
        hiddenByScroll = false
        overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        continueScanSoon()
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
        if (overlay.isShowing) handler.postDelayed(watchdog, WATCHDOG_MS)
        // Unsere Leiste ist weg, die echte gibt es noch (z. B. Video offen): laufend nachsehen.
        if (youTubeActive && !overlay.isShowing && (lastBar != null || watchPageOpen)) startPolling()
    }

    private fun hideAll() {
        overlay.hide()
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
        updateSplit()
        if (overlay.isShowing) ourShownAt = now
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
        val youTubeInFront = foreground?.packageName?.toString() == selectors.youtubePackage
        if (!youTubeInFront) leftYouTube = true
        if (foreground != null && !youTubeInFront && overlay.isShowing && waitingForNewWindow(now)) {
            // Andere App (z. B. Chrome nach Tipp auf Werbung) ist gemeldet, aber noch nicht zu sehen.
            reason("Tipp, warte auf neues Fenster")
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }
        if (foreground != null && foreground.packageName?.toString() == SYSTEM_UI) {
            // Tablet: Tipp auf den Griff zwischen zwei Apps öffnet ein kleines System-Menü (Tauschen). Das sind keine
            // Benachrichtigungen: unsere bleibt, sonst sah man die echte Leiste, solange das Menü offen war.
            val front = Rect().also { foregroundWindow()?.getBoundsInScreen(it) }
            val display = displayBounds()
            if (isTablet && youTubeActive && !front.isEmpty &&
                front.width().toLong() * front.height() < display.width().toLong() * display.height() / 6
            ) {
                reason("kleines System-Menü vorne, unsere bleibt")
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
            if (handleShade(foreground, now)) return
        } else if (foreground == null && now - shadeClosingAt < SHADE_GAP_MS) {
            // Nach dem Zuklappen meldet Android kurz gar kein Fenster: unsere stehen lassen.
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        } else {
            shadeOverYouTube = false
        }
        val keyboard = youTubeInFront && isKeyboardOpen()
        // Tastatur offen: jedes Bild nachsehen, ob sie zugeht (Android meldet das nicht immer sofort).
        // Dafür die Scans nicht auf 40 ms drosseln: Nach "Suchen" sah man sonst 2 Bilder die echte Leiste.
        if (keyboard) {
            fastUntil = maxOf(fastUntil, now + KEYBOARD_FAST_MS)
            handler.postDelayed({ scheduleScan() }, POLL_MS)
        }
        if (foreground == null || !youTubeInFront || keyboard) {
            reason(if (keyboard) "Tastatur offen" else "YouTube nicht vorne (${foreground?.packageName})")
            // Unter der Tastatur ist eine andere Seite (Suchvorschläge): Danach die Lage der echten Leiste neu
            // ansehen, nicht "weggescrollt" von vorher weiterführen (sonst stand unsere auf den Vorschlägen).
            if (keyboard) hiddenByScroll = false
            // Tablet: Seite ohne Leiste (z. B. Kamera nach Plus), dann fragt Android nach Fotos-Zugriff. Danach
            // nicht unsere vorab zeigen, sonst stand sie ~2 s über den Knöpfen der Kamera-Seite.
            if (youTubeActive && !keyboard) leftWithoutBar = isTablet && !overlay.isShowing && lastBar == null
            youTubeActive = false
            menuFront = false
            menuOpen = false
            youTubeSince = 0L
            hideAll()
            return
        }
        // "Suchen": Die Ergebnisse haben eine Leiste, die gleich hinter der ausblendenden Tastatur hervorkommt.
        // Unsere sofort zeigen, noch vor jeder weiteren Abfrage (YouTube ist beim Laden langsam).
        if (keyboardToPage && !youTubeActive) {
            keyboardToPage = false
            keyboardWasOpen = false
            val rest = restBounds
            val state = lastUiState
            if (rest != null && state != null && !watchPageOpen) {
                reason("Suchen: unsere sofort")
                youTubeActive = true
                youTubeSince = now
                preShownAt = now
                hiddenByScroll = false
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                continueScanSoon()
                return
            }
        }
        val root = youTubeMainRoot() ?: foreground
        if (leftYouTube) {
            leftYouTube = false
            enteredYouTubeAt = now
            touchedSinceEnter = false
            // Vielleicht kommt ein neuer Link: den geteilten Short neu merken.
            linkShort = null
        }
        // Menü (z. B. nach ⋮) im eigenen Fenster über YouTube: Es liegt über der echten Leiste, unsere läge
        // aber über dem Menü. Darum weg, solange das Menü die Leiste abdeckt; fährt es rein oder raus, steht
        // unsere. Alles andere (Seite, Link-Short) bleibt wie es ist. Das Fenster darunter gibt Android solange
        // gar nicht heraus, darum hier nichts weiter suchen.
        menuFront = isMenuWindow(foreground)
        if (menuFront) {
            youTubeActive = true
            val rest = restBounds
            val state = lastUiState
            val sheetTop = scanner.sheetTop(foreground)
            if (foreground.windowId != menuWindowId) {
                menuWindowId = foreground.windowId
                menuMinTop = Int.MAX_VALUE
            }
            if (sheetTop != null) menuMinTop = minOf(menuMinTop, sheetTop)
            // Fährt es schon ein Stück runter, geht es zu: unsere schon jetzt zeigen, sonst sieht man die echte kurz.
            val closing = sheetTop != null && sheetTop > menuMinTop + (CLOSING_DP * resources.displayMetrics.density)
            menuOpen = rest == null || state == null || watchPageOpen || sheetTop == null ||
                (sheetTop < rest.top - 2 && !closing)
            val sheet = scanner.sheetBounds(foreground)
            val dim = menuDim
            if (menuOpen && rest != null && state != null && sheet != null && !watchPageOpen && dim != null) {
                // Hinter dem Menü dunkelt YouTube alles ab, auch seine Leiste. Statt sie dort durchscheinen zu
                // lassen, zeigen wir unsere genauso abgedunkelt, mit einem Loch, wo das Menü liegt.
                reason("Menü offen, unsere abgedunkelt")
                overlay.showUnderMenu(rest, state.copy(style = currentStyle()), dim, sheet)
            } else if (menuOpen) {
                reason("Menü offen")
                hideAll()
                // Einmal messen, wie stark YouTube abdunkelt; ab dann zeigen wir unsere abgedunkelt.
                if (dim == null && rest != null && sheet != null) measureMenuDim(rest, sheet)
            } else if (rest != null && state != null) {
                reason("Menü fährt rein/raus (oben=$sheetTop)")
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            }
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }
        menuOpen = false
        // Nur wenn YouTube schon vorne war: Beim Zurückkommen ist kurz ein kleines Startfenster vorne.
        if (youTubeActive && youTubeSince != 0L && now - youTubeSince > POPUP_AFTER_MS) popupFront(foreground)?.let { popup ->
            youTubeActive = true
            if (youTubeSince == 0L) youTubeSince = now
            val rest = restBounds
            val state = lastUiState
            val big = popup.width().toLong() * popup.height() > screen.width().toLong() * screen.height() / 6
            if (big && rest != null && state != null && (dialogSlid || now - ourShownAt < DIALOG_SHOWN_MS) && !watchPageOpen) {
                dialogDimmed = true
                // Deckt der Dialog die ganze Leiste ab, fährt unsere sanft darunter weg (die echte bleibt verdeckt).
                // Sonst (z. B. Tablet: Dialog in der Mitte) sähe man dabei die echte: dann abgedunkelt stehen lassen.
                val coversBar = popup.left <= rest.left + 2 && popup.right >= rest.right - 2 && popup.bottom >= rest.bottom - 2
                if (!dialogNoSlide && (dialogSlid || coversBar)) {
                    reason("Dialog deckt die Leiste, unsere fährt runter")
                    dialogSlid = true
                    overlay.slideDown(rest, state.copy(style = currentStyle()))
                } else {
                    // Dialog (z. B. Suchfilter): Android dunkelt alles dahinter ab, auch die echte Leiste. Unsere genauso.
                    reason("Dialog vorne, unsere abgedunkelt")
                    overlay.showUnderMenu(rest, state.copy(style = currentStyle()), DIALOG_DIM, popup)
                }
            } else {
                reason("kleines Menü vorne, unsere bleibt")
            }
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }
        if (dialogDimmed) {
            dialogDimmed = false
            // Nach dem Wegfahren kommt unsere ohne Animation sofort wieder ganz (kein Hochfahren: dabei sähe man die echte).
            dialogSlid = false
            dialogNoSlide = false
            val rest = restBounds
            val state = lastUiState
            if (rest != null && state != null && overlay.isShowing) overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        }
        // Ein anderes YouTube-Fenster liegt über der Seite mit der Leiste (z. B. Premium-Werbung): Es deckt die
        // echte Leiste ab, unsere läge darüber. Wie eine Seite ohne Leiste behandeln (Schliessen zeigt sie vorab).
        // (Die Kamera ist anders: Dort ist das Hauptfenster gar nicht da, siehe barlessWindowId.)
        if (foreground.windowId == root.windowId) coverWindowId = -1
        if (foreground.windowId != root.windowId && foreground.windowId != barlessWindowId &&
            now - preShownAt >= PRE_SHOW_HOLD_MS && !waitingForNewWindow(now)
        ) {
            val rest = restBounds
            // Nur wenn es die ganze Leiste abdeckt: Das ⋮-Popup (YouTube 21.42) streift sie bloss mit seinem Schatten.
            if (rest == null || windowBounds(foreground.windowId).contains(rest)) {
                youTubeActive = true
                if (youTubeSince == 0L) youTubeSince = now
                reason("anderes YouTube-Fenster über der Leiste")
                coverWindowId = foreground.windowId
                hideAll()
                return
            }
        }
        if (!youTubeActive) {
            // YouTube kommt gerade nach vorne: unsere Leiste sofort an der gemerkten Stelle zeigen,
            // nicht erst nach dem Suchen. Die normale Logik übernimmt ab dem nächsten Durchgang.
            sampler.recheckSoon()
            val rest = restBounds
            val state = lastUiState
            // Nicht mit gespeicherter Lage nach einem Neustart: Vielleicht ist gerade ein Video oder die Kamera offen.
            // Nach Zurück aus der Tastatur auch nicht: Die Suchvorschläge haben keine Leiste. Die Suche unten zeigt
            // unsere, wenn die echte da ist. Nach "Suchen" aber sofort: YouTube baut die Ergebnisse auf und antwortet
            // bis 400 ms nicht, die echte Leiste kommt schon vorher hinter der Tastatur hervor.
            val afterKeyboard = keyboardWasOpen && !keyboardToPage
            if (keyboardToPage) preShownAt = now
            keyboardWasOpen = false
            keyboardToPage = false
            barGoneAfterKeyboard = afterKeyboard
            val wasBarless = leftWithoutBar
            leftWithoutBar = false
            if (rest != null && state != null && !watchPageOpen && !restoredState && !afterKeyboard && !wasBarless) {
                reason("YouTube wieder vorne, unsere sofort")
                youTubeActive = true
                youTubeSince = now
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                continueScanSoon()
                return
            }
        }
        youTubeActive = true
        if (youTubeSince == 0L) youTubeSince = now
        // Zurück aus einem YouTube-Fenster ohne Leiste (z. B. Kamera nach Plus): Sobald ein anderes Fenster vorne
        // ist, kommt die Seite mit der Leiste zurück. Sofort zeigen, die Suche danach kostet sonst 2 Bilder.
        val barless = barlessWindowId
        if (barless != -1 && (foreground.windowId != barless || root.windowId != barless)) {
            barlessWindowId = -1
            // Vielleicht wurde dort YouTube auf hell/dunkel umgestellt (Einstellungen).
            sampler.recheckSoon()
            val rest = restBounds
            val state = lastUiState
            if (rest != null && state != null && !watchPageOpen && !overlay.isShowing) {
                reason("zurück von Seite ohne Leiste")
                hiddenByScroll = false
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                continueScanSoon()
                return
            }
        }
        // Gerade getippt: Zuerst nur nachsehen, ob ein Short aufgeht, damit er so schnell wie möglich wieder zugeht.
        if (now < fastUntil && !linkShortActive && scanner.isShortsPlayerOpen(root, screen) && !shortAllowed(root, now)) {
            onShortsOpen()
            return
        }

        // Video offen: nur den Player nachsehen (eine Abfrage). Eine volle Suche dauert, während YouTube die
        // Video-Seite wegschiebt, bis zu 500 ms und hält so die Bild-für-Bild-Prüfung auf.
        onScreenSizeChanged(screenBounds())
        val openWatch = watchNode
        if (watchPageOpen && openWatch != null) {
            val open = watchStillOpen(openWatch, now)
            if (open == true) {
                // Beim Öffnen fährt der Player noch hoch: immer die höchste Lage merken.
                watchTop = minOf(watchTop, scanner.topOf(openWatch))
                reason("Video offen")
                hideAll()
                return
            }
            // Video geht gerade zu: unsere Leiste sofort zeigen, die volle Suche unten prüft danach alles.
            watchPageOpen = open == null
            watchNode = null
            val rest = restBounds
            val state = lastUiState
            if (open == false && rest != null && state != null) {
                reason("Video verlassen (Scan)")
                preShownAt = SystemClock.uptimeMillis()
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
        val player = scanner.findWatchPlayer(root, screen)
        if (watchPageOpen && now < watchRelearnUntil) {
            // Gerade gedreht: Der Player hat kurz eine ungewohnte Grösse oder wird neu gebaut. Video bleibt offen.
            player?.let { watchNode = it; watchTop = scanner.topOf(it) }
            reason("Video offen (gedreht)")
            hideAll()
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }
        watchNode = player?.also { watchTop = scanner.topOf(it) }
        watchPageOpen = watchNode != null

        if (bar == null && !watchPageOpen && handlePanel(root, now)) return
        panelMinTop = Int.MAX_VALUE

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
            if (!shortAllowed(root, now)) {
                onShortsOpen()
                return
            }
        } else if (linkShortActive) {
            // Geteilter Short ist zu (z. B. Startseite getippt). Kurz warten: Beim Umbau fehlt der Player manchmal.
            if (notShortsSince == 0L) notShortsSince = now
            if (now - notShortsSince > LINK_END_MS) {
                reason("Short per Link geschlossen")
                linkShortActive = false
                linkShort = null
            }
        }

        if (bar != null) {
            barSeenOnce = true
            goneFeed = null
            feedBeforeVideo = null
            lastBar = bar
            rememberRestPosition(bar)
            barWindowId = root.windowId
            // Der Player liegt über der Leiste. Kurz kommt das auch vor, wenn der Mini-Player umbaut (z. B. Werbung
            // startet): Dann nicht verstecken, sonst sah man ~7 Bilder die echte Leiste. Erst wenn es so bleibt.
            if (scanner.isBarCovered(root, bar)) {
                if (coveredSince == 0L) coveredSince = now
                if (now - coveredSince >= COVER_HOLD_MS) {
                    reason("verdeckt ${bar.bounds.toShortString()}")
                    hiddenOnPurpose = true
                    hideAll()
                    return
                }
                handler.postDelayed({ scheduleScan() }, POLL_MS)
            } else {
                coveredSince = 0L
            }
            hiddenOnPurpose = false
            // Positionen nur von der ganz eingefahrenen Leiste übernehmen, sonst nur Auswahl und roten Punkt.
            val atRest = bar.bounds == restBounds
            if (!atRest) restSince = 0L else if (restSince == 0L) restSince = now
            restoredState = false
            val previous = lastUiState
            lastUiState = if (atRest || previous == null) {
                buildUiState(bar).also { saved ->
                    restBounds?.let {
                        if (atRest) {
                            saveBarState(it, saved)
                            restBySize[sizeKey(screen)] = Rect(it) to saved
                        }
                    }
                }
            } else {
                previous.copy(
                    selected = bar.tabs.values.firstOrNull { it.selected }?.kind ?: previous.selected,
                    subscriptionsBadge = bar.tabs[TabKind.SUBSCRIPTIONS]?.badge ?: previous.subscriptionsBadge,
                )
            }
        } else if (lastBar == null) {
            if (rest != null && state != null && !barGoneAfterKeyboard && keepWhileScrolledAway(now)) {
                reason("echte weggescrollt und entfernt, unsere bleibt")
                hiddenByScroll = true
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                return
            }
            if (now - preShownAt < PRE_SHOW_HOLD_MS || waitingForNewWindow(now) || now - themeChangedAt < THEME_HOLD_MS) {
                // Kamera fährt noch weg bzw. kommt gleich: unsere Leiste stehen lassen, gleich nochmal nachsehen.
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
            reason("keine Leiste gefunden ($keepFail)")
            barlessWindowId = root.windowId
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

        val found = scanner.findShelves(root, screen, coverLimit())
        if (found.size != shelves.size) Log.d(TAG, "Shorts-Regale: ${found.size}")
        shelves = found
        if (shelves.isNotEmpty()) tryHideShelf(shelves, now)
    }

    /**
     * Nach einem Tipp, der ein neues Fenster öffnet (Plus → Kamera, Werbung → Chrome), nimmt YouTube seine Leiste
     * schon weg, bevor das neue Fenster zu sehen ist (1–3 Bilder lang sah man sonst die echte Leiste). Unsere
     * bleibt stehen, bis es wirklich da ist: Android meldet das Fenster schon als vorne, bevor es gezeichnet ist –
     * erst wenn es Inhalt hat, ist es zu sehen.
     */
    private fun waitingForNewWindow(now: Long, front: AccessibilityWindowInfo? = foregroundWindow()): Boolean {
        val held = now - createTappedAt <= CREATE_HOLD_MS || (now - lastNavAt <= NAV_HOLD_MS && !watchPageOpen)
        if (!held) return false
        if (front == null) return true
        if (front.id == barWindowId) return true
        val root = front.root ?: return true
        return !hasVisibleContent(root)
    }

    /**
     * Benachrichtigungen über YouTube. Solange sie offen sind, meldet Android nur das systemui-Fenster. Beim
     * Zuklappen meldet es YouTube erst ~400 ms später; dazwischen sah man 5 Bilder die echte Leiste. Früheres
     * Zeichen: die Ablage (`notificationShelf`) wird unsichtbar oder wandert nach oben. Dann unsere schon zeigen.
     * true = geregelt.
     */
    private fun handleShade(shade: AccessibilityNodeInfo, now: Long): Boolean {
        if (youTubeActive && !watchPageOpen) {
            shadeOverYouTube = true
            shadeMaxTop = -1
            shadeHadKids = false
            shadeOpenedAt = now
        }
        val rest = restBounds
        val state = lastUiState
        if (!shadeOverYouTube || rest == null || state == null ||
            getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        ) {
            shadeOverYouTube = false
            return false
        }
        youTubeActive = false
        leftYouTube = true
        val shelf = shade.findAccessibilityNodeInfosByViewId("$SYSTEM_UI:id/notificationShelf").firstOrNull()
        val r = Rect().also { shelf?.getBoundsInScreen(it) }
        val visible = shelf?.isVisibleToUser == true
        if (visible) shadeMaxTop = maxOf(shadeMaxTop, r.top)
        // Hat man die Liste vorher gescrollt, ist die Ablage weg. Dann zeigt eine leere Liste das Zugehen an.
        val kids = shade.findAccessibilityNodeInfosByViewId("$SYSTEM_UI:id/notification_stack_scroller")
            .firstOrNull()?.childCount ?: -1
        if (kids > 0) shadeHadKids = true
        // Nur Wandern reicht nicht: Scrollt man die Liste der Benachrichtigungen, wandert die Ablage auch.
        // Ohne Finger auf dem Bildschirm (z. B. Zurück-Taste) kann die Liste nicht gescrollt werden: Dann heisst schon
        // ein kleines Stück nach oben "geht zu". Sonst merkte man es erst ~250 ms später (8 Bilder verwischte Leiste).
        val untouched = now - maxOf(lastTouchAt, shadeOpenedAt) > SHADE_UNTOUCHED_MS
        val closing = (shadeMaxTop > 0 && shelf != null && !visible && r.top > 0 &&
            r.top < shadeMaxTop - CLOSING_DP * resources.displayMetrics.density) || (shadeHadKids && kids == 0) ||
            (untouched && shadeMaxTop > 0 && shelf != null && r.top > 0 && r.top < shadeMaxTop - SHADE_MOVE_PX)
        // Einmal am Zugehen: kurz stehen lassen, auch wenn die Ablage dabei wieder kurz sichtbar gemeldet wird.
        if (closing || now - shadeClosingAt < SHADE_GAP_MS) {
            reason("Benachrichtigungen gehen zu, unsere vorab zeigen")
            if (closing && now - shadeClosingAt >= SHADE_GAP_MS) shadeClosingAt = now
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        } else if (now - shadeOpenedAt < SHADE_VEIL_MS || now - shadeTouchAt < SHADE_TOUCH_VEIL_MS ||
            !shadeCoversBar(shade, rest)
        ) {
            // Android verwischt YouTube hinter dem Schleier, die echte Leiste schimmert durch. Darum dort eine ruhige
            // Fläche in Schleierfarbe, bis die Benachrichtigungen unten ankommen (bleibt unten frei: solange offen).
            reason("Benachrichtigungen offen, Fläche über der Leiste")
            overlay.showVeiled(rest, state, shadeVeil ?: defaultVeil())
        } else {
            reason("Benachrichtigungen offen")
            hideAll()
        }
        if (shadeVeil == null && now - shadeOpenedAt > SHADE_MEASURE_MS) measureShadeVeil()
        handler.postDelayed({ scheduleScan() }, POLL_MS)
        return true
    }

    /**
     * Finger auf den offenen Benachrichtigungen: Vielleicht schiebt er sie gleich hoch. Android meldet das Zugehen
     * erst ~200 ms später, so lange sah man die verwischte echte Leiste. Darum die Fläche schon jetzt zeigen
     * (Berührungen gehen durch sie hindurch); bleiben die Benachrichtigungen offen, verschwindet sie wieder.
     */
    private fun onShadeTouched() {
        shadeTouchAt = SystemClock.uptimeMillis()
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        if (shadeTouchAt - shadeClosingAt >= SHADE_GAP_MS) overlay.showVeiled(rest, state, shadeVeil ?: defaultVeil())
        scheduleScan()
    }

    private fun defaultVeil() = if (nightMode == Configuration.UI_MODE_NIGHT_YES) DEFAULT_VEIL else DEFAULT_VEIL_LIGHT

    /** Reichen die Benachrichtigungen (fertig aufgezogen) bis über die Leiste? Dann decken sie sie selbst ab. */
    private fun shadeCoversBar(shade: AccessibilityNodeInfo, rest: Rect): Boolean {
        val stack = shade.findAccessibilityNodeInfosByViewId("$SYSTEM_UI:id/notification_stack_scroller")
            .firstOrNull() ?: return true
        val r = Rect()
        var bottom = 0
        for (i in 0 until stack.childCount) {
            val child = stack.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            child.getBoundsInScreen(r)
            bottom = maxOf(bottom, r.bottom)
        }
        return bottom > rest.top
    }

    /**
     * Farbe des Schleiers messen: Bildschirmfoto, oben in der Mitte (dort liegt nur der verwischte Kopf von YouTube,
     * der etwa die Farbe der Leiste hat). Einmal je hell/dunkel.
     */
    private fun measureShadeVeil() {
        if (veilMeasuring || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        veilMeasuring = true
        val night = nightMode
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val buffer = result.hardwareBuffer
                        val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        if (bitmap != null && night == nightMode && shadeOverYouTube) {
                            var r = 0L; var g = 0L; var b = 0L; var n = 0
                            val x0 = (bitmap.width * 0.42f).toInt()
                            val x1 = (bitmap.width * 0.62f).toInt()
                            val y1 = (12 * resources.displayMetrics.density).roundToInt()
                            for (y in 2 until y1 step 2) for (x in x0 until x1 step 4) {
                                val p = bitmap.getPixel(x, y)
                                r += android.graphics.Color.red(p); g += android.graphics.Color.green(p)
                                b += android.graphics.Color.blue(p); n++
                            }
                            bitmap.recycle()
                            if (n > 0) {
                                shadeVeil = android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
                                shadeVeils[night] = shadeVeil!!
                                Log.d(TAG, "Schleierfarbe gemessen: #${Integer.toHexString(shadeVeil!!)}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Schleierfarbe messen fehlgeschlagen", e)
                    }
                    veilMeasuring = false
                }

                override fun onFailure(errorCode: Int) {
                    veilMeasuring = false
                }
            })
        } catch (e: Exception) {
            veilMeasuring = false
        }
    }

    /** Ist das Fenster mit der Leiste vorne?Ein kleines Popup (⋮), das die Leiste nicht ganz abdeckt, zählt nicht. */
    private fun isBarWindowInFront(): Boolean {
        val front = foregroundWindow() ?: return false
        if (front.id == barWindowId) return true
        val rest = restBounds ?: return false
        return front.root?.packageName?.toString() == selectors.youtubePackage && !windowBounds(front.id).contains(rest)
    }

    private fun hasVisibleContent(root: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            child.getBoundsInScreen(r)
            if (child.isVisibleToUser && !r.isEmpty) return true
        }
        return false
    }

    /**
     * Panel von unten im YouTube-Fenster selbst (z. B. Werbe-Menü "Mein Anzeigen-Center"). Es liegt über der
     * echten Leiste, unsere läge darüber: weg, solange es offen ist. Fährt es runter oder ist es zu, steht unsere
     * sofort – YouTube setzt seine Leiste erst ein paar Bilder später wieder ein, sonst sähe man sie kurz.
     * Gibt true zurück, wenn das Panel alles geregelt hat.
     */
    private fun handlePanel(root: AccessibilityNodeInfo, now: Long): Boolean {
        val top = scanner.panelTop(root, screen)
        val rest = restBounds
        val state = lastUiState
        if (top == null) {
            if (panelMinTop == Int.MAX_VALUE) return false
            panelMinTop = Int.MAX_VALUE
            if (rest == null || state == null) return false
            reason("Panel zu, unsere vorab zeigen")
            preShownAt = now
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            continueScanSoon()
            return true
        }
        panelMinTop = minOf(panelMinTop, top)
        val closing = top > panelMinTop + CLOSING_DP * resources.displayMetrics.density
        if (rest != null && state != null && (closing || top >= rest.top - 2)) {
            reason("Panel fährt raus (oben=$top)")
            preShownAt = now
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        } else {
            reason("Panel offen (oben=$top)")
            hideAll()
        }
        handler.postDelayed({ scheduleScan() }, POLL_MS)
        return true
    }

    /**
     * Misst, wie stark YouTube hinter einem offenen Menü abdunkelt. Dazu ein Bildschirmfoto, solange unsere
     * Leiste weg ist: Das hellste Pixel der echten Leiste neben dem Menü (weisse Schrift bzw. weisser
     * Hintergrund im hellen Design) wäre ohne Abdunkeln etwa 245 hell.
     */
    private fun measureMenuDim(rest: Rect, sheet: Rect) {
        if (dimMeasuring || overlay.isShowing || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        dimMeasuring = true
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val buffer = result.hardwareBuffer
                        val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        if (bitmap != null) {
                            var brightest = 0f
                            // Nur Icons und Schrift (oberer Teil), nicht den Gestenstrich darunter: den zeichnet
                            // Android über die Abdunkelung, er ist immer hell.
                            val iconsBottom = rest.top + (LABEL_TOP_DP * 1.4f * resources.displayMetrics.density).roundToInt()
                            val area = Rect(rest.left, rest.top, rest.right, minOf(rest.bottom, iconsBottom))
                                .apply { intersect(0, 0, bitmap.width, bitmap.height) }
                            var y = area.top
                            while (y < area.bottom) {
                                var x = area.left
                                while (x < area.right) {
                                    if (!sheet.contains(x, y)) {
                                        val p = bitmap.getPixel(x, y)
                                        val l = 0.299f * android.graphics.Color.red(p) +
                                            0.587f * android.graphics.Color.green(p) +
                                            0.114f * android.graphics.Color.blue(p)
                                        if (l > brightest) brightest = l
                                    }
                                    x += 2
                                }
                                y += 2
                            }
                            bitmap.recycle()
                            // Nur wenn genug von der echten Leiste neben dem Menü zu sehen war.
                            if (brightest > 10f) {
                                val dim = (1f - brightest / 245f).coerceIn(0.15f, 0.85f)
                                menuDim = dim
                                Prefs.setMenuDim(this@FuckShortsService, dim)
                                Log.d(TAG, "Menü-Abdunkelung gemessen: $dim")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Abdunkelung messen fehlgeschlagen", e)
                    }
                    dimMeasuring = false
                    scheduleScan()
                }

                override fun onFailure(errorCode: Int) {
                    dimMeasuring = false
                }
            })
        } catch (e: Exception) {
            dimMeasuring = false
        }
    }

    /**
     * Lage und Inhalt der ruhenden echten Leiste speichern. Nach einem Neustart unseres Dienstes (App-Update) auf
     * weggescrollter Startseite gibt es sonst keine echte Leiste zum Abschauen, und unsere fehlte.
     */
    private fun saveBarState(rest: Rect, state: BarUiState) {
        val json = org.json.JSONObject()
            .put("screen", "${screen.width()}x${screen.height()}")
            .put("rest", rest.flattenToString())
            .put("selected", state.selected?.name ?: "")
            .put("badge", state.subscriptionsBadge)
            .put("create", state.hasCreate)
            .put("labels", org.json.JSONObject(state.labels.mapKeys { it.key.name }))
            .toString()
        if (json == savedBarState) return
        savedBarState = json
        Prefs.setBarState(this, json)
    }

    private fun loadBarState() {
        try {
            val text = Prefs.barState(this) ?: return
            val json = org.json.JSONObject(text)
            screen = screenBounds()
            if (json.getString("screen") != "${screen.width()}x${screen.height()}") return
            val rest = Rect.unflattenFromString(json.getString("rest")) ?: return
            val labels = json.getJSONObject("labels")
            val density = resources.displayMetrics.density
            restBounds = rest
            lastUiState = BarUiState(
                selected = json.getString("selected").takeIf { it.isNotEmpty() }?.let { TabKind.valueOf(it) },
                labels = labels.keys().asSequence().associate { TabKind.valueOf(it) to labels.getString(it) },
                subscriptionsBadge = json.getBoolean("badge"),
                hasCreate = json.getBoolean("create"),
                barHeightPx = rest.height(),
                iconCenterPx = (ICON_CENTER_DP * density).roundToInt(),
                labelTopPx = (LABEL_TOP_DP * density).roundToInt(),
                showHandle = rest.bottom >= displayBounds().bottom - 2,
                style = currentStyle(),
            )
            restBySize[sizeKey(screen)] = Rect(rest) to lastUiState!!
            savedBarState = text
            restoredState = true
            Log.d(TAG, "Leiste: gespeicherte Lage geladen ${rest.toShortString()}")
        } catch (e: Exception) {
            Log.w(TAG, "Gespeicherte Leiste unlesbar", e)
        }
    }

    /** Merkt sich, wo die echte Leiste sitzt, wenn sie ganz eingefahren ist (höchste Lage, volle Höhe). */
    private fun rememberRestPosition(bar: FoundBar) {
        val rest = restBounds
        val b = bar.bounds
        // Tablet: Beim Schliessen der Kamera meldet YouTube die Leiste kurz 32 px zu hoch (Unterkante über der
        // gewohnten). Das ist keine neue Ruhelage, sonst stand unsere danach zu hoch und hell, darunter "Shorts".
        if (isTablet && rest != null && b.bottom < rest.bottom - 2) return
        // Tablet: Steht die ganze Leiste tiefer als die gemerkte Lage, war diese falsch (auch gespeichert): neu lernen.
        val wrongRest = isTablet && rest != null && b.height() >= rest.height() - 2 && b.bottom > rest.bottom + 2
        if (rest == null || wrongRest || b.height() > rest.height() + 2 || (b.height() >= rest.height() - 2 && b.top < rest.top)) {
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
        // Menü vorne oder Panel offen: Das regelt der Scan (Lage des Menüs), die Leiste ist nicht abfragbar.
        if (menuFront || panelMinTop != Int.MAX_VALUE || coverWindowId != -1) return
        if (popupFront(foregroundWindow()?.root) != null) return
        if (rest == null || state == null || menuOpen) {
            overlay.hide()
            return
        }
        // Keine echte Leiste bekannt und unsere ist weg (z. B. Short per Link läuft und meldet dauernd
        // "gescrollt"): nichts nachzuführen, das Wiederauftauchen merkt der Scan.
        if (fresh == null && lastBar == null && !overlay.isShowing && !watchPageOpen) return
        // Video offen: Die echte Leiste liegt unverändert hinter dem Video (oder fehlt ganz, wenn die Startseite
        // weggescrollt war). Das Verlassen merkt man darum nicht an ihr, sondern daran, dass der grosse Player
        // nach unten wegrutscht. YouTube meldet dabei ~100 ms lang nichts; darum bei jedem Bild selbst nachsehen.
        if (watchPageOpen && fresh == null) {
            onScreenSizeChanged(screenBounds())
            val node = watchNode
            if (node == null && now < watchRelearnUntil) {
                reason("Video offen (gedreht)")
                overlay.hide()
                scheduleScan()
                return
            }
            if (node != null && watchStillOpen(node, now) != false) {
                watchTop = minOf(watchTop, scanner.topOf(node))
                reason("Video offen")
                overlay.hide()
                return
            }
            watchPageOpen = false
            watchNode = null
            reason("Video verlassen")
            preShownAt = SystemClock.uptimeMillis()
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
            if (now - themeChangedAt < THEME_HOLD_MS) {
                // YouTube baut sich nach hell/dunkel neu auf, die Leiste fehlt kurz: unsere stehen lassen.
                reason("Design gewechselt, YouTube baut neu auf")
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
            if (!barGoneAfterKeyboard && keepWhileScrolledAway(now)) {
                reason("echte weggescrollt und entfernt, unsere bleibt")
                hiddenByScroll = true
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                return
            }
            if (now - preShownAt < PRE_SHOW_HOLD_MS) return
            if (waitingForNewWindow(now)) {
                reason("Tipp, warte auf neues Fenster")
                return
            }
            // YouTube baut die Leiste manchmal neu (z. B. beim Scrollen auf Abos): Dann ist nur unser gemerktes Element
            // weg, die echte Leiste steht noch da. Einmal neu suchen, sonst sah man sie bis zum nächsten Scan (~1,5 s).
            val main = youTubeMainRoot()?.takeIf { it.windowId == foregroundWindow()?.id }
            val again = main?.let { scanner.findBar(it, screen) }
            if (again != null && again.bounds.top <= rest.top + 2 && again.tabs[TabKind.SHORTS]?.selected != true) {
                lastBar = again
                updateBar(now, again)
                return
            }
            // Benachrichtigungen werden gerade aufgezogen: Das regelt der Scan (Fläche über der Leiste). Hier nicht
            // verstecken, sonst sah man 2 Bilder die verwischte echte Leiste.
            // (Beim Wechsel meldet Android kurz gar kein Fenster: ebenso dem Scan überlassen.)
            val frontPkg = foregroundWindow()?.root?.packageName?.toString()
            if (frontPkg == SYSTEM_UI || (frontPkg == null && now - lastTouchAt < SHADE_UNTOUCHED_MS)) {
                scheduleScan()
                return
            }
            // Die Leiste gibt es nicht mehr (anderer Bildschirm, z. B. Kamera nach Plus): sofort weg.
            reason("Leiste nicht mehr da ($keepFail)")
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
        val fullyGone = observedTop >= screen.bottom - 2
        if (!fullyGone) barGoneAfterKeyboard = false
        if (barGoneAfterKeyboard) {
            reason("nach Tastatur, echte ganz weg")
            overlay.hide()
            return
        }
        reason("normal (top=$observedTop)")
        // Startseite: unsere Leiste bleibt immer ganz da und fährt nie halb runter. Sie verschwindet erst,
        // wenn nach einem Tipp (Video geöffnet) die echte Leiste schon komplett weg ist.
        val onHome = state.selected == TabKind.HOME
        // Eben absichtlich vorab gezeigt (z. B. "Suchen"): Die echte Leiste kommt gleich von unten, nicht verstecken.
        val held = now - preShownAt < PRE_SHOW_HOLD_MS
        val videoOpening = now - lastNavAt < NAV_LINK_MS && navFromRest && !held
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
        val top = if (hiddenByScroll || held) rest.top else observedTop.coerceAtLeast(rest.top)
        if (top >= screen.bottom - 2) {
            overlay.hide()
        } else {
            overlay.show(top, rest, state.copy(style = currentStyle()))
        }
    }

    /** Echte Leiste ist weg: Nur weil gescrollt wurde (dieselbe Liste ist noch da)? Dann bleibt unsere stehen. */
    private fun keepWhileScrolledAway(now: Long): Boolean {
        if (watchPageOpen) {
            // Video aus der weggescrollten Liste geöffnet: Nach dem Verlassen ist es wieder dieselbe Liste.
            if (hiddenByScroll && goneFeed != null) feedBeforeVideo = goneFeed
            goneFeed = null
            keepFail = "Video offen"
            return false
        }
        feedBeforeVideo?.let { before ->
            feedBeforeVideo = null
            if (before.windowId == barWindowId && scanner.isFeedStillThere(before, screen)) goneFeed = before
        }
        val known = goneFeed
        val barWindowFront = isBarWindowInFront()
        if (known != null && known.windowId == barWindowId && barWindowFront && scanner.isFeedStillThere(known, screen)) {
            feedKeptAt = now
            return true
        }
        // Noch keine Liste gemerkt, oder sie war beim schnellen Wischen kurz nicht abfragbar: neu suchen,
        // aber nur direkt nach dem Scrollen und ohne Tipp dazwischen (sonst ist es wohl eine andere Seite).
        goneFeed = null
        // YouTube nimmt seine Leiste schon weg, sobald der Finger zum Wischen aufliegt, noch bevor es "gescrollt"
        // meldet: Die Berührung zählt darum schon als Scrollen. Ein Tipp (z. B. auf ein Video) meldet danach einen Klick.
        val scrollAt = maxOf(lastScrollAt, lastTouchAt)
        if (lastNavAt > scrollAt) {
            keepFail = "Tipp nach dem Scrollen"
            return false
        }
        // YouTube entfernt seine weggescrollte Leiste oft erst Sekunden später (z. B. ~8 s, wenn ein Video in der
        // Liste anläuft). Stand unsere wegen Scrollen allein da, ist es also trotzdem noch dieselbe Seite.
        // Neustart unseres Dienstes (App-Update) auf weggescrollter Startseite: Die echte Leiste gibt es nicht, ihre
        // Lage kennen wir aus dem Speicher. Steht die Startseiten-Liste im vordersten YouTube-Fenster, bleibt unsere.
        if (restoredState && barWindowId == -1 && lastUiState?.selected == TabKind.HOME) {
            val main = youTubeMainRoot()
            val feed = main?.takeIf { foregroundWindow()?.id == it.windowId }?.let { scanner.findFeed(it, screen) }
            if (main != null && feed != null) {
                barWindowId = main.windowId
                goneFeed = feed
                feedKeptAt = now
                return true
            }
        }
        if (known == null && now - scrollAt > SCROLL_GONE_MS && !hiddenByScroll) {
            keepFail = "nicht gescrollt"
            return false
        }
        // Nur eine Liste im Fenster der Leiste zählt (die YouTube-Einstellungen sind ein eigenes Fenster mit Liste).
        val root = youTubeMainRoot()?.takeIf { it.windowId == barWindowId && barWindowFront }
        if (root == null) {
            keepFail = "anderes Fenster vorne"
            return false
        }
        goneFeed = scanner.findFeed(root, screen)
        if (goneFeed != null) {
            feedKeptAt = now
            return true
        }
        // Läuft ein Video in der Liste an, baut YouTube die Liste um und sie ist kurz nicht abfragbar.
        // Dann nicht gleich verstecken, sondern gleich nochmal nachsehen.
        if (hiddenByScroll && now - feedKeptAt < FEED_GRACE_MS) {
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return true
        }
        keepFail = "keine Liste"
        return false
    }

    private fun coverLimit(): Int = if (overlay.isShowing) overlay.top else screen.bottom

    private fun currentStyle(): BarStyle = sampler.style(nightMode == Configuration.UI_MODE_NIGHT_YES)

    /**
     * Kleines Popup-Menü vorne (z. B. ⋮ oben rechts in der Suche). Solange es offen ist, gibt Android YouTubes
     * Hauptfenster nicht heraus, die Leiste ist also nicht zu finden – sie steht aber unverändert da. Unsere
     * darum genau so lassen, wie sie ist (vorher war sie weg und man sah die echte mit Shorts).
     */
    private fun popupFront(front: AccessibilityNodeInfo?): Rect? {
        if (front == null || front.packageName?.toString() != selectors.youtubePackage) return null
        if (menuWindows[front.windowId] == true) return null
        val rest = restBounds ?: return null
        val b = windowBounds(front.windowId)
        if (b.contains(rest) || Rect.intersects(b, rest)) return null
        return b.takeIf { it.width().toLong() * it.height() < screen.width().toLong() * screen.height() * 7 / 10 }
    }

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
            showHandle = rest.bottom >= displayBounds().bottom - 2,
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
        leaveShorts()
    }

    /**
     * Darf dieser Short offen bleiben? Nur ein per Link geöffneter (siehe [enteredYouTubeAt]) – und nur genau
     * dieser: Wischt man zum nächsten, scrollen wir die Liste wieder zurück an den Anfang.
     */
    private fun shortAllowed(root: AccessibilityNodeInfo, now: Long): Boolean {
        notShortsSince = 0L
        if (!linkShortActive) {
            if (touchedSinceEnter || now - enteredYouTubeAt > LINK_WINDOW_MS) return false
            linkShortActive = true
            linkShort = null
            reason("Short per Link offen")
        }
        // Der Abdruck braucht viele Abfragen; ein laufender Short löst aber dauernd Scans aus.
        if (linkShort != null && now - lastFingerprintAt < FINGERPRINT_MS) return true
        lastFingerprintAt = now
        val current = scanner.shortFingerprint(root, screen)
        if (current != lastFingerprint) {
            lastFingerprint = current
            Log.d(TAG, "Short sichtbar: $current (geteilt: $linkShort)")
        }
        if (current == null) return true
        val first = linkShort
        // Lädt der Short noch, kommen Kanal und Titel nacheinander: der längere Abdruck gilt.
        if (first == null || current.startsWith(first)) {
            if (current != first) Log.d(TAG, "Short per Link: $current")
            if (first == null) linkShortAt = now
            linkShort = current
            linkFixTries = 0
            return true
        }
        if (first.startsWith(current)) return true
        // Direkt nach dem Öffnen zeigt YouTube manchmal kurz noch den alten Short, dann erst den aus dem Link.
        if (now - linkShortAt < LINK_SETTLE_MS) {
            Log.d(TAG, "Short per Link (neu geladen): $current")
            linkShort = current
            return true
        }
        if (now - lastLinkFixAt < LINK_FIX_MS) return true
        lastLinkFixAt = now
        // Die Shorts-Liste lässt sich über die Bedienungshilfen nicht scrollen: einmal echt zurückwischen.
        if (linkFixTries == 0) {
            linkFixTries++
            reason("Short per Link: weitergewischt, zurück")
            swipeFeedDown()
            handler.postDelayed({ scheduleScan() }, LINK_FIX_MS)
            return true
        }
        // Hat nicht gereicht: Shorts ganz schliessen.
        reason("Short per Link: weitergewischt, schliesse")
        linkShortActive = false
        linkShort = null
        return false
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
        touchedSinceEnter = true
        val root = youTubeMainRoot() ?: run {
            tapLog("$kind: kein YouTube-Fenster gefunden")
            return
        }
        if (kind == TabKind.CREATE) {
            createTappedAt = SystemClock.uptimeMillis()
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
        if (active != null && !isOurs(active)) {
            // Splitscreen: Die andere App ist aktiv, YouTube aber daneben zu sehen – mit seiner Leiste.
            if (splitBounds != null && active.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                active.root?.packageName?.toString() != selectors.youtubePackage
            ) youTubeMainWindow(all)?.let { return it }
            return active
        }
        return all.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !isOurs(it) }.maxByOrNull { it.layer }
    }

    private fun isOurs(window: AccessibilityWindowInfo): Boolean =
        window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
            window.root?.packageName?.toString() == packageName

    private fun youTubeMainWindow(all: List<AccessibilityWindowInfo>): AccessibilityWindowInfo? {
        val r = Rect()
        return all
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString() == selectors.youtubePackage }
            .maxByOrNull { it.getBoundsInScreen(r); r.width().toLong() * r.height() }
    }

    /**
     * Splitscreen? YouTubes Hauptfenster ist dann so breit wie der Bildschirm, aber nur etwa halb so hoch. Dann gilt
     * sein Fenster als "Bildschirm": Leiste, Player und Co. liegen darin wie sonst auf dem ganzen Bildschirm.
     */
    private fun updateSplit() {
        val display = displayBounds()
        val main = youTubeMainWindow(windows)
        val b = main?.let { Rect().also { r -> it.getBoundsInScreen(r) } }
        splitBounds = b?.takeIf {
            !main.isInPictureInPictureMode && it.width() >= display.width() - 4 &&
                it.height() in (display.height() / 4)..(display.height() * 85 / 100)
        }
    }

    /**
     * Das Hauptfenster von YouTube (das grösste). Dort liegen Leiste und Startseite, auch wenn ein Menü offen ist.
     */
    private fun youTubeMainRoot(): AccessibilityNodeInfo? {
        val r = Rect()
        return windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { w -> w.root?.takeIf { it.packageName?.toString() == selectors.youtubePackage }?.let { w to it } }
            .maxByOrNull { (w, _) -> w.getBoundsInScreen(r); r.width().toLong() * r.height() }
            ?.second
    }

    /** Pro Fenster nur einmal nachsehen: Jede Abfrage kostet Zeit, die beim Verlassen eines Videos fehlt. */
    private fun isMenuWindow(windowRoot: AccessibilityNodeInfo): Boolean {
        menuWindows[windowRoot.windowId]?.let { return it }
        val menu = scanner.isBottomSheet(windowRoot)
        // Ein leeres Fenster wird vielleicht gerade erst aufgebaut: dann später nochmal schauen.
        if (windowRoot.childCount > 0) {
            if (menuWindows.size > 20) menuWindows.clear()
            menuWindows[windowRoot.windowId] = menu
        }
        return menu
    }

    /**
     * Tastatur offen? Nach "Suchen" blendet sie sich noch ~0,3 s aus, Android meldet sie aber bis zum Ende.
     * Dahinter sah man schon die echte Leiste der Ergebnis-Seite. YouTube nimmt den Fokus aber sofort aus dem
     * Suchfeld: Ist er weg, gilt die Tastatur schon als zu.
     */
    private fun isKeyboardOpen(): Boolean {
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        if (ime == null) {
            imeFocusSeen = false
            imeMinTop = Int.MAX_VALUE
            return false
        }
        // Beim Zugehen (Zurück oder "Suchen") meldet Android das Tastatur-Fenster schon tiefer, bevor sie sichtbar
        // runterrutscht und ausblendet. Ab da gilt sie als zu: Sonst sah man 3–5 Bilder die echte Leiste durch.
        val top = Rect().also { ime.getBoundsInScreen(it) }.top
        // Splitscreen, YouTube oben: Die Tastatur gehört zur App unten und deckt die Leiste nicht ab.
        splitBounds?.let { if (top >= it.bottom - 2) return false }
        imeMinTop = minOf(imeMinTop, top)
        if (top > imeMinTop + IME_CLOSING_PX) return false
        // Fokus von einem Eingabefeld auf etwas anderes gewechselt ("Suchen"): zu. Ohne Fokus (kommt beim Tippen
        // kurz vor) oder mit Fokus in der Tastatur selbst bleibt sie offen, sonst lag unsere Leiste einzelne Bilder
        // über der Tastatur.
        val focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.takeIf { it.packageName?.toString() == selectors.youtubePackage }
        if (focus?.isEditable == true) imeFocusSeen = true
        val open = focus == null || focus.isEditable || !imeFocusSeen
        if (open) keyboardWasOpen = true else if (keyboardWasOpen) keyboardToPage = true
        return open
    }

    private fun screenBounds(): Rect = splitBounds?.let { Rect(it) } ?: displayBounds()

    private fun displayBounds(): Rect {
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
        private const val COVER_HOLD_MS = 150L
        private const val KEYBOARD_FAST_MS = 100L
        /** So stark dunkelt Android hinter Dialogen ab (Standard, am Pixel gemessen: 255 → 102). */
        private const val DIALOG_DIM = 0.6f
        private const val DIALOG_SHOWN_MS = 1_000L
        private const val POPUP_AFTER_MS = 1_000L
        private const val POLL_DURATION_MS = 500L
        private const val NAV_LINK_MS = 1_500L
        private const val VERIFY_MS = 400L
        private const val FAST_WINDOW_MS = 1_500L
        private const val MINI_CHECK_MS = 400L
        private const val MINI_WALK_MS = 5_000L
        private const val DRAW_FIRST_MS = 50L
        private const val TABLET_DRAW_WAIT_MS = 500L
        private const val SCROLL_GONE_MS = 1_500L
        private const val FEED_GRACE_MS = 600L
        private const val FOREGROUND_GAP_MS = 500L
        private const val SLOW_SCAN_MS = 150L
        private const val REVEAL_POLL_MS = 50L
        private const val REVEAL_TRIES = 20
        private const val LINK_WINDOW_MS = 8_000L
        private const val LINK_END_MS = 400L
        private const val LINK_FIX_MS = 600L
        private const val LINK_SETTLE_MS = 2_000L
        private const val CLOSING_DP = 24f
        private const val FINGERPRINT_MS = 200L
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
        private const val PRE_SHOW_HOLD_MS = 700L
        private const val CREATE_HOLD_MS = 1_500L
        private const val NAV_HOLD_MS = 800L
        private const val THEME_HOLD_MS = 1_500L
        private const val SHADE_GAP_MS = 600L
        private const val IME_CLOSING_PX = 8
        /** So lange nach dem Aufziehen braucht der Schleier, bis die Benachrichtigungen unten angekommen sind. */
        private const val SHADE_VEIL_MS = 180L
        private const val SHADE_TOUCH_VEIL_MS = 700L
        private const val SHADE_UNTOUCHED_MS = 600L
        private const val SHADE_MOVE_PX = 48
        private const val SHADE_MEASURE_MS = 600L
        private const val WATCH_RELEARN_MS = 1_500L
        private val DEFAULT_VEIL = android.graphics.Color.rgb(0x1B, 0x25, 0x30)
        /** Schleier im hellen Design (Pixel 9 gemessen). */
        private val DEFAULT_VEIL_LIGHT = android.graphics.Color.rgb(0xC9, 0xD2, 0xDC)
        private const val SYSTEM_UI = "com.android.systemui"
        private val CLOSE_LABELS = listOf("schließen", "schliessen", "close", "zurück", "navigate up", "nach oben")

        @Volatile
        var running = false
            private set
    }
}
