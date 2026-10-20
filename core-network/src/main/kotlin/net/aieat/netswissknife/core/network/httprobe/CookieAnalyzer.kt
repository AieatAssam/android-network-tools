package net.aieat.netswissknife.core.network.httprobe

/** Grades each Set-Cookie response field without retaining or displaying its secret value. */
object CookieAnalyzer {
    fun analyze(
        responseHeaders: Map<String, List<String>>,
        isHttps: Boolean,
    ): List<SecurityHeaderCheck> {
        val cookies =
            responseHeaders.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value }
        return cookies.mapIndexed { index, raw -> analyzeCookie(raw, isHttps, index + 1) }
    }

    private fun analyzeCookie(
        raw: String,
        isHttps: Boolean,
        index: Int,
    ): SecurityHeaderCheck {
        val parts = splitAttributes(raw)
        val cookiePair = parts.firstOrNull()?.trim().orEmpty()
        val name = cookiePair.substringBefore('=', missingDelimiterValue = "").trim().ifBlank { "#$index" }
        val isWellFormed = cookiePair.contains('=') && COOKIE_NAME.matches(name)
        val attributes =
            parts
                .drop(1)
                .mapNotNull { part ->
                    val key = part.substringBefore('=').trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    key.lowercase() to part.substringAfter('=', "").trim().trim('"')
                }.toMap()
        val secure = attributes.containsKey("secure") && attributes["secure"].isNullOrEmpty()
        val httpOnly = attributes.containsKey("httponly") && attributes["httponly"].isNullOrEmpty()
        val sameSite = attributes["samesite"]?.lowercase() in setOf("strict", "lax", "none")
        val sameSiteNoneNeedsSecure = attributes["samesite"]?.equals("none", ignoreCase = true) == true && !secure
        val secureFromInsecureResponse = !isHttps && secure
        val missingSecure = isHttps && !secure
        val rating =
            when {
                !isWellFormed -> SecurityRating.WARN
                missingSecure || sameSiteNoneNeedsSecure || !httpOnly -> SecurityRating.WARN
                secureFromInsecureResponse -> SecurityRating.WARN
                !sameSite -> SecurityRating.WARN
                !secure -> SecurityRating.INFO
                else -> SecurityRating.PASS
            }
        val key =
            when {
                !isWellFormed -> "httprobe_sec_cookie_malformed"
                missingSecure && !httpOnly -> "httprobe_sec_cookie_missing_secure_httponly"
                sameSiteNoneNeedsSecure -> "httprobe_sec_cookie_samesite_none_requires_secure"
                missingSecure -> "httprobe_sec_cookie_missing_secure"
                secureFromInsecureResponse -> "httprobe_sec_cookie_secure_from_http"
                !httpOnly -> "httprobe_sec_cookie_missing_httponly"
                !sameSite -> "httprobe_sec_cookie_missing_samesite"
                !secure -> "httprobe_sec_cookie_secure_not_applicable"
                else -> "httprobe_sec_cookie_flags_complete"
            }
        val description =
            when (key) {
                "httprobe_sec_cookie_malformed" -> "Cookie does not contain a valid name and value pair."
                "httprobe_sec_cookie_missing_secure_httponly" -> "Cookie is missing Secure and HttpOnly flags."
                "httprobe_sec_cookie_samesite_none_requires_secure" -> "SameSite=None cookies require Secure and are rejected by browsers without it."
                "httprobe_sec_cookie_secure_from_http" -> "Browsers reject Secure cookies received over an insecure HTTP response."
                "httprobe_sec_cookie_missing_secure" -> "Cookie is missing the Secure flag on an HTTPS response."
                "httprobe_sec_cookie_missing_httponly" -> "Cookie is missing the HttpOnly flag."
                "httprobe_sec_cookie_missing_samesite" -> "Cookie is missing a valid SameSite attribute."
                "httprobe_sec_cookie_secure_not_applicable" -> "Cookie omits Secure on a non-HTTPS response."
                else -> "Cookie has Secure, HttpOnly, and a valid SameSite attribute."
            }
        return SecurityHeaderCheck(
            headerName = "Set-Cookie: $name",
            value = null,
            rating = rating,
            description = description,
            descriptionKey = key,
        )
    }

    private fun splitAttributes(raw: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var escaped = false
        for (char in raw) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                char == '\\' && quoted -> {
                    current.append(char)
                    escaped = true
                }

                char == '"' -> {
                    quoted = !quoted
                    current.append(char)
                }

                char == ';' && !quoted -> {
                    parts += current.toString()
                    current.clear()
                }

                else -> {
                    current.append(char)
                }
            }
        }
        parts += current.toString()
        return parts
    }

    private val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
}
