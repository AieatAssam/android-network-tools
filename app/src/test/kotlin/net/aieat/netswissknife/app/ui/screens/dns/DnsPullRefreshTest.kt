package net.aieat.netswissknife.app.ui.screens.dns

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DnsPullRefreshTest {
    @Test
    @DisplayName("Spinner shows only while a pull-started lookup is loading")
    fun spinnerFollowsPullStartedLoading() {
        assertTrue(dnsPullRefreshActive(pullRequested = true, state = DnsUiState.Loading))
    }

    @Test
    @DisplayName("Button-started lookup does not show the pull spinner")
    fun buttonStartedLookupShowsNoSpinner() {
        assertFalse(dnsPullRefreshActive(pullRequested = false, state = DnsUiState.Loading))
    }

    @Test
    @DisplayName("Spinner stops once a pull-started lookup leaves the loading state")
    fun spinnerStopsAfterLoading() {
        assertFalse(dnsPullRefreshActive(pullRequested = true, state = DnsUiState.Idle))
    }
}
