# MicLink 架构设计文档

## 系统架构

### 整体架构图
```
┌─────────────────┐         ┌─────────────────┐
│   Android App   │         │   Android App   │
│    (Client A)   │         │    (Client B)   │
└────────┬────────┘         └────────┬────────┘
         │                           │
         │  WebSocket (信令)          │
         │                           │
         └──────────┬────────────────┘
                    │
         ┌──────────▼──────────┐
         │   Signaling Server  │
         │     (Golang)        │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │   STUN/TURN Server  │
         │     (coturn)        │
         └─────────────────────┘
                    │
         ┌──────────▼──────────┐
         │   P2P Connection    │
         │   (WebRTC Media)    │
         │  Client A <-> B     │
         └─────────────────────┘
```

## 核心组件

### 1. Android客户端架构

#### 1.1 层级结构
```
┌─────────────────────────────────────┐
│           UI Layer (Compose)        │
│  - HomeScreen (在线用户列表)         │
│  - CallScreen (通话界面)             │
│  - SettingsDialog (音质/模式选择)    │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│          ViewModel Layer            │
│  - HomeViewModel                    │
│  - CallViewModel                    │
│  StateFlow + Coroutines             │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│        Repository Layer             │
│  - SignalingRepository              │
│  - WebRtcRepository                 │
└──────────────┬──────────────────────┘
               │
┌──────────────┴──────────────────────┐
│                                     │
▼                                     ▼
┌─────────────────┐    ┌──────────────────────┐
│ Network Layer   │    │   WebRTC Manager     │
│ - WebSocket     │    │ - PeerConnection     │
│ - SignalingAPI  │    │ - AudioManager       │
└─────────────────┘    │ - ICE Handler        │
                       └──────────────────────┘
```

#### 1.2 关键类设计

**WebRtcManager.kt**
```kotlin
class WebRtcManager(
    private val context: Context,
    private val signalingRepository: SignalingRepository
) {
    private var peerConnection: PeerConnection? = null
    private val audioManager: AudioManager
    
    // 初始化WebRTC
    fun initialize()
    
    // 创建Offer (发起方)
    suspend fun createOffer(quality: AudioQuality): String
    
    // 处理Answer (接收方)
    suspend fun handleAnswer(sdp: String)
    
    // 添加ICE候选
    fun addIceCandidate(candidate: IceCandidate)
    
    // 控制功能
    fun toggleMute()
    fun toggleSpeaker()
    fun hangup()
}
```

**SignalingRepository.kt**
```kotlin
class SignalingRepository(private val serverUrl: String) {
    private val webSocket: WebSocket
    private val _messages = MutableSharedFlow<SignalingMessage>()
    val messages: SharedFlow<SignalingMessage> = _messages
    
    // 连接服务器
    suspend fun connect(userId: String)
    
    // 发送信令消息
    suspend fun sendMessage(message: SignalingMessage)
    
    // 发起通话
    suspend fun initiateCall(
        targetId: String,
        mode: ConnectionMode,
        quality: AudioQuality
    )
}
```

**数据模型**
```kotlin
// 连接模式
enum class ConnectionMode {
    AUTO,      // 自动(优先P2P)
    P2P_ONLY,  // 仅P2P
    RELAY_ONLY // 仅中转
}

// 音质配置
enum class AudioQuality {
    LOW,    // 8kHz, 32kbps  - 节省流量
    MEDIUM, // 16kHz, 64kbps - 平衡
    HIGH    // 48kHz, 128kbps - 高清
}

// 信令消息
sealed class SignalingMessage {
    data class Join(val userId: String)
    data class Call(val to: String, val mode: ConnectionMode, val quality: AudioQuality)
    data class Offer(val to: String, val sdp: String)
    data class Answer(val to: String, val sdp: String)
    data class IceCandidate(val to: String, val candidate: String)
    data class Hangup(val to: String)
}
```

### 2. 后端服务器架构

#### 2.1 核心模块

**Server结构**
```go
// cmd/server/main.go
func main() {
    server := signaling.NewServer()
    server.Start(":8080")
}

// internal/signaling/server.go
type Server struct {
    clients    map[string]*Client  // userId -> Client
    mu         sync.RWMutex
    upgrader   websocket.Upgrader
}

type Client struct {
    ID         string
    Conn       *websocket.Conn
    Send       chan []byte
    Server     *Server
}

// 处理WebSocket连接
func (s *Server) HandleConnection(w http.ResponseWriter, r *http.Request)

// 广播用户列表
func (s *Server) BroadcastUserList()

// 转发信令消息
func (s *Server) RelayMessage(from, to string, msg Message)
```

**消息类型定义**
```go
type MessageType string

const (
    TypeJoin         MessageType = "join"
    TypeLeave        MessageType = "leave"
    TypeUserList     MessageType = "user_list"
    TypeCall         MessageType = "call"
    TypeCallResponse MessageType = "call_response"
    TypeOffer        MessageType = "offer"
    TypeAnswer       MessageType = "answer"
    TypeICECandidate MessageType = "ice_candidate"
    TypeHangup       MessageType = "hangup"
)

type Message struct {
    Type    MessageType     `json:"type"`
    From    string          `json:"from,omitempty"`
    To      string          `json:"to,omitempty"`
    Payload json.RawMessage `json:"payload,omitempty"`
}
```

#### 2.2 信令流程

**加入房间**
```go
// Client -> Server
{
  "type": "join",
  "from": "user123"
}

// Server -> All Clients
{
  "type": "user_list",
  "payload": ["user123", "user456", "user789"]
}
```

**发起通话**
```go
// Caller -> Server
{
  "type": "call",
  "from": "user123",
  "to": "user456",
  "payload": {
    "mode": "auto",
    "quality": "high"
  }
}

// Server -> Callee
{
  "type": "call",
  "from": "user123",
  "payload": {
    "mode": "auto",
    "quality": "high"
  }
}

// Callee -> Server
{
  "type": "call_response",
  "from": "user456",
  "to": "user123",
  "payload": {
    "accepted": true
  }
}
```

**WebRTC协商**
```go
// 1. Offer
{
  "type": "offer",
  "from": "user123",
  "to": "user456",
  "payload": {
    "sdp": "v=0\r\no=..."
  }
}

// 2. Answer
{
  "type": "answer",
  "from": "user456",
  "to": "user123",
  "payload": {
    "sdp": "v=0\r\no=..."
  }
}

// 3. ICE Candidates (multiple)
{
  "type": "ice_candidate",
  "from": "user123",
  "to": "user456",
  "payload": {
    "candidate": "candidate:...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

### 3. WebRTC配置

#### 3.1 ICE服务器配置
```kotlin
val iceServers = listOf(
    // STUN服务器 (用于NAT穿透)
    PeerConnection.IceServer.builder("stun:your-server.com:3478").createIceServer(),
    
    // TURN服务器 (中转模式)
    PeerConnection.IceServer.builder("turn:your-server.com:3478")
        .setUsername("username")
        .setPassword("password")
        .createIceServer()
)

val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
    when (connectionMode) {
        ConnectionMode.P2P_ONLY -> {
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
        ConnectionMode.RELAY_ONLY -> {
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
        ConnectionMode.AUTO -> {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
    }
}
```

#### 3.2 音频编码配置
```kotlin
fun getAudioConstraints(quality: AudioQuality): MediaConstraints {
    return MediaConstraints().apply {
        mandatory.addAll(
            when (quality) {
                AudioQuality.LOW -> listOf(
                    MediaConstraints.KeyValuePair("googEchoCancellation", "true"),
                    MediaConstraints.KeyValuePair("googNoiseSuppression", "true"),
                    MediaConstraints.KeyValuePair("maxaveragebitrate", "32000")
                )
                AudioQuality.MEDIUM -> listOf(
                    MediaConstraints.KeyValuePair("googEchoCancellation", "true"),
                    MediaConstraints.KeyValuePair("googNoiseSuppression", "true"),
                    MediaConstraints.KeyValuePair("maxaveragebitrate", "64000")
                )
                AudioQuality.HIGH -> listOf(
                    MediaConstraints.KeyValuePair("googEchoCancellation", "true"),
                    MediaConstraints.KeyValuePair("googNoiseSuppression", "true"),
                    MediaConstraints.KeyValuePair("googHighpassFilter", "true"),
                    MediaConstraints.KeyValuePair("maxaveragebitrate", "128000")
                )
            }
        )
    }
}
```

### 4. 连接模式策略

#### 4.1 自动模式 (AUTO)
```kotlin
class ConnectionStrategy {
    private val p2pTimeout = 5000L // 5秒
    
    suspend fun establishConnection(mode: ConnectionMode) {
        when (mode) {
            ConnectionMode.AUTO -> {
                // 启动P2P尝试
                val p2pJob = launch { tryP2PConnection() }
                
                // 5秒超时后降级
                delay(p2pTimeout)
                if (!isConnected()) {
                    p2pJob.cancel()
                    switchToRelay()
                }
            }
            ConnectionMode.P2P_ONLY -> tryP2PConnection()
            ConnectionMode.RELAY_ONLY -> useRelayOnly()
        }
    }
}
```

### 5. 状态管理

#### 5.1 通话状态
```kotlin
sealed class CallState {
    object Idle : CallState()
    data class Ringing(val peerId: String, val isIncoming: Boolean) : CallState()
    object Connecting : CallState()
    data class Connected(val peerId: String, val connectionType: String) : CallState()
    object Disconnected : CallState()
}

class CallViewModel : ViewModel() {
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()
}
```

## 性能优化

### 1. 延迟优化
- WebSocket使用二进制帧减少序列化开销
- ICE候选即时发送，不等待收集完成
- 启用WebRTC的低延迟模式
- 音频使用Opus编码器 (最佳实时性)

### 2. 带宽优化
- 根据网络状况自动调整码率
- 提供三档音质供用户选择
- 使用TURN时启用数据压缩

### 3. 稳定性
- WebSocket自动重连机制
- ICE连接超时后自动重试
- 网络切换时自动ICE重启

## 安全考虑

### 1. 传输安全
- WebSocket使用WSS (TLS加密)
- WebRTC使用DTLS-SRTP加密媒体流
- TURN服务器配置认证

### 2. 用户隔离
- 每个用户ID唯一
- 不允许重复ID登录
- 消息只转发给指定目标

## 扩展性

虽然当前版本仅支持10人以下规模，但架构设计考虑了未来扩展：
- 服务器采用无状态设计，可水平扩展
- 可引入Redis存储在线用户列表
- 可添加负载均衡支持更多用户
