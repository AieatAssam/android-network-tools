package net.aieat.netswissknife.core.network.httprobe

import java.net.URI
import java.net.URL

/** Pure redirect decision shared by the repository and focused policy tests. */
object RedirectPolicy {
    private val redirectStatuses = setOf(301, 302, 303, 307, 308)

    sealed interface Decision {
        data object NotRedirect : Decision
        data object MalformedLocation : Decision
        data object UnsupportedProtocol : Decision
        data object TooManyRedirects : Decision
        data class BlockedDowngrade(val destination: URL) : Decision
        data class Follow(
            val destination: URL,
            val method: HttpMethod,
            val body: String?,
            val changesOrigin: Boolean,
        ) : Decision
    }

    fun evaluate(
        source: URL,
        method: HttpMethod,
        body: String?,
        statusCode: Int,
        location: String?,
        redirectsAlreadyFollowed: Int,
        maxRedirects: Int,
    ): Decision {
        if (statusCode !in redirectStatuses || location.isNullOrBlank()) return Decision.NotRedirect
        if (redirectsAlreadyFollowed >= maxRedirects) return Decision.TooManyRedirects
        var destination = try {
            URI(source.toString()).resolve(location).toURL()
        } catch (_: Exception) {
            return Decision.MalformedLocation
        }
        if (destination.protocol !in setOf("http", "https")) return Decision.UnsupportedProtocol
        if (source.protocol.equals("https", true) && destination.protocol.equals("http", true)) {
            return Decision.BlockedDowngrade(destination)
        }
        val changesOrigin = !sameOrigin(source, destination)
        if (changesOrigin || destination.userInfo != source.userInfo) destination = withoutUserInfo(destination)
        val (nextMethod, nextBody) = redirectRequest(method, body, statusCode)
        return Decision.Follow(destination, nextMethod, nextBody, changesOrigin)
    }

    private fun sameOrigin(first: URL, second: URL): Boolean =
        first.protocol.equals(second.protocol, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) && effectivePort(first) == effectivePort(second)

    private fun effectivePort(url: URL): Int = when {
        url.port != -1 -> url.port
        url.protocol.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun withoutUserInfo(url: URL): URL = URL(url.protocol, url.host, url.port, url.file)

    private fun redirectRequest(method: HttpMethod, body: String?, statusCode: Int): Pair<HttpMethod, String?> = when (statusCode) {
        303 -> if (method == HttpMethod.HEAD) HttpMethod.HEAD to null else HttpMethod.GET to null
        301, 302 -> if (method == HttpMethod.POST) HttpMethod.GET to null else method to body
        else -> method to body
    }
}
