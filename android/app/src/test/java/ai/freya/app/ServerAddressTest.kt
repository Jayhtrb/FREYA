package ai.freya.app

import ai.freya.app.data.remote.ServerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    private fun n(s: String) = ServerAddress.normalize(s)?.toString()

    @Test fun bareIpGetsBackendPort() = assertEquals("http://192.168.1.20:5001/", n("192.168.1.20"))
    @Test fun explicitPortKept() = assertEquals("http://192.168.1.20:8080/", n(" 192.168.1.20:8080/ "))
    @Test fun schemeKept() = assertEquals("https://freya.example.com/", n("https://freya.example.com"))
    @Test fun pathDropped() = assertEquals("http://10.0.0.5:5001/", n("http://10.0.0.5:5001/api/x"))
    @Test fun junkRejected() {
        assertNull(n(""))
        assertNull(n("   "))
        assertNull(n("http://"))
    }
}
