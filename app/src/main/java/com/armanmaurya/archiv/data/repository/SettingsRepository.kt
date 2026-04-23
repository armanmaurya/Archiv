package com.armanmaurya.archiv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val USER_PREFERENCES_NAME = "settings"

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_NAME
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    val appTheme: Flow<String> = dataStore
        .getOrDefault(key = APP_THEME, default = "System")

    val pureBlack: Flow<Boolean> = dataStore
        .getOrDefault(key = PURE_BLACK, default = false)

    val dynamicTheme: Flow<Boolean> = dataStore
        .getOrDefault(key = DYNAMIC_THEME, default = true)

    val appLanguage: Flow<String> = dataStore
        .getOrDefault(key = APP_LANGUAGE, default = "System")

    suspend fun setAppTheme(theme: String) {
        dataStore.setOrUpdate(key = APP_THEME, value = theme)
    }

    suspend fun setPureBlack(enable: Boolean) {
        dataStore.setOrUpdate(key = PURE_BLACK, value = enable)
    }

    suspend fun setDynamicTheme(enable: Boolean) {
        dataStore.setOrUpdate(key = DYNAMIC_THEME, value = enable)
    }

    suspend fun setAppLanguage(language: String) {
        dataStore.setOrUpdate(key = APP_LANGUAGE, value = language)
    }

    private companion object PreferencesKeys {
        val APP_THEME = stringPreferencesKey("app_theme")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }
}

/** Returns a pre-saved preferences or `default` if it doesn't exist. */
private fun <T> DataStore<Preferences>.getOrDefault(key: Preferences.Key<T>, default: T): Flow<T> {
    return data.map { preferences -> preferences[key] ?: default }
}

/**
 * Returns a pre-saved preferences after applying a function or `default`
 * if it doesn't exist.
 */
private fun <T, U> DataStore<Preferences>.getMapOrDefault(
    key: Preferences.Key<T>,
    map: (T) -> U,
    default: U,
): Flow<U> {
    return data.map { preferences -> preferences[key]?.let(map) ?: default }
}

/** Sets a preferences or updates if it already exists .*/
private suspend fun <T> DataStore<Preferences>.setOrUpdate(key: Preferences.Key<T>, value: T) {
    edit { preferences -> preferences[key] = value }
}
