package com.owlynights.scalablecapitalwidgets

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's language: "" follows the system, otherwise a language tag such as "de".
 * Android 13+ handles per-app languages itself (and lists them in system settings);
 * older versions apply the stored choice to each context.
 */
object AppLanguage {
    val tags = listOf("", "en", "de", "it")

    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales[0]?.language.orEmpty()
        } else {
            prefs(context).getString(KEY, "").orEmpty()
        }

    fun set(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(context).edit { putString(KEY, tag) }
        }
    }

    /** Before Android 13: applies the stored language to [base]. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** Language names are shown in their own language, as Android does. */
    fun displayName(tag: String): String =
        Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() }

    private fun prefs(context: Context) = context.getSharedPreferences("language", Context.MODE_PRIVATE)

    private const val KEY = "tag"
}
