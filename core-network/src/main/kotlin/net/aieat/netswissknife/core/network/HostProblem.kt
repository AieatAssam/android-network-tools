package net.aieat.netswissknife.core.network

/** Why a host or IP literal was rejected by [HostValidator.problemOf]. */
enum class HostProblem {
    BLANK,
    CONTAINS_WHITESPACE,
    IPV4_INVALID,
    IPV6_INVALID,
    EMPTY_LABEL,
    INVALID_LABEL,
    INVALID_CHARACTERS,
    TOO_LONG,
    INVALID,
}
