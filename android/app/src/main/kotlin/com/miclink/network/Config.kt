package com.miclink.network

import android.content.Context
import org.webrtc.PeerConnection
import java.util.*

/**
 * 全局配置
 * 配置从 assets/config.properties 文件读取
 */
object Config {
    // 加载本地配置文件
    private val localConfig: Properties = Properties()
    private var isInitialized = false
    
    /**
     * 初始化配置 - 必须在应用启动时调用一次
     * @param context Android context
     */
    fun init(context: Context) {
        if (isInitialized) return
        
        loadLocalConfig(context)
        isInitialized = true
    }
    
    /**
     * 从 assets/config.properties 文件加载配置
     */
    private fun loadLocalConfig(context: Context) {
        try {
            context.assets.open("config.properties").use { input ->
                localConfig.load(input)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 异常时使用默认值
            setDefaultValues()
        }
    }
    
    /**
     * 设置默认值
     */
    private fun setDefaultValues() {
        localConfig.setProperty("DEV_SERVER_IP", "localhost")
        localConfig.setProperty("DEV_SERVER_PORT", "27890")
        localConfig.setProperty("API_KEY", "default-key")
        localConfig.setProperty("WS_PATH", "/ws")
    }
    
    // ========== 服务器配置 ==========
    // 开发环境 - 从 local.config.properties 动态读取
    val DEV_SERVER_IP: String
        get() = localConfig.getProperty("DEV_SERVER_IP", "localhost")
    
    val DEV_SERVER_PORT: String
        get() = localConfig.getProperty("DEV_SERVER_PORT", "8080")

    val WS_PATH: String
        get() = localConfig.getProperty("WS_PATH", "/ws")
    
    val DEV_SERVER_URL: String
        get() = "ws://$DEV_SERVER_IP:$DEV_SERVER_PORT$WS_PATH"
    
    // 生产环境 - 使用域名
    const val PROD_SERVER_URL = "wss://your-domain.com/ws"
    
    // 当前使用的服务器
    val SERVER_URL: String
        get() = DEV_SERVER_URL
    
    // ========== 安全配置 ==========
    /**
     * API密钥，用于服务器认证
     * 从 local.config.properties 动态读取
     */
    val API_KEY: String
        get() = localConfig.getProperty("API_KEY", "default-key")
    
    // ========== ICE服务器配置 ==========
    /**
     * 获取ICE服务器列表
     * 注意: Google STUN服务器在中国境内不稳定，建议使用自建服务器
     */
    fun getIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            // 公共STUN服务器 (中国境内可访问性较好)
            PeerConnection.IceServer.builder("stun:stun.stunprotocol.org:3478")
                .createIceServer(),
            
            // 备用STUN服务器
            PeerConnection.IceServer.builder("stun:stun.voip.blackberry.com:3478")
                .createIceServer(),
            
            // 第三备用STUN服务器
            PeerConnection.IceServer.builder("stun:stun.sipnet.net:3478")
                .createIceServer(),
            
            // 自建TURN服务器 (强烈推荐 - 需要部署coturn)
            PeerConnection.IceServer.builder("turn:$DEV_SERVER_IP:3478")
                .setUsername("miclink")
                .setPassword(API_KEY)
                .createIceServer()
        )
    }
    
    // ========== WebRTC配置 ==========
    // P2P连接超时时间 (毫秒)
    const val P2P_CONNECTION_TIMEOUT = 5000L
    
    // ICE收集超时时间 (毫秒)
    const val ICE_GATHERING_TIMEOUT = 3000L
    
    // ========== WebSocket配置 ==========
    // 心跳间隔 (毫秒)
    const val PING_INTERVAL = 30000L
    
    // 重连延迟 (毫秒)
    const val RECONNECT_DELAY = 2000L
    
    // 最大重连次数
    const val MAX_RECONNECT_ATTEMPTS = 5
    
    // ========== 调试配置 ==========
    // 是否启用详细日志
    const val ENABLE_VERBOSE_LOGGING = true
    
    // 日志TAG
    const val LOG_TAG = "MicLink"
}
