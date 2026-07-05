package com.batin.tvremote.data.remote.atv

import com.batin.tvremote.data.model.KeyPressKind
import com.batin.tvremote.data.model.RemoteKey
import com.batin.tvremote.data.remote.RemoteTransport
import com.batin.tvremote.data.remote.TransportEvent
import com.batin.tvremote.proto.remote.RemoteAppLinkLaunchRequest
import com.batin.tvremote.proto.remote.RemoteConfigure
import com.batin.tvremote.proto.remote.RemoteDeviceInfo
import com.batin.tvremote.proto.remote.RemoteDirection
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

                // The TV speaks first on this channel, announcing itself.
                val greeting = receive(newSocket)
                check(greeting.hasRemoteConfigure()) { "TV'den beklenmeyen ilk mesaj alındı" }

                send(
                    newSocket,
                    RemoteMessage.newBuilder()
                        .setRemoteConfigure(
                            RemoteConfigure.newBuilder()
                                .setCode1(CLIENT_CODE)
                                .setDeviceInfo(
                                    RemoteDeviceInfo.newBuilder()
                                        .setUnknown1(1)
                                        .setUnknown2(APP_VERSION)
                                        .setPackageName(PACKAGE_NAME)
                                        .setAppVersion(APP_VERSION)
                                )
                        )
                        .build()
                )
                send(
                    newSocket,
                    RemoteMessage.newBuilder()
                        .setRemoteSetActive(RemoteSetActive.newBuilder().setActive(1))
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

    override suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        for (character in text) {
            val (code, needsShift) = AndroidKeyCodeMapper.mapChar(character) ?: continue
            if (needsShift) injectKey(com.batin.tvremote.proto.remote.RemoteKeyCode.KEYCODE_SHIFT_LEFT, RemoteDirection.DIRECTION_START_LONG)
            injectKey(code, RemoteDirection.DIRECTION_SHORT)
            if (needsShift) injectKey(com.batin.tvremote.proto.remote.RemoteKeyCode.KEYCODE_SHIFT_LEFT, RemoteDirection.DIRECTION_END_LONG)
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
        private const val CLIENT_CODE = 622
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
