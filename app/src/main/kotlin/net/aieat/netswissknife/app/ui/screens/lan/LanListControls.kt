package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic

/** The filter once [tapped] is pressed: pressing the active filter again turns it off. */
internal fun lanFilterAfterTap(
    active: LanHostFilter,
    tapped: LanHostFilter,
): LanHostFilter = if (tapped == active) LanHostFilter.All else tapped

/** Uncertain probes in address order, as the device list is sorted, not in the order probes finished. */
internal fun lanDiagnosticsInIpOrder(probes: List<LanScanDiagnostic>) = probes.sortedBy { ipSortKey(it.ip) }
