package net.aieat.netswissknife.app.ui.screens.whois

import java.time.Instant
import java.time.ZoneOffset

/**
 * Calendar date of an epoch timestamp in UTC. The parser stores registry dates as UTC midnight,
 * so reading them in the device zone would show the previous day in zones west of UTC.
 */
internal fun formatWhoisDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
