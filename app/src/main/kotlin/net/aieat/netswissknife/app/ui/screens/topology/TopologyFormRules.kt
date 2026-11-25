package net.aieat.netswissknife.app.ui.screens.topology

import androidx.annotation.StringRes
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.topology.TopologyParams
import net.aieat.netswissknife.core.network.topology.TopologyParamsValidator

/** Credential problems the form flags inline. Target and timing problems keep their own messages. */
private val CREDENTIAL_ERROR_CODES =
    setOf(
        ErrorCode.SNMP_COMMUNITY_BLANK,
        ErrorCode.SNMP_V3_USER_BLANK,
        ErrorCode.SNMP_V3_PRIV_WITHOUT_AUTH,
        ErrorCode.SNMP_V3_AUTH_PASSWORD_BLANK,
        ErrorCode.SNMP_V3_PRIV_PASSWORD_BLANK,
    )

/** The credential error codes that apply to [params], so the form can mark each field and gate Discover. */
internal fun topologyCredentialErrors(params: TopologyParams): Set<ErrorCode> =
    TopologyParamsValidator
        .validate(params)
        .errors
        .map { it.code }
        .filterTo(mutableSetOf()) { it in CREDENTIAL_ERROR_CODES }

/**
 * A failure belongs to the inputs it was run with. Once any form input differs from those inputs
 * the failure is hidden, so the card never describes a different SNMP profile. A null [runParams]
 * means the run is unknown (for example after a restore), and the failure is kept.
 */
internal fun TopologyUiState.withoutStaleFailure(
    runParams: TopologyParams?,
    formParams: TopologyParams,
): TopologyUiState =
    if (this is TopologyUiState.Failure && runParams != null && runParams != formParams) {
        TopologyUiState.Idle
    } else {
        this
    }

/** Why Discover is disabled, or null when it is enabled apart from a run already in progress. */
@StringRes
internal fun topologyDisabledReasonRes(
    hasValidTarget: Boolean,
    credentialErrors: Set<ErrorCode>,
): Int? =
    when {
        !hasValidTarget -> R.string.topology_discover_disabled_target
        ErrorCode.SNMP_V3_PRIV_WITHOUT_AUTH in credentialErrors -> R.string.topology_discover_disabled_v3
        credentialErrors.isNotEmpty() -> R.string.topology_discover_disabled_credentials
        else -> null
    }
