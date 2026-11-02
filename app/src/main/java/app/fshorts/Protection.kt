package app.fshorts

import android.content.Context

/** Schutz an/aus. Ausschalten geht nur nach einer Wartezeit (siehe Startseite). Der Dienst liest nur [paused]. */
object Protection {
    const val MIN_WAIT_MINUTES = 5

    @Volatile var paused = false
        private set

    fun load(context: Context) {
        paused = Prefs.protectionPaused(context)
    }

    fun setPaused(context: Context, on: Boolean) {
        paused = on
        Prefs.setProtectionPaused(context, on)
    }
}
