package net.aieat.netswissknife.app.ui.screens.oui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.oui.MacAddressKind
import net.aieat.netswissknife.core.network.oui.MacLookupSummaryLabels
import net.aieat.netswissknife.core.network.oui.MacNotation
import net.aieat.netswissknife.core.network.oui.VendorResult

/** Every word of the Copy all and Share text, from strings.xml. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun rememberOuiSummaryLabels(): MacLookupSummaryLabels {
    val unicast = stringResource(R.string.oui_type_unicast)
    val multicast = stringResource(R.string.oui_type_multicast)
    val broadcast = stringResource(R.string.oui_type_broadcast)
    return MacLookupSummaryLabels(
        title = stringResource(R.string.oui_screen_title),
        input = stringResource(R.string.oui_summary_input),
        address = stringResource(R.string.oui_address_label),
        type = stringResource(R.string.oui_type_label),
        locallyAdministered = stringResource(R.string.oui_local_label),
        vendor = stringResource(R.string.oui_vendor_label),
        block = stringResource(R.string.oui_block_short),
        yes = stringResource(R.string.oui_yes),
        no = stringResource(R.string.oui_no),
        unknownVendor = stringResource(R.string.oui_vendor_unknown),
        locallyAdministeredVendor = stringResource(R.string.oui_vendor_locally_administered),
        broadcastVendor = stringResource(R.string.oui_vendor_broadcast),
        rejected = stringResource(R.string.oui_rejected),
        noEntries = stringResource(R.string.oui_no_entries),
        kindName = { kind ->
            when (kind) {
                MacAddressKind.UNICAST -> unicast
                MacAddressKind.MULTICAST -> multicast
                MacAddressKind.BROADCAST -> broadcast
            }
        },
    )
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun vendorText(vendor: VendorResult): String =
    when (vendor) {
        is VendorResult.Known -> vendor.name
        VendorResult.Unknown -> stringResource(R.string.oui_vendor_unknown)
        VendorResult.LocallyAdministered -> stringResource(R.string.oui_vendor_locally_administered)
        VendorResult.NotApplicable -> stringResource(R.string.oui_vendor_broadcast)
    }

internal fun kindRes(kind: MacAddressKind): Int =
    when (kind) {
        MacAddressKind.UNICAST -> R.string.oui_type_unicast
        MacAddressKind.MULTICAST -> R.string.oui_type_multicast
        MacAddressKind.BROADCAST -> R.string.oui_type_broadcast
    }

internal fun notationRes(notation: MacNotation): Int =
    when (notation) {
        MacNotation.COLON -> R.string.oui_notation_colon
        MacNotation.HYPHEN -> R.string.oui_notation_hyphen
        MacNotation.DOTTED -> R.string.oui_notation_dotted
        MacNotation.BARE -> R.string.oui_notation_bare
    }
