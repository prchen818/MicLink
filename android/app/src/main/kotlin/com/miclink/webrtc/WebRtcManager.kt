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
 * ICE 连接诊断信息数据类
 */
data class IceDiagnosis(
    val iceConnectionState: PeerConnection.IceConnectionState,
    val iceGatheringState: PeerConnection.IceGatheringState,
    val signalingState: PeerConnection.SignalingState,
    val totalCandidates: Int,
    val hasRelay: Boolean,
    val hasHost: Boolean,
    val hasSrflx: Boolean,
    val stats: Map<String, String>
) {
    override fun toString(): String {
        return """
            ┌─ ICE 诊断信息 ─────────────────┐
            │ ICE连接状态: $iceConnectionState
            │ ICE收集状态: $iceGatheringState
            │ 信令状态: $signalingState
            │ 总候选数: $totalCandidates
            │ ├─ 中转候选(RELAY): $hasRelay
            │ ├─ 本地候选(HOST): $hasHost
            │ └─ P2P候选(SRFLX): $hasSrflx
            │ 详细信息: $stats
            └────────────────────────────────┘
        """.trimIndent()
    }
}

/**
 * WebRTC管理器 - 核心音视频通信引擎
 */
/**
 * WebRTC 资源生命周期状态
 */
private enum class ResourceState {
    UNINITIALIZED,    // 未初始化
    INITIALIZED,      // 已初始化
    CONNECTED,        // 已连接（轨道已创建）
    CLOSED,           // 已关闭（连接和轨道已关闭）
    DISPOSED          // 已释放（所有资源已释放）
}

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
    
    // 资源生命周期状态机
    private var resourceState = ResourceState.UNINITIALIZED

    private val _connectionState = MutableStateFlow<PeerConnection.PeerConnectionState>(
        PeerConnection.PeerConnectionState.NEW
    )
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState

    private val _iceConnectionState = MutableStateFlow<PeerConnection.IceConnectionState>(
        PeerConnection.IceConnectionState.NEW
    )
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState> = _iceConnectionState

    private val _iceGatheringState = MutableStateFlow<PeerConnection.IceGatheringState>(
        PeerConnection.IceGatheringState.NEW
    )
    val iceGatheringState: StateFlow<PeerConnection.IceGatheringState> = _iceGatheringState

    private val _signalingState = MutableStateFlow<PeerConnection.SignalingState>(
        PeerConnection.SignalingState.STABLE
    )
    val signalingState: StateFlow<PeerConnection.SignalingState> = _signalingState

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private var onIceCandidateListener: ((IceCandidate) -> Unit)? = null
    private var onConnectionTypeListener: ((String) -> Unit)? = null

    /**
     * 初始化WebRTC
     */
    fun initialize() {
        if (resourceState != ResourceState.UNINITIALIZED) {
            Log.d(TAG, "WebRTC already initialized (state: $resourceState)")
            return
        }
        
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
        
        resourceState = ResourceState.INITIALIZED
        Log.d(TAG, "WebRTC initialized successfully")
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

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "onIceGatheringChange: $newState")
                newState?.let {
                    _iceGatheringState.value = it
                    if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                        Log.d(TAG, "ICE gathering completed!")
                    }
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
                        PeerConnection.IceConnectionState.CLOSED -> {
                            // RELAY_ONLY模式下可能出现CLOSED状态
                            // 这通常表示TURN服务器不可用或连接失败
                            Log.w(TAG, "ICE connection closed (TURN server may be unavailable)")
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

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                Log.d(TAG, "onSignalingChange: $newState")
                newState?.let { _signalingState.value = it }
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
        
        // 标记已连接状态（轨道已创建）
        resourceState = ResourceState.CONNECTED
        Log.d(TAG, "Audio track created and added to connection")
    }

    /**
     * 创建Offer (发起方)
     * 使用 Trickle ICE：立即返回 SDP，ICE 候选通过回调单独发送
     */
    suspend fun createOffer(): String? {
        return suspendCancellableCoroutine { continuation ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            var isResumed = false

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set successfully")
                            if (!isResumed) {
                                isResumed = true
                                // 立即返回 SDP，不等待 ICE gathering 完成（Trickle ICE）
                                continuation.resume(sdp.description)
                            }
                        }
                        override fun onSetFailure(error: String) {
                            Log.e(TAG, "Failed to set local description: $error")
                            if (!isResumed) {
                                isResumed = true
                                continuation.resumeWithException(Exception(error))
                            }
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }

                override fun onCreateFailure(error: String) {
                    Log.e(TAG, "Failed to create offer: $error")
                    if (!isResumed) {
                        isResumed = true
                        continuation.resumeWithException(Exception(error))
                    }
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)
        }
    }

    /**
     * 创建Answer (接收方)
     * 使用 Trickle ICE：立即返回 SDP，ICE 候选通过回调单独发送
     */
    suspend fun createAnswer(): String? {
        return suspendCancellableCoroutine { continuation ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            var isResumed = false

            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description (answer) set successfully")
                            if (!isResumed) {
                                isResumed = true
                                // 立即返回 SDP，不等待 ICE gathering 完成（Trickle ICE）
                                continuation.resume(sdp.description)
                            }
                        }
                        override fun onSetFailure(error: String) {
                            Log.e(TAG, "Failed to set local description (answer): $error")
                            if (!isResumed) {
                                isResumed = true
                                continuation.resumeWithException(Exception(error))
                            }
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }

                override fun onCreateFailure(error: String) {
                    Log.e(TAG, "Failed to create answer: $error")
                    if (!isResumed) {
                        isResumed = true
                        continuation.resumeWithException(Exception(error))
                    }
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
    /**
     * 诊断 ICE 连接状态 - 用于调试 TURN 连接失败
     * 返回详细的诊断信息
     */
    fun diagnoseIceConnection(): IceDiagnosis {
        val stats = mutableMapOf<String, String>()
        var hasRelayCandidate = false
        var hasHostCandidate = false
        var hasSrflxCandidate = false
        var totalCandidates = 0

        peerConnection?.let { pc ->
            pc.getStats { report ->
                report.statsMap.values.forEach { stat ->
                    totalCandidates++
                    
                    val type = stat.type
                    val candidateType = stat.members["candidateType"] as? String
                    val address = stat.members["address"] as? String
                    val port = stat.members["port"] as? Int
                    val priority = stat.members["priority"] as? String
                    
                    when (candidateType) {
                        "relay" -> {
                            hasRelayCandidate = true
                            stats["relay_address"] = address ?: "unknown"
                            stats["relay_port"] = port?.toString() ?: "unknown"
                            Log.d(TAG, "Found RELAY candidate: $address:$port (priority: $priority)")
                        }
                        "host" -> {
                            hasHostCandidate = true
                            stats["host_address"] = address ?: "unknown"
                            Log.d(TAG, "Found HOST candidate: $address")
                        }
                        "srflx" -> {
                            hasSrflxCandidate = true
                            stats["srflx_address"] = address ?: "unknown"
                            stats["srflx_port"] = port?.toString() ?: "unknown"
                            Log.d(TAG, "Found SRFLX (P2P) candidate: $address:$port")
                        }
                        else -> {
                            Log.d(TAG, "Found candidate: type=$candidateType, address=$address")
                        }
                    }
                }
            }
        }

        stats["total_candidates"] = totalCandidates.toString()
        stats["has_relay"] = hasRelayCandidate.toString()
        stats["has_host"] = hasHostCandidate.toString()
        stats["has_srflx"] = hasSrflxCandidate.toString()
        stats["ice_connection_state"] = _iceConnectionState.value.toString()
        stats["ice_gathering_state"] = _iceGatheringState.value.toString()
        stats["signaling_state"] = _signalingState.value.toString()

        val diagnosis = IceDiagnosis(
            iceConnectionState = _iceConnectionState.value,
            iceGatheringState = _iceGatheringState.value,
            signalingState = _signalingState.value,
            totalCandidates = totalCandidates,
            hasRelay = hasRelayCandidate,
            hasHost = hasHostCandidate,
            hasSrflx = hasSrflxCandidate,
            stats = stats
        )

        Log.d(TAG, "ICE Diagnosis: $diagnosis")
        return diagnosis
    }
    
    /**
     * 关闭连接和音频轨道
     * 只在 CONNECTED 状态执行，避免重复释放
     */
    fun close() {
        // 状态检查：只在 CONNECTED 状态时执行
        if (resourceState !in listOf(ResourceState.INITIALIZED, ResourceState.CONNECTED)) {
            Log.d(TAG, "Cannot close in state: $resourceState")
            return
        }
        
        // 依次释放资源，顺序很重要
        // 1. 关闭对等连接（停止数据流）
        peerConnection?.close()
        peerConnection = null
        
        // 2. 释放音频轨道（调用 dispose 前必须从连接中移除）
        localAudioTrack = null
        
        // 3. 释放音频源
        audioSource = null
        
        resourceState = ResourceState.CLOSED
        Log.d(TAG, "Connection closed, resources released")
    }

    /**
     * 释放工厂资源
     * 只在 CLOSED 状态执行，确保连接已关闭
     */
    fun dispose() {
        // 状态检查：如果已释放则直接返回
        if (resourceState == ResourceState.DISPOSED) {
            Log.d(TAG, "Already disposed")
            return
        }
        
        // 确保先关闭连接
        if (resourceState != ResourceState.CLOSED) {
            close()
        }
        
        // 释放工厂
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        resourceState = ResourceState.DISPOSED
        Log.d(TAG, "Factory disposed, all resources cleaned up")
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
