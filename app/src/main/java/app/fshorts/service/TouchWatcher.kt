package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Merkt, ob der Bildschirm berührt wurde – egal wo, auch in YouTube. Ein unsichtbares 1-Pixel-Fenster, dem
 * Android jede Berührung ausserhalb meldet (ohne Ort). So erkennen wir einen Short per Link: Er geht auf,
 * ohne dass seit dem Wechsel zu YouTube jemand getippt hat. Ein Tipp auf eine Short-Karte meldet YouTube
 * selbst nämlich nicht immer als Klick.
 */
class TouchWatcher(service: AccessibilityService, private val onTouch: () -> Unit) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val view = View(service)
    private var added = false

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
        if (added) return
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE || event.actionMasked == MotionEvent.ACTION_DOWN) {
                onTouch()
            }
            false
        }
        added = runCatching { windowManager.addView(view, params) }.isSuccess
    }

    fun stop() {
        if (!added) return
        runCatching { windowManager.removeViewImmediate(view) }
        added = false
    }
}
