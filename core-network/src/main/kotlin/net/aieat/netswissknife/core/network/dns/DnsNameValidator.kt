package net.aieat.netswissknife.core.network.dns

import net.aieat.netswissknife.core.network.HostProblem
import net.aieat.netswissknife.core.network.HostValidator

/**
 * Pre-submit check for DNS query names. It applies the same rules as
 * [DnsRepositoryImpl] so that a name accepted here is never rejected by the
 * resolver path, but unlike a lookup it runs as a pure string check and can be
 * called on every keystroke.
 */
object DnsNameValidator {
    /**
     * Names the first reason [domain] cannot be queried as [recordType], or null
     * when the lookup may proceed.
     */
    fun problemOf(
        domain: String,
        recordType: DnsRecordType,
    ): HostProblem? {
        val text = domain.trim()
        if (text.isEmpty()) return HostProblem.BLANK
        return try {
            DnsRepositoryImpl.normalizeDomain(text, recordType)
            null
        } catch (_: IllegalArgumentException) {
            HostValidator.problemOf(text) ?: HostProblem.INVALID
        }
    }
}
