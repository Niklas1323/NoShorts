package app.fshorts

import android.content.Context

/**
 * Darf die App arbeiten? Nur mit Abo (auch in den 3 Gratis-Tagen, die zählen bei Google Play als Abo) oder mit
 * dem Gratis-Code. Der Dienst fragt das bei jeder Meldung ab, darum nur Felder im Speicher (gleicher Prozess).
 */
object Access {
    private const val CODE_ON = "ningeninge"
    private const val CODE_TIMER = "ningeningetimer"

    @Volatile var subscribed = false
        private set
    @Volatile var promo = false
        private set

    val unlocked get() = subscribed || promo

    fun load(context: Context) {
        subscribed = Prefs.subscribed(context)
        promo = Prefs.promoUnlocked(context)
    }

    fun setSubscribed(context: Context, on: Boolean) {
        subscribed = on
        Prefs.setSubscribed(context, on)
    }

    enum class Redeem { Unlocked, FastTimer, Invalid }

    /**
     * Codes (Gross-/Kleinschreibung egal): "Ningeninge" schaltet gratis frei,
     * "Ningeningetimer" macht die Wartezeit fürs Ausschalten 5 Sekunden.
     */
    fun redeem(context: Context, code: String): Redeem = when (code.trim().lowercase()) {
        CODE_ON -> {
            promo = true
            Prefs.setPromoUnlocked(context, true)
            Redeem.Unlocked
        }
        CODE_TIMER -> {
            Prefs.setFastTimer(context, true)
            Redeem.FastTimer
        }
        else -> Redeem.Invalid
    }
}
