package com.miclink.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miclink.model.AudioQuality
import com.miclink.model.CallState
import com.miclink.model.ConnectionMode
import com.miclink.network.Config
import com.miclink.network.NetworkMonitor
import com.miclink.network.NetworkQuality
import com.miclink.network.NetworkStatus
import com.miclink.repository.*
import com.miclink.service.MicLinkService
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
    
    // 连接超时相关
    private var connectionTimeoutJob: Job? = null
    private val connectionTimeoutMs = 8000L  // 8秒超时
    
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
        
        // 订阅 ICE 收集和信令状态，用于显示详细的连接阶段
        viewModelScope.launch {
            webRtcRepository.iceGatheringState.collect { state ->
                updateConnectionStatus()
            }
        }
        
        viewModelScope.launch {
            webRtcRepository.signalingState.collect { state ->
                updateConnectionStatus()
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
                
                // 启动连接超时检测 (8秒)
                startConnectionTimeout()
                
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
                _callState.value = CallState.Connecting(
                    peerId = targetId,
                    iceConnectionState = "CHECKING",
                    signalingState = "STABLE",
                    iceGatheringState = "GATHERING"
                )
                
                // 启动音频管理器
                audioManager.start()
                
                // 响应通话
                signalingRepository?.respondToCall(targetId, accepted = true)
                
                // 启动连接超时检测 (8秒)
                startConnectionTimeout()
                
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
                    _callState.value = CallState.Connecting(
                        peerId = event.from,
                        iceConnectionState = "CHECKING",
                        signalingState = "STABLE",
                        iceGatheringState = "GATHERING"
                    )
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
     * 更新连接状态显示 - 显示详细的WebRTC连接阶段
     */
    private suspend fun updateConnectionStatus() {
        val currentCallState = _callState.value
        if (currentCallState !is CallState.Connecting) return
        
        val iceConnState = webRtcRepository.iceConnectionState.value?.name ?: "UNKNOWN"
        val iceGatherState = webRtcRepository.iceGatheringState.value?.name ?: "NEW"
        val signalingState = webRtcRepository.signalingState.value?.name ?: "STABLE"
        
        _callState.value = CallState.Connecting(
            peerId = currentCallState.peerId,
            iceConnectionState = iceConnState,
            signalingState = signalingState,
            iceGatheringState = iceGatherState
        )
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
                cancelConnectionTimeout()  // 取消连接超时
                _callState.value = CallState.Connected(
                    peerId,
                    _connectionType.value ?: "unknown"
                )
                startDurationCounter()
            }
            
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.CLOSED,
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                // 连接失败或断开 - 收集诊断信息
                Log.w(TAG, "ICE connection failed or disconnected: $state")
                
                // 异步收集诊断信息（延迟执行，确保候选收集完成）
                viewModelScope.launch {
                    delay(500)  // 等待候选完全收集
                    collectIceDiagnostics(state)
                }
                
                // 只在 Connected 状态时自动结束通话
                // 在 Connecting 状态时保持，用户可手动挂断或重试
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
     * 收集 ICE 连接诊断信息 - 用于调试 TURN 连接失败
     */
    private fun collectIceDiagnostics(failureState: PeerConnection.IceConnectionState) {
        viewModelScope.launch {
            try {
                val diagnosis = webRtcRepository.diagnoseConnection()
                
                Log.e(TAG, """
                    ╔══════════════════════════════════════════════════════════╗
                    ║           ICE 连接失败诊断信息 (调试用)                    ║
                    ╠══════════════════════════════════════════════════════════╣
                    ║ 连接模式: $connectionMode
                    ║ 音质设置: $audioQuality
                    ║ 失败状态: $failureState
                    ║ ─────────────────────────────────────────────────────── ║
                    ║ ICE连接状态: ${diagnosis["iceConnectionState"]}
                    ║ ICE收集状态: ${diagnosis["iceGatheringState"]}
                    ║ 信令状态: ${diagnosis["signalingState"]}
                    ║ ─────────────────────────────────────────────────────── ║
                    ║ 候选统计:
                    ║   ├─ 总计: ${diagnosis["totalCandidates"]} 个
                    ║   ├─ 中转(RELAY): ${diagnosis["hasRelay"]}
                    ║   ├─ 本地(HOST): ${diagnosis["hasHost"]}
                    ║   └─ P2P(SRFLX): ${diagnosis["hasSrflx"]}
                    ║ ─────────────────────────────────────────────────────── ║
                    ║ 详细信息:
                    ║ ${formatDiagnosticsDetails(diagnosis)}
                    ╚══════════════════════════════════════════════════════════╝
                """.trimIndent())
                
                // 根据诊断结果提供建议
                provideDiagnosticsSuggestions(diagnosis, failureState)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting ICE diagnostics", e)
            }
        }
    }
    
    /**
     * 格式化诊断详情
     */
    private fun formatDiagnosticsDetails(diagnosis: Map<String, Any?>): String {
        val sb = StringBuilder()
        val excludeKeys = setOf("totalCandidates", "hasRelay", "hasHost", "hasSrflx", 
                              "iceConnectionState", "iceGatheringState", "signalingState")
        
        diagnosis.forEach { (key, value) ->
            if (!excludeKeys.contains(key) && value != null) {
                sb.append("║   $key: $value\n")
            }
        }
        return sb.toString().trimEnd()
    }
    
    /**
     * 根据诊断结果提供建议
     */
    private fun provideDiagnosticsSuggestions(diagnosis: Map<String, Any?>, 
                                            failureState: PeerConnection.IceConnectionState) {
        val suggestions = mutableListOf<String>()
        
        val hasRelay = diagnosis["hasRelay"] as? Boolean ?: false
        val hasHost = diagnosis["hasHost"] as? Boolean ?: false
        val hasSrflx = diagnosis["hasSrflx"] as? Boolean ?: false
        val totalCandidates = diagnosis["totalCandidates"] as? Int ?: 0
        
        when {
            // RELAY_ONLY 模式诊断
            connectionMode == ConnectionMode.RELAY_ONLY -> {
                when {
                    !hasRelay -> {
                        suggestions.add("❌ 未获得TURN中转候选")
                        suggestions.add("📋 可能原因:")
                        suggestions.add("   1. TURN服务器不可达 (check firewall/DNS)")
                        suggestions.add("   2. TURN认证失败 (check username/password)")
                        suggestions.add("   3. TURN服务器地址错误或端口错误")
                        suggestions.add("✅ 解决方案:")
                        suggestions.add("   - 检查 Config.turn:${Config.DEV_SERVER_IP}:3478 配置")
                        suggestions.add("   - 测试TURN服务器: stunclient ${Config.DEV_SERVER_IP} 3478")
                        suggestions.add("   - 查看服务器日志: /var/log/coturn/")
                    }
                    totalCandidates == 0 -> {
                        suggestions.add("⚠️ 完全没有收集到任何候选")
                        suggestions.add("可能是DNS解析失败或网络完全不通")
                    }
                    else -> {
                        suggestions.add("✅ 有TURN候选，但连接失败")
                        suggestions.add("可能是NAT穿透问题或TURN服务器负载过高")
                    }
                }
            }
            
            // AUTO 模式诊断
            connectionMode == ConnectionMode.AUTO -> {
                when {
                    !hasSrflx && !hasRelay -> {
                        suggestions.add("❌ 既无P2P候选也无TURN候选")
                        suggestions.add("建议检查网络连接和DNS解析")
                    }
                    hasSrflx && !hasRelay -> {
                        suggestions.add("✓ P2P候选存在，但连接失败")
                        suggestions.add("可能是NAT类型不兼容或防火墙阻止")
                    }
                    hasRelay -> {
                        suggestions.add("✓ TURN候选存在，降级到中转模式")
                        suggestions.add("如果仍连接失败，检查TURN服务器负载")
                    }
                }
            }
            
            // P2P_ONLY 模式诊断
            connectionMode == ConnectionMode.P2P_ONLY -> {
                when {
                    !hasSrflx -> {
                        suggestions.add("❌ 无P2P候选 (STUN反射失败)")
                        suggestions.add("可能原因:")
                        suggestions.add("   - STUN服务器不可达")
                        suggestions.add("   - NAT类型过于严格 (Symmetric NAT)")
                        suggestions.add("✅ 解决方案: 切换为AUTO或RELAY_ONLY模式")
                    }
                }
            }
        }
        
        // 通用建议
        if (failureState == PeerConnection.IceConnectionState.FAILED) {
            suggestions.add("\n🔍 通用调试步骤:")
            suggestions.add("1. 查看 logcat 日志: adb logcat | grep WebRtcManager")
            suggestions.add("2. 测试网络连通性: ping ${Config.DEV_SERVER_IP}")
            suggestions.add("3. 对端是否也连接失败?")
            suggestions.add("4. 是否需要切换连接模式?")
        }
        
        if (suggestions.isNotEmpty()) {
            Log.i(TAG, """
                ╔══════════════════════════════════════════════════════════╗
                ║              ICE 连接失败诊断建议                          ║
                ╠══════════════════════════════════════════════════════════╣
                ${suggestions.mapIndexed { i, s -> "║ $s" }.joinToString("\n")}
                ╚══════════════════════════════════════════════════════════╝
            """.trimIndent())
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
     * 启动连接超时检测 (8秒)
     * 如果在规定时间内没有建立连接，则显示超时错误
     */
    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        
        connectionTimeoutJob = viewModelScope.launch {
            delay(connectionTimeoutMs)
            
            // 如果仍在 Connecting 或 Ringing 状态，说明连接超时
            val currentState = _callState.value
            if (currentState is CallState.Connecting) {
                Log.w(TAG, "Connection timeout after ${connectionTimeoutMs}ms")
                
                // 确定错误消息
                val errorMessage = when {
                    connectionMode == ConnectionMode.RELAY_ONLY -> 
                        "连接超时 - TURN 服务器可能不可用"
                    else -> 
                        "连接超时 - 请检查网络或对方状态"
                }
                
                _callState.value = CallState.Error(errorMessage)
                endCall()
            } else if (currentState is CallState.Ringing && currentState.isIncoming) {
                // 来电铃声超时（默认20秒未接听会自动挂断）
                Log.w(TAG, "Incoming call timeout")
            }
        }
    }
    
    /**
     * 取消连接超时检测
     * 当连接成功或通话结束时调用
     */
    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }
    
    /**
     * 结束通话
     */
    private fun endCall() {
        durationJob?.cancel()
        connectionTimeoutJob?.cancel()
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
        
        if (!isCleanedUp) {
            durationJob?.cancel()
            connectionTimeoutJob?.cancel()  // 取消连接超时
            audioManager.stop()
            webRtcRepository.dispose()
            currentUserId?.let { MicLinkService.endCall(getApplication(), it) }
            isCleanedUp = true
        }
    }
    
    /**
     * 显示来电界面 - 通过改变状态，在app内全屏显示
     */
    private fun showIncomingCallScreen(callerId: String) {
        // 直接改变通话状态为Ringing(来电)，UI会自动显示来电界面
        _callState.value = CallState.Ringing(
            peerId = callerId,
            isIncoming = true
        )
    }
}
