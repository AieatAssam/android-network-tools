package net.aieat.netswissknife.app.ui.navigation

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
class AppNavigationViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val pinnedRoutes: StateFlow<List<String>> = dataStore.data
        .map { prefs ->
            prefs[AppPreferenceKeys.PINNED_ROUTES]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: DEFAULT_PINNED_ROUTES
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_PINNED_ROUTES
        )

    fun togglePin(route: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val current = prefs[AppPreferenceKeys.PINNED_ROUTES]
                    ?.split("|")
                    ?.filter { it.isNotBlank() }
                    ?.toMutableList()
                    ?: DEFAULT_PINNED_ROUTES.toMutableList()

                if (current.contains(route)) {
                    current.remove(route)
                } else if (current.size < MAX_PINNED) {
                    current.add(route)
                }

                prefs[AppPreferenceKeys.PINNED_ROUTES] = current.joinToString("|")
            }
        }
    }

    companion object {
        const val MAX_PINNED = 3
        val DEFAULT_PINNED_ROUTES = listOf("ping", "dns", "ports")
    }
}
