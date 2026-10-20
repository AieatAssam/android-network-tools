package net.aieat.netswissknife.core.network.wifi

import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

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
    suspend fun scan(trigger: Boolean = true): WifiScanResult

    /** Caller-owned bounded operation variant; older repository implementations remain valid. */
    suspend fun scan(trigger: Boolean = true, operationSession: OperationSession): WifiScanResult =
        scan(trigger)

    /** Whether Android Location Services are currently enabled for Wi-Fi scanning. */
    val isLocationEnabled: Boolean
}

/** Shared bounded policy for one user-requested Wi-Fi scan. */
object WifiScanOperation {
    const val TIMEOUT_MILLIS = 12_000L

    fun newSession(clock: MonotonicClock = SystemMonotonicClock): OperationSession =
        OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.LOCAL_NETWORK,
                timeoutMillis = TIMEOUT_MILLIS,
                maxConcurrentProbes = 1,
                clock = clock,
            ),
        )
}
