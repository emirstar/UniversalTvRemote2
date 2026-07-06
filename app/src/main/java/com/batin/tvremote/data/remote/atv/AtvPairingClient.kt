package com.batin.tvremote.data.remote.atv

import android.os.Build
import com.batin.tvremote.proto.pairing.EncodingType
import com.batin.tvremote.proto.pairing.PairingConfiguration
import com.batin.tvremote.proto.pairing.PairingEncoding
import com.batin.tvremote.proto.pairing.PairingMessage
import com.batin.tvremote.proto.pairing.PairingOption
import com.batin.tvremote.proto.pairing.PairingRequest
import com.batin.tvremote.proto.pairing.PairingSecret
import com.batin.tvremote.proto.pairing.RoleType
import com.batin.tvremote.proto.pairing.Status
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject
import javax.net.ssl.SSLSocket

/**
 * Drives the pairing handshake described in pairing.proto. A successful call to
 * [beginPairing] leaves a live TLS socket open (wrapped in [AtvPairingSession]) and the TV
 * showing a 6-character code on screen; [AtvPairingSession.submitCode] finishes the
 * handshake once the user types that code in.
 */
class AtvPairingClient @Inject constructor(
    private val identityManager: ClientIdentityManager
) {

    suspend fun beginPairing(host: String, port: Int): AtvPairingSession = withContext(Dispatchers.IO) {
        val keyManagers = identityManager.keyManagers()
        var serverCertificate: X509Certificate? = null
        val socket = AtvWireProtocol.openTlsSocket(host, port, keyManagers) { cert ->
            serverCertificate = cert
        }
        try {
            send(
                socket,
                PairingMessage.newBuilder()
                    .setProtocolVersion(PROTOCOL_VERSION)
                    .setStatus(Status.STATUS_OK)
                    .setPairingRequest(
                        PairingRequest.newBuilder()
                            .setServiceName(SERVICE_NAME)
                            .setClientName(clientDisplayName())
                    )
                    .build()
            )
            val requestAck = receive(socket)
            check(requestAck.status == Status.STATUS_OK) {
                "TV eşleştirme isteğini reddetti (durum=${requestAck.status})"
            }

            val encoding = PairingEncoding.newBuilder()
                .setType(EncodingType.ENCODING_TYPE_HEXADECIMAL)
                .setSymbolLength(CODE_LENGTH)
                .build()

            send(
                socket,
                PairingMessage.newBuilder()
                    .setProtocolVersion(PROTOCOL_VERSION)
                    .setStatus(Status.STATUS_OK)
                    .setPairingOption(
                        PairingOption.newBuilder()
                            .setInputEncoding(encoding)
                            .setPreferredRole(RoleType.ROLE_TYPE_INPUT)
                    )
                    .build()
            )
            val optionAck = receive(socket)
            check(optionAck.status == Status.STATUS_OK) { "TV, eşleştirme seçeneklerini kabul etmedi" }

            send(
                socket,
                PairingMessage.newBuilder()
                    .setProtocolVersion(PROTOCOL_VERSION)
                    .setStatus(Status.STATUS_OK)
                    .setPairingConfiguration(
                        PairingConfiguration.newBuilder()
                            .setEncoding(encoding)
                            .setClientRole(RoleType.ROLE_TYPE_INPUT)
                    )
                    .build()
            )
            val configAck = receive(socket)
            check(configAck.status == Status.STATUS_OK) { "TV, yapılandırmayı kabul etmedi" }

            val certificate = serverCertificate
                ?: error("TV'nin sertifikası okunamadı; bağlantı beklenmedik şekilde kapandı")

            AtvPairingSession(socket, certificate, identityManager.publicKey())
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    private fun clientDisplayName(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        return "$model ($APP_LABEL)"
    }

    companion object {
        internal const val PROTOCOL_VERSION = 2
        internal const val CODE_LENGTH = 6
        private const val SERVICE_NAME = "com.batin.tvremote"
        private const val APP_LABEL = "TV Uzaktan Kumanda"

        internal fun send(socket: SSLSocket, message: PairingMessage) {
            AtvWireProtocol.writeDelimitedMessage(socket.outputStream, message.toByteArray())
        }

        internal fun receive(socket: SSLSocket): PairingMessage {
            val bytes = AtvWireProtocol.readDelimitedMessage(socket.inputStream)
            return PairingMessage.parseFrom(bytes)
        }
    }
}

/**
 * A pairing handshake that has reached the "TV is showing a code" step. Call
 * [submitCode] exactly once with what the user typed; either outcome closes the
 * underlying socket, since the remote-control channel (port 6466) always opens a fresh
 * connection of its own.
 */
class AtvPairingSession internal constructor(
    private val socket: SSLSocket,
    private val serverCertificate: X509Certificate,
    private val clientPublicKey: RSAPublicKey
) : Closeable {

    /** On success, returns the SHA-256 fingerprint of the TV's certificate to pin for future connections. */
    suspend fun submitCode(rawCode: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val code = rawCode.trim().uppercase()
            require(code.length == AtvPairingClient.CODE_LENGTH) { "Kod ${AtvPairingClient.CODE_LENGTH} karakter olmalı" }

            val serverPublicKey = serverCertificate.publicKey as? RSAPublicKey
                ?: error("TV, RSA tabanlı bir sertifika sunmuyor; bu istemci eşleştiremiyor")

            val mySecret = computePairingSecret(clientPublicKey, serverPublicKey, code)

            // Fast local sanity check (matches every reference implementation of this
            // protocol): the code's first byte is itself derived from this same hash by
            // the TV, so if it doesn't match here the user almost certainly mistyped the
            // code - no need to round-trip to the TV to find that out.
            val expectedFirstByte = code.substring(0, 2).toInt(16)
            val actualFirstByte = mySecret[0].toInt() and 0xFF
            if (actualFirstByte != expectedFirstByte) {
                error("Kod hatalı görünüyor. TV ekranındaki kodu kontrol edip tekrar deneyin.")
            }

            AtvPairingClient.send(
                socket,
                PairingMessage.newBuilder()
                    .setProtocolVersion(AtvPairingClient.PROTOCOL_VERSION)
                    .setStatus(Status.STATUS_OK)
                    .setPairingSecret(PairingSecret.newBuilder().setSecret(ByteString.copyFrom(mySecret)))
                    .build()
            )

            val response = AtvPairingClient.receive(socket)
            // BUGFIX: the previous version compared `response.pairingSecret.secret` against
            // our own hash, but the server's reply populates `pairing_secret_ack` (field 41),
            // not `pairing_secret` (field 40) - see pairing.proto. `pairingSecret` on the
            // response was always empty, so this comparison failed on every single pairing
            // attempt regardless of whether the code was correct. The reference
            // implementations of this protocol only check the status code here; they do not
            // re-verify the hash (the TV has already validated it - that's what determined
            // the status).
            check(response.status == Status.STATUS_OK) {
                "TV, girilen kodu reddetti. Kodu kontrol edip tekrar deneyin."
            }
            check(response.hasPairingSecretAck()) {
                "Beklenmeyen yanıt: TV'den pairing_secret_ack bekleniyordu"
            }

            AtvWireProtocol.sha256Fingerprint(serverCertificate)
        }.also {
            close()
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

/**
 * SHA-256(client modulus || client exponent || server modulus || server exponent || nonce).
 * The nonce is the last 4 hex characters of the 6-character code the TV displays (the
 * first 2 characters are not part of the hashed material). Both sides must agree on this
 * exact byte layout - see AtvWireProtocol.stripLeadingZero for the BigInteger sign-byte
 * normalization this depends on.
 */
private fun computePairingSecret(
    clientKey: RSAPublicKey,
    serverKey: RSAPublicKey,
    code: String
): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(AtvWireProtocol.stripLeadingZero(clientKey.modulus.toByteArray()))
    digest.update(AtvWireProtocol.stripLeadingZero(clientKey.publicExponent.toByteArray()))
    digest.update(AtvWireProtocol.stripLeadingZero(serverKey.modulus.toByteArray()))
    digest.update(AtvWireProtocol.stripLeadingZero(serverKey.publicExponent.toByteArray()))
    digest.update(hexToBytes(code.substring(2, 6)))
    return digest.digest()
}

private fun hexToBytes(hex: String): ByteArray {
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(hex[i * 2], 16)
        val lo = Character.digit(hex[i * 2 + 1], 16)
        out[i] = ((hi shl 4) + lo).toByte()
    }
    return out
}
