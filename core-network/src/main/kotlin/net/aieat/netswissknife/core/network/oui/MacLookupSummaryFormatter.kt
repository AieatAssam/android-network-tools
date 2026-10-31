package net.aieat.netswissknife.core.network.oui

/**
 * Every user-visible word of the MAC Lookup summary. The app fills these from strings.xml so the
 * formatter stays free of Android resources.
 */
data class MacLookupSummaryLabels(
    val title: String,
    val input: String,
    val address: String,
    val type: String,
    val locallyAdministered: String,
    val vendor: String,
    val block: String,
    val yes: String,
    val no: String,
    val unknownVendor: String,
    val locallyAdministeredVendor: String,
    val broadcastVendor: String,
    val rejected: String,
    val noEntries: String,
    val kindName: (MacAddressKind) -> String,
)

/** Plain-text report for Copy and Share. Rejected entries stay in the report with their reason. */
object MacLookupSummaryFormatter {
    fun format(
        entries: List<MacLookupEntry>,
        labels: MacLookupSummaryLabels,
    ): String =
        buildString {
            appendLine(labels.title)
            if (entries.isEmpty()) {
                appendLine(labels.noEntries)
                return@buildString
            }
            entries.forEach { entry ->
                appendLine()
                when (entry) {
                    is MacLookupEntry.Address -> appendAddress(entry, labels)
                    is MacLookupEntry.Rejected -> appendLine("${entry.input}: ${labels.rejected}")
                }
            }
        }

    private fun StringBuilder.appendAddress(
        entry: MacLookupEntry.Address,
        labels: MacLookupSummaryLabels,
    ) {
        val info = entry.info
        appendLine("${labels.input}: ${entry.input}")
        appendLine("${labels.address}: ${info.format(MacNotation.COLON, uppercase = true)}")
        appendLine("${labels.type}: ${labels.kindName(info.kind)}")
        appendLine("${labels.locallyAdministered}: ${if (info.locallyAdministered) labels.yes else labels.no}")
        appendLine("${labels.vendor}: ${vendorText(info.vendor, labels)}")
        val known = info.vendor as? VendorResult.Known
        if (known != null) appendLine("${labels.block}: ${known.blockSize.registryName}")
    }

    private fun vendorText(
        vendor: VendorResult,
        labels: MacLookupSummaryLabels,
    ): String =
        when (vendor) {
            is VendorResult.Known -> vendor.name
            VendorResult.Unknown -> labels.unknownVendor
            VendorResult.LocallyAdministered -> labels.locallyAdministeredVendor
            VendorResult.NotApplicable -> labels.broadcastVendor
        }
}
