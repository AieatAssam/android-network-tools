package net.aieat.netswissknife.core.network.subnet

internal const val OCTET_MASK = 0xFFL

/** Octet [index] (0 is the most significant) of an address held in a Long. */
private fun octetAt(
    addr: Long,
    index: Int,
): Long {
    val shift = (OCTET_COUNT - 1 - index) * OCTET_BITS
    return (addr shr shift) and OCTET_MASK
}

internal fun formatIpv4(addr: Long): String = (0 until OCTET_COUNT).joinToString(".") { octetAt(addr, it).toString() }

private fun binaryOctet(
    addr: Long,
    index: Int,
): String = octetAt(addr, index).toString(2).padStart(OCTET_BITS, '0')

internal fun formatIpv4Binary(addr: Long): String = (0 until OCTET_COUNT).joinToString(".") { binaryOctet(addr, it) }
