package net.aieat.netswissknife.core.network.net

/** Indicates Android denied a socket operation targeting the selected local network. */
class LocalNetworkPermissionDeniedException(cause: SecurityException) :
    Exception("Local network permission denied", cause)
