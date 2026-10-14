package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

/** Build typed validation information while retaining the historical developer-facing copy. */
internal fun validationError(
    code: ErrorCode,
    message: String,
    vararg args: Any?,
): ErrorInfo = ErrorInfo(code = code, args = args.toList(), developerMessage = message)

/** Compatibility copy for legacy callers that construct a typed error without developer text. */
internal fun ErrorInfo.developerCopy(): String =
    developerMessage ?: code.name.lowercase().replace('_', ' ')
