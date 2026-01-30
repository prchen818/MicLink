@echo off
echo ========================================
echo   MicLink 信令服务器启动脚本
echo ========================================
echo.

REM 设置环境变量 - 请修改这些值以匹配您的配置
set API_KEY=miclink-default-key-change-in-production
set SERVER_PORT=8080
set ENABLE_IP_WHITELIST=false
set ALLOWED_IPS=

cd server

echo [1/4] 检查Go环境...
go version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Go环境，请先安装Go
    echo 下载地址: https://go.dev/dl/
    pause
    exit /b 1
)
echo Go环境检查通过

echo.
echo [2/4] 下载依赖...
go mod download
if errorlevel 1 (
    echo 错误: 依赖下载失败
    pause
    exit /b 1
)

echo.
echo [3/4] 编译服务器...
go build -o bin\miclink-server.exe cmd\server\main.go
if errorlevel 1 (
    echo 错误: 编译失败
    pause
    exit /b 1
)
echo 编译成功

echo.
echo [4/4] 启动服务器...
echo 服务器地址: http://localhost:%SERVER_PORT%
echo 认证: 已启用 (API_KEY: %API_KEY:~0,4%...%API_KEY:~-4%)
if "%ENABLE_IP_WHITELIST%"=="true" (
    echo IP白名单: 已启用
)
echo 按 Ctrl+C 停止服务器
echo.
echo ========================================
echo.

bin\miclink-server.exe

pause
