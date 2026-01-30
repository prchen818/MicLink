package com.miclink.webrtc

import android.content.Context
import android.util.Log
import com.miclink.model.AudioQuality
import com.miclink.model.ConnectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WebRTC管理器 - 核心音视频通信引擎
 */
class WebRtcManager(
    private val context: Context,
    private val iceServers: List<PeerConnection.IceServer>
) {
    private val TAG = "WebRtcManager"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null

    private val _connectionState = MutableStateFlow<PeerConnection.PeerConnectionState>(
        PeerConnection.PeerConnectionState.NEW
    )
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState

    private val _iceConnectionState = MutableStateFlow<PeerConnection.IceConnectionState>(
        PeerConnection.IceConnectionState.NEW
    )
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState> = _iceConnectionState

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private var onIceCandidateListener: ((IceCandidate) -> Unit)? = null
    private var onConnectionTypeListener: ((String) -> Unit)? = null

    /**
     * 初始化WebRTC
     */
    fun initialize() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(null, false, false)
        val decoderFactory = DefaultVideoDecoderFactory(null)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }

    /**
     * 创建PeerConnection
     */
    fun createPeerConnection(
        connectionMode: ConnectionMode,
        audioQuality: AudioQuality,
        onIceCandidate: (IceCandidate) -> Unit,
        onConnectionType: (String) -> Unit
    ) {
        this.onIceCandidateListener = onIceCandidate
        this.onConnectionTypeListener = onConnectionType

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

            // 根据连接模式设置ICE传输类型
            iceTransportsType = when (connectionMode) {
                ConnectionMode.P2P_ONLY -> PeerConnection.IceTransportsType.NOHOST
                ConnectionMode.RELAY_ONLY -> PeerConnection.IceTransportsType.RELAY
                ConnectionMode.AUTO -> PeerConnection.IceTransportsType.ALL
            }
        }

        val observer = object : PeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "onIceCandidate: ${it.sdp}")
                    onIceCandidateListener?.invoke(it)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $newState")
                newState?.let {
                    _iceConnectionState.value = it

                    // 检测连接类型
                    when (it) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            Log.d(TAG, "ICE connection successful!")
                            checkConnectionType()
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            Log.e(TAG, "ICE connection failed!")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            Log.w(TAG, "ICE connection disconnected")
                        }
                        else -> {
                            Log.d(TAG, "ICE connection state: $it")
                        }
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "onConnectionChange: $newState")
                newState?.let { _connectionState.value = it }
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        // 创建音频轨道
        createAudioTrack(audioQuality)
    }

    /**
     * 创建音频轨道
     */
    private fun createAudioTrack(quality: AudioQuality) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            
            // 设置码率
            when (quality) {
                AudioQuality.LOW -> {
                    mandatory.add(MediaConstraints.KeyValuePair("maxaveragebitrate", "32000"))
                }
                AudioQuality.MEDIUM -> {
                    mandatory.add(MediaConstraints.KeyValuePair("maxaveragebitrate", "64000"))
                }
                AudioQuality.HIGH -> {
                    mandatory.add(MediaConstraints.KeyValuePair("maxaveragebitrate", "128000"))
                }
            }
        }

        audioSource = peerConnectionFactory?.createAudioSource(constraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)

        peerConnection?.addTrack(localAudioTrack, listOf("stream_id"))
    }

    /**
     * 创建Offer (发起方)
     */
    suspend fun createOffer(): String? {
        return suspendCancellableCoroutine { continuation ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            continuation.resume(sdp.description)
                        }
                        override fun onSetFailure(error: String) {
                            continuation.resumeWithException(Exception(error))
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }

                override fun onCreateFailure(error: String) {
                    continuation.resumeWithException(Exception(error))
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)
        }
    }

    /**
     * 创建Answer (接收方)
     */
    suspend fun createAnswer(): String? {
        return suspendCancellableCoroutine { continuation ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            continuation.resume(sdp.description)
                        }
                        override fun onSetFailure(error: String) {
                            continuation.resumeWithException(Exception(error))
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }

                override fun onCreateFailure(error: String) {
                    continuation.resumeWithException(Exception(error))
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)
        }
    }

    /**
     * 设置远程SDP
     */
    suspend fun setRemoteDescription(sdp: String, type: String) {
        return suspendCancellableCoroutine { continuation ->
            val sdpType = when (type) {
                "offer" -> SessionDescription.Type.OFFER
                "answer" -> SessionDescription.Type.ANSWER
                else -> SessionDescription.Type.OFFER
            }
            val sessionDescription = SessionDescription(sdpType, sdp)

            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    continuation.resumeWithException(Exception(error))
                }

                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, sessionDescription)
        }
    }

    /**
     * 添加ICE候选
     */
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * 切换静音
     */
    fun toggleMute() {
        localAudioTrack?.setEnabled(_isMuted.value)
        _isMuted.value = !_isMuted.value
    }

    /**
     * 设置静音状态
     */
    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        _isMuted.value = muted
    }

    /**
     * 检测连接类型 (P2P or Relay)
     */
    private fun checkConnectionType() {
        peerConnection?.let { pc ->
            // 通过统计信息判断连接类型
            pc.getStats { report ->
                report.statsMap.values.forEach { stats ->
                    if (stats.type == "candidate-pair" && 
                        stats.members["state"] == "succeeded") {
                        
                        val localCandidateId = stats.members["localCandidateId"] as? String
                        val remoteCandidateId = stats.members["remoteCandidateId"] as? String
                        
                        // 查找本地和远程候选的类型
                        report.statsMap.values.forEach { candidateStats ->
                            if (candidateStats.id == localCandidateId || 
                                candidateStats.id == remoteCandidateId) {
                                
                                val candidateType = candidateStats.members["candidateType"] as? String
                                val connectionType = when (candidateType) {
                                    "relay" -> "relay"
                                    "host", "srflx", "prflx" -> "p2p"
                                    else -> "unknown"
                                }
                                
                                if (connectionType != "unknown") {
                                    onConnectionTypeListener?.invoke(connectionType)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 重启ICE连接（通过触发重新协商）
     * 注意：实际的ICE重启需要通过信令重新协商
     */
    fun restartIce() {
        // Android WebRTC 不直接暴露 restartIce() 方法
        // ICE 重启需要通过创建新的 offer 并设置 iceRestart 选项
        // 这里记录请求，实际重启通过信令层处理
        Log.d(TAG, "ICE restart requested - will be handled via renegotiation")
    }

    /**
     * 关闭连接
     */
    fun close() {
        localAudioTrack?.dispose()
        audioSource?.dispose()
        peerConnection?.close()
        peerConnection = null
    }

    /**
     * 释放资源
     */
    fun dispose() {
        close()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
}

/**
 * PeerConnection观察者基类
 */
abstract class PeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(dataChannel: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
}

// Kotlin协程扩展
suspend fun <T> suspendCancellableCoroutine(
    block: (kotlin.coroutines.Continuation<T>) -> Unit
): T = kotlinx.coroutines.suspendCancellableCoroutine(block)
