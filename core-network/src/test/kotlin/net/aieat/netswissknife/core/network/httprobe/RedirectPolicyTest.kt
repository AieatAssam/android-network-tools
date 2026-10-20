package net.aieat.netswissknife.core.network.httprobe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URL

class RedirectPolicyTest {
    @Test
    fun `redirect method semantics preserve entities only where required`() {
        val source = URL("http://one.test/start")
        val post302 = RedirectPolicy.evaluate(source, HttpMethod.POST, "payload", 302, "/next", 0, 10)
        assertTrue(post302 is RedirectPolicy.Decision.Follow)
        assertEquals(HttpMethod.GET, (post302 as RedirectPolicy.Decision.Follow).method)
        assertEquals(null, post302.body)

        val post307 = RedirectPolicy.evaluate(source, HttpMethod.POST, "payload", 307, "/next", 0, 10)
        assertTrue(post307 is RedirectPolicy.Decision.Follow)
        assertEquals(HttpMethod.POST, (post307 as RedirectPolicy.Decision.Follow).method)
        assertEquals("payload", post307.body)
    }

    @Test
    fun `cross origin strips destination userinfo and reports origin change`() {
        val source = URL("http://alice:source@one.test/start")
        val decision = RedirectPolicy.evaluate(
            source, HttpMethod.GET, null, 302, "http://bob:destination@two.test/next", 0, 10
        )
        assertTrue(decision is RedirectPolicy.Decision.Follow)
        val follow = decision as RedirectPolicy.Decision.Follow
        assertTrue(follow.changesOrigin)
        assertEquals(null, follow.destination.userInfo)
    }

    @Test
    fun `policy blocks downgrade and bounds hops without resolving further`() {
        val source = URL("https://source.test/start")
        val downgrade = RedirectPolicy.evaluate(source, HttpMethod.GET, null, 302, "http://destination.test/", 0, 10)
        assertTrue(downgrade is RedirectPolicy.Decision.BlockedDowngrade)
        val tooMany = RedirectPolicy.evaluate(source, HttpMethod.GET, null, 302, "/next", 10, 10)
        assertEquals(RedirectPolicy.Decision.TooManyRedirects, tooMany)
        val malformed = RedirectPolicy.evaluate(source, HttpMethod.GET, null, 302, "http://[broken", 0, 10)
        assertEquals(RedirectPolicy.Decision.MalformedLocation, malformed)
        assertFalse(RedirectPolicy.evaluate(source, HttpMethod.GET, null, 200, "/ignored", 0, 10) is RedirectPolicy.Decision.Follow)
    }
}
