package net.aieat.netswissknife.core.network.oui

private const val MA_L_BITS = 24
private const val MA_M_BITS = 28
private const val MA_S_BITS = 36

/** IEEE registry block sizes, identified by how many leading bits of the address they assign. */
enum class OuiBlockSize(
    val registryName: String,
    val prefixBits: Int,
) {
    /** 24-bit OUI block (MA-L). */
    MA_L("MA-L", MA_L_BITS),

    /** 28-bit block (MA-M). */
    MA_M("MA-M", MA_M_BITS),

    /** 36-bit block (MA-S). */
    MA_S("MA-S", MA_S_BITS),
}

/** A registered block that matched a MAC prefix. */
data class OuiMatch(
    val vendor: String,
    val blockSize: OuiBlockSize,
)

/** Read-only view of the IEEE OUI registry. The bundled database implements it. */
interface OuiRegistry {
    /**
     * Longest registered block for [hexDigits], which are hex without separators (either case) and
     * are either a 6-digit OUI prefix or a full 12-digit MAC. Returns null when nothing is registered
     * or the address is locally administered.
     */
    fun match(hexDigits: String): OuiMatch?
}
