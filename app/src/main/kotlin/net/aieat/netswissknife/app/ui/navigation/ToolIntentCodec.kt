package net.aieat.netswissknife.app.ui.navigation

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Versioned URL-safe representation of [ToolIntent]. The `ti1.` prefix is
 * deliberately outside the Base64 payload so future versions can coexist.
 * No Android Uri, Bundle, or resource identifier is used here.
 */
object ToolIntentCodec {
    private const val PREFIX = "ti1."

    fun encode(intent: ToolIntent): String {
        val fields = when (val destination = intent.destination) {
            is ToolDestination.HostTarget -> listOf(
                "host", destination.tool.wireName, destination.host.value,
                destination.port?.value?.toString().orEmpty(), intent.source?.wireName.orEmpty(),
            )
            is ToolDestination.WakeOnLan -> listOf(
                "wol", destination.mac.value, intent.source?.wireName.orEmpty(),
            )
            is ToolDestination.Subnet -> listOf(
                "subnet", destination.subnet.value, intent.source?.wireName.orEmpty(),
            )
        }
        val payload = fields.joinToString("|").toByteArray(StandardCharsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    /** Returns null for malformed, unsupported, or invalid arguments. */
    fun decode(argument: String): ToolIntent? {
        if (!argument.startsWith(PREFIX)) return null
        val token = argument.substring(PREFIX.length)
        if (token.isEmpty() || !token.matches(Regex("[A-Za-z0-9_-]+"))) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(token)
            if (Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) != token) return null
            val payload = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            decodePayload(payload)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
    }

    /**
     * Compatibility adapter for the established `ports?host={host}` route.
     * Its host is percent-decoded as a URI query value (a literal `+` stays a
     * plus, matching Android Uri decoding), then validated by the same model.
     */
    fun decodeLegacyPortsRoute(route: String): ToolIntent? {
        val queryStart = route.indexOf('?')
        val path = if (queryStart < 0) route else route.substring(0, queryStart)
        if (path != "ports" || queryStart < 0) return null
        val query = route.substring(queryStart + 1)
        val components = query.split('&')
        if (components.size != 1) return null
        val component = components.single()
        val equals = component.indexOf('=')
        if (equals < 0 || component.indexOf('=', startIndex = equals + 1) >= 0) return null
        // Require the literal key so percent-encoded aliases cannot shadow or
        // duplicate the established argument name.
        if (component.substring(0, equals) != "host") return null
        val hostValue = component.substring(equals + 1)
        val decodedHost = percentDecode(hostValue) ?: return null
        val host = ToolHost.parse(decodedHost) ?: return null
        return ToolIntent(ToolDestination.HostTarget(HostTool.PORTS, host))
    }

    private fun decodePayload(payload: String): ToolIntent? {
        val fields = payload.split('|')
        return when (fields.firstOrNull()) {
            "host" -> {
                if (fields.size != 5) return null
                val tool = HostTool.entries.singleOrNull { it.wireName == fields[1] } ?: return null
                val host = ToolHost.parse(fields[2]) ?: return null
                val port = if (fields[3].isEmpty()) null else ToolPort.parse(fields[3].toIntOrNull() ?: return null)
                    ?: return null
                val source = parseSource(fields[4]) ?: if (fields[4].isEmpty()) null else return null
                ToolIntent(ToolDestination.HostTarget(tool, host, port), source)
            }
            "wol" -> {
                if (fields.size != 3) return null
                val mac = ToolMacAddress.parse(fields[1]) ?: return null
                val source = parseSource(fields[2]) ?: if (fields[2].isEmpty()) null else return null
                ToolIntent(ToolDestination.WakeOnLan(mac), source)
            }
            "subnet" -> {
                if (fields.size != 3) return null
                val subnet = ToolSubnet.parse(fields[1]) ?: return null
                val source = parseSource(fields[2]) ?: if (fields[2].isEmpty()) null else return null
                ToolIntent(ToolDestination.Subnet(subnet), source)
            }
            else -> null
        }
    }

    private fun parseSource(value: String): ToolSource? =
        ToolSource.entries.singleOrNull { it.wireName == value }

    private fun percentDecode(input: String): String? {
        val bytes = ByteArrayOutputStream(input.length)
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (char == '%') {
                if (index + 2 >= input.length) return null
                val high = input[index + 1].digitToIntOrNull(16) ?: return null
                val low = input[index + 2].digitToIntOrNull(16) ?: return null
                bytes.write((high shl 4) or low)
                index += 3
            } else {
                val codePoint = input.codePointAt(index)
                bytes.write(String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8))
                index += Character.charCount(codePoint)
            }
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
    }
}
