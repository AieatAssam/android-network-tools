package net.aieat.netswissknife.app.ui.screens.wol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.aieat.netswissknife.core.domain.WakeOnLanParams
import net.aieat.netswissknife.core.network.wol.WolMagicPacket

/**
 * A saved Wake-on-LAN target. [mac] is always canonical (`AA:BB:CC:DD:EE:FF`). [broadcastAddress] and
 * [port] are the send settings the device was saved with, so one tap wakes it the same way every time.
 */
data class WolSavedDevice(
    val mac: String,
    val name: String,
    val broadcastAddress: String = WakeOnLanViewModel.DEFAULT_BROADCAST,
    val port: Int = WakeOnLanViewModel.DEFAULT_PORT,
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
     * non-blank name, which renames it. The send settings always take the values given, so saving
     * a device records the broadcast address and port it was saved with. [mac] must be canonical.
     */
    fun upsert(
        devices: List<WolSavedDevice>,
        mac: String,
        name: String,
        broadcastAddress: String = WakeOnLanViewModel.DEFAULT_BROADCAST,
        port: Int = WakeOnLanViewModel.DEFAULT_PORT,
    ): List<WolSavedDevice> {
        val typed = sanitizeName(name)
        return if (devices.any { it.mac == mac }) {
            devices.map { existing ->
                when {
                    existing.mac != mac -> existing
                    typed.isEmpty() -> WolSavedDevice(mac, existing.name, broadcastAddress, port)
                    else -> WolSavedDevice(mac, typed, broadcastAddress, port)
                }
            }
        } else {
            devices + WolSavedDevice(mac, typed, broadcastAddress, port)
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
        broadcastAddress: String = WakeOnLanViewModel.DEFAULT_BROADCAST,
        port: Int = WakeOnLanViewModel.DEFAULT_PORT,
    ): WolSaveOutcome {
        val isSaved = devices.any { it.mac == originalMac }
        val conflicts = mac != originalMac && devices.any { it.mac == mac }
        val replacement = WolSavedDevice(mac, sanitizeName(name), broadcastAddress, port)
        return when {
            !isSaved -> {
                WolSaveOutcome.Saved(upsert(devices, mac, name, broadcastAddress, port))
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

    private fun encodeLine(device: WolSavedDevice): String =
        listOf(
            device.mac,
            sanitizeName(device.name),
            device.broadcastAddress,
            device.port.toString(),
        ).joinToString(FIELD_SEPARATOR.toString())

    /**
     * Parses the stored form. Malformed or multicast lines are skipped; the first duplicate MAC wins.
     * Lines written before the send settings were stored have two fields and get the defaults, as does
     * any stored broadcast address or port that no longer validates.
     */
    fun decode(raw: String?): List<WolSavedDevice> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw
            .split(LINE_SEPARATOR)
            .mapNotNull(::parseLine)
            .distinctBy { it.mac }
    }

    private fun parseLine(line: String): WolSavedDevice? {
        val fields = line.split(FIELD_SEPARATOR)
        val canonical = WolMagicPacket.canonicalMacOrNull(fields[0]) ?: return null
        val name = sanitizeName(fields.getOrElse(1) { "" })
        val broadcast =
            fields.getOrNull(2)
                ?.takeIf { WolMagicPacket.broadcastProblem(it) == null }
                ?: WakeOnLanViewModel.DEFAULT_BROADCAST
        val port =
            fields.getOrNull(3)
                ?.toIntOrNull()
                ?.takeIf { it in WakeOnLanParams.MIN_PORT..WakeOnLanParams.MAX_PORT }
                ?: WakeOnLanViewModel.DEFAULT_PORT
        return WolSavedDevice(canonical, name, broadcast, port)
    }

    private fun sanitizeName(name: String): String =
        name
            .map { if (it == '\t' || it == '\n' || it == '\r') ' ' else it }
            .joinToString("")
            .trim()
            .take(MAX_NAME_LENGTH)
}
