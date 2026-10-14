package net.aieat.netswissknife.app.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanRequestAwaiterTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun receiverReportsUpdatedResultsExtra() = runBlocking {
        val outcome = requestWithBroadcast(updatedExtra = true)

        assertEquals(WifiScanRefreshStatus.UPDATED, outcome.status)
    }

    @Test
    fun receiverReportsFalseUpdatedResultsExtra() = runBlocking {
        val outcome = requestWithBroadcast(updatedExtra = false)

        assertEquals(WifiScanRefreshStatus.NOT_UPDATED, outcome.status)
    }

    @Test
    fun receiverTreatsMissingUpdatedResultsExtraAsNotUpdated() = runBlocking {
        val outcome = requestWithBroadcast(updatedExtra = null)

        assertEquals(WifiScanRefreshStatus.NOT_UPDATED, outcome.status)
    }

    private suspend fun requestWithBroadcast(updatedExtra: Boolean?): ScanRequestOutcome {
        var receiver: BroadcastReceiver? = null
        val wifiManager = mockk<WifiManager>()
        every { wifiManager.startScan() } answers {
            val intent = Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            updatedExtra?.let { intent.putExtra(WifiManager.EXTRA_RESULTS_UPDATED, it) }
            receiver?.onReceive(context, intent)
            true
        }
        return ScanRequestAwaiter(
            context = context,
            wifiManager = wifiManager,
            registerReceiver = { registeredReceiver, _: IntentFilter -> receiver = registeredReceiver },
            unregisterReceiver = {}
        ).requestAndAwait(timeoutMs = 2_000L)
    }
}
