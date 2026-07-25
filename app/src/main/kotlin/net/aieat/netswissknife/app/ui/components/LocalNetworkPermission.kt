package net.aieat.netswissknife.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android 16 (API 36) gates LAN sockets, mDNS/NSD, and broadcast traffic behind
 * `NEARBY_WIFI_DEVICES` under Local Network Protections (currently opt-in via
 * `adb shell am compat enable RESTRICT_LOCAL_NETWORK`, enforcement lands in a
 * future release; confirmed against the android-36 SDK, no separate permission
 * constant exists). Tools that talk to local-network addresses call this once
 * on entry so the permission is already granted before enforcement ships;
 * denial isn't fatal today; the underlying socket call will surface as the
 * tool's existing error state.
 *
 * Gated on API 36+, not 33+ (`NEARBY_WIFI_DEVICES` also exists since 33 for
 * Wi-Fi scanning, but LNP itself only applies on 36+) so 13-15 devices don't
 * see an irrelevant permission prompt.
 */
@Composable
fun rememberLocalNetworkPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op: absence surfaces via the tool's normal error path */ }

    return remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }
}
