package com.example.netswissknife.app.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.netswissknife.core.network.lan.OuiDatabase
import com.example.netswissknife.core.network.wifi.WifiAccessPoint
import com.example.netswissknife.core.network.wifi.WifiBand
import com.example.netswissknife.core.network.wifi.WifiChannelHelper
import com.example.netswissknife.core.network.wifi.WifiChannelInfo
import com.example.netswissknife.core.network.wifi.WifiConnectionInfo
import com.example.netswissknife.core.network.wifi.WifiScanRepository
import com.example.netswissknife.core.network.wifi.WifiScanResult
import com.example.netswissknife.core.network.wifi.WifiSecurity
import com.example.netswissknife.core.network.wifi.WifiStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

class WifiScanRepositoryImpl(private val context: Context) : WifiScanRepository {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override val isSupported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)

    override suspend fun scan(): WifiScanResult = withContext(Dispatchers.IO) {
        val rawResults: List<ScanResult> = wifiManager.scanResults ?: emptyList()
        val connectedBssid = getConnectedBssid()
        val connectedInfo = getConnectionInfo(connectedBssid)

        val accessPoints = rawResults
            .map { sr -> mapScanResult(sr, connectedBssid) }
            .sortedByDescending { it.rssi }

        val channels = buildChannelInfo(accessPoints)

        WifiScanResult(
            accessPoints = accessPoints,
            channels = channels,
            connectedNetwork = connectedInfo,
            scanTimestampMs = System.currentTimeMillis(),
            isWifiEnabled = wifiManager.isWifiEnabled
        )
    }

    // ── Connected network info ────────────────────────────────────────────────

    private fun getConnectedBssid(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connectivityManager.activeNetwork ?: return null
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
            val wifiInfo = caps.transportInfo as? WifiInfo ?: return null
            wifiInfo.bssid?.takeIf { it != "02:00:00:00:00:00" }
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
        }
    }

    private fun getConnectionInfo(connectedBssid: String?): WifiConnectionInfo? {
        if (connectedBssid == null) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connectivityManager.activeNetwork ?: return null
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
            val wi = caps.transportInfo as? WifiInfo ?: return null
            buildConnectionInfo(wi)
        } else {
            @Suppress("DEPRECATION")
            val wi = wifiManager.connectionInfo ?: return null
            buildConnectionInfo(wi)
        }
    }

    private fun buildConnectionInfo(wi: WifiInfo): WifiConnectionInfo? {
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

        @Suppress("DEPRECATION")
        val ipInt = wi.ipAddress
        val ipAddress = intToIpAddress(ipInt)

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
            ipAddress = ipAddress,
            standard = standard,
            security = security
        )
    }

    // ── Scan result mapping ───────────────────────────────────────────────────

    private fun mapScanResult(sr: ScanResult, connectedBssid: String?): WifiAccessPoint {
        val band = WifiBand.fromFrequency(sr.frequency)
        val channel = WifiChannelHelper.frequencyToChannel(sr.frequency, band)
        val security = WifiSecurity.fromCapabilities(sr.capabilities ?: "")
        val standard = getScanResultStandard(sr, band)
        val widthMhz = WifiChannelHelper.channelWidthMhz(sr.channelWidth)
        val vendor = OuiDatabase.lookup(sr.BSSID ?: "") ?: ""

        return WifiAccessPoint(
            ssid = sr.SSID?.removeSurrounding("\"") ?: "",
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

    // ── Channel congestion ────────────────────────────────────────────────────

    private fun buildChannelInfo(accessPoints: List<WifiAccessPoint>): List<WifiChannelInfo> {
        val byChannel = accessPoints.groupBy { it.channel }

        return byChannel.map { (channel, aps) ->
            val band = aps.first().band
            val freqMhz = aps.first().frequency

            // Effective interference = sum of linear signal powers (in arbitrary units)
            // for APs on this channel and overlapping channels (2.4 GHz only).
            val interference = when (band) {
                WifiBand.BAND_2_4GHZ -> {
                    val overlapping = WifiChannelHelper.overlapping24GHzChannels(channel)
                    accessPoints
                        .filter { it.channel in overlapping }
                        .sumOf { 10.0.pow(it.rssi / 10.0) }
                }
                else -> aps.sumOf { 10.0.pow(it.rssi / 10.0) }
            }

            // Normalize: treat -40 dBm * 10 APs as "very busy" → score ≈ 1.0
            val maxInterference = 10.0.pow(-40.0 / 10.0) * 10.0
            val congestion = (interference / maxInterference).coerceIn(0.0, 1.0).toFloat()

            WifiChannelInfo(
                channel = channel,
                frequencyMhz = freqMhz,
                band = band,
                accessPointCount = aps.size,
                congestionScore = congestion,
                accessPoints = aps.sortedByDescending { it.rssi }
            )
        }.sortedWith(compareBy({ it.band.ordinal }, { it.channel }))
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

    private fun intToIpAddress(ip: Int): String {
        if (ip == 0) return ""
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}
