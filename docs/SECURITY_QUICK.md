# 访问控制快速参考

## 配置步骤

### 1. 服务器端（二选一）

#### 方式A: 修改启动脚本（推荐新手）

编辑 `start-server.bat`:
```batch
set API_KEY=your-strong-random-key-here
```

#### 方式B: 使用环境变量（推荐生产环境）

1. 复制示例配置:
   ```bash
   cd server
   cp .env.example .env
   ```

2. 编辑 `.env`:
   ```env
   API_KEY=your-strong-random-key-here
   ```

3. 加载并启动:
   ```powershell
   # Windows PowerShell
   Get-Content server\.env | ForEach-Object {
       if ($_ -match '^([^=]+)=(.*)$') {
           [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2])
       }
   }
   .\start-server.bat
   ```

### 2. 客户端

编辑 `android/app/src/main/kotlin/com/miclink/network/Config.kt`:
```kotlin
const val API_KEY = "your-strong-random-key-here"  // 必须与服务器相同
```

## 生成强密钥

### Windows PowerShell:
```powershell
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})
```

### Linux/Mac:
```bash
openssl rand -base64 32
```

## 启动服务器

### Windows:
```powershell
# 自动编译并启动
.\start-server.bat
```

### Linux/Mac:
```bash
# 自动编译并启动
./start-server.sh
```

编译后的可执行文件将保存在 `server/bin/` 目录中

## 启用IP白名单（可选）

### Windows:
编辑 `start-server.bat`:
```batch
set ENABLE_IP_WHITELIST=true
set ALLOWED_IPS=192.168.1.100,192.168.1.101
```

### Linux/Mac:
编辑 `start-server.sh`，或在启动前设置环境变量:
```bash
export ENABLE_IP_WHITELIST=true
export ALLOWED_IPS=192.168.1.100,192.168.1.101
./start-server.sh
```

## 验证配置

启动服务器后，查看日志应该显示：
```
Server configuration loaded:
  Port: 8080
  API Key: your...here
Authentication: ENABLED
```

## 常见问题

**Q: 客户端无法连接？**
- 确保API_KEY在客户端和服务器端完全一致
- 检查服务器日志是否显示 "Invalid API key"

**Q: 如何关闭认证？**
- 不建议关闭，但如果需要测试，可以临时注释 [main.go](../server/cmd/server/main.go) 中的 `middleware.AuthMiddleware()` 调用

**Q: Docker部署如何配置？**
- 创建 `docker/.env` 文件，设置 API_KEY
- 运行 `docker-compose up -d`

详细文档请参考 [SECURITY.md](SECURITY.md)
