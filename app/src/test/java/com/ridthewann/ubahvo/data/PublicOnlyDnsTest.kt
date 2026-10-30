package com.ridthewann.ubahvo.data

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PublicOnlyDnsTest {
    @Test
    fun returnsTheValidatedAddressSetWithoutFilteringOrReordering() {
        val expected = listOf(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("2606:4700:4700::1111")
        )
        val dns = PublicOnlyDns(FixedDns(expected))

        assertEquals(expected, dns.lookup("cdn.example.org"))
    }

    @Test
    fun rejectsMixedPublicAndPrivateAnswersAsAWhole() {
        val dns = PublicOnlyDns(
            FixedDns(
                listOf(
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("192.168.1.1")
                )
            )
        )

        assertThrows(UnknownHostException::class.java) {
            dns.lookup("mixed.example.org")
        }
    }

    @Test
    fun rejectsCarrierGradeNatAndSpecialPurposeAnswers() {
        listOf(
            "100.64.0.1",
            "198.18.0.1",
            "192.0.2.1",
            "203.0.113.1",
            "3fff::1",
            "5f00::1"
        ).forEach { address ->
            val dns = PublicOnlyDns(FixedDns(listOf(InetAddress.getByName(address))))
            assertThrows(UnknownHostException::class.java) {
                dns.lookup("non-global.example.org")
            }
        }
    }

    @Test
    fun rejectsEmptyAnswerSets() {
        val dns = PublicOnlyDns(FixedDns(emptyList()))

        assertThrows(UnknownHostException::class.java) {
            dns.lookup("empty.example.org")
        }
    }

    @Test
    fun preservesTheUnderlyingDnsFailure() {
        val expected = UnknownHostException("synthetic DNS failure")
        val dns = PublicOnlyDns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = throw expected
        })

        val actual = assertThrows(UnknownHostException::class.java) {
            dns.lookup("missing.example.org")
        }
        assertEquals(expected.message, actual.message)
    }

    private class FixedDns(private val addresses: List<InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = addresses
    }
}
