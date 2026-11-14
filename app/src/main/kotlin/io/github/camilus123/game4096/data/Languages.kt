package io.github.camilus123.game4096.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The in-app language choice.
 *
 * On Android 13 and later this is the system's own per-app language: the choice also shows
 * under the phone's Settings > Apps > 4096 > Language, and the two stay in step. On older
 * versions the choice is kept in the app's preferences and applied when the activity starts.
 */
object Languages {

    /** Follow the phone's language. */
    const val SYSTEM = ""

    /** Languages the game is translated into, as language tags. */
    val SUPPORTED = listOf("en", "sl")

    private const val PREFS = "settings"
    private const val KEY = "language"

    fun current(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) SYSTEM else locales[0].language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
        }
        return if (tag in SUPPORTED) tag else SYSTEM
    }

    /** Switches language. The activity is recreated so every text is reloaded. */
    fun set(activity: Activity, tag: String) {
        if (tag == current(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
            activity.recreate()
        }
    }

    /** Before Android 13: wraps the activity's base context so resources load in the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = current(base)
        if (tag == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
