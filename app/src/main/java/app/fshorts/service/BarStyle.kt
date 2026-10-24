package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

data class BarStyle(
    val background: Color,
    val content: Color,
    val plusBackground: Color,
    val avatar: ImageBitmap?,
) {
    companion object {
        fun default(dark: Boolean) = fromBackground(if (dark) Color(0xFF0F0F0F) else Color.White, null)

        fun fromBackground(background: Color, avatar: ImageBitmap?): BarStyle {
            val dark = background.luminance() < 0.5f
            return BarStyle(
                background = background,
                content = if (dark) Color.White else Color(0xFF0F0F0F),
                plusBackground = if (dark) Color(0xFF272727) else Color(0xFFF2F2F2),
                avatar = avatar,
            )
        }
    }
}

/**
 * Macht einmal ein Bildschirmfoto, solange unsere Leiste noch nicht sichtbar ist, und holt sich daraus
 * die echte Hintergrundfarbe der YouTube-Leiste und dein Profilbild. Das Foto wird nicht gespeichert.
 */
class BarStyleSampler(private val service: AccessibilityService) {

    var style: BarStyle? = null
        private set
    var attempted = false
        private set
    private var avatarAttempts = 0
    private var lastAttemptAt = 0L

    fun reset() {
        style = null
        attempted = false
        avatarAttempts = 0
    }

    /**
     * Fehlt das Profilbild noch (z. B. weil YouTube es beim ersten Foto noch nicht geladen hatte),
     * versuchen wir es später nochmal, aber nur wenn unsere Leiste sowieso gerade versteckt ist.
     */
    fun wantsAvatarRetry(bar: FoundBar): Boolean {
        val current = style ?: return false
        if (current.avatar != null || avatarAttempts >= MAX_AVATAR_ATTEMPTS) return false
        if (bar.tabs[TabKind.YOU]?.avatarBounds == null) return false
        return SystemClock.uptimeMillis() - lastAttemptAt > AVATAR_RETRY_MS
    }

    fun sample(bar: FoundBar, onDone: () -> Unit) {
        attempted = true
        avatarAttempts++
        lastAttemptAt = SystemClock.uptimeMillis()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onDone()
            return
        }
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val buffer = result.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                            buffer.close()
                            if (bitmap != null) {
                                style = build(bitmap, bar)
                                bitmap.recycle()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Bildschirmfoto konnte nicht gelesen werden", e)
                        }
                        onDone()
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Bildschirmfoto fehlgeschlagen: $errorCode")
                        onDone()
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Bildschirmfoto nicht möglich", e)
            onDone()
        }
    }

    private fun build(bitmap: Bitmap, bar: FoundBar): BarStyle {
        val y = bar.bounds.centerY().coerceIn(0, bitmap.height - 1)
        val x = (bar.bounds.left + 3).coerceIn(0, bitmap.width - 1)
        val background = Color(bitmap.getPixel(x, y))

        val avatar = bar.tabs[TabKind.YOU]?.avatarBounds?.let { r ->
            val crop = Rect(r)
            if (!crop.intersect(0, 0, bitmap.width, bitmap.height) || crop.width() < 8 || crop.height() < 8) {
                return@let null
            }
            val cut = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
            if (looksLikeAvatar(cut, background.toArgb())) cut.asImageBitmap() else null
        }
        return BarStyle.fromBackground(background, avatar ?: style?.avatar)
    }

    /** Ein echtes Profilbild hat Farbe. Ist der Ausschnitt fast nur Hintergrund, nehmen wir ein Icon. */
    private fun looksLikeAvatar(bitmap: Bitmap, background: Int): Boolean {
        var different = 0
        var total = 0
        for (gy in 1..7) {
            for (gx in 1..7) {
                val c = bitmap.getPixel(bitmap.width * gx / 8, bitmap.height * gy / 8)
                val diff = abs(AndroidColor.red(c) - AndroidColor.red(background)) +
                    abs(AndroidColor.green(c) - AndroidColor.green(background)) +
                    abs(AndroidColor.blue(c) - AndroidColor.blue(background))
                if (diff > 60) different++
                total++
            }
        }
        return different * 100 / total >= 30
    }

    private companion object {
        const val TAG = "FuckShorts"
        const val MAX_AVATAR_ATTEMPTS = 4
        const val AVATAR_RETRY_MS = 5_000L
    }
}
