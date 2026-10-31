package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.ErrorCode

internal const val IPV4_BITS = 32
internal const val IPV4_ALL_ONES = 0xFFFF_FFFFL

/** Prefix length at which no address is reserved (RFC 3021 point-to-point). */
internal const val PREFIX_POINT_TO_POINT = 31

/** Prefix length of a single host. */
internal const val PREFIX_HOST = 32

internal const val OCTET_COUNT = 4
internal const val OCTET_BITS = 8
private const val OCTET_MAX = 255
private const val OCTET_MAX_DIGITS = 3
private const val PREFIX_MAX_DIGITS = 2

/** Throws a [SubnetInputException] carrying a stable [code] and its format [args]. */
private fun failInput(
    code: ErrorCode,
    message: String,
    vararg args: Any?,
): Nothing = throw SubnetInputException(code = code, message = message, args = args.toList())

private fun isDecimalDigits(text: String): Boolean = text.isNotEmpty() && text.all { it in '0'..'9' }

/**
 * Parses dotted-quad IPv4 into an unsigned 32-bit value held in a Long.
 * Octets must be plain decimal digits; leading zeros are rejected because
 * "010" is ambiguous (octal in some tools, decimal in others).
 */
internal fun parseIpv4(text: String): Long {
    if (text.contains(':')) {
        failInput(ErrorCode.SUBNET_IPV6_UNSUPPORTED, "IPv6 is not supported: $text")
    }
    val parts = text.split('.')
    if (parts.size != OCTET_COUNT) failInput(ErrorCode.SUBNET_ADDRESS_INVALID, "Invalid IP address: $text", text)
    return parts.fold(0L) { acc, part -> (acc shl OCTET_BITS) or octetValue(part, text) }
}

private fun octetValue(
    part: String,
    text: String,
): Long {
    if (isDecimalDigits(part) && part.length > 1 && part[0] == '0') {
        failInput(ErrorCode.SUBNET_LEADING_ZERO, "Leading zero in octet $part of $text", part)
    }
    val value = part.takeIf { isDecimalDigits(it) && it.length <= OCTET_MAX_DIGITS }?.toInt()
    if (value == null || value > OCTET_MAX) {
        failInput(ErrorCode.SUBNET_ADDRESS_INVALID, "Invalid IP address: $text", text)
    }
    return value.toLong()
}

/** Converts a prefix length (0..32) to a 32-bit netmask. */
internal fun prefixToMask(prefix: Int): Long {
    require(prefix in 0..IPV4_BITS) { "Prefix length must be 0-32, got $prefix" }
    return if (prefix == 0) 0L else (IPV4_ALL_ONES shl (IPV4_BITS - prefix)) and IPV4_ALL_ONES
}

/** Returns the prefix length of a contiguous netmask; throws for anything else. */
internal fun maskToPrefix(mask: Long): Int {
    val inverted = mask.inv() and IPV4_ALL_ONES
    if ((inverted and (inverted + 1)) != 0L) {
        failInput(ErrorCode.SUBNET_MASK_NON_CONTIGUOUS, "Non-contiguous subnet mask")
    }
    return IPV4_BITS - java.lang.Long.bitCount(inverted)
}

/**
 * Splits the text after the first slash or the first space into a prefix length.
 * Accepts "/24", "/255.255.255.0" or " 255.255.255.0". Returns (address text, prefix).
 */
internal fun parseSubnetInput(input: String): Pair<String, Int> {
    val slashIndex = input.indexOf('/')
    val spaceIndex = input.indexOf(' ')
    return when {
        slashIndex != -1 -> {
            val ip = input.substring(0, slashIndex).trim()
            Pair(ip, parsePrefixText(input.substring(slashIndex + 1).trim()))
        }

        spaceIndex != -1 -> {
            val ip = input.substring(0, spaceIndex).trim()
            Pair(ip, maskToPrefix(parseIpv4(input.substring(spaceIndex + 1).trim())))
        }

        else -> {
            Pair(input, IPV4_BITS)
        }
    }
}

private fun parsePrefixText(suffix: String): Int {
    if (suffix.isEmpty()) failInput(ErrorCode.SUBNET_PREFIX_MISSING, "Missing prefix length after /")
    if (suffix.contains('.')) return maskToPrefix(parseIpv4(suffix))
    if (isDecimalDigits(suffix) && suffix.length > 1 && suffix[0] == '0') {
        failInput(ErrorCode.SUBNET_LEADING_ZERO, "Leading zero in prefix $suffix", suffix)
    }
    val value = suffix.takeIf { isDecimalDigits(it) && it.length <= PREFIX_MAX_DIGITS }?.toInt()
    if (value == null || value > IPV4_BITS) {
        failInput(ErrorCode.SUBNET_PREFIX_INVALID, "Invalid prefix: $suffix", suffix)
    }
    return value
}
