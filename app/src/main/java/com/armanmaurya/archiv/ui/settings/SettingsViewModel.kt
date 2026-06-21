package com.armanmaurya.archiv.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.armanmaurya.archiv.data.repository.dataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.armanmaurya.archiv.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appTheme: String = "System",
    val pureBlack: Boolean = false,
    val dynamicTheme: Boolean = true,
    val appLanguage: String = "System",
    val importConflictStrategy: String = "RENAME"
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState = combine(
        settingsRepository.appTheme,
        settingsRepository.pureBlack,
        settingsRepository.dynamicTheme
    ) { theme, pure, dynamic ->
        Triple(theme, pure, dynamic)
    }.combine(
        combine(
            settingsRepository.appLanguage,
            settingsRepository.importConflictStrategy
        ) { lang, conflict -> lang to conflict }
    ) { (theme, pure, dynamic), (lang, conflict) ->
        SettingsUiState(
            appTheme = theme,
            pureBlack = pure,
            dynamicTheme = dynamic,
            appLanguage = lang,
            importConflictStrategy = conflict
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun setPureBlack(enable: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPureBlack(enable)
        }
    }

    fun setDynamicTheme(enable: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicTheme(enable)
        }
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
            val appLocale: LocaleListCompat = if (language == "System") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language)
            }
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    fun setImportConflictStrategy(strategy: String) {
        viewModelScope.launch {
            settingsRepository.setImportConflictStrategy(strategy)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    // We assume context.dataStore extension is defined in SettingsRepository.kt
                    val repository = SettingsRepository(appContext.dataStore)
                    return SettingsViewModel(repository) as T
                }
            }
    }
}
