@echo off
echo ========================================
echo   MicLink 信令服务器启动脚本
echo ========================================
echo.

cd server

echo [1/3] 检查Go环境...
go version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Go环境，请先安装Go
    echo 下载地址: https://go.dev/dl/
    pause
    exit /b 1
)
echo Go环境检查通过

echo.
echo [2/3] 下载依赖...
go mod download
if errorlevel 1 (
    echo 错误: 依赖下载失败
    pause
    exit /b 1
)

echo.
echo [3/3] 启动服务器...
echo 服务器地址: http://localhost:8080
echo 按 Ctrl+C 停止服务器
echo.
echo ========================================
echo.

go run cmd/server/main.go

pause
