package app.fshorts

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Sprache der App (Einstellungen und Onboarding). Ohne Wahl gilt die Sprache des Handys (falls wir sie haben,
 * sonst Englisch). Ab Android 13 merkt sich Android die Wahl selbst (auch in den System-Einstellungen unter
 * "App-Sprachen" änderbar, `res/xml/locales_config.xml`), davor speichern wir sie und setzen sie in
 * [MainActivity.attachBaseContext]. Die YouTube-Leiste ist davon noch nicht betroffen.
 */
object AppLanguage {
    class Language(val code: String, val name: String, val flag: String)

    /** Reihenfolge = Liste in der Auswahl. Texte: tools/i18n/gen_strings.py. */
    val ALL = listOf(
        Language("en", "English", "🇺🇸"),
        Language("de", "Deutsch", "🇩🇪"),
        Language("fr", "Français", "🇫🇷"),
        Language("it", "Italiano", "🇮🇹"),
        Language("es", "Español", "🇪🇸"),
        Language("pt", "Português", "🇧🇷"),
        Language("tr", "Türkçe", "🇹🇷"),
    )

    private const val PREFS = "fuckshorts"
    private const val KEY = "app_language"

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

    /** Gewählte Sprache (z. B. "de"), oder null = wie das Handy. */
    private fun chosenTag(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }

    /** Sprache, in der die App gerade erscheint. */
    fun current(context: Context): Language {
        val code = Locale.forLanguageTag(chosenTag(context) ?: systemLocale().toLanguageTag()).language
        return ALL.firstOrNull { it.code == code } ?: ALL.first()
    }

    /**
     * Sprache wechseln. Ist es die Sprache des Handys, folgen wir wieder dem Handy (so bekommt z. B. ein Schweizer
     * Handy "ss" statt "ß" und spätere Wechsel am Handy gelten auch für die App). Die Seite baut sich danach neu auf.
     */
    fun set(activity: Activity, code: String) {
        if (code == current(activity).code) return
        val followSystem = code == systemLocale().language
        if (Build.VERSION.SDK_INT >= 33) {
            // Android baut die Activity selbst neu auf.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (followSystem) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .apply { if (followSystem) remove(KEY) else putString(KEY, code) }
                .commit()
            activity.recreate()
        }
    }

    /** Vor Android 13: gewählte Sprache in den Context der Activity einbauen. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = chosenTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
