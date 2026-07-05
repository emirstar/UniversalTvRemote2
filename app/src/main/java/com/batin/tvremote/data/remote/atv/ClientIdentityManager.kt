package com.batin.tvremote.data.remote.atv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

/**
 * Owns the single RSA client certificate this app presents to every Android TV it talks
 * to. The Android TV Remote Protocol authenticates the client purely by remembering
 * "have I seen this exact certificate before, after a successful PIN pairing" - so unlike
 * a normal TLS client cert, this one is deliberately generated once and kept forever
 * (inside AndroidKeyStore, never leaving secure hardware where the device supports it).
 * Regenerating it would force re-pairing with every TV the app has ever paired with.
 *
 * The pairing handshake's secret hash is computed over the RSA modulus/exponent of both
 * peers (see AtvPairingClient), which is why this must specifically be an RSA key -
 * an EC key would make that computation meaningless to the TV.
 */
@Singleton
class ClientIdentityManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    @Synchronized
    fun ensureIdentity(): KeyStore.PrivateKeyEntry {
        if (!keyStore.containsAlias(ALIAS)) {
            generateKeyPair()
        }
        return keyStore.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
    }

    fun certificate(): X509Certificate = ensureIdentity().certificate as X509Certificate

    fun publicKey(): RSAPublicKey = certificate().publicKey as RSAPublicKey

    fun privateKey(): PrivateKey = ensureIdentity().privateKey

    /** A minimal [KeyManager] array that always authenticates as our single alias. */
    fun keyManagers(): Array<KeyManager> {
        val entry = ensureIdentity()
        return arrayOf(
            SingleAliasKeyManager(
                alias = ALIAS,
                privateKey = entry.privateKey,
                chain = arrayOf(entry.certificate as X509Certificate)
            )
        )
    }

    private fun generateKeyPair() {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE)
        val notBefore = Calendar.getInstance()
        val notAfter = Calendar.getInstance().apply { add(Calendar.YEAR, 40) }
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setCertificateSubject(X500Principal("CN=$CERT_COMMON_NAME"))
            .setCertificateSerialNumber(BigInteger.valueOf(1))
            .setCertificateNotBefore(notBefore.time)
            .setCertificateNotAfter(notAfter.time)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    /**
     * A trivial [X509ExtendedKeyManager] that ignores whatever alias/issuer negotiation the
     * server proposes and always presents our one identity. Android TV boxes accept
     * self-signed client certs during the pairing/remote handshakes, so there is nothing
     * to negotiate; letting the platform's default key manager guess the alias has been
     * observed (across several AndroidKeyStore + custom CA setups) to sometimes pick
     * "none", which is why this is spelled out explicitly instead.
     */
    private class SingleAliasKeyManager(
        private val alias: String,
        private val privateKey: PrivateKey,
        private val chain: Array<X509Certificate>
    ) : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<Principal>?) = arrayOf(alias)
        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: java.net.Socket?
        ) = alias

        override fun chooseEngineClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            engine: javax.net.ssl.SSLEngine?
        ) = alias

        override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: java.net.Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate> = chain
        override fun getPrivateKey(alias: String?): PrivateKey = privateKey
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val ALIAS = "tv_remote_client_identity"
        private const val CERT_COMMON_NAME = "TvRemoteUniversal Client"
    }
}
