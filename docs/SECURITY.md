# 安全部署指南

## 概述

MicLink支持基于API密钥和IP白名单的双重认证机制，确保只有授权的客户端能够访问服务器。

## 认证机制

### 1. API密钥认证（必需）

所有WebSocket连接必须提供有效的API密钥。服务器支持以下三种方式接收密钥：

- **查询参数**: `ws://server:8080/ws?api_key=YOUR_KEY`
- **HTTP Header**: `X-API-Key: YOUR_KEY`
- **Authorization Header**: `Authorization: Bearer YOUR_KEY`

### 2. IP白名单（可选）

可以配置IP白名单，进一步限制访问源。

## 服务器端配置

### 步骤1: 配置环境变量

1. 复制环境变量示例文件：
   ```bash
   cd server
   cp .env.example .env
   ```

2. 编辑`.env`文件，设置API密钥：
   ```bash
   # 生成随机密钥（推荐）
   # Linux/Mac:
   openssl rand -base64 32
   
   # Windows PowerShell:
   -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})
   ```

3. 将生成的密钥填入`.env`:
   ```env
   API_KEY=your-generated-strong-random-key-here
   ```

### 步骤2: 配置IP白名单（可选）

如果需要限制特定IP访问，编辑`.env`:

```env
ENABLE_IP_WHITELIST=true
ALLOWED_IPS=192.168.1.100,192.168.1.101,10.0.0.5
```

### 步骤3: 加载环境变量并启动服务器

#### Linux/Mac:
```bash
# 方式1: 使用启动脚本（推荐）
# 脚本会自动编译和启动
export API_KEY=your-api-key-here
./start-server.sh

# 方式2: 手动加载.env文件后启动
export $(cat .env | xargs)
go build -o bin/miclink-server cmd/server/main.go
./bin/miclink-server
```

或使用Docker:
```bash
docker-compose up -d
```

#### Windows PowerShell:
```powershell
# 方式1: 使用启动脚本（推荐）
# 脚本会自动编译和启动
.\start-server.bat

# 方式2: 手动加载.env文件后启动
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2])
    }
}
cd server
go build -o bin\miclink-server.exe cmd\server\main.go
.\bin\miclink-server.exe
```

或直接修改脚本后启动:
```batch
@echo off
set API_KEY=your-api-key-here
set SERVER_PORT=8080
set ENABLE_IP_WHITELIST=false
.\start-server.bat
```

## 客户端配置

### Android客户端

编辑`android/app/src/main/kotlin/com/miclink/network/Config.kt`:

```kotlin
object Config {
    // 服务器地址
    private const val DEV_SERVER_IP = "YOUR_SERVER_IP"
    
    // API密钥（必须与服务器端匹配）
    const val API_KEY = "your-generated-strong-random-key-here"
}
```

**重要**: 确保客户端的`API_KEY`与服务器端的`API_KEY`环境变量完全一致。

### 安全建议

1. **不要硬编码生产密钥**: 
   - 开发环境可以使用配置文件
   - 生产环境建议使用BuildConfig或密钥管理服务

2. **使用BuildConfig（推荐）**:
   
   在`android/app/build.gradle.kts`中：
   ```kotlin
   android {
       buildTypes {
           release {
               buildConfigField("String", "API_KEY", "\"${System.getenv("MICLINK_API_KEY")}\"")
           }
           debug {
               buildConfigField("String", "API_KEY", "\"miclink-dev-key\"")
           }
       }
   }
   ```
   
   然后在代码中使用：
   ```kotlin
   const val API_KEY = BuildConfig.API_KEY
   ```

## Docker部署

编辑`docker/docker-compose.yml`:

```yaml
version: '3.8'
services:
  miclink-server:
    environment:
      - API_KEY=${API_KEY}
      - SERVER_PORT=8080
      - ENABLE_IP_WHITELIST=${ENABLE_IP_WHITELIST:-false}
      - ALLOWED_IPS=${ALLOWED_IPS}
```

创建`.env`文件在docker目录：
```env
API_KEY=your-production-key
ENABLE_IP_WHITELIST=true
ALLOWED_IPS=client-ip-1,client-ip-2
```

启动：
```bash
cd docker
docker-compose up -d
```

## 验证部署

### 1. 测试未授权访问

```bash
# 应该返回401 Unauthorized
curl -i http://your-server:8080/ws
```

### 2. 测试授权访问

```bash
# 应该成功升级到WebSocket
wscat -c "ws://your-server:8080/ws?api_key=your-api-key"
```

### 3. 检查服务器日志

启动服务器后，应该看到：
```
Server configuration loaded:
  Port: 8080
  API Key: your...here
  IP Whitelist Enabled: false
Authentication: ENABLED
```

## 故障排查

### 客户端无法连接

1. **检查API密钥**:
   - 确保客户端和服务器的API_KEY完全一致
   - 检查是否有多余的空格或换行符

2. **检查IP白名单**:
   - 如果启用了IP白名单，确保客户端IP在列表中
   - 使用`curl`测试服务器可达性

3. **检查日志**:
   - 服务器日志会显示认证失败的原因
   - 客户端日志会显示连接错误详情

### 常见错误

- `Invalid API key`: API密钥不匹配
- `Access denied: IP not in whitelist`: IP不在白名单中
- `401 Unauthorized`: 未提供API密钥或密钥无效

## 最佳实践

1. **定期轮换密钥**: 建议每90天更换一次API密钥
2. **使用HTTPS/WSS**: 生产环境必须使用加密连接
3. **监控访问日志**: 定期检查异常访问尝试
4. **限制重试次数**: 防止暴力破解
5. **最小权限原则**: 仅开放必要的端口和IP

## 生产环境检查清单

- [ ] 已设置强随机API密钥
- [ ] 客户端和服务器API密钥匹配
- [ ] 使用HTTPS/WSS加密连接
- [ ] 已配置防火墙规则
- [ ] IP白名单已正确配置（如需要）
- [ ] 日志监控已启用
- [ ] 备份和恢复计划已制定
