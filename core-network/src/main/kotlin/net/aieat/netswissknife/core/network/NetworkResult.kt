package net.aieat.netswissknife.core.network

/**
 * Generic result wrapper for network operations.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val code: String? = null,
        val descriptionKey: String? = null,
        /** Structured error data for localized presentation. Null for legacy callers. */
        val info: ErrorInfo? = null,
    ) : NetworkResult<Nothing>()

    companion object {
        /** Create a typed error while retaining its developer-facing diagnostic message. */
        fun error(
            code: ErrorCode,
            vararg args: Any?,
        ): Error {
            val info = ErrorInfo(code = code, args = args.toList())
            return Error(message = info.defaultDeveloperMessage(), info = info)
        }

        /** Create a typed error and preserve the existing developer message and exception. */
        fun error(
            code: ErrorCode,
            developerMessage: String,
            cause: Throwable? = null,
            args: List<Any?> = emptyList(),
            legacyCode: String? = null,
            descriptionKey: String? = null,
        ): Error {
            val info = ErrorInfo(code = code, args = args, developerMessage = developerMessage)
            return Error(
                message = developerMessage,
                cause = cause,
                code = legacyCode,
                descriptionKey = descriptionKey,
                info = info,
            )
        }
    }
}
