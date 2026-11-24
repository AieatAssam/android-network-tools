package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanScanSummary
import net.aieat.netswissknife.core.network.operation.CancellationReason

/** [state] with [ip] expanded, or collapsed when it already was; null when [state] has no host rows. */
internal fun toggleHostState(
    state: LanScanUiState,
    ip: String,
): LanScanUiState? =
    when (state) {
        is LanScanUiState.Scanning -> {
            state.copy(expandedHostIp = if (state.expandedHostIp == ip) null else ip)
        }

        is LanScanUiState.Finished -> {
            state.copy(expandedHostIp = if (state.expandedHostIp == ip) null else ip)
        }

        is LanScanUiState.Canceled -> {
            state.copy(expandedHostIp = if (state.expandedHostIp == ip) null else ip)
        }

        else -> {
            null
        }
    }

/** [state] with its expanded host collapsed, or null when no host row is expanded. */
internal fun collapseHostState(state: LanScanUiState): LanScanUiState? =
    when (state) {
        is LanScanUiState.Scanning -> state.expandedHostIp?.let { state.copy(expandedHostIp = null) }
        is LanScanUiState.Finished -> state.expandedHostIp?.let { state.copy(expandedHostIp = null) }
        is LanScanUiState.Canceled -> state.expandedHostIp?.let { state.copy(expandedHostIp = null) }
        else -> null
    }

/** [state] with the diagnostics panel toggled, or null when [state] is not a finished or canceled scan. */
internal fun toggleDiagnosticsState(state: LanScanUiState): LanScanUiState? =
    when (state) {
        is LanScanUiState.Finished -> state.copy(showDiagnostics = !state.showDiagnostics)
        is LanScanUiState.Canceled -> state.copy(showDiagnostics = !state.showDiagnostics)
        else -> null
    }

/**
 * The terminal state a scan that is still stopping reaches for [reason]: a user Stop stays
 * Canceled, a deadline finishes as a partial result that reached the time limit, and anything
 * else (a pause, or no recorded reason) finishes as a partial result.
 */
internal fun stoppingScanStateToKeep(
    summary: LanScanSummary,
    reason: CancellationReason?,
    expandedHostIp: String?,
): LanScanUiState =
    when (reason) {
        CancellationReason.USER_STOP -> {
            LanScanUiState.Canceled(summary, expandedHostIp = expandedHostIp)
        }

        CancellationReason.DEADLINE_EXCEEDED -> {
            LanScanUiState.Finished(
                summary,
                expandedHostIp = expandedHostIp,
                partial = true,
                timeLimitReached = true,
            )
        }

        else -> {
            LanScanUiState.Finished(summary, expandedHostIp = expandedHostIp, partial = true)
        }
    }
