package com.ridthewann.ubahvo.data

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Connects HTTPS to an already-validated DNS answer without asking the resolver again.
 *
 * The TCP socket is opened against a literal InetAddress from the validated answer set. TLS
 * is then layered using the original hostname for SNI and HTTPS endpoint identification, so
 * pinning the network destination does not disable certificate or hostname verification.
 */
internal class PinnedHttpsSocketFactory(
    private val hostname: String,
    addresses: List<InetAddress>,
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int
) : SSLSocketFactory() {
    private val approvedAddresses = addresses.toList()
    private val delegate = SSLSocketFactory.getDefault() as SSLSocketFactory

    init {
        require(hostname.isNotBlank())
        require(approvedAddresses.isNotEmpty())
        require(approvedAddresses.all(PublicNetworkAddressPolicy::isGloballyReachable))
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(host: String, port: Int): Socket {
        requireExpectedPeer(host, port)
        return connectToApprovedAddress(port, null, 0)
    }

    override fun createSocket(
        host: String,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket {
        requireExpectedPeer(host, port)
        return connectToApprovedAddress(port, localAddress, localPort)
    }

    override fun createSocket(address: InetAddress, port: Int): Socket {
        requireApprovedAddress(address)
        requireHttpsPort(port)
        return connectAndWrap(address, port, null, 0)
    }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket {
        requireApprovedAddress(address)
        requireHttpsPort(port)
        return connectAndWrap(address, port, localAddress, localPort)
    }

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        requireExpectedPeer(host, port)
        val peerAddress = socket.inetAddress
        if (!socket.isConnected || socket.isClosed || peerAddress == null) {
            throw IOException("Pinned HTTPS requires an already-connected socket.")
        }
        requireApprovedAddress(peerAddress)
        return wrapTls(socket, port, autoClose)
    }

    override fun createSocket(): Socket {
        throw IOException("Pinned HTTPS does not allow an unbound socket.")
    }

    private fun connectToApprovedAddress(
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket {
        requireHttpsPort(port)
        val deadlineNanos = System.nanoTime() + connectTimeoutMs * 1_000_000L
        var lastFailure: IOException? = null

        for (address in approvedAddresses) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) break
            val remainingMs = (remainingNanos / 1_000_000L).coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            try {
                return connectAndWrap(address, port, localAddress, localPort, remainingMs)
            } catch (error: IOException) {
                lastFailure = error
            }
        }

        throw IOException(
            "Could not connect to any validated public address for $hostname.",
            lastFailure
        )
    }

    private fun connectAndWrap(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
        timeoutMs: Int = connectTimeoutMs
    ): Socket {
        val rawSocket = Socket()
        try {
            if (localAddress != null || localPort != 0) {
                rawSocket.bind(InetSocketAddress(localAddress, localPort))
            }
            rawSocket.tcpNoDelay = true
            rawSocket.connect(InetSocketAddress(address, port), timeoutMs)
            rawSocket.soTimeout = readTimeoutMs
            return wrapTls(rawSocket, port, true)
        } catch (error: Exception) {
            runCatching { rawSocket.close() }
            if (error is IOException) throw error
            throw IOException("Could not open the pinned HTTPS connection.", error)
        }
    }

    private fun wrapTls(socket: Socket, port: Int, autoClose: Boolean): Socket {
        val sslSocket = delegate.createSocket(socket, hostname, port, autoClose) as? SSLSocket
            ?: throw IOException("The platform did not create an SSL socket.")
        try {
            val parameters: SSLParameters = sslSocket.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            parameters.serverNames = listOf(SNIHostName(hostname))
            sslSocket.sslParameters = parameters
            return sslSocket
        } catch (error: Exception) {
            runCatching { sslSocket.close() }
            if (error is IOException) throw error
            throw IOException("Could not configure hostname-verified TLS.", error)
        }
    }

    private fun requireExpectedPeer(host: String, port: Int) {
        if (!host.equals(hostname, ignoreCase = true)) {
            throw IOException("Pinned HTTPS refused an unexpected hostname.")
        }
        requireHttpsPort(port)
    }

    private fun requireHttpsPort(port: Int) {
        if (port != 443) throw IOException("Pinned HTTPS only permits the standard HTTPS port.")
    }

    private fun requireApprovedAddress(address: InetAddress) {
        if (approvedAddresses.none { it.address.contentEquals(address.address) }) {
            throw IOException("Pinned HTTPS refused an address that was not in the validated DNS answer.")
        }
    }
}
