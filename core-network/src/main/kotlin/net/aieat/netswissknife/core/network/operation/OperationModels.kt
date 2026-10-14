package net.aieat.netswissknife.core.network.operation

import java.util.UUID

/** Opaque identifier shared by the parent operation and its child probes. */
@JvmInline
value class OperationId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "Operation id must not be blank" }
        require(value == value.trim()) { "Operation id must not have surrounding whitespace" }
        require(value.length <= MAX_LENGTH) { "Operation id is too long" }
    }

    companion object {
        private const val MAX_LENGTH = 128

        fun create(): OperationId = OperationId(UUID.randomUUID().toString())

        fun from(value: String): OperationId = OperationId(value)
    }
}

/** Network reachability required by an operation's target. */
enum class OperationRequirement {
    LOCAL_NETWORK,
    INTERNET,
    ANY_NETWORK,
}

/** Reasons an operation owner may use when recording a stopped session. */
enum class CancellationReason {
    USER_STOP,
    DEADLINE_EXCEEDED,
    LIFECYCLE_PAUSE,
    PERMISSION_DENIED,
    NETWORK_LOST,
    PARENT_CANCELLED,
}
