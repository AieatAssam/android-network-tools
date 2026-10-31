package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.ErrorCode

/** Input problem carrying a stable [code] so the app can render localized copy. */
internal class SubnetInputException(
    val code: ErrorCode,
    message: String,
    val args: List<Any?> = emptyList(),
) : IllegalArgumentException(message)
