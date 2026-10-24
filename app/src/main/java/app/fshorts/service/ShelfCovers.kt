package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb

/**
 * Deckt Shorts-Regale mit einer ruhigen Fläche ab.
 * Die Fläche lässt alle Berührungen durch, damit man die Startseite ganz normal scrollen kann.
 */
class ShelfCovers(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val covers = mutableListOf<Cover>()

    private class Cover(val view: FrameLayout, val label: TextView, val params: WindowManager.LayoutParams) {
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
            cover.view.setBackgroundColor(style.background.toArgb())
            cover.label.setTextColor(style.content.copy(alpha = 0.45f).toArgb())
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
        val label = TextView(service).apply {
            text = "Shorts ausgeblendet"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        val view = FrameLayout(service).apply {
            addView(label, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
            visibility = View.INVISIBLE
        }
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
        return Cover(view, label, params).also { it.visible = false }
    }

    private companion object {
        const val MAX_COVERS = 3
    }
}
