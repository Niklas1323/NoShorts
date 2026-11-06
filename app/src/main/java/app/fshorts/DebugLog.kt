package app.fshorts

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Test-Protokoll im Speicher (nur Test-Versionen): Was unsere Leiste gerade tut und warum. Mit dem Code "Ningelog"
 * teilt die App es (WhatsApp, Mail …) – so lassen sich Bildschirmaufnahmen ohne Computer/USB auswerten.
 * Enthält Zustände, Fenster-Nummern und Beschriftungen getippter Elemente (kann Video-Titel enthalten). Bleibt im
 * Speicher des Handys, bis der Nutzer es selbst teilt; Play-Store-Version schreibt nichts.
 */
object DebugLog {
    private const val MAX_LINES = 6_000
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    fun add(text: String) {
        if (!BuildConfig.DEBUG) return
        // Keine Zeilenumbrüche/Steuerzeichen: Beim Einfügen als Text brach das Protokoll sonst mittendrin ab.
        val clean = text.map { if (it.isISOControl() || it.isSurrogate()) ' ' else it }.joinToString("")
        val line = "${time.format(Date())} $clean"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    /** Speichert das Protokoll in "Downloads" (zum Anhängen im Claude-Chat) und öffnet das Teilen-Menü. */
    fun share(context: Context): String? {
        val file = File(File(context.cacheDir, "."), "fshorts-protokoll.txt")
        val body = synchronized(lines) { lines.joinToString("\n") }
        val text = "F*ck Shorts ${BuildConfig.VERSION_NAME} – ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n" +
            "Uhrzeit beim Teilen: ${time.format(Date())}\n\n" + body + "\n"
        file.writeText(text)
        val saved = runCatching { saveToDownloads(context, text) }.getOrNull()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return saved
    }

    private fun saveToDownloads(context: Context, text: String): String {
        val name = "fshorts-protokoll-${SimpleDateFormat("HHmm", Locale.ROOT).format(Date())}.txt"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("kein Platz in Downloads")
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("nicht schreibbar")
        return name
    }
}
