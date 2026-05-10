package net.aieat.netswissknife.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

object AppPreferenceKeys {

    // ── Navigation ────────────────────────────────────────────────────────────
    /** Ordered pipe-delimited list of pinned route strings, e.g. "ping|dns|ports". */
    val PINNED_ROUTES = stringPreferencesKey("pinned_routes")

    // ── Settings ──────────────────────────────────────────────────────────────
    /** Theme override: "SYSTEM", "LIGHT", or "DARK". */
    val THEME_OVERRIDE = stringPreferencesKey("theme_override")

    /** Default ping packet count (1–100). */
    val DEFAULT_PING_COUNT = intPreferencesKey("default_ping_count")

    /** Default network operation timeout in milliseconds. */
    val DEFAULT_TIMEOUT_MS = intPreferencesKey("default_timeout_ms")

    /** Default number of concurrent probes (1–500). */
    val DEFAULT_CONCURRENCY = intPreferencesKey("default_concurrency")

    // ── Recent hosts (ordered pipe-delimited lists, max 5 entries each) ───────
    val RECENT_PING_HOSTS       = stringPreferencesKey("recent_ping_hosts")
    val RECENT_DNS_HOSTS        = stringPreferencesKey("recent_dns_hosts")
    val RECENT_PORTS_HOSTS      = stringPreferencesKey("recent_ports_hosts")
    val RECENT_TRACEROUTE_HOSTS = stringPreferencesKey("recent_traceroute_hosts")
    val RECENT_TLS_HOSTS        = stringPreferencesKey("recent_tls_hosts")
    val RECENT_WHOIS_HOSTS      = stringPreferencesKey("recent_whois_hosts")
    val RECENT_HTTP_HOSTS       = stringPreferencesKey("recent_http_hosts")
    /** Stores recent subnets for LAN Scanner (CIDR notation). */
    val RECENT_LAN_SUBNETS      = stringPreferencesKey("recent_lan_subnets")

    /** All recent-host keys — iterate this to clear all at once. */
    val ALL_RECENT_HOST_KEYS: List<Preferences.Key<String>> = listOf(
        RECENT_PING_HOSTS, RECENT_DNS_HOSTS, RECENT_PORTS_HOSTS,
        RECENT_TRACEROUTE_HOSTS, RECENT_TLS_HOSTS, RECENT_WHOIS_HOSTS,
        RECENT_HTTP_HOSTS, RECENT_LAN_SUBNETS
    )
}
