package app.fshorts.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
import kotlin.math.abs
import kotlin.math.max

data class BarStyle(
    val background: Color,
    val content: Color,
    val plusBackground: Color,
    val avatar: ImageBitmap?,
) {
    companion object {
        // Feste Farben statt abgeschauter Farbe: Die echte YouTube-Leiste ist durchsichtig, darum wäre eine
        // abgeschaute Farbe je nach Video darunter mal braun, mal blau. Unsere Leiste muss deckend sein,
        // sonst scheint der Shorts-Knopf durch.
        fun default(dark: Boolean, avatar: ImageBitmap? = null) = BarStyle(
            background = if (dark) Color(0xFF0F0F0F) else Color.White,
            content = if (dark) Color(0xFFF1F1F1) else Color(0xFF0F0F0F),
            plusBackground = if (dark) Color(0xFF272727) else Color(0xFFF2F2F2),
            avatar = avatar,
        )
    }
}

/**
 * Macht ein Bildschirmfoto, solange unsere Leiste noch nicht sichtbar ist, und schneidet daraus
 * dein Profilbild genau so aus, wie YouTube es zeigt. Das Profilbild wird in der App gespeichert,
 * damit es beim nächsten Mal sofort da ist. Sonst wird nichts gespeichert.
 */
class BarStyleSampler(private val service: AccessibilityService) {

    var style: BarStyle? = null
        private set
    var attempted = false
        private set
    private var attempts = 0
    private var lastAttemptAt = 0L
    private val avatarFile = File(service.filesDir, "avatar.png")
    var savedAvatar: ImageBitmap? = loadSavedAvatar()
        private set

    fun reset() {
        style = null
        attempted = false
        attempts = 0
    }

    /** Profilbild fehlt noch (z. B. noch nicht geladen): später nochmal probieren. */
    fun wantsAvatarRetry(): Boolean {
        val current = style ?: return false
        if (current.avatar != null || attempts >= MAX_ATTEMPTS) return false
        return SystemClock.uptimeMillis() - lastAttemptAt > RETRY_MS
    }

    fun sample(bar: FoundBar, onDone: () -> Unit) {
        attempted = true
        attempts++
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
        val y = (bar.bounds.top + bar.bounds.height() / 4).coerceIn(0, bitmap.height - 1)
        val x = (bar.bounds.left + 3).coerceIn(0, bitmap.width - 1)
        val dark = Color(bitmap.getPixel(x, y)).luminance() < 0.5f

        saveDebugImage(bitmap, bar)
        val avatar = cropAvatar(bitmap, bar)
        if (avatar != null) {
            runCatching { avatarFile.outputStream().use { avatar.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            savedAvatar = avatar.asImageBitmap()
        }
        Log.i(TAG, "Profilbild: ${if (avatar != null) "${avatar.width}x${avatar.height}" else "nicht gefunden"}")
        return BarStyle.default(dark, savedAvatar)
    }

    /**
     * Schneidet das Profilbild aus. Bereich: der "Mein YouTube"-Tab oberhalb der Beschriftung.
     * Darin suchen wir das Bild (alles, was sich deutlich vom Hintergrund abhebt).
     */
    private fun cropAvatar(bitmap: Bitmap, bar: FoundBar): Bitmap? {
        val tab = bar.tabs[TabKind.YOU] ?: return null
        val area = Rect(tab.bounds)
        tab.labelBounds?.let { area.bottom = it.top }
        if (!area.intersect(0, 0, bitmap.width, bitmap.height) || area.width() < 8 || area.height() < 8) return null

        val box = tab.iconBounds?.takeIf { area.contains(it) && it.width() >= 8 } ?: findContentBox(bitmap, area)
        ?: return null
        // Quadrat um die Mitte, so gross wie das Bild.
        val size = max(box.width(), box.height())
        val square = Rect(
            box.centerX() - size / 2,
            box.centerY() - size / 2,
            box.centerX() - size / 2 + size,
            box.centerY() - size / 2 + size,
        )
        if (!square.intersect(0, 0, bitmap.width, bitmap.height)) return null
        val cut = Bitmap.createBitmap(bitmap, square.left, square.top, square.width(), square.height())
        val reference = bitmap.getPixel(area.left + 1, area.top + 1)
        return if (looksLikePicture(cut, reference)) cut else null
    }

    private fun findContentBox(bitmap: Bitmap, area: Rect): Rect? {
        val reference = bitmap.getPixel(area.left + 1, area.top + 1)
        val pixels = IntArray(area.width() * area.height())
        bitmap.getPixels(pixels, 0, area.width(), area.left, area.top, area.width(), area.height())
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (yy in 0 until area.height()) {
            for (xx in 0 until area.width()) {
                if (distance(pixels[yy * area.width() + xx], reference) > 90) {
                    if (xx < minX) minX = xx
                    if (yy < minY) minY = yy
                    if (xx > maxX) maxX = xx
                    if (yy > maxY) maxY = yy
                }
            }
        }
        if (maxX < 0 || maxX - minX < 8 || maxY - minY < 8) return null
        return Rect(area.left + minX, area.top + minY, area.left + maxX + 1, area.top + maxY + 1)
    }

    /** Ein Profilbild hat Inhalt. Ist der Ausschnitt fast nur Hintergrund, ist das Bild noch nicht geladen. */
    private fun looksLikePicture(bitmap: Bitmap, background: Int): Boolean {
        var different = 0
        var total = 0
        for (gy in 2..6) {
            for (gx in 2..6) {
                if (distance(bitmap.getPixel(bitmap.width * gx / 8, bitmap.height * gy / 8), background) > 60) different++
                total++
            }
        }
        return different * 100 / total >= 40
    }

    private fun distance(a: Int, b: Int) =
        abs(AndroidColor.red(a) - AndroidColor.red(b)) +
            abs(AndroidColor.green(a) - AndroidColor.green(b)) +
            abs(AndroidColor.blue(a) - AndroidColor.blue(b))

    /** Bild der echten Leiste für die Fehlersuche (landet bei "Struktur teilen"). */
    private fun saveDebugImage(bitmap: Bitmap, bar: FoundBar) {
        runCatching {
            val r = Rect(bar.bounds)
            if (!r.intersect(0, 0, bitmap.width, bitmap.height)) return
            val crop = Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
            val dir = TreeDumper.dumpDir(service).apply { mkdirs() }
            File(dir, "leiste-${System.currentTimeMillis()}.png").outputStream().use {
                crop.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            crop.recycle()
        }
    }

    private fun loadSavedAvatar(): ImageBitmap? = runCatching {
        if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.path)?.asImageBitmap() else null
    }.getOrNull()

    private companion object {
        const val TAG = "FuckShorts"
        const val MAX_ATTEMPTS = 6
        const val RETRY_MS = 4_000L
    }
}
