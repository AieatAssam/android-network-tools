package net.aieat.netswissknife.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkStatus

enum class NetworkStatusScope { INTERNET, LOCAL_NETWORK, ANY_NETWORK }

/** Shows only connectivity states that can change how the current tool reaches its target. */
@Composable
fun NetworkStatusBanner(
    status: NetworkStatus,
    scope: NetworkStatusScope,
    modifier: Modifier = Modifier,
    permissionDenied: Boolean = false,
    onGrantPermission: (() -> Unit)? = null,
) {
    val noNetworkCapabilities = !status.hasInternet && !status.hasLocalNetwork && !status.vpnActive
    val statusMessage = when {
        scope == NetworkStatusScope.INTERNET && !status.hasInternet -> R.string.network_banner_no_internet
        scope == NetworkStatusScope.INTERNET && !status.hasValidatedInternet ->
            R.string.network_banner_internet_unvalidated
        scope == NetworkStatusScope.LOCAL_NETWORK && !status.hasLocalNetwork && status.vpnActive ->
            R.string.network_banner_local_vpn_route_unknown
        scope == NetworkStatusScope.LOCAL_NETWORK && !status.hasLocalNetwork -> R.string.network_banner_no_local
        scope == NetworkStatusScope.ANY_NETWORK && noNetworkCapabilities && status.transport == null ->
            R.string.network_banner_no_network
        scope == NetworkStatusScope.ANY_NETWORK && noNetworkCapabilities && status.transport != null ->
            R.string.network_banner_route_unknown
        scope == NetworkStatusScope.LOCAL_NETWORK && status.vpnActive -> R.string.network_banner_vpn_info
        else -> null
    }
    val isAdvisoryMessage = statusMessage == R.string.network_banner_vpn_info ||
        statusMessage == R.string.network_banner_internet_unvalidated ||
        statusMessage == R.string.network_banner_local_vpn_route_unknown ||
        statusMessage == R.string.network_banner_route_unknown

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = statusMessage != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(STATUS_BANNER_TEST_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isAdvisoryMessage) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isAdvisoryMessage) Icons.Default.Info else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isAdvisoryMessage) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                    Text(
                        text = statusMessage?.let { stringResource(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAdvisoryMessage) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = permissionDenied,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PERMISSION_CARD_TEST_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.network_error_local_permission),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (onGrantPermission != null) {
                        Button(onClick = onGrantPermission) {
                            Text(stringResource(R.string.network_action_grant_permission))
                        }
                    }
                }
            }
        }
    }
}

const val STATUS_BANNER_TEST_TAG = "network-status-banner"
const val PERMISSION_CARD_TEST_TAG = "network-permission-card"
