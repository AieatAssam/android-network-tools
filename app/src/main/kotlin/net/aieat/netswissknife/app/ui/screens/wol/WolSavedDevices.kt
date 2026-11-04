package net.aieat.netswissknife.app.ui.screens.wol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.aieat.netswissknife.core.network.wol.WolMagicPacket

/** A saved Wake-on-LAN target. [mac] is always canonical (`AA:BB:CC:DD:EE:FF`). */
data class WolSavedDevice(
    val mac: String,
    val name: String,
)

/** Result of saving an edited device. */
sealed interface WolSaveOutcome {
    data class Saved(
        val devices: List<WolSavedDevice>,
    ) : WolSaveOutcome

    data object Duplicate : WolSaveOutcome
}

/** Persistence boundary for saved devices. Implementations must apply [update] atomically. */
interface WolSavedDeviceStore {
    val devices: Flow<List<WolSavedDevice>>

    suspend fun update(transform: (List<WolSavedDevice>) -> List<WolSavedDevice>)
}

/** Process-local store; used by ViewModel unit tests. Production uses the DataStore-backed store. */
class InMemoryWolSavedDeviceStore(
    initial: List<WolSavedDevice> = emptyList(),
) : WolSavedDeviceStore {
    private val state = MutableStateFlow(initial)

    /** Current contents, for tests and previews. */
    val current: StateFlow<List<WolSavedDevice>>
        get() = state

    override val devices: Flow<List<WolSavedDevice>>
        get() = state

    override suspend fun update(transform: (List<WolSavedDevice>) -> List<WolSavedDevice>) {
        state.update(transform)
    }
}

/**
 * Pure list operations for saved devices. MACs are compared in canonical form, so the same
 * device saved in two notations is one entry. Names are single-line and capped.
 */
object WolSavedDevices {
    const val MAX_NAME_LENGTH = 40

    private const val LINE_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = '\t'

    /**
     * Adds [mac], or when it is already saved keeps that entry's name unless [name] is a new,
     * non-blank name, which renames it. [mac] must be canonical.
     */
    fun upsert(
        devices: List<WolSavedDevice>,
        mac: String,
        name: String,
    ): List<WolSavedDevice> {
        val typed = sanitizeName(name)
        return if (devices.any { it.mac == mac }) {
            devices.map { existing ->
                when {
                    existing.mac != mac -> existing
                    typed.isEmpty() -> existing
                    else -> WolSavedDevice(mac, typed)
                }
            }
        } else {
            devices + WolSavedDevice(mac, typed)
        }
    }

    /**
     * Explains a save of [mac] that already exists: the saved name is kept when no new name was
     * typed (or it matches), otherwise the device is renamed. Null when [mac] is not saved yet.
     */
    fun saveNotice(
        devices: List<WolSavedDevice>,
        mac: String,
        name: String,
    ): WolSaveNotice? {
        val existing = devices.firstOrNull { it.mac == mac } ?: return null
        val previous = displayName(existing)
        val typed = sanitizeName(name)
        return if (typed.isEmpty() || typed == existing.name) {
            WolSaveNotice.KeptName(previous)
        } else {
            WolSaveNotice.Renamed(previousName = previous, name = typed)
        }
    }

    /**
     * Replaces the entry for [originalMac] with [mac] and [name], keeping its position.
     * Returns [WolSaveOutcome.Duplicate] when [mac] belongs to a different saved entry.
     * If [originalMac] is no longer saved, this behaves like [upsert].
     */
    fun edit(
        devices: List<WolSavedDevice>,
        originalMac: String,
        mac: String,
        name: String,
    ): WolSaveOutcome {
        val isSaved = devices.any { it.mac == originalMac }
        val conflicts = mac != originalMac && devices.any { it.mac == mac }
        val replacement = WolSavedDevice(mac, sanitizeName(name))
        return when {
            !isSaved -> {
                WolSaveOutcome.Saved(upsert(devices, mac, name))
            }

            conflicts -> {
                WolSaveOutcome.Duplicate
            }

            else -> {
                WolSaveOutcome.Saved(devices.map { if (it.mac == originalMac) replacement else it })
            }
        }
    }

    fun remove(
        devices: List<WolSavedDevice>,
        mac: String,
    ): List<WolSavedDevice> = devices.filterNot { it.mac == mac }

    /** Label for a device: its name, or its MAC when unnamed. */
    fun displayName(device: WolSavedDevice): String = device.name.ifBlank { device.mac }

    fun encode(devices: List<WolSavedDevice>): String = devices.joinToString(LINE_SEPARATOR, transform = ::encodeLine)

    private fun encodeLine(device: WolSavedDevice): String = "${device.mac}$FIELD_SEPARATOR${sanitizeName(device.name)}"

    /** Parses the stored form. Malformed or multicast lines are skipped; the first duplicate MAC wins. */
    fun decode(raw: String?): List<WolSavedDevice> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw
            .split(LINE_SEPARATOR)
            .mapNotNull(::parseLine)
            .distinctBy { it.mac }
    }

    private fun parseLine(line: String): WolSavedDevice? {
        val canonical = WolMagicPacket.canonicalMacOrNull(line.substringBefore(FIELD_SEPARATOR)) ?: return null
        return WolSavedDevice(canonical, sanitizeName(line.substringAfter(FIELD_SEPARATOR, "")))
    }

    private fun sanitizeName(name: String): String =
        name
            .map { if (it == '\t' || it == '\n' || it == '\r') ' ' else it }
            .joinToString("")
            .trim()
            .take(MAX_NAME_LENGTH)
}
