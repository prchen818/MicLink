package com.miclink.network

import com.google.gson.Gson
import com.miclink.model.SignalingMessage
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
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * 连接到信令服务器
     */
    fun connect(userId: String): Flow<SignalingEvent> = callbackFlow {
        // 在URL中添加API密钥作为查询参数
        val authenticatedUrl = "$serverUrl?api_key=${Config.API_KEY}"
        
        val request = Request.Builder()
            .url(authenticatedUrl)
            .addHeader("X-API-Key", Config.API_KEY) // 同时在Header中添加，双重保险
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 连接成功后立即发送加入消息
                val joinMsg = mapOf(
                    "type" to "join",
                    "from" to userId
                )
                webSocket.send(gson.toJson(joinMsg))
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
                trySend(SignalingEvent.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(SignalingEvent.Error("连接失败: ${t.message}"))
                trySend(SignalingEvent.Disconnected)
            }
        })

        awaitClose {
            disconnect()
        }
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
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }

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
