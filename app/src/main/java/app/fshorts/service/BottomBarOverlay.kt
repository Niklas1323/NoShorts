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
    /** Handy quer: flache Leiste, Wert = Höhe der Gestenleiste darunter. -1 = normale Leiste. */
    private var compactNav by mutableStateOf(-1)
    private val restoreSink = Runnable { sunk = false }
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
        val hole = panel?.let { Rect(it).apply { offset(-rest.left, -rest.top) } }
        place(rest.top, rest, state.copy(dim = 0f, hole = hole, veil = veil))
    }

    /**
     * Ein Menü (⋮, Teilen) ist offen und liegt über der Leiste. Unsere Leiste liegt aber immer zuoberst.
     * Darum: abgedunkelt wie YouTube den Rest, mit einem Loch, wo das Menü ist. Berührungen gehen durch
     * (Tipp neben das Menü schliesst es, wie bei YouTube).
     */
    fun showUnderMenu(rest: Rect, state: BarUiState, dim: Float, sheet: Rect) {
        underMenu = true
        val hole = Rect(sheet).apply { offset(-rest.left, -rest.top) }
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
            target.set(rest.left, top, rest.right, top + rest.height())
            return
        }
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

    /** Handy quer: Höhe der Gestenleiste unten (mindestens 0) = flache Leiste. Sonst -1. */
    private fun compactNavPx(rest: Rect): Int {
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
        sinkSnap = false
        sunk = true
        sinkUntil = SystemClock.uptimeMillis() + SINK_MS
        v.removeCallbacks(restoreSink)
        v.postDelayed(restoreSink, restoreMs)
    }

    fun hide() {
        val v = view ?: return
        view?.removeCallbacks(restoreSink)
        if (sunk) {
            sinkSnap = true
            sunk = false
        }
        if (!isShowing) return
        stopSlide()
        stopRise()
        isShowing = false
        // Durchsichtig statt unsichtbar: Ein unsichtbares Fenster richtet Android beim Wiederzeigen neu ein
        // (1–2 Bilder später, z. B. beim Verlassen eines Videos sah man so die echte Leiste kurz).
        v.alpha = 0f
        applyFlags()
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
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        lifecycleOwner?.destroy()
        view = null
        lifecycleOwner = null
        isShowing = false
    }

    private fun currentFlags(): Int =
        if (!isShowing || passThrough || underMenu) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS

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

    private fun create(): ComposeView {
        val owner = OverlayLifecycleOwner().also { it.start() }
        lifecycleOwner = owner
        return ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            viewTreeObserver.addOnDrawListener { drawnAt = SystemClock.uptimeMillis() }
            // Berührungen selbst auswerten statt über Compose: einfacher und zuverlässiger in einem Overlay.
            // Die Leiste hat vier gleich breite Spalten: Startseite, Abos, Mein YouTube, Plus.
            setOnTouchListener(TapHandler(onTap = tap@{ x, width ->
                if (SystemClock.uptimeMillis() < ignoreTapsUntil) return@tap
                val column = if (uiState?.style?.tablet == true) {
                    // Tablet: enger Block in der Mitte (4 Spalten à 96 dp). Ausserhalb: kein Knopf.
                    val slot = TABLET_SLOT_DP * resources.displayMetrics.density
                    val start = (width - slot * 4) / 2f
                    val c = Math.floor(((x - start) / slot).toDouble()).toInt()
                    if (c !in 0..3) return@tap
                    c
                } else {
                    (x / width * 4).toInt().coerceIn(0, 3)
                }
                val kind = COLUMNS[column]
                if (kind != TabKind.CREATE || uiState?.hasCreate == true) {
                    if (kind != TabKind.CREATE) {
                        optimistic = kind
                        optimisticAt = SystemClock.uptimeMillis()
                        uiState = uiState?.copy(selected = kind)
                    }
                    onTap(kind)
                }
            }, onLongPress = onLongPress))
            setContent {
                uiState?.let { state ->
                    BottomBar(
                        state = state,
                        dimInstant = dimInstant,
                        sunk = sunk,
                        sinkSnap = sinkSnap,
                        compactNav = compactNav,
                        rise = { risePx },
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
            view = this
        }
    }

    private companion object {
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        const val OPTIMISTIC_MS = 600L
        const val SLIDE_MS = 300L
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
    rise: () -> Float,
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
        else ink.animateTo(target, tween(if (target > 0f) INK_IN_MS else INK_OUT_MS, easing = FastOutSlowInEasing))
    }
    val sink = remember { Animatable(0f) }
    LaunchedEffect(sunk, sinkSnap) {
        when {
            sunk -> sink.animateTo(1f, tween(SINK_MS, easing = FastOutLinearInEasing))
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
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                // Video verlassen: Die ganze Leiste fährt das letzte Stück von unten herein.
                translationY = rise()
            }
            .drawWithContent {
                drawContent()
                val p = ink.value
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
        // Tablet: Die echte Leiste hat enge Spalten (96 dp) als Block in der Mitte; auf dem Handy teilen sich vier
        // gleich breite Spalten die ganze Breite.
        if (compactNav >= 0) CompactRow(
            state = state,
            height = (COMPACT_TOTAL_DP - COMPACT_HANDLE_DP).dp,
            modifier = Modifier.graphicsLayer {
                scaleX = duck
                scaleY = duck
                translationY = sink.value * size.height * 0.85f
                alpha = 1f - sink.value
            },
        ) else Row(
            modifier = Modifier.fillMaxWidth().height(barHeight).graphicsLayer {
                scaleX = duck
                scaleY = duck
                translationY = sink.value * size.height * 0.85f
                alpha = 1f - sink.value
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
                label = state.labels[TabKind.HOME] ?: "Home",
                state = state,
                onTap = onTap,
                onLongPress = onLongPress,
            ) {
                TabIcon(if (homeSelected) Icons.Filled.FilledHome else Icons.Outlined.OutlinedHome, style.content, iconSize)
            }
            TabSlot(
                modifier = slot,
                kind = TabKind.SUBSCRIPTIONS,
                label = state.labels[TabKind.SUBSCRIPTIONS] ?: "Subscriptions",
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
                label = state.labels[TabKind.YOU] ?: "You",
                state = state,
                onTap = onTap,
                onLongPress = onLongPress,
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
                modifier = slot.fillMaxHeight(),
            ) {
                if (state.hasCreate) {
                    // Wie im Vorlagenbild: Plus-Kreis etwas unter der Icon-Reihe (Mitte 25,5 dp ab Oberkante).
                    val plusCenter = with(density) { 25.5.dp.roundToPx() }
                    IconArea(plusCenter) {
                        // Runder Knopf, mittig in seiner Spalte: alle vier Elemente haben gleichen Abstand.
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(style.plusBackground),
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
            Label(text = state.labels[TabKind.HOME] ?: "Home", color = style.content)
        }
        Row(slot, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TabIcon(
                if (state.selected == TabKind.SUBSCRIPTIONS) Icons.Filled.FilledSubscriptions else Icons.Outlined.OutlinedSubscriptions,
                style.content,
                22.dp,
            )
            Spacer(Modifier.width(8.dp))
            Label(text = state.labels[TabKind.SUBSCRIPTIONS] ?: "Subscriptions", color = style.content)
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
            Label(text = state.labels[TabKind.YOU] ?: "You", color = style.content)
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
    icon: @Composable () -> Unit,
) {
    val labelTop = state.labelTopPx
    Box(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        if (state.iconCenterPx != null && labelTop != null) {
            // Gleiche Höhe wie bei YouTube: Icon-Mitte und Schrift-Oberkante aus der echten Leiste.
            IconArea(state.iconCenterPx) { icon() }
            Label(
                text = label,
                color = state.style.content,
                tablet = state.style.tablet,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, labelTop) },
            )
        } else {
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
/** Abdunkeln beim ⋮-Menü: Tinte breitet sich aus (ms) bzw. zieht sich zurück. */
private const val INK_IN_MS = 480
private const val INK_OUT_MS = 380
/** Plus: Knöpfe sinken nach unten weg (ms), die Fläche bleibt stehen, bis die Kamera darüber ist. */
private const val SINK_MS = 150
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
