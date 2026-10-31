package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.oui.MacInputParser
import net.aieat.netswissknife.core.network.oui.MacLookupEngine
import net.aieat.netswissknife.core.network.oui.MacLookupEntry
import net.aieat.netswissknife.core.network.oui.OuiRegistry

/** Result of one MAC Lookup. [truncated] is true when the paste held more entries than were analysed. */
data class OuiLookupReport(
    val entries: List<MacLookupEntry>,
    val truncated: Boolean,
)

/**
 * Analyses every entry in pasted text against the bundled OUI registry. The first registry read
 * loads the whole table, so the work runs off the main thread.
 */
class OuiLookupUseCase(
    registry: OuiRegistry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UseCase<String, OuiLookupReport> {
    private val engine = MacLookupEngine(registry)

    override suspend fun invoke(params: String): OuiLookupReport =
        withContext(dispatcher) {
            val pieces = MacInputParser.splitEntries(params)
            OuiLookupReport(
                entries = pieces.take(MAX_ENTRIES).map(engine::lookup),
                truncated = pieces.size > MAX_ENTRIES,
            )
        }

    companion object {
        /** Keeps a huge paste from building an unbounded result list. */
        const val MAX_ENTRIES = 100
    }
}
