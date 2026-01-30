# MicLink 开发指南

## 开发环境搭建

### 前置要求

**Android开发**
- JDK 17+
- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 34
- Kotlin 1.9.20+

**后端开发**
- Go 1.21+
- Git

### 克隆项目
```bash
git clone https://github.com/your-username/MicLink.git
cd MicLink
```

## Android客户端开发

### 1. 导入项目
```bash
cd android
# 使用Android Studio打开此目录
```

### 2. 同步依赖
```bash
./gradlew build
```

### 3. 运行应用
```bash
# 连接设备或启动模拟器
./gradlew installDebug

# 或在Android Studio中点击Run
```

### 4. 项目结构说明

```
app/src/main/kotlin/com/miclink/
├── model/          # 数据模型
│   └── Models.kt   # CallState, AudioQuality等
├── network/        # 网络层
│   └── SignalingClient.kt  # WebSocket客户端
├── webrtc/         # WebRTC封装
│   └── WebRtcManager.kt    # 核心音视频引擎
├── repository/     # 数据仓库层 (待实现)
├── viewmodel/      # ViewModel层 (待实现)
└── ui/            # UI层 (待实现)
    ├── MainActivity.kt
    ├── screens/
    │   ├── HomeScreen.kt
    │   └── CallScreen.kt
    └── components/
```

### 5. 添加UI层 (下一步)

**HomeViewModel.kt** - 管理在线用户和通话状态
```kotlin
class HomeViewModel : ViewModel() {
    private val signalingClient = SignalingClient(Config.SERVER_URL)
    private val _onlineUsers = MutableStateFlow<List<String>>(emptyList())
    val onlineUsers: StateFlow<List<String>> = _onlineUsers
}
```

**HomeScreen.kt** - 显示在线用户列表
```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val users by viewModel.onlineUsers.collectAsState()
    LazyColumn {
        items(users) { user ->
            UserItem(user) { 
                viewModel.initiateCall(user)
            }
        }
    }
}
```

### 6. 调试技巧

**查看WebRTC日志**
```kotlin
Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)
```

**查看信令消息**
```kotlin
private val TAG = "SignalingClient"
Log.d(TAG, "Message received: $message")
```

**使用Chrome检查WebRTC统计**
```
chrome://webrtc-internals
```

## 后端开发

### 1. 初始化项目
```bash
cd server
go mod download
```

### 2. 运行服务器
```bash
go run cmd/server/main.go
```

### 3. 热重载开发 (推荐)
```bash
# 安装air
go install github.com/cosmtrek/air@latest

# 运行
air
```

创建 `.air.toml`:
```toml
root = "."
tmp_dir = "tmp"

[build]
cmd = "go build -o ./tmp/main ./cmd/server"
bin = "tmp/main"
include_ext = ["go"]
exclude_dir = ["tmp"]
```

### 4. 测试信令服务器

**使用wscat测试**
```bash
npm install -g wscat
wscat -c ws://localhost:8080/ws

# 发送
> {"type":"join","from":"test1"}

# 接收
< {"type":"user_list","payload":{"users":["test1"]}}
```

**使用curl测试HTTP API**
```bash
curl http://localhost:8080/health
curl http://localhost:8080/users
```

### 5. 添加单元测试

**server_test.go**
```go
func TestUserJoin(t *testing.T) {
    server := NewServer()
    // 测试用户加入逻辑
}
```

运行测试:
```bash
go test ./...
```

## 常见开发任务

### 添加新的信令消息类型

**1. 后端 - 添加消息类型**
```go
// internal/model/message.go
const (
    TypeNewFeature MessageType = "new_feature"
)

type NewFeaturePayload struct {
    Data string `json:"data"`
}
```

**2. 后端 - 处理消息**
```go
// internal/signaling/server.go
func (s *Server) HandleMessage(client *Client, msg *model.Message) {
    switch msg.Type {
    case model.TypeNewFeature:
        // 处理新功能
    }
}
```

**3. Android - 添加模型**
```kotlin
// model/Models.kt
sealed class SignalingMessage {
    data class NewFeature(val data: String) : SignalingMessage() {
        override val type = "new_feature"
    }
}
```

**4. Android - 处理消息**
```kotlin
// network/SignalingClient.kt
when (message["type"]) {
    "new_feature" -> {
        // 处理新功能
    }
}
```

### 修改音频参数

**WebRtcManager.kt**
```kotlin
fun createAudioTrack(quality: AudioQuality) {
    val constraints = MediaConstraints().apply {
        // 修改约束参数
        mandatory.add(MediaConstraints.KeyValuePair("key", "value"))
    }
}
```

### 添加新的连接模式

**1. 定义枚举**
```kotlin
enum class ConnectionMode {
    AUTO,
    P2P_ONLY,
    RELAY_ONLY,
    NEW_MODE  // 新增
}
```

**2. 实现逻辑**
```kotlin
when (connectionMode) {
    ConnectionMode.NEW_MODE -> {
        // 实现新模式
    }
}
```

## 代码规范

### Kotlin代码风格
- 使用ktlint格式化
- 遵循官方Kotlin编码规范
- 使用有意义的变量名

```bash
# 安装ktlint
brew install ktlint

# 格式化
ktlint -F "**/*.kt"
```

### Go代码风格
- 使用gofmt格式化
- 遵循Go官方编码规范

```bash
# 格式化
go fmt ./...

# 静态检查
go vet ./...
```

## Git工作流

### 分支策略
```
main          - 生产环境
develop       - 开发环境
feature/*     - 功能分支
bugfix/*      - 修复分支
```

### 提交规范
```
feat: 添加新功能
fix: 修复bug
docs: 更新文档
style: 代码格式调整
refactor: 重构
test: 添加测试
chore: 构建/工具链更新
```

示例:
```bash
git commit -m "feat: 添加静音功能"
git commit -m "fix: 修复WebSocket重连问题"
```

## 性能分析

### Android性能分析
```kotlin
// 使用Android Profiler
// CPU: 查看方法耗时
// Memory: 检测内存泄漏
// Network: 查看网络请求
```

### Go性能分析
```bash
# CPU分析
go test -cpuprofile=cpu.prof
go tool pprof cpu.prof

# 内存分析
go test -memprofile=mem.prof
go tool pprof mem.prof
```

## 故障排查清单

### WebSocket连接问题
- [ ] 检查服务器是否运行
- [ ] 确认URL格式正确
- [ ] 查看网络权限
- [ ] 检查防火墙设置

### WebRTC连接问题
- [ ] 验证ICE服务器配置
- [ ] 检查音频权限
- [ ] 查看ICE连接状态
- [ ] 确认SDP交换成功

### 音频问题
- [ ] 确认麦克风权限
- [ ] 检查AudioTrack状态
- [ ] 验证音频约束参数
- [ ] 测试不同音质设置

## 有用的资源

### 官方文档
- [WebRTC官方文档](https://webrtc.org/)
- [Jetpack Compose文档](https://developer.android.com/jetpack/compose)
- [Gin框架文档](https://gin-gonic.com/)
- [Gorilla WebSocket文档](https://github.com/gorilla/websocket)

### 社区资源
- [WebRTC Samples](https://webrtc.github.io/samples/)
- [Android WebRTC示例](https://github.com/webrtc/samples)

## 下一步开发计划

1. **UI实现** (优先级: 高)
   - [ ] HomeScreen - 用户列表
   - [ ] CallScreen - 通话界面
   - [ ] SettingsDialog - 设置对话框

2. **Repository层** (优先级: 高)
   - [ ] SignalingRepository
   - [ ] WebRtcRepository

3. **ViewModel层** (优先级: 高)
   - [ ] HomeViewModel
   - [ ] CallViewModel

4. **功能完善** (优先级: 中)
   - [ ] 网络状态监控
   - [ ] 自动重连机制
   - [ ] 通话质量指示器

5. **测试** (优先级: 中)
   - [ ] 单元测试
   - [ ] 集成测试
   - [ ] UI测试

6. **优化** (优先级: 低)
   - [ ] 性能优化
   - [ ] 电池优化
   - [ ] 内存优化
