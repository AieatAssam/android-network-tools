package net.aieat.netswissknife.core.network.whois

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.util.Locale

/** Converts one RFC 9083 RDAP response into the existing WHOIS presentation model. */
object RdapMapper {
    private val json = Json {
        isLenient = false
        ignoreUnknownKeys = true
    }

    /**
     * Parse and map a raw RDAP object. Unsupported object classes and malformed JSON fail fast
     * so the repository can treat the response as unusable and try its WHOIS fallback.
     */
    fun map(
        rawJson: String,
        query: String,
        queryType: WhoisQueryType,
        serverHost: String,
        queryTimeMs: Long = 0,
    ): WhoisResult {
        require(queryTimeMs >= 0) { "queryTimeMs cannot be negative" }
        val root = json.parseToJsonElement(rawJson) as? JsonObject
            ?: throw IllegalArgumentException("RDAP response must be a JSON object")
        val response = decode(root)
        require(response.matchesQueryType(queryType)) {
            "RDAP object ${response.objectClassName}/${response.ipVersion} does not match $queryType"
        }

        val registrar = response.entities.firstOrNull { "registrar" in it.roles }
        val registrant = response.entities.firstOrNull { "registrant" in it.roles }
        // Keep the first valid known event for each action, even if a server repeats one.
        val events = response.events.asReversed().mapNotNull { event ->
            val action = event.action.trim().lowercase(Locale.ROOT)
            val date = WhoisResponseParser.parseDate(event.date) ?: return@mapNotNull null
            if (action in EVENT_ACTIONS) action to date else null
        }.toMap()
        val hop = WhoisHop(
            server = WhoisServer(serverHost, WhoisServerRole.RDAP),
            rawResponse = rawJson,
            queryTimeMs = queryTimeMs,
            referral = null,
        )

        return WhoisResult(
            query = query,
            queryType = queryType,
            hops = listOf(hop),
            domainName = response.ldhName ?: response.unicodeName,
            registrar = registrar?.fullName ?: registrar?.organization,
            registrarUrl = registrar?.url?.takeIf(::isSafeWebUrl),
            registeredOn = events["registration"],
            expiresOn = events["expiration"] ?: events["expiry"],
            updatedOn = events["last changed"],
            nameServers = response.nameServers,
            statusCodes = response.statuses,
            registrantOrg = registrant?.organization,
            registrantCountry = registrant?.country,
            dnssec = response.delegationSigned?.let { if (it) "Signed" else "Unsigned" },
            netName = when (queryType) {
                WhoisQueryType.IPV4, WhoisQueryType.IPV6, WhoisQueryType.ASN -> response.name
                WhoisQueryType.DOMAIN -> null
            },
            netRange = when (queryType) {
                WhoisQueryType.IPV4, WhoisQueryType.IPV6 -> range(response.startAddress, response.endAddress)
                WhoisQueryType.ASN -> autnumRange(response.startAutnum, response.endAutnum)
                WhoisQueryType.DOMAIN -> null
            },
            orgName = when (queryType) {
                WhoisQueryType.ASN, WhoisQueryType.IPV4, WhoisQueryType.IPV6 -> registrant?.organization
                WhoisQueryType.DOMAIN -> null
            },
            country = response.country,
            totalQueryTimeMs = queryTimeMs,
            handle = response.handle,
        )
    }

    private fun decode(root: JsonObject): RdapResponse {
        val objectClassName = root.string("objectClassName")
            ?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("RDAP response is missing objectClassName")
        require(objectClassName in SUPPORTED_CLASSES) { "Unsupported RDAP objectClassName: $objectClassName" }

        return RdapResponse(
            objectClassName = objectClassName,
            handle = root.string("handle"),
            name = root.string("name"),
            ldhName = root.string("ldhName"),
            unicodeName = root.string("unicodeName"),
            ipVersion = root.string("ipVersion"),
            startAddress = root.string("startAddress"),
            endAddress = root.string("endAddress"),
            startAutnum = root.long("startAutnum"),
            endAutnum = root.long("endAutnum"),
            country = root.string("country"),
            events = root.objects("events").mapNotNull { event ->
                val action = event.string("eventAction")?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                val date = event.string("eventDate") ?: return@mapNotNull null
                RdapEvent(action, date)
            },
            nameServers = root.objects("nameservers")
                .mapNotNull { it.string("ldhName") ?: it.string("unicodeName") }
                .distinctBy { it.lowercase(Locale.ROOT) },
            statuses = root.strings("status").distinct(),
            // RFC 9083 roles are relative to the closest containing object;
            // only direct domain entities describe roles on this domain.
            entities = root.objects("entities").map(::decodeEntity),
            delegationSigned = root.objectValue("secureDNS")?.boolean("delegationSigned"),
        )
    }

    private fun decodeEntity(entity: JsonObject): RdapEntity {
        val roles = entity.strings("roles").map { it.lowercase(Locale.ROOT) }.toSet()
        val vcard = entity.arrayValue("vcardArray")
        val card = vcard
            ?.takeIf { it.getOrNull(0).stringValue().equals("vcard", ignoreCase = true) }
            ?.getOrNull(1) as? JsonArray
        val fullName = card?.vcardValue("fn")?.stringValue()
        val organization = card?.vcardValue("org")?.vcardTextValue()
        val address = card?.vcardValue("adr") as? JsonArray
        val country = address?.getOrNull(6).stringValue()
            ?: card?.vcardValue("country-name")?.stringValue()
        val url = card?.vcardValue("url")?.stringValue()
        return RdapEntity(roles, fullName, organization, country, url)
    }

    private fun JsonArray.vcardValue(property: String): JsonElement? =
        firstNotNullOfOrNull { row ->
            val values = row as? JsonArray ?: return@firstNotNullOfOrNull null
            if (values.getOrNull(0).stringValue()?.equals(property, ignoreCase = true) == true) {
                values.getOrNull(3)
            } else {
                null
            }
        }

    private fun JsonElement?.stringValue(): String? =
        (this as? JsonPrimitive)
            ?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    /** vCard ORG may be a structured array of string components. */
    private fun JsonElement?.vcardTextValue(): String? = when (this) {
        is JsonPrimitive -> stringValue()
        is JsonArray -> mapNotNull { it.stringValue() }.joinToString(" ").takeIf(String::isNotEmpty)
        else -> null
    }

    private fun JsonObject.string(name: String): String? = this[name].stringValue()

    private fun JsonObject.long(name: String): Long? =
        (this[name] as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.contentOrNull
            ?.toLongOrNull()
            ?.takeIf { it in 0L..MAX_AUTNUM }

    private fun JsonObject.boolean(name: String): Boolean? =
        (this[name] as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.booleanOrNull

    private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

    private fun JsonObject.arrayValue(name: String): JsonArray? = this[name] as? JsonArray

    private fun JsonObject.objects(name: String): List<JsonObject> =
        arrayValue(name)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.strings(name: String): List<String> =
        arrayValue(name)?.mapNotNull { it.stringValue() }.orEmpty()

    private fun range(start: String?, end: String?): String? = when {
        start != null && end != null -> "$start-$end"
        else -> null
    }

    private fun autnumRange(start: Long?, end: Long?): String? = when {
        start != null && end != null && start <= end -> "$start-$end"
        else -> null
    }

    private fun isSafeWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    private fun RdapResponse.matchesQueryType(type: WhoisQueryType): Boolean = when (type) {
        WhoisQueryType.DOMAIN -> objectClassName == "domain"
        WhoisQueryType.IPV4 -> objectClassName == "ip network" && (ipVersion == null || ipVersion.equals("v4", true))
        WhoisQueryType.IPV6 -> objectClassName == "ip network" && (ipVersion == null || ipVersion.equals("v6", true))
        WhoisQueryType.ASN -> objectClassName == "autnum"
    }

    private val SUPPORTED_CLASSES = setOf("domain", "ip network", "autnum")
    private val EVENT_ACTIONS = setOf("registration", "expiration", "expiry", "last changed")
    private const val MAX_AUTNUM = 4_294_967_295L
}
