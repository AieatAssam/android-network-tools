package net.aieat.netswissknife.app.ui.i18n

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

/** Maps platform-neutral core error codes to app-owned, locale-aware resources. */
object ErrorTextMapper {
    fun map(info: ErrorInfo?, fallback: String): UiText {
        if (info == null) return UiText.Plain(fallback)
        // String-based ValidationError constructors intentionally use UNKNOWN. Keep their
        // existing diagnostic wording visible until each producer has a specific code.
        if (info.code == ErrorCode.UNKNOWN) {
            return UiText.Plain(info.developerMessage ?: fallback)
        }
        // Some legacy producers do not yet supply the full argument contract. Do not ask
        // Android's formatter to render an incomplete resource; preserve their old copy.
        if (!info.args.matches(formatArgumentTypes(info.code))) {
            return UiText.Plain(info.developerMessage ?: fallback)
        }
        return UiText.Res(
            id = info.code.stringResourceId(),
            args = info.args,
            developerFallback = fallback,
        )
    }

    private fun ErrorCode.stringResourceId(): Int = when (this) {
        ErrorCode.UNKNOWN -> R.string.err_unknown
        ErrorCode.HOST_BLANK -> R.string.err_host_blank
        ErrorCode.HOST_INVALID -> R.string.err_host_invalid
        ErrorCode.COUNT_OUT_OF_RANGE -> R.string.err_count_out_of_range
        ErrorCode.CONCURRENCY_OUT_OF_RANGE -> R.string.err_concurrency_out_of_range
        ErrorCode.QUERY_BLANK -> R.string.err_query_blank
        ErrorCode.DOMAIN_TOO_LONG -> R.string.err_domain_too_long
        ErrorCode.CUSTOM_DNS_BLANK -> R.string.err_custom_dns_blank
        ErrorCode.CUSTOM_DNS_INVALID -> R.string.err_custom_dns_invalid
        ErrorCode.URL_BLANK -> R.string.err_url_blank
        ErrorCode.URL_INVALID -> R.string.err_url_invalid
        ErrorCode.URL_MALFORMED -> R.string.err_url_malformed
        ErrorCode.URL_SCHEME_UNSUPPORTED -> R.string.err_url_scheme_unsupported
        ErrorCode.HTTPS_DOWNGRADE_BLOCKED -> R.string.err_https_downgrade_blocked
        ErrorCode.TIMEOUT_OUT_OF_RANGE -> R.string.err_timeout_out_of_range
        ErrorCode.RESPONSE_SIZE_OUT_OF_RANGE -> R.string.err_response_size_out_of_range
        ErrorCode.HTTP_RESPONSE_HEADERS_TOO_LARGE -> R.string.err_http_response_headers_too_large
        ErrorCode.PORT_OUT_OF_RANGE -> R.string.err_port_out_of_range
        ErrorCode.PORT_RANGE_INVERTED -> R.string.err_port_range_inverted
        ErrorCode.PORT_RANGE_TOO_LARGE -> R.string.err_port_range_too_large
        ErrorCode.PORT_SELECTION_EMPTY -> R.string.err_port_selection_empty
        ErrorCode.MAX_HOPS_OUT_OF_RANGE -> R.string.err_max_hops_out_of_range
        ErrorCode.PROBES_OUT_OF_RANGE -> R.string.err_probes_out_of_range
        ErrorCode.PACKET_SIZE_OUT_OF_RANGE -> R.string.err_packet_size_out_of_range
        ErrorCode.PAYLOAD_OUT_OF_RANGE -> R.string.err_payload_out_of_range
        ErrorCode.TTL_OUT_OF_RANGE -> R.string.err_ttl_out_of_range
        ErrorCode.INTERVAL_OUT_OF_RANGE -> R.string.err_interval_out_of_range
        ErrorCode.RETRIES_OUT_OF_RANGE -> R.string.err_retries_out_of_range
        ErrorCode.OPERATION_DEADLINE_EXCEEDED -> R.string.err_operation_deadline_exceeded
        ErrorCode.SNMP_COMMUNITY_BLANK -> R.string.err_snmp_community_blank
        ErrorCode.SNMP_V3_USER_BLANK -> R.string.err_snmp_v3_user_blank
        ErrorCode.SNMP_V3_PRIV_WITHOUT_AUTH -> R.string.err_snmp_v3_priv_without_auth
        ErrorCode.SNMP_V3_AUTH_PASSWORD_BLANK -> R.string.err_snmp_v3_auth_password_blank
        ErrorCode.SNMP_V3_PRIV_PASSWORD_BLANK -> R.string.err_snmp_v3_priv_password_blank
        ErrorCode.MAC_BLANK -> R.string.err_mac_blank
        ErrorCode.BROADCAST_BLANK -> R.string.err_broadcast_blank
        ErrorCode.DNS_NO_SYSTEM_RESOLVER -> R.string.err_dns_no_system_resolver
        ErrorCode.DNS_INVALID_NAME -> R.string.err_dns_invalid_name
        ErrorCode.DNS_LOOKUP_FAILED -> R.string.err_dns_lookup_failed
        ErrorCode.DNS_NO_RESULT -> R.string.err_dns_no_result
        ErrorCode.NETWORK_TIMEOUT -> R.string.err_network_timeout
        ErrorCode.NETWORK_REQUEST_FAILED -> R.string.err_network_request_failed
        ErrorCode.WHOIS_INVALID_QUERY -> R.string.err_whois_invalid_query
        ErrorCode.WHOIS_IANA_FAILED -> R.string.err_whois_iana_failed
        ErrorCode.WHOIS_RESPONSE_TOO_LARGE -> R.string.err_whois_response_too_large
        ErrorCode.WHOIS_REFERRAL_REFUSED -> R.string.err_whois_referral_refused
        ErrorCode.WHOIS_UNAVAILABLE -> R.string.err_whois_unavailable
        ErrorCode.WHOIS_NO_RESULT -> R.string.err_whois_no_result
        ErrorCode.WHOIS_LOOKUP_FAILED -> R.string.err_whois_lookup_failed
        ErrorCode.HTTP_REDIRECT_INVALID -> R.string.err_http_redirect_invalid
        ErrorCode.HTTP_REDIRECT_MALFORMED -> R.string.err_http_redirect_malformed
        ErrorCode.HTTP_REDIRECT_UNSUPPORTED -> R.string.err_http_redirect_unsupported
        ErrorCode.HTTP_REDIRECT_UNSUPPORTED_SCHEME -> R.string.err_http_redirect_unsupported_scheme
        ErrorCode.HTTP_INSECURE_DOWNGRADE -> R.string.err_http_insecure_downgrade
        ErrorCode.HTTP_REDIRECT_LIMIT -> R.string.err_http_redirect_limit
        ErrorCode.HTTP_REDIRECT_APPROVAL_REQUIRED -> R.string.err_http_redirect_approval_required
        ErrorCode.HTTP_REDIRECT_REPLAY_REJECTED -> R.string.err_http_redirect_replay_rejected
        ErrorCode.HTTP_TOO_MANY_REDIRECTS -> R.string.err_http_too_many_redirects
        ErrorCode.HTTP_NETWORK_ERROR -> R.string.err_http_network_error
        ErrorCode.TLS_INSPECTION_FAILED -> R.string.err_tls_inspection_failed
        ErrorCode.TLS_HANDSHAKE_FAILED -> R.string.err_tls_handshake_failed
        ErrorCode.TLS_PIN_INVALID -> R.string.err_tls_pin_invalid
        ErrorCode.MAC_INVALID -> R.string.err_mac_invalid
        ErrorCode.LOCAL_NETWORK_PERMISSION_DENIED -> R.string.err_local_network_permission_denied
        ErrorCode.WOL_SEND_FAILED -> R.string.err_wol_send_failed
        ErrorCode.SUBNET_BLANK -> R.string.err_subnet_blank
        ErrorCode.SUBNET_INVALID -> R.string.err_subnet_invalid
        ErrorCode.SUBNET_RANGE_INVALID -> R.string.err_subnet_range_invalid
        ErrorCode.NO_RESPONSE -> R.string.err_no_response
        ErrorCode.NETWORK_IO -> R.string.err_network_io
    }
}

private fun List<Any?>.matches(expectedTypes: List<FormatArgumentType>): Boolean =
    size == expectedTypes.size && zip(expectedTypes).all { (argument, expectedType) ->
        when (expectedType) {
            FormatArgumentType.STRING -> argument is String
            FormatArgumentType.INTEGER -> argument is Byte ||
                argument is Short ||
                argument is Int ||
                argument is Long ||
                argument is java.math.BigInteger
        }
    }

internal enum class FormatArgumentType {
    STRING,
    INTEGER,
}

/** Resource format argument contract audited against user-facing core ErrorInfo construction. */
internal fun formatArgumentTypes(code: ErrorCode): List<FormatArgumentType> = when (code) {
    ErrorCode.HOST_INVALID -> listOf(FormatArgumentType.STRING)
    ErrorCode.DOMAIN_TOO_LONG -> listOf(FormatArgumentType.INTEGER)

    ErrorCode.COUNT_OUT_OF_RANGE,
    ErrorCode.CONCURRENCY_OUT_OF_RANGE,
    ErrorCode.TIMEOUT_OUT_OF_RANGE,
    ErrorCode.RESPONSE_SIZE_OUT_OF_RANGE,
    ErrorCode.PORT_RANGE_INVERTED,
    ErrorCode.PORT_RANGE_TOO_LARGE,
    ErrorCode.MAX_HOPS_OUT_OF_RANGE,
    ErrorCode.PROBES_OUT_OF_RANGE,
    ErrorCode.PAYLOAD_OUT_OF_RANGE,
    ErrorCode.TTL_OUT_OF_RANGE,
    ErrorCode.INTERVAL_OUT_OF_RANGE,
    ErrorCode.RETRIES_OUT_OF_RANGE -> List(2) { FormatArgumentType.INTEGER }

    ErrorCode.PORT_OUT_OF_RANGE,
    ErrorCode.PACKET_SIZE_OUT_OF_RANGE -> List(3) { FormatArgumentType.INTEGER }

    else -> emptyList()
}

/** Number of format arguments declared by [formatArgumentTypes]. */
internal fun expectedArgumentCount(code: ErrorCode): Int = formatArgumentTypes(code).size
