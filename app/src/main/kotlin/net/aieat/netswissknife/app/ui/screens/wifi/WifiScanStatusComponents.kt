package net.aieat.netswissknife.app.ui.screens.wifi

import android.os.SystemClock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.wifi.WifiConnectionInfo
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.signalLevelForRssi

@Composable
fun WifiLocationDisabledScreen(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(R.string.wifi_location_disabled_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.wifi_location_disabled_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.wifi_open_location_settings))
                }
            }
        }
    }
}

/** Ticks the scan age once every few seconds while the screen is resumed, so the label stays current. */
@Composable
private fun rememberWifiScanAgeSeconds(result: WifiScanResult): Long? {
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowElapsedMs by remember(result) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(result, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                nowElapsedMs = SystemClock.elapsedRealtime()
                delay(WIFI_AGE_TICK_MS)
            }
        }
    }
    return wifiScanAgeSeconds(result.scanAgeMs, result.cacheReadElapsedRealtimeMs, nowElapsedMs)
}

private const val WIFI_AGE_TICK_MS = 5_000L

@Composable
fun WifiScanFreshnessStatus(
    result: WifiScanResult,
    onRequestPermission: (() -> Unit)? = null,
) {
    val ageSeconds = rememberWifiScanAgeSeconds(result)
    val ageLabel =
        if (ageSeconds == null) {
            stringResource(R.string.wifi_results_age_unknown)
        } else {
            val count = ageSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            pluralStringResource(R.plurals.wifi_results_age, count, count)
        }
    val statusLabel =
        when (result.refreshStatus) {
            WifiScanRefreshStatus.NOT_REQUESTED,
            WifiScanRefreshStatus.UPDATED,
            -> null

            WifiScanRefreshStatus.NOT_UPDATED -> stringResource(R.string.wifi_scan_not_updated)

            WifiScanRefreshStatus.TIMED_OUT -> stringResource(R.string.wifi_scan_timed_out)

            WifiScanRefreshStatus.REJECTED -> stringResource(R.string.wifi_scan_rejected)

            WifiScanRefreshStatus.FAILED -> stringResource(R.string.wifi_scan_failed_cached)

            WifiScanRefreshStatus.PERMISSION_DENIED -> stringResource(R.string.wifi_scan_permission_denied_cached)
        }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (statusLabel != null) {
            Text(
                statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            ageLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (result.refreshStatus == WifiScanRefreshStatus.PERMISSION_DENIED && onRequestPermission != null) {
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.wifi_grant_permission))
            }
        }
    }
}

@Composable
fun WifiRefreshIntervalPicker(
    selectedIntervalMs: Long?,
    onSelected: (Long?) -> Unit,
) {
    val options = listOf<Long?>(null, 15_000L, 30_000L, 60_000L)
    val labels =
        listOf(
            stringResource(R.string.wifi_refresh_interval_off),
            stringResource(R.string.wifi_refresh_interval_15s),
            stringResource(R.string.wifi_refresh_interval_30s),
            stringResource(R.string.wifi_refresh_interval_60s),
        )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, interval ->
            SegmentedButton(
                selected = selectedIntervalMs == interval,
                onClick = { onSelected(interval) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(labels[index], style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun WifiConnectedNetworkCard(info: WifiConnectionInfo) {
    val unavailableValue = stringResource(R.string.wifi_value_unavailable)
    val title =
        if (info.ssid.isBlank()) {
            stringResource(R.string.wifi_connected_network_header)
        } else {
            stringResource(R.string.wifi_connected_to_ssid, info.ssid)
        }
    val levelLabel = stringResource(signalLevelLabelRes(signalLevelForRssi(info.rssi)))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            WifiConnectionLinkRows(info = info, levelLabel = levelLabel, unavailableValue = unavailableValue)
            WifiConnectionAddressRows(info = info, unavailableValue = unavailableValue)
        }
    }
}

/** BSSID, band and channel, signal, and the link speeds the platform reports. */
@Composable
private fun WifiConnectionLinkRows(
    info: WifiConnectionInfo,
    levelLabel: String,
    unavailableValue: String,
) {
    ConnectionDetailRow(stringResource(R.string.wifi_detail_bssid), info.bssid.ifBlank { unavailableValue })
    ConnectionDetailRow(
        stringResource(R.string.wifi_detail_band_channel),
        stringResource(R.string.wifi_band_channel_value, info.band.ghzLabel, info.channel),
    )
    ConnectionDetailRow(
        stringResource(R.string.wifi_detail_signal),
        stringResource(R.string.wifi_rssi_with_level, info.rssi, levelLabel),
    )
    ConnectionDetailRow(
        stringResource(R.string.wifi_detail_link_speed),
        stringResource(R.string.wifi_link_speed_value, info.linkSpeedMbps),
    )
    if (info.txLinkSpeedMbps >= 0) {
        ConnectionDetailRow(
            stringResource(R.string.wifi_detail_tx_speed),
            stringResource(R.string.wifi_link_speed_value, info.txLinkSpeedMbps),
        )
    }
    if (info.rxLinkSpeedMbps >= 0) {
        ConnectionDetailRow(
            stringResource(R.string.wifi_detail_rx_speed),
            stringResource(R.string.wifi_link_speed_value, info.rxLinkSpeedMbps),
        )
    }
}

/** IPv4 and IPv6 addresses, gateway and DNS servers. */
@Composable
private fun WifiConnectionAddressRows(
    info: WifiConnectionInfo,
    unavailableValue: String,
) {
    ConnectionDetailRow(
        stringResource(R.string.wifi_detail_ip),
        info.ipAddress.ifBlank { unavailableValue },
    )
    if (info.ipv6Addresses.isNotEmpty()) {
        ConnectionDetailRow(
            stringResource(R.string.wifi_detail_ipv6),
            info.ipv6Addresses.joinToString("\n"),
        )
    }
    info.gateway?.let {
        ConnectionDetailRow(stringResource(R.string.wifi_detail_gateway), it)
    }
    if (info.dnsServers.isNotEmpty()) {
        ConnectionDetailRow(
            stringResource(R.string.wifi_detail_dns),
            info.dnsServers.joinToString("\n"),
        )
    }
}

@Composable
private fun ConnectionDetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp),
        )
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
