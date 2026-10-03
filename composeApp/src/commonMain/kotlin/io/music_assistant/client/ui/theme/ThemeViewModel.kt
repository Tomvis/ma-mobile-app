package io.music_assistant.client.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ThemeViewModel(
    private val settingsRepository: SettingsRepository,
    private val homeTheme: HomeThemeRepository,
) : ViewModel() {
    // Fork (HW-65): the person's home mode decides light/dark once known; upstream's setting otherwise.
    val theme = combine(settingsRepository.theme, homeTheme.effective) { own, home ->
        home?.mode?.toThemeSetting() ?: own
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.theme.value)

    fun switchTheme(theme: ThemeSetting) {
        settingsRepository.switchTheme(theme)
        homeTheme.effective.value?.let { homeTheme.choose(it.theme, theme.toHomeMode()) }
    }
}
