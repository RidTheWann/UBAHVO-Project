package com.ridthewann.ubahvo.data

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Conservative destination policy for public image fetching.
 *
 * IPv4 prefixes follow the IANA IPv4 Special-Purpose Address Registry. IPv6 destinations
 * must be global-unicast (2000::/3) and must not fall in the IANA protocol-assignment,
 * documentation, or 6to4 ranges listed below. A mixed DNS answer is rejected in full.
 */
internal object PublicNetworkAddressPolicy {
    private data class Ipv4Prefix(val network: Long, val prefixLength: Int)

    private val nonGlobalIpv4 = listOf(
        Ipv4Prefix(0x00000000L, 8),  // This network
        Ipv4Prefix(0x0A000000L, 8),  // RFC 1918
        Ipv4Prefix(0x64400000L, 10), // Shared address space / carrier-grade NAT
        Ipv4Prefix(0x7F000000L, 8),  // Loopback
        Ipv4Prefix(0xA9FE0000L, 16), // Link-local
        Ipv4Prefix(0xAC100000L, 12), // RFC 1918
        Ipv4Prefix(0xC0000000L, 24), // IETF protocol assignments (conservative whole-block deny)
        Ipv4Prefix(0xC0000200L, 24), // TEST-NET-1 documentation
        Ipv4Prefix(0xC0586300L, 24), // Deprecated 6to4 relay anycast
        Ipv4Prefix(0xC0A80000L, 16), // RFC 1918
        Ipv4Prefix(0xC6120000L, 15), // Benchmarking
        Ipv4Prefix(0xC6336400L, 24), // TEST-NET-2 documentation
        Ipv4Prefix(0xCB007100L, 24), // TEST-NET-3 documentation
        Ipv4Prefix(0xE0000000L, 4),  // Multicast
        Ipv4Prefix(0xF0000000L, 4)   // Reserved and limited broadcast
    )

    fun isGloballyReachable(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return false

        return when (address) {
            is Inet4Address -> isGlobalIpv4(address.address)
            is Inet6Address -> isGlobalIpv6(address.address)
            else -> false
        }
    }

    private fun isGlobalIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val value = bytes.fold(0L) { result, byte ->
            (result shl 8) or (byte.toLong() and 0xFFL)
        }
        return nonGlobalIpv4.none { prefix ->
            val mask = (0xFFFFFFFFL shl (32 - prefix.prefixLength)) and 0xFFFFFFFFL
            (value and mask) == (prefix.network and mask)
        }
    }

    private fun isGlobalIpv6(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false

        // Global unicast allocation: 2000::/3. This excludes mapped IPv4, NAT64,
        // ULA, link-local, multicast, unspecified, and other non-global address classes.
        if ((bytes[0].toInt() and 0xE0) != 0x20) return false

        // The 3f00::/8 portion remains reserved by IANA, including 3fff::/20
        // documentation space. 5f00::/16 is reserved for SRv6 SIDs and is not
        // globally reachable as a conventional Internet destination.
        if (matchesIpv6Prefix(bytes, byteArrayOf(0x3F, 0x00), 8)) return false
        if (matchesIpv6Prefix(bytes, byteArrayOf(0x5F, 0x00), 16)) return false

        // IANA special-purpose/protocol ranges and legacy transition space. Deny the
        // complete 2001::/23 block conservatively rather than trying to maintain exceptions.
        if (matchesIpv6Prefix(bytes, byteArrayOf(0x20, 0x01, 0x00), 23)) return false
        if (matchesIpv6Prefix(bytes, byteArrayOf(0x20, 0x01, 0x0D, 0xB8.toByte()), 32)) return false
        if (matchesIpv6Prefix(bytes, byteArrayOf(0x20, 0x02), 16)) return false

        return true
    }

    private fun matchesIpv6Prefix(address: ByteArray, network: ByteArray, prefixLength: Int): Boolean {
        for (bit in 0 until prefixLength) {
            val byteIndex = bit / 8
            val bitMask = 1 shl (7 - (bit % 8))
            val addressBit = address[byteIndex].toInt() and bitMask
            val networkBit = network[byteIndex].toInt() and bitMask
            if (addressBit != networkBit) return false
        }
        return true
    }
}
