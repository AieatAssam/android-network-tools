package net.aieat.netswissknife.app.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Outcome of one platform scan request. */
data class ScanRequestOutcome(
    val startScanReturned: Boolean,
    val broadcastArrived: Boolean
) {
    /** Android returns false when the request is rejected, including throttling. */
    val throttled: Boolean get() = !startScanReturned
}

/** Pure decision function used by the Android adapter and JVM tests. */
fun decideOutcome(startScanReturned: Boolean, broadcastArrived: Boolean): ScanRequestOutcome =
    ScanRequestOutcome(
        startScanReturned = startScanReturned,
        broadcastArrived = broadcastArrived
    )

/**
 * Starts one foreground Wi-Fi scan and waits for the platform completion event.
 * The receiver is deliberately scoped to this request so it cannot leak into a
 * later refresh or keep the app alive after the timeout.
 */
class ScanRequestAwaiter(
    private val context: Context,
    private val wifiManager: WifiManager
) {

    @Suppress("DEPRECATION")
    suspend fun requestAndAwait(timeoutMs: Long): ScanRequestOutcome {
        var startScanReturned = false
        var broadcastArrived = false
        var receiver: BroadcastReceiver? = null
        var receiverRegistered = false

        try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val scanReceiver = object : BroadcastReceiver() {
                        override fun onReceive(receiverContext: Context?, intent: Intent?) {
                            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                            broadcastArrived = true
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    receiver = scanReceiver
                    ContextCompat.registerReceiver(
                        context,
                        scanReceiver,
                        IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    receiverRegistered = true
                    continuation.invokeOnCancellation {
                        if (receiverRegistered) unregisterQuietly(scanReceiver)
                    }

                    // startScan() is deprecated since API 28, but remains the
                    // documented foreground trigger on API 26–37. Android's
                    // foreground throttle is four scans per two minutes.
                    startScanReturned = wifiManager.startScan()
                    if (!startScanReturned && continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        } finally {
            receiver?.let { unregisterQuietly(it) }
            receiverRegistered = false
        }

        return decideOutcome(startScanReturned, broadcastArrived)
    }

    private fun unregisterQuietly(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // The receiver may already have been removed by cancellation cleanup.
        }
    }
}
