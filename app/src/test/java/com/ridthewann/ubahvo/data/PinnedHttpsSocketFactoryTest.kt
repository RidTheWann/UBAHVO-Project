package com.ridthewann.ubahvo.data

import java.io.IOException
import java.net.InetAddress
import org.junit.Assert.assertThrows
import org.junit.Test

class PinnedHttpsSocketFactoryTest {
    private val publicAddress = InetAddress.getByName("8.8.8.8")

    @Test
    fun rejectsAnUnexpectedHostnameBeforeOpeningAnySocket() {
        val factory = factory()

        assertThrows(IOException::class.java) {
            factory.createSocket("attacker.example", 443)
        }
    }

    @Test
    fun rejectsNonHttpsPortsBeforeOpeningAnySocket() {
        val factory = factory()

        assertThrows(IOException::class.java) {
            factory.createSocket("cdn.example.org", 8443)
        }
    }

    @Test
    fun rejectsAnAddressOutsideTheValidatedDnsAnswer() {
        val factory = factory()

        assertThrows(IOException::class.java) {
            factory.createSocket(InetAddress.getByName("127.0.0.1"), 443)
        }
    }

    @Test
    fun rejectsUnboundSocketCreationThatCouldResolveDnsAgain() {
        val factory = factory()

        assertThrows(IOException::class.java) {
            factory.createSocket()
        }
    }

    private fun factory() = PinnedHttpsSocketFactory(
        hostname = "cdn.example.org",
        addresses = listOf(publicAddress),
        connectTimeoutMs = 100,
        readTimeoutMs = 100
    )
}
