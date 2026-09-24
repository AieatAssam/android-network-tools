package net.aieat.netswissknife.app.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.location.LocationManagerCompat
import net.aieat.netswissknife.core.network.lan.OuiDatabase
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiChannelHelper
import net.aieat.netswissknife.core.network.wifi.WifiChannelAnalyzer
import net.aieat.netswissknife.core.network.wifi.WifiConnectionInfo
import net.aieat.netswissknife.core.network.wifi.WifiScanRepository
import net.aieat.netswissknife.core.network.wifi.WifiScanOperation
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiScanFreshness
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WifiScanRepositoryImpl(private val context: Context) : WifiScanRepository {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val locationManager: LocationManager by lazy {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val scanRequestAwaiter: ScanRequestAwaiter by lazy {
        ScanRequestAwaiter(context.applicationContext, wifiManager)
    }

    override val isSupported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)

    override val isLocationEnabled: Boolean
        get() = LocationManagerCompat.isLocationEnabled(locationManager)

    // Permission is verified by the caller (WifiScanScreen) before invoking scan(); a
    // SecurityException here (e.g. permission revoked mid-session) is caught by
    // WifiScanViewModel and surfaced as WifiScanUiState.NoPermission.
    override suspend fun scan(trigger: Boolean): WifiScanResult =
        scan(trigger, WifiScanOperation.newSession())

    @SuppressLint("MissingPermission")
    override suspend fun scan(
        trigger: Boolean,
        operationSession: OperationSession,
    ): WifiScanResult = OperationRunner.runOrJoin(operationSession) {
        withContext(Dispatchers.IO) {
            ensureOperationActive()
            val locationEnabled = isLocationEnabled
            ensureOperationActive()
            val requestOutcome = if (trigger && locationEnabled) {
                scanRequestAwaiter.requestAndAwait(
                    timeoutMs = SCAN_TIMEOUT_MS.coerceAtMost(budget.remainingTimeoutMillis()),
                    operationSession = operationSession,
                )
            } else {
                null
            }
            ensureOperationActive()
            val rawResults: List<ScanResult> = wifiManager.scanResults ?: emptyList()
            ensureOperationActive()
            val activeConnection = getActiveWifiConnection()
            val connectedBssid = activeConnection?.wifiInfo?.bssid
                ?.takeIf { it != "02:00:00:00:00:00" }
            val connectedInfo = activeConnection?.let { buildConnectionInfo(it.wifiInfo, it.linkProperties) }

            val accessPoints = rawResults
                .map { sr -> mapScanResult(sr, connectedBssid) }
                .sortedByDescending { it.rssi }

            val channels = WifiChannelAnalyzer.analyze(accessPoints)
            val cacheReadElapsedRealtimeMs = SystemClock.elapsedRealtime()
            val freshness = WifiScanFreshness.compute(
                newestTimestampUs = rawResults.maxOfOrNull { it.timestamp },
                nowElapsedMs = cacheReadElapsedRealtimeMs
            )
            val refreshStatus = requestOutcome?.status ?: WifiScanRefreshStatus.NOT_REQUESTED
            val sampledAtMs = estimateScanSampleTimeMs(
                nowWallClockMs = System.currentTimeMillis(),
                scanAgeMs = freshness.ageMs,
                refreshStatus = refreshStatus
            )
            ensureOperationActive()

            WifiScanResult(
                accessPoints = accessPoints,
                channels = channels,
                connectedNetwork = connectedInfo,
                scanTimestampMs = sampledAtMs,
                isWifiEnabled = wifiManager.isWifiEnabled,
                isFresh = freshness.isFresh,
                scanAgeMs = freshness.ageMs,
                cacheReadElapsedRealtimeMs = cacheReadElapsedRealtimeMs,
                refreshStatus = refreshStatus,
                locationEnabled = locationEnabled
            )
        }
    }

    // ── Connected network info ────────────────────────────────────────────────

    private data class ActiveWifiConnection(
        val wifiInfo: WifiInfo,
        val linkProperties: LinkProperties?
    )

    @SuppressLint("MissingPermission")
    private fun getActiveWifiConnection(): ActiveWifiConnection? {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                transportWifiInfo(capabilities)
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }
            return wifiInfo?.let {
                ActiveWifiConnection(it, connectivityManager.getLinkProperties(network))
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            return wifiManager.connectionInfo?.let { ActiveWifiConnection(it, null) }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun transportWifiInfo(capabilities: NetworkCapabilities): WifiInfo? =
        capabilities.transportInfo as? WifiInfo

    // Same permission guarantee as scan(): caller has already verified the permission
    // before scan() (and transitively this) is invoked.
    @SuppressLint("MissingPermission")
    private fun buildConnectionInfo(wi: WifiInfo, linkProperties: LinkProperties?): WifiConnectionInfo? {
        val rawSsid = wi.ssid ?: return null
        val ssid = rawSsid.removeSurrounding("\"")
        if (ssid == "<unknown ssid>" || ssid.isBlank()) return null

        val frequency = wi.frequency
        val band = WifiBand.fromFrequency(frequency)
        val channel = WifiChannelHelper.frequencyToChannel(frequency, band)

        val txSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wi.txLinkSpeedMbps else -1
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wi.rxLinkSpeedMbps else -1
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mapWifiInfoStandard(wi.wifiStandard)
        } else {
            inferStandardFromBand(band)
        }

        val addresses = WifiConnectionInfoMapper.mapLinkAddresses(
            linkProperties?.linkAddresses.orEmpty().mapNotNull { it.address.hostAddress }
        )
        val gateway = linkProperties?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway?.isAnyLocalAddress == false }
            ?.gateway
            ?.hostAddress
            ?.substringBefore('%')
        val dnsServers = linkProperties?.dnsServers.orEmpty()
            .mapNotNull { it.hostAddress?.substringBefore('%') }

        // Find the capabilities from scan results to determine security
        val security = wifiManager.scanResults
            ?.find { it.BSSID == wi.bssid }
            ?.capabilities
            ?.let { WifiSecurity.fromCapabilities(it) }
            ?: WifiSecurity.UNKNOWN

        return WifiConnectionInfo(
            ssid = ssid,
            bssid = wi.bssid ?: "",
            rssi = wi.rssi,
            frequency = frequency,
            channel = channel,
            band = band,
            linkSpeedMbps = wi.linkSpeed,
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            ipAddress = addresses.ipv4Address,
            standard = standard,
            security = security,
            ipv6Addresses = addresses.ipv6Addresses,
            gateway = gateway,
            dnsServers = dnsServers
        )
    }

    // ── Scan result mapping ───────────────────────────────────────────────────

    private fun mapScanResult(sr: ScanResult, connectedBssid: String?): WifiAccessPoint {
        val band = WifiBand.fromFrequency(sr.frequency)
        val channel = WifiChannelHelper.frequencyToChannel(sr.frequency, band)
        val security = WifiSecurity.fromCapabilities(sr.capabilities)
        val standard = getScanResultStandard(sr, band)
        val widthMhz = WifiChannelHelper.channelWidthMhz(sr.channelWidth)
        val vendor = OuiDatabase.lookup(sr.BSSID ?: "") ?: ""

        return WifiAccessPoint(
            ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sr.wifiSsid?.toString()?.removeSurrounding("\"") ?: ""
            } else {
                @Suppress("DEPRECATION")
                sr.SSID?.removeSurrounding("\"") ?: ""
            },
            bssid = sr.BSSID ?: "",
            rssi = sr.level,
            frequency = sr.frequency,
            channelWidthMhz = widthMhz,
            capabilities = sr.capabilities ?: "",
            channel = channel,
            band = band,
            standard = standard,
            security = security,
            isConnected = (sr.BSSID != null && sr.BSSID == connectedBssid),
            vendor = vendor,
            centerFrequency0 = sr.centerFreq0,
            centerFrequency1 = sr.centerFreq1,
            timestampUs = sr.timestamp
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getScanResultStandard(sr: ScanResult, band: WifiBand): WifiStandard {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (sr.wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
                ScanResult.WIFI_STANDARD_11N    -> WifiStandard.WIFI_4
                ScanResult.WIFI_STANDARD_11AC   -> WifiStandard.WIFI_5
                ScanResult.WIFI_STANDARD_11AX   -> if (band == WifiBand.BAND_6GHZ) WifiStandard.WIFI_6E else WifiStandard.WIFI_6
                ScanResult.WIFI_STANDARD_11AD   -> WifiStandard.LEGACY
                ScanResult.WIFI_STANDARD_11BE   -> WifiStandard.WIFI_7
                else -> WifiStandard.UNKNOWN
            }
        } else {
            inferStandardFromBand(band)
        }
    }

    private fun inferStandardFromBand(band: WifiBand): WifiStandard = when (band) {
        WifiBand.BAND_6GHZ  -> WifiStandard.WIFI_6E
        WifiBand.BAND_5GHZ  -> WifiStandard.WIFI_5
        WifiBand.BAND_2_4GHZ -> WifiStandard.WIFI_4
        WifiBand.BAND_60GHZ -> WifiStandard.LEGACY
        WifiBand.UNKNOWN    -> WifiStandard.UNKNOWN
    }

    private fun mapWifiInfoStandard(standard: Int): WifiStandard {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return WifiStandard.UNKNOWN
        return when (standard) {
            ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
            ScanResult.WIFI_STANDARD_11N    -> WifiStandard.WIFI_4
            ScanResult.WIFI_STANDARD_11AC   -> WifiStandard.WIFI_5
            ScanResult.WIFI_STANDARD_11AX   -> WifiStandard.WIFI_6
            ScanResult.WIFI_STANDARD_11BE   -> WifiStandard.WIFI_7
            else -> WifiStandard.UNKNOWN
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 8_000L
    }
}
