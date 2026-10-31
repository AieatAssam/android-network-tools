@file:Suppress("FunctionNaming", "ktlint:standard:function-naming") // Compose functions are PascalCase by convention.

package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.i18n.ErrorTextMapper
import net.aieat.netswissknife.app.ui.i18n.asString
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetAddressRole
import net.aieat.netswissknife.core.network.subnet.SubnetMembership

// ── Is this address in the subnet? ────────────────────────────────────────────

@Composable
internal fun SubnetMembershipCard(
    input: String,
    membership: SubnetMembership?,
    error: NetworkResult.Error?,
    onInputChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.subnet_check_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ToolTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.subnet_check_label)) },
                placeholder = { Text(stringResource(R.string.subnet_check_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            AnimatedContent(
                targetState = Triple(membership, error, input.isBlank()),
                label = "membership-state",
            ) { (answer, failure, blank) ->
                MembershipAnswer(answer = answer, failure = failure, blank = blank)
            }
        }
    }
}

@Composable
private fun MembershipAnswer(
    answer: SubnetMembership?,
    failure: NetworkResult.Error?,
    blank: Boolean,
) {
    when {
        answer != null -> {
            SubnetMembershipVerdict(answer)
        }

        failure != null -> {
            Text(
                text = ErrorTextMapper.map(failure.info, failure.message).asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        else -> {
            Text(
                text = if (blank) stringResource(R.string.subnet_check_idle) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubnetMembershipVerdict(answer: SubnetMembership) {
    val verdict =
        if (answer.inSubnet) {
            stringResource(R.string.subnet_check_yes, answer.address, answer.cidrNotation)
        } else {
            stringResource(R.string.subnet_check_no, answer.address, answer.cidrNotation)
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = verdict,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (answer.inSubnet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = membershipReason(answer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun membershipReason(answer: SubnetMembership): String =
    when (answer.role) {
        SubnetAddressRole.USABLE_HOST -> stringResource(R.string.subnet_check_reason_usable)
        SubnetAddressRole.NETWORK_ADDRESS -> stringResource(R.string.subnet_check_reason_network)
        SubnetAddressRole.BROADCAST_ADDRESS -> stringResource(R.string.subnet_check_reason_broadcast)
        SubnetAddressRole.OUTSIDE -> outsideReason(answer)
    }

@Composable
private fun outsideReason(answer: SubnetMembership): String {
    val first = answer.networkAddress
    val last = answer.broadcastAddress
    return stringResource(R.string.subnet_check_reason_outside, first, last)
}
