package net.aieat.netswissknife.core.network.netinfo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

const val PUBLIC_IPV4_ENDPOINT = "https://api4.ipify.org"
const val PUBLIC_IPV6_ENDPOINT = "https://api6.ipify.org"

private const val MAX_RESPONSE_BYTES = 128
private const val CONNECT_TIMEOUT_SECONDS = 8L
private const val READ_TIMEOUT_SECONDS = 8L
private const val CALL_TIMEOUT_SECONDS = 12L

/**
 * Reads the public address from api4/api6.ipify.org over HTTPS. Each family uses an endpoint
 * that only answers on that family, so a missing IPv6 route fails only the IPv6 lookup.
 * Redirects are not followed; a response that is not a bare address is a failure.
 */
class OkHttpPublicIpRepository(
    private val client: OkHttpClient = defaultClient(),
    private val ipv4Url: HttpUrl = PUBLIC_IPV4_ENDPOINT.toHttpUrl(),
    private val ipv6Url: HttpUrl = PUBLIC_IPV6_ENDPOINT.toHttpUrl(),
) : PublicIpRepository {
    override suspend fun fetch(family: IpFamily): NetworkResult<String> {
        val url = if (family == IpFamily.IPV4) ipv4Url else ipv6Url
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "text/plain")
                .get()
                .build()
        return withContext(Dispatchers.IO) {
            val call = client.newCall(request)
            // Cancelling the coroutine must also abort the blocking socket read.
            coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }
            try {
                call.execute().use { interpret(it, family) }
            } catch (timeout: SocketTimeoutException) {
                NetworkResult.error(
                    code = ErrorCode.NETWORK_TIMEOUT,
                    developerMessage = "Public IP request timed out",
                    cause = timeout,
                )
            } catch (failure: IOException) {
                NetworkResult.error(
                    code = ErrorCode.NETWORK_REQUEST_FAILED,
                    developerMessage = "Public IP request failed: ${failure.message}",
                    cause = failure,
                )
            }
        }
    }

    private fun interpret(
        response: Response,
        family: IpFamily,
    ): NetworkResult<String> {
        if (!response.isSuccessful) return failure("Public IP service returned HTTP ${response.code}")
        val body = readBounded(response.body.byteStream())
        val address = body?.let { PublicIpParser.parse(it, family) }
        return address?.let { NetworkResult.Success(it) }
            ?: failure("Public IP service did not return a valid address")
    }

    /** Reads at most [MAX_RESPONSE_BYTES]; a longer body is not an address and is rejected. */
    private fun readBounded(stream: InputStream): String? =
        stream.use { input ->
            val buffer = ByteArray(MAX_RESPONSE_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            if (total > MAX_RESPONSE_BYTES) null else String(buffer, 0, total, Charsets.UTF_8)
        }

    private fun failure(message: String): NetworkResult.Error =
        NetworkResult.error(
            code = ErrorCode.NETWORK_REQUEST_FAILED,
            developerMessage = message,
        )

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }
}
