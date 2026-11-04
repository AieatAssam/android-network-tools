package net.aieat.netswissknife.app.ui.format

private const val ZWSP = "\u200B"

/**
 * Display-only: adds a zero-width space after each `_` and `-` so long identifiers such as cipher
 * suites and header names wrap at a separator instead of in the middle of a word (PHB-20).
 * Never use the result for copy, share or export text; it contains invisible characters.
 */
fun withSeparatorBreaks(text: String): String = text.replace("_", "_$ZWSP").replace("-", "-$ZWSP")
