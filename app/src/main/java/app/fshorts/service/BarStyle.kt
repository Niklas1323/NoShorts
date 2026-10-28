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
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

data class BarStyle(
    val background: Color,
    val content: Color,
    val plusBackground: Color,
    val handle: Color,
    val avatar: ImageBitmap?,
    /** Echte YouTube-Icons als weisse Masken, Schlüssel siehe [iconKey]. */
    val icons: Map<String, ImageBitmap>,
) {
    companion object {
        // Feste, deckende Farben wie im Vorlagenbild. Unsere Leiste muss deckend sein, sonst scheint der
        // Shorts-Knopf der echten (durchsichtigen) Leiste durch.
        fun default(dark: Boolean, avatar: ImageBitmap? = null, icons: Map<String, ImageBitmap> = emptyMap()) =
            BarStyle(
                background = if (dark) Color(0xFF1C1C1C) else Color.White,
                content = if (dark) Color(0xFFF1F1F1) else Color(0xFF0F0F0F),
                plusBackground = if (dark) Color(0xFF2B2B2B) else Color(0xFFF2F2F2),
                handle = if (dark) Color(0xFFEBEBEB) else Color(0xFF1F1F1F),
                avatar = avatar,
                icons = icons,
            )

        fun iconKey(kind: TabKind, selected: Boolean) = "${kind.name}_$selected"
    }
}

/**
 * Schaut sich die echte YouTube-Leiste an und übernimmt daraus Pixel für Pixel:
 * - die Icons (Startseite, Abos) als Masken, je Zustand (ausgewählt / nicht ausgewählt)
 * - dein Profilbild
 *
 * Ab Android 14 fotografieren wir nur das YouTube-Fenster, unsere Leiste ist dabei nicht im Bild.
 * Darum geht das jederzeit, ohne dass unsere Leiste verschwinden muss.
 * Icons und Profilbild werden in der App gespeichert, damit sie beim nächsten Mal sofort da sind.
 */
class BarStyleSampler(private val service: AccessibilityService) {

    private val density = service.resources.displayMetrics.density
    private val dir = File(service.filesDir, "bar").apply { mkdirs() }
    private val avatarFile = File(dir, "avatar.png")

    private var avatar: ImageBitmap? = load(avatarFile)
    private val icons = mutableMapOf<String, ImageBitmap>()
    private var dark: Boolean? = null
    private var lastAttemptAt = 0L
    private var checkedAt = 0L
    private var attempts = 0
    private var busy = false

    /** Kann ein Foto nur vom YouTube-Fenster machen (unsere Leiste darf sichtbar bleiben). */
    val canCaptureBehindOverlay = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    init {
        File(service.filesDir, "avatar.png").delete()
        File(service.filesDir, "avatar_v2.png").delete()
        dir.listFiles()?.filter { it.name.startsWith("icon_") }?.forEach { it.delete() }
    }

    fun style(nightMode: Boolean): BarStyle = BarStyle.default(dark ?: nightMode, avatar, icons.toMap())

    fun reset() {
        dark = null
        attempts = 0
    }

    /** Fehlt noch etwas für den aktuellen Zustand der Leiste? */
    fun needsCapture(bar: FoundBar): Boolean {
        if (busy || attempts >= MAX_ATTEMPTS) return false
        if (SystemClock.uptimeMillis() - lastAttemptAt < MIN_INTERVAL_MS) return false
        if (dark == null || avatar == null) return true
        // Hell/dunkel kann man in YouTube umstellen (unabhängig vom Handy): ab und zu neu nachsehen.
        return SystemClock.uptimeMillis() - checkedAt > RECHECK_MS
    }

    /** Bald neu nachsehen (z. B. zurück aus den YouTube-Einstellungen, wo man hell/dunkel umstellt). */
    fun recheckSoon() {
        checkedAt = 0L
        attempts = 0
    }

    fun capture(bar: FoundBar, windowId: Int, windowBounds: Rect, onDone: () -> Unit) {
        busy = true
        attempts++
        lastAttemptAt = SystemClock.uptimeMillis()
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                try {
                    val buffer = result.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    if (bitmap != null) {
                        read(bitmap, bar, windowBounds)
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bildschirmfoto konnte nicht gelesen werden", e)
                }
                busy = false
                onDone()
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "Bildschirmfoto fehlgeschlagen: $errorCode")
                busy = false
                onDone()
            }
        }
        try {
            when {
                canCaptureBehindOverlay ->
                    service.takeScreenshotOfWindow(windowId, service.mainExecutor, callback)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
                else -> {
                    busy = false
                    onDone()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bildschirmfoto nicht möglich", e)
            busy = false
            onDone()
        }
    }

    private fun read(bitmap: Bitmap, bar: FoundBar, windowBounds: Rect) {
        // Fensterfoto: Koordinaten relativ zum Fenster. Bildschirmfoto: windowBounds ist dann (0,0).
        val dx = if (canCaptureBehindOverlay) windowBounds.left else 0
        val dy = if (canCaptureBehindOverlay) windowBounds.top else 0
        val b = Rect(bar.bounds).apply { offset(-dx, -dy) }
        if (b.bottom > bitmap.height || b.right > bitmap.width || b.top < 0) return

        val sample = bitmap.getPixel((b.left + 3).coerceAtMost(bitmap.width - 1), (b.top + b.height() / 4))
        val nowDark = luminance(sample) < 128
        if (dark != null && dark != nowDark) Log.i(TAG, "YouTube-Design gewechselt: ${if (nowDark) "dunkel" else "hell"}")
        dark = nowDark
        checkedAt = SystemClock.uptimeMillis()
        attempts = 0
        saveDebugImage(bitmap, b)

        val iconCenterY = (bar.tabs[TabKind.HOME]?.iconBounds ?: bar.tabs.values.firstNotNullOfOrNull { it.iconBounds })
            ?.centerY()?.minus(dy) ?: return

        // Icons zeichnen wir selbst (sauberer als abgeschaute Masken), nur das Profilbild wird übernommen.
        // Gewählt hat das Profilbild einen weissen Ring: dann nur nehmen, wenn wir noch keins haben.
        bar.tabs[TabKind.YOU]?.takeIf { !it.selected || avatar == null }?.let { tab ->
            val cut = cropAvatar(bitmap, tab.bounds.centerX() - dx, iconCenterY)
            if (cut != null) {
                avatar = cut.asImageBitmap()
                save(cut, avatarFile)
            }
            Log.i(TAG, "Profilbild: ${if (cut != null) "${cut.width}x${cut.height}" else "noch nicht geladen"}")
        }
    }

    /** Profilbild: 24-dp-Quadrat genau auf Icon-Höhe, mittig im Tab. */
    private fun cropAvatar(bitmap: Bitmap, centerX: Int, centerY: Int): Bitmap? {
        val size = (AVATAR_DP * density).roundToInt()
        val left = centerX - size / 2
        val top = centerY - size / 2
        if (left < 0 || top < 0 || left + size > bitmap.width || top + size > bitmap.height) return null
        val cut = Bitmap.createBitmap(bitmap, left, top, size, size)
        // Ein geladenes Profilbild hat Inhalt; ein leerer Platzhalter ist fast einfarbig und grau.
        // Ein Buchstaben-Bild (farbiger Kreis mit dünnem "L") hat kaum Helligkeitsunterschiede an den
        // Messpunkten, aber eine kräftige Farbe.
        var min = 255f
        var maxL = 0f
        var maxChroma = 0
        for (gy in 2..6) {
            for (gx in 2..6) {
                val p = cut.getPixel(cut.width * gx / 8, cut.height * gy / 8)
                val l = luminance(p)
                min = minOf(min, l)
                maxL = maxOf(maxL, l)
                val r = AndroidColor.red(p)
                val g = AndroidColor.green(p)
                val bl = AndroidColor.blue(p)
                maxChroma = maxOf(maxChroma, maxOf(r, g, bl) - minOf(r, g, bl))
            }
        }
        return if (maxL - min > 25 || maxChroma > 60) cut else null
    }

    private fun luminance(p: Int) =
        0.299f * AndroidColor.red(p) + 0.587f * AndroidColor.green(p) + 0.114f * AndroidColor.blue(p)

    /** Bild der echten Leiste für die Fehlersuche (landet bei "Struktur teilen"). */
    private fun saveDebugImage(bitmap: Bitmap, r: Rect) {
        runCatching {
            val crop = Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
            val dumps = TreeDumper.dumpDir(service).apply { mkdirs() }
            File(dumps, "leiste-${System.currentTimeMillis()}.png").outputStream().use {
                crop.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            crop.recycle()
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun load(file: File): ImageBitmap? = runCatching {
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
    }.getOrNull()

    private companion object {
        const val TAG = "FuckShorts"
        const val MAX_ATTEMPTS = 30
        const val MIN_INTERVAL_MS = 1_000L
        const val RECHECK_MS = 30_000L
        const val AVATAR_DP = 24f
        const val ICON_BOX_DP = 28f
    }
}
