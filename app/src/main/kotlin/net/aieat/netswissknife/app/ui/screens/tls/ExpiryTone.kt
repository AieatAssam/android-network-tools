package net.aieat.netswissknife.app.ui.screens.tls

/** Colour role for a certificate's expiry chip. Colour carries the state; the text carries the number. */
enum class ExpiryTone { OK, WARNING, ERROR }

/** Certificates that expire within this many days are shown as a warning. */
const val EXPIRY_WARNING_DAYS = 30L

/**
 * Maps a certificate's validity to a tone. Expired and not-yet-valid certificates are errors whatever
 * the day count; valid ones are a warning from [EXPIRY_WARNING_DAYS] days left (inclusive) and OK
 * beyond that.
 */
fun expiryTone(
    daysLeft: Long,
    expired: Boolean,
    notYetValid: Boolean,
): ExpiryTone =
    when {
        expired || notYetValid || daysLeft < 0 -> ExpiryTone.ERROR
        daysLeft <= EXPIRY_WARNING_DAYS -> ExpiryTone.WARNING
        else -> ExpiryTone.OK
    }
