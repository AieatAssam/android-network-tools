package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.wifi.WifiScanRepository
import com.example.netswissknife.core.network.wifi.WifiScanResult

/**
 * Use case that orchestrates a Wi-Fi environment scan.
 *
 * The use case is a thin delegation layer – it validates preconditions and
 * delegates to the [WifiScanRepository] for the actual platform work.
 */
class WifiScanUseCase(private val repository: WifiScanRepository) {

    /** True when the device has Wi-Fi hardware. */
    val isSupported: Boolean get() = repository.isSupported

    /**
     * Trigger (or read cached) scan results.
     *
     * @throws WifiNotSupportedException if the device lacks Wi-Fi hardware.
     * @throws Exception propagated from the repository on I/O or permission errors.
     */
    suspend operator fun invoke(): WifiScanResult {
        if (!repository.isSupported) throw WifiNotSupportedException()
        return repository.scan()
    }
}

class WifiNotSupportedException : Exception("Wi-Fi hardware not available on this device")
