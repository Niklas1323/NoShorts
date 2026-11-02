package app.fshorts

import android.content.Context

object Prefs {
    private const val FILE = "fuckshorts"
    // v3: Hinweis nennt jetzt auch Superwall/Google Play (Internet für Abos), darum muss man neu zustimmen.
    private const val DISCLOSURE = "disclosure_accepted_v3"
    private const val ONBOARDING_DONE = "onboarding_done"
    private const val THEME_MODE = "theme_mode"
    private const val SUBSCRIBED = "subscribed"
    private const val PROMO = "promo_unlocked"
    private const val PAUSED = "protection_paused"
    private const val UNLOCK_AT = "unlock_at"
    private const val NEXT_WAIT = "next_wait_min"
    private const val FAST_TIMER = "fast_timer"
    private const val STARTED_AT = "started_at"
    private const val DEBUG_DUMPS = "debug_dumps"
    private const val SHORTS_BLOCKED = "shorts_blocked"
    private const val SHELVES_HIDDEN = "shelves_hidden"
    private const val MENU_DIM = "menu_dim"
    private const val BAR_STATE = "bar_state"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun disclosureAccepted(context: Context) = prefs(context).getBoolean(DISCLOSURE, false)
    fun setDisclosureAccepted(context: Context) = prefs(context).edit().putBoolean(DISCLOSURE, true).apply()

    fun onboardingDone(context: Context) = prefs(context).getBoolean(ONBOARDING_DONE, false)
    fun setOnboardingDone(context: Context, done: Boolean = true) =
        prefs(context).edit().putBoolean(ONBOARDING_DONE, done).apply()

    /** Letzter bekannter Abo-Stand von Superwall (Testzeit zählt als Abo), für den Start ohne Netz. */
    fun subscribed(context: Context) = prefs(context).getBoolean(SUBSCRIBED, false)
    fun setSubscribed(context: Context, on: Boolean) = prefs(context).edit().putBoolean(SUBSCRIBED, on).apply()

    /** Schutz vom Nutzer pausiert (Shorts laufen, unsere Leiste bleibt). */
    fun protectionPaused(context: Context) = prefs(context).getBoolean(PAUSED, false)
    fun setProtectionPaused(context: Context, on: Boolean) = prefs(context).edit().putBoolean(PAUSED, on).apply()

    /** Wann das Ausschalten erlaubt ist (Uhrzeit in ms, 0 = keine Wartezeit läuft). Läuft auch bei geschlossener App weiter. */
    fun unlockAt(context: Context) = prefs(context).getLong(UNLOCK_AT, 0L)
    fun setUnlockAt(context: Context, at: Long) = prefs(context).edit().putLong(UNLOCK_AT, at).apply()

    /** Wartezeit in Minuten fürs nächste Ausschalten (beim Wieder-Einschalten gewählt, mind. 5, beim ersten Mal 10). */
    fun nextWaitMinutes(context: Context) = prefs(context).getInt(NEXT_WAIT, 10)
    fun setNextWaitMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt(NEXT_WAIT, minutes).apply()

    /** Seit wann der Nutzer die App benutzt (für "Tag 1, 2, …" im Ausschalt-Fenster). Wird beim ersten Lesen gesetzt. */
    fun startedAt(context: Context): Long {
        val p = prefs(context)
        val at = p.getLong(STARTED_AT, 0L)
        if (at > 0L) return at
        val now = System.currentTimeMillis()
        p.edit().putLong(STARTED_AT, now).apply()
        return now
    }

    /** Test-Code "Ningeningetimer": Wartezeit nur 5 Sekunden. */
    fun fastTimer(context: Context) = prefs(context).getBoolean(FAST_TIMER, false)
    fun setFastTimer(context: Context, on: Boolean) = prefs(context).edit().putBoolean(FAST_TIMER, on).apply()

    fun promoUnlocked(context: Context) = prefs(context).getBoolean(PROMO, false)
    fun setPromoUnlocked(context: Context, on: Boolean) = prefs(context).edit().putBoolean(PROMO, on).apply()

    /** 0 = wie das Handy, 1 = dunkel, 2 = hell. */
    fun themeMode(context: Context) = prefs(context).getInt(THEME_MODE, 0)
    fun setThemeMode(context: Context, mode: Int) = prefs(context).edit().putInt(THEME_MODE, mode).apply()

    fun debugDumps(context: Context) = prefs(context).getBoolean(DEBUG_DUMPS, false)
    fun setDebugDumps(context: Context, on: Boolean) = prefs(context).edit().putBoolean(DEBUG_DUMPS, on).apply()

    fun shortsBlocked(context: Context) = prefs(context).getInt(SHORTS_BLOCKED, 0)
    fun incrementShortsBlocked(context: Context) =
        prefs(context).edit().putInt(SHORTS_BLOCKED, shortsBlocked(context) + 1).apply()

    /** Regale werden immer ausgeblendet (Schalter entfernt, Wunsch des Nutzers 02.11.2026). */
    @Suppress("UNUSED_PARAMETER")
    fun autoHideShelf(context: Context) = true

    fun shelvesHidden(context: Context) = prefs(context).getInt(SHELVES_HIDDEN, 0)
    fun incrementShelvesHidden(context: Context) =
        prefs(context).edit().putInt(SHELVES_HIDDEN, shelvesHidden(context) + 1).apply()

    fun menuDim(context: Context): Float? =
        prefs(context).getFloat(MENU_DIM, -1f).takeIf { it >= 0f }
    fun setMenuDim(context: Context, dim: Float) = prefs(context).edit().putFloat(MENU_DIM, dim).apply()

    /** Lage und Inhalt der echten Leiste, damit unsere nach einem Neustart (App-Update) sofort steht. */
    fun barState(context: Context): String? = prefs(context).getString(BAR_STATE, null)
    fun setBarState(context: Context, value: String) = prefs(context).edit().putString(BAR_STATE, value).apply()
}
