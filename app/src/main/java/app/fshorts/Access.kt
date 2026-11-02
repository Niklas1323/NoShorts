package app.fshorts

import android.content.Context

/**
 * Darf die App arbeiten? Nur mit Abo (auch in den 3 Gratis-Tagen, die zählen bei Google Play als Abo) oder mit
 * dem Gratis-Code. Der Dienst fragt das bei jeder Meldung ab, darum nur Felder im Speicher (gleicher Prozess).
 */
object Access {
    private const val CODE_ON = "ningeninge"
    private const val CODE_OFF = "ningeningestop"

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

    enum class Redeem { Unlocked, Stopped, Invalid }

    /** "Ningeninge" schaltet gratis frei, "Ningeningestop" nimmt es wieder weg (Gross-/Kleinschreibung egal). */
    fun redeem(context: Context, code: String): Redeem {
        val result = when (code.trim().lowercase()) {
            CODE_ON -> Redeem.Unlocked
            CODE_OFF -> Redeem.Stopped
            else -> return Redeem.Invalid
        }
        promo = result == Redeem.Unlocked
        Prefs.setPromoUnlocked(context, promo)
        return result
    }
}
