package net.aieat.netswissknife.app.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val themeOverride: StateFlow<String> = dataStore.data
        .map { it[AppPreferenceKeys.THEME_OVERRIDE] ?: "SYSTEM" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")

    val defaultPingCount: StateFlow<Int> = dataStore.data
        .map { it[AppPreferenceKeys.DEFAULT_PING_COUNT] ?: 10 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 10)

    val defaultTimeoutMs: StateFlow<Int> = dataStore.data
        .map { it[AppPreferenceKeys.DEFAULT_TIMEOUT_MS] ?: 2000 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2000)

    val defaultConcurrency: StateFlow<Int> = dataStore.data
        .map { it[AppPreferenceKeys.DEFAULT_CONCURRENCY] ?: 50 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 50)

    fun setThemeOverride(value: String) {
        viewModelScope.launch {
            dataStore.edit { it[AppPreferenceKeys.THEME_OVERRIDE] = value }
        }
    }

    fun setDefaultPingCount(value: Int) {
        viewModelScope.launch {
            dataStore.edit { it[AppPreferenceKeys.DEFAULT_PING_COUNT] = value.coerceIn(1, 100) }
        }
    }

    fun setDefaultTimeoutMs(value: Int) {
        viewModelScope.launch {
            dataStore.edit { it[AppPreferenceKeys.DEFAULT_TIMEOUT_MS] = value.coerceIn(100, 30_000) }
        }
    }

    fun setDefaultConcurrency(value: Int) {
        viewModelScope.launch {
            dataStore.edit { it[AppPreferenceKeys.DEFAULT_CONCURRENCY] = value.coerceIn(1, 500) }
        }
    }

    fun clearAllRecentHosts() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                AppPreferenceKeys.ALL_RECENT_HOST_KEYS.forEach { prefs.remove(it) }
            }
        }
    }
}
