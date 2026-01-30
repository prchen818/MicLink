package com.miclink.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 网络状态
 */
enum class NetworkStatus {
    AVAILABLE,      // 网络可用
    UNAVAILABLE,    // 网络不可用
    LOSING,         // 网络即将丢失
    LOST            // 网络已丢失
}

/**
 * 网络类型
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN,
    NONE
}

/**
 * 网络质量等级
 */
enum class NetworkQuality {
    EXCELLENT,  // 优秀
    GOOD,       // 良好
    FAIR,       // 一般
    POOR,       // 差
    UNKNOWN     // 未知
}

/**
 * 网络信息
 */
data class NetworkInfo(
    val status: NetworkStatus = NetworkStatus.UNAVAILABLE,
    val type: NetworkType = NetworkType.NONE,
    val quality: NetworkQuality = NetworkQuality.UNKNOWN,
    val downloadSpeedMbps: Int = 0,
    val uploadSpeedMbps: Int = 0,
    val latencyMs: Int = 0
)

/**
 * 网络监控器 - 监控网络状态变化
 */
class NetworkMonitor(private val context: Context) {
    
    private val TAG = "NetworkMonitor"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    /**
     * 开始监控网络
     */
    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                updateNetworkInfo(NetworkStatus.AVAILABLE)
            }
            
            override fun onLosing(network: Network, maxMsToLive: Int) {
                Log.d(TAG, "Network losing, max ms to live: $maxMsToLive")
                updateNetworkInfo(NetworkStatus.LOSING)
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                updateNetworkInfo(NetworkStatus.LOST)
            }
            
            override fun onUnavailable() {
                Log.d(TAG, "Network unavailable")
                updateNetworkInfo(NetworkStatus.UNAVAILABLE)
            }
            
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateNetworkCapabilities(capabilities)
            }
        }
        
        networkCallback?.let {
            connectivityManager.registerNetworkCallback(request, it)
        }
        
        // 立即检查当前状态
        checkCurrentNetwork()
    }
    
    /**
     * 停止监控
     */
    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callback", e)
            }
        }
        networkCallback = null
    }
    
    /**
     * 检查当前网络状态
     */
    private fun checkCurrentNetwork() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        if (network != null && capabilities != null) {
            updateNetworkInfo(NetworkStatus.AVAILABLE)
            updateNetworkCapabilities(capabilities)
        } else {
            updateNetworkInfo(NetworkStatus.UNAVAILABLE)
        }
    }
    
    /**
     * 更新网络信息
     */
    private fun updateNetworkInfo(status: NetworkStatus) {
        _isConnected.value = status == NetworkStatus.AVAILABLE
        _networkInfo.value = _networkInfo.value.copy(status = status)
    }
    
    /**
     * 更新网络能力信息
     */
    private fun updateNetworkCapabilities(capabilities: NetworkCapabilities) {
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }
        
        val downloadMbps = capabilities.linkDownstreamBandwidthKbps / 1000
        val uploadMbps = capabilities.linkUpstreamBandwidthKbps / 1000
        
        val quality = when {
            downloadMbps >= 50 && uploadMbps >= 10 -> NetworkQuality.EXCELLENT
            downloadMbps >= 10 && uploadMbps >= 2 -> NetworkQuality.GOOD
            downloadMbps >= 2 && uploadMbps >= 0.5 -> NetworkQuality.FAIR
            downloadMbps > 0 -> NetworkQuality.POOR
            else -> NetworkQuality.UNKNOWN
        }
        
        _networkInfo.value = _networkInfo.value.copy(
            type = type,
            quality = quality,
            downloadSpeedMbps = downloadMbps,
            uploadSpeedMbps = uploadMbps
        )
        
        Log.d(TAG, "Network updated: type=$type, quality=$quality, down=${downloadMbps}Mbps, up=${uploadMbps}Mbps")
    }
    
    /**
     * 获取网络状态Flow
     */
    fun observeNetworkStatus(): Flow<NetworkInfo> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(networkInfo.value.copy(status = NetworkStatus.AVAILABLE))
            }
            
            override fun onLost(network: Network) {
                trySend(networkInfo.value.copy(status = NetworkStatus.LOST))
            }
            
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(networkInfo.value)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
