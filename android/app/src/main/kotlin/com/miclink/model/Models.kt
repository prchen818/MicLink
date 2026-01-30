package com.miclink.model

/**
 * 连接模式
 */
enum class ConnectionMode {
    AUTO,       // 自动模式: 优先P2P, 失败后降级到中转
    P2P_ONLY,   // 仅P2P模式
    RELAY_ONLY  // 仅中转模式
}

/**
 * 音频质量配置
 */
enum class AudioQuality(
    val displayName: String,
    val sampleRate: Int,
    val bitrate: Int
) {
    LOW("低质量 (省流量)", 8000, 32000),
    MEDIUM("中等质量 (推荐)", 16000, 64000),
    HIGH("高质量 (高清)", 48000, 128000)
}

/**
 * 通话状态
 */
sealed class CallState {
    object Idle : CallState()
    data class Ringing(val peerId: String, val isIncoming: Boolean) : CallState()
    object Connecting : CallState()
    data class Connected(
        val peerId: String,
        val connectionType: String // "p2p" or "relay"
    ) : CallState()
    object Disconnected : CallState()
    data class Error(val message: String) : CallState()
}

/**
 * 在线用户
 */
data class OnlineUser(
    val userId: String,
    val isOnline: Boolean = true
)

/**
 * 信令消息
 */
sealed class SignalingMessage {
    abstract val type: String

    data class Join(val userId: String) : SignalingMessage() {
        override val type = "join"
    }

    data class Leave(val userId: String) : SignalingMessage() {
        override val type = "leave"
    }

    data class UserList(val users: List<String>) : SignalingMessage() {
        override val type = "user_list"
    }

    data class Call(
        val to: String,
        val mode: ConnectionMode,
        val quality: AudioQuality
    ) : SignalingMessage() {
        override val type = "call"
    }

    data class CallResponse(
        val to: String,
        val accepted: Boolean
    ) : SignalingMessage() {
        override val type = "call_response"
    }

    data class Offer(
        val to: String,
        val sdp: String
    ) : SignalingMessage() {
        override val type = "offer"
    }

    data class Answer(
        val to: String,
        val sdp: String
    ) : SignalingMessage() {
        override val type = "answer"
    }

    data class IceCandidate(
        val to: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?
    ) : SignalingMessage() {
        override val type = "ice_candidate"
    }

    data class Hangup(val to: String) : SignalingMessage() {
        override val type = "hangup"
    }
}
