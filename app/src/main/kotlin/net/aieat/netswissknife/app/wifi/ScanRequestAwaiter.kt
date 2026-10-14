package net.aieat.netswissknife.app.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Outcome of one platform scan request. */
data class ScanRequestOutcome(val status: WifiScanRefreshStatus)

/** Pure decision function used by the Android adapter and JVM tests. */
fun decideOutcome(
    startScanReturned: Boolean,
    broadcastArrived: Boolean,
    resultsUpdated: Boolean = false
): ScanRequestOutcome = ScanRequestOutcome(
    status = when {
        !startScanReturned -> WifiScanRefreshStatus.REJECTED
        !broadcastArrived -> WifiScanRefreshStatus.TIMED_OUT
        resultsUpdated -> WifiScanRefreshStatus.UPDATED
        else -> WifiScanRefreshStatus.NOT_UPDATED
    }
)

/**
 * Starts one foreground Wi-Fi scan and waits for the platform completion event.
 * The receiver is deliberately scoped to this request so it cannot leak into a
 * later refresh or keep the app alive after the timeout.
 */
class ScanRequestAwaiter(
    private val context: Context,
    private val wifiManager: WifiManager,
    private val registerReceiver: (BroadcastReceiver, IntentFilter) -> Unit = { scanReceiver, filter ->
        ContextCompat.registerReceiver(
            context,
            scanReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    },
    private val unregisterReceiver: (BroadcastReceiver) -> Unit = { context.unregisterReceiver(it) },
    private val createIntentFilter: (String) -> IntentFilter = ::IntentFilter
) {

    @Suppress("DEPRECATION")
    suspend fun requestAndAwait(timeoutMs: Long): ScanRequestOutcome = requestAndAwait(timeoutMs, null)

    /** The Android scan request itself cannot be cancelled; the receiver wait can. */
    @Suppress("DEPRECATION")
    suspend fun requestAndAwait(timeoutMs: Long, operationSession: OperationSession?): ScanRequestOutcome {
        var startScanReturned = false
        var broadcastArrived = false
        var resultsUpdated = false
        var receiverLease: ReceiverLease? = null

        try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    if (!continuation.isActive) return@suspendCancellableCoroutine
                    val scanReceiver = object : BroadcastReceiver() {
                        override fun onReceive(receiverContext: Context?, intent: Intent?) {
                            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                            broadcastArrived = true
                            resultsUpdated = intent.getBooleanExtra(
                                WifiManager.EXTRA_RESULTS_UPDATED,
                                false
                            )
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    registerReceiver(
                        scanReceiver,
                        createIntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    )
                    val lease = ReceiverLease { unregisterQuietly(scanReceiver) }
                    receiverLease = lease
                    operationSession?.resources?.register(lease)
                    continuation.invokeOnCancellation {
                        lease.close()
                    }
                    if (!continuation.isActive) return@suspendCancellableCoroutine

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
            val lease = receiverLease
            if (lease != null &&
                (operationSession == null || operationSession.resources.release(lease))
            ) {
                lease.close()
            }
        }

        return decideOutcome(startScanReturned, broadcastArrived, resultsUpdated)
    }

    private fun unregisterQuietly(receiver: BroadcastReceiver) {
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // The receiver may already have been removed by cancellation cleanup.
        }
    }

    private class ReceiverLease(private val unregister: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (closed.compareAndSet(false, true)) unregister()
        }
    }
}
