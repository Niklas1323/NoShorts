package app.fshorts.service

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.fshorts.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schreibt den Aufbau der YouTube-Oberfläche in eine Textdatei: nur Klassen, IDs, Beschriftungen und
 * Positionen. Damit können wir die Such-Regeln anpassen, wenn YouTube etwas ändert.
 * Die Dateien bleiben auf dem Handy, bis du sie selbst in der App teilst.
 */
class TreeDumper(private val context: Context) {

    fun dump(root: AccessibilityNodeInfo, windows: List<AccessibilityWindowInfo>, reason: String): File? = try {
        val text = buildString {
            appendLine("Fuck Shorts – Struktur")
            appendLine("Grund: $reason")
            appendLine("Zeit: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}")
            appendLine("App: ${BuildConfig.VERSION_NAME}")
            appendLine("YouTube: ${youTubeVersion(root.packageName?.toString())}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), Gerät: ${Build.MODEL}")
            appendLine()
            appendLine("Fenster:")
            val r = Rect()
            for (w in windows) {
                w.getBoundsInScreen(r)
                appendLine("  type=${w.type} layer=${w.layer} active=${w.isActive} title=${w.title} ${r.toShortString()}")
            }
            appendLine()
            appendLine("Aufbau:")
            var count = 0
            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                if (count >= MAX_NODES) return
                count++
                append("  ".repeat(depth))
                appendLine(describe(node))
                for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
            }
            walk(root, 0)
            if (count >= MAX_NODES) appendLine("… abgeschnitten nach $MAX_NODES Elementen")
        }
        val dir = dumpDir(context).apply { mkdirs() }
        val safeReason = reason.replace(Regex("[^a-zA-Z0-9-]"), "_")
        val file = File(dir, "dump-${System.currentTimeMillis()}-$safeReason.txt")
        file.writeText(text)
        prune(dir)
        text.chunked(3000).forEach { Log.i(TAG, it) }
        file
    } catch (e: Exception) {
        Log.w(TAG, "Dump fehlgeschlagen", e)
        null
    }

    private fun describe(node: AccessibilityNodeInfo): String {
        val r = Rect()
        node.getBoundsInScreen(r)
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val id = node.viewIdResourceName?.substringAfter(":id/")
        val flags = buildString {
            if (node.isClickable) append('c')
            if (node.isSelected) append('s')
            if (node.isScrollable) append('r')
            if (!node.isVisibleToUser) append('h')
        }
        return buildString {
            append(cls)
            if (id != null) append(" id=").append(id)
            node.text?.let { append(" text=\"").append(it.toString().take(60).replace('\n', ' ')).append('"') }
            node.contentDescription?.let { append(" desc=\"").append(it.toString().take(60).replace('\n', ' ')).append('"') }
            append(' ').append(r.toShortString())
            if (flags.isNotEmpty()) append(" [").append(flags).append(']')
        }
    }

    @Suppress("DEPRECATION")
    private fun youTubeVersion(pkg: String?): String = try {
        if (pkg == null) "?" else context.packageManager.getPackageInfo(pkg, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun prune(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.name } ?: return
        files.drop(MAX_FILES).forEach { it.delete() }
    }

    companion object {
        private const val TAG = "FuckShorts"
        private const val MAX_NODES = 4000
        private const val MAX_FILES = 8

        private fun dumpDir(context: Context) = File(context.filesDir, "dumps")

        fun files(context: Context): List<File> =
            dumpDir(context).listFiles()?.sortedByDescending { it.name }.orEmpty()

        fun clear(context: Context) {
            dumpDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
