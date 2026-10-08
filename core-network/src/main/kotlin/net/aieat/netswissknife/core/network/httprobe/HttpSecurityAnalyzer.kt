package net.aieat.netswissknife.core.network.httprobe

object HttpSecurityAnalyzer {
    fun analyze(
        responseHeaders: Map<String, List<String>>,
        isHttps: Boolean,
    ): List<SecurityHeaderCheck> {
        val normalized =
            responseHeaders.entries
                .groupBy { it.key.lowercase() }
                .mapValues { (_, entries) -> entries.flatMap { it.value } }
        return listOf(
            checkHsts(normalized, isHttps),
            checkContentSecurityPolicy(normalized),
            checkXFrameOptions(normalized),
            checkXssProtection(normalized),
            checkXContentTypeOptions(normalized),
            checkReferrerPolicy(normalized),
            checkPermissionsPolicy(normalized),
            checkCrossOriginOpenerPolicy(normalized),
            checkCrossOriginEmbedderPolicy(normalized),
            checkServerHeader(normalized),
        ) + CookieAnalyzer.analyze(responseHeaders, isHttps)
    }

    private fun rawValues(
        headers: Map<String, List<String>>,
        name: String,
    ): List<String> = headers.entries.filter { it.key.equals(name, ignoreCase = true) }.flatMap { it.value }

    private fun values(
        headers: Map<String, List<String>>,
        name: String,
    ): List<String> = rawValues(headers, name).filter { it.isNotBlank() }

    private fun joined(values: List<String>): String? = values.takeIf { it.isNotEmpty() }?.joinToString("\n")

    private fun checkHsts(
        headers: Map<String, List<String>>,
        isHttps: Boolean,
    ): SecurityHeaderCheck {
        val rawHeaderValues = rawValues(headers, "Strict-Transport-Security")
        if (!isHttps && rawHeaderValues.isNotEmpty()) {
            return SecurityHeaderCheck(
                headerName = "Strict-Transport-Security",
                value = joined(rawHeaderValues.filter(String::isNotBlank)),
                rating = SecurityRating.INFO,
                description = "Browsers ignore HSTS received over HTTP.",
                descriptionKey = "httprobe_sec_hsts_ignored_http",
            )
        }
        val headerValues = rawHeaderValues.filter(String::isNotBlank)
        val value = joined(headerValues)
        val directives =
            headerValues
                .flatMap { it.split(';') }
                .map(String::trim)
                .filter { it.startsWith("max-age", ignoreCase = true) }
        val parsedMaxAge =
            if (rawHeaderValues.size == 1 && headerValues.size == 1 && directives.size == 1) {
                HSTS_MAX_AGE.matchEntire(directives.single())
            } else {
                null
            }
        val maxAge = parsedMaxAge?.let { (it.groups[1]?.value ?: it.groups[2]?.value)?.toLongOrNull() }
        return gradeHsts(value, maxAge, isHttps)
    }

    private fun gradeHsts(
        value: String?,
        maxAge: Long?,
        isHttps: Boolean,
    ): SecurityHeaderCheck =
        when {
            value != null && maxAge != null && maxAge >= HSTS_STRONG_MAX_AGE_SECONDS -> {
                SecurityHeaderCheck(
                    headerName = "Strict-Transport-Security",
                    value = value,
                    rating = SecurityRating.PASS,
                    description =
                        "HSTS is enabled with a long max-age. Browsers will upgrade future connections to HTTPS.",
                    descriptionKey = "httprobe_sec_hsts_pass_strong",
                )
            }

            value != null && maxAge != null -> {
                SecurityHeaderCheck(
                    headerName = "Strict-Transport-Security",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "HSTS max-age is shorter than one year.",
                    descriptionKey = "httprobe_sec_hsts_short",
                )
            }

            value != null -> {
                SecurityHeaderCheck(
                    headerName = "Strict-Transport-Security",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "HSTS does not contain a valid max-age directive.",
                    descriptionKey = "httprobe_sec_hsts_invalid",
                )
            }

            !isHttps -> {
                SecurityHeaderCheck(
                    headerName = "Strict-Transport-Security",
                    value = null,
                    rating = SecurityRating.INFO,
                    description = "HSTS only applies to HTTPS responses.",
                )
            }

            else -> {
                SecurityHeaderCheck(
                    headerName = "Strict-Transport-Security",
                    value = null,
                    rating = SecurityRating.FAIL,
                    description = "HSTS not set - browsers may downgrade connections to HTTP.",
                )
            }
        }

    private fun checkContentSecurityPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val rawEnforcedValues = rawValues(headers, "Content-Security-Policy")
        val enforcedValues = rawEnforcedValues.filter(String::isNotBlank)
        val reportOnly = joined(values(headers, "Content-Security-Policy-Report-Only"))
        val value = joined(enforcedValues)
        return if (value != null) {
            val hasMalformedDuplicate = rawEnforcedValues.size != enforcedValues.size
            val weakDirective = hasMalformedDuplicate || enforcedValues.any(::hasUnsafeEffectiveScriptSource)
            SecurityHeaderCheck(
                headerName = "Content-Security-Policy",
                value = value,
                rating = if (weakDirective) SecurityRating.WARN else SecurityRating.PASS,
                description =
                    if (hasMalformedDuplicate) {
                        "Conflicting or malformed CSP values were received."
                    } else if (weakDirective) {
                        "CSP permits unsafe inline or evaluated script content."
                    } else {
                        "CSP is set. Helps mitigate XSS and data injection attacks."
                    },
                descriptionKey =
                    when {
                        hasMalformedDuplicate -> "httprobe_sec_csp_ambiguous"
                        weakDirective -> "httprobe_sec_csp_unsafe_inline"
                        else -> null
                    },
            )
        } else if (reportOnly != null) {
            SecurityHeaderCheck(
                headerName = "Content-Security-Policy",
                value = reportOnly,
                rating = SecurityRating.INFO,
                description = "Only a report-only CSP is set; it does not enforce restrictions.",
                descriptionKey = "httprobe_sec_csp_report_only",
            )
        } else {
            SecurityHeaderCheck(
                headerName = "Content-Security-Policy",
                value = null,
                rating = SecurityRating.WARN,
                description = "No CSP header - the site may be vulnerable to XSS attacks.",
            )
        }
    }

    private fun checkXFrameOptions(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val xfoValues = rawValues(headers, "X-Frame-Options").map(String::trim)
        val value = joined(xfoValues)
        val enforcedCsp = rawValues(headers, "Content-Security-Policy")
        val frameAncestors = hasProtectiveFrameAncestors(enforcedCsp)
        return when {
            xfoValues.isEmpty() && frameAncestors -> {
                SecurityHeaderCheck(
                    headerName = "X-Frame-Options",
                    value = null,
                    rating = SecurityRating.PASS,
                    description = "CSP frame-ancestors provides clickjacking protection.",
                    descriptionKey = "httprobe_sec_xfo_via_csp",
                )
            }

            xfoValues.size == 1 &&
                xfoValues.single().let { it.equals("DENY", true) || it.equals("SAMEORIGIN", true) } -> {
                SecurityHeaderCheck(
                    headerName = "X-Frame-Options",
                    value = value,
                    rating = SecurityRating.PASS,
                    description = "Clickjacking protection is enabled.",
                )
            }

            xfoValues.isNotEmpty() -> {
                SecurityHeaderCheck(
                    headerName = "X-Frame-Options",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "X-Frame-Options is present but the value '$value' may not provide full protection.",
                )
            }

            else -> {
                SecurityHeaderCheck(
                    headerName = "X-Frame-Options",
                    value = null,
                    rating = SecurityRating.FAIL,
                    description = "No X-Frame-Options - page may be embeddable in iframes (clickjacking risk).",
                )
            }
        }
    }

    private fun checkXssProtection(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val value = joined(rawValues(headers, "X-XSS-Protection"))
        return SecurityHeaderCheck(
            headerName = "X-XSS-Protection",
            value = value,
            rating = SecurityRating.INFO,
            description =
                if (value == null) {
                    "X-XSS-Protection is not set; modern browsers rely on Content Security Policy."
                } else {
                    "X-XSS-Protection is deprecated; use Content Security Policy instead."
                },
            descriptionKey =
                if (value == null) "httprobe_sec_xss_protection_absent" else "httprobe_sec_xss_protection_deprecated",
        )
    }

    private fun checkXContentTypeOptions(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val headerValues = rawValues(headers, "X-Content-Type-Options")
        val value = joined(headerValues)
        return if (headerValues.size == 1 && headerValues.single().equals("nosniff", ignoreCase = true)) {
            SecurityHeaderCheck(
                headerName = "X-Content-Type-Options",
                value = value,
                rating = SecurityRating.PASS,
                description = "MIME sniffing is disabled.",
            )
        } else {
            SecurityHeaderCheck(
                headerName = "X-Content-Type-Options",
                value = value,
                rating = SecurityRating.FAIL,
                description = "No 'nosniff' directive - browsers may MIME-sniff responses.",
            )
        }
    }

    private fun checkReferrerPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val headerValues = rawValues(headers, "Referrer-Policy")
        val value = joined(headerValues)
        val strictValues =
            setOf(
                "no-referrer",
                "no-referrer-when-downgrade",
                "strict-origin",
                "strict-origin-when-cross-origin",
                "same-origin",
            )
        return when {
            headerValues.size == 1 && headerValues.single().lowercase() in strictValues -> {
                SecurityHeaderCheck(
                    headerName = "Referrer-Policy",
                    value = value,
                    rating = SecurityRating.PASS,
                    description = "Referrer policy is set to a privacy-preserving value.",
                )
            }

            value != null -> {
                SecurityHeaderCheck(
                    headerName = "Referrer-Policy",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "Referrer-Policy is set to '$value' which may leak URL data.",
                )
            }

            else -> {
                SecurityHeaderCheck(
                    headerName = "Referrer-Policy",
                    value = null,
                    rating = SecurityRating.WARN,
                    description = "No Referrer-Policy - referrer data may be sent to third parties.",
                )
            }
        }
    }

    private fun checkPermissionsPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val headerValues = rawValues(headers, "Permissions-Policy")
        val value = joined(headerValues)
        return if (headerValues.size == 1 && headerValues.single().isNotBlank()) {
            SecurityHeaderCheck(
                headerName = "Permissions-Policy",
                value = value,
                rating = SecurityRating.PASS,
                description = "Permissions Policy restricts browser feature access.",
            )
        } else {
            SecurityHeaderCheck(
                headerName = "Permissions-Policy",
                value = value,
                rating = SecurityRating.WARN,
                description =
                    if (value == null) {
                        "No Permissions-Policy - browser features like camera/mic are unrestricted."
                    } else {
                        "Conflicting Permissions-Policy values were received."
                    },
            )
        }
    }

    private fun checkCrossOriginOpenerPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val headerValues = rawValues(headers, "Cross-Origin-Opener-Policy")
        val value = joined(headerValues)
        return when {
            headerValues.size == 1 &&
                headerValues.single().lowercase() in setOf("same-origin", "same-origin-allow-popups") -> {
                SecurityHeaderCheck(
                    headerName = "Cross-Origin-Opener-Policy",
                    value = value,
                    rating = SecurityRating.PASS,
                    description = "COOP isolates the browsing context, mitigating cross-origin attacks like Spectre.",
                )
            }

            value != null -> {
                SecurityHeaderCheck(
                    headerName = "Cross-Origin-Opener-Policy",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "COOP is set to '$value', which does not isolate the browsing context.",
                )
            }

            else -> {
                SecurityHeaderCheck(
                    headerName = "Cross-Origin-Opener-Policy",
                    value = null,
                    rating = SecurityRating.WARN,
                    description = "No Cross-Origin-Opener-Policy - the page can be accessed by cross-origin windows.",
                )
            }
        }
    }

    private fun checkCrossOriginEmbedderPolicy(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val headerValues = rawValues(headers, "Cross-Origin-Embedder-Policy")
        val value = joined(headerValues)
        return if (
            headerValues.size == 1 &&
            headerValues.single().lowercase() in setOf("require-corp", "credentialless")
        ) {
            SecurityHeaderCheck(
                headerName = "Cross-Origin-Embedder-Policy",
                value = value,
                rating = SecurityRating.PASS,
                description = "COEP prevents the document from loading cross-origin resources that don't grant it.",
            )
        } else {
            SecurityHeaderCheck(
                headerName = "Cross-Origin-Embedder-Policy",
                value = value,
                rating = SecurityRating.INFO,
                description =
                    "No Cross-Origin-Embedder-Policy - only required for cross-origin isolation features " +
                        "(e.g. SharedArrayBuffer).",
            )
        }
    }

    private fun checkServerHeader(headers: Map<String, List<String>>): SecurityHeaderCheck {
        val values = rawValues(headers, "Server")
        val value = joined(values)
        val versionPattern = Regex("""\d+\.\d+""")
        return when {
            value == null -> {
                SecurityHeaderCheck(
                    headerName = "Server",
                    value = null,
                    rating = SecurityRating.INFO,
                    description = "Server header is not present (good for security).",
                )
            }

            values.any(versionPattern::containsMatchIn) -> {
                SecurityHeaderCheck(
                    headerName = "Server",
                    value = value,
                    rating = SecurityRating.WARN,
                    description = "Server header reveals version information which may help attackers.",
                )
            }

            else -> {
                SecurityHeaderCheck(
                    headerName = "Server",
                    value = value,
                    rating = SecurityRating.INFO,
                    description = "Server header is present but does not reveal version details.",
                )
            }
        }
    }

    private fun hasUnsafeEffectiveScriptSource(policy: String): Boolean {
        val directives = parseCspDirectives(policy)

        fun effective(vararg names: String): List<String> {
            val directive = names.firstNotNullOfOrNull { name -> directives[name]?.firstOrNull() }
            return directive.orEmpty()
        }
        val effectiveInlineSources =
            listOf(
                effective("script-src-elem", "script-src", "default-src"),
                effective("script-src-attr", "script-src", "default-src"),
            )
        val unsafeInline =
            effectiveInlineSources.any { sources ->
                val normalized = sources.map { it.lowercase() }
                "'unsafe-inline'" in normalized &&
                    "'strict-dynamic'" !in normalized &&
                    normalized.none(::isNonceOrHashSource)
            }
        val effectiveEvalSources =
            effective("script-src", "default-src")
                .map { it.lowercase() }
        val unsafeEval = "'unsafe-eval'" in effectiveEvalSources
        return unsafeInline || unsafeEval
    }

    private fun isNonceOrHashSource(source: String): Boolean {
        val matchesNonce = NONCE_SOURCE.matches(source)
        return matchesNonce || HASH_SOURCE.matches(source)
    }

    private fun hasProtectiveFrameAncestors(policies: List<String>): Boolean {
        if (policies.isEmpty() || policies.any(String::isBlank)) return false
        val frameDirectives =
            policies.flatMap { policy ->
                parseCspDirectives(policy)["frame-ancestors"].orEmpty()
            }
        return frameDirectives.isNotEmpty() &&
            frameDirectives.all { sources ->
                sources.isNotEmpty() &&
                    sources.all { source ->
                        source.lowercase() in setOf("'self'", "'none'") ||
                            (FRAME_ANCESTOR_HOST_SOURCE.matches(source) && '*' !in source)
                    }
            }
    }

    private fun parseCspDirectives(policy: String): Map<String, List<List<String>>> {
        val result = linkedMapOf<String, MutableList<List<String>>>()
        for (rawDirective in policy.split(';')) {
            val tokens = rawDirective.trim().split(WHITESPACE).filter(String::isNotEmpty)
            val name = tokens.firstOrNull()?.lowercase() ?: continue
            if (name !in result) result[name] = mutableListOf(tokens.drop(1))
        }
        return result
    }

    private const val HSTS_STRONG_MAX_AGE_SECONDS = 31_536_000L
    private val HSTS_MAX_AGE = Regex("max-age\\s*=\\s*(?:\"([0-9]+)\"|([0-9]+))", RegexOption.IGNORE_CASE)
    private val FRAME_ANCESTOR_HOST_SOURCE =
        Regex(
            "(?:https?://)?[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" +
                "(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*(?::[0-9]{1,5})?(?:/[^\\s]*)?",
            RegexOption.IGNORE_CASE,
        )
    private val WHITESPACE = Regex("\\s+")
    private val NONCE_SOURCE = Regex("'nonce-[a-z0-9+/_-]+={0,2}'", RegexOption.IGNORE_CASE)
    private val HASH_SOURCE =
        Regex(
            "'(?:sha256-[a-z0-9+/_-]{43}=?|sha384-[a-z0-9+/_-]{64}|sha512-[a-z0-9+/_-]{86}={0,2})'",
            RegexOption.IGNORE_CASE,
        )
}
