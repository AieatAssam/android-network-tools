package net.aieat.netswissknife.app.ui.components

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.app.platform.LiteralDestinationClassifier
import net.aieat.netswissknife.app.platform.LocalNetworkPermissionPolicy
import net.aieat.netswissknife.app.platform.OperationAvailability
import net.aieat.netswissknife.core.network.operation.OperationRequirement

/**
 * Requests the permission required by the OS release: Android 16 uses the
 * temporary `NEARBY_WIFI_DEVICES` path, Android 17+ uses `ACCESS_LOCAL_NETWORK`,
 * and earlier releases keep their implicit access without showing a prompt.
 */
@Composable
fun rememberLocalNetworkPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op: absence surfaces via the tool's normal error path */ }

    return remember(context) {
        {
            val permission = LocalNetworkPermissionPolicy.permissionToRequest(Build.VERSION.SDK_INT)
            val granted = permission?.let {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            } ?: false
            if (LocalNetworkPermissionPolicy.shouldRequestPermission(Build.VERSION.SDK_INT, true, granted)) {
                checkNotNull(permission)
                launcher.launch(permission)
            }
        }
    }
}

/**
 * Starts public/system-resolver destinations immediately and gates local targets on the
 * OS-appropriate runtime permission. The start callback runs after either grant or denial;
 * on API 37+ the ViewModel then reports the explicit denial from its own permission seam.
 */
@Composable
fun rememberLocalNetworkPermissionRequester(onStart: () -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val pendingStart = remember { mutableStateOf<(() -> Unit)?>(null) }
    val latestOnStart = rememberUpdatedState(onStart)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        val start = pendingStart.value
        pendingStart.value = null
        start?.invoke()
    }

    return remember(context, launcher) {
        { destination ->
            val normalizedHost = HostValidator.normalize(destination) ?: destination.trim()
            val target = LiteralDestinationClassifier.target(normalizedHost)
            val requirement = OperationAvailability.requirementFor(target)
            val permission = LocalNetworkPermissionPolicy.permissionToRequest(Build.VERSION.SDK_INT)
            val granted = permission?.let {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            } ?: false
            val shouldRequest = LocalNetworkPermissionPolicy.shouldRequestPermission(
                apiLevel = Build.VERSION.SDK_INT,
                isLocalTarget = requirement == OperationRequirement.LOCAL_NETWORK,
                permissionGranted = granted,
            )
            if (!shouldRequest) {
                latestOnStart.value()
            } else {
                pendingStart.value = { latestOnStart.value() }
                launcher.launch(checkNotNull(permission))
            }
        }
    }
}
