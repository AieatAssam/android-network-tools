@file:Suppress("FunctionNaming", "ktlint:standard:function-naming") // Compose functions are PascalCase by convention.

package net.aieat.netswissknife.app.ui.screens.subnet

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.subnet.SubnetInfo

// ── Copy and share ────────────────────────────────────────────────────────────

@Composable
internal fun SubnetShareActions(info: SubnetInfo) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val text = buildSubnetShareText(info, shareLabels(), stringResource(info.scope.labelRes()))
    val copied = stringResource(R.string.subnet_copied)
    val subject = stringResource(R.string.subnet_share_subject, info.cidrNotation)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = hapticAction { copyText(scope, clipboard, context, copied, text) },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.subnet_copy_button))
        }
        FilledTonalButton(
            onClick = hapticAction { context.shareText(text, subject) },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.subnet_share_button))
        }
    }
}

private fun copyText(
    scope: CoroutineScope,
    clipboard: Clipboard,
    context: Context,
    copied: String,
    text: String,
) {
    scope.launch {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun shareLabels(): SubnetShareLabels =
    SubnetShareLabels(
        cidr = stringResource(R.string.subnet_notation_cidr),
        networkAddress = stringResource(R.string.subnet_network_address),
        broadcast = stringResource(R.string.subnet_broadcast),
        firstHost = stringResource(R.string.subnet_first_host),
        lastHost = stringResource(R.string.subnet_last_host),
        totalAddresses = stringResource(R.string.subnet_total_hosts),
        usableHosts = stringResource(R.string.subnet_usable_hosts),
        subnetMask = stringResource(R.string.subnet_notation_mask),
        wildcardMask = stringResource(R.string.subnet_notation_wildcard),
        scope = stringResource(R.string.subnet_scope),
    )
