package com.example.netswissknife.app.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class AppNavigationViewModel @Inject constructor() : ViewModel() {

    private val _pinnedRoutes = MutableStateFlow(NavRoutes.defaultPinnedRoutes)
    val pinnedRoutes: StateFlow<List<String>> = _pinnedRoutes.asStateFlow()

    fun togglePin(route: String) {
        val current = _pinnedRoutes.value.toMutableList()
        if (current.contains(route)) {
            current.remove(route)
        } else if (current.size < MAX_PINNED) {
            current.add(route)
        }
        _pinnedRoutes.value = current
    }

    companion object {
        const val MAX_PINNED = 3
    }
}
