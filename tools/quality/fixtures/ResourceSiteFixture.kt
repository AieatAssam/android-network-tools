import java.net.Socket

class ResourceSiteFixture {
    // Imports, type annotations, and factory method names are not creation sites.
    fun viaFactory(newTcpSocket: () -> Socket): Socket = newTcpSocket()
    fun createRawSocket(): Socket = Socket()
}
