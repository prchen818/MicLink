package com.miclink.repository

import android.content.Context
import android.util.Log
import com.miclink.model.AudioQuality
import com.miclink.model.ConnectionMode
import com.miclink.network.Config
import com.miclink.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

/**
 * WebRTC仓库 - 管理WebRTC连接和音视频通信
 */
class WebRtcRepository(private val context: Context) {
    
    private val TAG = "WebRtcRepository"
    private var webRtcManager: WebRtcManager? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // WebRTC连接状态
    private val _peerConnectionState = MutableStateFlow<PeerConnection.PeerConnectionState>(
        PeerConnection.PeerConnectionState.NEW
    )
    val peerConnectionState: StateFlow<PeerConnection.PeerConnectionState> = 
        _peerConnectionState.asStateFlow()
    
    // ICE连接状态
    private val _iceConnectionState = MutableStateFlow<PeerConnection.IceConnectionState>(
        PeerConnection.IceConnectionState.NEW
    )
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState> = 
        _iceConnectionState.asStateFlow()
    
    // 静音状态
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    // 连接类型 (p2p or relay)
    private val _connectionType = MutableStateFlow<String?>(null)
    val connectionType: StateFlow<String?> = _connectionType.asStateFlow()
    
    // ICE候选回调
    var onIceCandidateListener: ((IceCandidate) -> Unit)? = null
    
    /**
     * 初始化WebRTC
     */
    fun initialize() {
        if (webRtcManager == null) {
            webRtcManager = WebRtcManager(context, Config.getIceServers())
            webRtcManager?.initialize()
            Log.d(TAG, "WebRTC initialized")
        }
    }
    
    /**
     * 创建PeerConnection并作为发起方创建Offer
     */
    suspend fun createOfferAsCaller(
        connectionMode: ConnectionMode,
        audioQuality: AudioQuality,
        onIceCandidate: (IceCandidate) -> Unit
    ): Result<String> {
        return try {
            this.onIceCandidateListener = onIceCandidate
            
            webRtcManager?.createPeerConnection(
                connectionMode = connectionMode,
                audioQuality = audioQuality,
                onIceCandidate = onIceCandidate,
                onConnectionType = { type ->
                    _connectionType.value = type
                    Log.d(TAG, "Connection type: $type")
                }
            )
            
            // 订阅状态变化
            subscribeToStates()
            
            val sdp = webRtcManager?.createOffer()
            if (sdp != null) {
                Log.d(TAG, "Created offer")
                Result.success(sdp)
            } else {
                Result.failure(Exception("Failed to create offer"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating offer", e)
            Result.failure(e)
        }
    }
    
    /**
     * 作为接收方处理Offer并创建Answer
     */
    suspend fun handleOfferAndCreateAnswer(
        sdp: String,
        connectionMode: ConnectionMode,
        audioQuality: AudioQuality,
        onIceCandidate: (IceCandidate) -> Unit
    ): Result<String> {
        return try {
            this.onIceCandidateListener = onIceCandidate
            
            webRtcManager?.createPeerConnection(
                connectionMode = connectionMode,
                audioQuality = audioQuality,
                onIceCandidate = onIceCandidate,
                onConnectionType = { type ->
                    _connectionType.value = type
                }
            )
            
            subscribeToStates()
            
            // 设置远程SDP
            webRtcManager?.setRemoteDescription(sdp, "offer")
            
            // 创建Answer
            val answerSdp = webRtcManager?.createAnswer()
            if (answerSdp != null) {
                Log.d(TAG, "Created answer")
                Result.success(answerSdp)
            } else {
                Result.failure(Exception("Failed to create answer"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling offer", e)
            Result.failure(e)
        }
    }
    
    /**
     * 处理Answer (发起方调用)
     */
    suspend fun handleAnswer(sdp: String): Result<Unit> {
        return try {
            webRtcManager?.setRemoteDescription(sdp, "answer")
            Log.d(TAG, "Set remote answer")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling answer", e)
            Result.failure(e)
        }
    }
    
    /**
     * 添加ICE候选
     */
    fun addIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        try {
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
            webRtcManager?.addIceCandidate(iceCandidate)
            Log.d(TAG, "Added ICE candidate")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding ICE candidate", e)
        }
    }
    
    /**
     * 切换静音状态
     */
    fun toggleMute() {
        webRtcManager?.toggleMute()
        _isMuted.value = webRtcManager?.isMuted?.value ?: false
        Log.d(TAG, "Mute toggled: ${_isMuted.value}")
    }
    
    /**
     * 设置静音状态
     */
    fun setMuted(muted: Boolean) {
        webRtcManager?.setMuted(muted)
        _isMuted.value = muted
    }
    
    /**
     * 重启ICE连接（用于自动重连）
     */
    fun restartIce() {
        try {
            webRtcManager?.restartIce()
            Log.d(TAG, "ICE restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting ICE", e)
        }
    }
    
    /**
     * 关闭连接
     */
    fun close() {
        webRtcManager?.close()
        _peerConnectionState.value = PeerConnection.PeerConnectionState.CLOSED
        _iceConnectionState.value = PeerConnection.IceConnectionState.CLOSED
        _connectionType.value = null
        Log.d(TAG, "WebRTC connection closed")
    }
    
    /**
     * 释放资源
     */
    fun dispose() {
        webRtcManager?.dispose()
        webRtcManager = null
        Log.d(TAG, "WebRTC disposed")
    }
    
    /**
     * 订阅WebRTC状态变化
     */
    private fun subscribeToStates() {
        webRtcManager?.let { manager ->
            // 订阅 ICE 连接状态
            manager.iceConnectionState.onEach { state ->
                Log.d(TAG, "ICE connection state changed: $state")
                _iceConnectionState.value = state
            }.launchIn(repositoryScope)
            
            // 订阅对等连接状态
            manager.connectionState.onEach { state ->
                Log.d(TAG, "Peer connection state changed: $state")
                _peerConnectionState.value = state
            }.launchIn(repositoryScope)
        }
    }
}
