package com.miclink.network

import android.util.Log
import com.google.gson.Gson
import com.miclink.model.SignalingMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket信令客户端
 */
class SignalingClient(
    private val serverUrl: String,
    private val gson: Gson = Gson()
) {
    private val TAG = "SignalingClient"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 无限读取超时以保持长连接
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // 重连相关
    private var currentUserId: String? = null
    private var isManuallyDisconnected = false
    private var reconnectJob: Job? = null
    private val reconnectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectAttempts = 0
    private val maxReconnectDelay = 30_000L // 最大重连延迟30秒

    /**
     * 连接到信令服务器
     */
    fun connect(userId: String): Flow<SignalingEvent> = callbackFlow {
        currentUserId = userId
        isManuallyDisconnected = false
        reconnectAttempts = 0
        
        // 在URL中添加API密钥作为查询参数
        val authenticatedUrl = "$serverUrl?api_key=${Config.API_KEY}"
        
        val request = Request.Builder()
            .url(authenticatedUrl)
            .addHeader("X-API-Key", Config.API_KEY) // 同时在Header中添加，双重保险
            .build()

        fun createConnection() {
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempts = 0 // 连接成功，重置重连计数
                    // 连接成功后立即发送加入消息
                    val joinMsg = mapOf(
                        "type" to "join",
                        "from" to userId
                    )
                    webSocket.send(gson.toJson(joinMsg))
                    Log.d(TAG, "Connected to signaling server")
                    trySend(SignalingEvent.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val message = parseMessage(text)
                        trySend(SignalingEvent.MessageReceived(message))
                    } catch (e: Exception) {
                        trySend(SignalingEvent.Error("解析消息失败: ${e.message}"))
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    onMessage(webSocket, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    if (!isManuallyDisconnected) {
                        Log.d(TAG, "Connection closing, will attempt reconnect")
                        scheduleReconnect { createConnection() }
                    }
                    trySend(SignalingEvent.Disconnected)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "Connection failed: ${t.message}")
                    trySend(SignalingEvent.Error("连接失败: ${t.message}"))
                    
                    if (!isManuallyDisconnected) {
                        Log.d(TAG, "Will attempt reconnect after failure")
                        scheduleReconnect { createConnection() }
                    }
                    trySend(SignalingEvent.Disconnected)
                }
            })
        }

        createConnection()

        awaitClose {
            disconnect()
        }
    }
    
    /**
     * 安排重连
     */
    private fun scheduleReconnect(reconnectAction: () -> Unit) {
        reconnectJob?.cancel()
        reconnectJob = reconnectScope.launch {
            val delay = calculateReconnectDelay()
            Log.d(TAG, "Scheduling reconnect in ${delay}ms (attempt ${reconnectAttempts + 1})")
            delay(delay)
            
            if (!isManuallyDisconnected && currentUserId != null) {
                reconnectAttempts++
                Log.d(TAG, "Attempting reconnect #$reconnectAttempts")
                reconnectAction()
            }
        }
    }
    
    /**
     * 计算重连延迟（指数退避）
     */
    private fun calculateReconnectDelay(): Long {
        val baseDelay = 1000L
        val delay = (baseDelay * (1 shl minOf(reconnectAttempts, 5))).coerceAtMost(maxReconnectDelay)
        return delay
    }

    /**
     * 发送信令消息
     */
    fun sendMessage(message: Map<String, Any>) {
        val json = gson.toJson(message)
        webSocket?.send(json)
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "Manual disconnect called")
        isManuallyDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null
        currentUserId = null
        reconnectAttempts = 0
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = webSocket != null

    /**
     * 解析服务器消息
     */
    private fun parseMessage(json: String): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(json, Map::class.java) as Map<String, Any>
    }
}

/**
 * 信令事件
 */
sealed class SignalingEvent {
    object Connected : SignalingEvent()
    object Disconnected : SignalingEvent()
    data class MessageReceived(val message: Map<String, Any>) : SignalingEvent()
    data class Error(val message: String) : SignalingEvent()
}
