package net.aieat.netswissknife.app.ui.screens.onboarding

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
class OnboardingViewModel
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ViewModel() {
        // Wait for the stored choice before showing the modal sheet. An optimistic
        // true value briefly opens it for returning users on every process start.
        val shouldShowOnboarding: StateFlow<Boolean?> =
            dataStore.data
                .map<Preferences, Boolean?> { prefs -> prefs[AppPreferenceKeys.ONBOARDING_COMPLETED] != true }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun completeOnboarding() {
            viewModelScope.launch {
                dataStore.edit { it[AppPreferenceKeys.ONBOARDING_COMPLETED] = true }
            }
        }
    }
