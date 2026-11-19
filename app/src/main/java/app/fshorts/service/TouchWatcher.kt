package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Merkt, ob der Bildschirm berührt wurde – egal wo, auch in YouTube. Ein unsichtbares 1-Pixel-Fenster, dem
 * Android jede Berührung ausserhalb meldet (ohne Ort). So erkennen wir einen Short per Link: Er geht auf,
 * ohne dass seit dem Wechsel zu YouTube jemand getippt hat. Ein Tipp auf eine Short-Karte meldet YouTube
 * selbst nämlich nicht immer als Klick.
 *
 * Das Fenster hängt an einem eigenen Thread: Auf dem Haupt-Thread kam die Meldung oft erst nach einer laufenden Suche
 * an (bis 0,4 s zu spät, gemessen 19.11.), so fuhr unsere Leiste beim Scrollen viel später weg als YouTubes.
 * [onTouchFast] läuft sofort auf diesem Thread mit der echten Zeit der Berührung (uptimeMillis), [onTouch] danach auf
 * dem Haupt-Thread.
 */
class TouchWatcher(
    service: AccessibilityService,
    private val onTouchFast: (downAt: Long) -> Unit,
    private val onTouch: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val context = service
    private val main = Handler(service.mainLooper)
    private val thread = HandlerThread("fshorts-touch").also { it.start() }
    private val touchHandler = Handler(thread.looper)
    private var view: View? = null
    @Volatile private var added = false

    private val params = WindowManager.LayoutParams(
        1,
        1,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        title = "FuckShortsTouch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    @SuppressLint("ClickableViewAccessibility")
    fun start() {
        touchHandler.post {
            if (added) return@post
            val v = View(context)
            v.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE || event.actionMasked == MotionEvent.ACTION_DOWN) {
                    runCatching { onTouchFast(event.eventTime) }
                    main.post { onTouch() }
                }
                false
            }
            added = runCatching { windowManager.addView(v, params) }.isSuccess
            if (added) view = v
        }
    }

    fun stop() {
        touchHandler.post {
            val v = view
            if (added && v != null) runCatching { windowManager.removeViewImmediate(v) }
            added = false
            view = null
        }
        thread.quitSafely()
    }
}
