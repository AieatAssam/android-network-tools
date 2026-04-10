package net.aieat.netswissknife.core.network.httprobe

object HttpSecurityAnalyzer {

    fun analyze(
        responseHeaders: Map<String, List<String>>,
        isHttps: Boolean
    ): List<SecurityHeaderCheck> {
        val normalized = responseHeaders.mapKeys { it.key.lowercase() }
        return listOf(
            checkHsts(normalized, isHttps),
            checkContentSecurityPolicy(normalized),
            checkXFrameOptions(normalized),
            checkXContentTypeOptions(normalized),
            checkReferrerPolicy(normalized),
            checkPermissionsPolicy(normalized),
            checkServerHeader(normalized)
        )
    }

    private fun get(headers: Map<String, List<String>>, name: String): String? =
        headers[name.lowercase()]?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun checkHsts(headers: Map<String, List<String>>, isHttps: Boolean): SecurityHeaderCheck {
        val value = get(headers, "Strict-Transport-Security")
        return when {
            value != null -> SecurityHeaderCheck(
                headerName = "Strict-Transport-Security",
                value = value,
                rating = SecurityRating.PASS,
                description = "HSTS is enabled. Browsers will upgrade future connections to HTTPS."
            )
            !isHttps -> SecurityHeaderCheck(
                headerName = "Strict-Transport-Security",
                value = null,
                rating = SecurityRating.INFO,
                description = "HSTS only applies to HTTPS responses."
            )
            else -> SecurityHeaderCheck(
                headerName = "Strict-Transport-Security",
                value = null,
                rating = SecurityRating.FAIL,
                description = "HSTS not set — browsers may downgrade connections to HTTP."
            )
        }
    }

    private fun checkContentSecurityPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val value = get(headers, "Content-Security-Policy")
        return if (value != null) {
            SecurityHeaderCheck(
                headerName = "Content-Security-Policy",
                value = value,
                rating = SecurityRating.PASS,
                description = "CSP is set. Helps mitigate XSS and data injection attacks."
            )
        } else {
            SecurityHeaderCheck(
                headerName = "Content-Security-Policy",
                value = null,
                rating = SecurityRating.WARN,
                description = "No CSP header — the site may be vulnerable to XSS attacks."
            )
        }
    }

    private fun checkXFrameOptions(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val value = get(headers, "X-Frame-Options")
        return when {
            value != null && value.uppercase().let { it == "DENY" || it == "SAMEORIGIN" } ->
                SecurityHeaderCheck(
                    headerName = "X-Frame-Options",
                    value = value,
                    rating = SecurityRating.PASS,
                    description = "Clickjacking protection is enabled."
                )
            value != null ->
                SecurityHeaderCheck(
                    headerName = "X-Frame-Options",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "X-Frame-Options is present but the value '$value' may not provide full protection."
                )
            else ->
                SecurityHeaderCheck(
                    headerName = "X-Frame-Options",
                    value = null,
                    rating = SecurityRating.FAIL,
                    description = "No X-Frame-Options — page may be embeddable in iframes (clickjacking risk)."
                )
        }
    }

    private fun checkXContentTypeOptions(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val value = get(headers, "X-Content-Type-Options")
        return if (value?.lowercase() == "nosniff") {
            SecurityHeaderCheck(
                headerName = "X-Content-Type-Options",
                value = value,
                rating = SecurityRating.PASS,
                description = "MIME sniffing is disabled."
            )
        } else {
            SecurityHeaderCheck(
                headerName = "X-Content-Type-Options",
                value = value,
                rating = SecurityRating.FAIL,
                description = "No 'nosniff' directive — browsers may MIME-sniff responses."
            )
        }
    }

    private fun checkReferrerPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val value = get(headers, "Referrer-Policy")
        val strictValues = setOf(
            "no-referrer",
            "no-referrer-when-downgrade",
            "strict-origin",
            "strict-origin-when-cross-origin",
            "same-origin"
        )
        return when {
            value != null && value.lowercase() in strictValues ->
                SecurityHeaderCheck(
                    headerName = "Referrer-Policy",
                    value = value,
                    rating = SecurityRating.PASS,
                    description = "Referrer policy is set to a privacy-preserving value."
                )
            value != null ->
                SecurityHeaderCheck(
                    headerName = "Referrer-Policy",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "Referrer-Policy is set to '$value' which may leak URL data."
                )
            else ->
                SecurityHeaderCheck(
                    headerName = "Referrer-Policy",
                    value = null,
                    rating = SecurityRating.WARN,
                    description = "No Referrer-Policy — referrer data may be sent to third parties."
                )
        }
    }

    private fun checkPermissionsPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val value = get(headers, "Permissions-Policy")
        return if (value != null) {
            SecurityHeaderCheck(
                headerName = "Permissions-Policy",
                value = value,
                rating = SecurityRating.PASS,
                description = "Permissions Policy restricts browser feature access."
            )
        } else {
            SecurityHeaderCheck(
                headerName = "Permissions-Policy",
                value = null,
                rating = SecurityRating.WARN,
                description = "No Permissions-Policy — browser features like camera/mic are unrestricted."
            )
        }
    }

    private fun checkServerHeader(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val value = get(headers, "Server")
        val versionPattern = Regex("""\d+\.\d+""")
        return when {
            value == null ->
                SecurityHeaderCheck(
                    headerName = "Server",
                    value = null,
                    rating = SecurityRating.INFO,
                    description = "Server header is not present (good for security)."
                )
            versionPattern.containsMatchIn(value) ->
                SecurityHeaderCheck(
                    headerName = "Server",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "Server header reveals version information which may help attackers."
                )
            else ->
                SecurityHeaderCheck(
                    headerName = "Server",
                    value = value,
                    rating = SecurityRating.INFO,
                    description = "Server header is present but does not reveal version details."
                )
        }
    }
}
