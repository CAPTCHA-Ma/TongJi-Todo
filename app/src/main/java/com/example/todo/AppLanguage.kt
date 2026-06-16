package com.example.todo

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val tag: String, val locale: Locale) {
    English("en", Locale.ENGLISH),
    Chinese("zh", Locale.SIMPLIFIED_CHINESE);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: Chinese
    }
}

object AppLanguageStore {
    private const val PreferencesName = "app_language"
    private const val SelectedLanguageKey = "selected_language"

    fun load(context: Context): AppLanguage =
        AppLanguage.fromTag(
            context
                .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .getString(SelectedLanguageKey, null)
        )

    fun save(context: Context, language: AppLanguage) {
        context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(SelectedLanguageKey, language.tag)
            .apply()
    }
}

fun Context.localizedContext(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(language.locale))
    return createConfigurationContext(configuration)
}

fun Context.localizedConfiguration(language: AppLanguage): Configuration {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(language.locale))
    return configuration
}
