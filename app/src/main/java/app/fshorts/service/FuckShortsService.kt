package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
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
import app.fshorts.Access
import app.fshorts.DebugLog
import app.fshorts.Prefs
import app.fshorts.Protection
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
    /** Für Klicks, die unsere Animation nicht aufhalten dürfen (Plus). */
    private val clicker = java.util.concurrent.Executors.newSingleThreadExecutor()
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
    @Volatile
    private var youTubeActive = false
    private var barSeenOnce = false
    private var lastAutoDumpAt = 0L
    private var lastDebugDumpAt = 0L
    private var lastVideoDumpAt = 0L
    private var lastPaneDumpAt = 0L
    /** Letzte Berührung, die nicht auf einer Video-Seite war (z. B. Tipp auf ein Video in der Liste). */
    private var feedTouchAt = 0L
    /** Bis dann nach einer Berührung laufend nachsehen, ob eine Video-Seite aufgeht (YouTube meldet ~1 s nichts). */
    private var tapProbeUntil = 0L
    /** Zuletzt die graue Lade-Seite eines Videos gesehen (unsere ist dann weg). */
    private var loadingPageAt = 0L
    /** Zuletzt nach oben gewischt (Liste rutscht runter): in dieser Berührung fährt unsere nicht weg. */
    private var lastUpScrollAt = 0L
    /** Richtung des Wischens, solange unsere weggescrollt ist (siehe startDirectionProbe, eigener Thread). */
    @Volatile
    private var dirUntil = 0L
    /** Spätestens dann hört die Richtungs-Messung auf (siehe startDirectionProbe). */
    @Volatile
    private var probeUntil = 0L
    /** Die Richtungs-Messung hat ein Listen-Element und misst (dann pausiert die Rückkehr-Prüfung kurz). */
    @Volatile
    private var probeReady = false
    @Volatile
    private var probeGen = 0
    // Zwei eigene Threads: Richtung (beim Fingerauflegen) und Rückkehr der echten Leiste. Im selben Thread warteten sie
    // aufeinander (gemessen 16.11.: Richtung erst nach 216 ms statt ~50 ms erkannt).
    private val probeThread = android.os.HandlerThread("fshorts-richtung").also { it.start() }
    private val probeHandler = Handler(probeThread.looper)
    /** Liste der Seite suchen (kann beim Wischen lange dauern): eigener Thread, damit die Richtung nicht darauf wartet. */
    private val feedThread = android.os.HandlerThread("fshorts-liste").also { it.start() }
    private val feedHandler = Handler(feedThread.looper)
    private val presenceThread = android.os.HandlerThread("fshorts-rueckkehr").also { it.start() }
    private val presenceHandler = Handler(presenceThread.looper)
    private val labHandler by lazy { Handler(android.os.HandlerThread("fshorts-lab").also { it.start() }.looper) }
    /** Seit wann die echte Leiste weg ist (entfernt oder ganz unten). 0 = sie ist zu sehen. */
    @Volatile
    private var realGoneSince = 0L
    /**
     * Liste einer Seite, auf der wir YouTube schon beim Scrollen ausblenden sahen (Startseite, Abos). Nur dort fährt unsere
     * beim schnellen Runterwischen von selbst sofort weg (Mein YouTube und Suchergebnisse blenden nicht aus, gemessen 19.11.).
     */
    @Volatile
    private var hidesFeed: AccessibilityNodeInfo? = null
    /** Die Liste bewegt sich (Wischen): bis dann keine volle Suche, sie hielte unsere Animation an (siehe probeLoop). */
    @Volatile
    private var scanHoldUntil = 0L
    /** Zuletzt ist unsere beim Runterwischen selbst (früh) weggefahren, siehe PROBE_DOWN_EARLY. */
    @Volatile
    private var earlyAwayAt = 0L
    /** Element der gemerkten Liste ([hidesFeed]) für die Früh-Messung. */
    @Volatile
    private var earlyChild: AccessibilityNodeInfo? = null
    @Volatile
    private var presenceRunning = false
    /** Seit wann unsere weggescrollt ist (für die Rückkehr-Prüfung). */
    @Volatile
    private var awaySince = 0L
    /** Liste der Seite, im Hintergrund gemerkt, wenn unsere wegfährt (die echte ist dann oft nicht entfernt). */
    @Volatile
    private var probeFeed: AccessibilityNodeInfo? = null
    /** Ein Element der Liste (Mitte des Bildschirms), dessen Lage beim Fingerauflegen die Wischrichtung verrät. */
    @Volatile
    private var probeChild: AccessibilityNodeInfo? = null

    /** Die echte Leiste wurde beim Scrollen aus der Oberfläche genommen (unsere ist dann weggefahren). */
    private var realGoneByScroll = false
    /** Das offene Panel ging über einer Seite mit unserer Leiste auf (siehe coverByPanel). */
    private var panelFromBar = false
    /** Google Play (Werbung) geht über YouTube auf, unsere ist abgedunkelt (siehe dimForSheetApp). 0 = nein. */
    private var sheetAppAt = 0L
    /** Google Play hat (nach dem Werbe-Tipp) zum ersten Mal Inhalt gemeldet: Es ist gezeichnet. */
    private var sheetAppContentAt = 0L
    /** Android hat (nach dem Werbe-Tipp) ein Startfenster entfernt: Das Play-Blatt ist gezeichnet. */
    private var sheetAppDrawnAt = 0L
    /** Seit dann liegt das Play-Blatt über YouTube (unsere weg); beim Schliessen sofort zeigen. 0 = nein. */
    private var sheetAppLeftAt = 0L
    /** Video offen und die Startseite hat sich gemeldet (PANE_APPEARED): bis dann den Player ganz dicht nachsehen. */
    private var paneBurstUntil = 0L
    /** Zuletzt ist unsere weggefahren, weil nach einem Tipp die echte Leiste ganz hinausfuhr (Video geht auf). */
    private var videoSlideAt = 0L

    private var debugDumpWanted = false
    private var nightMode = 0
    private var screen = Rect()

    // Leiste: YouTube schiebt seine Leiste beim Runterscrollen raus und beim Hochscrollen wieder rein.
    private var restBounds: Rect? = null
    private var lastBar: FoundBar? = null
    private var lastUiState: BarUiState? = null
    @Volatile
    private var hiddenByScroll = false
    private var navFromRest = false
    @Volatile
    private var watchPageOpen = false
        set(value) {
            if (field && !value) watchClosedAt = SystemClock.uptimeMillis()
            field = value
        }
    private var watchClosedAt = 0L
    private var watchNode: AccessibilityNodeInfo? = null
    private var watchTop = 0
    /** Der letzte volle Scan hat unsere Leiste absichtlich versteckt (Shorts, verdeckt): nicht vorschnell zeigen. */
    private var hiddenOnPurpose = false
    /**
     * YouTube nimmt seine Leiste beim Runterscrollen (z. B. Startseite) ganz aus der Oberfläche. Dann merken wir
     * uns die Liste der Seite: Solange es sie gibt, sind wir noch auf derselben Seite und unsere Leiste bleibt.
     */
    @Volatile
    private var goneFeed: AccessibilityNodeInfo? = null
    @Volatile
    private var lastScrollAt = 0L
    private var feedKeptAt = 0L
    @Volatile
    private var lastTouchAt = 0L
    /** Seit wann die Startseiten-Liste nach dem Scrollen nicht zu finden ist (0 = gefunden). */
    private var feedMissSince = 0L

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
    /** Nur fürs Test-Protokoll: zuletzt gemeldetes vorderes Fenster. */
    private var loggedForeground = -2
    private var miniPlayerOpen = false
    private var lastMiniCheckAt = 0L
    private var lastMiniWalkAt = 0L
    @Volatile
    private var lastNavAt = 0L
    private var restSince = 0L
    private var fastUntil = 0L
    private var lastObservedTop = -1
    /** Wann [lastObservedTop] gemessen wurde (eine Richtung gilt nur zwischen zwei frischen Messungen). */
    private var lastObservedAt = 0L
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
    /** Das Menü ging über einer Seite ohne Leiste auf (z. B. Kamera: "Delete / Save and exit / Cancel"). */
    private var menuOverBarless = false
    private var saveExitAt = 0L
    private var menuMinTop = Int.MAX_VALUE
    /** Wie stark YouTube hinter einem Menü abdunkelt (0..1), einmal gemessen. null = noch unbekannt. */
    private var menuDim: Float? = null
    private var dimMeasuring = false
    private val menuWindows = HashMap<Int, Boolean>()
    /** YouTube-Fenster, in dem es keine Leiste gab (z. B. Kamera). -1 = keins. */
    private var barlessWindowId = -1
    private var lastDisplay = Rect()
    private var displayChangedAt = 0L
    /** Je Bildschirmgrösse: Stand unsere Leiste, als diese Ausrichtung verlassen wurde? (siehe onScreenSizeChanged) */
    private val shownWhenLeft = HashMap<String, Boolean>()
    /** Mögliche neue Ruhelage mit höherer Unterkante und seit wann sie gemeldet wird (siehe rememberRestPosition). */

    private var higherRest: Rect? = null
    private var higherRestSince = 0L
    private var shorterRest: Rect? = null
    private var shorterRestSince = 0L
    /** Kamera mit X geschlossen (siehe onBarlessWindowGone). */
    private var xCloseAt = 0L
    private var xDelayed = false
    /** Fenster der Kamera nach Plus (meldet sich selbst, siehe onWindowSignal). -1 = keins. */
    private var cameraWindowId = -1
    /** YouTube ging nach hinten, während eine Seite ohne Leiste offen war (nur Tablet, siehe scanInternal). */
    private var leftWithoutBar = false
    /** Anderes YouTube-Fenster (z. B. Premium-Werbung) über der Seite mit der Leiste. -1 = keins. */
    private var coverWindowId = -1
    /** Höchste Lage eines offenen Panels im YouTube-Fenster (z. B. Werbe-Menü). MAX_VALUE = keins offen. */
    private var panelMinTop = Int.MAX_VALUE
    /** Das offene Panel selbst: wird jedes Bild nachgesehen (eine Abfrage), damit unsere beim Zugehen sofort steht. */
    private var panelNode: AccessibilityNodeInfo? = null
    /** Test-Versionen: Aufbau einer Seite mit Panel einmal speichern (fürs Protokoll "Ningelog"). */
    private var panelDumpedAt = 0L
    private var linkShortAt = 0L
    /** Unsere Leiste wurde vor dem Schliessen der Kamera schon gezeigt: kurz nicht wieder verstecken. */
    private var preShownAt = 0L
    private var lastFingerprintAt = 0L
    private var linkFixTries = 0

    // Shorts-Regale und automatisches "Nicht interessiert".
    private var shelves: List<Shelf> = emptyList()
    private var shelfMenuOpenedAt = 0L
    /** Das offene Menü gehört zu einer einzelnen Short-Karte (nicht zum ganzen Regal). */
    private var shelfMenuForCard = false
    private var cardClickFails = 0
    private var lastShelfHideAt = 0L
    private var shelfHideBroken = false
    private var homeTappedAt = 0L
    private var shelfMenuDoneAt = 0L
    private val shelfRetry = Runnable { scheduleScan() }

    private val scanRunnable = Runnable {
        scanPending = false
        // Unsere fährt gerade unter einen Dialog weg: erst fertig animieren lassen (ein Scan liesse sie ruckeln).
        // Ebenso, wenn sie gerade hereinfährt (Video verlassen) oder ihre Knöpfe wegsinken (Plus): Jede Abfrage an
        // YouTube hält unseren Haupt-Thread 50–300 ms auf, die Animation wurde dann gar nicht gezeichnet.
        if (overlay.isSliding || overlay.isRising || overlay.isSinking || overlay.isScrollMoving) {
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return@Runnable
        }
        // Handy: Die Liste bewegt sich (Wischen, gemessen im eigenen Thread). Eine volle Suche hielte den Haupt-Thread bis
        // 0,4 s an, so konnte unsere Leiste nicht mit YouTubes wegfahren (gemessen 19.11.: ~0,35 s zu spät).
        val holdLeft = scanHoldUntil - SystemClock.uptimeMillis()
        if (!isTablet && holdLeft > 0 && youTubeActive) {
            handler.postDelayed({ scheduleScan() }, holdLeft)
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
        val nowQuiet = SystemClock.uptimeMillis()
        // Video offen, unsere Leiste weg: Das Nachführen jedes Bildes (Poll, eine Abfrage) erkennt das Verlassen
        // ohnehin. Eine volle Suche kostet 150–340 ms und hielt, wenn sie gerade beim Wegwischen lief, alles auf
        // (3–4 Bilder echte Leiste mehr). Darum dann nur alle 3 s eine volle Suche.
        if (watchPageOpen && watchNode != null && !overlay.isShowing && polling && youTubeActive &&
            nowQuiet >= fastUntil && nowQuiet >= tapProbeUntil && nowQuiet >= watchRelearnUntil &&
            nowQuiet - lastScanAt < QUIET_FULL_SCAN_MS && screenBounds() == screen
        ) {
            handler.postDelayed({ scheduleScan() }, QUIET_FULL_SCAN_MS - (nowQuiet - lastScanAt))
            return@Runnable
        }
        // Handy, unsere beim Scrollen weggefahren und seither kein Tipp: keine volle Suche. Sie hielt den Haupt-Thread
        // 0,25–0,4 s auf, genau wenn die echte wiederkam (gemessen 15./16.11.: bis 16 Bilder echte Leiste). Die Rückkehr
        // merken die zwei Prüfungen im eigenen Thread (Richtung, startPresenceProbe). Nur wenn ein anderes Fenster vorne
        // ist (Fensterliste vom System, keine Abfrage an YouTube), doch suchen.
        // Ausser eine Tastatur ist aufgegangen (Tipp auf die Lupe galt sonst als Wischen, die Tastatur wurde nie bemerkt
        // und nach "Suchen" kam unsere ~15 Bilder zu spät, gemessen 17.11.).
        if (!isTablet && overlay.isScrolledAway && youTubeActive && lastNavAt < lastTouchAt && presenceRunning &&
            windows.let { w ->
                w.firstOrNull { it.isActive }?.id == barWindowId &&
                    w.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            }
        ) {

            handler.postDelayed({ scheduleScan() }, SCROLL_SCAN_RETRY_MS)
            return@Runnable
        }
        lastScanAt = nowQuiet
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
            val waitingForBar = (!overlay.isShowing && (lastBar != null || watchPageOpen)) || panelMinTop != Int.MAX_VALUE
            val probing = now < tapProbeUntil && !watchPageOpen
            // Nur zum Messen (Test-Versionen): setprop log.tag.FShortsMeasure VERBOSE → jedes Bild die Lage der echten
            // Leiste ins Protokoll und nie aufhören nachzusehen; log.tag.FShortsGhost VERBOSE → unsere unsichtbar.
            val measure = app.fshorts.BuildConfig.DEBUG && Log.isLoggable("FShortsMeasure", Log.VERBOSE)
            val g = app.fshorts.BuildConfig.DEBUG && Log.isLoggable("FShortsGhost", Log.VERBOSE)
            if (g != BottomBarOverlay.ghost) BottomBarOverlay.ghost = g
            val mt = app.fshorts.BuildConfig.DEBUG && Log.isLoggable("FShortsMirror", Log.VERBOSE)
            if (mt != BottomBarOverlay.mirrorTest) BottomBarOverlay.mirrorTest = mt


            if (measure && connected && youTubeActive) measureRealBar()
            // Unsere ist weggescrollt und die Richtung wird gerade im eigenen Thread geprüft (oder niemand berührt, dann
            // prüft startPresenceProbe): hier keine Abfragen (sie hielten den Haupt-Thread auf, das Hereinfahren kam
            // dann zu spät).
            if (overlay.isScrolledAway && presenceRunning) {
                handler.postDelayed(this, POLL_MS)
                return
            }
            if (!connected || !Access.unlocked || !youTubeActive ||
                (now > pollUntil && !waitingForBar && now > fastUntil && !probing && !measure && now >= dirUntil)

            ) {
                polling = false
                return
            }
            // Tablet: Unsere eben gezeigte Leiste ist noch nicht gezeichnet. Jede Abfrage an YouTube wartet dort bis
            // 200 ms und hielt das Zeichnen auf (Video verlassen: 0,3 s echte Leiste). Erst zeichnen lassen.
            if (drawPending()) {
                handler.postDelayed(this, POLL_MS)
                return
            }
            try {
                updateBar(now, null)
                if (now < fastUntil || probing) scheduleScan()
            } catch (e: Exception) {
                Log.w(TAG, "Nachführen fehlgeschlagen", e)
            }
            handler.postDelayed(this, if (watchPageOpen && now < paneBurstUntil) PANE_BURST_POLL_MS else POLL_MS)
        }
    }

    /** Nur zum Messen: Lage der echten Leiste jetzt (ohne etwas zu ändern). */
    private fun measureRealBar() {
        val t0 = SystemClock.uptimeMillis()
        val c = lastBar?.container
        val live = c?.let { scanner.liveTop(it, screen) }
        val desc = if (live != null) {
            "gemerkt top=$live"
        } else {
            val again = youTubeMainRoot()?.let { scanner.findBar(it, screen) }
            if (again != null) "neu gefunden top=${again.bounds.top} h=${again.bounds.height()}" else "keine"
        }
        Log.d(TAG, "Mess: $desc (${SystemClock.uptimeMillis() - t0} ms) unsere=${if (overlay.isShowing) "da" else "weg"}")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        selectors = Selectors.load(this)
        scanner = YouTubeScanner(selectors, resources.displayMetrics.density, isTablet)
        overlay = BottomBarOverlay(this, ::onTabTapped, ::onBarLongPressed)
        // YouTubes Hauptfenster vorne (nicht Kamera o. Ä. von YouTube darüber): Dann darf der Spiegel nicht ewig stehen.
        overlay.youTubeFront = { youTubeActive && barlessWindowId == -1 && cameraWindowId == -1 }


        dumper = TreeDumper(this)
        sampler = BarStyleSampler(this)
        touchWatcher = TouchWatcher(this, ::onTouchDown) {
            if (!touchedSinceEnter && youTubeActive) Log.d(TAG, "Berührung in YouTube")
            if (app.fshorts.BuildConfig.DEBUG && Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {
                Log.d(TAG, "Finger auf (Haupt-Thread ${SystemClock.uptimeMillis() - lastTouchAt} ms später)")
            }

            touchedSinceEnter = true
            if (shadeOverYouTube) onShadeTouched()
            if (app.fshorts.BuildConfig.DEBUG && youTubeActive && Log.isLoggable("FShortsLab", Log.VERBOSE)) startLab()
            if (DIAGNOSTIC_DUMPS && app.fshorts.BuildConfig.DEBUG) dumpAfterTapSoon(lastTouchAt)
            // Tipp auf ein Video in der Liste: YouTube zeigt die Video-Seite, meldet aber ~1 s gar nichts. Darum kurz
            // selbst laufend nachsehen (sonst stand unsere 0,5–1 s über der grauen Lade-Seite).
            if (youTubeActive && !watchPageOpen && lastTouchAt - watchClosedAt > TAP_AFTER_VIDEO_MS) {
                feedTouchAt = lastTouchAt
                tapProbeUntil = lastTouchAt + TAP_PROBE_MS
                startPolling()
            }
            // Tablet: Beim Runterwischen des Videos meldet YouTube nichts, bis der Mini-Player steht (~1 s echte
            // Leiste). Nach jeder Berührung darum Bild für Bild nachsehen, ob der Player wegrutscht.
            if (watchPageOpen && youTubeActive) startPolling()
            // Unter einem Dialog weggefahren: Jede Berührung kann ihn schliessen (Abbrechen, daneben, Zurück-Geste).
            // Das merkt der Scan erst ~0,3 s später, so lange sähe man die echte. Darum sofort abgedunkelt zurück.
            if (dialogSlid) bringBackFromSlide()
            // Tablet: Dialog vorne (z. B. Suchfilter), Berührung kann ihn schliessen. Bild für Bild nachsehen, sonst
            // blieb unsere ~0,3 s zu lange abgedunkelt.
            if (isTablet && dialogDimmed) {
                fastUntil = SystemClock.uptimeMillis() + DIALOG_CLOSE_FAST_MS
                startPolling()
            }
        }.also { it.start() }
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        menuDim = Prefs.menuDim(this)
        barModern = Prefs.barModern(this)
        Prefs.registerListener(this, prefsListener)
        loadBarState()

        // Fenster unserer Leiste schon jetzt anlegen (unsichtbar): Das erste Anlegen dauert ~0,5 s. Musste es erst beim
        // ersten Zeigen entstehen (z. B. App-Update, während ein Video lief, dann Zurück), sah man so lange die echte.
        val warmRest = restBounds
        val warmState = lastUiState
        if (warmRest != null && warmState != null) overlay.warmUp(warmRest, warmState.copy(style = currentStyle()))

        connected = true
        running = true
        scheduleScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!connected) return
        // Ohne Testzeit, Abo oder Code tut die App nichts (YouTube bleibt, wie es ist).
        if (!Access.unlocked) {
            if (overlay.isShowing) overlay.hide()
            return
        }
        val pkg = event.packageName?.toString()
        if (pkg == packageName) return
        if (sheetAppAt != 0L && pkg == PLAY_STORE_PKG && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.className?.toString() == "android.view.View" && SystemClock.uptimeMillis() - sheetAppAt > 40
        ) {
            sheetAppContentAt = SystemClock.uptimeMillis()
            Log.d(TAG, "Play: erster Inhalt nach ${sheetAppContentAt - sheetAppAt} ms")
            handler.removeCallbacks(sheetAppWatch)
            handler.post(sheetAppWatch)
        }
        if (app.fshorts.BuildConfig.DEBUG && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && pkg != selectors.youtubePackage &&
            pkg != SYSTEM_UI && Log.isLoggable(TRACE_TAG, Log.VERBOSE)
        ) {
            Log.d(TAG, "Inhalt $pkg w=${event.windowId} ch=${event.contentChangeTypes} ${event.className}")
        }
        // Nur zum Messen: auch YouTubes Inhalts-Meldungen (sehr viele). Einschalten: setprop log.tag.FShortsTraceYT VERBOSE
        if (app.fshorts.BuildConfig.DEBUG && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            pkg == selectors.youtubePackage && Log.isLoggable("FShortsTraceYT", Log.VERBOSE)
        ) {
            Log.d(TAG, "YT-Inhalt w=${event.windowId} ch=${event.contentChangeTypes} ${event.className} " +
                "\"${(event.contentDescription ?: event.text.firstOrNull())?.toString()?.take(24)}\"")
        }

        if (app.fshorts.BuildConfig.DEBUG && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {

            Log.d(TAG, "Scroll dy=${event.scrollDeltaY} ${event.className}")
        }
        if (app.fshorts.BuildConfig.DEBUG && event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && Log.isLoggable(TRACE_TAG, Log.VERBOSE) &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {

            Log.d(TAG, "Ereignis ${AccessibilityEvent.eventTypeToString(event.eventType)} $pkg w=${event.windowId} " +
                "ch=${event.contentChangeTypes} wch=${event.windowChanges} ${event.className} " +
                "\"${(event.contentDescription ?: event.text.firstOrNull())?.toString()?.take(30)}\"" +
                (if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                    windows.firstOrNull { it.id == event.windowId }?.let { w ->
                        " bounds=${Rect().also { w.getBoundsInScreen(it) }.toShortString()} aktiv=${w.isActive} typ=${w.type}"
                    } ?: " (nicht in Liste)"
                } else ""))

        }
        // Tipp in YouTube öffnet eine andere App (Werbung → Chrome Custom Tab): Deren Fenster ist sofort undurchsichtig.
        // Nicht auf den nächsten Scan warten (unsere stand sonst ~0,2 s über dem Tab).
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null &&
            pkg != selectors.youtubePackage && pkg != SYSTEM_UI && youTubeActive && overlay.isShowing &&
            !overlay.isSliding && splitBounds == null && event.className?.contains("Activity") == true &&
            SystemClock.uptimeMillis() - lastTouchAt < 1500
        ) {
            if (pkg == PLAY_STORE_PKG && !isTablet) dimForSheetApp() else {
                reason("Andere App geöffnet ($pkg), unsere sofort weg")
                hideAll(keepMirror = true)
            }
        }
        if (app.fshorts.BuildConfig.DEBUG && (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)
        ) {
            val label = (event.contentDescription ?: event.text.firstOrNull())?.toString()?.take(40)
            val kind = if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) "Klick" else "Seite"
            DebugLog.add("$kind $pkg w=${event.windowId} ch=${event.contentChangeTypes} ${event.className} \"$label\"")
            // Video aus der Liste geöffnet: YouTube meldet ch=32, während die graue Lade-Seite zu sehen ist, ~0,3 s
            // bevor wir den Player erkennen. Genau dann den Aufbau speichern (Test-Protokoll).
            val now = SystemClock.uptimeMillis()
            if (DIAGNOSTIC_DUMPS && pkg == selectors.youtubePackage && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED != 0 &&
                !watchPageOpen && now - lastPaneDumpAt > PANE_DUMP_INTERVAL_MS
            ) {
                lastPaneDumpAt = now
                handler.post {
                    DebugLog.add("Aufbau bei Seiten-Wechsel gespeichert")
                    youTubeMainRoot()?.let { r -> dumper.dump(r, windows, "seite-32") }
                }
            }
        }
        if (!isTablet) onWindowSignal(event, pkg)
        // Tablet: Unsere Leiste wartet aufs Zeichnen. YouTube schickt beim Seitenwechsel viele Meldungen, jede
        // Abfrage dazu hielt das Zeichnen ~0,4 s auf. Nur später nachsehen lassen.
        if (drawPending()) {
            if (pkg == selectors.youtubePackage) touchedSinceEnter = touchedSinceEnter || event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            scheduleScan()
            return
        }
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
                        // Ein Seitenwechsel zählt nur nach einem Tipp als "Navigation". YouTube meldet solche Wechsel
                        // auch beim Scrollen (z. B. ein Video in der Liste läuft an) – dann fuhr unsere Leiste runter.
                        if (looksLikeTap(SystemClock.uptimeMillis())) onNavigation()
                    }
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        if (closesBarlessPage(event)) showBeforeBarlessCloses(event)
                        else if (savesAndExitsCamera(event)) showAfterSaveExit()
                        // Lupe oben: Die Tastatur kommt gleich, gemeldet wird sie erst, wenn sie ganz da ist. Unsere
                        // fährt schon jetzt sanft weg (die Seite mit den Vorschlägen hat keine Leiste).
                        if (isSearchButton(event) && overlay.isShowing && !watchPageOpen) {
                            reason("Suche getippt, unsere fährt weg")
                            overlay.slideAway(SLIDE_AWAY_MS, SEARCH_HOLD_MS)
                        }
                        touchedSinceEnter = true
                        onNavigation()
                        // Ein Tipp kann einen Short öffnen: die nächsten 1,5 s ganz oft nachsehen, damit er sofort zugeht.
                        fastUntil = SystemClock.uptimeMillis() + FAST_WINDOW_MS
                        startPolling()
                    }
                    AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event.scrollDeltaY)
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

    /**
     * Handy: Fenster-Meldungen, auf die wir sofort reagieren, ohne YouTube etwas zu fragen (jede Abfrage kostet
     * 50–200 ms, in denen man sonst die echte Leiste sah). Gemessen 15.11. (Pixel 9, YouTube 21.45):
     * - Plus: ~100 ms nach dem Klick meldet sich die Kamera (CreationModesActivity, eigenes Fenster), ~50 ms später
     *   deckt sie die Leiste ab (sie fährt von unten hoch).
     * - Kamera zu (Zurück-Taste/-Geste): zuerst "Fenster entfernt", ~40 ms später meldet sich das Hauptfenster
     *   (MainActivity). Der Scan zeigte unsere erst ~130 ms nach der ersten Meldung: 4 Bilder echte Leiste.
     */
    private fun onWindowSignal(event: AccessibilityEvent, pkg: String?) {
        val now = SystemClock.uptimeMillis()
        val wid = event.windowId
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != selectors.youtubePackage || wid == -1 || barWindowId == -1) return
                val isActivity = event.className?.toString()?.endsWith("Activity") == true
                if (!isActivity || event.contentChangeTypes != 0) return
                if (wid != barWindowId) {
                    if (now - createTappedAt < CREATE_HOLD_MS) onCameraAnnounced(wid, now)
                } else if (wid != barlessWindowId) {
                    onBarlessWindowGone("Hauptfenster meldet sich")
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (sheetAppAt != 0L && event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0 &&
                    wid != barWindowId
                ) sheetAppDrawnAt = now
                if (sheetAppLeftAt != 0L && event.windowChanges and
                    (AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0
                ) onSheetAppMaybeClosed(now)
                if (event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED == 0 || wid == -1) return
                // Legt sich nur ein anderes Fenster darüber (z. B. Androids Fotos-Frage über der Kamera), meldet
                // Android die Kamera auch als "entfernt": Dann ist aber ein anderes App-Fenster in der Liste.
                if (wid == barlessWindowId && wid != barWindowId &&
                    windows.none { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.id != barWindowId }
                ) onBarlessWindowGone("Fenster entfernt")

            }
        }
    }

    /** Plus getippt und die Kamera meldet sich: Dann ist sie schon über der Leiste (sie fährt von unten hoch): unsere weg. */
    private fun onCameraAnnounced(windowId: Int, now: Long) {
        if (cameraWindowId == windowId) return
        cameraWindowId = windowId
        val tappedAt = createTappedAt
        handler.postDelayed({
            if (createTappedAt != tappedAt || cameraWindowId != windowId || !overlay.isShowing || !overlay.isSunk) return@postDelayed
            reason("Kamera deckt die Leiste ab, unsere weg")
            lastBar = null
            lastObservedTop = -1
            barlessWindowId = windowId
            // Spiegel bleibt unter der Kamera (normal, ohne gedrehtes Plus): Fährt sie wieder runter, sieht man darunter
            // unsere statt der echten.
            overlay.hideKeepMirror(showMirror = true)
            scheduleScan()
        }, CAMERA_COVER_MS)
    }

    /**
     * Das Fenster ohne Leiste (Kamera) geht zu, darunter liegt die Seite mit der Leiste: unsere sofort zeigen, noch vor
     * jeder Abfrage. Die Schliess-Animation zeigt sonst die echte Leiste (sie fährt mit der Seite seitlich herein).
     */
    private fun onBarlessWindowGone(why: String) {
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        if (barlessWindowId == -1 || barlessWindowId == barWindowId || watchPageOpen || overlay.isShowing) return
        if (!youTubeActive || menuFront || menuOverBarless) return
        // Mit X geschlossen: Android meldet das Fenster sofort als entfernt, die Kamera ist aber noch ~130 ms ganz zu
        // sehen (gemessen 15.11.). Etwas später zeigen, sonst stand unsere ~8 Bilder über der Kamera.
        if (SystemClock.uptimeMillis() - xCloseAt < X_CLOSE_WINDOW_MS && !xDelayed) {
            xDelayed = true
            handler.postDelayed({
                onBarlessWindowGone(why)
                xDelayed = false
                xCloseAt = 0L
            }, X_CLOSE_DELAY_MS)
            return
        }
        xDelayed = false
        xCloseAt = 0L
        // Der Spiegel liegt unter der Kamera (an YouTubes Fenster geheftet): Er zeigt unsere Leiste, während die Kamera
        // wegfährt. Unsere eigene kommt erst, wenn YouTubes Fenster wieder vorne ist (vorher stand sie ~4 Bilder über der
        // noch sichtbaren Kamera).
        if (overlay.mirrorCovering) {
            reason("Seite ohne Leiste geht zu ($why), Spiegel liegt darunter")
            preShownAt = SystemClock.uptimeMillis()
            hiddenByScroll = false
            continueScanSoon()
            return
        }
        reason("Seite ohne Leiste geht zu ($why), unsere vorab zeigen")

        preShownAt = SystemClock.uptimeMillis()
        hiddenByScroll = false
        // Die Kamera ist noch ~4 Bilder ganz zu sehen, dann blendet die Seite mit der echten Leiste ein. Unsere fährt
        // in dieser Zeit ein kurzes Stück von unten herein (statt über der Kamera aufzuspringen) und steht rechtzeitig.
        overlay.showRising(rest, state.copy(style = currentStyle()), CLOSE_RISE_DP * resources.displayMetrics.density, RISE_MS)
        continueScanSoon()
    }

    /**
     * Werbung getippt, Google Play öffnet sich als halb durchsichtiges Fenster über YouTube (gemessen 15.11.): YouTube
     * wird ~0,18 s lang abgedunkelt (auf ~50 %), dann fährt das Play-Blatt von unten hoch. Vorher war unsere sofort weg
     * und man sah so lange die echte Leiste (abgedunkelt). Jetzt dunkelt unsere mit (Tinte wie beim ⋮-Menü) und
     * verschwindet erst, wenn das Blatt über ihr ist.
     */
    private fun dimForSheetApp() {
        // Schon dran (abdunkeln läuft, blendet aus oder Blatt liegt schon darüber): nichts neu anfangen.
        if (sheetAppAt != 0L || overlay.isFading || SystemClock.uptimeMillis() - sheetAppLeftAt < SHEET_APP_REPEAT_MS) return
        val rest = restBounds

        val state = lastUiState
        if (rest == null || state == null || !overlay.isShowing) {
            reason("Andere App geöffnet ($PLAY_STORE_PKG), unsere sofort weg")
            hideAll(keepMirror = true)
            return
        }
        sheetAppAt = SystemClock.uptimeMillis()
        reason("Google Play öffnet sich über YouTube, unsere dunkelt mit ab")
        val cx = rest.centerX()
        overlay.showUnderMenu(rest, state.copy(style = currentStyle()), SHEET_APP_DIM, Rect(cx, rest.top, cx, rest.top), SHEET_APP_INK_MS)
        handler.removeCallbacks(sheetAppWatch)
        handler.postDelayed(sheetAppWatch, POLL_MS)
    }

    private val sheetAppWatch = object : Runnable {
        override fun run() {
            if (sheetAppAt == 0L) return
            if (!overlay.isShowing) {
                sheetAppAt = 0L
                return
            }
            val now = SystemClock.uptimeMillis()
            val rest = restBounds
            // Bestes Zeichen (gemessen 15.11.): Plays erste Inhalts-Meldung (View) nach dem Öffnen kommt mit dem ersten
            // gezeichneten Bild (Lade-Kreis), ein Bild später blendet das Blatt ein. Dann blendet unsere in
            // SHEET_APP_FADE_MS aus (Überblendung mit dem Blatt). Sonst: Startfenster entfernt, notfalls nach
            // SHEET_APP_COVER_MS. Keine Abfragen an Plays Oberfläche hier: Die kosteten pro Bild bis ~50 ms und
            // hielten unser Zeichnen auf (Abdunkeln kaum zu sehen, Ausblenden ~6 Bilder zu spät).
            val covered = rest != null && (sheetAppContentAt >= sheetAppAt || sheetAppDrawnAt >= sheetAppAt ||
                now - sheetAppAt >= SHEET_APP_COVER_MS)
            if (covered || now - sheetAppAt > SHEET_APP_MAX_MS) {
                reason(if (covered) "Play-Blatt über der Leiste, unsere blendet aus (${now - sheetAppAt} ms)" else "Play-Blatt kam nicht, unsere weg")
                sheetAppAt = 0L
                sheetAppLeftAt = now
                // Unter dem Play-Blatt liegt YouTube mit seiner Leiste: dort den Spiegel (Play dunkelt ihn selbst ab).
                overlay.hideKeepMirror(showMirror = true)
                scheduleScan()
                return
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    /**
     * Google-Play-Blatt (nach Werbe-Tipp) wird geschlossen: Es verschwindet in einem Bild, darunter steht YouTube
     * sofort wieder hell mit der echten Leiste. Der Scan zeigte unsere erst ~60 ms später (5 Bilder echte Leiste,
     * gemessen 15.11.). Erstes Zeichen: Play-Fenster verliert "aktiv" bzw. wird entfernt.
     */
    private fun onSheetAppMaybeClosed(now: Long) {
        if (now - sheetAppLeftAt > SHEET_APP_RETURN_MS) {
            sheetAppLeftAt = 0L
            return
        }
        val all = windows
        if (Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {
            Log.d(TAG, "Play zu? Fenster: " + all.joinToString { "${it.id}:${it.root?.packageName}:${if (it.isActive) "A" else ""}" } + " bar=$barWindowId")
        }
        if (all.any { it.isActive && it.root?.packageName?.toString() == PLAY_STORE_PKG }) return
        val main = all.firstOrNull { it.id == barWindowId } ?: return

        if (main.root?.packageName?.toString() != selectors.youtubePackage) return
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        sheetAppLeftAt = 0L
        if (overlay.isShowing || watchPageOpen) return
        reason("Google Play geht zu, unsere sofort")
        youTubeActive = true
        youTubeSince = now
        preShownAt = now
        hiddenByScroll = false
        overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        continueScanSoon()
    }

    /** Liegt ein Teil des Play-Blatts (nicht das bildschirmgrosse, durchsichtige Fenster selbst) über unserer Leiste? */
    private fun sheetOverBar(root: AccessibilityNodeInfo, rest: Rect): Boolean {
        val display = displayBounds()
        val r = Rect()
        var checked = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (checked++ > 400 || depth > 25) return false
            if (depth > 0 && node.isVisibleToUser) {
                node.getBoundsInScreen(r)
                val fullScreen = r.height() >= display.height() - 4 * resources.displayMetrics.density * 30
                if (!fullScreen && !r.isEmpty && Rect.intersects(r, rest)) {
                    if (Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {
                        Log.d(TAG, "Play-Knoten über Leiste: ${node.className} ${node.viewIdResourceName} ${r.toShortString()} " +
                            "tiefe=$depth alpha? text=${(node.text ?: node.contentDescription)?.toString()?.take(20)}")
                    }
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                val c = node.getChild(i) ?: continue
                if (walk(c, depth + 1)) return true
            }
            return false
        }
        return walk(root, 0)
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
        // Quer zeigt YouTube (21.45, Handy) gar keine Leiste. Zurück im Hochformat steht sie sofort wieder da: Stand
        // unsere dort, bevor gedreht wurde, kommt sie gleich an die bekannte Stelle (sonst ~5 Bilder echte Leiste,
        // bis der Scan sie findet).
        // (Kurz vorher zählt auch: Oft merkt das Nachführen "Leiste weg" ein paar ms, bevor die neue Grösse da ist.)
        shownWhenLeft[sizeKey(screen)] = wasShowing || SystemClock.uptimeMillis() - ourShownAt < 500

        val wasShownHere = shownWhenLeft[sizeKey(newScreen)] == true && youTubeActive
        Log.d(TAG, "Gedreht: ${newScreen.width()}x${newScreen.height()}, Lage bekannt=${known != null}, zeigte=$wasShowing/$wasShownHere, Video=$watchPageOpen")
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
            if ((wasShowing || wasShownHere) && !watchPageOpen) {
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

    private fun drawPending(): Boolean = overlay.isSlidingAway || overlay.isRising || overlay.isSinking || overlay.isScrollMoving || (overlay.isShowing &&
        overlay.drawnAt < overlay.shownAt &&
        SystemClock.uptimeMillis() - overlay.shownAt < if (isTablet) TABLET_DRAW_WAIT_MS else PHONE_DRAW_WAIT_MS)

    private fun snapToEdges(r: Rect): Rect {
        val s = screenBounds()
        val out = Rect(r)
        if (kotlin.math.abs(out.left - s.left) <= EDGE_SNAP_PX) out.left = s.left
        if (kotlin.math.abs(out.right - s.right) <= EDGE_SNAP_PX) out.right = s.right
        // Unten grosszügiger: YouTube meldet seine Leiste beim Zurückfahren (Scrollen nach oben) manchmal ein paar
        // Pixel zu hoch. So gemerkt, stand unsere bis zu ~12 px über dem Rand, darunter scrollte der Inhalt durch.
        // Die echte Leiste reicht bei Gesten-Navigation immer bis ganz unten; mit 3 Knöpfen liegt sie 48 dp höher
        // (über der Knopfleiste) und bleibt darum unberührt.
        val bottomSnap = (BOTTOM_SNAP_DP * resources.displayMetrics.density).toInt()
        val gap = s.bottom - out.bottom
        if (gap in -EDGE_SNAP_PX..bottomSnap) out.offset(0, gap)
        return out
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
        if (!node.refresh()) {
            // Tablet: Beim Drehen baut YouTube den Player neu (quer zwei Spalten). Neu suchen statt "verlassen",
            // sonst stand unsere ~0,3 s über dem Video. Nach Zurück ist keiner mehr da.
            // Handy genauso: Beim Wechsel Werbung → Video kann der Player neu gebaut werden.

            val again = youTubeMainRoot()?.let { scanner.findWatchPlayer(it, screen) }
                // null = gerade gedreht, YouTube hat den Player neu gebaut: der volle Scan sucht ihn neu.
                ?: return if (now < watchRelearnUntil) null else false
            watchNode = again
            watchTop = scanner.topOf(again)
            return true
        }
        // Ab hier keine weitere Abfrage: Jede kostet 20–50 ms, während YouTube die Seite wegschiebt (vorher drei
        // pro Bild – beim schnellen Runterwischen kam "Video verlassen" so ~0,1 s zu spät, 1–3 Bilder echte Leiste).
        val b = Rect().also { node.getBoundsInScreen(it) }
        if (kotlin.math.abs(b.width() - screen.width()) > 4 && kotlin.math.abs(b.width() - screen.height()) <= 4) {
            watchRelearnUntil = now + WATCH_RELEARN_MS
        }
        if (now < watchRelearnUntil) {
            // Während der Dreh-Animation meldet YouTube unsinnige Lagen (z. B. -740): nur sinnvolle lernen.
            if (b.top >= 0 && b.top < screen.height() * 0.35f) watchTop = b.top
            return true
        }
        return scanner.isStillLargePlayerFresh(node, watchTop, screen)
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
    private fun onScrolled(dy: Int = 0) {
        lastScrollAt = SystemClock.uptimeMillis()
        if (dy < 0) lastUpScrollAt = lastScrollAt
        // Hochgescrollt, unsere ist weggescrollt: Ob YouTube seine Leiste zeigt, hängt vom Tempo ab – das misst
        // startDirectionProbe. Nur wenn dort nichts misst (keine Berührung bekannt, z. B. Tastatur/Maus) und die Liste in
        // einer Meldung weit gerutscht ist, kommt unsere hier.
        if (dy < 0 && overlay.isScrolledAway && youTubeActive &&
            SystemClock.uptimeMillis() > probeUntil && -dy > SCROLL_EVENT_FAST_DP * resources.displayMetrics.density
        ) {
            realGoneByScroll = false
            reason("Hochgescrollt ohne Messung ($dy px), unsere fährt herein")
            overlay.scrollRise(RISE_NO_PROBE_PHASE_MS)
        }
        // Weggescrollt: Die Rückkehr merken die Hintergrund-Prüfungen; eine Abfrage hier hielt beim Wischen alles auf.
        if (youTubeActive && !(overlay.isScrolledAway && presenceRunning)) updateBar(SystemClock.uptimeMillis(), null)
        startPolling()
    }

    /**
     * Finger liegt auf (Touch-Thread, mit der echten Zeit der Berührung): Wischrichtung und -tempo messen, wenn es für
     * unsere Leiste zählt. Drei Fälle:
     * - unsere ist weggescrollt: kommt sie (wie YouTubes) wieder? ([PROBE_UP])
     * - unsere steht, die echte ist schon weg: fährt sie beim schnellen Runterwischen weg? ([PROBE_DOWN_GONE])
     * - unsere und die echte stehen, auf einer Seite, wo YouTube beim Scrollen ausblendet: unsere fährt beim schnellen
     *   Runterwischen gleichzeitig mit YouTubes weg ([PROBE_DOWN_EARLY]). Vorher kam das erst über die Abfrage der echten,
     *   ~0,35 s zu spät (gemessen 19.11.).
     */
    private fun onTouchDown(downAt: Long) {
        lastTouchAt = downAt
        if (!connected || isTablet || !youTubeActive || !::overlay.isInitialized) return
        if (overlay.isScrolledAway) {
            startDirectionProbe(PROBE_UP)
        } else if (overlay.isShowing && hiddenByScroll && realGoneSince != 0L && scrollFollowPage()) {
            // Unsere steht, die echte ist weggescrollt (z. B. vorher langsam hochgescrollt): Wischt der Finger jetzt
            // schnell runter, fährt unsere weg wie YouTube seine.
            startDirectionProbe(PROBE_DOWN_GONE)
        } else if (overlay.isShowing && !hiddenByScroll && scrollFollowPage()) {
            // Hier blendet YouTube beim Scrollen aus (schon gesehen): unsere fährt beim schnellen Runterwischen gleich mit.
            // Sonst (noch nicht gesehen, Mein YouTube, Suchergebnisse) nur beobachten: Bewegt sich die Liste, keine volle
            // Suche (sie hielte das Mitfahren nach der Abfrage der echten an).
            val early = hidesFeed != null && lastUiState?.selected != TabKind.YOU
            startDirectionProbe(if (early) PROBE_DOWN_EARLY else PROBE_OBSERVE)
            // Gleich keine volle Suche starten: Wird gewischt, hielte sie unsere Animation an. Bewegt sich die Liste nach
            // TAP_RELEASE_MS nicht (Tipp), gibt die Messung das wieder frei.
            if (early) scanHoldUntil = maxOf(scanHoldUntil, downAt + TOUCH_SCAN_HOLD_MS)
        }
    }

    /**
     * Wie YouTube 21.45 seine Leiste beim Scrollen bewegt (gemessen 19.11.2026, Pixel 9, Bild für Bild mit unsichtbarer
     * eigener Leiste, `input swipe` mit festen Tempi, Startseite und Abos):
     * - Entscheidung pro Bild: Rutscht die Liste in einem Bild um mehr als den Touch-Slop (8 dp = 23 px), fährt die Leiste
     *   weg (runter) bzw. kommt (hoch). 1,0 px/ms (16–22 px pro Bild) nie, 1,2 px/ms nur wenn ein Bild ≥ 24 px springt,
     *   ab ~1,45 px/ms immer. Langsames Ziehen bewegt sie gar nicht: Sie folgt nie dem Finger, sie spielt eine feste
     *   Animation ab (immer gleich, egal wie schnell).
     * - Wegfahren beginnt 1 Bild nach dem auslösenden Bild, ~100–125 ms lang. Hereinfahren beginnt ~62 ms danach (im
     *   ersten Bild ganz unten), ab ~116 ms sind die Knöpfe zu sehen.
     * Meldungen von YouTube kommen dafür zu spät (>100 ms, gedrosselt), darum misst ein eigener Thread laufend die Lage
     * eines Listen-Elements.
     */
    @Synchronized
    private fun startDirectionProbe(mode: Int = PROBE_UP) {
        val touch = lastTouchAt
        dirUntil = touch + DIR_PROBE_MS
        probeUntil = touch + DIR_MAX_MS
        val gen = ++probeGen
        val feed = when (mode) {
            PROBE_DOWN_EARLY -> hidesFeed
            PROBE_OBSERVE -> null
            else -> goneFeed ?: probeFeed
        }
        val scr = Rect(screen)
        val trace = app.fshorts.BuildConfig.DEBUG && Log.isLoggable(TRACE_TAG, Log.VERBOSE)
        probeHandler.post { runDirectionProbe(gen, feed, scr, trace, mode) }
    }

    /**
     * Läuft im eigenen Thread (alle [DIR_PROBE_STEP_MS]): Der Haupt-Thread war während des Wischens oft 50–120 ms mit
     * Abfragen beschäftigt, so kam die Erkennung zu spät (gemessen 15.11.: bis 14 Bilder echte Leiste).
     * Danach (Liste steht) gleich ein Element in der Mitte für das nächste Fingerauflegen bereitlegen: Das Suchen
     * dauerte beim Wischen bis 330 ms, so lange sah man die hochfahrende echte (gemessen 17.11.: 15 Bilder).
     */
    private fun runDirectionProbe(gen: Int, feedIn: AccessibilityNodeInfo?, scr: Rect, trace: Boolean, mode: Int) {
        probeReady = false
        val last = probeLoop(gen, feedIn, scr, trace, mode)
        probeReady = false
        if (gen != probeGen) return
        val r = Rect()
        fun inMiddle(c: AccessibilityNodeInfo?) = c != null && c.refresh() && c.isVisibleToUser && run {
            c.getBoundsInScreen(r)
            r.height() > 0 && r.top > scr.top + scr.height() / 8 && r.top < scr.bottom - scr.height() / 4
        }
        if (mode == PROBE_OBSERVE) return
        if (mode == PROBE_DOWN_EARLY) {
            earlyChild = if (inMiddle(last)) last else feedIn?.takeIf { it.refresh() }?.let { scanner.pickFeedChild(it, scr) }
            return
        }
        if (mode != PROBE_UP || !overlay.isScrolledAway) return
        probeChild = if (inMiddle(last)) last else {
            val feed = feedIn?.takeIf { it.refresh() } ?: youTubeMainRoot()?.let { scanner.findFeedQuick(it, scr) }
            feed?.let { scanner.pickFeedChild(it, scr) }
        }
        if (trace) Log.d(TAG, "Richtung: Element für nächstes Mal ${if (probeChild != null) "bereit" else "nicht gefunden"}")
    }

    /** Die eigentliche Messung (siehe [startDirectionProbe]). Gibt das zuletzt gemessene Listen-Element zurück. */
    private fun probeLoop(gen: Int, feedIn: AccessibilityNodeInfo?, scr: Rect, trace: Boolean, mode: Int): AccessibilityNodeInfo? {
        val down = mode != PROBE_UP
        val early = mode == PROBE_DOWN_EARLY
        // Nur beobachten (unsere steht, YouTube blendet hier vielleicht nicht aus): nichts auslösen, nur bei Bewegung die
        // volle Suche anhalten.
        val observe = mode == PROBE_OBSERVE
        var tapReleased = false
        var stillSamples = 0
        val r = Rect()
        val density = resources.displayMetrics.density
        fun pick(): Pair<AccessibilityNodeInfo, Int>? {
            // Früh-Modus nur mit der gemerkten Liste (dort blendet YouTube aus), nie mit einer anderen.
            val feed = if (early) {
                feedIn?.takeIf { it.refresh() && it.isVisibleToUser } ?: return null
            } else {
                feedIn?.takeIf { it.refresh() } ?: youTubeMainRoot()?.let { scanner.findFeedQuick(it, scr) } ?: return null
            }
            val c = scanner.pickFeedChild(feed, scr) ?: return null
            c.getBoundsInScreen(r)
            return c to r.top
        }
        // Das gemerkte Element nur nehmen, wenn es noch sichtbar in der Mitte steht: Ist es beim Weiterscrollen aus dem
        // Bild gerutscht, bewegt es sich nicht mehr (gemessen 16.11.: bis 250 ms "0 px", obwohl die Liste lief).
        var picked: Pair<AccessibilityNodeInfo, Int>? = (if (early) earlyChild else probeChild)?.takeIf { it.refresh() && it.isVisibleToUser }?.let { c ->
            c.getBoundsInScreen(r)
            if (r.height() > 0 && r.top > scr.top + scr.height() / 8 && r.top < scr.bottom - scr.height() / 4) c to r.top else null
        }
        val minDist = DIR_PROBE_DP * density
        val riseSpeed = DIR_SPEED_DP_MS * density
        val hideSpeed = HIDE_SPEED_DP_MS * density
        // Verlauf: Zeit (Mitte der Abfrage) und Lage des Inhalts seit dem Fingerauflegen (+ = nach unten = hochwischen).
        val ts = LongArray(DIR_SAMPLES)
        val ps = IntArray(DIR_SAMPLES)
        var n = 0
        var pos = 0
        var lastMoveAt = SystemClock.uptimeMillis()
        var lastBarCheck = 0L
        var lastQueryAt = 0L
        var anyMove = false
        // Schwung in die "falsche" Richtung nach dem Loslassen: nur noch selten nachsehen, bis die Liste steht.
        var passive = false
        var confirmAt = 0L
        var reversed = false
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var prevT = 0L
        var firstUpAt = 0L
        // Letzte Messung, bei der die Liste (in Wischrichtung) noch stand: frühester möglicher Auslöser bei YouTube.
        var stillAt = lastTouchAt
        /**
         * [phaseMs]: so viele ms ist YouTubes Animation (geschätzt) zum Zeitpunkt [at] schon unterwegs. Beim Ausführen
         * (Haupt-Thread, evtl. später) wird die seither vergangene Zeit dazugezählt.
         */
        fun post(why: String, rise: Boolean, phaseMs: Long, at: Long) {
            handler.postAtFrontOfQueue {
                if (gen != probeGen) return@postAtFrontOfQueue
                dirUntil = 0L
                val phase = phaseMs + (SystemClock.uptimeMillis() - at)
                if (down) {
                    if (overlay.isScrolledAway || !overlay.isShowing || !scrollFollowPage()) return@postAtFrontOfQueue
                    if (!early && realGoneSince == 0L) return@postAtFrontOfQueue
                    if (early) earlyAwayAt = SystemClock.uptimeMillis()
                    realGoneByScroll = true
                    reason("$why (Phase $phase ms)")
                    followAway(phase)
                    return@postAtFrontOfQueue
                }
                if (!overlay.isScrolledAway) return@postAtFrontOfQueue
                lastUpScrollAt = SystemClock.uptimeMillis()
                realGoneByScroll = false
                reason("$why (Phase $phase ms)")
                if (rise) overlay.scrollRise(phase) else overlay.scrollBackNow()
                if (trace) {
                    Log.d(TAG, "Zustand: ${overlay.debugState()}")
                    handler.postDelayed({ Log.d(TAG, "Zustand +100: ${overlay.debugState()}") }, 100)
                }
            }
        }
        // Noch kein Element (Liste wird gesucht): Zwischendurch direkt nachsehen, ob die echte schon kommt.
        // Direkt nach der echten sehen. Sie "kommt" nur, wenn sie steigt oder schon ganz oben steht: Gleich nach dem Wegfahren
        // unserer ist die echte noch unterwegs nach unten (gemessen 18.11.: das holte unsere fälschlich zurück).
        var prevBarTop = Int.MAX_VALUE
        val rest = restBounds
        fun barCheck(now: Long, why: String): Boolean {
            // Eben war sie sichtbar (aber nicht steigend): gleich nochmals nachsehen, sonst seltener.
            val every = if (prevBarTop < scr.bottom - 2) DIR_PROBE_STEP_MS * 2 else DIR_BAR_CHECK_MS
            if (down || now - lastTouchAt < DIR_BAR_FIRST_MS || now - lastBarCheck < every) return false
            lastBarCheck = now
            val realTop = realBarTopQuick(scr)
            val prev = prevBarTop
            prevBarTop = realTop ?: Int.MAX_VALUE
            if (realTop == null || realTop >= scr.bottom - 2) return false
            // Gleich nach unserem frühen Wegfahren steht die echte noch (ihre Animation beginnt erst): keine Rückkehr.
            val atRest = rest != null && realTop <= rest.top + SCROLL_OUT_PX && !inEarlyGrace(now)
            val rising = prev != Int.MAX_VALUE && realTop < prev - 2
            if (!atRest && !rising) return false
            if (rest != null && realTop > rest.top + 1) {
                post("echte kommt (top=$realTop, $why), unsere an ihrer Stelle", rise = true, risePhaseForTop(realTop, rest), SystemClock.uptimeMillis())
            } else {
                post("echte steht (top=$realTop, $why), unsere sofort", rise = false, 0L, SystemClock.uptimeMillis())
            }
            return true
        }
        while (gen == probeGen) {
            val now = SystemClock.uptimeMillis()
            // Fertig: nach der Anfangszeit, sobald sich nichts mehr bewegt (Finger weg, Liste steht), spätestens nach
            // DIR_MAX_MS. Ein neues Fingerauflegen startet neu. Früh-Modus: ohne Bewegung bald aufhören (Tipp, nicht Wischen).
            if (now > probeUntil || (now - lastTouchAt > DIR_PROBE_MS && now - lastMoveAt > DIR_STILL_MS)) return picked?.first
            if ((early || observe) && !anyMove && now - lastTouchAt > EARLY_IDLE_MS) return picked?.first
            // Liste bewegt sich nicht (Tipp): die Pause der vollen Suche wieder freigeben (ein Tipp soll sofort wirken, z. B.
            // Short schliessen).
            if ((early || observe) && !anyMove && !tapReleased && stillSamples >= 2 && now - lastTouchAt >= TAP_RELEASE_MS) {
                tapReleased = true
                val hold = scanHoldUntil
                if (hold > now && hold <= lastTouchAt + TOUCH_SCAN_HOLD_MS) {
                    scanHoldUntil = 0L
                    handler.post { scheduleScan() }
                }
            }
            if (passive && now - lastQueryAt < DIR_PASSIVE_STEP_MS) {
                SystemClock.sleep(DIR_PROBE_STEP_MS)
                continue
            }
            lastQueryAt = now
            val p = picked
            if (p == null || !p.first.refresh() || !p.first.isVisibleToUser) {
                probeReady = false
                if (barCheck(now, "Liste noch nicht gemessen")) return null
                picked = pick()
                if (early && picked == null) return null
                probeReady = picked != null
                SystemClock.sleep(DIR_PROBE_STEP_MS)
                continue
            }
            probeReady = true
            p.first.getBoundsInScreen(r)
            val t = (now + SystemClock.uptimeMillis()) / 2
            val moved = r.top - p.second
            if (kotlin.math.abs(moved) > scr.height() / 2) {
                // Element wurde für einen anderen Eintrag wiederverwendet – oder die Liste ist weit gerutscht, weil
                // YouTube lange nicht antwortete (quer: niedriger Bildschirm, gemessen 17.11.: 164 ms, dann nur noch
                // "0 px" und 4 Bilder echte Leiste). Dann direkt nachsehen, ob die echte gerade hochfährt.
                if (!down && moved > 0) {
                    val realTop = realBarTopQuick(scr)
                    if (realTop != null && realTop < scr.bottom - 2 && rest != null) {
                        post("Hochwischen erkannt (Liste weit gerutscht, echte bei $realTop)", rise = true, risePhaseForTop(realTop, rest), SystemClock.uptimeMillis())
                        return null
                    }
                }
                picked = p.first to r.top
                n = 0
                lastMoveAt = t
                SystemClock.sleep(DIR_PROBE_STEP_MS)
                continue
            }
            picked = p.first to r.top
            pos += moved
            if (moved != 0) {
                lastMoveAt = t
                anyMove = true
                // Die Liste bewegt sich (Wischen): keine volle Suche, sie hielte den Haupt-Thread (und damit unsere
                // Animation) bis 0,4 s an.
                scanHoldUntil = t + SCAN_HOLD_SCROLL_MS
            } else if (pos == 0) {
                stillAt = t
                // Nur echte Messungen zählen (die erste dauert oft ~70 ms): erst dann gilt "Liste steht" als Tipp.
                if (t - lastTouchAt >= TAP_RELEASE_MS - 15) stillSamples++
            }
            // Noch keine Bewegung gesehen: Vielleicht ist das Element veraltet (z. B. nach dem Drehen, gemessen 17.11.: 26 Bilder
            // echte Leiste quer) und bewegt sich gar nicht mit. Zur Sicherheit direkt nach der echten sehen.
            if (!down && pos == 0 && barCheck(now, "Liste steht")) return null
            if (passive) {
                SystemClock.sleep(DIR_PROBE_STEP_MS)
                continue
            }
            if (moved > 0 && firstUpAt == 0L) firstUpAt = now
            if (moved < 0) firstUpAt = 0L
            val dt = if (prevT == 0L) DIR_PROBE_STEP_MS else (t - prevT).coerceAtLeast(1L)
            prevT = t
            // Ein Sprung wartet auf Bestätigung: Läuft die Liste in diesem Schritt nicht auch schnell weiter, war es keiner.
            if (confirmAt != 0L && moved < dt * riseSpeed * 0.5f) confirmAt = 0L
            if (n == DIR_SAMPLES) {
                System.arraycopy(ts, 1, ts, 0, n - 1)
                System.arraycopy(ps, 1, ps, 0, n - 1)
                n--
            }
            ts[n] = t
            ps[n] = pos
            n++
            // Geschwindigkeit über die letzten ~50 ms (mindestens DIR_SPEED_MIN_DT_MS, sonst ist sie zu ungenau: der
            // Inhalt springt nur einmal pro Bild weiter).
            var ref = n - 2
            while (ref > 0 && t - ts[ref - 1] <= DIR_SPEED_WINDOW_MS) ref--
            while (ref > 0 && t - ts[ref] < DIR_SPEED_MIN_DT_MS) ref--
            val winOk = ref >= 0 && t - ts[ref] >= DIR_SPEED_MIN_DT_MS
            val vWin = if (ref >= 0 && t > ts[ref]) (pos - ps[ref]).toFloat() / (t - ts[ref]) else 0f
            // Am Anfang ist das Fenster noch kurz: Solange es seit dem Fingerauflegen nur in eine Richtung ging, zählt auch
            // das Tempo des Fingers (Weg der Liste + Touch-Slop, die Liste läuft erst danach los). Die Zeit der Berührung
            // ist jetzt genau (Ereigniszeit aus dem Touch-Thread).
            if (if (down) moved > 0 else moved < 0) reversed = true
            val vStart = if (!reversed && pos != 0 && t > lastTouchAt) (pos + if (pos > 0) slop else -slop) / (t - lastTouchAt) else 0f
            // Entschieden wird nur mit vollem Fenster: Die Schätzung über den Fingerweg lag am Anfang oft zu hoch (gemessen 19.11.:
            // 1,0 px/ms als 1,62 px/ms, unsere kam, YouTubes nicht). vStart nur fürs Protokoll.
            val v = if (winOk) vWin else 0f
            if (trace && moved != 0) Log.d(TAG, "Richtung${if (early) " (früh)" else ""}: $pos px, v=${"%.2f".format(v)} px/ms (Start ${"%.2f".format(vStart)}), ${t - lastTouchAt} ms nach Finger")
            // Nach der Anfangszeit läuft die Liste in die "falsche" Richtung weiter (Schwung nach dem Loslassen): Das kehrt
            // nicht mehr um, also nur noch selten nachsehen, bis sie steht.
            if (now - lastTouchAt > DIR_PROBE_MS && (if (down) v > 0f else v < 0f)) {
                passive = true
                probeReady = false
                continue
            }
            if (down) {
                if (!observe && -v >= hideSpeed && -pos > slop) {
                    // Wegfahren: YouTube hat schon mit dem auslösenden Bild begonnen. Unsere beginnt bei der neusten Messung
                    // (eher etwas später: fährt sie früher als die echte, sähe man diese darüber).
                    post(if (early) "Runterwischen schnell (${"%.2f".format(-v)} px/ms), unsere fährt mit der echten weg"
                        else "Runterwischen schnell (${"%.2f".format(-v)} px/ms), echte schon weg, unsere fährt runter",
                        rise = false, 0L, t)
                    return null
                }
            } else if (v >= riseSpeed && moved > 0 && pos > minDist) {
                // War bisher langsam und springt plötzlich (z. B. ein Element ändert die Grösse, gemessen 17.11.: 175 px in
                // einem Schritt, YouTube zeigte nichts): erst glauben, wenn auch die nächste Messung schnell weiterläuft.
                val wasSlow = firstUpAt != 0L && now - firstUpAt > DIR_CONFIRM_AFTER_MS
                if (wasSlow && confirmAt == 0L) {
                    confirmAt = t
                } else {
                    // YouTubes Animation beginnt ~RISE_START_MS nach dem auslösenden Bild. Das lag frühestens kurz nach der
                    // letzten Messung, bei der die Liste noch stand, spätestens am Anfang des Messfensters.
                    // Vorsichtig früh schätzen (Messung der Liste ist um 1–2 Bilder unsicher): eher zu früh als zu spät.
                    val trig = maxOf(stillAt, t - RISE_TRIG_BACK_MS)
                    post("Hochwischen schnell (${"%.2f".format(v)} px/ms, $pos px), unsere kommt", rise = true,
                        t - trig - RISE_START_MS + RISE_LEAD_MS, t)
                    return null
                }
            } else if (moved > 0 && pos > minDist) {
                // Langsam hoch: YouTube zeigt seine Leiste normalerweise nicht. Zur Sicherheit ab und zu nachsehen.
                if (barCheck(now, "langsam hoch")) return null
            }
            SystemClock.sleep(if (early) EARLY_STEP_MS else DIR_PROBE_STEP_MS)
        }
        return null
    }

    /** Wie weit YouTubes Hereinfahren schon ist, wenn ihre Leiste bei [top] steht (Rest ≈ Höhe·e^(−t/45 ms)). */
    private fun risePhaseForTop(top: Int, rest: Rect): Long {
        val off = (top - rest.top).toFloat()
        val h = rest.height().toFloat().coerceAtLeast(1f)
        if (off <= 1f) return RISE_DONE_MS
        if (off >= h) return RISE_LEAD_MS
        return (RISE_TAU_MS * kotlin.math.ln(h / off)).toLong() + RISE_LEAD_MS
    }

    /** Wie weit YouTubes Wegfahren schon ist, wenn ihre Leiste bei [top] steht (≈ Höhe·(t/125 ms)^2,9), etwas später geschätzt. */
    private fun awayPhaseForTop(top: Int, rest: Rect): Long {
        val off = (top - rest.top).toFloat().coerceAtLeast(0f)
        val h = rest.height().toFloat().coerceAtLeast(1f)
        val e = AWAY_MS * Math.pow((off / h).coerceIn(0f, 1f).toDouble(), 1.0 / 2.9)
        return (e - AWAY_LAG_MS).toLong().coerceAtLeast(0L)
    }

    /** Eben selbst früh weggefahren: Die echte steht noch kurz (ihre Animation beginnt erst), das ist keine Rückkehr. */
    private fun inEarlyGrace(now: Long) = now - earlyAwayAt < EARLY_GRACE_MS

    /**
     * Nur zum Messen (Test-Versionen, `setprop log.tag.FShortsLab VERBOSE`): nach dem Fingerauflegen 1,2 s lang alle
     * 8 ms die Lage eines Listen-Elements und der echten Leiste protokollieren ("Lab: t c b"). So sieht man, bei welcher
     * Bewegung YouTube seine Leiste zeigt.
     */
    private fun startLab() {
        val t0 = lastTouchAt
        val scr = Rect(screen)
        labHandler.removeCallbacksAndMessages(null)
        labHandler.post {
            val r = Rect()
            var child: AccessibilityNodeInfo? = null
            var base = 0
            var off = 0
            while (SystemClock.uptimeMillis() - t0 < 1_200) {
                val q0 = SystemClock.uptimeMillis()
                val c = child?.takeIf { it.refresh() && it.isVisibleToUser }
                var cy: Int? = null
                if (c != null) {
                    c.getBoundsInScreen(r)
                    if (kotlin.math.abs(r.top - base) > scr.height() / 2) child = null else { cy = r.top - base + off; off = cy; base = r.top }
                }
                if (child == null) {
                    child = youTubeMainRoot()?.let { scanner.findFeedQuick(it, scr) }?.let { scanner.pickFeedChild(it, scr) }
                    child?.getBoundsInScreen(r)
                    base = r.top
                }
                val bar = realBarTopQuick(scr)
                val q1 = SystemClock.uptimeMillis()
                Log.d(TAG, "Lab: ${q0 - t0} ${cy ?: "?"} ${bar ?: "-"} (${q1 - q0} ms)")
                SystemClock.sleep(8)
            }
        }
    }

    /** Lage der echten Leiste mit möglichst wenig Abfragen (für Hintergrund-Threads). null = nicht da. */
    private fun realBarTopQuick(scr: Rect): Int? {
        val root = youTubeMainRoot() ?: return null
        val r = Rect()
        for (id in selectors.barIds) {
            val bar = root.findAccessibilityNodeInfosByViewId(selectors.fullId(id)).firstOrNull { it.isVisibleToUser } ?: continue
            bar.getBoundsInScreen(r)
            if (!r.isEmpty && r.top < scr.bottom) return r.top
        }
        return null
    }

    /**
     * Unsere fährt beim Scrollen weg. Gleich im Hintergrund die Liste der Seite merken: Beim nächsten Fingerauflegen

     * muss die Richtungserkennung sofort ein Element haben. Ohne Liste suchte sie erst den ganzen Baum ab (bei
     * beschäftigtem YouTube zu langsam, gemessen 16.11.: 15 Bilder echte Leiste).
     */
    private fun followAway(phaseMs: Long, learn: Boolean = false) {
        if (!overlay.isScrolledAway) {
            val known = goneFeed
            val scr = Rect(screen)
            feedHandler.post {
                val feed = known?.takeIf { it.refresh() } ?: youTubeMainRoot()?.let { r ->
                    scanner.findFeedQuick(r, scr) ?: scanner.findFeed(r, scr)
                }
                probeFeed = feed
                probeChild = feed?.let { scanner.pickFeedChild(it, scr) }
                // Hier blendet YouTube beim Scrollen aus (eben gesehen): Ab jetzt fährt unsere auf dieser Seite beim schnellen
                // Runterwischen gleich selbst mit weg (siehe PROBE_DOWN_EARLY).
                if (learn && feed != null && feed != hidesFeed) {
                    hidesFeed = feed
                    earlyChild = null
                    if (app.fshorts.BuildConfig.DEBUG) Log.d(TAG, "Leiste: Seite blendet beim Scrollen aus (gemerkt)")
                }
            }
        }
        overlay.scrollAway(phaseMs)
        startPresenceProbe()
        // Finger liegt noch auf bzw. die Liste rutscht: Richtung weiter messen (Umkehr in derselben Berührung) und, wenn
        // die Liste steht, ein Element für das nächste Fingerauflegen bereitlegen.
        if (!isTablet && SystemClock.uptimeMillis() - lastTouchAt < DIR_MAX_MS) startDirectionProbe()
    }

    /**
     * Unsere ist weggescrollt: Im eigenen Thread laufend (beim Wischen jedes Bild) nur die echte Leiste abfragen, solange
     * unsere weg ist. Taucht sie auf oder fährt sie hoch (Hochwischen, Zurück-Taste, Seitenwechsel), kommt unsere sofort.
     * Fährt sie nur weiter raus, bleibt unsere unten (darum zählt die erste Messung nur als Ausgangslage). Geht eine
     * Video-Seite auf (die echte liegt dahinter), nie zurückkommen, nur den Scan anstossen.
     * Vorher lief das nur ohne Berührung (gemessen 16.11.: beim schnellen Hoch-und-runter 5–15 Bilder echte Leiste).
     */
    private fun startPresenceProbe() {
        if (presenceRunning) return
        presenceRunning = true
        awaySince = SystemClock.uptimeMillis()
        val scr = Rect(screen)
        val restTop = restBounds?.top ?: return run { presenceRunning = false }
        val ids = selectors.barIds.map(selectors::fullId)
        val trace = app.fshorts.BuildConfig.DEBUG && Log.isLoggable(TRACE_TAG, Log.VERBOSE)
        presenceHandler.post(object : Runnable {
            var hasSample = false
            var prevTop: Int? = null
            override fun run() {
                if (!connected || !overlay.isScrolledAway || !youTubeActive) {
                    presenceRunning = false
                    return
                }
                val now = SystemClock.uptimeMillis()
                // Während die Richtung geprüft wird (gleich nach dem Fingerauflegen), YouTube nicht zusätzlich abfragen:
                // Zu viele Abfragen auf einmal liessen die Antworten an die Richtungserkennung bis 100 ms warten
                // (gemessen 16.11.: dann 1 Bild echte Leiste).
                if (now < dirUntil && probeReady) {
                    presenceHandler.postDelayed(this, PRESENCE_BUSY_STEP_MS)
                    return
                }
                val root = youTubeMainRoot()
                // Eine Video-Seite geht nur nach einem Tipp auf (Klick-Meldung). Sonst nicht danach suchen (spart Abfragen).
                if (root != null && lastNavAt > awaySince && scanner.findWatchPlayer(root, scr) != null) {
                    // Video-Seite: Die echte liegt dahinter in Ruhe, das ist keine Rückkehr.
                    handler.post { scheduleScan() }
                    hasSample = false
                    presenceHandler.postDelayed(this, PRESENCE_SLOW_STEP_MS)
                    return
                }
                val r = Rect()
                val bar = root?.let { rt -> ids.asSequence().flatMap { rt.findAccessibilityNodeInfosByViewId(it).asSequence() }.firstOrNull { it.isVisibleToUser } }
                bar?.getBoundsInScreen(r)
                val top = if (bar != null && !r.isEmpty && r.top < scr.bottom - 2) r.top else null
                val calm = now - lastTouchAt > PRESENCE_IDLE_MS && now - lastScrollAt > PRESENCE_IDLE_MS
                // Gleich nach unserem frühen Wegfahren steht die echte noch (ihre Animation beginnt erst): keine Rückkehr.
                val atRest = top != null && top <= restTop + SCROLL_OUT_PX && !inEarlyGrace(now)
                val back = top != null && if (!hasSample) atRest && calm else (prevTop == null || top < prevTop!! - 2 || (atRest && top == prevTop))
                if (trace && top != prevTop) Log.d(TAG, "Rückkehr-Prüfung: echte=${top ?: "weg"} (vorher ${prevTop ?: "weg"})")
                hasSample = true
                prevTop = top
                if (back) {
                    val t = top!!
                    handler.postAtFrontOfQueue {
                        if (!overlay.isScrolledAway) return@postAtFrontOfQueue
                        val rest = restBounds ?: return@postAtFrontOfQueue
                        realGoneByScroll = false
                        realGoneSince = 0L
                        val instant = t <= rest.top + SCROLL_OUT_PX
                        reason("echte kommt zurück (top=$t), unsere ${if (instant) "sofort" else "an ihrer Stelle"}")
                        if (instant) overlay.scrollBackNow() else overlay.scrollRise(risePhaseForTop(t, rest))
                        scheduleScan()
                    }
                    presenceRunning = false
                    return
                }
                // Beim Wischen und kurz danach jedes Bild, dann ein paar Sekunden oft (Zurück-Taste), danach seltener.
                val busy = now - lastTouchAt < PRESENCE_BUSY_MS || now - lastScrollAt < PRESENCE_BUSY_MS
                presenceHandler.postDelayed(this, when {
                    busy -> PRESENCE_BUSY_STEP_MS
                    now - lastTouchAt < PRESENCE_FAST_FOR_MS -> PRESENCE_STEP_MS
                    else -> PRESENCE_SLOW_STEP_MS
                })
            }
        })
    }

    /** Unsere ist weggescrollt, seit einer Weile keine Berührung, die Hintergrund-Prüfung läuft. */
    private fun scrolledAwayIdle(now: Long): Boolean =
        !isTablet && overlay.isScrolledAway && youTubeActive && presenceRunning &&
            now - lastTouchAt > IDLE_AFTER_TOUCH_MS && now - lastScrollAt > PRESENCE_IDLE_MS

    /** Handy, Startseite/Abos/Mein YouTube: dort fährt unsere beim Scrollen mit der echten weg und wieder herein. */

    private fun scrollFollowPage(): Boolean {
        if (isTablet || watchPageOpen || splitBounds != null || menuFront || dialogDimmed) return false
        if (panelMinTop != Int.MAX_VALUE || sheetAppAt != 0L) return false
        val sel = lastUiState?.selected ?: return false
        return sel == TabKind.HOME || sel == TabKind.SUBSCRIPTIONS || sel == TabKind.YOU
    }

    /**
     * Die echte fährt gerade wegen Wischen raus: Finger kurz vorher aufgelegt, seither kein Tipp (Klick). YouTube nimmt
     * die Leiste beim schnellen Wischen oft schon weg, bevor es "gescrollt" meldet – darum zählt die Berührung.
     */
    private fun touchScrollNow(now: Long): Boolean =
        now - lastTouchAt < SCROLL_TOUCH_MS && lastNavAt < lastTouchAt && !watchPageOpen && lastUpScrollAt < lastTouchAt

    /**
     * Ein Tipp in YouTube. Nur wenn die echte Leiste dabei ganz da war, darf unsere danach mit ihr verschwinden
     * (z. B. Video geöffnet). War sie weggescrollt (z. B. Tipp auf ⋮), bleibt unsere stehen.
     */
    private fun onNavigation() {
        lastNavAt = SystemClock.uptimeMillis()
        navFromRest = !hiddenByScroll
    }

    /** Finger kurz vorher aufgelegt und seither nicht gescrollt: ein Tipp, kein Wischen. */
    private fun looksLikeTap(now: Long) = now - lastTouchAt < TAP_STATE_MS && lastScrollAt < lastTouchAt

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
        val b = Rect().also { node.getBoundsInScreen(it) }
        if (kotlin.math.abs(b.width() - screen.width()) > 4 && kotlin.math.abs(b.width() - screen.height()) <= 4) {
            watchRelearnUntil = SystemClock.uptimeMillis() + WATCH_RELEARN_MS
            return
        }
        val top = b.top
        if (Log.isLoggable(TRACE_TAG, Log.VERBOSE)) Log.d(TAG, "Pane: Player top=$top (offen $watchTop)")
        if (top <= watchTop + 1) {
            // Die Startseite meldet sich schon, der Player steht aber noch: Gleich rutscht er. Kurz ganz dicht
            // nachsehen (eine Abfrage alle paar ms), damit unsere beim ersten Rutschen losfährt.
            paneBurstUntil = SystemClock.uptimeMillis() + PANE_BURST_MS
            startPolling()
            return
        }
        watchPageOpen = false
        watchNode = null
        reason("Video verlassen (Pane, top $watchTop→$top)")
        preShownAt = SystemClock.uptimeMillis()
        hiddenByScroll = false
        showAfterVideo(rest, state, top - watchTop)
        continueScanSoon()
    }

    /**
     * Video verlassen: Unsere Leiste fährt das letzte Stück von unten herein (Handy). YouTubes eigene Leiste fährt
     * gleichzeitig in ~0,3 s von ganz unten hoch (gemessen 15.11.: 141, 119, 97, 79, 65, 51 … px unter der Ruhelage,
     * Bild für Bild). Unsere beginnt höher und bremst stärker, liegt also immer darüber. Tablet: wie bisher sofort da.
     */
    private fun showAfterVideo(rest: Rect, state: BarUiState, movedPx: Int) {
        val s = state.copy(style = currentStyle())
        if (isTablet) {
            overlay.show(rest.top, rest, s)
            return
        }
        // Früh erkannt (Player erst wenig gerutscht): YouTubes Leiste hat noch gar nicht begonnen, unsere fährt fast
        // die ganze Höhe herein. Spät erkannt (Player schon weit unten, z. B. YouTube mit Werbung beschäftigt): Die
        // echte ist schon unterwegs, unsere beginnt weiter oben, damit sie in jedem Bild über ihr liegt.
        val d = resources.displayMetrics.density
        val early = 1f - ((movedPx - RISE_EARLY_DP * d) / ((RISE_LATE_DP - RISE_EARLY_DP) * d)).coerceIn(0f, 1f)
        val from = (RISE_MIN_DP + (RISE_MAX_DP - RISE_MIN_DP) * early) * d
        Log.d(TAG, "Leiste fährt herein: ${from.toInt()} px (Player $movedPx px tiefer)")
        overlay.showRising(rest, s, minOf(from, rest.height() * 0.85f), RISE_MS)
    }

    /**
     * Nur Test-Versionen: ~0,3 s nach einem Tipp den Aufbau speichern, solange noch kein Video erkannt ist. So sieht
     * man, wie eine Video-Seite mit Werbung vorneweg aussieht, bevor wir sie erkennen (fürs Test-Protokoll).
     */
    private fun dumpAfterTapSoon(touchAt: Long) {
        handler.postDelayed({
            val now = SystemClock.uptimeMillis()
            if (lastTouchAt != touchAt || lastScrollAt > touchAt || watchPageOpen || !youTubeActive || !overlay.isShowing) {
                return@postDelayed
            }
            if (now - lastVideoDumpAt < VIDEO_DUMP_INTERVAL_MS) return@postDelayed
            lastVideoDumpAt = now
            DebugLog.add("Aufbau nach Tipp gespeichert")
            youTubeMainRoot()?.let { r -> dumper.dump(r, windows, "nach-tipp") }
        }, 300)
    }

    private fun isSearchButton(event: AccessibilityEvent): Boolean {
        val label = (event.contentDescription ?: event.text.firstOrNull())?.toString()?.trim()?.lowercase() ?: return false
        if (label !in SEARCH_LABELS) return false
        val r = Rect().also { event.source?.getBoundsInScreen(it) }
        return r.isEmpty || r.top < screen.top + screen.height() / 5
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
    private fun showBeforeBarlessCloses(event: AccessibilityEvent) {
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        if (watchPageOpen || overlay.isShowing) return
        // Kamera mit Aufnahme: X öffnet das Blatt "Neu beginnen / Entwurf speichern", die Kamera geht nicht zu. Dann
        // nicht vorab zeigen (unsere stand sonst ~0,1 s über dem hochfahrenden Blatt). Erkennbar am Weiter-Knopf.
        val cameraRoot = event.source?.window?.root
        if (cameraRoot != null &&
            cameraRoot.findAccessibilityNodeInfosByViewId(selectors.fullId("shorts_camera_next_button_delegate")).isNotEmpty()
        ) {
            reason("Kamera mit Aufnahme: X öffnet ein Blatt, unsere nicht vorab")
            return
        }
        // Handy: nicht schon beim Tipp auf X zeigen (die Kamera stand danach noch ~8 Bilder, unsere darüber), sondern
        // wenn Android ihr Fenster entfernt (onWindowSignal, wie bei der Zurück-Geste: ~4 Bilder früher, echte nie).
        if (!isTablet) {
            reason("Kamera wird geschlossen (X), unsere bei Fenster-Meldung")
            xCloseAt = SystemClock.uptimeMillis()
            return
        }
        reason("Kamera wird geschlossen, unsere vorab zeigen")

        preShownAt = SystemClock.uptimeMillis()
        hiddenByScroll = false
        overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        continueScanSoon()
    }

    /** Tipp auf "Entwurf speichern und beenden" im Blatt über der Kamera mit Aufnahme? Dann geht die Kamera gleich zu. */
    private fun savesAndExitsCamera(event: AccessibilityEvent): Boolean {
        if (!menuOverBarless || event.windowId != menuWindowId) return false
        val label = (event.contentDescription ?: event.text.firstOrNull())?.toString()?.trim()?.lowercase() ?: return false
        return SAVE_EXIT_WORDS.any { label.contains(it) }
    }

    /** Kamera geht nach "Speichern und beenden" zu: Android meldet die Seite erst, wenn die echte Leiste schon da ist. */
    private fun showAfterSaveExit() {
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        if (watchPageOpen || overlay.isShowing) return
        reason("Entwurf speichern und beenden, unsere vorab zeigen")
        preShownAt = SystemClock.uptimeMillis()
        saveExitAt = preShownAt
        hiddenByScroll = false
        overlay.show(rest.top, rest, state.copy(style = currentStyle()))
        continueScanSoon()
    }

    /** Abbruch einer laufenden Suche, weil die Liste gerade gewischt wird (siehe [yieldIfScrolling]). */
    private class ScanYield : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    /**
     * Handy: Wird gerade gewischt (die Richtungs-Messung sah die Liste laufen), eine volle Suche an einer sicheren Stelle
     * abbrechen. Sie hielte den Haupt-Thread bis 0,4 s an, unsere Leiste könnte so lange nicht mit YouTubes fahren.
     */
    private fun yieldIfScrolling() {
        if (!isTablet && SystemClock.uptimeMillis() < scanHoldUntil) throw ScanYield()
    }

    private fun scan() {
        handler.removeCallbacks(watchdog)
        val started = SystemClock.uptimeMillis()
        try {
            scanInternal()
            val took = SystemClock.uptimeMillis() - started
            if (took > SLOW_SCAN_MS) Log.d(TAG, "Scan dauerte $took ms")
        } catch (e: ScanYield) {
            // Liste bewegt sich: Suche abgebrochen, nach dem Wischen neu (siehe yieldIfScrolling).
            if (app.fshorts.BuildConfig.DEBUG) Log.d(TAG, "Scan abgebrochen (Wischen) nach ${SystemClock.uptimeMillis() - started} ms")
            handler.postDelayed({ scheduleScan() }, (scanHoldUntil - SystemClock.uptimeMillis()).coerceAtLeast(POLL_MS))
            return
        } catch (e: Exception) {
            // Lieber nichts anzeigen als YouTube blockieren.
            Log.w(TAG, "Scan fehlgeschlagen", e)
            hideAll()
        }
        if (overlay.isShowing) handler.postDelayed(watchdog, WATCHDOG_MS)
        // Unsere Leiste ist weg, die echte gibt es noch (z. B. Video offen): laufend nachsehen.
        if (youTubeActive && !overlay.isShowing && (lastBar != null || watchPageOpen)) startPolling()
    }

    /** [keepMirror]: YouTube wird nur verdeckt oder verlassen, seine Leiste bleibt darunter (siehe Spiegel im Overlay). */
    /** Siehe "Tastatur offen": Spiegel unter die offene Tastatur legen (nur wenn sie noch offen ist). */
    private val mirrorUnderKeyboard = Runnable {
        if (app.fshorts.BuildConfig.DEBUG && Log.isLoggable(TRACE_TAG, Log.VERBOSE)) Log.d(TAG, "Spiegel unter Tastatur? aktiv=$youTubeActive suchen=$keyboardToPage")
        if (!youTubeActive && !keyboardToPage && isKeyboardOpen()) overlay.showMirrorNow()
    }

    private fun hideAll(keepMirror: Boolean = false) {
        // Fährt gerade sanft weg (Tastatur, Video): fertig fahren lassen, danach ist sie ohnehin weg.
        if (overlay.isSlidingAway) return
        if (keepMirror) overlay.hideKeepMirror() else overlay.hide()
    }

    /** Spiegel unserer Leiste an YouTubes Hauptfenster heften (Handy, nicht im Splitscreen). */
    private fun updateMirrorWindow() {
        if (isTablet) return
        overlay.mirrorWindow(if (splitBounds != null) -1 else barWindowId)
    }

    /** Sanft wegfahren statt verschwinden (Tastatur geht auf, Video wird geöffnet). Sonst wie [hideAll]. */
    private fun hideSmooth() {
        if (overlay.isShowing && !overlay.isSliding) overlay.slideAway(VIDEO_SLIDE_MS, fast = true) else hideAll()
    }

    private var lastReason = ""

    /** Schreibt ins Protokoll (adb logcat -s FuckShorts), warum die Leiste gerade steht oder weg ist. Nur bei Wechsel. */
    private fun reason(text: String) {
        if (text == lastReason) return
        lastReason = text
        Log.d(TAG, "Leiste: $text")
        DebugLog.add("Leiste: $text")
    }

    private fun scanInternal() {
        val now = SystemClock.uptimeMillis()
        if (!Access.unlocked) {
            overlay.hide()
            return
        }
        yieldIfScrolling()
        // Google Play geht über YouTube auf, unsere dunkelt mit: das regelt sheetAppWatch.
        if (sheetAppAt != 0L) {
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }
        if (shelfMenuOpenedAt != 0L) {
            handleShelfMenu(now)
            return
        }
        updateSplit()
        if (overlay.isShowing) ourShownAt = now
        val foreground = foregroundWindow()?.root
        if (app.fshorts.BuildConfig.DEBUG) {
            val fw = foreground?.windowId ?: -1
            if (fw != loggedForeground) {
                loggedForeground = fw
                DebugLog.add("vorne: w=$fw ${foreground?.packageName} (Hauptfenster w=$barWindowId, ohne Leiste w=$barlessWindowId)")
            }
        }
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
        if (!keyboard && !isTablet && foreground?.packageName?.toString() == PLAY_STORE_PKG && youTubeActive &&
            overlay.isShowing && now - lastTouchAt < 1500
        ) {
            dimForSheetApp()
            return
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
            if (keyboard) overlay.releaseHold()
            // Tastatur: nur wegfahren, wenn unsere noch steht (sonst nichts tun – ein Verstecken nähme den Spiegel unter der
            // Tastatur wieder weg).
            if (keyboard) {
                if (overlay.isShowing) hideSmooth()
            } else {
                hideAll(keepMirror = true)
            }
            // Tastatur offen (Suche): Den Spiegel gleich unter die Tastatur legen, sobald sie ganz offen ist (dort unsichtbar).
            // Nach "Suchen" kommt die Ergebnis-Seite mit Leiste hinter der wegfahrenden Tastatur hervor; die Meldung dazu kam
            // ~0,3 s später, so sah man in 1 von 3 Läufen 3 Bilder die echte Leiste durch die Tastatur (gemessen 18.11.).
            if (keyboard && !isTablet && splitBounds == null) {
                // Nur einmal pro Tastatur (beim Tippen kommen laufend Scans, ein Neu-Ansetzen schob es endlos hinaus).
                if (!handler.hasCallbacks(mirrorUnderKeyboard) && !overlay.mirrorCovering) {
                    handler.postDelayed(mirrorUnderKeyboard, KEYBOARD_MIRROR_MS)
                }
            }
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
                // Auch nach "Kamera schliessen" (unsere vorab gezeigt): Mit Aufnahme geht stattdessen dieses Menü auf.
                menuOverBarless = barlessWindowId != -1 && (!overlay.isShowing || now - preShownAt < PRE_SHOW_HOLD_MS)
            }
            if (menuOverBarless && now - saveExitAt < PRE_SHOW_HOLD_MS) {
                // "Speichern und beenden" getippt: Kamera und Blatt gehen zu, unsere steht schon bereit.
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
            if (menuOverBarless) {
                // Darunter liegt keine echte Leiste (Kamera nach einer Aufnahme): unsere nie zeigen, auch nicht,
                // während das Menü rein- oder rausfährt (vorher ~0,25 s unsere über dem Menü).
                reason("Menü über Seite ohne Leiste")
                hideAll()
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
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
        if (menuOverBarless) {
            // Nächstes Menü (vielleicht auf einer Seite mit Leiste) neu beurteilen.
            menuOverBarless = false
            menuWindowId = -1
        }
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
            // Vorne ist die Kamera (z. B. nach der Fotos-Frage zurück): dort gibt es keine Leiste. Vorher blitzte
            // unsere ~0,13 s über der Kamera auf (gemessen 15.11.).
            val onCamera = foreground.windowId == cameraWindowId || foreground.windowId == barlessWindowId
            if (rest != null && state != null && !watchPageOpen && !restoredState && !afterKeyboard && !wasBarless && !onCamera) {
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
        // Kamera eben mit X geschlossen: Das Zeigen macht onBarlessWindowGone etwas verzögert (Kamera noch sichtbar).
        if (barless != -1 && now - xCloseAt < X_CLOSE_DELAY_MS + 40) {
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }
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

        // Eben in die Liste getippt und unsere Leiste steht: zuerst NUR den Player suchen (eine Abfrage), nicht erst die
        // echte Leiste. Während YouTube die Video-Seite aufbaut, kostet jede Abfrage 0,1–0,2 s; die Lade-Seite stand so
        // bis 0,5 s über unserer Leiste.
        if (!isTablet && !watchPageOpen && overlay.isShowing && !overlay.isSliding &&
            now - feedTouchAt < TAP_PROBE_MS && feedTouchAt > watchClosedAt
        ) {
            val quick = scanner.findWatchPlayer(root, screen)
            if (quick != null) {
                watchNode = quick
                watchTop = scanner.topOf(quick)
                watchPageOpen = true
                reason("Video erkannt (nach Tipp, ${now - feedTouchAt} ms)")
                hideSmooth()
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
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
                hideSmooth()
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
                // Vom langsamen Scan erkannt: vermutlich spät.
                showAfterVideo(rest, state, Int.MAX_VALUE / 2)
                // Erst zeichnen lassen, dann weitersuchen: Die volle Suche blockiert sonst das Zeichnen.
                continueScanSoon()
                return
            }
        }

        // Reihenfolge ist wichtig: Erst alles, was nötig ist, damit unsere Leiste richtig steht. Langsames
        // (Mini-Player, Profilbild, Regale) danach, sonst sieht man beim Verlassen eines Videos die echte Leiste.
        yieldIfScrolling()
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
        val watchWasOpen = watchPageOpen
        watchPageOpen = watchNode != null
        if (app.fshorts.BuildConfig.DEBUG && watchPageOpen && !watchWasOpen) {
            // Test-Protokoll: Wie sah die Video-Seite aus, als wir sie erkannt haben? (Mit Werbung vorneweg kam die
            // Erkennung ~0,7 s zu spät.) Kurz danach speichern, damit das Verstecken nicht wartet.
            DebugLog.add("Video erkannt, letzte Berührung vor ${now - lastTouchAt} ms")
        }

        // Player eben erkannt: sofort wegfahren, nicht erst am Ende dieses Scans. Während YouTube die Seite aufbaut,
        // dauert ein Scan bis 0,45 s; die Lade-Seite stand so ~0,2 s länger über unserer Leiste (gemessen 07.11.).
        if (watchPageOpen && !watchWasOpen && overlay.isShowing && !overlay.isSliding) {
            reason("Video erkannt (früh)")
            hideSmooth()
            // Den Rest des Scans nicht mehr machen: Er läuft auf dem Haupt-Thread (bis 0,3 s) und hielte die
            // Wegfahr-Animation an. Der nächste Scan (nach dem Wegfahren) holt alles nach.
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }

        // Video aus der Liste angetippt, Player noch nicht da: Die graue Lade-Seite deckt die echte Leiste schon ab.
        // Unsere fährt jetzt weg (vorher erst, wenn der Player erkannt war: 0,5–1 s über der Lade-Seite).
        // Nur nach einem Tipp ausserhalb eines Videos und solange seither kein Video zuging (beim Verlassen fährt die
        // Video-Seite über die Leiste nach unten weg: dort muss unsere sofort stehen).
        if (!watchPageOpen && now - feedTouchAt < LOADING_RULE_MS && feedTouchAt > watchClosedAt) {
            val barTop = restBounds?.top ?: (screen.bottom - (70 * resources.displayMetrics.density).toInt())
            if (scanner.isWatchPageLoading(root, screen, barTop)) {
                loadingPageAt = now
                reason("Video-Seite lädt")
                hideSmooth()
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
        }

        // Handy: Panel auch suchen, solange die echte Leiste noch gemeldet wird (das Panel fährt schon darüber hoch).
        if ((bar == null || (!isTablet && overlay.isShowing)) && !watchPageOpen && handlePanel(root, now)) return
        panelMinTop = Int.MAX_VALUE
        panelNode = null

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
            updateMirrorWindow()
            cameraWindowId = -1

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
                buildUiState(bar, root, previous?.selected).also { saved ->
                    restBounds?.let {
                        if (atRest) {
                            saveBarState(it, saved)
                            restBySize[sizeKey(screen)] = Rect(it) to saved
                        }
                    }
                }
            } else {
                previous.copy(
                    selected = selectedTab(bar, root, previous.selected) ?: previous.selected,
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
            // Video geht auf: sanft wegfahren (die Video-Seite deckt die echte Leiste ab). Liegt nur ein anderes Fenster
            // vorne (Kamera), bleibt der Spiegel darunter.
            if (watchPageOpen) hideSmooth() else hideAll(keepMirror = keepFail == "anderes Fenster vorne")

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
        yieldIfScrolling()

        if (now - lastMiniCheckAt > MINI_CHECK_MS) {
            lastMiniCheckAt = now
            val walk = now - lastMiniWalkAt > MINI_WALK_MS
            if (walk) lastMiniWalkAt = now
            miniPlayerOpen = scanner.isMiniPlayerVisible(root, screen, walk)
        }
        yieldIfScrolling()

        if (debugDumpWanted) {
            debugDumpWanted = false
            if (Prefs.debugDumps(this) && now - lastDebugDumpAt > DEBUG_DUMP_INTERVAL_MS) {
                lastDebugDumpAt = now
                dumper.dump(root, windows, "bildschirmwechsel")
            }
        }

        // Icons und Profilbild von der echten Leiste abschauen, sobald sie ganz still steht.
        // Nicht direkt nach Berührung/Scrollen: Die echte (durchsichtige) Leiste ist dann oft noch in Bewegung, obwohl
        // sie schon an der Ruhelage gemeldet wird. So wurde einmal ein fremdes Bild ("17") als Profilbild abgeschaut.
        val calm = now - lastTouchAt > SAMPLE_CALM_MS && now - lastScrollAt > SAMPLE_CALM_MS && !overlay.isScrolledAway
        if (bar != null && bar.bounds == restBounds && calm && sampler.needsCapture(bar)) {
            if (now - restSince < REST_STABLE_MS) {
                handler.postDelayed({ scheduleScan() }, REST_STABLE_MS)
            } else if (now - watchClosedAt < SAMPLE_AFTER_VIDEO_MS) {
                // Die Video-Seite rutscht noch über die Leiste, deren Lage stimmt aber schon: Foto zeigte ein halbes
                // Profilbild (blieb bis zum nächsten Foto stehen).
                handler.postDelayed({ scheduleScan() }, SAMPLE_AFTER_VIDEO_MS)
            } else if (sampler.canCaptureBehindOverlay) {
                sampler.capture(bar, root.windowId, windowBounds(root.windowId)) { scheduleScan() }
            } else if (!overlay.isShowing) {
                // Ältere Android-Versionen: nur ganzer Bildschirm, darum vorher nichts von uns zeigen.
                sampler.capture(bar, root.windowId, Rect(screen)) { scheduleScan() }
                return
            }
        }

        yieldIfScrolling()
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
        // Eine andere App (z. B. Chrome Custom Tab nach einem Tipp auf Werbung) ist vorne: Ihr Fenster ist schon
        // undurchsichtig, auf "Inhalt" warten hiess ~0,3 s unsere Leiste über dem leeren Tab.
        if (root.packageName?.toString() != selectors.youtubePackage && root.packageName?.toString() != packageName) return false
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
        } else if (isTablet && shadePanel(shade, rest) != null) {
            // Tablet quer: Die Benachrichtigungen sind eine Spalte in der Mitte. Links und rechts davon schimmert die
            // echte Leiste durch, beim Zuklappen ~4 Bilder ganz. Darum Fläche in Schleierfarbe mit Loch für die Spalte.
            reason("Benachrichtigungen offen (Spalte), Fläche mit Loch")
            // Loch erst, wenn die Spalte bis zur Leiste reicht (beim Aufziehen schimmerte sonst dort die echte durch).
            val hole = shadePanel(shade, rest)?.takeIf { shadeCoversBar(shade, rest) }
            overlay.showVeiled(rest, state, shadeVeil ?: defaultVeil(), hole)
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
    /** Tablet quer: Bereich der schmalen Benachrichtigungs-Spalte über unserer Leiste, sonst null. */
    private fun shadePanel(shade: AccessibilityNodeInfo, rest: Rect): Rect? {
        val stack = shade.findAccessibilityNodeInfosByViewId("$SYSTEM_UI:id/notification_stack_scroller")
            .firstOrNull() ?: return null
        val r = Rect().also { stack.getBoundsInScreen(it) }
        // Nur quer: Hochkant ist die Spalte fast so breit wie die Leiste, dort gilt die Regel wie auf dem Handy.
        val display = displayBounds()
        if (display.width() <= display.height() || r.isEmpty || r.width() > rest.width() * 3 / 4) return null
        // Die Spalte reicht bis unten (Knöpfe "Löschen" usw.): Loch über die ganze Höhe unserer Leiste, etwas breiter.
        val pad = (8 * resources.displayMetrics.density).toInt()
        return Rect(r.left - pad, rest.top, r.right + pad, rest.bottom)
    }

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
            panelNode = null
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
        val firstSeen = panelMinTop == Int.MAX_VALUE
        // Handy: Panel über einer Seite mit unserer Leiste (z. B. ⋮ bei Werbung → "Mein Anzeigen-Center"): Unsere wird
        // genau an der Kante des Panels abgeschnitten und dunkelt mit YouTube ab (siehe coverByPanel).
        if (firstSeen) panelFromBar = !isTablet && (overlay.isShowing || now - ourShownAt < PANEL_FROM_BAR_MS)
        panelMinTop = minOf(panelMinTop, top)
        panelNode = scanner.findPanel(root, screen)
        if (firstSeen && app.fshorts.BuildConfig.DEBUG && now - panelDumpedAt > PANEL_DUMP_INTERVAL_MS) {
            // Test-Versionen: Aufbau etwas später speichern (ein Dump hält alles bis ~1 s auf), nur wenn das Panel noch
            // offen ist. So sieht man im Protokoll, was für ein Panel es ist (Werbe-Video oder ⋮-Menü).
            panelDumpedAt = now
            handler.postDelayed({
                if (panelMinTop == Int.MAX_VALUE || overlay.isShowing || !youTubeActive) return@postDelayed
                val r = youTubeMainRoot() ?: return@postDelayed
                DebugLog.add("Aufbau mit Panel gespeichert (oben=$panelMinTop)")
                dumper.dump(r, windows, "panel")
            }, PANEL_DUMP_DELAY_MS)
        }
        // Grosses Blatt bis weit nach oben (Werbe-Video: "Sponsored … Shop now" unter dem Player) ist eine ganze
        // Seite, kein Menü: Rutscht es ein Stück (z. B. nächste Werbung), darf unsere NICHT darüber erscheinen
        // (Video vom 14.11.). Nur ein kleines Menü-Panel (⋮ bei Werbung) zeigt unsere schon beim Runterfahren.
        if (panelFromBar && rest != null && state != null) {
            coverByPanel(rest, state, top, now)
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return true
        }
        val bigSheet = panelMinTop < screen.top + screen.height() * BIG_SHEET_TOP
        val closing = !bigSheet && top > panelMinTop + CLOSING_DP * resources.displayMetrics.density
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
     * Panel (Werbe-Menü) fährt über unsere Leiste hoch bzw. wieder runter (gemessen 15.11.: hoch in ~4 Bildern, runter
     * in ~5). Unsere wird nur oberhalb der Panel-Kante gezeichnet, abgedunkelt wie YouTube dahinter (Tinte vom Rand
     * her). So "verschluckt" das Panel sie beim Öffnen und gibt sie beim Schliessen frei. Vorher: beim Öffnen 4 Bilder
     * über dem Panel, dann weg; beim Schliessen 4 Bilder gar keine Leiste, dann hell unter dem noch dunklen YouTube.
     */
    private fun coverByPanel(rest: Rect, state: BarUiState, top: Int, now: Long) {
        // Fährt es nach unten (zu)? YouTube meldet die Lage dabei nur alle ~130 ms (es ist mit der Animation
        // beschäftigt), das Panel ist in Wirklichkeit schon weiter unten: unsere gleich ganz zeigen, abgedunkelt wie
        // YouTube dahinter. Hell wird sie (Tinte zieht sich zurück), wenn das Panel weg ist.
        val closing = top > panelMinTop + CLOSING_DP * resources.displayMetrics.density
        if (closing) {
            // YouTube hellt die Seite ab hier in ~0,13 s auf (und springt dann ganz hell). Unsere kommt abgedunkelt und
            // hellt im selben Takt auf (vorher blieb sie ~5 Bilder länger dunkel als die Seite).
            if (!overlay.isShowing) {
                reason("Panel fährt zu (oben=$top), unsere hellt mit YouTube auf")
                preShownAt = now
                hiddenByScroll = false
                overlay.showFromDim(rest, state.copy(style = currentStyle()), PANEL_DIM, PANEL_BRIGHTEN_MS)
            }
            return
        }
        if (top > rest.top + 2) {

            reason("Panel an der Leiste (oben=$top), unsere abgeschnitten")
            preShownAt = now
            hiddenByScroll = false
            overlay.showCoveredBy(rest, state.copy(style = currentStyle()), PANEL_DIM, top)
        } else {
            reason("Panel offen (oben=$top)")
            if (overlay.isShowing) overlay.hide()
        }
    }

    /**
     * Panel offen, unsere weg: eine Abfrage pro Bild. Ist das Panel weg (oder unter unsere Leiste gerutscht), sofort
     * zeigen wie "Panel zu" im Scan. Baut YouTube das Panel neu (Knoten ungültig), erst neu suchen, nicht gleich zeigen.
     */
    private fun checkPanelGone(now: Long) {
        val rest = restBounds ?: return
        val state = lastUiState ?: return
        if (overlay.isShowing && !panelFromBar) return
        var node = panelNode ?: return
        if (!node.refresh() || !node.isVisibleToUser) {
            val again = youTubeMainRoot()?.let { scanner.findPanel(it, screen) }
            if (again != null) {
                panelNode = again
                node = again
            } else {
                panelNode = null
                panelMinTop = Int.MAX_VALUE
                reason("Panel zu (Nachführen), unsere vorab zeigen")
                preShownAt = now
                hiddenByScroll = false
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                continueScanSoon()
                return
            }
        }
        val top = scanner.topOf(node)
        if (panelFromBar) {
            coverByPanel(rest, state, top, now)
            return
        }
        if (top >= rest.top - 2) {
            reason("Panel unter der Leiste (Nachführen, oben=$top)")
            preShownAt = now
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            continueScanSoon()
        }
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
            val rest = Rect.unflattenFromString(json.getString("rest"))?.let { snapToEdges(it) } ?: return
            val labels = json.getJSONObject("labels")
            val density = resources.displayMetrics.density
            restBounds = rest
            lastUiState = BarUiState(
                selected = json.getString("selected").takeIf { it.isNotEmpty() }?.let { TabKind.valueOf(it) },
                labels = labels.keys().asSequence().associate { TabKind.valueOf(it) to labels.getString(it) },
                subscriptionsBadge = json.getBoolean("badge"),
                hasCreate = json.getBoolean("create"),
                barHeightPx = rest.height(),
                iconCenterPx = (iconCenterDp * density).roundToInt(),
                labelTopPx = (labelTopDp * density).roundToInt(),
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
        // Bei Animationen (z. B. Zurück-Geste) meldet YouTube die Leiste kurz 1 px verschoben. So gemerkt, stand
        // unsere 1 px daneben: links ein Strich, unten 2 Zeilen Inhalt sichtbar. Fast am Rand = genau am Rand.
        restBounds?.let { r -> snapToEdges(r).takeIf { it != r }?.let { restBounds = it } }
        val rest = restBounds
        val b = snapToEdges(bar.bounds)
        // Tablet: Gleiche Oberkante, aber kürzer: Die gemerkte Lage war zu hoch gelernt (auch gespeichert, 144 statt
        // 72 px). Dann galt die Leiste nie als "still", Farbe und Profilbild wurden nie abgeschaut (weiss auf dunkel).
        if (isTablet && rest != null && kotlin.math.abs(b.top - rest.top) <= 2 && b.bottom < rest.bottom - 2) {
            Log.d(TAG, "Leiste: Ruhelage zu hoch, jetzt ${b.toShortString()} (war ${rest.toShortString()})")
            restBounds = Rect(b)
            return
        }
        // Tablet: Beim Schliessen der Kamera meldet YouTube die Leiste kurz 32 px zu hoch (Unterkante über der
        // gewohnten). Das ist keine neue Ruhelage, sonst stand unsere danach zu hoch und hell, darunter "Shorts".
        if (isTablet && rest != null && b.bottom < rest.bottom - 2) return
        // Handy: Beim Drehen meldet YouTube die Leiste kurz zu hoch (gemessen 15.11.: 71 px, mehr als snapToEdges
        // ausgleicht). So gelernt, stand unsere dauerhaft 71 px zu hoch, darunter ein Streifen der echten. Eine Lage
        // mit höherer Unterkante gilt darum erst, wenn sie eine Weile unverändert bleibt (z. B. Wechsel auf
        // 3-Knopf-Navigation), nicht bei einer einzelnen Messung.
        // Handy: Auch eine höhere/grössere Leiste (gleiche Unterkante, z. B. kurz beim Mini-Player) wurde sofort gelernt:
        // unsere stand dann 64 px zu hoch (gesehen 15.11., Niklas: "Leiste ist höher geworden"). Darum gilt jede höhere
        // Lage erst, wenn sie eine Weile unverändert bleibt.
        val taller = rest != null && b.bottom >= rest.bottom - 2 && b.top < rest.top - 2
        if (!isTablet && rest != null && (b.bottom < rest.bottom - 2 || taller)) {
            val now = SystemClock.uptimeMillis()
            if (b != higherRest) {
                higherRest = Rect(b)
                higherRestSince = now
                return
            }
            if (now - higherRestSince < HIGHER_REST_MS) return
            Log.d(TAG, "Leiste: Ruhelage höher, jetzt ${b.toShortString()} (war ${rest.toShortString()})")
            higherRest = null
            restBounds = Rect(b)
            return
        }
        // Handy: Früher zu gross gelernt (gleiche Unterkante, Oberkante zu hoch) und die echte steht seit einer Weile
        // ruhig tiefer, ohne Berührung (also nicht halb weggescrollt): wieder auf die echte Grösse.
        val shorter = rest != null && kotlin.math.abs(b.bottom - rest.bottom) <= 2 && b.top > rest.top + 2 &&
            b.height() >= rest.height() / 2
        if (!isTablet && shorter) {
            val now = SystemClock.uptimeMillis()
            if (b != shorterRest || lastTouchAt > shorterRestSince) {
                shorterRest = Rect(b)
                shorterRestSince = now
                return
            }
            if (now - shorterRestSince < SHORTER_REST_MS) return
            Log.d(TAG, "Leiste: Ruhelage war zu gross, jetzt ${b.toShortString()} (war ${rest?.toShortString()})")
            shorterRest = null
            restBounds = Rect(b)
            return
        }
        higherRest = null
        shorterRest = null
        // Tablet: Steht die ganze Leiste tiefer als die gemerkte Lage, war diese falsch (auch gespeichert): neu lernen.
        // Handy: nur wenn sie dabei ganz zu sehen ist und bis zum unteren Rand reicht (beim Wegscrollen ragt sie
        // unten hinaus). So korrigiert sich eine früher falsch gelernte, zu hohe Lage von selbst.
        val s = screenBounds()
        val wrongRest = rest != null && b.height() >= rest.height() - 2 && b.bottom > rest.bottom + 2 &&
            (isTablet || b.bottom == s.bottom)
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
        if (sheetAppAt != 0L) return
        val rest = restBounds
        val state = lastUiState
        // Panel offen: Die Leiste ist nicht abfragbar. Aber das Panel selbst jedes Bild nachsehen: Geht es zu (z. B.
        // Werbe-Video verlassen), steht unsere sofort und nicht erst nach dem nächsten Scan (mit Werbung 0,3 s
        // echte Leiste, Video vom 14.11.).
        if (panelMinTop != Int.MAX_VALUE && !menuFront && coverWindowId == -1) {
            checkPanelGone(now)
            return
        }
        // Menü vorne: Das regelt der Scan (Lage des Menüs), die Leiste ist nicht abfragbar.
        if (menuFront || panelMinTop != Int.MAX_VALUE || coverWindowId != -1) return
        // Video offen und das Hauptfenster ist das aktive: Dann ist kein Popup vorne. Das sagt die Fensterliste
        // (Abfrage ans System, schnell); YouTube selbst wird dafür nicht gefragt (langsam, solange es animiert).
        val mainActive = watchPageOpen && fresh == null && barWindowId != -1 &&
            windows.firstOrNull { it.isActive }?.id == barWindowId
        if (!mainActive && popupFront(foregroundWindow()?.root) != null) return

        // Unser eigener Tipp aufs Karten-⋮: Das Menü ist gleich wieder zu, unsere bleibt stehen (sonst 0,13 s weg).
        if (shelfMenuOpenedAt != 0L || now - shelfMenuDoneAt < SHELF_MENU_HOLD_MS) return
        // Graue Lade-Seite eines Videos eben gesehen: Das regelt der Scan (sonst zeigte das Nachführen unsere wieder,
        // weil die echte Leiste dahinter unverändert steht).
        if (!watchPageOpen && fresh == null && now - loadingPageAt < LOADING_HOLD_MS) return
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
            val t0 = SystemClock.uptimeMillis()
            val stillOpen = node != null && watchStillOpen(node, now) != false
            if (app.fshorts.BuildConfig.DEBUG && now - lastTouchAt < 1500 && Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {
                Log.d(TAG, "Poll: Player top=${node?.let { scanner.topOf(it) }} in ${SystemClock.uptimeMillis() - t0} ms")
            }
            if (node != null && stillOpen) {
                watchTop = minOf(watchTop, scanner.topOf(node))
                reason("Video offen")
                hideSmooth()
                return
            }

            val moved = node?.let { scanner.topOf(it) - watchTop } ?: Int.MAX_VALUE / 2
            watchPageOpen = false
            watchNode = null
            reason("Video verlassen")
            preShownAt = SystemClock.uptimeMillis()
            hiddenByScroll = false
            showAfterVideo(rest, state, moved)
            scheduleScan()
            return
        }
        val observedTop = when {
            fresh != null -> fresh.bounds.top
            else -> lastBar?.let { scanner.liveTop(it.container, screen) }
        }
        if (overlay.isScrolledAway && app.fshorts.BuildConfig.DEBUG && Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {
            Log.d(TAG, "Weg-Nachführen: echte=${observedTop ?: "keine"} frisch=${fresh != null} gemerkt=${lastBar != null}")
        }
        if (observedTop == null || observedTop >= screen.bottom - 2) {
            if (realGoneSince == 0L) realGoneSince = now
        } else {
            realGoneSince = 0L
        }
        if (observedTop == null) {

            // Handy: Gleich nach einer Berührung ist die echte weg. Vielleicht geht gerade das Werbe-Menü auf (YouTube
            // nimmt seine Leiste dafür ~20 ms nach dem Tipp weg, das Panel kommt ~30 ms später und ist nach 1 Bild
            // über der Leiste). Darum hier kurz jedes Bild nach dem Panel sehen, nicht erst beim nächsten Scan.
            if (!isTablet && overlay.isShowing && now - lastTouchAt < PANEL_PROBE_MS && panelMinTop == Int.MAX_VALUE) {
                val main = youTubeMainRoot()
                if (main != null && Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {
                    Log.d(TAG, "Panel-Probe ${now - lastTouchAt} ms nach Berührung: ${scanner.panelDebug(main)}")
                }

                if (main != null && scanner.panelTop(main, screen) != null && handlePanel(main, now)) return
            }
            if (now - themeChangedAt < THEME_HOLD_MS) {
                // YouTube baut sich nach hell/dunkel neu auf, die Leiste fehlt kurz: unsere stehen lassen.
                reason("Design gewechselt, YouTube baut neu auf")
                handler.postDelayed({ scheduleScan() }, POLL_MS)
                return
            }
            if (!barGoneAfterKeyboard && keepWhileScrolledAway(now)) {
                hiddenByScroll = true
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
                // Handy, Startseite/Abos/Mein YouTube: Beim schnellen Runterwischen nimmt YouTube (21.45) seine Leiste
                // ~3 Bilder nach Beginn weg und zeigt sie erst beim Hochwischen wieder (gemessen 15.11. mit unsichtbarer
                // eigener Leiste). Unsere fährt dann wie das Original nach unten weg.
                // Nur wenn die echte in DIESER Berührung verschwunden ist. War sie schon vorher weg (unsere blieb stehen),
                // ist ein neues Fingerauflegen oft ein Hochwischen: Unsere fuhr sonst weg, und die echte kam zuerst
                // (gemessen 16.11.: 9 Bilder echte Leiste beim schnellen Hoch-und-runter).
                if (scrollFollowPage() && touchScrollNow(now) && realGoneSince >= lastTouchAt - GONE_TOUCH_SLACK_MS) {
                    realGoneByScroll = true
                    if (!overlay.isScrolledAway) reason("echte beim Scrollen weg, unsere fährt runter")
                    // Die echte ist schon aus der Oberfläche: Ihre Animation ist fast oder ganz fertig, unsere holt rasch auf.
                    followAway(AWAY_LATE_PHASE_MS, learn = true)
                } else {
                    reason("echte weggescrollt und entfernt, unsere bleibt")
                }
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
            if (app.fshorts.BuildConfig.DEBUG && Log.isLoggable(TRACE_TAG, Log.VERBOSE)) {
                val ws = windows
                Log.d(TAG, "Fenster: vorne=$frontPkg leiste=$barWindowId " + ws.joinToString { w ->
                    "${w.id}:${w.type}${if (w.isActive) "A" else ""}${if (w.isFocused) "F" else ""}:${w.root?.packageName}:${Rect().also(w::getBoundsInScreen).toShortString()}"
                })
            }
            lastBar = null
            lastObservedTop = -1
            // Video wird geöffnet (Tipp, nicht Plus/Kamera): sanft wegfahren. Sonst sofort weg.
            // Eine andere App (Chrome Custom Tab nach Werbe-Tipp) ist schon vorne: sofort weg, nicht über ihr wegfahren.
            // Werbung → Google Play: Oft meldet sich Plays Startfenster vor der Seiten-Meldung. Auch dann mit abdunkeln.
            if (frontPkg == PLAY_STORE_PKG && !isTablet && now - lastTouchAt < 1500 && overlay.isShowing) {
                dimForSheetApp()
                return
            }
            val otherApp = frontPkg != null && frontPkg != selectors.youtubePackage && frontPkg != packageName
            // Andere App (Google Play, Chrome …) oder anderes Fenster vorne: YouTubes Leiste liegt darunter unverändert,
            // der Spiegel bleibt (sonst sah man sie unter Plays halbdurchsichtigem Blatt, gemessen 17.11.: 10 + 7 Bilder).
            if (!otherApp && now - lastNavAt < NAV_LINK_MS && now - createTappedAt > CREATE_HOLD_MS) hideSmooth()
            // Kein Inhalt mehr im vordersten Fenster (frontPkg null): YouTubes Fenster schliesst gerade (z. B. Zurück auf der
            // Startseite) und fährt mit seiner Leiste weg. Der Spiegel fährt mit (sonst sah man darauf ~8 Bilder die echte).
            else hideAll(keepMirror = otherApp || frontPkg == null || keepFail == "anderes Fenster vorne")
            return

        }
        // Taucht die echte Leiste gerade wieder auf oder fährt hoch (z. B. Video verlassen), steht unsere
        // sofort ganz da. So kommt die echte nie zuerst.
        // Weggescrollt: Kommt die echte zurück (YouTube meldet sie ~0,2 s bevor sie sichtbar hochfährt), fährt unsere
        // sofort herein und ist so vor der echten oben. Fährt die echte noch raus, bleibt unsere unten.
        val previousTop = lastObservedTop
        val realOut = observedTop > rest.top + SCROLL_OUT_PX
        val realRising = previousTop >= 0 && observedTop < previousTop - 1
        if (!isTablet && (overlay.isScrolledAway || realGoneByScroll)) {
            // Zurück nur, wenn die echte wirklich wieder hochfährt oder ganz da ist (nicht bei gleicher Meldung: sonst
            // fuhr unsere hin und her, gemessen 15.11.).
            // Gleich nach unserem frühen Wegfahren steht die echte noch an der Ruhelage (ihre Animation beginnt erst).
            if ((!realOut && !inEarlyGrace(now)) || realRising) {
                realGoneByScroll = false
                if (overlay.isScrolledAway) {
                    // Steht die echte schon ganz da (YouTube setzt sie z. B. bei einem Tipp ohne Animation zurück),
                    // unsere sofort ganz; fährt sie noch hoch, unsere an derselben Stelle ihrer Kurve.
                    val instant = !realOut
                    reason("echte kommt zurück (top=$observedTop), unsere ${if (instant) "sofort" else "an ihrer Stelle"}")
                    if (instant) overlay.scrollBackNow() else overlay.scrollRise(risePhaseForTop(observedTop, rest))
                }

            }
        } else if (scrollFollowPage() && realOut && previousTop >= 0 && observedTop > previousTop &&
            // Ganz draussen: auch nach einer längeren Lücke (eine volle Suche hielt den Haupt-Thread an, gemessen 19.11.:
            // unsere blieb dann stehen, obwohl YouTubes längst weg war).
            (now - lastObservedAt < FRESH_OBSERVATION_MS || observedTop >= screen.bottom - 2 || previousTop <= rest.top + SCROLL_OUT_PX) &&
            touchScrollNow(now) && overlay.isShowing
        ) {

            // Die echte fährt (noch in der Oberfläche) nach unten raus: unsere hinterher.
            reason("echte fährt beim Scrollen raus (top=$observedTop), unsere mit")
            followAway(awayPhaseForTop(observedTop, rest), learn = true)
        }
        val visibleNow = observedTop < screen.bottom - 2
        val wasHidden = lastObservedTop < 0 || lastObservedTop >= screen.bottom - 2
        val appearing = visibleNow && wasHidden
        val movingUp = visibleNow && lastObservedTop >= 0 && observedTop < lastObservedTop - 1
        lastObservedTop = observedTop
        lastObservedAt = now
        if (appearing || movingUp) {
            reason("echte taucht auf (top=$observedTop)")
            hiddenByScroll = false
            overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            if (observedTop <= rest.top + 1) overlay.scrollBackNow() else overlay.scrollRise(risePhaseForTop(observedTop, rest))
            return
        }
        if (watchPageOpen) {
            reason("Video offen")
            hideSmooth()
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
        // Finger nach dem Tipp wieder aufgelegt (z. B. Tipp auf "Ton aus", dann gescrollt): kein Video, unsere bleibt.
        val videoOpening = now - lastNavAt < NAV_LINK_MS && navFromRest && !held && lastTouchAt <= lastNavAt
        if (onHome) {
            if (observedTop <= rest.top + 2) hiddenByScroll = false
            if (videoOpening && fullyGone) {
                videoSlideAt = now
                hideSmooth()
            } else {
                if (!fullyGone || overlay.isShowing) hiddenByScroll = observedTop > rest.top + 2
                overlay.show(rest.top, rest, state.copy(style = currentStyle()))
            }
            return
        }
        // Andere Seiten: Mini-Player offen oder Video geöffnet -> mit der echten Leiste mitfahren
        // (YouTube setzt den Mini-Player direkt über seine Leiste). Sonst stehen bleiben.
        // Früher fuhr unsere hier mit, wenn ein Mini-Player offen war (damit er frei bleibt). Gemessen 15.11. (Abos,
        // Mini-Player, schnell wischen): unsere fuhr mit runter und sprang dann bei "weggescrollt und entfernt" wieder
        // hoch – das "Herunterfahren ohne Grund", das Niklas sah. Jetzt bleibt sie wie auf der Startseite einfach stehen.
        val follow = videoOpening

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
        // Eben für ein Video weggefahren (z. B. Mini-Player aufgeklappt) und seither nicht gescrollt: YouTube entfernt
        // jetzt seine Leiste, die Liste dahinter gibt es noch. Das ist kein Scrollen – sonst blitzte unsere ~4 Bilder
        // über der Video-Seite auf, bis der Player erkannt war (gemessen 14.11., 1 von 6 Läufen).
        if (now - videoSlideAt < TAP_PROBE_MS && lastScrollAt < videoSlideAt) {
            goneFeed = null
            keepFail = "Video geht auf"
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
        // ⋮-Menü vorne: Die Liste ist nur verdeckt. Sonst galt der Tipp daneben als Seitenwechsel und unsere blieb weg.
        if (known != null && known.windowId == barWindowId && !barWindowFront) feedBeforeVideo = known
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
        if (restoredState && barWindowId == -1 &&
            (lastUiState?.selected == TabKind.HOME || lastUiState?.selected == TabKind.SUBSCRIPTIONS)
        ) {
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
            feedMissSince = 0L
            return true
        }
        // Läuft ein Video in der Liste an, baut YouTube die Liste um und sie ist kurz nicht abfragbar (auch mitten
        // im Wischen). Dann nicht gleich verstecken, sondern gleich nochmal nachsehen.
        if (feedMissSince == 0L || now - feedMissSince > SCROLL_GONE_MS) feedMissSince = now
        val justScrolled = now - scrollAt < SCROLL_GONE_MS && now - feedMissSince < FEED_GRACE_MS
        if ((hiddenByScroll && now - feedKeptAt < FEED_GRACE_MS) || justScrolled) {
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return true
        }
        keepFail = "keine Liste"
        return false
    }

    private fun coverLimit(): Int = if (overlay.isShowing) overlay.top else screen.bottom

    private fun currentStyle(): BarStyle = sampler.style(nightMode == Configuration.UI_MODE_NIGHT_YES).copy(modern = barModern)

    /** Leisten-Stil aus den Einstellungen (klassisch / modern), siehe [Prefs.barModern]. */
    private var barModern = false
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.BAR_LAYOUT) handler.post { onBarLayoutChanged() }
    }

    /** In der App umgestellt: sofort mit dem neuen Stil zeichnen (auch die Kopie an YouTubes Fenster). */
    private fun onBarLayoutChanged() {
        val modern = Prefs.barModern(this)
        if (modern == barModern) return
        barModern = modern
        val style = currentStyle()
        lastUiState = lastUiState?.copy(style = style)
        for ((key, value) in restBySize.toList()) restBySize[key] = value.first to value.second.copy(style = style)
        overlay.restyle(style)
        Log.d(TAG, "Leiste: Stil ${if (modern) "modern" else "klassisch"}")
    }

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
        if (b.contains(rest)) return null
        // Ein Dialog in der Mitte (YouTube 21.45: Suchfilter [80,279][1000,2248]) ragt ein Stück in die Leiste, ist aber
        // schmaler als sie: weiterhin ein Popup (sonst war unsere weg und die echte abgedunkelt mit Shorts zu sehen).
        if (Rect.intersects(b, rest) && b.left <= rest.left + POPUP_EDGE_PX && b.right >= rest.right - POPUP_EDGE_PX) return null
        return b.takeIf { it.width().toLong() * it.height() < screen.width().toLong() * screen.height() * 7 / 10 }
    }

    private fun windowBounds(windowId: Int): Rect =
        windows.firstOrNull { it.id == windowId }?.let { w -> Rect().also { w.getBoundsInScreen(it) } } ?: Rect(screen)

    /**
     * Welcher Tab gewählt ist. Melden mehrere "gewählt" (auf Mein YouTube kam Abos dazwischen, unsere sprang hin und
     * her), gewinnt Mein YouTube, dann Startseite. Im neuen Design liegt Abos oben als Reiter auf der Startseite.
     */
    private fun selectedTab(bar: FoundBar, root: AccessibilityNodeInfo, previous: TabKind?): TabKind? {
        val marked = bar.tabs.values.filter { it.selected }.map { it.kind }
        var pick = when {
            marked.size <= 1 -> marked.firstOrNull()
            else -> listOf(TabKind.YOU, TabKind.HOME, TabKind.SUBSCRIPTIONS, TabKind.SHORTS).firstOrNull { it in marked }
        }
        if (pick == null || pick == TabKind.HOME) {
            val top = scanner.findTopTab(root, screen, TabKind.SUBSCRIPTIONS, bar.tabs[TabKind.SUBSCRIPTIONS]?.label)
            if (top != null && scanner.isSelectedNear(top)) pick = TabKind.SUBSCRIPTIONS
            // Abos weggescrollt: Die Reiter oben sind dann weg, die Leiste meldet Startseite. Auswahl bleibt auf Abos.
            if (top == null && pick == TabKind.HOME && previous == TabKind.SUBSCRIPTIONS &&
                TabKind.SUBSCRIPTIONS !in bar.tabs
            ) pick = TabKind.SUBSCRIPTIONS
        }
        // Startseite getippt, YouTube zeigt kurz noch den gemerkten Reiter Abos: Auswahl springt nicht mit.
        if (pick == TabKind.SUBSCRIPTIONS && previous != null && SystemClock.uptimeMillis() - homeTappedAt < HOME_TAP_HOLD_MS) {
            pick = TabKind.HOME
        }
        if (pick != previous) Log.d(TAG, "Tab gewählt: $pick (Leiste meldet $marked)")
        return pick
    }

    private fun buildUiState(bar: FoundBar, root: AccessibilityNodeInfo, previous: TabKind?): BarUiState {
        // Feste Abstände wie im Vorlagenbild (ab Oberkante der Leiste).
        val density = resources.displayMetrics.density
        val rest = restBounds ?: bar.bounds
        return BarUiState(
            selected = selectedTab(bar, root, previous),
            labels = bar.tabs.mapNotNull { (kind, tab) -> tab.label?.let { kind to it } }.toMap(),
            subscriptionsBadge = bar.tabs[TabKind.SUBSCRIPTIONS]?.badge == true,
            hasCreate = TabKind.CREATE in bar.tabs,
            barHeightPx = rest.height(),
            iconCenterPx = (iconCenterDp * density).roundToInt(),
            labelTopPx = (labelTopDp * density).roundToInt(),
            showHandle = rest.bottom >= displayBounds().bottom - 2,
            style = currentStyle(),
        )
    }

    /** Höhe von Symbol-Mitte und Schrift-Oberkante: Tablet (Galaxy Tab A8 gemessen) etwas höher als auf dem Handy. */
    private val iconCenterDp get() = if (isTablet) TABLET_ICON_CENTER_DP else ICON_CENTER_DP
    private val labelTopDp get() = if (isTablet) TABLET_LABEL_TOP_DP else LABEL_TOP_DP

    /** Tippt auf ⋮ beim Shorts-Regal. Den Menüpunkt "Nicht interessiert" wählt [handleShelfMenu]. */
    private fun tryHideShelf(shelves: List<Shelf>, now: Long) {
        if (Protection.paused) return
        if (shelfHideBroken || !Prefs.autoHideShelf(this)) return
        val header = shelves.firstNotNullOfOrNull { it.menuButton }
        // Regal ohne eigenes ⋮ (z. B. "Abos"): Karten einzeln über ihr ⋮ → "Ausblenden" (Wunsch des Nutzers).
        val card = if (header == null) shelves.firstNotNullOfOrNull { it.cardMenuButtons.firstOrNull() } else null
        val interval = if (card != null) CARD_HIDE_INTERVAL_MS else SHELF_HIDE_INTERVAL_MS
        val button = header ?: card ?: return
        if (now - lastShelfHideAt < interval) {
            // Ohne neues Ereignis käme sonst kein Scan mehr (stille Seite): nächste Karte blieb stehen.
            handler.removeCallbacks(shelfRetry)
            handler.postDelayed(shelfRetry, interval - (now - lastShelfHideAt))
            return
        }
        lastShelfHideAt = now
        if (button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            shelfMenuOpenedAt = now
            shelfMenuForCard = card != null && header == null
            // So schnell wie möglich den Menüpunkt tippen: Das Menü soll man kaum sehen.
            handler.postDelayed({ scheduleScan() }, POLL_MS)
        } else if (card != null && ++cardClickFails >= CARD_CLICK_FAILS_MAX) {
            // Karten-⋮ reagiert nicht: in dieser Sitzung nicht weiter probieren (kein Dauertippen).
            shelfHideBroken = true
        }
    }

    private fun handleShelfMenu(now: Long) {
        val roots = windows.mapNotNull { it.root }.filter { it.packageName?.toString() == selectors.youtubePackage }
        // Karte: nur "Ausblenden" (nimmt den einen Short raus). Nie "Kein Interesse": Das gäbe es auch im Menü
        // eines normalen Videos, falls doch einmal das falsche ⋮ getroffen wurde.
        // Regal: Während die Liste läuft, kann das ⋮ der ersten Karte als ⋮ des Regals gelten, darum auch "Ausblenden".
        val labels = if (shelfMenuForCard) selectors.cardHideLabels else selectors.shelfHideLabels + selectors.cardHideLabels
        val item = scanner.findMenuItem(roots, labels)
        if (item != null) {
            shelfMenuOpenedAt = 0L
            shelfMenuDoneAt = now
            cardClickFails = 0
            val ok = click(item)
            if (ok) Prefs.incrementShelvesHidden(this)
            Log.d(TAG, "Regal: ${if (shelfMenuForCard) "Karte" else "Regal"} ausblenden: ${item.text ?: item.contentDescription} ($ok)")
            scheduleScan()
            return
        }
        if (now - shelfMenuOpenedAt < SHELF_MENU_TIMEOUT_MS) {
            handler.postDelayed({ scheduleScan() }, POLL_MS)
            return
        }
        // Menüpunkt nicht gefunden: Menü schliessen, Aufbau speichern, in dieser Sitzung nicht mehr probieren.
        Log.d(TAG, "Regal: Menüpunkt nicht gefunden, aufgegeben")
        shelfMenuOpenedAt = 0L
        // Karten: Läuft in der Liste ein Video an, kommt das Menü manchmal sehr spät. Nicht gleich ganz aufgeben.
        if (!shelfMenuForCard || ++cardClickFails >= CARD_CLICK_FAILS_MAX) shelfHideBroken = true
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
        // Schutz pausiert (vom Nutzer, nach der Wartezeit): Shorts dürfen laufen, unsere Leiste bleibt aber.
        if (Protection.paused) return true
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
        if (kind == TabKind.CREATE) {
            createTappedAt = SystemClock.uptimeMillis()
            // Plus öffnet die Kamera. Die erscheint erst ~300 ms später; bis dahin bleibt unsere Leiste stehen,
            // sonst sieht man so lange die echte mit Shorts-Knopf. Sie verschwindet, sobald die echte Leiste
            // aus YouTube verschwindet (siehe updateBar). Dafür ab jetzt jedes Bild prüfen.
            fastUntil = SystemClock.uptimeMillis() + FAST_WINDOW_MS
            startPolling()
            // Tablet: Die Kamera braucht dort ~0,9 s, bis sie aufgeht (Galaxy Tab A8). So lange stand unsere Leiste
            // ("viel zu lange", Wunsch von Niklas). Darum fährt sie sofort weg und bleibt kurz weg; dafür ist die
            // echte Leiste bis zur Kamera zu sehen. Geht die Kamera nicht auf, kommt unsere danach wieder.
            // Handy: Die ganze Leiste wegfahren zeigte darunter 4–6 Bilder die echte Leiste (Video vom 14.11.). Darum
            // sinken nur die Knöpfe sofort weg (reagiert sofort, mit Animation), die Fläche bleibt und deckt die echte
            // ab, bis die Kamera darüber ist; dann versteckt der Scan unsere wie vorher.
            if (isTablet) overlay.slideAway(VIDEO_SLIDE_MS, CREATE_SLIDE_HOLD_MS, fast = true)
            else overlay.sinkContent(CREATE_SINK_RESTORE_MS)
            // Handy: Den echten Knopf im Hintergrund klicken. Der Klick (und die Suche nach der Leiste) wartet auf
            // YouTube, oft 50–200 ms; auf dem Haupt-Thread stand so lange unsere Animation still (gemessen 15.11.:
            // die Knöpfe sanken gar nicht sichtbar). Klappt es nicht, läuft der alte Weg unten.
            val known = lastBar?.nodes?.get(TabKind.CREATE)
            if (!isTablet && known != null) {
                val seq = tapSeq
                clicker.execute {
                    val ok = runCatching { click(known) }.getOrDefault(false)
                    handler.post {
                        tapLog("$kind: Klick ${if (ok) "ok" else "fehlgeschlagen"} (Hintergrund)")
                        if (!ok && seq == tapSeq) youTubeMainRoot()?.let { clickTab(kind, it) }
                        scheduleScan()
                    }
                }
                return
            }
        }
        val root = youTubeMainRoot() ?: run {
            tapLog("$kind: kein YouTube-Fenster gefunden")
            return
        }
        clickTab(kind, root)
    }

    /** Sucht den echten Knopf und klickt ihn (mit allen Umwegen: weggescrollte Leiste, Abos-Reiter oben, Finger-Tipp). */
    private fun clickTab(kind: TabKind, root: AccessibilityNodeInfo) {
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
        if (node == null && kind == TabKind.SUBSCRIPTIONS && bar != null && openSubsFromTop(root, bar)) return
        if (node == null) {
            tapLog("$kind: Tab nicht gefunden (Leiste da)")
            dumper.dump(root, windows, "tab-$kind-nicht-gefunden")
            tapThroughIfVisible(kind)
            return
        }
        val clicked = click(node)
        tapLog("$kind: Klick ${if (clicked) "ok" else "fehlgeschlagen"}")
        if (clicked && kind == TabKind.HOME) {
            homeTappedAt = SystemClock.uptimeMillis()
            leaveSubsTopTabSoon(tapSeq, 0)
        }
        if (!clicked) {
            tapThroughIfVisible(kind)
        } else if (kind != TabKind.CREATE) {
            verifyLater(kind)
        }
        scheduleScan()
    }

    /**
     * Neues Design: Abos fehlt in der Leiste und ist oben ein Reiter, den es nur auf der Startseite gibt
     * (ganz oben in der Liste). Von anderswo erst Startseite klicken, dann auf den Reiter warten.
     */
    private fun openSubsFromTop(root: AccessibilityNodeInfo, bar: FoundBar): Boolean {
        val kind = TabKind.SUBSCRIPTIONS
        val top = scanner.findTopTab(root, screen, kind, null)
        if (top != null && click(top)) {
            tapLog("$kind: nicht in der Leiste, Reiter oben geklickt")
            verifyLater(kind)
            scheduleScan()
            return true
        }
        val home = bar.nodes[TabKind.HOME] ?: return false
        // Ein "Vorhang" über der kurz sichtbaren Startseite (15.11.) sah wie ein Ladebildschirm aus: auf Wunsch
        // von Niklas wieder entfernt. Der Weg per Link (youtube.com/feed/subscriptions) legt Abos auf den Stapel von
        // "Mein YouTube" (danach zeigte dieser Tab Abos) und bleibt darum verworfen.
        if (!click(home)) return false
        tapLog("$kind: Reiter nicht da, erst Startseite")
        waitForTopTabThenClick(tapSeq, 0)
        scheduleScan()
        return true
    }

    /** Von You zurück merkt sich YouTube den Reiter Abos: gleich auf den Reiter Startseite, nicht erst nach [VERIFY_MS]. */
    private fun leaveSubsTopTabSoon(seq: Int, attempt: Int) {
        handler.postDelayed({
            if (seq != tapSeq) return@postDelayed
            val root = youTubeMainRoot() ?: return@postDelayed
            val subs = scanner.findTopTab(root, screen, TabKind.SUBSCRIPTIONS, null)
            when {
                subs != null && scanner.isSelectedNear(subs) ->
                    scanner.findTopTab(root, screen, TabKind.HOME, null)?.let { home ->
                        if (click(home)) tapLog("HOME: Reiter oben geklickt")
                        scheduleScan()
                    }
                attempt < HOME_TAP_TRIES -> leaveSubsTopTabSoon(seq, attempt + 1)
            }
        }, REVEAL_POLL_MS)
    }

    private fun waitForTopTabThenClick(seq: Int, attempt: Int) {
        val kind = TabKind.SUBSCRIPTIONS
        handler.postDelayed({
            if (seq != tapSeq) return@postDelayed
            val root = youTubeMainRoot() ?: return@postDelayed
            val top = scanner.findTopTab(root, screen, kind, null)
            when {
                top != null -> {
                    val clicked = click(top)
                    tapLog("$kind: Reiter oben geklickt (${if (clicked) "ok" else "fehlgeschlagen"})")
                    if (clicked) verifyLater(kind)
                    scheduleScan()
                }
                // Nach dem Start oder Scrollen fehlen die Reiter, bis die Liste ein Stück nach unten gezogen wird.
                attempt == TOP_TAB_NUDGE_FAST || attempt == TOP_TAB_NUDGE_FAST * 3 -> {
                    nudgeFeedDown()
                    waitForTopTabThenClick(seq, attempt + 1)
                }
                attempt < TOP_TAB_TRIES_FAST -> waitForTopTabThenClick(seq, attempt + 1)
                else -> tapLog("$kind: Reiter oben kam nicht")
            }
        }, if (attempt == 0) 0L else TOP_TAB_POLL_MS)
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
            val found = scanner.findBar(root, screenBounds())
            val node = found?.nodes?.get(kind)
            when {
                node == null && found != null && kind == TabKind.SUBSCRIPTIONS -> {
                    if (!openSubsFromTop(root, found)) tapLog("$kind: Reiter oben nicht erreichbar")
                }
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

    /** Kurzes, langsames Wischen nach unten: Reiter oben kommen, die Liste lädt nicht neu. */
    private fun nudgeFeedDown() {
        val x = screen.exactCenterX()
        val path = Path().apply {
            moveTo(x, screen.top + screen.height() * 0.45f)
            lineTo(x, screen.top + screen.height() * 0.57f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
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
        if (selectedTab(bar, root, null) == kind) {
            tapLog("$kind: ausgewählt")
            return
        }
        // Neues Design: Abos ist oben ein Reiter der Startseite, der Knopf unten tut dann nichts.
        if (kind == TabKind.SUBSCRIPTIONS) {
            val top = scanner.findTopTab(root, screen, kind, bar.tabs[kind]?.label)
            if (top != null && click(top)) {
                tapLog("$kind: Reiter oben geklickt")
                return
            }
        }
        // Auf Abos (Reiter oben) führt der Startseite-Knopf unten nicht zurück: Reiter "Startseite" oben klicken.
        if (kind == TabKind.HOME) {
            val top = scanner.findTopTab(root, screen, kind, bar.tabs[kind]?.label)
            if (top != null && click(top)) {
                tapLog("$kind: Reiter oben geklickt")
                return
            }
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
        DebugLog.add("Tipp: $line")
        if (!app.fshorts.BuildConfig.DEBUG) return // Play-Store-Version: keine Fehler-Dateien
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
        // Unsere eigene App ist vorne (nicht unsere Leiste): YouTube ist dann höchstens als Bild-in-Bild-Fenster da
        // und darf nicht als "vorne" gelten, sonst liegt unsere Leiste über unserer eigenen App.
        if (active != null && active.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
            active.root?.packageName?.toString() == packageName
        ) return null
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
        // Eben gedreht: Android meldet YouTubes Fenster noch kurz in der alten Ausrichtung (oder in Zwischengrössen
        // der Dreh-Animation). Das ist kein Splitscreen – sonst sprang unsere Leiste beim Zurückdrehen mehrmals weg
        // und wieder her (gemessen 15.11.: 2 × 3 Bilder echte Leiste).
        if (display != lastDisplay) {
            lastDisplay = Rect(display)
            displayChangedAt = SystemClock.uptimeMillis()
        }
        if (!isTablet && SystemClock.uptimeMillis() - displayChangedAt < ROTATE_SETTLE_MS) {

            splitBounds = null
            return
        }
        val main = youTubeMainWindow(windows)
        val b = main?.let { Rect().also { r -> it.getBoundsInScreen(r) } }
        val minSide = 300 * resources.displayMetrics.density
        splitBounds = b?.takeIf {
            !main.isInPictureInPictureMode && it.width() >= display.width() - 4 &&
                it.height() in (display.height() / 4)..(display.height() * 85 / 100)
        } ?: b?.takeIf {
            // Tablet: Splitscreen nebeneinander (quer) oder Pop-up-Ansicht (schwebendes Fenster). Vorher galt das
            // Fenster als Dialog: unsere lag abgedunkelt unten am Bildschirm, im Fenster die echte mit Shorts.
            isTablet && !main.isInPictureInPictureMode &&
                (it.width() < display.width() - 4 || it.height() < display.height() * 85 / 100) &&
                it.width() >= minSide && it.height() >= minSide
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
        if (open) {
            keyboardWasOpen = true
        } else if (keyboardWasOpen && !keyboardToPage) {
            keyboardToPage = true
            // "Suchen": Die Ergebnisse haben eine Leiste, die hinter der wegfahrenden Tastatur hervorkommt. Der Spiegel
            // liegt unter der Tastatur und deckt sie dort sofort ab (unsere kommt, sobald YouTube wieder vorne ist).
            if (!isTablet && splitBounds == null) overlay.showMirrorNow()
        }
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
        private const val DIALOG_CLOSE_FAST_MS = 600L
        private const val DIALOG_SHOWN_MS = 1_000L
        private const val POPUP_AFTER_MS = 1_000L
        private const val POLL_DURATION_MS = 500L
        private const val QUIET_FULL_SCAN_MS = 3_000L
        private const val NAV_LINK_MS = 1_500L
        private const val VERIFY_MS = 400L
        private const val FAST_WINDOW_MS = 1_500L
        private const val MINI_CHECK_MS = 400L
        private const val MINI_WALK_MS = 5_000L
        private const val DRAW_FIRST_MS = 50L
        private const val EDGE_SNAP_PX = 3
        /** Lücke unter der Leiste bis zu so viel dp gilt als "ganz unten" (3-Knopf-Navigation: 48 dp, bleibt). */
        private const val BOTTOM_SNAP_DP = 20
        private const val VIDEO_DUMP_INTERVAL_MS = 4_000L
        private const val PANE_DUMP_INTERVAL_MS = 2_000L
        private const val TAP_PROBE_MS = 1_500L
        private const val TAP_AFTER_VIDEO_MS = 800L
        private const val LOADING_RULE_MS = 5_000L
        private const val LOADING_HOLD_MS = 150L
        private const val SLIDE_AWAY_MS = 450L
        private const val VIDEO_SLIDE_MS = 280L

        // Struktur-Dumps nach Tipps (nur Test-Versionen) kosten 0,2–0,4 s Abfragezeit genau dann, wenn die Video-
        // Lade-Seite aufgeht: unsere Leiste fuhr dadurch ~0,4 s zu spät weg. Die Aufbauten sind bekannt → aus.
        private const val DIAGNOSTIC_DUMPS = false
        /**
         * Nur zum Messen am Kabel (Test-Versionen): jede Meldung (ausser Inhalt/Scrollen) ins Protokoll.
         * Einschalten: `adb shell setprop log.tag.FShortsTrace VERBOSE` (bis zum Neustart des Handys).
         */
        private const val TRACE_TAG = "FShortsTrace"


        private const val TABLET_DRAW_WAIT_MS = 500L
        private const val SCROLL_GONE_MS = 1_500L
        private const val FEED_GRACE_MS = 600L
        private const val TAP_STATE_MS = 800L
        private const val FOREGROUND_GAP_MS = 500L
        private const val SLOW_SCAN_MS = 150L
        private const val REVEAL_POLL_MS = 50L
        /** Handy: nach dem Zeigen höchstens so lange nicht abfragen, bis unsere Leiste gezeichnet ist. */
        private const val PHONE_DRAW_WAIT_MS = 60L
        /** Abos über den Reiter oben: jedes Bild nachsehen (vorher alle 50 ms, so sah man die Startseite länger). */
        private const val TOP_TAB_POLL_MS = 16L
        private const val REVEAL_TRIES = 20
        private const val HOME_TAP_TRIES = 8
        private const val HOME_TAP_HOLD_MS = 800L
        // gleiche Zeiten wie vorher (0,4 s / 1,2 s nudgen, 3 s aufgeben), aber jedes Bild nachsehen
        private const val TOP_TAB_NUDGE_FAST = 25
        private const val TOP_TAB_TRIES_FAST = 190
        private const val LINK_WINDOW_MS = 8_000L
        private const val LINK_END_MS = 400L
        private const val LINK_FIX_MS = 600L
        private const val LINK_SETTLE_MS = 2_000L
        private const val CLOSING_DP = 24f
        /** Panel, das höher als dieser Anteil des Bildschirms reicht, ist eine ganze Seite (Werbe-Video), kein Menü. */
        private const val BIG_SHEET_TOP = 0.45f
        private const val PANEL_DUMP_INTERVAL_MS = 20_000L
        private const val PANEL_DUMP_DELAY_MS = 1_500L
        private const val FINGERPRINT_MS = 200L
        // Android braucht ein paar Bilder, bis unsere Leiste Berührungen durchlässt.
        private const val PASS_THROUGH_DELAY_MS = 80L
        private const val ICON_CENTER_DP = 20f
        private const val TABLET_ICON_CENTER_DP = 16.1f
        private const val TABLET_LABEL_TOP_DP = 30f
        private const val LABEL_TOP_DP = 32.5f
        private const val REST_STABLE_MS = 300L
        private const val SAMPLE_AFTER_VIDEO_MS = 1500L
        private const val WATCHDOG_MS = 400L
        private const val BACK_COOLDOWN_MS = 700L
        /** Tastatur geht auf: so lange warten, bis sie sicher ganz offen ist, dann den Spiegel darunter legen. */
        private const val KEYBOARD_MIRROR_MS = 450L
        private const val HINT_WINDOW_MS = 10_000L
        private const val AUTO_DUMP_AFTER_MS = 2_000L
        private const val AUTO_DUMP_INTERVAL_MS = 20_000L
        private const val DEBUG_DUMP_INTERVAL_MS = 3_000L
        private const val SHELF_HIDE_INTERVAL_MS = 60_000L
        private const val CARD_HIDE_INTERVAL_MS = 1_500L
        private const val CARD_CLICK_FAILS_MAX = 3
        private const val SHELF_MENU_HOLD_MS = 500L
        private const val SHELF_MENU_TIMEOUT_MS = 3_000L
        private const val PRE_SHOW_HOLD_MS = 700L
        private val SAVE_EXIT_WORDS = listOf("speichern", "save", "enregistrer", "guardar", "salva", "sauvegarder")
        private const val CREATE_HOLD_MS = 1_500L
        private const val CREATE_SLIDE_HOLD_MS = 1_200L
        private const val CREATE_SINK_RESTORE_MS = 1_500L
        /** Meldet sich die Kamera, ist sie schon über der Leiste (gemessen 15.11.). Ein Bild Sicherheit. */
        private const val CAMERA_COVER_MS = 40L
        private const val X_CLOSE_WINDOW_MS = 300L
        private const val X_CLOSE_DELAY_MS = 80L




        /** Kamera geht zu: Unsere fährt nur dieses kurze Stück herein (die echte steht dort sofort ganz da). */
        private const val CLOSE_RISE_DP = 30f


        /** Video verlassen (Handy): Unsere fährt die letzten RISE_FROM_DP von unten herein, in RISE_MS. */
        private const val RISE_MAX_DP = 60f
        private const val RISE_MIN_DP = 18f
        /** Player höchstens so weit gerutscht = früh erkannt (ganze Fahrt); ab RISE_LATE_DP = spät (kurze Fahrt). */
        private const val RISE_EARLY_DP = 24f
        private const val RISE_LATE_DP = 140f
        private const val RISE_MS = 240L
        private const val HIGHER_REST_MS = 1_500L
        private const val SHORTER_REST_MS = 3_000L

        private const val PLAY_STORE_PKG = "com.android.vending"
        /** Google Play dunkelt YouTube auf ~50 % ab (gemessen 226 → 121). */
        private const val SHEET_APP_DIM = 0.5f
        private const val SHEET_APP_MAX_MS = 900L
        private const val SHEET_APP_COVER_MS = 150L
        /** Google Play dunkelt in ~150 ms ab: unsere Tinte hier genauso schnell (beim ⋮-Menü bleibt sie langsam). */
        private const val SHEET_APP_INK_MS = 140


        private const val SHEET_APP_REPEAT_MS = 3_000L
        /** YouTube dunkelt hinter dem Werbe-Menü-Panel auf ~50 % ab (gemessen 57 → 118). */
        private const val PANEL_DIM = 0.5f
        private const val PANEL_FROM_BAR_MS = 500L
        private const val POPUP_EDGE_PX = 8
        private const val PANEL_PROBE_MS = 250L
        /** Scrollen: unsere fährt in so vielen ms raus (die echte braucht ~50) bzw. wieder herein (die echte ~170). */
        // Scrollen wie YouTube 21.45 (gemessen 19.11.2026, siehe startDirectionProbe und BottomBarOverlay.scrollRise).
        const val PROBE_UP = 0
        const val PROBE_DOWN_GONE = 1
        const val PROBE_DOWN_EARLY = 2
        const val PROBE_OBSERVE = 3
        /** Fingerauflegen: Bewegt sich die Liste nach so vielen ms noch nicht (Tipp), Pause der vollen Suche aufheben. */
        private const val TAP_RELEASE_MS = 50L
        /** YouTubes Hereinfahren beginnt so viele ms nach dem auslösenden Bild (Liste springt > Touch-Slop). */
        private const val RISE_START_MS = 62L
        /** Unsere so viel früher als YouTubes (fährt sie später, sähe man die echte darüber). */
        private const val RISE_LEAD_MS = 10L
        private const val RISE_DONE_MS = 300L
        private const val RISE_TAU_MS = 45f
        private const val AWAY_MS = 125.0
        /** Wegfahren: unsere so viel später als die beobachtete echte (fährt sie früher, sähe man die echte darüber). */
        private const val AWAY_LAG_MS = 8.0
        /** Echte schon aus der Oberfläche (Wegfahren fertig): unsere beginnt so weit in der Kurve (holt rasch auf). */
        private const val AWAY_LATE_PHASE_MS = 60L
        /** Hochgescrollt ohne Messung (keine Berührung bekannt): unsere beginnt so weit in der Kurve. */
        private const val RISE_NO_PROBE_PHASE_MS = 60L
        /** Nach unserem frühen Wegfahren so lange "echte steht noch" nicht als Rückkehr werten (ihre Animation läuft). */
        private const val EARLY_GRACE_MS = 260L
        /** Früh-Messung (unsere steht): ohne Bewegung der Liste nach so vielen ms aufhören (Tipp, kein Wischen). */
        private const val EARLY_IDLE_MS = 160L
        private const val EARLY_STEP_MS = 8L
        /** Liste bewegt sich: so lange danach keine volle Suche (hielte unsere Animation an). */
        private const val SCAN_HOLD_SCROLL_MS = 220L
        /** Runterwischen mindestens so schnell (dp/ms), dann fährt unsere von selbst weg. YouTube: ab 23 px pro Bild = 1,38 px/ms
         * = 0,48 dp/ms. Unsere Messung schwankt um ~20 % (bei 1,2 px/ms einmal 1,44 gemessen, unsere fuhr zu früh: 15 Bilder echte),
         * darum deutlich darüber (1,8 px/ms). Dazwischen fährt unsere mit, sobald die echte sichtbar rausfährt (Abfrage). */
        private const val HIDE_SPEED_DP_MS = 0.63f
        /** Hochwischen: YouTubes Auslöser lag höchstens so lange vor unserer Erkennung (geschätzt). */
        private const val RISE_TRIG_BACK_MS = 100L
        /** Fingerauflegen, wo unsere mitfährt: so lange keine volle Suche (sie hielte die Animation an, gemessen 19.11.: 320 ms). */
        private const val TOUCH_SCAN_HOLD_MS = 100L

        private const val DIR_PROBE_MS = 450L
        private const val DIR_PROBE_DP = 6f
        private const val DIR_PROBE_STEP_MS = 8L
        /**
         * Hochwischen mindestens so schnell (dp/ms), dann kommt unsere. YouTube: ab 23 px pro Bild (1,38 px/ms); bei
         * ungleichmässigem Wischen schon ab ~1,2 px/ms. Etwas darunter (lieber zu früh als die echte sehen).
         */
        private const val DIR_SPEED_DP_MS = 0.435f
        private const val DIR_SPEED_WINDOW_MS = 50L
        private const val DIR_SPEED_MIN_DT_MS = 30L
        private const val DIR_SAMPLES = 16
        /** Messung endet, wenn sich die Liste so lange nicht bewegt hat (nach DIR_PROBE_MS), spätestens nach DIR_MAX_MS. */
        private const val DIR_STILL_MS = 220L
        private const val DIR_MAX_MS = 4_000L
        /** Langsam hoch: so oft zur Sicherheit nachsehen, ob die echte doch kommt. */
        private const val DIR_BAR_CHECK_MS = 60L
        /** Ohne Listen-Element: ab so vielen ms nach dem Finger direkt nach der echten sehen. */
        private const val DIR_BAR_FIRST_MS = 40L
        /** So lange schon langsam hoch: ein plötzlicher Sprung muss von der nächsten Messung bestätigt werden. */
        private const val DIR_CONFIRM_AFTER_MS = 120L
        /** Schwung in die falsche Richtung: so selten nachsehen, bis die Liste steht. */
        private const val DIR_PASSIVE_STEP_MS = 40L
        /** Ohne Messung: eine Scroll-Meldung mit so viel Weg nach oben gilt als schnelles Hochwischen. */
        private const val SCROLL_EVENT_FAST_DP = 60f
        private const val PRESENCE_STEP_MS = 25L
        private const val PRESENCE_BUSY_STEP_MS = 16L

        private const val PRESENCE_BUSY_MS = 1_500L
        private const val GONE_TOUCH_SLACK_MS = 100L
        private const val FRESH_OBSERVATION_MS = 120L



        private const val PRESENCE_SLOW_STEP_MS = 60L
        private const val PRESENCE_FAST_FOR_MS = 10_000L

        private const val PRESENCE_IDLE_MS = 300L
        private const val SAMPLE_CALM_MS = 1_500L
        private const val IDLE_AFTER_TOUCH_MS = 1_200L
        private const val IDLE_SCAN_RETRY_MS = 200L





        private const val SCROLL_TOUCH_MS = 1_500L
        /** Die echte gilt als "raus", sobald sie so viele px tiefer steht als in Ruhe. */
        private const val SCROLL_OUT_PX = 6

        private const val SCROLL_SCAN_HOLD_MS = 700L
        private const val SCROLL_SCAN_RETRY_MS = 50L


        private const val PANEL_BRIGHTEN_MS = 90







        private const val SHEET_APP_RETURN_MS = 120_000L


        private const val ROTATE_SETTLE_MS = 800L

        private const val PANE_BURST_MS = 250L
        private const val PANE_BURST_POLL_MS = 5L

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
        private const val SEARCH_HOLD_MS = 900L
        private val SEARCH_LABELS = setOf(
            "suchen", "search", "rechercher", "buscar", "pesquisar", "cerca", "zoeken", "szukaj", "ara", "поиск",
            "пошук", "検索", "검색", "搜索", "搜尋", "بحث", "खोजें", "telusuri", "tìm kiếm", "ค้นหา", "sök", "søg", "søk",
            "haku", "hledat", "căutare", "keresés", "αναζήτηση",
        )
        private val CLOSE_LABELS = listOf(
            "schließen", "schliessen", "close", "zurück", "navigate up", "nach oben", "fermer", "cerrar", "fechar",
            "chiudi", "sluiten", "zamknij", "kapat", "закрыть", "закрити", "閉じる", "닫기", "关闭", "關閉", "إغلاق",
            "बंद करें", "tutup", "đóng", "ปิด", "stäng", "luk", "lukk", "sulje", "zavřít", "închide", "bezárás", "κλείσιμο",
        )

        @Volatile
        var running = false
            private set
    }
}
