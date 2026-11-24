package net.aieat.netswissknife.core.network.httprobe

import mockwebserver3.MockWebServer
import net.aieat.netswissknife.core.network.httprobe.engine.OkHttpEngine
import okhttp3.OkHttpClient
import okhttp3.Protocol

/** Starts MockWebServers for one test and closes every one of them afterwards. */
internal class MockWebServerPool {
    private val servers = mutableListOf<MockWebServer>()

    fun start(protocols: List<Protocol>): MockWebServer =
        MockWebServer().also {
            it.protocols = protocols
            it.start()
            servers += it
        }

    /** Tracks a server the test configured and started itself. */
    operator fun plusAssign(server: MockWebServer) {
        servers += server
    }

    fun closeAll() {
        servers.forEach(MockWebServer::close)
    }
}

/** A probe repository over a real OkHttp engine that speaks only [protocol]. */
internal fun okHttpRepositoryFor(protocol: Protocol): HttpProbeRepositoryImpl =
    HttpProbeRepositoryImpl(
        OkHttpEngine(OkHttpClient.Builder().protocols(listOf(protocol)).build()),
    )
