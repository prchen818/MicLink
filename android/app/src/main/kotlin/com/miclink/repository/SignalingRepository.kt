package com.miclink.repository

import android.util.Log
import com.google.gson.Gson
import com.miclink.model.*
import com.miclink.network.Config
import com.miclink.network.SignalingClient
import com.miclink.network.SignalingEvent
import kotlinx.coroutines.flow.*
import org.webrtc.IceCandidate

/**
 * 信令仓库 - 管理与信令服务器的通信
 */
class SignalingRepository {
    
    private val TAG = "SignalingRepository"
    private val gson = Gson()
    private var signalingClient: SignalingClient? = null
    private var currentUserId: String? = null
    
    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // 在线用户列表
    private val _onlineUsers = MutableStateFlow<List<String>>(emptyList())
    val onlineUsers: StateFlow<List<String>> = _onlineUsers.asStateFlow()
    
    // 信令消息
    private val _signalingMessages = MutableSharedFlow<SignalingMessageEvent>()
    val signalingMessages: SharedFlow<SignalingMessageEvent> = _signalingMessages.asSharedFlow()
    
    /**
     * 连接到信令服务器
     */
    suspend fun connect(userId: String): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                return Result.failure(Exception("用户ID不能为空"))
            }
            
            currentUserId = userId
            _connectionState.value = ConnectionState.Connecting
            
            signalingClient = SignalingClient(Config.SERVER_URL, gson)
            
            signalingClient?.connect(userId)?.collect { event ->
                handleSignalingEvent(event)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "连接失败")
            Result.failure(e)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        currentUserId?.let { userId ->
            sendLeaveMessage(userId)
        }
        signalingClient?.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        _onlineUsers.value = emptyList()
    }
    
    /**
     * 发起通话
     */
    fun initiateCall(targetId: String, mode: ConnectionMode, quality: AudioQuality) {
        val message: Map<String, Any> = mapOf(
            "type" to "call",
            "from" to currentUserId.orEmpty(),
            "to" to targetId,
            "payload" to mapOf(
                "mode" to mode.name.lowercase(),
                "quality" to quality.name.lowercase()
            ) as Any
        )
        signalingClient?.sendMessage(message)
        Log.d(TAG, "Initiated call to $targetId")
    }
    
    /**
     * 响应通话请求
     */
    fun respondToCall(targetId: String, accepted: Boolean) {
        val message: Map<String, Any> = mapOf(
            "type" to "call_response",
            "from" to currentUserId.orEmpty(),
            "to" to targetId,
            "payload" to mapOf(
                "accepted" to accepted
            ) as Any
        )
        signalingClient?.sendMessage(message)
        Log.d(TAG, "Responded to call from $targetId: $accepted")
    }
    
    /**
     * 发送SDP Offer
     */
    fun sendOffer(targetId: String, sdp: String) {
        val message: Map<String, Any> = mapOf(
            "type" to "offer",
            "from" to currentUserId.orEmpty(),
            "to" to targetId,
            "payload" to mapOf(
                "sdp" to sdp
            ) as Any
        )
        signalingClient?.sendMessage(message)
        Log.d(TAG, "Sent offer to $targetId")
    }
    
    /**
     * 发送SDP Answer
     */
    fun sendAnswer(targetId: String, sdp: String) {
        val message: Map<String, Any> = mapOf(
            "type" to "answer",
            "from" to currentUserId.orEmpty(),
            "to" to targetId,
            "payload" to mapOf(
                "sdp" to sdp
            ) as Any
        )
        signalingClient?.sendMessage(message)
        Log.d(TAG, "Sent answer to $targetId")
    }
    
    /**
     * 发送ICE候选
     */
    fun sendIceCandidate(targetId: String, candidate: IceCandidate) {
        val message: Map<String, Any> = mapOf(
            "type" to "ice_candidate",
            "from" to currentUserId.orEmpty(),
            "to" to targetId,
            "payload" to mapOf(
                "candidate" to candidate.sdp,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex
            ) as Any
        )
        signalingClient?.sendMessage(message)
    }
    
    /**
     * 发送挂断消息
     */
    fun sendHangup(targetId: String) {
        val message: Map<String, Any> = mapOf(
            "type" to "hangup",
            "from" to currentUserId.orEmpty(),
            "to" to targetId
        )
        signalingClient?.sendMessage(message)
        Log.d(TAG, "Sent hangup to $targetId")
    }
    
    /**
     * 发送离开消息
     */
    private fun sendLeaveMessage(userId: String) {
        val message: Map<String, Any> = mapOf(
            "type" to "leave",
            "from" to userId
        )
        signalingClient?.sendMessage(message)
    }
    
    /**
     * 处理信令事件
     */
    private suspend fun handleSignalingEvent(event: SignalingEvent) {
        when (event) {
            is SignalingEvent.Connected -> {
                _connectionState.value = ConnectionState.Connected
                Log.d(TAG, "Connected to signaling server")
            }
            
            is SignalingEvent.Disconnected -> {
                _connectionState.value = ConnectionState.Disconnected
                _onlineUsers.value = emptyList()
                Log.d(TAG, "Disconnected from signaling server")
            }
            
            is SignalingEvent.MessageReceived -> {
                handleMessage(event.message)
            }
            
            is SignalingEvent.Error -> {
                _connectionState.value = ConnectionState.Error(event.message)
                Log.e(TAG, "Signaling error: ${event.message}")
            }
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private suspend fun handleMessage(message: Map<String, Any>) {
        val type = message["type"] as? String ?: return
        val from = message["from"] as? String
        val payload = message["payload"]
        
        Log.d(TAG, "Received message: type=$type, from=$from")
        
        when (type) {
            "user_list" -> {
                @Suppress("UNCHECKED_CAST")
                val usersMap = payload as? Map<String, Any>
                val users = (usersMap?.get("users") as? List<String>) ?: emptyList()
                _onlineUsers.value = users.filter { it != currentUserId }
            }
            
            "call" -> {
                from?.let {
                    @Suppress("UNCHECKED_CAST")
                    val callPayload = payload as? Map<String, Any>
                    val mode = callPayload?.get("mode") as? String ?: "auto"
                    val quality = callPayload?.get("quality") as? String ?: "medium"
                    
                    _signalingMessages.emit(
                        SignalingMessageEvent.IncomingCall(
                            from = it,
                            mode = parseConnectionMode(mode),
                            quality = parseAudioQuality(quality)
                        )
                    )
                }
            }
            
            "call_response" -> {
                from?.let {
                    @Suppress("UNCHECKED_CAST")
                    val responsePayload = payload as? Map<String, Any>
                    val accepted = responsePayload?.get("accepted") as? Boolean ?: false
                    
                    _signalingMessages.emit(
                        SignalingMessageEvent.CallResponse(from = it, accepted = accepted)
                    )
                }
            }
            
            "offer" -> {
                from?.let {
                    @Suppress("UNCHECKED_CAST")
                    val sdpPayload = payload as? Map<String, Any>
                    val sdp = sdpPayload?.get("sdp") as? String ?: return
                    
                    _signalingMessages.emit(
                        SignalingMessageEvent.Offer(from = it, sdp = sdp)
                    )
                }
            }
            
            "answer" -> {
                from?.let {
                    @Suppress("UNCHECKED_CAST")
                    val sdpPayload = payload as? Map<String, Any>
                    val sdp = sdpPayload?.get("sdp") as? String ?: return
                    
                    _signalingMessages.emit(
                        SignalingMessageEvent.Answer(from = it, sdp = sdp)
                    )
                }
            }
            
            "ice_candidate" -> {
                from?.let {
                    @Suppress("UNCHECKED_CAST")
                    val candidatePayload = payload as? Map<String, Any>
                    val candidateSdp = candidatePayload?.get("candidate") as? String ?: return
                    val sdpMid = candidatePayload["sdpMid"] as? String ?: ""
                    val sdpMLineIndex = (candidatePayload["sdpMLineIndex"] as? Double)?.toInt() ?: 0
                    
                    _signalingMessages.emit(
                        SignalingMessageEvent.IceCandidate(
                            from = it,
                            candidate = candidateSdp,
                            sdpMid = sdpMid,
                            sdpMLineIndex = sdpMLineIndex
                        )
                    )
                }
            }
            
            "hangup" -> {
                from?.let {
                    _signalingMessages.emit(SignalingMessageEvent.Hangup(from = it))
                }
            }
            
            "error" -> {
                @Suppress("UNCHECKED_CAST")
                val errorPayload = payload as? Map<String, Any>
                val errorMessage = errorPayload?.get("message") as? String ?: "未知错误"
                _connectionState.value = ConnectionState.Error(errorMessage)
            }
        }
    }
    
    private fun parseConnectionMode(mode: String): ConnectionMode {
        return when (mode.lowercase()) {
            "auto" -> ConnectionMode.AUTO
            "p2p_only" -> ConnectionMode.P2P_ONLY
            "relay_only" -> ConnectionMode.RELAY_ONLY
            else -> ConnectionMode.AUTO
        }
    }
    
    private fun parseAudioQuality(quality: String): AudioQuality {
        return when (quality.lowercase()) {
            "low" -> AudioQuality.LOW
            "medium" -> AudioQuality.MEDIUM
            "high" -> AudioQuality.HIGH
            else -> AudioQuality.MEDIUM
        }
    }
}

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * 信令消息事件
 */
sealed class SignalingMessageEvent {
    data class IncomingCall(
        val from: String,
        val mode: ConnectionMode,
        val quality: AudioQuality
    ) : SignalingMessageEvent()
    
    data class CallResponse(val from: String, val accepted: Boolean) : SignalingMessageEvent()
    data class Offer(val from: String, val sdp: String) : SignalingMessageEvent()
    data class Answer(val from: String, val sdp: String) : SignalingMessageEvent()
    data class IceCandidate(
        val from: String,
        val candidate: String,
        val sdpMid: String,
        val sdpMLineIndex: Int
    ) : SignalingMessageEvent()
    data class Hangup(val from: String) : SignalingMessageEvent()
}
