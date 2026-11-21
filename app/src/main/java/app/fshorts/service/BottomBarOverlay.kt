package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp

import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.material.icons.filled.Home as FilledHome
import androidx.compose.material.icons.filled.Subscriptions as FilledSubscriptions
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Subscriptions as OutlinedSubscriptions

/**
 * Was unsere Leiste zeigt. Die Positionen (in Pixeln, ab Oberkante der Leiste) kommen von der echten
 * YouTube-Leiste, damit Icons und Schrift genau gleich hoch sitzen.
 */
data class BarUiState(
    val selected: TabKind?,
    val labels: Map<TabKind, String>,
    val subscriptionsBadge: Boolean,
    val hasCreate: Boolean,
    val barHeightPx: Int,
    val iconCenterPx: Int?,
    val labelTopPx: Int?,
    /** Unsere Leiste reicht bis ganz unten (Gestensteuerung): Gestenstrich selbst zeichnen. */
    val showHandle: Boolean,
    val style: BarStyle,
    /** Ist ein Menü offen, dunkelt YouTube alles ab: unsere Leiste genauso stark (0 = normal). */
    val dim: Float = 0f,
    /** Wo das Menü über unserer Leiste liegt (Fensterkoordinaten): dort ist unsere Leiste durchsichtig. */
    val hole: Rect? = null,
    /**
     * Benachrichtigungen werden über YouTube aufgezogen: Android verwischt und tönt alles darunter. Statt der
     * verwischten echten Leiste zeigen wir dort eine ruhige Fläche in dieser Farbe (ARGB). null = normal.
     */
    val veil: Int? = null,
)

/**
 * Unsere eigene untere Leiste. Liegt als Fenster genau über der echten YouTube-Leiste und hat keinen Shorts-Knopf.
 */
class BottomBarOverlay(
    private val service: AccessibilityService,
    private val onTap: (TabKind) -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var uiState by mutableStateOf<BarUiState?>(null)
    /** Leiste war versteckt: Abdunklung beim Wiederzeigen sofort richtig, ohne Animation (sonst sähe man sie verblassen). */
    private var dimInstant by mutableStateOf(true)
    /** Plus getippt: Knöpfe sinken weg, die Fläche bleibt (deckt die echte Leiste ab, bis die Kamera da ist). */
    private var sunk by mutableStateOf(false)
    /** Versteckt: Knöpfe beim nächsten Zeigen sofort wieder oben, ohne Animation. */
    private var sinkSnap by mutableStateOf(false)
    /** Ein Panel liegt ab dieser Höhe (px ab Oberkante unserer Leiste) über ihr: darunter nichts zeichnen. */
    private var coverFromPx by mutableFloatStateOf(Float.MAX_VALUE)
    /** Dauer, mit der sich die Tinte beim Aufhellen zurückzieht (⋮-Menü: INK_OUT_MS, Werbe-Menü: schneller). */
    private var inkOutMs by mutableStateOf(INK_OUT_MS)
    /** Dauer, mit der sich die Tinte beim Abdunkeln ausbreitet (⋮-Menü: INK_IN_MS, Google Play: schneller). */
    private var inkInMs by mutableStateOf(INK_IN_MS)
    /** Handy hochkant: so viele px ist unsere höher als die echte (Inhalt um diesen Rand tiefer gesetzt). */
    private var topExtra by mutableStateOf(0)
    /** Handy quer: flache Leiste, Wert = Höhe der Gestenleiste darunter. -1 = normale Leiste. */
    private var compactNav by mutableStateOf(-1)
    private val restoreSink = Runnable {
        if (handOver) {
            // Keine Kamera gekommen: unsere wieder zeigen (Plus ohne Drehen), der Spiegel läuft wieder mit.
            handOver = false
            sinkSnap = true
            mirrorFrozen = false
            view?.removeCallbacks(mirrorWatch)
        }
        sunk = false
        syncMirror()
    }
    /** Plus getippt, der Spiegel zeigt die Leiste: unsere zeichnet nichts (siehe [sinkContent]). */
    private var handOver by mutableStateOf(false)
    /** Plus getippt: bis dann sinken die Knöpfe (so lange keine Abfragen an YouTube, sie hielten die Animation an). */
    private var sinkUntil = 0L
    /** Video verlassen: Die Leiste fährt von unten herein. So viele Pixel steht sie gerade noch zu tief. */
    private var risePx by mutableFloatStateOf(0f)
    private var riser: android.animation.ValueAnimator? = null
    private var optimistic: TabKind? = null
    private var optimisticAt = 0L

    private val target = Rect()
    private var passThrough = false
    /** Flags, die das Fenster gerade wirklich hat. */
    private var appliedFlags = 0
    private var underMenu = false
    private var ignoreTapsUntil = 0L
    private var slide: android.animation.Animator? = null

    var isShowing = false
        private set(value) {
            if (value && !field) shownAt = SystemClock.uptimeMillis()
            field = value
        }
    /** Wann unsere Leiste zuletzt sichtbar wurde (Android braucht danach ein paar ms, um sie zu zeichnen). */
    var shownAt = 0L
        private set

    /** Wann Android unsere Leiste zuletzt gezeichnet hat (zum Abwarten vor langen Scans, siehe Dienst). */
    var drawnAt = 0L
        private set

    /** Oberkante unserer Leiste auf dem Bildschirm (nur gültig, wenn [isShowing]). */
    val top: Int get() = target.top

    private val params = WindowManager.LayoutParams(
        0,
        0,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        windowAnimations = 0
        title = "FuckShortsBar"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /**
     * Zeigt die Leiste mit Oberkante bei [top]. [rest] ist die Lage der echten Leiste, wenn sie ganz
     * eingefahren ist (Breite, Höhe).
     */
    fun show(top: Int, rest: Rect, state: BarUiState) {
        // Fährt gerade weg (Tastatur, Video): nicht mittendrin zurückholen. Danach zeigt der nächste Durchgang sie
        // wieder, falls nötig.
        if (isSlidingAway || SystemClock.uptimeMillis() < holdHiddenUntil) return
        underMenu = false
        place(top, rest, state.copy(dim = 0f, hole = null, veil = null))
    }

    /**
     * Ein Panel (Werbe-Menü) liegt über dem unteren Teil der Leiste: nur oberhalb von [panelTop] (Bildschirm) zeichnen,
     * abgedunkelt um [dim] (Tinte breitet sich von der Mitte aus). Berührungen gehen durch.
     */
    fun showCoveredBy(rest: Rect, state: BarUiState, dim: Float, panelTop: Int) {
        underMenu = true
        // YouTube hellt nach dem Werbe-Menü in ~0,13 s auf (gemessen): unsere genauso schnell.
        if (inkOutMs != PANEL_INK_OUT_MS) inkOutMs = PANEL_INK_OUT_MS
        val cx = rest.centerX() - rest.left
        place(rest.top, rest, state.copy(dim = dim, hole = Rect(cx, 0, cx, 0), veil = null))
        val cover = (panelTop - rest.top + topExtraFor(rest)).toFloat()
        if (cover != coverFromPx) coverFromPx = cover
    }

    /**
     * Zeigen und dabei aufhellen: erstes Bild abgedunkelt um [dim], dann hellt die Leiste in [ms] auf (z. B. nach dem
     * Werbe-Menü im Takt von YouTubes Seite). Berührungen gehen gleich wieder.
     */
    fun showFromDim(rest: Rect, state: BarUiState, dim: Float, ms: Int) {
        if (isSlidingAway || SystemClock.uptimeMillis() < holdHiddenUntil) return
        underMenu = true
        if (inkOutMs != ms) inkOutMs = ms
        val cx = rest.centerX() - rest.left
        place(rest.top, rest, state.copy(dim = dim, hole = Rect(cx, 0, cx, 0), veil = null))
        val v = view ?: return
        val shownAt = this.shownAt
        // Ein Bild später aufhellen (dann ist die Abdunklung sicher gezeichnet und die Tinte steht auf "dunkel").
        v.postDelayed({
            if (!isShowing || this.shownAt != shownAt || uiState?.dim == 0f) return@postDelayed
            underMenu = false
            place(rest.top, rest, state.copy(dim = 0f, hole = null, veil = null))
        }, FRAME_MS)
    }

    /** Anderer Stil (z. B. klassisch ↔ modern in den Einstellungen): sofort so zeichnen. */
    fun restyle(style: BarStyle) {
        val s = uiState ?: return
        if (s.style != style) uiState = s.copy(style = style)
        mirrorDirty = true
    }

    /** Beim Scrollen weggefahren: so viele Pixel steht der Inhalt tiefer (0 = ganz da). */

    private var scrollPx by mutableFloatStateOf(0f)
    private var scroller: android.animation.ValueAnimator? = null
    private var scrollTarget = 0f

    /** Fährt gerade beim Scrollen raus oder herein (solange keine langen Abfragen, sie hielten die Animation an). */
    val isScrollMoving: Boolean get() = scroller != null || SystemClock.uptimeMillis() < scrollQuietUntil
    private var scrollQuietUntil = 0L

    /** Beim Scrollen (ganz oder teilweise) nach unten weggefahren. Berührungen gehen dann durch. */

    val isScrolledAway: Boolean get() = scrollTarget > 0f

    /**
     * Die echte Leiste fährt beim Runterscrollen weg: unsere genau wie YouTubes (gemessen 19.11., Pixel 9, YouTube
     * 21.45, Bild für Bild mit unsichtbarer eigener Leiste): Fläche beschleunigt raus (+1, +4, +12, +30, +69, +117 px pro
     * Bild, ≈ Höhe·(t/125 ms)^2,9), Knöpfe nur halb so weit und gegen Ende schrumpfend (ab ~58 ms, weg bei ~92 ms).
     * [phaseMs]: so viele ms ist YouTubes Animation schon unterwegs (unsere beginnt an derselben Stelle der Kurve).
     */
    fun scrollAway(phaseMs: Long = 0L) {
        val h = target.height().toFloat()
        if (!isShowing || scrollTarget >= h) return
        cancelPendingRise()
        // Kommt sie gerade herein und sind die Knöpfe schon zu sehen: YouTube springt dann sofort in die Ruhelage und fährt
        // von dort weg (gemessen 19.11.). Sonst an der Stelle der Kurve weiterfahren, an der sie steht.
        val risingVisible = scroller != null && scrollTarget == 0f && scrollPop > 0f
        val from = if (scrollPx > 0f && !risingVisible) AWAY_MS * Math.pow((scrollPx / h).toDouble(), 1.0 / AWAY_POW).toFloat() else 0f
        runScroll(rise = false, start = maxOf(phaseMs.toFloat(), from), end = AWAY_MS)
    }

    /**
     * Die echte kommt zurück (schnell hochgewischt): unsere wie YouTubes (gemessen 19.11.): Fläche kommt von ganz unten
     * und bremst gleichmässig ab (Rest ≈ Höhe·e^(−t/45 ms), nach ~55 ms noch ~30 %), Knöpfe nur halb so weit, sie wachsen
     * dabei von klein auf (20–112 ms, ohne Ausblenden). [phaseMs]: so weit ist YouTubes Animation schon (negativ = beginnt
     * erst so viele ms später). Steht unsere schon höher, fährt sie von dort weiter (nie wieder runter).
     */
    fun scrollRise(phaseMs: Long) {
        if (scrollTarget == 0f && scroller == null) return
        val h = target.height().toFloat()
        cancelPendingRise()
        // Fährt sie gerade weg und beginnt YouTubes Hereinfahren erst später: erst fertig wegfahren, dann von ganz unten
        // kommen (YouTube bricht sein Wegfahren nicht ab, gemessen 19.11.).
        if (scroller != null && scrollTarget > 0f && phaseMs < 0L) {
            val v = view
            if (v != null) {
                val r = Runnable { pendingRise = null; scrollRise(0L) }
                pendingRise = r
                v.postDelayed(r, -phaseMs)
                return
            }
        }
        val from = when {
            scrollPx >= h -> Float.NEGATIVE_INFINITY
            scrollPx <= 0.5f -> RISE_END_MS
            else -> RISE_TAU_MS * kotlin.math.ln(h / scrollPx)
        }
        runScroll(rise = true, start = maxOf(phaseMs.toFloat(), from), end = RISE_END_MS)
    }

    private var pendingRise: Runnable? = null

    private fun cancelPendingRise() {
        pendingRise?.let { view?.removeCallbacks(it) }
        pendingRise = null
    }

    /** Die echte steht schon ganz da (z. B. Zurück-Taste, Seitenwechsel): unsere sofort ganz da. */
    fun scrollBackNow() {
        cancelPendingRise()
        if (scrollTarget == 0f && scroller == null) return
        scroller?.cancel()
        scroller = null
        scrollTarget = 0f
        scrollPx = 0f
        scrollPop = 1f
        applyFlags()
        syncMirror()
    }

    /** Grösse der Knöpfe beim Weg-/Hereinfahren (1 = normal). */
    private var scrollPop by mutableFloatStateOf(1f)
    private var popper: android.animation.ValueAnimator? = null

    /** Lage und Knopfgrösse für Zeitpunkt [e] (ms ab Beginn von YouTubes Animation). */
    private fun scrollAt(rise: Boolean, e: Float, h: Float) {
        if (rise) {
            scrollPx = if (e <= 0f) h else if (e >= RISE_END_MS) 0f else h * kotlin.math.exp(-e / RISE_TAU_MS)
            // Abbremsend wie YouTubes Knöpfe (gemessen: 0,46 → 0,70 → 0,89 → 1,0 in aufeinanderfolgenden Bildern).
            val x = ((e - RISE_POP_DELAY_MS) / RISE_POP_MS).coerceIn(0f, 1f)
            scrollPop = 1f - (1f - x) * (1f - x)
        } else {
            val x = (e / AWAY_MS).coerceIn(0f, 1f)
            scrollPx = h * Math.pow(x.toDouble(), AWAY_POW).toFloat()
            val s = ((e - AWAY_POP_DELAY_MS) / AWAY_POP_MS).coerceIn(0f, 1f)
            scrollPop = 1f - Math.pow(s.toDouble(), 1.5).toFloat()
        }
    }

    private fun runScroll(rise: Boolean, start: Float, end: Float) {
        val h = target.height().toFloat()
        scrollTarget = if (rise) 0f else h
        scroller?.cancel()
        scroller = null
        val s = start.coerceAtLeast(if (rise) -RISE_WAIT_MAX_MS else 0f)
        if (s >= end) {
            scrollAt(rise, end, h)
            scrollQuietUntil = SystemClock.uptimeMillis() + SCROLL_QUIET_AFTER_MS
            applyFlags()
            syncMirror()
            return
        }
        scrollAt(rise, s, h)
        val ms = (end - s).toLong().coerceAtLeast(1L)
        scrollQuietUntil = SystemClock.uptimeMillis() + ms + SCROLL_QUIET_AFTER_MS
        scroller = android.animation.ValueAnimator.ofFloat(s, end).apply {
            duration = ms
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { scrollAt(rise, it.animatedValue as Float, h) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (scroller === animation) {
                        scroller = null
                        scrollAt(rise, end, h)
                    }
                    syncMirror()
                }
            })
            start()
        }
        applyFlags()
    }

    /** Nur fürs Test-Protokoll: Zustand der Leiste. */
    fun debugState(): String = "zeigt=$isShowing alpha=${view?.alpha} ty=${view?.translationY} scroll=$scrollPx/$scrollTarget " +
        "slideAway=$isSlidingAway hold=${holdHiddenUntil - SystemClock.uptimeMillis()} rise=$risePx"

    /** Fährt gerade von unten herein ([showRising]). Solange keine Abfragen an YouTube: sie hielten die Animation an. */
    val isRising: Boolean get() = riser != null

    /** Die Knöpfe sind weggesunken oder sinken gerade (Plus getippt). */
    val isSunk: Boolean get() = sunk

    /** So lange sinken die Knöpfe noch (0 = fertig). */
    fun sinkRemainingMs(): Long = if (sunk) (sinkUntil - SystemClock.uptimeMillis()).coerceAtLeast(0L) else 0L

    /** Die Knöpfe sinken gerade weg ([sinkContent]). */

    val isSinking: Boolean get() = sunk && SystemClock.uptimeMillis() < sinkUntil

    /**
     * Wie [show], aber die Leiste fährt das letzte Stück ([fromPx]) von unten herein statt einfach dazustehen
     * (Video verlassen). YouTubes eigene Leiste fährt dort in ~0,3 s von ganz unten hoch; unsere beginnt weiter oben
     * und bremst stark ab, so liegt sie in jedem Bild über der echten. War sie schon da, bleibt sie einfach stehen.
     */
    fun showRising(rest: Rect, state: BarUiState, fromPx: Float, durationMs: Long) {
        if (isSlidingAway || SystemClock.uptimeMillis() < holdHiddenUntil) return
        val wasShowing = isShowing && view?.alpha != 0f
        underMenu = false
        if (!wasShowing && view != null) {
            riser?.cancel()
            risePx = fromPx
            riser = android.animation.ValueAnimator.ofFloat(fromPx, 0f).apply {
                duration = durationMs
                // Gleichmässig abklingend (jedes Bild ~2/3 des Rests), wie YouTubes Leiste, nur schneller.
                interpolator = android.animation.TimeInterpolator { x ->
                    val end = Math.exp(-RISE_DECAY)
                    ((1.0 - (Math.exp(-RISE_DECAY * x) - end) / (1.0 - end))).toFloat()
                }
                addUpdateListener { risePx = it.animatedValue as Float }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (riser !== animation) return
                        riser = null
                        risePx = 0f
                        syncMirror()
                    }
                })
                start()
            }
        }
        place(rest.top, rest, state.copy(dim = 0f, hole = null, veil = null))
    }

    /** Legt das Fenster unsichtbar an (Dienst-Start), damit das erste Zeigen so schnell ist wie jedes spätere. */
    fun warmUp(rest: Rect, state: BarUiState) {
        if (view != null) return
        uiState = state.copy(dim = 0f, hole = null, veil = null)
        setTarget(rest.top, rest)
        applyBounds()
        params.flags = currentFlags()
        appliedFlags = params.flags
        create().also {
            it.alpha = 0f
            windowManager.addView(it, params)
        }
    }

    private fun stopRise() {
        val running = riser
        riser = null
        running?.cancel()
        risePx = 0f
    }

    /**
     * Benachrichtigungen offen: nur eine Fläche in der Farbe des Schleiers, Berührungen gehen durch (sie gehören
     * dann den Benachrichtigungen).
     */
    fun showVeiled(rest: Rect, state: BarUiState, veil: Int, panel: Rect? = null) {
        underMenu = true
        val hole = panel?.let { Rect(it).apply { offset(-rest.left, -rest.top + topExtraFor(rest)) } }
        place(rest.top, rest, state.copy(dim = 0f, hole = hole, veil = veil))
    }

    /**
     * Ein Menü (⋮, Teilen) ist offen und liegt über der Leiste. Unsere Leiste liegt aber immer zuoberst.
     * Darum: abgedunkelt wie YouTube den Rest, mit einem Loch, wo das Menü ist. Berührungen gehen durch
     * (Tipp neben das Menü schliesst es, wie bei YouTube).
     */
    fun showUnderMenu(rest: Rect, state: BarUiState, dim: Float, sheet: Rect, inkMs: Int = INK_IN_MS) {
        underMenu = true
        if (inkMs != inkInMs) inkInMs = inkMs
        if (inkOutMs != INK_OUT_MS) inkOutMs = INK_OUT_MS
        val hole = Rect(sheet).apply { offset(-rest.left, -rest.top + topExtraFor(rest)) }
        place(rest.top, rest, state.copy(dim = dim, hole = hole, veil = null))
    }

    /**
     * Ein Dialog (z. B. Suchfilter) deckt die ganze Leiste ab: unsere fährt sanft nach unten weg. Das Fenster bleibt
     * dabei an seiner Stelle und "sichtbar", so ist sie nach dem Schliessen mit [show] sofort wieder ganz da.
     */
    fun slideDown(rest: Rect, state: BarUiState) {
        underMenu = true
        place(rest.top, rest, state.copy(dim = 0f, hole = null, veil = null), keepSlide = true)
        val v = view ?: return
        if (slide != null || v.translationY > 0f) return
        slide = android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, rest.height().toFloat()).apply {
            duration = SLIDE_MS
            interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (slide === animation) slide = null
                    syncMirror()
                }
            })
            start()
        }
    }

    /** Fährt gerade weg (Scans blockieren das Zeichnen, darum solange keine). */
    val isSliding: Boolean get() = slide != null

    /** Nach dem Wegfahren eine Weile nicht wieder zeigen (z. B. bis die Tastatur gemeldet wird). */
    private var holdHiddenUntil = 0L

    fun releaseHold() {
        holdHiddenUntil = 0L
    }

    /** Fährt gerade weg und verschwindet danach ganz ([slideAway]). */
    var isSlidingAway = false
        private set

    /**
     * Sanft nach unten wegfahren und danach verstecken (Tastatur geht auf, Video wird geöffnet) – statt einfach
     * zu verschwinden. Berührungen gehen dabei schon durch.
     */
    fun slideAway(durationMs: Long, holdMs: Long = 0L, fast: Boolean = false) {
        val v = view ?: return
        if (!isShowing || slide != null) return
        holdHiddenUntil = SystemClock.uptimeMillis() + durationMs + holdMs
        isSlidingAway = true
        underMenu = true
        applyFlags()
        slide = android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, v.translationY, target.height().toFloat()).apply {
            duration = durationMs
            // fast: Video geht auf, die Lade-Seite fährt in ~0,3 s über die Leiste. Der sanfte Start (0.4/0/0.6/1) liess
            // sie noch darüber stehen; hier fährt sie sofort los und bremst erst am Ende.
            interpolator = if (fast) android.view.animation.PathInterpolator(0f, 0f, 0.2f, 1f)
            else android.view.animation.PathInterpolator(0.4f, 0f, 0.6f, 1f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (slide !== animation) return
                    slide = null
                    isSlidingAway = false
                    hide()
                }
            })
            start()
        }
    }

    /**
     * Versteckte Leiste schon an die neue Stelle legen (z. B. gedreht, während ein Video offen ist). Auf dem Tablet
     * dauerte das Neu-Einrichten beim Wiederzeigen sonst ~350 ms, so lange sah man die echte.
     */
    fun prepare(rest: Rect, state: BarUiState) {
        val v = view ?: return
        if (isShowing) return
        stopSlide()
        val next = state.copy(dim = 0f, hole = null, veil = null)
        if (next != uiState) uiState = next
        setTarget(rest.top, rest)
        if (applyBounds()) windowManager.updateViewLayout(v, params)
    }

    private fun stopSlide() {
        val running = slide
        slide = null
        isSlidingAway = false
        running?.cancel()
        view?.translationY = 0f
    }

    private fun place(top: Int, rest: Rect, state: BarUiState, keepSlide: Boolean = false) {
        if (!keepSlide) stopSlide()
        // Unsere zeigt wieder selbst: Spiegel folgt ihr wieder. War sie versteckt (YouTube weg, Kamera ...), hat Android
        // YouTubes Fenster oft neu aufgebaut (neue Fläche, gemessen 17.11.: Spiegel danach weg) → neu anheften.
        // Plus getippt (Spiegel zeigt die Leiste, Kamera kommt): Spiegel nicht anrühren.
        val reattach = !handOver && (mirrorFrozen || !isShowing || view?.alpha == 0f)
        if (mirrorFrozen && !handOver) {
            mirrorFrozen = false
            view?.removeCallbacks(mirrorWatch)
        }
        if (reattach && mirrorWindowId >= 0) view?.post { reattachMirror() }

        if (coverFromPx != Float.MAX_VALUE) coverFromPx = Float.MAX_VALUE
        if (fading) {
            fading = false
            view?.animate()?.cancel()
            view?.alpha = 1f
        }

        val keepOptimistic = optimistic != null &&
            SystemClock.uptimeMillis() - optimisticAt < OPTIMISTIC_MS &&
            state.selected != optimistic
        val next = if (keepOptimistic) state.copy(selected = optimistic) else state
        val instant = !isShowing || view?.alpha == 0f
        if (instant != dimInstant) dimInstant = instant
        if (next != uiState) uiState = next
        // Nur so hoch wie die echte Leiste: Bei Navigation mit 3 Knöpfen bleiben die Knöpfe frei.
        setTarget(top, rest)

        val v = view
        if (v == null) {
            isShowing = true
            applyBounds()
            params.flags = currentFlags()
            appliedFlags = params.flags
            create().also { windowManager.addView(it, params) }
            return
        }
        val moved = applyBounds()
        if (!isShowing) {
            // Nur sichtbar machen (siehe hide): erscheint schon im nächsten Bild. Berührbar wird sie ein Bild
            // später, damit Android das Zeichnen nicht mit dem Umstellen des Fensters aufhält.
            v.alpha = 1f
            isShowing = true
            if (!moved) {
                // Fährt sie gerade herein, erst gegen Ende: Das Umstellen des Fensters kostete sonst ein Bild der Fahrt.
                if (riser != null) v.postDelayed({ applyFlags() }, RISE_TOUCH_MS) else v.post { applyFlags() }
                return
            }
        }
        params.flags = currentFlags()
        if (moved || params.flags != appliedFlags) {
            appliedFlags = params.flags
            windowManager.updateViewLayout(v, params)
        }
        syncMirror()
    }


    /**
     * Lage des Fensters. Handy quer: YouTube zeigt dort gar keine eigene Leiste (21.45 gemessen), unsere ist allein und
     * flach (Symbol und Text nebeneinander, COMPACT_CONTENT_DP plus Gestenleiste) statt 207 px hoch. Wunsch von
     * Niklas: Sie bedeckte quer sehr viel vom Bildschirm.
     */
    private fun setTarget(top: Int, rest: Rect) {
        val nav = compactNavPx(rest)
        if (nav != compactNav) compactNav = nav
        if (nav < 0) {
            val extra = topExtraFor(rest)
            if (extra != topExtra) topExtra = extra
            target.set(rest.left, top - extra, rest.right, top + rest.height())
            return
        }
        if (topExtra != 0) topExtra = 0
        val d = service.resources.displayMetrics.density
        val h = (COMPACT_TOTAL_DP * d).toInt()
        val screenBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.bottom
        } else {
            service.resources.displayMetrics.heightPixels
        }
        val bottom = minOf(top + rest.height(), screenBottom)
        target.set(rest.left, bottom - h, rest.right, bottom)
    }

    /**
     * Handy hochkant: unsere ist [TOP_EXTRA_DP] höher als die echte (Wunsch von Niklas: "nur ein ganz kleines bisschen
     * höher als das Original"); so deckt sie auch die oberste Kante der durchsichtigen echten Leiste sicher ab. Die Knöpfe
     * bleiben an derselben Stelle (der Inhalt wird um diesen Rand nach unten gesetzt).
     */
    private fun topExtraFor(rest: Rect): Int {
        if (service.resources.configuration.smallestScreenWidthDp >= 600 || compactNavPx(rest) >= 0) return 0
        return (TOP_EXTRA_DP * service.resources.displayMetrics.density).toInt()
    }

    /** Handy quer: Höhe der Gestenleiste unten (mindestens 0) = flache Leiste. Sonst -1. */
    private fun compactNavPx(rest: Rect): Int {
        // Aus (17.11.2026): YouTube 21.45 zeigt quer doch eine eigene Leiste (207 px, mit Shorts-Knopf), je nach Lage
        // (am 15.11. war sie beim Messen wohl gerade weggescrollt). Die flache Leiste (40 dp) deckte nur die untere
        // Hälfte ab, darüber sah man die Symbole der echten. Darum quer gleich hoch wie die echte.
        if (!COMPACT_LANDSCAPE) return -1

        val cfg = service.resources.configuration
        if (cfg.smallestScreenWidthDp >= 600 || cfg.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) return -1
        val nav = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        } else {
            0
        }
        val d = service.resources.displayMetrics.density
        return if ((COMPACT_TOTAL_DP * d).toInt() < rest.height()) nav else -1
    }

    private fun applyFlags() {
        val v = view ?: return
        val flags = currentFlags()
        // Spiegel zuerst aus, wenn unsere gleich Berührungen durchlässt (sonst finge er sie statt YouTube).
        syncMirror()
        if (flags == appliedFlags) return
        params.flags = flags
        appliedFlags = flags
        windowManager.updateViewLayout(v, params)
    }

    /**
     * Plus getippt (Handy): Die Knöpfe sinken sofort nach unten weg und blenden aus, die Fläche bleibt stehen. So
     * reagiert die Leiste sofort, aber die echte Leiste darunter (mit Shorts-Knopf) sieht man nie. Versteckt wird
     * erst, wenn die Kamera darüber ist (wie vorher). Geht keine Kamera auf, kommen die Knöpfe nach [restoreMs] zurück.
     */
    fun sinkContent(restoreMs: Long) {
        val v = view ?: return
        if (!isShowing) return
        if (mirrorShown && mirrorWindowId >= 0 && !mirrorFrozen && !mirrorRect.isEmpty) {
            // Spiegel da (Handy, Android 14+): Er liegt in YouTubes Fenster genau unter unserer und zeigt dasselbe. Wie bei
            // YouTube dreht sich nichts: Unsere zeichnet nichts mehr (fängt aber weiter alle Tipps ab), der Spiegel bleibt
            // stehen, und die Kamera fährt genau wie beim Original über die Leiste. Vorher drehte sich Plus zum "×" und
            // sprang nach der Kamera zurück ("unsatisfying", Niklas 17.11.).
            mirrorFrozen = true
            mirrorFrozenFrontSince = 0L
            v.removeCallbacks(mirrorWatch)
            v.postDelayed(mirrorWatch, MIRROR_WATCH_MS)
            handOver = true
            sinkSnap = false
            sunk = true
            sinkUntil = SystemClock.uptimeMillis()
            v.removeCallbacks(restoreSink)
            v.postDelayed(restoreSink, restoreMs)
            return
        }
        sinkSnap = false
        sunk = true
        sinkUntil = SystemClock.uptimeMillis() + SINK_MS
        v.removeCallbacks(restoreSink)
        v.postDelayed(restoreSink, restoreMs)
    }

    /** Blendet in [ms] aus und versteckt danach (Überblendung mit einem darüber einblendenden Fenster). */
    fun fadeOut(ms: Long) {
        val v = view ?: return
        if (!isShowing) return
        fading = true
        v.animate().cancel()
        v.animate().alpha(0f).setDuration(ms).setStartDelay(0L).withEndAction {
            if (fading) {
                fading = false
                hide()
            }
        }.start()
    }

    private var fading = false

    /** Blendet gerade aus ([fadeOut]). */
    val isFading: Boolean get() = fading

    fun hide() {
        // Plus getippt, die Kamera kommt gleich: Der Spiegel bleibt stehen, bis sie darüber ist (sonst sah man beim
        // Verstecken 4 Bilder die echte Leiste, gemessen 17.11.).
        if (handOver) {
            hideKeepMirror()
            return
        }
        // YouTube selbst zeigt etwas ohne Leiste (Video, Tastatur ...): Spiegel auch weg.
        if (mirrorFrozen) {
            mirrorFrozen = false
            view?.removeCallbacks(mirrorWatch)
        }
        setMirrorShown(false)
        hideInternal()
    }

    private fun hideInternal() {
        val v = view ?: return
        handOver = false
        if (fading) {
            fading = false
            v.animate().cancel()
        }
        view?.removeCallbacks(restoreSink)
        if (sunk) {
            sinkSnap = true
            sunk = false
        }
        if (!isShowing) return
        stopSlide()
        stopRise()
        // Versteckt: beim nächsten Zeigen ganz da (nicht weggescrollt).
        cancelPendingRise()
        scroller?.cancel()
        scroller = null
        scrollTarget = 0f
        scrollPx = 0f
        popper?.cancel()
        popper = null
        scrollPop = 1f
        isShowing = false
        // Versteckt nie abgedunkelt zurücklassen: Sonst kam sie beim nächsten Zeigen erst dunkel und hellte auf
        // (gemessen 15.11. nach Google Play: 6 Bilder).
        if (uiState?.dim != 0f || uiState?.hole != null) {
            dimInstant = true
            uiState = uiState?.copy(dim = 0f, hole = null)
        }

        // Durchsichtig statt unsichtbar: Ein unsichtbares Fenster richtet Android beim Wiederzeigen neu ein
        // (1–2 Bilder später, z. B. beim Verlassen eines Videos sah man so die echte Leiste kurz).
        v.alpha = 0f
        // Fenster-Flags erst im nächsten Bild umstellen: Das Umstellen richtet das Fenster neu ein, und Android zeigte
        // das Ausblenden erst danach (gemessen 15.11.: unsere stand so noch 3 Bilder über dem Werbe-Menü).
        v.post { if (!isShowing) applyFlags() }
        syncMirror()
    }

    /**
     * Lässt Berührungen kurz durch unsere Leiste hindurch (für den Fallback-Tipp auf die echte Leiste).
     * Android setzt das erst ein paar Bilder später um. Kommt unser künstlicher Tipp früher an, landet er
     * auf unserer Leiste – in einer anderen Spalte (4 statt 5 Knöpfe). Darum Tipps eine Weile ignorieren.
     */
    fun setPassThrough(enabled: Boolean) {
        passThrough = enabled
        if (enabled) ignoreTapsUntil = SystemClock.uptimeMillis() + IGNORE_AFTER_PASS_THROUGH_MS
        applyFlags()
    }

    fun destroy() {
        mirrorSurface?.let { sc -> runCatching { android.view.SurfaceControl.Transaction().reparent(sc, null).apply() } }
        runCatching { mirrorCanvasSurface?.release() }
        runCatching { mirrorSurface?.release() }
        mirrorCanvasSurface = null
        mirrorSurface = null
        mirrorBitmap = null
        mirrorWindowId = -1
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        lifecycleOwner?.destroy()
        view = null
        lifecycleOwner = null
        isShowing = false
    }

    private fun currentFlags(): Int =
        if (!isShowing || passThrough || underMenu || scrollTarget > 0f) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS

    private fun applyBounds(): Boolean {
        // Immer über die ganze Bildschirmbreite ab dem linken Rand: So kann die Leiste nie seitlich verrutschen.
        // Ausser YouTube ist schmaler (Tablet: Pop-up-Ansicht, Splitscreen nebeneinander): dann genau so breit.
        val displayWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            service.resources.displayMetrics.widthPixels
        }
        // Fast ganze Breite = ganze Breite (gemeldete Lage kann 1 px daneben liegen: sonst Strich am Rand).
        val fullWidth = target.width() >= displayWidth - 6
        val x = if (fullWidth) 0 else target.left
        val width = if (fullWidth) WindowManager.LayoutParams.MATCH_PARENT else target.width()
        val y = target.top
        if (params.x == x && params.y == y && params.width == width && params.height == target.height()) {
            return false
        }
        params.x = x
        params.y = y
        params.width = width
        params.height = target.height()
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Spiegel: eine zweite, gleich gezeichnete Leiste, an YouTubes Fenster geheftet (ab Android 14,
    // attachAccessibilityOverlayToWindow). Sie bewegt sich mit YouTubes Fenster, wenn Android es animiert (App wechseln
    // per Gestenleiste, App-Übersicht): Dann steht unser eigenes Fenster fest bzw. ist weg, und man sah im
    // wegrutschenden YouTube die echte Leiste (gemessen 16.11.: 4 Bilder beim Wegwischen, 10 beim Zurückwischen).
    // Sonst liegt der Spiegel genau unter unserer Leiste (gleiche Lage, gleicher Inhalt) und ist nicht zu sehen. Er ist
    // nur an, solange unsere Leiste ganz normal steht; beim Verlassen von YouTube bleibt er, wie er war ("eingefroren").
    // ---------------------------------------------------------------------------------------------------------------
    private var mirrorSurface: android.view.SurfaceControl? = null
    private var mirrorCanvasSurface: android.view.Surface? = null
    private var mirrorBitmap: android.graphics.Bitmap? = null
    private var mirrorWindowId = -1
    private var mirrorShown = false
    private val mirrorRect = Rect()
    /** Inhalt neu malen, sobald unsere Leiste normal steht (nach jedem Zeichnen unserer Leiste). */
    private var mirrorDirty = true
    private var mirrorDrawPosted = false
    /** YouTube ist nicht vorne (andere App, Kamera ...): Spiegel bleibt, wie er beim Verlassen war. */
    private var mirrorFrozen = false
    /** Der Spiegel stand schon einmal an unserer Stelle (vorher liegt er oben links). */
    private var mirrorPlaced = false
    private var mirrorFrozenFrontSince = 0L
    /** Vom Dienst gesetzt: Ist YouTubes Hauptfenster vorne? (Eingefrorener Spiegel darf dann nicht ewig stehen bleiben.) */
    var youTubeFront: () -> Boolean = { false }

    /** Der Spiegel steht eingefroren und sichtbar unter einem anderen Fenster (Kamera, Google Play ...). */
    val mirrorCovering: Boolean get() = mirrorFrozen && mirrorShown && mirrorWindowId >= 0

    /** Spiegel an YouTubes Hauptfenster [windowId] heften (-1 = keiner, z. B. Splitscreen, Bild-in-Bild). */

    fun mirrorWindow(windowId: Int) {
        if (Build.VERSION.SDK_INT < 34 || windowId == mirrorWindowId) return
        if (windowId < 0) {
            mirrorWindowId = -1
            mirrorFrozen = false
            setMirrorShown(false)
            return
        }
        if (view == null) return
        val sc = mirrorSurface ?: createMirror() ?: return
        try {
            service.attachAccessibilityOverlayToWindow(windowId, sc)
            mirrorWindowId = windowId
            android.util.Log.d("FuckShorts", "Spiegel an YouTubes Fenster $windowId geheftet")
        } catch (e: Exception) {
            android.util.Log.w("FuckShorts", "Spiegel anheften ging nicht", e)
            mirrorWindowId = -1
            return
        }
        // Neues Fenster: erst zeigen, wenn unsere Leiste normal steht.
        mirrorFrozen = false
        mirrorShown = true
        setMirrorShown(false)
        mirrorDirty = true
        syncMirror()
    }

    /** Nochmals an dasselbe Fenster heften (dessen Fläche kann neu sein), sofort und zur Sicherheit etwas später. */
    private fun reattachMirror() {
        val sc = mirrorSurface ?: return
        if (mirrorWindowId < 0 || Build.VERSION.SDK_INT < 34) return
        val id = mirrorWindowId
        val attach = Runnable {
            if (mirrorWindowId != id || mirrorSurface !== sc) return@Runnable
            runCatching { service.attachAccessibilityOverlayToWindow(id, sc) }
                .onFailure { android.util.Log.w("FuckShorts", "Spiegel neu anheften ging nicht", it) }
            if (app.fshorts.BuildConfig.DEBUG && android.util.Log.isLoggable("FShortsTrace", android.util.Log.VERBOSE)) {
                android.util.Log.d("FuckShorts", "Spiegel neu angeheftet ($id), zeigt=$mirrorShown")
            }
            // Sichtbarkeit und Lage neu setzen (gilt für die neue Fläche).
            val shown = mirrorShown
            mirrorShown = !shown
            setMirrorShown(shown)
            if (!mirrorRect.isEmpty) android.view.SurfaceControl.Transaction()
                .setPosition(sc, mirrorRect.left.toFloat(), mirrorRect.top.toFloat()).apply()
        }
        attach.run()
        view?.postDelayed(attach, MIRROR_REATTACH_MS)
    }

    /**
     * Eine reine Bildfläche (kein Fenster): nimmt keine Berührungen an und gilt für Android nicht als Fenster über YouTube.
     * Ein Fenster (SurfaceControlViewHost) liess YouTubes echte Leiste für die Bedienungshilfen "verdeckt" erscheinen
     * (Lage 2424 statt 2217, gemessen 17.11.: Scrollen wie das Original ging so nicht mehr).
     */
    private fun createMirror(): android.view.SurfaceControl? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            val w = maxOf(1, target.width())
            val h = maxOf(1, target.height())
            val sc = android.view.SurfaceControl.Builder()
                .setName("FuckShortsSpiegel")
                .setBufferSize(w, h)
                .setFormat(PixelFormat.TRANSLUCENT)
                .setOpaque(false)
                .build()
            android.view.SurfaceControl.Transaction().setVisibility(sc, false).apply()
            mirrorSurface = sc
            mirrorCanvasSurface = android.view.Surface(sc)
            mirrorRect.set(0, 0, w, h)
            mirrorDirty = true
            sc
        } catch (e: Exception) {
            android.util.Log.w("FuckShorts", "Spiegel anlegen ging nicht", e)
            null
        }
    }

    /** Malt unsere Leiste (so wie sie gerade steht) in die Fläche des Spiegels. */
    private fun drawMirror() {
        mirrorDrawPosted = false
        val v = view ?: return
        val surface = mirrorCanvasSurface ?: return
        if (!mirrorWanted() || mirrorFrozen || v.width <= 0 || v.height <= 0) return
        val w = mirrorRect.width()
        val h = mirrorRect.height()
        if (w != v.width || h != v.height) {
            // Lage stimmt noch nicht mit dem Fenster überein (wird gleich gesetzt): beim nächsten Zeichnen.
            mirrorDirty = true
            return
        }
        try {
            val bmp = mirrorBitmap?.takeIf { it.width == w && it.height == h }
                ?: android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { mirrorBitmap = it }
            bmp.eraseColor(0)
            drawingMirror = true
            try {
                v.draw(android.graphics.Canvas(bmp))
            } finally {
                drawingMirror = false
            }
            val c = surface.lockHardwareCanvas()
            c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
            c.drawBitmap(bmp, 0f, 0f, null)
            surface.unlockCanvasAndPost(c)
            mirrorDirty = false
        } catch (e: Exception) {
            android.util.Log.w("FuckShorts", "Spiegel malen ging nicht", e)
        }
    }

    /** Steht unsere Leiste ganz normal (nichts fährt, nichts abgedunkelt, berührbar)? Nur dann gilt der Spiegel. */
    private fun mirrorWanted(): Boolean {
        val v = view ?: return false
        val s = uiState ?: return false
        return isShowing && !passThrough && !underMenu && !sunk && !fading && slide == null && riser == null &&
            scrollTarget == 0f && scroller == null && popper == null && scrollPop == 1f && coverFromPx == Float.MAX_VALUE && compactNav < 0 &&
            s.dim == 0f && s.hole == null && s.veil == null && v.alpha == 1f && v.translationY == 0f
    }

    /** Spiegel an Lage und Zustand unserer Leiste angleichen (bei jedem Zeichnen und jeder Änderung). */
    private fun syncMirror(drawn: Boolean = false) {
        val sc = mirrorSurface ?: return
        if (mirrorWindowId < 0 || mirrorFrozen) return
        if (drawn) mirrorDirty = true
        val want = mirrorWanted()
        if (want) {
            val displayWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.width()
            } else {
                service.resources.displayMetrics.widthPixels
            }
            val w = if (params.width == WindowManager.LayoutParams.MATCH_PARENT) displayWidth else params.width
            val r = Rect(params.x, params.y, params.x + w, params.y + params.height)
            if (r != mirrorRect) {
                val t = android.view.SurfaceControl.Transaction()
                if (r.width() != mirrorRect.width() || r.height() != mirrorRect.height()) {
                    t.setBufferSize(sc, r.width(), r.height())
                    mirrorDirty = true
                }
                mirrorRect.set(r)
                t.setPosition(sc, r.left.toFloat(), r.top.toFloat()).apply()
                mirrorPlaced = true
            }
            // Erst den Inhalt malen, dann zeigen (sonst stünde ein Bild lang ein alter oder leerer Inhalt da).
            if (mirrorDirty && !mirrorDrawPosted) {
                mirrorDrawPosted = true
                view?.post { drawMirror(); syncMirror() }
                if (!mirrorShown) return
            }
        }
        setMirrorShown(want)
    }

    private fun setMirrorShown(shown: Boolean) {
        val sc = mirrorSurface ?: return
        if (shown == mirrorShown) return
        mirrorShown = shown
        if (app.fshorts.BuildConfig.DEBUG && android.util.Log.isLoggable("FShortsTrace", android.util.Log.VERBOSE)) {
            android.util.Log.d("FuckShorts", "Spiegel ${if (shown) "an" else "aus"}${if (mirrorFrozen) " (eingefroren)" else ""}")
        }
        android.view.SurfaceControl.Transaction().setVisibility(sc, shown).apply()
    }

    /** Eingefroren und YouTube wieder vorne, aber unsere kam nicht: nach [MIRROR_FRONT_MAX_MS] Spiegel weg. */
    private val mirrorWatch = object : Runnable {
        override fun run() {
            if (!mirrorFrozen) return
            val now = SystemClock.uptimeMillis()
            if (youTubeFront()) {
                if (mirrorFrozenFrontSince == 0L) mirrorFrozenFrontSince = now
                if (now - mirrorFrozenFrontSince > MIRROR_FRONT_MAX_MS) {
                    mirrorFrozen = false
                    syncMirror()
                    return
                }
            } else {
                mirrorFrozenFrontSince = 0L
            }
            view?.postDelayed(this, MIRROR_WATCH_MS)
        }
    }

    /**
     * Wie [hide], aber der Spiegel bleibt, wie er ist: YouTube wird von einem anderen Fenster verdeckt oder verlassen
     * (andere App, Kamera, Google Play, App wechseln). Seine Leiste liegt darunter unverändert. Fährt YouTube wieder
     * herein, zeigt der Spiegel unsere statt der echten.
     */
    /**
     * Spiegel sofort zeigen (mit dem zuletzt gemalten Bild), noch bevor unsere Leiste selbst kommt. Er liegt in YouTubes
     * Fenster, also UNTER einer sich schliessenden Tastatur: Nach "Suchen" kommt die echte Leiste hinter der
     * wegfahrenden Tastatur hervor (gemessen 18.11.: 3–4 schwache Bilder), unsere selbst läge dort über der Tastatur.
     */
    fun showMirrorNow() {
        if (mirrorSurface == null || mirrorWindowId < 0 || !mirrorPlaced || isShowing) {
            if (app.fshorts.BuildConfig.DEBUG && android.util.Log.isLoggable("FShortsTrace", android.util.Log.VERBOSE)) {
                android.util.Log.d("FuckShorts", "Spiegel jetzt: nein (fläche=${mirrorSurface != null} fenster=$mirrorWindowId platziert=$mirrorPlaced zeigt=$isShowing)")
            }
            return
        }
        mirrorFrozen = true
        mirrorFrozenFrontSince = 0L
        view?.removeCallbacks(mirrorWatch)
        view?.postDelayed(mirrorWatch, MIRROR_WATCH_MS)
        setMirrorShown(true)
    }

    fun hideKeepMirror(showMirror: Boolean = false) {
        // showMirror: Spiegel jetzt sicher zeigen (unsere war gerade nicht "normal", z. B. Plus gedreht oder abgedunkelt).
        // Er zeigt den Zustand nach dem Verstecken: nicht abgedunkelt, Knöpfe oben.
        if (showMirror && mirrorWindowId >= 0 && !mirrorRect.isEmpty && isShowing) {
            mirrorFrozen = false
            setMirrorShown(true)
        }
        if (mirrorWindowId >= 0 && !mirrorFrozen) {
            mirrorFrozen = true
            mirrorFrozenFrontSince = 0L
            view?.removeCallbacks(mirrorWatch)
            view?.postDelayed(mirrorWatch, MIRROR_WATCH_MS)
        }
        hideInternal()
    }


    private fun makeTapHandler(): View.OnTouchListener = TapHandler(onTap = tap@{ x, width ->
        if (SystemClock.uptimeMillis() < ignoreTapsUntil) return@tap
        // Klassisch 4 Spalten (Startseite, Abos, Mein YouTube, Plus), modern 3 (Startseite, Mein YouTube, Plus).
        val columns = if (uiState?.style?.modern == true) MODERN_COLUMNS else COLUMNS
        val n = columns.size
        val column = if (uiState?.style?.tablet == true) {
            // Tablet: enger Block in der Mitte (Spalten à 96 dp). Ausserhalb: kein Knopf.
            val slot = TABLET_SLOT_DP * service.resources.displayMetrics.density
            val start = (width - slot * n) / 2f
            val c = Math.floor(((x - start) / slot).toDouble()).toInt()
            if (c !in 0 until n) return@tap
            c
        } else {
            (x / width * n).toInt().coerceIn(0, n - 1)
        }
        val kind = columns[column]

        if (kind != TabKind.CREATE || uiState?.hasCreate == true) {
            if (kind != TabKind.CREATE) {
                optimistic = kind
                optimisticAt = SystemClock.uptimeMillis()
                uiState = uiState?.copy(selected = kind)
            }
            onTap(kind)
        }
    }, onLongPress = onLongPress)

    @Composable
    private fun BarContent() {
        uiState?.let { state ->
            androidx.compose.runtime.CompositionLocalProvider(LocalPop provides ScrollPop({ scrollPop }, { scrollPx })) {
            BottomBar(
                state = state,
                dimInstant = dimInstant,
                sunk = sunk,
                sinkSnap = sinkSnap,
                compactNav = compactNav,
                topExtra = topExtra,
                inkMs = inkInMs,
                inkOutMs = inkOutMs,
                cover = { coverFromPx },
                rise = { risePx + scrollPx },
                invisible = { handOver },
                onTap = { kind ->
                    if (kind != TabKind.CREATE) {
                        optimistic = kind
                        optimisticAt = SystemClock.uptimeMillis()
                        uiState = uiState?.copy(selected = kind)
                    }
                    onTap(kind)
                },
                onLongPress = onLongPress,
            )
            }
        }
    }

    private fun create(): ComposeView {
        val owner = OverlayLifecycleOwner().also { it.start() }
        lifecycleOwner = owner
        return ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            viewTreeObserver.addOnDrawListener {
                drawnAt = SystemClock.uptimeMillis()
                syncMirror(drawn = true)
            }
            // Berührungen selbst auswerten statt über Compose: einfacher und zuverlässiger in einem Overlay.
            // Die Leiste hat vier gleich breite Spalten: Startseite, Abos, Mein YouTube, Plus.
            setOnTouchListener(makeTapHandler())
            setContent { BarContent() }
            view = this
        }
    }

    companion object {
        /** Nur zum Messen (Test-Versionen): unsere Leiste unsichtbar zeichnen. */
        var ghost by mutableStateOf(false)
        /** Nur zum Testen (Test-Versionen): unsere unsichtbar, nur der Spiegel wird gezeichnet. */
        var mirrorTest by mutableStateOf(false)
        /** Gerade wird in den Spiegel gemalt (nicht ins eigene Fenster). */
        @JvmStatic var drawingMirror = false



        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        const val OPTIMISTIC_MS = 600L
        const val SLIDE_MS = 300L
        const val FRAME_MS = 40L
        const val SCROLL_QUIET_AFTER_MS = 150L
        /**
         * Scrollen wie YouTube 21.45 (gemessen 19.11.2026, Pixel 9, Bild für Bild): Hereinfahren = Rest·e^(−t/τ), fertig
         * nach [RISE_END_MS]; Knöpfe wachsen von [RISE_POP_DELAY_MS] an in [RISE_POP_MS] auf volle Grösse. Wegfahren =
         * Höhe·(t/[AWAY_MS])^[AWAY_POW]; Knöpfe schrumpfen ab [AWAY_POP_DELAY_MS] in [AWAY_POP_MS] auf nichts.
         */
        private const val RISE_TAU_MS = 45f
        private const val RISE_END_MS = 275f
        private const val RISE_POP_DELAY_MS = 40f
        private const val RISE_POP_MS = 70f
        /** Beginnt YouTubes Animation erst später: höchstens so lange warten (sonst sofort beginnen). */
        private const val RISE_WAIT_MAX_MS = 120f
        private const val AWAY_MS = 125f
        private const val AWAY_POW = 2.9
        private const val AWAY_POP_DELAY_MS = 58f
        private const val AWAY_POP_MS = 34f
        /** Profilbild: wird nicht kleiner, sondern oben abgedeckt (Kante so weit über seiner Oberkante) und rutscht höchstens so weit. */
        val AVATAR_CLIP_ABOVE_DP = 3.8f
        val AVATAR_SHIFT_MAX_DP = 2.1f
        /** Eingefrorener Spiegel, YouTube wieder vorne, unsere kam nicht: so lange darf er noch stehen. */
        const val MIRROR_FRONT_MAX_MS = 1_500L
        const val MIRROR_WATCH_MS = 250L
        const val MIRROR_REATTACH_MS = 600L



        const val RISE_TOUCH_MS = 110L

        const val IGNORE_AFTER_PASS_THROUGH_MS = 700L
        val COLUMNS = listOf(TabKind.HOME, TabKind.SUBSCRIPTIONS, TabKind.YOU, TabKind.CREATE)
    }

    /** Erkennt einen kurzen Tipp (Finger kaum bewegt) und langes Drücken. */
    private class TapHandler(
        private val onTap: (x: Float, width: Float) -> Unit,
        private val onLongPress: () -> Unit,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        private var cancelled = false
        private val longPress = Runnable {
            cancelled = true
            onLongPress()
        }

        override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
            val slop = 12 * v.resources.displayMetrics.density
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAt = SystemClock.uptimeMillis()
                    cancelled = false
                    v.postDelayed(longPress, 600)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) {
                        cancelled = true
                        v.removeCallbacks(longPress)
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (!cancelled) onTap(event.x, v.width.toFloat())
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    cancelled = true
                    v.removeCallbacks(longPress)
                }
            }
            return true
        }
    }
}

@Composable
private fun BottomBar(
    state: BarUiState,
    dimInstant: Boolean,
    sunk: Boolean,
    sinkSnap: Boolean,
    compactNav: Int,
    topExtra: Int,
    inkMs: Int,
    inkOutMs: Int,
    cover: () -> Float,
    rise: () -> Float,
    invisible: () -> Boolean = { false },
    onTap: (TabKind) -> Unit,
    onLongPress: () -> Unit,
) {
    val style = state.style
    val density = LocalDensity.current
    val barHeight = with(density) { state.barHeightPx.toDp() }
    val holeRadius = with(density) { 16.dp.toPx() }
    state.veil?.let { veil ->
        // Mit Loch: Tablet quer, Benachrichtigungen als Spalte in der Mitte – die bleibt frei.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    state.hole?.let { h ->
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(h.left.toFloat(), h.top.toFloat()),
                            size = Size(h.width().toFloat(), h.height().toFloat()),
                            cornerRadius = CornerRadius(holeRadius, holeRadius),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
                .background(Color(veil)),
        )
        return
    }
    // Abdunkeln (⋮-Menü, Dialog) nicht springen lassen: Die Dunkelheit breitet sich wie Tinte von der Stelle des
    // Menüs über die Leiste aus (Öffnen) und zieht sich dorthin zurück (Schliessen). Die Knöpfe ducken sich leicht
    // weg und federn beim Aufhellen zurück.
    val ink = remember { Animatable(if (state.dim > 0f) 1f else 0f) }
    var inkLevel by remember { mutableFloatStateOf(state.dim) }
    if (state.dim > 0f) inkLevel = state.dim
    // Ursprung der Tinte: Mitte des Menü-Lochs, sonst Mitte der Leiste (jeweils an der Oberkante)
    var inkOrigin by remember { mutableStateOf<Offset?>(null) }
    state.hole?.let { inkOrigin = Offset(it.exactCenterX(), 0f) }
    LaunchedEffect(state.dim > 0f) {
        val target = if (state.dim > 0f) 1f else 0f
        if (dimInstant) ink.snapTo(target)
        else ink.animateTo(target, tween(if (target > 0f) inkMs else inkOutMs, easing = FastOutSlowInEasing))

    }
    val sink = remember { Animatable(0f) }
    LaunchedEffect(sunk, sinkSnap) {
        when {
            // Plus getippt: Der Knopf wird federnd grösser und dreht sich zum "×" (sofort sichtbar, die übrige Leiste
            // bleibt stehen und deckt die echte ab, bis die Kamera darüber ist).
            sunk -> sink.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
            sinkSnap -> sink.snapTo(0f)
            else -> sink.animateTo(0f, tween(RISE_MS, easing = FastOutSlowInEasing))
        }
    }
    val duck by animateFloatAsState(
        targetValue = if (state.dim > 0f) 0.94f else 1f,
        animationSpec = if (state.dim > 0f) tween(INK_IN_MS, easing = FastOutSlowInEasing) else spring(dampingRatio = 0.45f, stiffness = 300f),
        label = "duck",
    )
    Box(
        modifier = Modifier
            .drawWithContent {
                // Nur zum Messen: unsere unsichtbar (die Logik läuft weiter).
                if (BottomBarOverlay.ghost) return@drawWithContent
                // Nur zum Testen des Spiegels: unsere eigene unsichtbar, der Spiegel bleibt.
                if (!BottomBarOverlay.drawingMirror && BottomBarOverlay.mirrorTest) return@drawWithContent
                // Plus getippt, der Spiegel in YouTubes Fenster zeigt die Leiste (Kamera fährt darüber).
                if (invisible()) return@drawWithContent
                // Panel über dem unteren Teil: nur darüber zeichnen (das Panel "verschluckt" die Leiste).
                val c = cover()
                if (c >= size.height) {
                    drawContent()
                } else if (c > 0f) {
                    clipRect(bottom = c) { this@drawWithContent.drawContent() }
                }
            }
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                // Video verlassen: Die ganze Leiste fährt das letzte Stück von unten herein.
                translationY = rise()
            }
            .drawWithContent {
                drawContent()
                // Eben erst (wieder) gezeigt: Abdunklung sofort richtig zeichnen, nicht auf die Animation warten (sonst
                // war das erste Bild noch hell bzw. noch dunkel, gemessen 15.11. beim Schliessen des Werbe-Menüs).
                val p = if (dimInstant) (if (state.dim > 0f) 1f else 0f) else ink.value

                if (p > 0.001f && inkLevel > 0f) {
                    val dark = Color.Black.copy(alpha = inkLevel)
                    if (p >= 0.999f) {
                        drawRect(dark)
                    } else {
                        val o = inkOrigin ?: Offset(size.width / 2f, 0f)
                        val far = maxOf(
                            Offset(0f, size.height).minus(o).getDistance(),
                            Offset(size.width, size.height).minus(o).getDistance(),
                        )
                        val r = (far * 1.25f * p).coerceAtLeast(1f)
                        drawRect(
                            Brush.radialGradient(
                                0f to dark,
                                0.78f to dark,
                                1f to Color.Transparent,
                                center = o,
                                radius = r,
                            ),
                        )
                    }
                }
                state.hole?.let { h ->
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(h.left.toFloat(), h.top.toFloat()),
                        size = Size(h.width().toFloat(), h.height().toFloat()),
                        cornerRadius = CornerRadius(holeRadius, holeRadius),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
            .background(style.background),
    ) {
        if (style.tablet && style.background == Color(0xFF0F0F0F)) {
            // Tablet, dunkel: YouTube zeichnet oben eine feine Trennlinie (2 px, #2B2B2B am Galaxy Tab A8 gemessen).
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(Color(0xFF2B2B2B)))
        }
        // Tablet: Die echte Leiste hat enge Spalten (96 dp) als Block in der Mitte; auf dem Handy teilen sich vier
        // gleich breite Spalten die ganze Breite.
        if (style.modern) {
            // Moderne Leiste (Wahl in den Einstellungen): nur Symbole, drei Knöpfe.
            if (compactNav >= 0) ModernCompactRow(
                state = state,
                height = (COMPACT_TOTAL_DP - COMPACT_HANDLE_DP).dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = duck
                    scaleY = duck
                },
                spin = { sink.value },
            ) else ModernRow(state, barHeight, topExtra, duck, spin = { sink.value })
        } else if (compactNav >= 0) CompactRow(
            state = state,
            height = (COMPACT_TOTAL_DP - COMPACT_HANDLE_DP).dp,
            modifier = Modifier.graphicsLayer {
                scaleX = duck
                scaleY = duck
            },
        ) else Row(
            modifier = Modifier.offset { IntOffset(0, topExtra) }.fillMaxWidth().height(barHeight).graphicsLayer {
                scaleX = duck
                scaleY = duck
                transformOrigin = TransformOrigin(0.5f, 0.35f)
            },
            horizontalArrangement = if (style.tablet) Arrangement.Center else Arrangement.Start,
        ) {
            val homeSelected = state.selected == TabKind.HOME
            val subsSelected = state.selected == TabKind.SUBSCRIPTIONS
            val slot: Modifier = if (style.tablet) Modifier.width(TABLET_SLOT_DP.dp) else Modifier.weight(1f)
            val iconSize = if (style.tablet) TABLET_ICON_DP.dp else 24.dp
            TabSlot(
                modifier = slot,
                kind = TabKind.HOME,
                label = state.labels[TabKind.HOME] ?: androidx.compose.ui.res.stringResource(app.fshorts.R.string.bar_label_home),
                state = state,
                onTap = onTap,
                onLongPress = onLongPress,
            ) {
                TabIcon(if (homeSelected) Icons.Filled.FilledHome else Icons.Outlined.OutlinedHome, style.content, iconSize)
            }
            TabSlot(
                modifier = slot,
                kind = TabKind.SUBSCRIPTIONS,
                label = state.labels[TabKind.SUBSCRIPTIONS] ?: androidx.compose.ui.res.stringResource(app.fshorts.R.string.bar_label_subscriptions),
                state = state,
                onTap = onTap,
                onLongPress = onLongPress,
            ) {
                Box {
                    TabIcon(
                        if (subsSelected) Icons.Filled.FilledSubscriptions else Icons.Outlined.OutlinedSubscriptions,
                        style.content,
                        iconSize,
                    )
                    if (state.subscriptionsBadge) {
                        val shift = 3.dp
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = shift, y = -shift)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(style.background)
                                .border(2.dp, style.background, CircleShape),
                        ) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF0033)),
                            )
                        }
                    }
                }
            }
            TabSlot(
                modifier = slot,
                kind = TabKind.YOU,
                label = state.labels[TabKind.YOU] ?: androidx.compose.ui.res.stringResource(app.fshorts.R.string.bar_label_you),
                state = state,
                onTap = onTap,
                onLongPress = onLongPress,
                avatarTop = state.style.avatar?.let { a -> state.iconCenterPx?.let { c -> c - a.height / 2f } },
            ) {
                val avatar = style.avatar
                if (avatar != null) {
                    // Genau der Ausschnitt aus der echten Leiste, in Originalgrösse.
                    val size = with(density) { avatar.width.toDp() }
                    val ring = if (state.selected == TabKind.YOU) {
                        Modifier.border(1.5.dp, style.content, CircleShape)
                    } else {
                        Modifier
                    }
                    // Der Ausschnitt kann YouTubes weissen Ring enthalten (abgeschaut, als Mein YouTube gewählt war):
                    // den Rand darum immer wegschneiden. Den Ring zeichnen wir selbst, nur wenn der Tab gewählt ist.
                    Box(Modifier.size(size).then(ring), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.None,
                            modifier = Modifier
                                .size(if (style.tablet) size else size - AVATAR_INSET * 2)
                                .clip(CircleShape),
                        )
                    }
                } else {
                    TabIcon(Icons.Filled.AccountCircle, style.content, iconSize)
                }
            }
            Box(
                modifier = slot.fillMaxHeight().popIn(originPx = with(density) { 25.5.dp.toPx() }),
            ) {
                if (state.hasCreate) {
                    // Wie im Vorlagenbild: Plus-Kreis etwas unter der Icon-Reihe (Mitte 25,5 dp ab Oberkante).
                    val plusCenter = with(density) { 25.5.dp.roundToPx() }
                    IconArea(plusCenter) {
                        // Runder Knopf, mittig in seiner Spalte: alle vier Elemente haben gleichen Abstand.
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    val p = sink.value
                                    scaleX = 1f + PLUS_GROW * p
                                    scaleY = 1f + PLUS_GROW * p
                                    rotationZ = PLUS_TURN * p
                                }
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(lerp(style.plusBackground, style.content, PLUS_LIGHTEN * sink.value.coerceIn(0f, 1f))),
                            contentAlignment = Alignment.Center,
                        ) {
                            val color = style.content
                            Canvas(Modifier.size(18.dp)) {
                                val stroke = 2.dp.toPx()
                                drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke, StrokeCap.Round)
                                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }
        // Gestenstrich zuletzt (oben drauf): Beim Weg- und Hereinfahren laufen die Knöpfe darunter durch wie beim Original
        // (dort liegt der Strich des Systems über YouTubes Leiste).
        if (state.showHandle || compactNav >= 0) {
            val handleBottom = if (compactNav >= 0) 5.dp else 10.dp
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = handleBottom)
                    // Der Gestenstrich fährt nicht mit herein: Er steht still wie der vom System.
                    .graphicsLayer { translationY = -rise() }
                    .size(width = 108.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(style.handle),
            )
        }
    }
}

/**
 * Handy quer: flache Leiste. Vier gleich breite Spalten, in jeder Symbol und Text nebeneinander (Platz ist quer genug
 * da), Symbole etwas kleiner (22 dp), Plus-Kreis 30 dp.
 */
@Composable
private fun CompactRow(state: BarUiState, height: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val style = state.style
    Row(modifier = modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        val slot = Modifier.weight(1f).fillMaxHeight()
        Row(slot, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TabIcon(if (state.selected == TabKind.HOME) Icons.Filled.FilledHome else Icons.Outlined.OutlinedHome, style.content, 22.dp)
            Spacer(Modifier.width(8.dp))
            Label(text = state.labels[TabKind.HOME] ?: androidx.compose.ui.res.stringResource(app.fshorts.R.string.bar_label_home), color = style.content)
        }
        Row(slot, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TabIcon(
                if (state.selected == TabKind.SUBSCRIPTIONS) Icons.Filled.FilledSubscriptions else Icons.Outlined.OutlinedSubscriptions,
                style.content,
                22.dp,
            )
            Spacer(Modifier.width(8.dp))
            Label(text = state.labels[TabKind.SUBSCRIPTIONS] ?: androidx.compose.ui.res.stringResource(app.fshorts.R.string.bar_label_subscriptions), color = style.content)
        }
        Row(slot, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val avatar = style.avatar
            if (avatar != null) {
                val ring = if (state.selected == TabKind.YOU) Modifier.border(1.5.dp, style.content, CircleShape) else Modifier
                Box(Modifier.size(24.dp).then(ring), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(21.dp).clip(CircleShape),
                    )
                }
            } else {
                TabIcon(Icons.Filled.AccountCircle, style.content, 22.dp)
            }
            Spacer(Modifier.width(8.dp))
            Label(text = state.labels[TabKind.YOU] ?: androidx.compose.ui.res.stringResource(app.fshorts.R.string.bar_label_you), color = style.content)
        }
        Box(slot, contentAlignment = Alignment.Center) {
            if (state.hasCreate) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(style.plusBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    val color = style.content
                    Canvas(Modifier.size(15.dp)) {
                        val stroke = 2.dp.toPx()
                        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke, StrokeCap.Round)
                        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSlot(
    modifier: Modifier,
    kind: TabKind,
    label: String,
    state: BarUiState,
    onTap: (TabKind) -> Unit,
    onLongPress: () -> Unit,
    avatarTop: Float? = null,
    icon: @Composable () -> Unit,
) {
    val labelTop = state.labelTopPx
    val center = state.iconCenterPx
    if (center != null && labelTop != null) {
        // Gleiche Höhe wie bei YouTube: Icon-Mitte und Schrift-Oberkante aus der echten Leiste.
        // Symbol und Schrift getrennt: Beim Profilbild wird nur das Bild oben abgedeckt, die Schrift schrumpft wie bei den
        // anderen Knöpfen (sonst stand beim Wegfahren kurz "Mein YouTube" allein da).
        Box(modifier = modifier.fillMaxHeight()) {
            Box(Modifier.matchParentSize().popIn(avatarTop = avatarTop, originPx = center.toFloat())) { IconArea(center) { icon() } }
            Box(Modifier.matchParentSize().popIn(originPx = center.toFloat())) {
                Label(
                    text = label,
                    color = state.style.content,
                    tablet = state.style.tablet,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, labelTop) },
                )
            }
        }
        return
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .popIn(),
    ) {
        run {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                icon()
                Spacer(Modifier.height(2.dp))
                Label(text = label, color = state.style.content, tablet = state.style.tablet)
            }
        }
    }
}

/** Beim Scrollen: Grösse der Knöpfe (1 = normal) und wie weit die Fläche weggefahren ist (px), siehe [BottomBarOverlay.scrollRise]. */
private class ScrollPop(val scale: () -> Float, val shift: () -> Float)

private val LocalPop = androidx.compose.runtime.staticCompositionLocalOf { ScrollPop({ 1f }, { 0f }) }

/**
 * Knopf beim Weg-/Hereinfahren (Scrollen) wie bei YouTube (gemessen 19.11.): Er fährt nur halb so weit wie die Fläche und
 * "ploppt" (Grösse um die Knopf-Mitte, ohne Ausblenden; nur ganz klein durchsichtig).
 * [avatarTop] (px ab Oberkante des Knopfs): Profilbild. Das wird bei YouTube nicht kleiner, sondern von einer Kante
 * knapp darüber, die mit der Fläche fährt, abgedeckt; es selbst rutscht nur ein paar Pixel.
 */
@Composable
private fun Modifier.popIn(originY: Float = POP_ORIGIN_Y, avatarTop: Float? = null, originPx: Float? = null): Modifier {
    val pop = LocalPop.current
    val density = LocalDensity.current
    val clipAbove = with(density) { BottomBarOverlay.AVATAR_CLIP_ABOVE_DP.dp.toPx() }
    val shiftMax = with(density) { BottomBarOverlay.AVATAR_SHIFT_MAX_DP.dp.toPx() }
    val layered = graphicsLayer {
        val shift = pop.shift()
        if (avatarTop != null) {
            if (shift != 0f) translationY = -shift + minOf(shiftMax, shift / 2f)
            return@graphicsLayer
        }
        if (shift != 0f) translationY = -shift / 2f
        val s = pop.scale()
        if (s != 1f) {
            scaleX = s
            scaleY = s
            alpha = (s / 0.25f).coerceIn(0f, 1f)
            // Um die Mitte des Symbols (wie YouTube), sonst wanderte es beim Wachsen mit.
            transformOrigin = TransformOrigin(0.5f, if (originPx != null && size.height > 0f) (originPx / size.height).coerceIn(0f, 1f) else originY)
        }
    }
    if (avatarTop == null) return layered
    return layered.drawWithContent {
        val shift = pop.shift()
        if (shift <= 0f) {
            drawContent()
        } else {
            // Kante auf dem Bildschirm = Ruhelage der Kante + Weg der Fläche; hier in eigenen Koordinaten (das Profilbild
            // selbst steht nur minOf(shiftMax, shift/2) tiefer).
            val edge = avatarTop - clipAbove + shift - minOf(shiftMax, shift / 2f)
            if (edge < size.height) clipRect(top = edge.coerceAtLeast(0f)) { this@drawWithContent.drawContent() }
        }
    }
}

/** Box, deren Mitte genau auf der Höhe der Icon-Mitte der echten Leiste liegt. */
@Composable
private fun BoxScope.IconArea(
    center: Int?,
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val modifier = if (center != null) {
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(with(density) { (center * 2).toDp() })
    } else {
        Modifier.align(Alignment.Center).fillMaxWidth()
    }
    Box(modifier = modifier, contentAlignment = alignment) { content() }
}

@Composable
private fun Label(text: String, color: Color, tablet: Boolean = false, modifier: Modifier = Modifier) {
    // YouTube vergrössert die Schrift in seiner Leiste nicht mit der Systemschrift. Darum in dp statt sp,
    // sonst wird "Mein YouTube" bei grosser Schrift abgeschnitten.
    val density = LocalDensity.current
    Text(
        text = text,
        color = color,
        fontSize = with(density) { (if (tablet) TABLET_LABEL_DP else LABEL_DP).dp.toSp() },
        lineHeight = with(density) { (if (tablet) 14f else 12f).dp.toSp() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Schriftgrösse wie die echte Leiste (gemessen: "Startseite" ist dort 129 px breit bei Dichte 2,875). */
private const val LABEL_DP = 10.5f
/** Drehpunkt der Knöpfe beim "Ploppen" (Anteil der Leistenhöhe, etwa Symbol-Mitte). */
private const val POP_ORIGIN_Y = 0.42f
/** Abdunkeln beim ⋮-Menü: Tinte breitet sich aus (ms) bzw. zieht sich zurück. */
private const val INK_IN_MS = 480
private const val INK_OUT_MS = 380
private const val PANEL_INK_OUT_MS = 150

/** Plus: Knöpfe sinken nach unten weg (ms), die Fläche bleibt stehen, bis die Kamera darüber ist. */
private const val SINK_MS = 260
/** Handy hochkant: unsere Leiste ist so viel höher als die echte. */
private const val TOP_EXTRA_DP = 2f

/** Plus getippt: Knopf wird so viel grösser, dreht sich so weit (Grad, 45 = "×") und hellt so stark auf. */
private const val PLUS_GROW = 0.18f
private const val PLUS_TURN = 45f
private const val PLUS_LIGHTEN = 0.18f
/** Handy quer: Höhe der flachen Leiste (ganz) und davon unten der Streifen für den Gestenstrich. */
private const val COMPACT_TOTAL_DP = 40f
private const val COMPACT_HANDLE_DP = 12f



private const val RISE_MS = 260
/** Hereinfahren nach "Video verlassen": Abkling-Stärke der Kurve (5 = nach 1/5 der Zeit ist noch ~1/3 übrig). */
private const val RISE_DECAY = 5.0
/** Tablet (Galaxy Tab A8, Dichte 1,75 gemessen): "Startseite" ist dort 89 px breit = 12,1 dp; Symbole ~28 dp; Spalte 96 dp. */
private const val TABLET_LABEL_DP = 12.1f
private const val TABLET_ICON_DP = 28f
private const val TABLET_SLOT_DP = 96f
private val AVATAR_INSET = 2.5.dp

@Composable
private fun TabIcon(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 24.dp) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

// -------------------------------------------------------------------------------------------------------------------
// Moderne Leiste (Wahl in den Einstellungen): nur Symbole wie YouTubes neues Design, drei Knöpfe Startseite, Mein
// YouTube, Plus (Abos gibt es dort nur oben als Reiter). Formen Pixel für Pixel von YouTube 21.45 abgemessen (Pixel 9,
// Dichte 2,875, 17.11.2026): Symbol-Mitte 92,5 px unter der Oberkante, Haus 63 × 57 px, Plus-Kreis Ø 63 px mit 6 px
// Rand und Kreuz 29 px / 5 px, Profilbild 69 px (gewählt: 6 px Ring, 3 px Abstand, Bild 51 px).
// Alle Masse unten in px bei Dichte 2,875 (MODERN_PX), umgerechnet mit der Dichte des Geräts.
// -------------------------------------------------------------------------------------------------------------------

/** Symbol-Mitte unter der Oberkante der echten Leiste (dp). */
internal const val MODERN_ICON_CENTER_DP = 27.5f
/** Bezugsdichte der abgemessenen Pixelwerte. */
private const val MODERN_PX = 2.875f
/** Symbole etwas grösser als bei YouTube (Wunsch von Niklas, 17.11.: "ein bisschen klein"). */
private const val MODERN_SCALE = 1.14f

/** Die drei Spalten der modernen Leiste, von links nach rechts. */
internal val MODERN_COLUMNS = listOf(TabKind.HOME, TabKind.YOU, TabKind.CREATE)

/**
 * Haus wie YouTube: gefüllt (gewählt) oder als Umriss (5 px Strich, Tür als umgekehrtes U). Ecken leicht gerundet
 * (~2,5 px). Erst die Einzelformen runden, dann abziehen: Rundet man das fertige Haus, verliert es seine Innenfläche.
 */
private fun modernHousePath(filled: Boolean, k: Float, cx: Float, cy: Float): android.graphics.Path {
    fun poly(pts: List<Pair<Float, Float>>) = android.graphics.Path().apply {
        pts.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(cx + x * k, cy + y * k) else lineTo(cx + x * k, cy + y * k)
        }
        close()
    }
    val rounder = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        pathEffect = android.graphics.CornerPathEffect(2.5f * k)
    }
    @Suppress("DEPRECATION")
    fun round(p: android.graphics.Path) = android.graphics.Path().also { rounder.getFillPath(p, it) }
    // Umriss aussen: Spitze, Dach, Traufe, Wände, Boden; gefüllt mit der Tür als Aussparung von unten.
    val house = round(poly(listOf(
        0f to -29.5f, 31.5f to -11.5f, 31.5f to -6.5f, 25.5f to -6.5f, 25.5f to 28.5f,
        -25.5f to 28.5f, -25.5f to -6.5f, -31.5f to -6.5f, -31.5f to -11.5f,
    )))
    if (filled) {
        // Tür eckig wie bei YouTube (11 px breit, ab 5,5 px unter der Mitte).
        house.op(poly(listOf(-5.5f to 5.5f, 5.5f to 5.5f, 5.5f to 31f, -5.5f to 31f)), android.graphics.Path.Op.DIFFERENCE)
        return house
    }

    // Innenraum (Strich 5 px) ohne den Türrahmen, dazu die Türöffnung durch den Boden.
    val inner = round(poly(listOf(
        0f to -23f, 20.5f to -10.5f, 20.5f to 22.5f, 8.5f to 22.5f, 8.5f to 2.5f,
        -8.5f to 2.5f, -8.5f to 22.5f, -20.5f to 22.5f, -20.5f to -10.5f,
    )))
    val door = poly(listOf(-2.5f to 8.5f, 2.5f to 8.5f, 2.5f to 31f, -2.5f to 31f))
    house.op(inner, android.graphics.Path.Op.DIFFERENCE)
    house.op(door, android.graphics.Path.Op.DIFFERENCE)
    return house
}

@Composable
private fun ModernHome(selected: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(30.dp)) {
        val k = density / MODERN_PX * MODERN_SCALE
        val path = modernHousePath(selected, k, size.width / 2f, size.height / 2f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = android.graphics.Paint.Style.FILL
        }
        drawContext.canvas.nativeCanvas.drawPath(path, paint)
    }
}

/** Plus wie YouTube: Kreis (Rand 6 px) mit Kreuz. [spin] 0..1 = Plus getippt (wird grösser, dreht zum "×"). */
@Composable
private fun ModernPlus(color: Color, spin: () -> Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(34.dp)
            .graphicsLayer {
                val p = spin()
                scaleX = 1f + PLUS_GROW * p
                scaleY = 1f + PLUS_GROW * p
                rotationZ = PLUS_TURN * p
            },
    ) {
        val k = density / MODERN_PX * MODERN_SCALE
        val c = Offset(size.width / 2f, size.height / 2f)
        val p = spin().coerceIn(0f, 1f)
        if (p > 0f) drawCircle(color.copy(alpha = PLUS_LIGHTEN * p), radius = 28.75f * k, center = c)
        drawCircle(
            color,
            radius = 28.75f * k,
            center = c,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f * k),
        )
        val arm = 12f * k
        val w = 5f * k
        drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), w, StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), w, StrokeCap.Round)
    }
}

/** Profilbild wie YouTube: 69 px rund; gewählt mit Ring (6 px) und Abstand (3 px) in der Farbe der Leiste. */
@Composable
private fun ModernAvatar(style: BarStyle, selected: Boolean, modifier: Modifier = Modifier) {
    val avatar = style.avatar
    val content = style.content
    val background = style.background
    if (avatar == null) {
        // Noch kein Profilbild abgeschaut (oder Vorschau in der App): Personen-Symbol wie YouTube ohne Bild.
        Box(modifier.size(30.dp), contentAlignment = Alignment.Center) {
            val ring = if (selected) Modifier.border(2.dp, content, CircleShape).padding(3.dp) else Modifier
            Box(Modifier.size(27.dp).then(ring), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AccountCircle, null, tint = content, modifier = Modifier.fillMaxSize())
            }
        }
        return
    }
    Canvas(modifier.size(30.dp)) {
        val k = density / MODERN_PX * MODERN_SCALE
        val c = Offset(size.width / 2f, size.height / 2f)
        val outer = 34.5f * k
        val imageRadius = if (selected) 25.5f * k else outer
        if (selected) {
            drawCircle(content, radius = outer, center = c)
            drawCircle(background, radius = outer - 6f * k, center = c)
        }
        if (avatar != null) {
            // Ganzer Ausschnitt aus der echten Leiste (69 px), auf den Kreis gelegt: gewählt verkleinert wie bei YouTube.
            val inset = 0
            val src = androidx.compose.ui.unit.IntSize(avatar.width - inset * 2, avatar.height - inset * 2)

            val d = (imageRadius * 2f).toInt()
            val clip = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c, imageRadius))
            }
            clipPath(clip) {
                drawImage(
                    avatar,
                    srcOffset = IntOffset(inset, inset),
                    srcSize = src,
                    dstOffset = IntOffset((c.x - d / 2f).toInt(), (c.y - d / 2f).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(d, d),
                )
            }
        } else {
            drawCircle(content.copy(alpha = 0.55f), radius = imageRadius, center = c)
        }
    }
}

/** Die drei Knöpfe der modernen Leiste (hochkant). Tablet: Block in der Mitte wie die klassische. */
@Composable
private fun ModernRow(
    state: BarUiState,
    barHeight: androidx.compose.ui.unit.Dp,
    topExtra: Int,
    duck: Float,
    spin: () -> Float,
) {
    val style = state.style
    val density = LocalDensity.current
    // Abos gibt es in der modernen Leiste nicht: Die Abos-Seite gehört zur Startseite (wie bei YouTube).
    val homeSelected = state.selected == TabKind.HOME || state.selected == TabKind.SUBSCRIPTIONS
    val center = if (style.tablet) state.iconCenterPx else with(density) { MODERN_ICON_CENTER_DP.dp.roundToPx() }
    Row(
        modifier = Modifier.offset { IntOffset(0, topExtra) }.fillMaxWidth().height(barHeight).graphicsLayer {
            scaleX = duck
            scaleY = duck
            transformOrigin = TransformOrigin(0.5f, 0.35f)
        },
        horizontalArrangement = if (style.tablet) Arrangement.Center else Arrangement.Start,
    ) {
        val slot: Modifier = if (style.tablet) Modifier.width(TABLET_SLOT_DP.dp) else Modifier.weight(1f)
        Box(slot.fillMaxHeight().popIn(originPx = center?.toFloat())) { IconArea(center) { ModernHome(homeSelected, style.content) } }
        Box(slot.fillMaxHeight().popIn(avatarTop = center?.let { it - with(density) { 15.dp.toPx() } })) { IconArea(center) { ModernAvatar(style, state.selected == TabKind.YOU) } }
        Box(slot.fillMaxHeight().popIn(originPx = center?.toFloat())) {
            if (state.hasCreate) IconArea(center) { ModernPlus(style.content, spin) }
        }
    }
}

/** Moderne Leiste quer (flach): nur die drei Symbole, mittig in drei gleich breiten Spalten. */
@Composable
private fun ModernCompactRow(state: BarUiState, height: androidx.compose.ui.unit.Dp, modifier: Modifier, spin: () -> Float) {
    val style = state.style
    Row(modifier = modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        val slot = Modifier.weight(1f).fillMaxHeight()
        val homeSelected = state.selected == TabKind.HOME || state.selected == TabKind.SUBSCRIPTIONS
        Box(slot, contentAlignment = Alignment.Center) { ModernHome(homeSelected, style.content, Modifier.size(22.dp)) }
        Box(slot, contentAlignment = Alignment.Center) {
            ModernAvatar(style, state.selected == TabKind.YOU, Modifier.size(22.dp))
        }
        Box(slot, contentAlignment = Alignment.Center) {
            if (state.hasCreate) ModernPlus(style.content, spin, Modifier.size(26.dp))
        }
    }
}

/**
 * Vorschau der Leiste für die App (Einstellungen, Startseite): dieselbe Zeichnung wie in YouTube, mit Beispiel-Inhalt.
 * [modern]: moderne Leiste (nur Symbole) statt klassisch (Symbole mit Text).
 */
@Composable
fun BarPreview(modern: Boolean, dark: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val heightDp = 64.dp
    val style = BarStyle.default(dark).copy(modern = modern)
    val state = BarUiState(
        selected = TabKind.HOME,
        labels = emptyMap(),
        subscriptionsBadge = false,
        hasCreate = true,
        barHeightPx = with(density) { heightDp.roundToPx() },
        iconCenterPx = with(density) { 20.dp.roundToPx() },
        labelTopPx = with(density) { 36.dp.roundToPx() },
        showHandle = false,
        style = style,
    )
    Box(modifier.fillMaxWidth().height(heightDp)) {
        if (modern) {
            Box(Modifier.fillMaxSize().background(style.background)) {
                ModernRowPreview(state, heightDp)
            }
        } else {
            BottomBar(
                state = state,
                dimInstant = true,
                sunk = false,
                sinkSnap = false,
                compactNav = -1,
                topExtra = 0,
                inkMs = INK_IN_MS,
                inkOutMs = INK_OUT_MS,
                cover = { Float.MAX_VALUE },
                rise = { 0f },
                onTap = {},
                onLongPress = {},
            )
        }
    }
}

/** Vorschau: moderne Knöpfe in der Mitte der (niedrigeren) Vorschau-Fläche. */
@Composable
private fun ModernRowPreview(state: BarUiState, height: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        val slot = Modifier.weight(1f)
        Box(slot, contentAlignment = Alignment.Center) { ModernHome(true, state.style.content) }
        Box(slot, contentAlignment = Alignment.Center) { ModernAvatar(state.style, false) }
        Box(slot, contentAlignment = Alignment.Center) { ModernPlus(state.style.content, { 0f }) }
    }
}

/** Flache Leiste im Querformat (siehe compactNavPx). Aus, solange YouTube quer eine eigene Leiste zeigt. */
private const val COMPACT_LANDSCAPE = false
