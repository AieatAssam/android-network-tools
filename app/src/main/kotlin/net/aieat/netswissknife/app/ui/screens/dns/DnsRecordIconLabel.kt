package net.aieat.netswissknife.app.ui.screens.dns

import net.aieat.netswissknife.core.network.dns.DnsRecord

private const val ICON_LABEL_LENGTH = 3

/**
 * Short label drawn inside a record's type icon. Uses the type the server returned,
 * so pseudo-records such as EDNS OPT are not shown under the TXT fallback.
 */
fun dnsRecordIconLabel(record: DnsRecord): String = record.rrTypeName.take(ICON_LABEL_LENGTH)
