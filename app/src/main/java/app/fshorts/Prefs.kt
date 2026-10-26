package app.fshorts

import android.content.Context

object Prefs {
    private const val FILE = "fuckshorts"
    private const val DISCLOSURE = "disclosure_accepted"
    private const val DEBUG_DUMPS = "debug_dumps"
    private const val SHORTS_BLOCKED = "shorts_blocked"
    private const val AUTO_HIDE_SHELF = "auto_hide_shelf"
    private const val SHELVES_HIDDEN = "shelves_hidden"
    private const val MENU_DIM = "menu_dim"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun disclosureAccepted(context: Context) = prefs(context).getBoolean(DISCLOSURE, false)
    fun setDisclosureAccepted(context: Context) = prefs(context).edit().putBoolean(DISCLOSURE, true).apply()

    fun debugDumps(context: Context) = prefs(context).getBoolean(DEBUG_DUMPS, false)
    fun setDebugDumps(context: Context, on: Boolean) = prefs(context).edit().putBoolean(DEBUG_DUMPS, on).apply()

    fun shortsBlocked(context: Context) = prefs(context).getInt(SHORTS_BLOCKED, 0)
    fun incrementShortsBlocked(context: Context) =
        prefs(context).edit().putInt(SHORTS_BLOCKED, shortsBlocked(context) + 1).apply()

    fun autoHideShelf(context: Context) = prefs(context).getBoolean(AUTO_HIDE_SHELF, true)
    fun setAutoHideShelf(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(AUTO_HIDE_SHELF, on).apply()

    fun shelvesHidden(context: Context) = prefs(context).getInt(SHELVES_HIDDEN, 0)
    fun incrementShelvesHidden(context: Context) =
        prefs(context).edit().putInt(SHELVES_HIDDEN, shelvesHidden(context) + 1).apply()

    fun menuDim(context: Context): Float? =
        prefs(context).getFloat(MENU_DIM, -1f).takeIf { it >= 0f }
    fun setMenuDim(context: Context, dim: Float) = prefs(context).edit().putFloat(MENU_DIM, dim).apply()
}
