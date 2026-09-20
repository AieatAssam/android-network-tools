package net.aieat.netswissknife.app.ui.screens.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.wifi.WifiConnectionInfo
import net.aieat.netswissknife.core.network.wifi.WifiScanResult

@Composable
fun WifiLocationDisabledScreen(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(R.string.wifi_location_disabled_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.wifi_location_disabled_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.wifi_open_location_settings))
                }
            }
        }
    }
}

@Composable
fun WifiScanFreshnessStatus(result: WifiScanResult) {
    val ageSeconds = result.scanAgeMs?.let { (it / 1_000L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    val ageLabel = if (ageSeconds == null) {
        stringResource(R.string.wifi_results_age_unknown)
    } else {
        pluralStringResource(R.plurals.wifi_results_age, ageSeconds, ageSeconds)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = {},
            label = {
                Text(
                    if (result.throttled) stringResource(R.string.wifi_scan_throttled)
                    else ageLabel
                )
            }
        )
        if (result.throttled) {
            Text(
                ageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WifiRefreshIntervalPicker(
    selectedIntervalMs: Long?,
    onSelected: (Long?) -> Unit
) {
    val options = listOf<Long?>(null, 15_000L, 30_000L, 60_000L)
    val labels = listOf(
        stringResource(R.string.wifi_refresh_interval_off),
        stringResource(R.string.wifi_refresh_interval_15s),
        stringResource(R.string.wifi_refresh_interval_30s),
        stringResource(R.string.wifi_refresh_interval_60s)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, interval ->
            SegmentedButton(
                selected = selectedIntervalMs == interval,
                onClick = { onSelected(interval) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(labels[index], style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun WifiConnectedNetworkCard(info: WifiConnectionInfo) {
    val unavailableValue = stringResource(R.string.wifi_value_unavailable)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.wifi_connected_network_header),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            ConnectionDetailRow(
                stringResource(R.string.wifi_detail_ip),
                info.ipAddress.ifBlank { unavailableValue }
            )
            if (info.ipv6Addresses.isNotEmpty()) {
                ConnectionDetailRow(
                    stringResource(R.string.wifi_detail_ipv6),
                    info.ipv6Addresses.joinToString("\n")
                )
            }
            info.gateway?.let {
                ConnectionDetailRow(stringResource(R.string.wifi_detail_gateway), it)
            }
            if (info.dnsServers.isNotEmpty()) {
                ConnectionDetailRow(
                    stringResource(R.string.wifi_detail_dns),
                    info.dnsServers.joinToString("\n")
                )
            }
        }
    }
}

@Composable
private fun ConnectionDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
