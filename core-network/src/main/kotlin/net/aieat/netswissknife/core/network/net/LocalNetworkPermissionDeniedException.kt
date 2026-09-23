package net.aieat.netswissknife.core.network.net

/** Indicates Android denied a socket operation targeting the selected local network. */
class LocalNetworkPermissionDeniedException(cause: SecurityException) :
    Exception("Local network permission denied", cause)

/** Checks wrapped transport failures without assuming repository layers preserve the top-level type. */
fun Throwable?.containsLocalNetworkPermissionDenied(): Boolean {
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    var current = this
    while (current != null && visited.add(current)) {
        if (current is LocalNetworkPermissionDeniedException) return true
        current = current.cause
    }
    return false
}
