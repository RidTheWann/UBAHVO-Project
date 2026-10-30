package com.ridthewann.ubahvo.data

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Resolves a host once, validates the complete DNS answer set, and returns those exact
 * InetAddress instances to OkHttp's route selector. OkHttp then connects to those addresses
 * directly while retaining the original URL hostname for TLS SNI and certificate validation.
 */
internal class PublicOnlyDns(
    private val delegate: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = try {
            delegate.lookup(hostname)
        } catch (error: UnknownHostException) {
            throw error
        } catch (error: Exception) {
            throw UnknownHostException("DNS lookup failed for $hostname: ${error.message}")
        }

        if (!PublicNetworkAddressPolicy.areAllGloballyReachable(addresses)) {
            throw UnknownHostException(
                "DNS for $hostname returned an empty or non-global address set; request refused."
            )
        }
        return addresses.toList()
    }
}
