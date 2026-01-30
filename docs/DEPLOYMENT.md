# MicLink 部署指南

## 📋 部署方案选择

### 开发/测试环境：IP直连（推荐初学者）
```
特点：
- 简单快速，无需域名和SSL
- 仅支持局域网访问
- 配置：ws://192.168.1.100:8080
```

### 生产环境方案A：IP直连（内网部署）
```
适用场景：企业内网、私有部署
特点：
- 无需域名和SSL
- 配置简单
- 仅支持内网访问
- 成本低
```

### 生产环境方案B：域名+SSL（推荐外网）
```
适用场景：云服务器、外网应用
特点：
- 需要域名和SSL证书
- 安全加密
- 支持外网访问
- 推荐用Let's Encrypt免费证书
配置：wss://your-domain.com/ws
```

---

## 服务器部署

### 1. 后端信令服务器部署

#### 环境要求
- Go 1.21+
- 操作系统: Linux/Windows/macOS
- 端口: 8080 (HTTP/WebSocket)

#### 快速部署

**方式一: 使用启动脚本（推荐）**
```bash
# Linux/macOS
chmod +x start-server.sh
./start-server.sh

# Windows
.\start-server.bat
```
脚本会自动进行依赖下载、编译和启动

**方式二: 手动编译运行**
```bash
cd server
go mod download
go build -o bin/miclink-server cmd/server/main.go

# Linux/macOS
./bin/miclink-server

# Windows
.\bin\miclink-server.exe
```

**方式三: Docker部署**
```bash
cd docker
docker-compose up -d
```

#### 配置文件
创建 `server/config/config.yaml`:
```yaml
server:
  port: 8080
  host: "0.0.0.0"

websocket:
  read_buffer_size: 1024
  write_buffer_size: 1024
  ping_interval: 30s

cors:
  allow_origins: ["*"]
  allow_headers: ["Origin", "Content-Type"]
```

### 2. TURN/STUN服务器部署 (coturn)

#### 安装coturn

**Ubuntu/Debian**
```bash
sudo apt-get update
sudo apt-get install coturn
```

**CentOS/RHEL**
```bash
sudo yum install coturn
```

**Docker方式**
```bash
docker run -d --network=host \
  -v $(pwd)/turnserver.conf:/etc/coturn/turnserver.conf \
  coturn/coturn
```

#### 配置coturn
编辑 `/etc/turnserver.conf`:
```conf
# 监听端口
listening-port=3478
tls-listening-port=5349

# 外部IP (替换为你的服务器公网IP)
external-ip=YOUR_PUBLIC_IP

# 中继地址范围
min-port=49152
max-port=65535

# 认证
use-auth-secret
static-auth-secret=your-secret-key-change-this

# 域名
realm=your-domain.com

# 日志
verbose
log-file=/var/log/turnserver.log

# 性能优化
total-quota=100
max-bps=3000000

# 禁用不需要的协议
no-tcp-relay
no-multicast-peers
```

#### 启动coturn
```bash
sudo systemctl enable coturn
sudo systemctl start coturn
sudo systemctl status coturn
```

#### 测试TURN服务器
访问: https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/

输入:
```
STUN: stun:YOUR_SERVER_IP:3478
TURN: turn:YOUR_SERVER_IP:3478
Username: (留空，使用secret认证)
Password: (留空)
```

### 3. 防火墙配置

**开放端口**
```bash
# 信令服务器
sudo ufw allow 8080/tcp

# STUN/TURN
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 5349/tcp
sudo ufw allow 49152:65535/udp
```

### 4. Nginx反向代理 (可选)

创建 `/etc/nginx/sites-available/miclink`:
```nginx
server {
    listen 80;
    server_name your-domain.com;

    # WebSocket升级
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 86400;
    }

    # API路由
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

启用配置:
```bash
sudo ln -s /etc/nginx/sites-available/miclink /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### 5. SSL证书配置 (推荐)

使用Let's Encrypt:
```bash
sudo apt-get install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

更新Nginx配置支持WSS:
```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## Android客户端配置

### 0. 开发环境 - 快速配置（IP直连）

编辑 `android/app/src/main/kotlin/com/miclink/network/Config.kt`:
```kotlin
object Config {
    // ⚠️ 开发环境配置 - 改成你的电脑IP
    private const val DEV_SERVER_IP = "192.168.1.100"  // 获取IP: ipconfig (Windows) 或 ifconfig (Linux)
    private const val DEV_SERVER_PORT = 8080
    
    // 使用ws:// (非加密) - 局域网测试
    const val SERVER_URL = "ws://$DEV_SERVER_IP:$DEV_SERVER_PORT/ws"
    
    // 如果手机在模拟器上，使用特殊IP
    // const val SERVER_URL = "ws://10.0.2.2:8080/ws"  // Android模拟器访问主机
}
```

**快速检查**：
```powershell
# 1. 获取你的IP
ipconfig

# 2. 修改Config.kt中的DEV_SERVER_IP

# 3. 在手机浏览器访问验证
http://192.168.1.100:8080/health
```

### 1. 修改服务器地址（生产环境）

编辑 `android/app/src/main/kotlin/com/miclink/network/Config.kt`:
```kotlin
object Config {
    // 生产环境配置
    
    // 方案A: IP直连（内网）
    const val SERVER_URL = "ws://server-ip:8080/ws"
    
    // 方案B: 域名+SSL（推荐外网）
    const val SERVER_URL = "wss://your-domain.com/ws"
    
    // STUN服务器（可选，若使用Google的STUN则无需修改）
    const val STUN_SERVER = "stun:stun.l.google.com:19302"
    // 或使用自己的TURN服务器
    // const val STUN_SERVER = "stun:your-domain.com:3478"
    
    // TURN服务器（可选，仅当需要中继时）
    const val TURN_SERVER = "turn:your-domain.com:3478"
    const val TURN_USERNAME = ""
    const val TURN_CREDENTIAL = "your-secret-key-change-this"
}
```

### 2. 编译APK

**Debug版本**
```bash
cd android
./gradlew assembleDebug
# 输出: android/app/build/outputs/apk/debug/app-debug.apk
```

**Release版本**
```bash
cd android
./gradlew assembleRelease
# 输出: android/app/build/outputs/apk/release/app-release.apk
```

### 3. 签名APK (Release)

生成密钥库:
```bash
keytool -genkey -v -keystore miclink.keystore -alias miclink \
  -keyalg RSA -keysize 2048 -validity 10000
```

配置签名 `android/app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../miclink.keystore")
            storePassword = "your-password"
            keyAlias = "miclink"
            keyPassword = "your-password"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

## Docker Compose一键部署

创建 `docker-compose.yml`:
```yaml
version: '3.8'

services:
  # 信令服务器
  signaling:
    build:
      context: ./server
      dockerfile: ../docker/Dockerfile.server
    ports:
      - "8080:8080"
    restart: unless-stopped
    networks:
      - miclink

  # TURN/STUN服务器
  coturn:
    image: coturn/coturn:latest
    network_mode: host
    volumes:
      - ./docker/turnserver.conf:/etc/coturn/turnserver.conf
    restart: unless-stopped

networks:
  miclink:
    driver: bridge
```

启动:
```bash
docker-compose up -d
```

## 监控与日志

### 1. 查看日志

**信令服务器日志**
```bash
# 直接运行
tail -f server.log

# Docker
docker logs -f miclink
```

**coturn日志**
```bash
tail -f /var/log/turnserver.log
```

### 2. 性能监控

检查在线用户:
```bash
curl http://localhost:8080/health
```

查看用户列表:
```bash
curl http://localhost:8080/users
```

### 3. 资源监控

```bash
# CPU和内存使用
top -p $(pidof miclink-server)

# 网络连接
netstat -anp | grep :8080
```

## 故障排查

### 1. WebSocket连接失败
```bash
# 测试WebSocket连接
wscat -c ws://your-server:8080/ws
```

### 2. TURN服务器无法连接
```bash
# 检查端口
sudo netstat -tulpn | grep 3478

# 检查防火墙
sudo ufw status
```

### 3. P2P连接失败
- 检查STUN服务器是否可达
- 确认防火墙UDP端口已开放
- 验证NAT类型 (Symmetric NAT无法P2P)

### 4. 音频无声音
- 检查Android权限是否授予
- 确认WebRTC PeerConnection状态
- 查看ICE连接状态

## 性能优化

### 1. 服务器优化
```bash
# 增加文件描述符限制
ulimit -n 65535

# 调整TCP参数
sudo sysctl -w net.core.somaxconn=1024
sudo sysctl -w net.ipv4.tcp_max_syn_backlog=2048
```

### 2. 带宽优化
- 使用CDN加速静态资源
- 启用Gzip压缩
- 限制单用户带宽

### 3. 安全加固
- 启用SSL/TLS
- 限制来源IP
- 添加用户认证
- 定期更新依赖

## 备份与恢复

由于本系统无状态，无需备份用户数据。仅需备份：
- 配置文件
- SSL证书
- 密钥库文件

## 扩展性考虑

当前架构支持10人以下规模，若需扩展：

1. **水平扩展**: 使用Redis存储在线用户
2. **负载均衡**: Nginx/HAProxy分发WebSocket连接
3. **集群部署**: 多个信令服务器 + 消息队列

## 成本估算

**小规模部署 (10人)**
- 云服务器: $5-10/月 (1核2G)
- 带宽: ~50GB/月 (中继模式)
- 总计: ~$10/月

**P2P模式下**
- 服务器仅处理信令，带宽消耗极低
- 估计成本: <$5/月
