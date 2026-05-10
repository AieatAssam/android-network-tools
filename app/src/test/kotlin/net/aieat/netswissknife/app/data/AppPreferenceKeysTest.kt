package net.aieat.netswissknife.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that every DataStore key name is stable. Renaming a key without migrating
 * stored data silently corrupts user preferences, so these tests act as a change-detector.
 * Changing a key name here is a deliberate, reviewable act.
 */
@DisplayName("AppPreferenceKeys – key name stability")
class AppPreferenceKeysTest {

    @Test fun `PINNED_ROUTES key name`()           = assertKey("pinned_routes",           AppPreferenceKeys.PINNED_ROUTES)
    @Test fun `THEME_OVERRIDE key name`()          = assertKey("theme_override",           AppPreferenceKeys.THEME_OVERRIDE)
    @Test fun `DEFAULT_PING_COUNT key name`()      = assertKey("default_ping_count",       AppPreferenceKeys.DEFAULT_PING_COUNT)
    @Test fun `DEFAULT_TIMEOUT_MS key name`()      = assertKey("default_timeout_ms",       AppPreferenceKeys.DEFAULT_TIMEOUT_MS)
    @Test fun `DEFAULT_CONCURRENCY key name`()     = assertKey("default_concurrency",      AppPreferenceKeys.DEFAULT_CONCURRENCY)
    @Test fun `RECENT_PING_HOSTS key name`()       = assertKey("recent_ping_hosts",        AppPreferenceKeys.RECENT_PING_HOSTS)
    @Test fun `RECENT_DNS_HOSTS key name`()        = assertKey("recent_dns_hosts",         AppPreferenceKeys.RECENT_DNS_HOSTS)
    @Test fun `RECENT_PORTS_HOSTS key name`()      = assertKey("recent_ports_hosts",       AppPreferenceKeys.RECENT_PORTS_HOSTS)
    @Test fun `RECENT_TRACEROUTE_HOSTS key name`() = assertKey("recent_traceroute_hosts",  AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS)
    @Test fun `RECENT_TLS_HOSTS key name`()        = assertKey("recent_tls_hosts",         AppPreferenceKeys.RECENT_TLS_HOSTS)
    @Test fun `RECENT_WHOIS_HOSTS key name`()      = assertKey("recent_whois_hosts",       AppPreferenceKeys.RECENT_WHOIS_HOSTS)
    @Test fun `RECENT_HTTP_HOSTS key name`()       = assertKey("recent_http_hosts",        AppPreferenceKeys.RECENT_HTTP_HOSTS)
    @Test fun `RECENT_LAN_SUBNETS key name`()      = assertKey("recent_lan_subnets",       AppPreferenceKeys.RECENT_LAN_SUBNETS)

    private fun assertKey(expectedName: String, key: androidx.datastore.preferences.core.Preferences.Key<*>) {
        assertEquals(expectedName, key.name)
    }
}
