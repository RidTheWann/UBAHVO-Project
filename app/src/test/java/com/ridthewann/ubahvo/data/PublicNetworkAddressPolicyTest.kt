package com.ridthewann.ubahvo.data

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicNetworkAddressPolicyTest {
    @Test
    fun acceptsOrdinaryGlobalIpv4AndIpv6Destinations() {
        listOf(
            "1.1.1.1",
            "8.8.8.8",
            "93.184.216.34",
            "2606:4700:4700::1111",
            "2001:4860:4860::8888"
        ).forEach { address ->
            assertTrue("$address should be globally reachable", isGlobal(address))
        }
    }

    @Test
    fun rejectsPrivateLoopbackLinkLocalAndCarrierGradeNatIpv4() {
        listOf(
            "0.1.2.3",
            "10.0.0.1",
            "100.64.0.1",
            "100.127.255.254",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "172.31.255.254",
            "192.0.0.1",
            "192.168.1.1",
            "224.0.0.1",
            "255.255.255.255"
        ).forEach { address ->
            assertFalse("$address must not be fetched", isGlobal(address))
        }
    }

    @Test
    fun rejectsBenchmarkingDocumentationAndDeprecatedIpv4Ranges() {
        listOf(
            "192.0.2.1",
            "192.88.99.1",
            "198.18.0.1",
            "198.19.255.254",
            "198.51.100.1",
            "203.0.113.1",
            "240.0.0.1"
        ).forEach { address ->
            assertFalse("$address must not be fetched", isGlobal(address))
        }
    }

    @Test
    fun rejectsNonGlobalIpv6ClassesAndSpecialPurposeRanges() {
        listOf(
            "::",
            "::1",
            "::ffff:127.0.0.1",
            "64:ff9b::a00:1",
            "100::1",
            "2001:0:1::1",
            "2001:2::1",
            "2001:db8::1",
            "2002::1",
            "3f00::1",
            "3fff::1",
            "5f00::1",
            "fc00::1",
            "fe80::1",
            "ff02::1"
        ).forEach { address ->
            assertFalse("$address must not be fetched", isGlobal(address))
        }
    }

    @Test
    fun rejectsMixedOrUnsupportedAddressFamiliesRatherThanAssumingGlobalReachability() {
        assertFalse(isGlobal("localhost"))
    }

    private fun isGlobal(value: String): Boolean =
        PublicNetworkAddressPolicy.isGloballyReachable(InetAddress.getByName(value))
}
