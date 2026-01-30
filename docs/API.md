# MicLink API文档

## WebSocket信令协议

### 连接

**端点**: `ws://your-server:8080/ws` 或 `wss://your-server/ws`

### 消息格式

所有消息使用JSON格式:
```json
{
  "type": "message_type",
  "from": "sender_user_id",
  "to": "target_user_id",
  "payload": {}
}
```

## 消息类型

### 1. 加入房间 (Join)

**客户端 -> 服务器**
```json
{
  "type": "join",
  "from": "user123"
}
```

**服务器响应**: 广播用户列表给所有在线用户

---

### 2. 用户列表 (User List)

**服务器 -> 所有客户端**
```json
{
  "type": "user_list",
  "payload": {
    "users": ["user123", "user456", "user789"]
  }
}
```

**触发时机**:
- 新用户加入
- 用户离线
- 客户端首次连接

---

### 3. 发起通话 (Call)

**发起方 -> 服务器**
```json
{
  "type": "call",
  "from": "user123",
  "to": "user456",
  "payload": {
    "mode": "auto",        // "auto" | "p2p_only" | "relay_only"
    "quality": "high"      // "low" | "medium" | "high"
  }
}
```

**服务器 -> 接收方**
```json
{
  "type": "call",
  "from": "user123",
  "payload": {
    "mode": "auto",
    "quality": "high"
  }
}
```

---

### 4. 通话响应 (Call Response)

**接收方 -> 服务器**
```json
{
  "type": "call_response",
  "from": "user456",
  "to": "user123",
  "payload": {
    "accepted": true       // true: 接受, false: 拒绝
  }
}
```

**服务器 -> 发起方**
```json
{
  "type": "call_response",
  "from": "user456",
  "payload": {
    "accepted": true
  }
}
```

---

### 5. SDP Offer

**发起方 -> 服务器**
```json
{
  "type": "offer",
  "from": "user123",
  "to": "user456",
  "payload": {
    "sdp": "v=0\r\no=- 123456789 2 IN IP4 127.0.0.1\r\n..."
  }
}
```

**服务器 -> 接收方**
```json
{
  "type": "offer",
  "from": "user123",
  "payload": {
    "sdp": "..."
  }
}
```

---

### 6. SDP Answer

**接收方 -> 服务器**
```json
{
  "type": "answer",
  "from": "user456",
  "to": "user123",
  "payload": {
    "sdp": "v=0\r\no=- 987654321 2 IN IP4 127.0.0.1\r\n..."
  }
}
```

**服务器 -> 发起方**
```json
{
  "type": "answer",
  "from": "user456",
  "payload": {
    "sdp": "..."
  }
}
```

---

### 7. ICE Candidate

**任意方 -> 服务器**
```json
{
  "type": "ice_candidate",
  "from": "user123",
  "to": "user456",
  "payload": {
    "candidate": "candidate:1 1 UDP 2130706431 192.168.1.100 54321 typ host",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

**服务器 -> 对方**
```json
{
  "type": "ice_candidate",
  "from": "user123",
  "payload": {
    "candidate": "...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

**注意**: ICE候选会发送多次，直到收集完成

---

### 8. 挂断 (Hangup)

**任意方 -> 服务器**
```json
{
  "type": "hangup",
  "from": "user123",
  "to": "user456"
}
```

**服务器 -> 对方**
```json
{
  "type": "hangup",
  "from": "user123"
}
```

---

### 9. 错误消息 (Error)

**服务器 -> 客户端**
```json
{
  "type": "error",
  "payload": {
    "message": "User ID already exists"
  }
}
```

**常见错误**:
- `User ID already exists` - 用户ID已被使用
- `Target user not found` - 目标用户不在线
- `Invalid message format` - 消息格式错误

---

## 通话流程示例

### 完整通话建立流程

```
1. 用户A加入
A -> Server: {"type": "join", "from": "userA"}
Server -> All: {"type": "user_list", "payload": {"users": ["userA"]}}

2. 用户B加入
B -> Server: {"type": "join", "from": "userB"}
Server -> All: {"type": "user_list", "payload": {"users": ["userA", "userB"]}}

3. 用户A发起通话
A -> Server: {"type": "call", "from": "userA", "to": "userB", "payload": {...}}
Server -> B: {"type": "call", "from": "userA", "payload": {...}}

4. 用户B接受
B -> Server: {"type": "call_response", "from": "userB", "to": "userA", "payload": {"accepted": true}}
Server -> A: {"type": "call_response", "from": "userB", "payload": {"accepted": true}}

5. WebRTC协商 - Offer
A -> Server: {"type": "offer", "from": "userA", "to": "userB", "payload": {"sdp": "..."}}
Server -> B: {"type": "offer", "from": "userA", "payload": {"sdp": "..."}}

6. WebRTC协商 - Answer
B -> Server: {"type": "answer", "from": "userB", "to": "userA", "payload": {"sdp": "..."}}
Server -> A: {"type": "answer", "from": "userB", "payload": {"sdp": "..."}}

7. ICE候选交换 (多次)
A -> Server: {"type": "ice_candidate", ...}
Server -> B: {"type": "ice_candidate", ...}
B -> Server: {"type": "ice_candidate", ...}
Server -> A: {"type": "ice_candidate", ...}

8. 连接建立 (WebRTC层处理)

9. 通话结束
A -> Server: {"type": "hangup", "from": "userA", "to": "userB"}
Server -> B: {"type": "hangup", "from": "userA"}
```

---

## HTTP API

### 健康检查

**请求**
```
GET /health
```

**响应**
```json
{
  "status": "ok",
  "online_users": 5
}
```

---

### 获取在线用户

**请求**
```
GET /users
```

**响应**
```json
{
  "users": ["user123", "user456", "user789"]
}
```

---

## 音质配置说明

| 质量   | 采样率 | 码率    | 适用场景         |
|--------|--------|---------|------------------|
| LOW    | 8kHz   | 32kbps  | 弱网环境/省流量  |
| MEDIUM | 16kHz  | 64kbps  | 标准通话(推荐)   |
| HIGH   | 48kHz  | 128kbps | 高清通话         |

---

## 连接模式说明

| 模式       | 说明                           | 适用场景                |
|------------|--------------------------------|-------------------------|
| AUTO       | 优先P2P，5秒超时后降级到中转   | 推荐默认使用            |
| P2P_ONLY   | 仅使用P2P直连                  | 测试NAT穿透/局域网      |
| RELAY_ONLY | 强制使用TURN服务器中转         | 严格防火墙环境/调试     |

---

## 错误码

| 状态码 | 说明                     |
|--------|--------------------------|
| 1000   | 正常关闭                 |
| 1001   | 客户端离开               |
| 1002   | 协议错误                 |
| 1003   | 不支持的数据类型         |
| 1006   | 异常关闭（无关闭帧）     |
| 1008   | 策略违规                 |
| 1009   | 消息过大                 |

---

## 客户端实现建议

### 1. 心跳机制
```kotlin
// 每30秒发送ping保持连接
scope.launch {
    while (isActive) {
        delay(30_000)
        webSocket.send("ping")
    }
}
```

### 2. 自动重连
```kotlin
fun reconnect() {
    var attempts = 0
    while (attempts < 5) {
        delay(2000 * attempts) // 指数退避
        try {
            connect()
            break
        } catch (e: Exception) {
            attempts++
        }
    }
}
```

### 3. 消息队列
```kotlin
// 连接成功前缓存消息
val messageQueue = mutableListOf<Message>()
if (!isConnected) {
    messageQueue.add(message)
} else {
    send(message)
}
```

---

## 安全建议

1. **生产环境使用WSS** (WebSocket over TLS)
2. **添加用户认证** (Token验证)
3. **限制消息大小** (防止DDoS)
4. **添加频率限制** (Rate limiting)
5. **验证用户ID格式** (防止注入攻击)

---

## 调试工具

### WebSocket测试
```bash
# 安装wscat
npm install -g wscat

# 连接测试
wscat -c ws://localhost:8080/ws

# 发送消息
> {"type": "join", "from": "test_user"}
```

### 抓包分析
```bash
# 使用tcpdump
sudo tcpdump -i any -A 'port 8080'

# 使用Wireshark
wireshark -i any -f "port 8080"
```

---

## 性能指标

**预期性能**:
- 信令延迟: < 50ms
- P2P音频延迟: 50-200ms
- 中转音频延迟: 100-300ms
- 并发连接: 100+ (单机)
- 消息吞吐: 1000+ msg/s

**资源消耗**:
- 内存: ~10MB per connection
- CPU: < 5% (待机)
- 带宽: ~5KB/s per connection (信令)
