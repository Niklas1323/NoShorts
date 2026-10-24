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
import kotlin.math.roundToInt

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
    private val avatarFile = File(service.filesDir, "avatar_v2.png")
    var savedAvatar: ImageBitmap? = loadSavedAvatar()
        private set

    init {
        // Alte, falsch ausgeschnittene Version wegwerfen.
        File(service.filesDir, "avatar.png").delete()
    }

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
     * Schneidet das Profilbild aus: ein Quadrat von 24 dp (so gross zeigt YouTube es), mittig im
     * "Mein YouTube"-Tab und auf derselben Höhe wie die anderen Icons.
     */
    private fun cropAvatar(bitmap: Bitmap, bar: FoundBar): Bitmap? {
        val tab = bar.tabs[TabKind.YOU] ?: return null
        val centerY = bar.tabs[TabKind.HOME]?.iconBounds?.centerY()
            ?: tab.iconBounds?.centerY()
            ?: return null
        val size = (AVATAR_DP * service.resources.displayMetrics.density).roundToInt()
        val left = tab.bounds.centerX() - size / 2
        val top = centerY - size / 2
        if (left < 0 || top < 0 || left + size > bitmap.width || top + size > bitmap.height) return null
        val cut = Bitmap.createBitmap(bitmap, left, top, size, size)
        val reference = bitmap.getPixel(tab.bounds.left + 2, top)
        return if (looksLikePicture(cut, reference)) cut else null
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
        const val AVATAR_DP = 24f
    }
}
