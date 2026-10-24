package app.fshorts

import android.content.Context

object Prefs {
    private const val FILE = "fuckshorts"
    private const val DISCLOSURE = "disclosure_accepted"
    private const val DEBUG_DUMPS = "debug_dumps"
    private const val SHORTS_BLOCKED = "shorts_blocked"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun disclosureAccepted(context: Context) = prefs(context).getBoolean(DISCLOSURE, false)
    fun setDisclosureAccepted(context: Context) = prefs(context).edit().putBoolean(DISCLOSURE, true).apply()

    fun debugDumps(context: Context) = prefs(context).getBoolean(DEBUG_DUMPS, false)
    fun setDebugDumps(context: Context, on: Boolean) = prefs(context).edit().putBoolean(DEBUG_DUMPS, on).apply()

    fun shortsBlocked(context: Context) = prefs(context).getInt(SHORTS_BLOCKED, 0)
    fun incrementShortsBlocked(context: Context) =
        prefs(context).edit().putInt(SHORTS_BLOCKED, shortsBlocked(context) + 1).apply()
}
