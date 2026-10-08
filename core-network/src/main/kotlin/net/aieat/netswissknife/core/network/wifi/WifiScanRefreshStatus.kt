package net.aieat.netswissknife.core.network.wifi

/** Outcome of the most recent request to refresh the platform's Wi-Fi scan cache. */
enum class WifiScanRefreshStatus {
    /** Results were read without asking Android to start a new scan. */
    NOT_REQUESTED,
    /** Android reported that the scan results were updated. */
    UPDATED,
    /** Android completed the broadcast but reported that results were not updated. */
    NOT_UPDATED,
    /** Android accepted the request but did not report completion before the deadline. */
    TIMED_OUT,
    /** Android rejected the request; this does not establish why it was rejected. */
    REJECTED,
    /** The request or cache read failed after a previous result was already available. */
    FAILED,
    /** Location permission was revoked after a previous result was already available. */
    PERMISSION_DENIED
}
