package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Vorhang: eine einfarbige Fläche in YouTubes Seitenfarbe über dem Inhalt (zwischen Statusleiste und unserer Leiste).
 *
 * Wozu: Im neuen YouTube-Design gibt es "Abos" nur als Reiter oben auf der Startseite. Tippt man von "Mein YouTube"
 * aus auf unseren Abos-Knopf, müssen wir erst die Startseite öffnen und dann den Reiter klicken – die Startseite
 * blitzte dabei ~0,15 s auf. Der Vorhang deckt genau diesen Moment ab: Man sieht kurz eine leere Seite (wie beim
 * Laden), dann Abos. Der direkte Weg per Link (youtube.com/feed/subscriptions) wurde am 15.11.2026 getestet und
 * verworfen: Er legt die Abos-Seite auf den Stapel des gerade offenen Tabs – danach zeigte "Mein YouTube" Abos.
 *
 * Berührungen gehen immer durch. Nach spätestens `maxMs` verschwindet er von selbst.
 */
class PageCurtain(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private val timeout = Runnable { hide(0L) }

    var isShown = false
        private set

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        0,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        windowAnimations = 0
        title = "FuckShortsCurtain"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /** Zeigt den Vorhang von [top] bis [bottom] (Bildschirm-Pixel) in [color], weich eingeblendet. */
    fun show(top: Int, bottom: Int, color: Int, maxMs: Long) {
        if (bottom - top < 1) return
        var v = view
        if (v == null) {
            v = View(service).also {
                it.alpha = 0f
                params.y = top
                params.height = bottom - top
                runCatching { windowManager.addView(it, params) }.onFailure { return }
            }
            view = v
        } else if (params.y != top || params.height != bottom - top) {
            params.y = top
            params.height = bottom - top
            windowManager.updateViewLayout(v, params)
        }
        v.setBackgroundColor(color)
        v.removeCallbacks(timeout)
        v.animate().cancel()
        v.animate().alpha(1f).setStartDelay(0L).setDuration(FADE_IN_MS).withEndAction(null).start()
        isShown = true
        v.postDelayed(timeout, maxMs)
    }

    /** Blendet den Vorhang nach [delayMs] aus und nimmt das Fenster danach ganz weg. */
    fun hide(delayMs: Long) {
        val v = view ?: return
        if (!isShown) return
        isShown = false
        v.removeCallbacks(timeout)
        v.animate().cancel()
        v.animate().alpha(0f).setStartDelay(delayMs).setDuration(FADE_OUT_MS).withEndAction {
            if (!isShown && view === v) {
                view = null
                runCatching { windowManager.removeViewImmediate(v) }
            }
        }.start()
    }

    fun destroy() {
        val v = view ?: return
        view = null
        isShown = false
        v.removeCallbacks(timeout)
        v.animate().cancel()
        runCatching { windowManager.removeViewImmediate(v) }
    }

    private companion object {
        const val FADE_IN_MS = 60L
        const val FADE_OUT_MS = 160L
    }
}
