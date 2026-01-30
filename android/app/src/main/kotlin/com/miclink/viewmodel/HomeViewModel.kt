package com.miclink.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miclink.model.AudioQuality
import com.miclink.model.ConnectionMode
import com.miclink.repository.ConnectionState
import com.miclink.repository.SignalingRepository
import com.miclink.service.MicLinkService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 主界面ViewModel - 管理在线用户和连接状态
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "HomeViewModel"
    private val context = application.applicationContext
    private val signalingRepository = SignalingRepository()
    
    // 当前用户ID
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()
    
    // 连接状态
    val connectionState: StateFlow<ConnectionState> = signalingRepository.connectionState
    
    // 在线用户列表
    val onlineUsers: StateFlow<List<String>> = signalingRepository.onlineUsers
    
    // 通话设置
    private val _connectionMode = MutableStateFlow(ConnectionMode.AUTO)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()
    
    private val _audioQuality = MutableStateFlow(AudioQuality.MEDIUM)
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()
    
    // 错误消息
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()
    
    /**
     * 连接到服务器
     */
    fun connect(userId: String) {
        if (userId.isBlank()) {
            viewModelScope.launch {
                _errorMessage.emit("用户ID不能为空")
            }
            return
        }
        
        // 验证用户ID格式 (只允许字母和数字)
        if (!userId.matches(Regex("^[a-zA-Z0-9]+$"))) {
            viewModelScope.launch {
                _errorMessage.emit("用户ID只能包含字母和数字")
            }
            return
        }
        
        viewModelScope.launch {
            try {
                _currentUserId.value = userId
                
                val result = signalingRepository.connect(userId)
                
                result.onSuccess {
                    Log.d(TAG, "Connected to server as $userId")
                    // 上线，启动服务保持后台连接
                    MicLinkService.goOnline(context, userId)
                }
                
                result.onFailure { e ->
                    Log.e(TAG, "Failed to connect", e)
                    _errorMessage.emit(e.message ?: "连接失败")
                    _currentUserId.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting", e)
                _errorMessage.emit(e.message ?: "连接失败")
                _currentUserId.value = null
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        // 离线，停止服务
        MicLinkService.goOffline(context)
        signalingRepository.disconnect()
        _currentUserId.value = null
        Log.d(TAG, "Disconnected from server")
    }
    
    /**
     * 设置连接模式
     */
    fun setConnectionMode(mode: ConnectionMode) {
        _connectionMode.value = mode
        Log.d(TAG, "Connection mode set to: $mode")
    }
    
    /**
     * 设置音频质量
     */
    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        Log.d(TAG, "Audio quality set to: $quality")
    }
    
    /**
     * 获取信令仓库 (供CallViewModel使用)
     */
    fun getSignalingRepository(): SignalingRepository {
        return signalingRepository
    }
    
    override fun onCleared() {
        super.onCleared()
        // 注意：不在这里断开连接，让 SignalingService 保持后台连接
        // 只有用户主动断开时才停止服务
        Log.d(TAG, "HomeViewModel cleared, signaling service keeps running")
    }
}
