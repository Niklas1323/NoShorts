package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.luminance

/**
 * Deckt Shorts-Regale mit einer etwas dunkleren grauen Fläche ab (ohne Text).
 * Die Fläche lässt alle Berührungen durch, damit man die Startseite ganz normal scrollen kann.
 */
class ShelfCovers(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val covers = mutableListOf<Cover>()

    private class Cover(val view: View, val params: WindowManager.LayoutParams) {
        var visible = true
    }

    fun show(areas: List<Rect>, style: BarStyle) {
        while (covers.size < areas.size.coerceAtMost(MAX_COVERS)) covers += create()
        covers.forEachIndexed { i, cover ->
            val area = areas.getOrNull(i)
            if (area == null) {
                setVisible(cover, false)
                return@forEachIndexed
            }
            val dark = style.content.luminance() > 0.5f
            cover.view.setBackgroundColor(if (dark) COVER_DARK else COVER_LIGHT)
            val p = cover.params
            val changed = p.x != area.left || p.y != area.top || p.width != area.width() || p.height != area.height()
            p.x = area.left
            p.y = area.top
            p.width = area.width()
            p.height = area.height()
            if (!cover.visible) {
                cover.visible = true
                cover.view.visibility = View.VISIBLE
                windowManager.updateViewLayout(cover.view, p)
            } else if (changed) {
                windowManager.updateViewLayout(cover.view, p)
            }
        }
    }

    val anyVisible get() = covers.any { it.visible }

    fun hideAll() = covers.forEach { setVisible(it, false) }

    fun destroy() {
        covers.forEach { runCatching { windowManager.removeViewImmediate(it.view) } }
        covers.clear()
    }

    private fun setVisible(cover: Cover, visible: Boolean) {
        if (cover.visible == visible) return
        cover.visible = visible
        cover.view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun create(): Cover {
        val view = View(service).apply { visibility = View.INVISIBLE }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
            title = "FuckShortsShelfCover"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(view, params)
        return Cover(view, params).also { it.visible = false }
    }

    private companion object {
        const val MAX_COVERS = 3
        const val COVER_DARK = 0xFF1A1A1A.toInt()
        const val COVER_LIGHT = 0xFFEDEDED.toInt()
    }
}
