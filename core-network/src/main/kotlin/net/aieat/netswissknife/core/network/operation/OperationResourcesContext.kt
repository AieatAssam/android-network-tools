package net.aieat.netswissknife.core.network.operation

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Makes the active operation's resource owner available to nested repository adapters. */
internal class OperationResourcesContext(
    val session: OperationSession,
) : AbstractCoroutineContextElement(Key) {
    val resources: ResourceScope get() = session.resources

    companion object Key : CoroutineContext.Key<OperationResourcesContext>
}

/** Checks job cancellation and the active operation's first-wins reason and deadline. */
internal suspend fun ensureCurrentOperationActive() {
    val context = currentCoroutineContext()
    context.ensureActive()
    context[OperationResourcesContext]?.session?.let { session ->
        session.throwIfCancelled()
        session.budget.throwIfExpired()
    }
}
