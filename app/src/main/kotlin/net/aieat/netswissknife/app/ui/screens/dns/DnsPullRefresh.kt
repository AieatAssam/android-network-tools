package net.aieat.netswissknife.app.ui.screens.dns

/** The pull-to-refresh spinner only reflects a lookup that the pull gesture started. */
internal fun dnsPullRefreshActive(pullRequested: Boolean, state: DnsUiState): Boolean =
    pullRequested && state is DnsUiState.Loading
