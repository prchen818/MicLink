package com.miclink.network

import org.webrtc.PeerConnection

/**
 * 全局配置
 */
object Config {
    // ========== 服务器配置 ==========
    // 开发环境 - 使用本地IP (请替换为你的电脑IP)
    private const val DEV_SERVER_IP = "192.168.0.106" // TODO: 修改为你的IP
    const val DEV_SERVER_URL = "ws://$DEV_SERVER_IP:8080/ws"
    
    // 生产环境 - 使用域名
    const val PROD_SERVER_URL = "wss://your-domain.com/ws"
    
    // 当前使用的服务器
    const val SERVER_URL = DEV_SERVER_URL
    
    // ========== 安全配置 ==========
    /**
     * API密钥，用于服务器认证
     * 重要: 在生产环境中，此密钥应与服务器端的API_KEY环境变量匹配
     * 建议: 使用BuildConfig或更安全的方式存储此密钥
     */
    const val API_KEY = "miclink-default-key-change-in-production" // TODO: 修改为你的密钥
    
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
            // 阿里云/腾讯云不提供公共STUN服务器，建议在云服务器上部署自己的TURN服务器
            // PeerConnection.IceServer.builder("turn:$DEV_SERVER_IP:3478")
            //     .setUsername("") // 使用静态密钥认证时留空
            //     .setPassword("miclink-secret-change-this-in-production")
            //     .createIceServer()
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
