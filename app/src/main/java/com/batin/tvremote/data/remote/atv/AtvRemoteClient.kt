package com.batin.tvremote.data.remote.atv

import com.batin.tvremote.data.model.KeyPressKind
import com.batin.tvremote.data.model.RemoteKey
import com.batin.tvremote.data.remote.RemoteTransport
import com.batin.tvremote.data.remote.TransportEvent
import com.batin.tvremote.proto.remote.RemoteAppLinkLaunchRequest
import com.batin.tvremote.proto.remote.RemoteConfigure
import com.batin.tvremote.proto.remote.RemoteDeviceInfo
import com.batin.tvremote.proto.remote.RemoteDirection
import com.batin.tvremote.proto.remote.RemoteEditInfo
import com.batin.tvremote.proto.remote.RemoteImeBatchEdit
import com.batin.tvremote.proto.remote.RemoteImeObject
import com.batin.tvremote.proto.remote.RemoteKeyInject
import com.batin.tvremote.proto.remote.RemoteMessage
import com.batin.tvremote.proto.remote.RemotePingResponse
import com.batin.tvremote.proto.remote.RemoteSetActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLSocket
import kotlin.math.absoluteValue

/**
 * One AtvRemoteClient instance == one live connection to a single Android TV over the
 * Android TV Remote Protocol. RemoteControlRepositoryImpl creates a fresh instance per
 * connection attempt (Hilt provides it un-scoped, see RepositoryModule).
 */
class AtvRemoteClient @Inject constructor(
    private val identityManager: ClientIdentityManager
) : RemoteTransport {

    private var socket: SSLSocket? = null
    private var scope: CoroutineScope? = null
    private var readerJob: Job? = null
    private val writeMutex = Mutex()

    // Populated once the TV's first remote_configure tells us what it supports; must be
    // echoed back verbatim whenever the TV later asks for remote_set_active (see readLoop).
    @Volatile private var activeFeatures: Int = 0

    // Updated whenever the TV sends us a remote_ime_batch_edit of its own; sendText() must
    // reuse the latest counters the TV gave us, not start from zero every time.
    @Volatile private var imeCounter: Int = 0
    @Volatile private var imeFieldCounter: Int = 0

    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<TransportEvent> = _events

    // Touchpad-as-dpad accumulator (see sendPointerMove).
    private var accumDx = 0f
    private var accumDy = 0f

    /**
     * Opens the control-channel TLS connection. If [pinnedFingerprint] is non-null (i.e.
     * this device was paired before), the TV's certificate must match it exactly or the
     * connection is aborted - this is the only thing standing between "trust nobody" and
     * "trust whoever answers on this IP", since there is no certificate authority in this
     * protocol by design.
     */
    suspend fun connect(host: String, port: Int, pinnedFingerprint: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                var peerCert: X509Certificate? = null
                val newSocket = AtvWireProtocol.openTlsSocket(
                    host = host,
                    port = port,
                    keyManagers = identityManager.keyManagers()
                ) { cert -> peerCert = cert }

                val fingerprint = peerCert?.let(AtvWireProtocol::sha256Fingerprint)
                if (pinnedFingerprint != null && fingerprint != pinnedFingerprint) {
                    runCatching { newSocket.close() }
                    error(
                        "TV'nin sertifikası önceki eşleştirmeden farklı. Güvenlik nedeniyle " +
                            "bağlantı reddedildi; cihazı listeden silip tekrar eşleştirin."
                    )
                }

                // The TV speaks first on this channel, announcing which features it supports.
                val greeting = receive(newSocket)
                check(greeting.hasRemoteConfigure()) { "TV'den beklenmeyen ilk mesaj alındı" }

                // BUGFIX: previously this always echoed back a fixed CLIENT_CODE regardless
                // of what the TV said it supports, and unconditionally sent our own
                // remote_set_active immediately instead of waiting for (and correctly
                // replying to) the TV's own remote_set_active request in the read loop. The
                // TV may not treat the input channel as fully active - silently dropping key
                // presses - until it gets a properly negotiated reply. We now intersect our
                // desired features with what the TV advertised, exactly like every reference
                // client of this protocol, and remember the result so readLoop can echo the
                // *same* value back when remote_set_active is requested.
                activeFeatures = DESIRED_FEATURES and greeting.remoteConfigure.code1
                send(
                    newSocket,
                    RemoteMessage.newBuilder()
                        .setRemoteConfigure(
                            RemoteConfigure.newBuilder()
                                .setCode1(activeFeatures)
                                .setDeviceInfo(
                                    RemoteDeviceInfo.newBuilder()
                                        .setUnknown1(1)
                                        // BUGFIX: reference implementations always send the
                                        // literal string "1" here, not the app version. The
                                        // true meaning of this field is undocumented, so we
                                        // match the known-working value instead of guessing.
                                        .setUnknown2("1")
                                        .setPackageName(PACKAGE_NAME)
                                        .setAppVersion(APP_VERSION)
                                )
                        )
                        .build()
                )

                socket = newSocket
                val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                scope = newScope
                readerJob = newScope.launch { readLoop(newSocket) }
            }
        }

    private suspend fun readLoop(activeSocket: SSLSocket) {
        try {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val message = receive(activeSocket)
                when {
                    message.hasRemotePingRequest() -> {
                        val val1 = message.remotePingRequest.val1
                        writeMutex.withLock {
                            send(
                                activeSocket,
                                RemoteMessage.newBuilder()
                                    .setRemotePingResponse(RemotePingResponse.newBuilder().setVal1(val1))
                                    .build()
                            )
                        }
                    }

                    // BUGFIX: previously unhandled (fell through to the `else` branch below),
                    // so the TV's request to activate the input channel never got a reply.
                    // Some TVs/firmware only start delivering key presses to the foreground
                    // app once this handshake step completes correctly.
                    message.hasRemoteSetActive() -> {
                        writeMutex.withLock {
                            send(
                                activeSocket,
                                RemoteMessage.newBuilder()
                                    .setRemoteSetActive(RemoteSetActive.newBuilder().setActive(activeFeatures))
                                    .build()
                            )
                        }
                    }

                    // The TV echoes its own ime/field counters back to us periodically;
                    // sendText() must reuse the latest values it has seen, not always send 0.
                    message.hasRemoteImeBatchEdit() -> {
                        imeCounter = message.remoteImeBatchEdit.imeCounter
                        imeFieldCounter = message.remoteImeBatchEdit.fieldCounter
                    }

                    message.hasRemoteImeKeyInject() -> {
                        val pkg = message.remoteImeKeyInject.appInfo.appPackage
                        if (pkg.isNotEmpty()) {
                            _events.tryEmit(TransportEvent.CurrentAppChanged(pkg))
                        }
                    }

                    message.hasRemoteSetVolumeLevel() -> {
                        val volume = message.remoteSetVolumeLevel
                        _events.tryEmit(
                            TransportEvent.VolumeChanged(
                                level = volume.volumeLevel,
                                max = volume.volumeMax,
                                muted = volume.volumeMuted
                            )
                        )
                    }

                    else -> Unit // RemoteStart, RemoteError, voice messages, etc. - not needed by this app.
                }
            }
        } catch (_: Exception) {
            _events.tryEmit(TransportEvent.Disconnected)
        }
    }

    override suspend fun sendKey(key: RemoteKey, kind: KeyPressKind) = withContext(Dispatchers.IO) {
        val code = AndroidKeyCodeMapper.map(key)
        val direction = when (kind) {
            KeyPressKind.SHORT -> RemoteDirection.DIRECTION_SHORT
            KeyPressKind.LONG_PRESS_START -> RemoteDirection.DIRECTION_START_LONG
            KeyPressKind.LONG_PRESS_END -> RemoteDirection.DIRECTION_END_LONG
        }
        injectKey(code, direction)
    }

    // BUGFIX: previously this typed text one character at a time via RemoteKeyInject,
    // which can only replay key codes that already exist in the RemoteKeyCode enum -
    // Turkish letters like ç/ğ/ı/ö/ş/ü have no such key code and were silently dropped
    // (or transliterated). remote_ime_batch_edit instead carries the literal UTF-8 string
    // directly to the TV's IME, which is what the official app uses and supports any
    // Unicode text without needing a key code for every character.
    override suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext
        val activeSocket = socket ?: return@withContext
        val cursor = text.length - 1
        val imeObject = RemoteImeObject.newBuilder()
            .setStart(cursor)
            .setEnd(cursor)
            .setValue(text)
            .build()
        val editInfo = RemoteEditInfo.newBuilder()
            .setInsert(1)
            .setTextFieldStatus(imeObject)
            .build()
        val batchEdit = RemoteImeBatchEdit.newBuilder()
            .setImeCounter(imeCounter)
            .setFieldCounter(imeFieldCounter)
            .addEditInfo(editInfo)
            .build()
        writeMutex.withLock {
            send(activeSocket, RemoteMessage.newBuilder().setRemoteImeBatchEdit(batchEdit).build())
        }
    }

    /**
     * There is no confirmed "move a pointer" message in this protocol, so touchpad
     * gestures are approximated as discrete D-pad presses: dx/dy accumulate until they
     * cross [STEP_THRESHOLD_DP], at which point one D-pad press fires in the dominant
     * axis and the accumulator resets. This trades pixel-perfect pointer control for
     * something that reliably works with every Android TV launcher's focus navigation.
     */
    override suspend fun sendPointerMove(dx: Float, dy: Float) = withContext(Dispatchers.IO) {
        accumDx += dx
        accumDy += dy
        if (accumDx.absoluteValue >= accumDy.absoluteValue) {
            if (accumDx.absoluteValue >= STEP_THRESHOLD_DP) {
                sendKey(if (accumDx > 0) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT)
                accumDx = 0f
                accumDy = 0f
            }
        } else if (accumDy.absoluteValue >= STEP_THRESHOLD_DP) {
            sendKey(if (accumDy > 0) RemoteKey.DPAD_DOWN else RemoteKey.DPAD_UP)
            accumDx = 0f
            accumDy = 0f
        }
    }

    override suspend fun sendPointerClick() = sendKey(RemoteKey.DPAD_CENTER)

    override suspend fun launchApp(target: String): Boolean = withContext(Dispatchers.IO) {
        val activeSocket = socket ?: return@withContext false
        writeMutex.withLock {
            send(
                activeSocket,
                RemoteMessage.newBuilder()
                    .setRemoteAppLinkLaunchRequest(RemoteAppLinkLaunchRequest.newBuilder().setAppLink(target))
                    .build()
            )
        }
        true
    }

    private suspend fun injectKey(code: com.batin.tvremote.proto.remote.RemoteKeyCode, direction: RemoteDirection) {
        val activeSocket = socket ?: return
        writeMutex.withLock {
            send(
                activeSocket,
                RemoteMessage.newBuilder()
                    .setRemoteKeyInject(RemoteKeyInject.newBuilder().setKeyCode(code).setDirection(direction))
                    .build()
            )
        }
    }

    override fun disconnect() {
        readerJob?.cancel()
        scope?.cancel()
        runCatching { socket?.close() }
        socket = null
        accumDx = 0f
        accumDy = 0f
    }

    companion object {
        // Feature bits from the RemoteConfigure.code1 bitmask (cross-checked against
        // captured traces, same values used by every reference client of this protocol).
        // Voice search is deliberately excluded: it requires streaming microphone audio to
        // the TV, out of scope for a button/touch remote.
        private const val FEATURE_PING = 1 shl 0
        private const val FEATURE_KEY = 1 shl 1
        private const val FEATURE_IME = 1 shl 2
        private const val FEATURE_POWER = 1 shl 5
        private const val FEATURE_VOLUME = 1 shl 6
        private const val FEATURE_APP_LINK = 1 shl 9
        private const val DESIRED_FEATURES =
            FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK

        private const val PACKAGE_NAME = "com.batin.tvremote"
        private const val APP_VERSION = "1.0.0"
        private const val STEP_THRESHOLD_DP = 24f

        private fun send(socket: SSLSocket, message: RemoteMessage) {
            AtvWireProtocol.writeDelimitedMessage(socket.outputStream, message.toByteArray())
        }

        private fun receive(socket: SSLSocket): RemoteMessage {
            val bytes = AtvWireProtocol.readDelimitedMessage(socket.inputStream)
            return RemoteMessage.parseFrom(bytes)
        }
    }
}
