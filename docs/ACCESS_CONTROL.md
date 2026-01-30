# MicLink 访问控制实现总结

## ✅ 已完成的更改

### 服务器端

1. **新增文件**:
   - [server/internal/config/config.go](../server/internal/config/config.go) - 配置管理模块
   - [server/internal/middleware/auth.go](../server/internal/middleware/auth.go) - 认证中间件
   - [server/.env.example](../server/.env.example) - 环境变量示例

2. **修改文件**:
   - [server/cmd/server/main.go](../server/cmd/server/main.go) - 添加认证中间件和配置加载

3. **配置文件**:
   - [docker/docker-compose.yml](../docker/docker-compose.yml) - 添加环境变量支持
   - [docker/.env.example](../docker/.env.example) - Docker环境变量示例
   - [start-server.bat](../start-server.bat) - 添加API密钥配置

### 客户端

1. **修改文件**:
   - [android/app/src/main/kotlin/com/miclink/network/Config.kt](../android/app/src/main/kotlin/com/miclink/network/Config.kt) - 添加API_KEY配置
   - [android/app/src/main/kotlin/com/miclink/network/SignalingClient.kt](../android/app/src/main/kotlin/com/miclink/network/SignalingClient.kt) - WebSocket连接时发送API密钥

### 文档

1. **新增文档**:
   - [docs/SECURITY.md](../docs/SECURITY.md) - 完整安全部署指南
   - [docs/SECURITY_QUICK.md](../docs/SECURITY_QUICK.md) - 快速参考指南

2. **更新文档**:
   - [README.md](../README.md) - 添加安全配置说明
   - [setup-env.ps1](../setup-env.ps1) - 添加服务器环境变量注释

## 🔐 安全特性

### 1. API密钥认证（必需）
- 所有WebSocket连接必须提供有效的API密钥
- 支持三种传递方式：
  - 查询参数: `?api_key=YOUR_KEY`
  - HTTP Header: `X-API-Key: YOUR_KEY`
  - Authorization Header: `Bearer YOUR_KEY`

### 2. IP白名单（可选）
- 可配置允许访问的IP地址列表
- 适用于已知客户端IP的场景

## 📝 部署步骤

### 快速开始（开发环境）

#### Windows:
1. **启动服务器**（自动编译）:
   ```powershell
   .\start-server.bat
   ```
   默认使用密钥: `miclink-default-key-change-in-production`

2. **配置客户端**:
   确保 `Config.kt` 中的 `API_KEY` 与服务器匹配（默认已匹配）

3. **编译运行**:
   ```powershell
   cd android
   .\gradlew installDebug
   ```

#### Linux/Mac:
1. **启动服务器**（自动编译）:
   ```bash
   chmod +x start-server.sh
   ./start-server.sh
   ```

2. **配置客户端**: 同上

3. **编译运行**: 同上

### 生产环境部署

#### Windows:
1. **生成强密钥**:
   ```powershell
   -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})
   ```

2. **配置服务器**:
   编辑 `start-server.bat`:
   ```batch
   set API_KEY=YOUR_GENERATED_KEY
   set ENABLE_IP_WHITELIST=false
   ```

3. **配置客户端**:
   编辑 `Config.kt`:
   ```kotlin
   const val API_KEY = "YOUR_GENERATED_KEY"
   ```

4. **启动服务器**:
   ```powershell
   .\start-server.bat
   ```

#### Linux/Mac:
1. **生成强密钥**:
   ```bash
   openssl rand -base64 32
   ```

2. **配置服务器**:
   ```bash
   # 方式A: 编辑脚本
   # 修改 start-server.sh 中的 API_KEY 变量
   
   # 方式B: 使用环境变量
   export API_KEY=YOUR_GENERATED_KEY
   export ENABLE_IP_WHITELIST=false
   ```

3. **配置客户端**: 同上

4. **启动服务器**:
   ```bash
   ./start-server.sh
   ```

### （可选）启用IP白名单

#### Windows:
编辑 `start-server.bat`:
```batch
set ENABLE_IP_WHITELIST=true
set ALLOWED_IPS=192.168.1.100,192.168.1.101
```

#### Linux/Mac:
编辑 `start-server.sh` 或设置环境变量:
```bash
export ENABLE_IP_WHITELIST=true
export ALLOWED_IPS=192.168.1.100,192.168.1.101
./start-server.sh
```

## 🧪 测试认证

### 测试未授权访问（应该失败）
```powershell
# 使用错误的密钥
curl -i "http://your-server:8080/ws?api_key=wrong-key"
# 预期: 401 Unauthorized
```

### 测试授权访问（应该成功）
```powershell
# 使用正确的密钥
wscat -c "ws://your-server:8080/ws?api_key=miclink-default-key-change-in-production"
# 预期: WebSocket连接成功
```

## 🔧 工作原理

### 认证流程

1. **客户端发起连接**:
   ```kotlin
   // SignalingClient.kt
   val url = "$serverUrl?api_key=${Config.API_KEY}"
   request.addHeader("X-API-Key", Config.API_KEY)
   ```

2. **服务器验证**:
   ```go
   // middleware/auth.go
   apiKey := c.Query("api_key")
   if !cfg.ValidateAPIKey(apiKey) {
       c.JSON(401, gin.H{"error": "Invalid API key"})
   }
   ```

3. **连接建立或拒绝**:
   - ✅ 密钥正确 → WebSocket升级成功
   - ❌ 密钥错误 → 返回401，连接关闭

### IP白名单流程（可选）

1. **提取客户端IP**:
   ```go
   clientIP := c.ClientIP()
   ```

2. **检查白名单**:
   ```go
   if !cfg.IsIPAllowed(clientIP) {
       return 403 Forbidden
   }
   ```

## 📊 配置选项

| 环境变量 | 说明 | 默认值 | 必需 |
|---------|------|--------|------|
| `API_KEY` | API密钥 | `miclink-default-key...` | 是 |
| `SERVER_PORT` | 服务器端口 | `8080` | 否 |
| `ENABLE_IP_WHITELIST` | 启用IP白名单 | `false` | 否 |
| `ALLOWED_IPS` | 允许的IP列表 | 空 | 否* |

*仅当 `ENABLE_IP_WHITELIST=true` 时需要

## 🛡️ 安全建议

1. ✅ **生产环境必须修改默认密钥**
2. ✅ **使用强随机密钥（32+字符）**
3. ✅ **定期轮换密钥（建议90天）**
4. ✅ **使用HTTPS/WSS加密传输**
5. ✅ **记录访问日志，监控异常**
6. ✅ **客户端使用BuildConfig存储密钥**

## 📚 相关文档

- [完整安全指南](SECURITY.md) - 详细配置和最佳实践
- [快速参考](SECURITY_QUICK.md) - 常用配置命令
- [部署指南](DEPLOYMENT.md) - 生产环境部署
- [API文档](API.md) - WebSocket协议详情

## 🐛 故障排查

### 客户端无法连接

1. **检查密钥是否匹配**:
   - 服务器: 查看启动日志显示的API Key
   - 客户端: 检查 `Config.kt` 中的 `API_KEY`

2. **查看服务器日志**:
   ```
   Invalid API key from IP: xxx.xxx.xxx.xxx  # 密钥错误
   IP not allowed: xxx.xxx.xxx.xxx            # IP不在白名单
   Authentication successful for IP: xxx      # 认证成功
   ```

3. **测试网络连接**:
   ```powershell
   # 测试服务器可达性
   curl http://your-server:8080/health
   ```

### 常见错误

- `401 Unauthorized` - API密钥无效或缺失
- `403 Forbidden` - IP不在白名单中
- 连接立即断开 - 检查客户端日志查看详细错误

## ✨ 特性总结

✅ **双重认证**: API密钥 + IP白名单（可选）
✅ **灵活配置**: 环境变量或脚本配置
✅ **多种传递方式**: 查询参数、Header、Authorization
✅ **详细日志**: 记录所有认证尝试
✅ **开发友好**: 默认配置开箱即用
✅ **生产就绪**: 支持强密钥和IP限制

---

**部署完成后，请参考 [SECURITY.md](SECURITY.md) 进行生产环境加固！**
