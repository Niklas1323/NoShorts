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
        // Kurz halten: Beim Einfügen als Text bricht der Chat nach ~25 000 Zeichen ab. Aufbau zuerst (wichtig), dann
        // nur die letzten 3 Minuten Protokoll.
        val since = System.currentTimeMillis() - 3 * 60 * 1000L
        val recent = synchronized(lines) { lines.filter { lineTime(it) >= since }.joinToString("\n") }
        val body = recentDumps(context) + "\n\n===== Protokoll =====\n" + recent
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

    /** Uhrzeit einer Zeile (heute) in ms, für "nur die letzten Minuten". */
    private fun lineTime(line: String): Long = runCatching {
        val t = time.parse(line.substring(0, 12)) ?: return@runCatching 0L
        val cal = java.util.Calendar.getInstance()
        val day = java.util.Calendar.getInstance().apply { this.time = t }
        cal.set(java.util.Calendar.HOUR_OF_DAY, day.get(java.util.Calendar.HOUR_OF_DAY))
        cal.set(java.util.Calendar.MINUTE, day.get(java.util.Calendar.MINUTE))
        cal.set(java.util.Calendar.SECOND, day.get(java.util.Calendar.SECOND))
        cal.set(java.util.Calendar.MILLISECOND, day.get(java.util.Calendar.MILLISECOND))
        cal.timeInMillis
    }.getOrDefault(0L)

    /**
     * Die letzten Aufbau-Dateien (höchstens 2, letzte 10 Minuten), gekürzt: nur sichtbare Elemente mit ID oder
     * Beschreibung, höchstens 80 Zeilen je Datei.
     */
    private fun recentDumps(context: Context): String {
        val since = System.currentTimeMillis() - 10 * 60 * 1000L
        val files = app.fshorts.service.TreeDumper.files(context).filter { it.lastModified() > since }.take(2)
        if (files.isEmpty()) return ""
        return files.reversed().joinToString("") { f ->
            val all = runCatching { f.readLines() }.getOrDefault(emptyList())
            val head = all.takeWhile { it != "Aufbau:" }.filter { it.startsWith("Zeit") || it.startsWith("  type=") }
            val nodes = all.dropWhile { it != "Aufbau:" }.drop(1)
                .filter { !it.endsWith("h]") && ("id=" in it || "desc=" in it) }
                .map { it.trimEnd().replace(Regex("^( +)"), { m -> " ".repeat(m.value.length / 2) }) }
                .take(80)
            "\n===== ${f.name} =====\n" + (head + nodes).joinToString("\n")
        }
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
