package net.aieat.netswissknife.core.network.wifi

/**
 * Contract for obtaining Wi-Fi scan results.
 *
 * The interface is platform-agnostic (pure Kotlin); implementations in the
 * `:app` module use Android's [android.net.wifi.WifiManager].
 */
interface WifiScanRepository {
    /**
     * Returns true when the device has Wi-Fi hardware.
     * Always check this before calling [scan].
     */
    val isSupported: Boolean

    /**
     * Performs (or reads cached) scan results and returns an aggregated [WifiScanResult].
     * May throw if Wi-Fi is disabled or the required permissions are missing.
     */
    suspend fun scan(): WifiScanResult
}
