package com.batin.tvremote.data.remote.atv

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Both the pairing channel (port 6467) and the remote-control channel (port 6466) frame
 * every protobuf message the same way: a protobuf-style unsigned varint holding the
 * message's byte length, immediately followed by that many bytes of serialized message.
 * This mirrors how nested/length-delimited fields are framed inside protobuf itself, and
 * matches every independent implementation of this protocol that has published its wire
 * traces.
 */
object AtvWireProtocol {

    fun writeDelimitedMessage(output: OutputStream, message: ByteArray) {
        writeRawVarint32(output, message.size)
        output.write(message)
        output.flush()
    }

    @Throws(IOException::class)
    fun readDelimitedMessage(input: InputStream): ByteArray {
        val length = readRawVarint32(input)
        if (length < 0 || length > MAX_MESSAGE_SIZE) {
            throw IOException("Refusing to read implausible message length: $length")
        }
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) throw EOFException("Stream closed after $offset/$length bytes")
            offset += read
        }
        return buffer
    }

    fun writeRawVarint32(output: OutputStream, valueIn: Int) {
        var value = valueIn
        while (true) {
            if (value and 0x7F.inv() == 0) {
                output.write(value)
                return
            } else {
                output.write((value and 0x7F) or 0x80)
                value = value ushr 7
            }
        }
    }

    @Throws(IOException::class)
    fun readRawVarint32(input: InputStream): Int {
        var result = 0
        var shift = 0
        while (shift < 32) {
            val b = input.read()
            if (b == -1) {
                if (shift == 0) throw EofOnFirstByte()
                throw EOFException("Stream closed mid-varint")
            }
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw IOException("Malformed varint (too many continuation bytes)")
    }

    /** Thrown when the peer closes the socket cleanly between messages; callers treat this as "disconnected", not a protocol error. */
    class EofOnFirstByte : IOException("Peer closed the connection")

    private const val MAX_MESSAGE_SIZE = 1 shl 20 // 1 MiB safety ceiling; real messages are a few dozen bytes

    /**
     * Opens a mutually-authenticated TLS socket. There is deliberately no certificate
     * authority here: Android TV boxes present a self-signed certificate and so does this
     * app (see [ClientIdentityManager]). Trust is established out of band, either by the
     * user typing in the on-screen pairing code (first connection) or by this app pinning
     * the exact certificate it saw during that first pairing (every connection after).
     * [onPeerCertificate] is invoked synchronously during the handshake so the caller can
     * capture/verify the fingerprint before any application data is exchanged.
     */
    fun openTlsSocket(
        host: String,
        port: Int,
        keyManagers: Array<KeyManager>,
        connectTimeoutMs: Int = 10_000,
        onPeerCertificate: (X509Certificate) -> Unit = {}
    ): SSLSocket {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                    ?: throw javax.net.ssl.SSLPeerUnverifiedException("TV presented no certificate")
                onPeerCertificate(leaf)
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers, arrayOf(trustManager), SecureRandom())

        val rawSocket = Socket()
        rawSocket.connect(InetSocketAddress(host, port), connectTimeoutMs)

        val sslSocket = sslContext.socketFactory.createSocket(rawSocket, host, port, true) as SSLSocket
        sslSocket.useClientMode = true
        sslSocket.soTimeout = SOCKET_READ_TIMEOUT_MS
        sslSocket.startHandshake()
        return sslSocket
    }

    fun sha256Fingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString(separator = ":") { byte -> "%02X".format(byte) }
    }

    /**
     * Strips a leading 0x00 sign byte from a two's-complement [java.math.BigInteger]
     * encoding. Both this client and the TV must agree on this normalization or the
     * pairing secret hash (computed over raw modulus/exponent bytes) will never match,
     * even when the underlying numeric values are identical.
     */
    fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes

    private const val SOCKET_READ_TIMEOUT_MS = 15_000
}
