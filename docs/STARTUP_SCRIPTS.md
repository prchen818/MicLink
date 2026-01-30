# Linux/macOS 启动脚本更新总结

## ✅ 完成的修改

### 1. 启动脚本升级

#### start-server.sh （Linux/macOS）
- ✅ 添加了API密钥环境变量支持
- ✅ 改为**编译后运行**的方式（而不是 `go run`）
- ✅ 自动创建 `bin/` 目录存放编译产物
- ✅ 添加编译步骤状态提示
- ✅ 显示认证配置信息

#### start-server.bat （Windows）
- ✅ 已改为**编译后运行**的方式（保持一致性）
- ✅ 自动创建 `bin\` 目录存放编译产物
- ✅ 显示更详细的启动信息

### 2. 文档更新

以下文档已更新为反映新的启动方式：
- ✅ [docs/SECURITY_QUICK.md](../docs/SECURITY_QUICK.md) - 快速参考指南
- ✅ [docs/SECURITY.md](../docs/SECURITY.md) - 完整安全部署指南
- ✅ [docs/ACCESS_CONTROL.md](../docs/ACCESS_CONTROL.md) - 功能实现总结
- ✅ [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md) - 部署指南

### 3. Git配置

- ✅ 更新 [server/.gitignore](../server/.gitignore) - 添加 `bin/` 和 `dist/` 目录

## 🚀 新的启动方式

### Linux/macOS

```bash
# 1. 给脚本添加执行权限（首次只需做一次）
chmod +x start-server.sh

# 2. 运行脚本
./start-server.sh

# 输出示例：
# ========================================
#   MicLink 信令服务器启动脚本
# ========================================
#
# [1/4] 检查Go环境...
# Go环境检查通过
#
# [2/4] 下载依赖...
# [3/4] 编译服务器...
# 编译成功
#
# [4/4] 启动服务器...
# 服务器地址: http://localhost:8080
# 认证: 已启用 (API_KEY: micl...uction)
# 按 Ctrl+C 停止服务器
```

### Windows

```powershell
# 直接运行批处理脚本
.\start-server.bat

# 输出示例：
# ========================================
#   MicLink 信令服务器启动脚本
# ========================================
#
# [1/4] 检查Go环境...
# Go环境检查通过
#
# [2/4] 下载依赖...
# [3/4] 编译服务器...
# 编译成功
#
# [4/4] 启动服务器...
# 服务器地址: http://localhost:8080
# 认证: 已启用 (API_KEY: micl...uction)
# 按 Ctrl+C 停止服务器
```

## 📋 脚本做了什么

### 1️⃣ 检查Go环境
验证系统是否安装了Go，没有则退出

### 2️⃣ 下载依赖
运行 `go mod download` 下载项目依赖

### 3️⃣ 编译服务器
```bash
# Linux/macOS
go build -o ./bin/miclink-server ./cmd/server/main.go

# Windows
go build -o bin\miclink-server.exe cmd\server\main.go
```
编译后的二进制文件保存在 `server/bin/` 目录

### 4️⃣ 启动服务器
直接运行编译后的二进制文件，而不是 `go run`

## 🔑 环境变量配置

脚本支持以下环境变量（可在脚本中修改或外部设置）：

```bash
# Linux/macOS - 在脚本中或启动前设置
export API_KEY="your-api-key"
export SERVER_PORT="8080"
export ENABLE_IP_WHITELIST="false"
export ALLOWED_IPS=""

./start-server.sh
```

```batch
REM Windows - 在 start-server.bat 中修改
set API_KEY=your-api-key
set SERVER_PORT=8080
set ENABLE_IP_WHITELIST=false
set ALLOWED_IPS=

start-server.bat
```

## ✨ 优势对比

| 方面 | `go run` | 编译后运行 |
|------|----------|-----------|
| **启动速度** | 较慢（需编译） | ⚡ 快（预编译） |
| **资源占用** | 较高 | 📉 较低 |
| **部署方式** | 需要Go环境 | 仅需二进制 |
| **易于分发** | ❌ 不便 | ✅ 容易打包 |
| **生产环境** | ⚠️ 不推荐 | ✅ 推荐 |

## 📁 项目结构更新

```
server/
├── bin/                    # 编译后的二进制文件（新增）
│   ├── miclink-server      # Linux/macOS 可执行文件
│   └── miclink-server.exe  # Windows 可执行文件
├── cmd/
│   └── server/
│       └── main.go
├── internal/
│   ├── config/
│   ├── middleware/
│   ├── model/
│   └── signaling/
├── go.mod
├── go.sum
├── .gitignore              # 已更新
└── README.md
```

## 🔧 常见操作

### 重新编译
```bash
# 脚本会自动重新编译，或手动编译：
cd server
go build -o bin/miclink-server cmd/server/main.go
```

### 清除编译产物
```bash
# Linux/macOS
rm -rf server/bin/

# Windows
rmdir /s /q server\bin
```

### 使用自定义密钥启动

#### Linux/macOS:
```bash
export API_KEY="my-custom-key"
./start-server.sh
```

#### Windows:
编辑 `start-server.bat`，修改：
```batch
set API_KEY=my-custom-key
```

## 📝 第一次使用步骤

### Linux/macOS:
```bash
# 1. 进入项目目录
cd MicLink

# 2. 第一次需要给脚本添加执行权限
chmod +x start-server.sh

# 3. 运行脚本（会自动编译）
./start-server.sh

# 4. 看到 "服务器地址: http://localhost:8080" 表示成功
```

### Windows:
```powershell
# 1. 进入项目目录
cd MicLink

# 2. 直接运行脚本（会自动编译）
.\start-server.bat

# 3. 看到 "服务器地址: http://localhost:8080" 表示成功
```

## 🐛 故障排查

### 编译失败
```
错误: 编译失败
```
**解决方案**:
- 检查Go版本: `go version` （需要 Go 1.21+）
- 清除缓存: `go clean`
- 检查代码是否有语法错误

### 依赖下载失败
```
错误: 依赖下载失败
```
**解决方案**:
- 检查网络连接
- 尝试设置Go代理（中国用户）:
  ```bash
  export GOPROXY=https://goproxy.cn,direct
  ```

### 权限不足 (Linux/macOS)
```bash
chmod +x start-server.sh
```

## 📚 相关文档

- [SECURITY.md](SECURITY.md) - 完整安全部署指南
- [SECURITY_QUICK.md](SECURITY_QUICK.md) - 快速配置参考
- [DEPLOYMENT.md](DEPLOYMENT.md) - 生产环境部署

---

**所有脚本都已测试，可以直接使用！** ✅
