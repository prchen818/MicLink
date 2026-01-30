package com.miclink.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miclink.model.AudioQuality
import com.miclink.model.CallState
import com.miclink.model.ConnectionMode
import com.miclink.network.NetworkMonitor
import com.miclink.network.NetworkQuality
import com.miclink.network.NetworkStatus
import com.miclink.repository.*
import com.miclink.service.MicLinkService
import com.miclink.ui.IncomingCallActivity
import com.miclink.webrtc.AudioDeviceInfo2
import com.miclink.webrtc.MicLinkAudioManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

/**
 * 通话ViewModel - 管理通话状态和逻辑
 */
class CallViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "CallViewModel"
    
    private var signalingRepository: SignalingRepository? = null
    private val webRtcRepository = WebRtcRepository(application)
    private val audioManager = MicLinkAudioManager(application)
    private val networkMonitor = NetworkMonitor(application)
    
    // 当前用户ID
    private var currentUserId: String? = null
    
    // 追踪是否已经清理
    private var isCleanedUp = false
    
    // 通话状态
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    // 对方用户ID
    private val _peerUserId = MutableStateFlow<String?>(null)
    val peerUserId: StateFlow<String?> = _peerUserId.asStateFlow()
    
    // 静音状态
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    // 扬声器状态
    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()
    
    // 连接类型
    private val _connectionType = MutableStateFlow<String?>(null)
    val connectionType: StateFlow<String?> = _connectionType.asStateFlow()
    
    // 通话时长
    private val _callDuration = MutableStateFlow(0)
    val callDuration: StateFlow<Int> = _callDuration.asStateFlow()
    
    // 网络质量
    private val _networkQuality = MutableStateFlow(NetworkQuality.UNKNOWN)
    val networkQuality: StateFlow<NetworkQuality> = _networkQuality.asStateFlow()
    
    // 当前音频设备
    private val _currentAudioDevice = MutableStateFlow(MicLinkAudioManager.AudioDevice.EARPIECE)
    val currentAudioDevice: StateFlow<MicLinkAudioManager.AudioDevice> = _currentAudioDevice.asStateFlow()
    
    // 可用音频设备列表（带名称）
    private val _availableAudioDevices = MutableStateFlow<List<AudioDeviceInfo2>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioDeviceInfo2>> = _availableAudioDevices.asStateFlow()
    
    // 自动重连相关
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private val reconnectDelayMs = 2000L
    
    // 通话设置
    private var connectionMode = ConnectionMode.AUTO
    private var audioQuality = AudioQuality.MEDIUM
    
    private var durationJob: Job? = null
    
    init {
        // 初始化WebRTC
        webRtcRepository.initialize()
        
        // 启动网络监控
        networkMonitor.startMonitoring()
        
        // 订阅网络状态
        viewModelScope.launch {
            networkMonitor.networkInfo.collect { info ->
                _networkQuality.value = info.quality
                
                // 网络断开时尝试重连
                if (info.status == NetworkStatus.LOST && _callState.value is CallState.Connected) {
                    handleNetworkLost()
                }
            }
        }
        
        // 订阅WebRTC状态
        viewModelScope.launch {
            webRtcRepository.iceConnectionState.collect { state ->
                handleIceConnectionState(state)
            }
        }
        
        // 订阅连接类型
        viewModelScope.launch {
            webRtcRepository.connectionType.collect { type ->
                _connectionType.value = type
            }
        }
        
        // 更新可用音频设备
        updateAvailableAudioDevices()
        
        // 监听设备变化
        audioManager.setOnDeviceChangeListener {
            updateAvailableAudioDevices()
        }
        
        // 订阅设备列表变化
        viewModelScope.launch {
            audioManager.availableDevices.collect { devices ->
                _availableAudioDevices.value = devices
            }
        }
    }
    
    /**
     * 更新可用音频设备列表
     */
    private fun updateAvailableAudioDevices() {
        _availableAudioDevices.value = audioManager.getAvailableAudioDevicesWithNames()
    }
    
    /**
     * 选择音频设备
     */
    fun selectAudioDevice(device: MicLinkAudioManager.AudioDevice) {
        audioManager.selectAudioDevice(device)
        _currentAudioDevice.value = device
        _isSpeakerOn.value = device == MicLinkAudioManager.AudioDevice.SPEAKER_PHONE
        Log.d(TAG, "Audio device selected: $device")
    }
    
    /**
     * 处理网络丢失
     */
    private fun handleNetworkLost() {
        Log.w(TAG, "Network lost during call, attempting to maintain connection...")
        // WebRTC有自己的ICE重连机制，这里主要记录状态
        // 如果ICE也失败了，会在handleIceConnectionState中处理
    }
    
    /**
     * 尝试重连
     */
    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts reached, ending call")
            endCall()
            return
        }
        
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            reconnectAttempts++
            Log.d(TAG, "Attempting reconnect ($reconnectAttempts/$maxReconnectAttempts)")
            
            delay(reconnectDelayMs)
            
            // 尝试ICE重启
            webRtcRepository.restartIce()
        }
    }
    
    /**
     * 设置信令仓库 (从HomeViewModel共享)
     */
    fun setSignalingRepository(repository: SignalingRepository) {
        signalingRepository = repository
        
        // 订阅信令消息
        viewModelScope.launch {
            repository.signalingMessages.collect { event ->
                handleSignalingMessage(event)
            }
        }
    }
    
    /**
     * 设置用户ID
     */
    fun setUserId(userId: String) {
        currentUserId = userId
    }
    
    /**
     * 设置通话参数
     */
    fun setCallSettings(mode: ConnectionMode, quality: AudioQuality) {
        connectionMode = mode
        audioQuality = quality
        Log.d(TAG, "Call settings: mode=$mode, quality=$quality")
    }
    
    /**
     * 发起通话
     */
    fun initiateCall(targetId: String) {
        if (_callState.value !is CallState.Idle) {
            Log.w(TAG, "Cannot initiate call in current state: ${_callState.value}")
            return
        }
        
        viewModelScope.launch {
            try {
                _peerUserId.value = targetId
                _callState.value = CallState.Ringing(targetId, isIncoming = false)
                
                // 启动通话服务
                MicLinkService.startCall(getApplication(), targetId, isIncoming = false)
                
                // 发送通话请求
                signalingRepository?.initiateCall(targetId, connectionMode, audioQuality)
                
                Log.d(TAG, "Initiated call to $targetId")
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating call", e)
                _callState.value = CallState.Error(e.message ?: "发起通话失败")
                currentUserId?.let { MicLinkService.endCall(getApplication(), it) }
            }
        }
    }
    
    /**
     * 接受来电
     */
    fun acceptCall() {
        val currentState = _callState.value
        if (currentState !is CallState.Ringing || !currentState.isIncoming) {
            return
        }
        
        viewModelScope.launch {
            try {
                val targetId = currentState.peerId
                _callState.value = CallState.Connecting
                
                // 启动音频管理器
                audioManager.start()
                
                // 响应通话
                signalingRepository?.respondToCall(targetId, accepted = true)
                
                Log.d(TAG, "Accepted call from $targetId")
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting call", e)
                _callState.value = CallState.Error(e.message ?: "接听失败")
            }
        }
    }
    
    /**
     * 拒绝来电
     */
    fun rejectCall() {
        val currentState = _callState.value
        if (currentState !is CallState.Ringing || !currentState.isIncoming) {
            return
        }
        
        viewModelScope.launch {
            signalingRepository?.respondToCall(currentState.peerId, accepted = false)
            _callState.value = CallState.Idle
            _peerUserId.value = null
            
            // 回到在线状态
            currentUserId?.let { MicLinkService.endCall(getApplication(), it) }
            
            Log.d(TAG, "Rejected call from ${currentState.peerId}")
        }
    }
    
    /**
     * 挂断通话
     */
    fun hangup() {
        viewModelScope.launch {
            _peerUserId.value?.let { targetId ->
                signalingRepository?.sendHangup(targetId)
            }
            endCall()
        }
    }
    
    /**
     * 切换静音
     */
    fun toggleMute() {
        webRtcRepository.toggleMute()
        _isMuted.value = !_isMuted.value
    }
    
    /**
     * 切换扬声器
     */
    fun toggleSpeaker() {
        val newState = audioManager.toggleSpeakerPhone()
        _isSpeakerOn.value = newState
    }
    
    /**
     * 处理信令消息
     */
    private suspend fun handleSignalingMessage(event: SignalingMessageEvent) {
        when (event) {
            is SignalingMessageEvent.IncomingCall -> {
                // 来电
                _peerUserId.value = event.from
                _callState.value = CallState.Ringing(event.from, isIncoming = true)
                connectionMode = event.mode
                audioQuality = event.quality
                Log.d(TAG, "Incoming call from ${event.from}")
                
                // 显示全屏来电界面（在锁屏上也能显示）
                showIncomingCallScreen(event.from)
                
                // 启动通话服务
                MicLinkService.startCall(getApplication(), event.from, isIncoming = true)
            }
            
            is SignalingMessageEvent.CallResponse -> {
                if (event.accepted) {
                    // 对方接受，开始WebRTC协商
                    _callState.value = CallState.Connecting
                    audioManager.start()
                    // 在单独的协程中启动WebRTC协商，避免阻塞消息处理
                    viewModelScope.launch {
                        startWebRtcNegotiation(isInitiator = true)
                    }
                } else {
                    // 对方拒绝
                    _callState.value = CallState.Idle
                    _peerUserId.value = null
                    // 回到在线状态
                    currentUserId?.let { MicLinkService.endCall(getApplication(), it) }
                    Log.d(TAG, "Call rejected by ${event.from}")
                }
            }
            
            is SignalingMessageEvent.Offer -> {
                // 收到Offer，创建Answer
                Log.d(TAG, "Received Offer from ${event.from}, current state: ${_callState.value}, peerUserId: ${_peerUserId.value}")
                // 在单独的协程中处理Offer，避免阻塞消息处理
                viewModelScope.launch {
                    handleOffer(event.from, event.sdp)
                }
            }
            
            is SignalingMessageEvent.Answer -> {
                // 收到Answer
                Log.d(TAG, "Received Answer from ${event.from}")
                // 在单独的协程中处理Answer，避免阻塞消息处理
                viewModelScope.launch {
                    handleAnswer(event.sdp)
                }
            }
            
            is SignalingMessageEvent.IceCandidate -> {
                // 收到ICE候选
                Log.d(TAG, "Received ICE candidate from ${event.from}: ${event.candidate}")
                webRtcRepository.addIceCandidate(
                    event.candidate,
                    event.sdpMid,
                    event.sdpMLineIndex
                )
            }
            
            is SignalingMessageEvent.Hangup -> {
                // 对方挂断
                endCall()
            }
        }
    }
    
    /**
     * 开始WebRTC协商
     */
    private suspend fun startWebRtcNegotiation(isInitiator: Boolean) {
        try {
            val targetId = _peerUserId.value
            if (targetId == null) {
                Log.e(TAG, "Cannot start WebRTC negotiation: peer ID is null")
                return
            }
            
            Log.d(TAG, "Starting WebRTC negotiation as ${if (isInitiator) "initiator" else "receiver"}")
            
            if (isInitiator) {
                // 作为发起方，创建Offer
                Log.d(TAG, "Creating offer for $targetId")
                val result = webRtcRepository.createOfferAsCaller(
                    connectionMode = connectionMode,
                    audioQuality = audioQuality,
                    onIceCandidate = { candidate ->
                        Log.d(TAG, "Generated ICE candidate: ${candidate.sdp}")
                        signalingRepository?.sendIceCandidate(targetId, candidate)
                    }
                )
                
                result.onSuccess { sdp ->
                    Log.d(TAG, "Offer created successfully, sending to $targetId")
                    signalingRepository?.sendOffer(targetId, sdp)
                }
                
                result.onFailure { e ->
                    Log.e(TAG, "Failed to create offer", e)
                    _callState.value = CallState.Error("创建通话失败")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in WebRTC negotiation", e)
            _callState.value = CallState.Error(e.message ?: "连接失败")
        }
    }
    
    /**
     * 处理收到的Offer
     */
    private suspend fun handleOffer(from: String, sdp: String) {
        try {
            val targetId = _peerUserId.value
            if (targetId == null) {
                Log.e(TAG, "Cannot handle offer: peer ID is null")
                return
            }
            
            Log.d(TAG, "Handling offer from $from")
            
            val result = webRtcRepository.handleOfferAndCreateAnswer(
                sdp = sdp,
                connectionMode = connectionMode,
                audioQuality = audioQuality,
                onIceCandidate = { candidate ->
                    Log.d(TAG, "Generated ICE candidate (answer): ${candidate.sdp}")
                    signalingRepository?.sendIceCandidate(targetId, candidate)
                }
            )
            
            result.onSuccess { answerSdp ->
                Log.d(TAG, "Answer created successfully, sending to $targetId")
                signalingRepository?.sendAnswer(targetId, answerSdp)
            }
            
            result.onFailure { e ->
                Log.e(TAG, "Failed to handle offer", e)
                _callState.value = CallState.Error("处理通话请求失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling offer", e)
            _callState.value = CallState.Error(e.message ?: "连接失败")
        }
    }
    
    /**
     * 处理收到的Answer
     */
    private suspend fun handleAnswer(sdp: String) {
        try {
            val result = webRtcRepository.handleAnswer(sdp)
            
            result.onFailure { e ->
                Log.e(TAG, "Failed to handle answer", e)
                _callState.value = CallState.Error("建立连接失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling answer", e)
            _callState.value = CallState.Error(e.message ?: "连接失败")
        }
    }
    
    /**
     * 处理ICE连接状态变化
     */
    private fun handleIceConnectionState(state: PeerConnection.IceConnectionState) {
        Log.d(TAG, "ICE connection state: $state, current call state: ${_callState.value}")
        
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                // 连接成功
                val peerId = _peerUserId.value
                if (peerId == null) {
                    Log.w(TAG, "ICE connected but peer ID is null")
                    return
                }
                Log.d(TAG, "ICE connection established with $peerId")
                _callState.value = CallState.Connected(
                    peerId,
                    _connectionType.value ?: "unknown"
                )
                startDurationCounter()
            }
            
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                // 连接失败或断开
                Log.w(TAG, "ICE connection failed or disconnected: $state")
                if (_callState.value is CallState.Connected) {
                    endCall()
                }
            }
            
            PeerConnection.IceConnectionState.CHECKING -> {
                Log.d(TAG, "ICE connection checking...")
            }
            
            else -> {
                // 其他状态
                Log.d(TAG, "ICE connection state: $state")
            }
        }
    }
    
    /**
     * 开始计时
     */
    private fun startDurationCounter() {
        durationJob?.cancel()
        _callDuration.value = 0
        
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callDuration.value += 1
            }
        }
    }
    
    /**
     * 结束通话
     */
    private fun endCall() {
        durationJob?.cancel()
        audioManager.stop()
        webRtcRepository.close()
        
        // 回到在线状态
        currentUserId?.let { MicLinkService.endCall(getApplication(), it) }
        
        _callState.value = CallState.Idle
        _peerUserId.value = null
        _callDuration.value = 0
        _isMuted.value = false
        _isSpeakerOn.value = false
        _connectionType.value = null
        
        isCleanedUp = true
        Log.d(TAG, "Call ended")
    }
    
    override fun onCleared() {
        super.onCleared()
        // 清除来电回调
        IncomingCallActivity.onCallResponse = null
        
        if (!isCleanedUp) {
            durationJob?.cancel()
            audioManager.stop()
            webRtcRepository.dispose()
            currentUserId?.let { MicLinkService.endCall(getApplication(), it) }
            isCleanedUp = true
        }
    }
    
    /**
     * 显示全屏来电界面
     */
    private fun showIncomingCallScreen(callerId: String) {
        // 设置来电响应回调
        IncomingCallActivity.onCallResponse = { accepted ->
            if (accepted) {
                acceptCall()
            } else {
                rejectCall()
            }
        }
        
        // 显示来电界面
        IncomingCallActivity.show(getApplication(), callerId)
    }
}
