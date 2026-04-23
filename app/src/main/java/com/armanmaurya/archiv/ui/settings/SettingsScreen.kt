package com.armanmaurya.archiv.ui.settings

import android.content.Intent
import com.armanmaurya.archiv.core.theme.AppTheme
import com.armanmaurya.archiv.core.theme.toAppTheme
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armanmaurya.archiv.R
import com.armanmaurya.archiv.ui.settings.components.ExpandableItem
import com.armanmaurya.archiv.ui.settings.components.Item
import com.armanmaurya.archiv.ui.settings.components.OptionItem
import com.armanmaurya.archiv.ui.settings.components.Section
import com.armanmaurya.archiv.ui.settings.components.ToggleItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // UI-only state for expand/collapse
    var themeExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SettingsTopBar(onBackClick) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AppearanceSection(
                uiState = uiState,
                availableThemes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM),
                themeExpanded = themeExpanded,
                onToggleThemeExpanded = { themeExpanded = !themeExpanded },
                onSetDynamicTheme = viewModel::setDynamicTheme,
                onSetTheme = { viewModel.setAppTheme(it.toPreferenceValue()) },
                onSetPureBlack = viewModel::setPureBlack
            )
            GeneralSection(
                uiState = uiState,
                languages = availableLanguages,
                languageExpanded = languageExpanded,
                onToggleLanguageExpanded = { languageExpanded = !languageExpanded },
                onSetLanguage = viewModel::setAppLanguage
            )
            AboutSection(onAboutClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBackClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    )
}

@Composable
private fun AppearanceSection(
    uiState: SettingsUiState,
    availableThemes: List<AppTheme>,
    themeExpanded: Boolean,
    onToggleThemeExpanded: () -> Unit,
    onSetDynamicTheme: (Boolean) -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetPureBlack: (Boolean) -> Unit
) {
    Section(title = stringResource(R.string.settings_appearance_section)) {
        ToggleItem(
            title = stringResource(R.string.settings_dynamic_theme_title),
            subtitle = stringResource(R.string.settings_dynamic_theme_subtitle),
            isEnabled = uiState.dynamicTheme,
            onToggle = onSetDynamicTheme,
            icon = Icons.Default.AutoAwesome
        )

        ExpandableItem(
            title = stringResource(R.string.settings_theme_title),
            subtitle = uiState.appTheme.toAppTheme().toDisplayString(),
            isExpanded = themeExpanded,
            onToggle = onToggleThemeExpanded,
            icon = Icons.Default.Brightness4
        ) {
            availableThemes.forEach { theme ->
                OptionItem(
                    label = theme.toDisplayString(),
                    isSelected = uiState.appTheme.toAppTheme() == theme,
                    onClick = { onSetTheme(theme) }
                )
            }
        }

        ToggleItem(
            title = stringResource(R.string.settings_pure_black_title),
            subtitle = stringResource(R.string.settings_pure_black_subtitle),
            isEnabled = uiState.pureBlack,
            onToggle = onSetPureBlack,
            icon = Icons.Default.Contrast
        )
    }
}

@Composable
private fun AppTheme.toDisplayString(): String = when (this) {
    AppTheme.LIGHT -> stringResource(R.string.settings_theme_light)
    AppTheme.DARK -> stringResource(R.string.settings_theme_dark)
    AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun GeneralSection(
    uiState: SettingsUiState,
    languages: List<Pair<String, String>>,
    languageExpanded: Boolean,
    onToggleLanguageExpanded: () -> Unit,
    onSetLanguage: (String) -> Unit
) {
    Section(title = stringResource(R.string.settings_general_section)) {
        ExpandableItem(
            title = stringResource(R.string.settings_language_title),
            subtitle = uiState.appLanguage.getLanguageDisplayName(),
            isExpanded = languageExpanded,
            onToggle = onToggleLanguageExpanded,
            icon = Icons.Default.Translate
        ) {
            languages.forEach { (code, name) ->
                OptionItem(
                    label = name,
                    isSelected = uiState.appLanguage == code,
                    onClick = { onSetLanguage(code) }
                )
            }
        }
    }
}

@Composable
private fun AboutSection(onAboutClick: () -> Unit) {
    val context = LocalContext.current

    Section(title = stringResource(R.string.settings_about_section)) {
        Item(
            title = stringResource(R.string.settings_rate_review),
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data =
                            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Handle error silently
                }
            },
            icon = Icons.Default.StarRate
        )
        Item(
            title = stringResource(R.string.settings_about_us),
            onClick = onAboutClick,
            icon = Icons.Default.Info
        )
    }
}

private val availableLanguages = listOf(
    "System" to "System Default",
    "en" to "English",
    "hi" to "हिन्दी"
)

private fun String.getLanguageDisplayName(): String {
    return availableLanguages.find { it.first == this }?.second ?: "System Default"
}
