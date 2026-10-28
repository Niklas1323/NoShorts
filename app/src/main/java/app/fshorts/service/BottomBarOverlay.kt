package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
        underMenu = false
        place(top, rest, state.copy(dim = 0f, hole = null, veil = null))
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
        target.set(rest.left, rest.top, rest.right, rest.top + rest.height())
        if (applyBounds()) windowManager.updateViewLayout(v, params)
    }

    private fun stopSlide() {
        slide?.cancel()
        slide = null
        view?.translationY = 0f
    }

    private fun place(top: Int, rest: Rect, state: BarUiState, keepSlide: Boolean = false) {
        if (!keepSlide) stopSlide()
        val keepOptimistic = optimistic != null &&
            SystemClock.uptimeMillis() - optimisticAt < OPTIMISTIC_MS &&
            state.selected != optimistic
        val next = if (keepOptimistic) state.copy(selected = optimistic) else state
        if (next != uiState) uiState = next
        // Nur so hoch wie die echte Leiste: Bei Navigation mit 3 Knöpfen bleiben die Knöpfe frei.
        target.set(rest.left, top, rest.right, top + rest.height())

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
                v.post { applyFlags() }
                return
            }
        }
        params.flags = currentFlags()
        if (moved || params.flags != appliedFlags) {
            appliedFlags = params.flags
            windowManager.updateViewLayout(v, params)
        }
    }

    private fun applyFlags() {
        val v = view ?: return
        val flags = currentFlags()
        if (flags == appliedFlags) return
        params.flags = flags
        appliedFlags = flags
        windowManager.updateViewLayout(v, params)
    }

    fun hide() {
        val v = view ?: return
        if (!isShowing) return
        stopSlide()
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
        val fullWidth = target.left <= 0 && target.right >= displayWidth - 2
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
                val column = (x / width * 4).toInt().coerceIn(0, 3)
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
private fun BottomBar(state: BarUiState, onTap: (TabKind) -> Unit, onLongPress: () -> Unit) {
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (state.dim > 0f) drawRect(Color.Black.copy(alpha = state.dim))
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
        if (state.showHandle) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(width = 108.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(style.handle),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().height(barHeight)) {
            val homeSelected = state.selected == TabKind.HOME
            val subsSelected = state.selected == TabKind.SUBSCRIPTIONS
            TabSlot(
                modifier = Modifier.weight(1f),
                kind = TabKind.HOME,
                label = state.labels[TabKind.HOME] ?: "Home",
                state = state,
                onTap = onTap,
                onLongPress = onLongPress,
            ) {
                TabIcon(if (homeSelected) Icons.Filled.FilledHome else Icons.Outlined.OutlinedHome, style.content)
            }
            TabSlot(
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1f),
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
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .then(ring),
                    )
                } else {
                    TabIcon(Icons.Filled.AccountCircle, style.content)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                Label(text = label, color = state.style.content)
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
private fun Label(text: String, color: Color, modifier: Modifier = Modifier) {
    // YouTube vergrössert die Schrift in seiner Leiste nicht mit der Systemschrift. Darum in dp statt sp,
    // sonst wird "Mein YouTube" bei grosser Schrift abgeschnitten.
    val density = LocalDensity.current
    Text(
        text = text,
        color = color,
        fontSize = with(density) { LABEL_DP.dp.toSp() },
        lineHeight = with(density) { 12.dp.toSp() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Schriftgrösse wie die echte Leiste (gemessen: "Startseite" ist dort 129 px breit bei Dichte 2,875). */
private const val LABEL_DP = 10.5f

@Composable
private fun TabIcon(icon: ImageVector, tint: Color) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
}
