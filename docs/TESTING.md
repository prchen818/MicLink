# ✅ MicLink 测试清单与故障排查指南

## 📋 测试清单

### 编译和安装

- [ ] JDK 17+ 已安装
- [ ] Android Studio 已安装
- [ ] 项目可以成功编译
- [ ] APK 可以安装到设备
- [ ] 应用启动时请求麦克风权限
- [ ] 权限授予后应用可以正常启动

### 登录和连接

- [ ] 应用主界面显示正常
- [ ] 可以输入用户ID
- [ ] 点击"加入"后显示"连接中..."
- [ ] 连接成功后状态栏显示"在线"
- [ ] 显示当前用户ID
- [ ] 用户列表显示实时更新

### 用户列表

- [ ] 第一个用户加入后，第二个用户可以看到它
- [ ] 用户列表实时更新
- [ ] 显示在线用户数量
- [ ] 用户头像和信息正确显示
- [ ] 可以刷新用户列表

### 服务器测试

- [ ] 服务器可以启动
- [ ] 访问 `http://localhost:8080/health` 返回 `{"status":"ok","online_users":0}`
- [ ] 访问 `http://localhost:8080/users` 返回用户列表JSON
- [ ] 用户连接时服务器日志显示连接信息
- [ ] 多个用户连接时服务器能正确管理

### 发起通话

- [ ] 点击用户可以发起通话
- [ ] 显示"呼叫中..."状态
- [ ] 发起通话时不能再点击其他用户
- [ ] 超时后自动取消呼叫
- [ ] 可以主动取消呼叫

### 接听通话

- [ ] 对方收到来电通知
- [ ] 显示来电界面
- [ ] 可以看到"接听"和"拒绝"按钮
- [ ] 点击"接听"建立连接
- [ ] 点击"拒绝"挂断来电
- [ ] 拒绝后返回主界面

### 通话中

- [ ] 显示连接类型（P2P直连/服务器中转）
- [ ] 显示通话时长计时器
- [ ] 显示对方用户ID
- [ ] 可以听到对方声音
- [ ] 对方可以听到自己声音
- [ ] 静音按钮可用
- [ ] 扬声器按钮可用
- [ ] 挂断按钮可用

### 通话控制

- [ ] 静音后对方听不到声音
- [ ] 再次点击恢复声音
- [ ] 可以在听筒和扬声器间切换
- [ ] 扬声器模式下音量足够大
- [ ] 听筒模式下音量适中
- [ ] 点击挂断结束通话

### 挂断通话

- [ ] 主动挂断后返回主界面
- [ ] 对方挂断时收到通知
- [ ] 通话时长归零
- [ ] 静音和扬声器状态重置
- [ ] 可以立即发起新的通话

### 连接模式测试

**自动模式 (AUTO)**
- [ ] 默认使用自动模式
- [ ] 优先尝试P2P连接
- [ ] P2P成功显示"P2P直连"
- [ ] P2P失败后降级到中转

**仅P2P模式**
- [ ] 设置仅P2P模式
- [ ] 发起通话
- [ ] 成功时显示"P2P直连"
- [ ] 失败时显示错误或超时

**仅中转模式**
- [ ] 设置仅中转模式
- [ ] 发起通话
- [ ] 显示"服务器中转"
- [ ] 需要部署TURN服务器

### 音质测试

**低质量 (LOW)**
- [ ] 选择低质量
- [ ] 发起通话
- [ ] 流量消耗较少
- [ ] 声音清晰度降低但流畅

**中等质量 (MEDIUM)**
- [ ] 选择中等质量
- [ ] 发起通话
- [ ] 流量消耗适中
- [ ] 声音清晰流畅（推荐）

**高质量 (HIGH)**
- [ ] 选择高质量
- [ ] 发起通话
- [ ] 流量消耗较大
- [ ] 声音高清

### 设置功能

- [ ] 可以打开设置对话框
- [ ] 可以选择连接模式（自动/P2P/中转）
- [ ] 可以选择音质（低/中/高）
- [ ] 设置保存并生效
- [ ] 关闭设置后设置被保存

### 网络环境测试

**WiFi环境**
- [ ] WiFi下可以正常连接
- [ ] WiFi下通话质量良好
- [ ] WiFi下延迟较低

**4G/5G环境**
- [ ] 移动网络下可以连接
- [ ] 移动网络下可以通话
- [ ] 自动适应网络变化

**网络切换**
- [ ] 通话中从WiFi切换到4G
- [ ] 连接自动恢复或重建
- [ ] 通话继续进行

### 权限问题

- [ ] 拒绝麦克风权限后提示
- [ ] 可以引导用户授权
- [ ] 权限不足时禁用通话
- [ ] 重新授权后可以通话

### 网络异常

- [ ] 服务器断开后显示错误
- [ ] 自动重连机制正常
- [ ] 对方掉线后挂断
- [ ] 超时处理正确

### 并发情况

- [ ] 同时两人呼叫同一用户
- [ ] 多人同时在线
- [ ] 正在通话时收到新来电拒绝

### 资源占用

- [ ] CPU使用率 < 10%（通话中）
- [ ] 内存使用 < 100MB
- [ ] 不会过度发热
- [ ] 电池消耗正常

### 长时间通话

- [ ] 通话10分钟无断线
- [ ] 通话30分钟稳定
- [ ] 音质无明显下降

### 用户ID验证

- [ ] 空ID无法加入
- [ ] 特殊字符ID被拒绝
- [ ] 重复ID显示错误
- [ ] 只允许字母和数字

### 日志和调试

- [ ] 关键操作有日志记录
- [ ] 错误信息清晰明确
- [ ] Logcat可以看到MicLink日志
- [ ] 连接状态变化有记录
- [ ] ICE候选收集过程可见
- [ ] WebRTC统计信息可查看

---

## 🔧 故障排查指南

### 1️⃣ 服务器无法启动

#### 症状
```
panic: listen tcp :8080: bind: address already in use
```

#### 原因
端口8080已被占用

#### 解决方案

**Windows：**
```powershell
# 查找占用8080端口的进程
netstat -ano | findstr :8080

# 结束进程（替换PID为实际进程ID）
taskkill /PID <PID> /F

# 或改用其他端口
go run cmd/server/main.go -port=8081
```

**Linux/macOS：**
```bash
# 查找占用8080端口的进程
lsof -i :8080

# 结束进程
kill -9 <PID>
```

---

### 2️⃣ Android客户端无法连接服务器

#### 症状
- 显示"连接中..."一直无响应
- 显示"连接失败"
- Logcat显示连接超时

#### 排查步骤

**Step 1: 检查服务器是否运行**
```powershell
# 浏览器访问
http://localhost:8080/health

# 应该返回
{"status":"ok","online_users":0}
```

**Step 2: 检查IP配置**
```kotlin
// android/app/src/main/kotlin/com/miclink/network/Config.kt
private const val DEV_SERVER_IP = "192.168.1.100"  // 确保是你的电脑IP
```

**Step 3: 检查网络**
- 手机和电脑在同一WiFi网络吗？
- 防火墙允许8080端口吗？

**Step 4: 测试连接**
```powershell
# 在手机浏览器访问
http://你的电脑IP:8080/health

# 或使用命令
curl http://你的电脑IP:8080/health
```

**Step 5: 关闭防火墙测试**
```powershell
# Windows - 临时关闭防火墙
# 控制面板 -> Windows Defender 防火墙 -> 关闭

# 或添加规则
netsh advfirewall firewall add rule name="MicLink" dir=in action=allow protocol=TCP localport=8080
```

---

### 3️⃣ 编译失败

#### 现象A: Gradle同步失败
```
Could not resolve dependencies
```

#### 解决方案
```powershell
cd android

# 清理缓存
.\gradlew clean

# 强制刷新依赖
.\gradlew build --refresh-dependencies
```

#### 现象B: JDK版本不对
```
Unsupported class file major version
```

#### 解决方案
- 确保使用JDK 17或更高版本
- Android Studio: File -> Project Structure -> SDK Location -> JDK location

---

### 4️⃣ 无法听到声音

#### 排查清单

**检查1: 权限**
```
设置 -> 应用 -> MicLink -> 权限 -> 麦克风（允许）
```

**检查2: 音量**
- 设备音量是否开启？
- 是否静音？
- 尝试切换扬声器模式

**检查3: WebRTC连接**
```powershell
查看Logcat日志:
adb logcat | findstr "WebRTC"

确认看到:
- "ICE connection state: CONNECTED"
- "Created offer/answer"
```

**检查4: 音频设备**
```
尝试:
1. 拔掉耳机测试
2. 切换扬声器/听筒
3. 重启应用
```

---

### 5️⃣ 看不到在线用户

#### 可能原因

**原因1: 用户ID重复**
- 两台设备使用了相同的ID
- 更换不同的ID重试

**原因2: WebSocket未连接**
```
查看日志:
adb logcat | findstr "SignalingRepository"

应该看到:
"Connected to signaling server"
```

**原因3: 服务器未收到用户列表更新**
```powershell
# 检查服务器日志
curl http://localhost:8080/users

# 应该返回在线用户列表
{"users":["user1","user2"]}
```

---

### 6️⃣ P2P连接失败

#### 症状
显示"服务器中转"而不是"P2P直连"

#### 原因分析

**1. NAT类型限制**
- Symmetric NAT无法P2P
- 需要TURN服务器

**2. 防火墙阻止UDP**
- 检查防火墙UDP端口
- 尝试关闭防火墙测试

**3. 网络环境**
- 某些企业网络禁用P2P
- 使用移动网络测试

#### 验证方法
```
设置 -> 仅P2P模式 -> 发起通话
查看是否成功建立连接
```

---

### 7️⃣ 通话延迟高

#### 优化建议

**1. 检查网络**
```powershell
# 测试延迟
ping 服务器IP

# 应该 < 50ms
```

**2. 降低音质**
```
设置 -> 音频质量 -> 低质量
```

**3. 使用P2P模式**
```
设置 -> 连接模式 -> 仅P2P
```

**4. 优化网络**
- 关闭其他占用带宽的应用
- 使用5GHz WiFi而不是2.4GHz
- 靠近路由器

---

### 8️⃣ 应用崩溃

#### 排查步骤

**Step 1: 查看崩溃日志**
```powershell
adb logcat -b crash
```

**Step 2: 常见崩溃原因**

```
1. 权限未授予
   -> 确保授予麦克风权限

2. WebRTC初始化失败
   -> 检查设备是否支持WebRTC
   -> 清除应用数据重试

3. 网络异常
   -> 检查网络连接
   -> 添加异常处理
```

**Step 3: 清除应用数据**
```
设置 -> 应用 -> MicLink -> 存储 -> 清除数据
```

---

### 9️⃣ 内存泄漏

#### 检测方法
```
Android Studio -> Profiler -> Memory
观察内存增长曲线
```

#### 常见原因
- ViewModel未正确释放
- WebSocket连接未关闭
- 监听器未注销

#### 解决方案
```kotlin
// 确保在Activity销毁时清理
override fun onDestroy() {
    super.onDestroy()
    viewModel.disconnect()
}
```

---

### 🔟 其他问题

#### 用户ID验证失败
```
错误: "用户ID只能包含字母和数字"

解决: 只使用 a-z, A-Z, 0-9
不要使用空格、特殊字符、中文
```

#### 设置不生效
```
问题: 修改连接模式或音质后无效

解决: 
1. 重新发起通话
2. 设置在发起通话前修改
```

#### 重复连接
```
错误: "User ID already exists"

解决:
1. 断开之前的连接
2. 使用不同的用户ID
3. 重启应用
```

---

## 📊 诊断工具

### 检查服务器状态
```powershell
# 健康检查
curl http://localhost:8080/health

# 在线用户
curl http://localhost:8080/users
```

### 查看Android日志
```powershell
# 所有MicLink日志
adb logcat | findstr MicLink

# WebRTC日志
adb logcat | findstr WebRTC

# 信令日志
adb logcat | findstr Signaling

# 仅错误
adb logcat *:E
```

### 网络诊断
```powershell
# 测试连接
telnet 服务器IP 8080

# 测试WebSocket
# 使用在线工具: http://www.websocket.org/echo.html
```

### WebRTC诊断
```
Chrome/Chromium:
访问 chrome://webrtc-internals
查看实时连接统计信息
```

---

## 🆘 获取帮助

如果以上方法都无法解决问题:

1. **收集信息**
   - 服务器日志
   - Android Logcat日志
   - 错误截图
   - 复现步骤

2. **检查文档**
   - [快速启动](QUICKSTART.md)
   - [架构设计](../docs/ARCHITECTURE.md)
   - [API文档](../docs/API.md)
   - [开发指南](../docs/DEVELOPMENT.md)

3. **查看代码**
   - 相关源码
   - 代码注释

---

## 💡 最佳实践

### 开发调试
- 启用详细日志：`Config.DEBUG_ENABLED = true`
- 使用Android Studio Profiler监控性能
- 使用`chrome://webrtc-internals`查看WebRTC统计

### 测试建议
- 先在局域网测试
- 使用真机而不是模拟器
- 准备两台物理设备
- 测试不同网络环境

### 部署建议
- 服务器部署到云端
- 配置TURN服务器
- 启用HTTPS/WSS
- 添加监控和日志

---

## 📝 测试报告模板

### 基本信息
- 测试日期：___________
- 服务器系统：___________
- Go版本：___________
- Android设备1：___________
- Android设备2：___________
- 网络环境：___________

### 测试结果统计
- 总测试项：___
- 通过：___
- 失败：___
- 跳过：___

### 问题记录

| 问题描述 | 严重程度 | 复现步骤 | 状态 |
|---------|---------|---------|------|
| | | | |

---

**最后更新**：2026年1月
**版本**：1.0.0
