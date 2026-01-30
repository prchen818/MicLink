package main

import (
	"log"
	"net/http"

	"miclink-server/internal/config"
	"miclink-server/internal/middleware"
	"miclink-server/internal/signaling"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
)

func main() {
	// 加载配置
	cfg := config.LoadConfig()

	// 创建信令服务器
	signalingServer := signaling.NewServer()

	// 启动信令服务器
	go signalingServer.Run()

	// 创建HTTP服务器
	router := gin.Default()

	// 配置CORS
	corsConfig := cors.DefaultConfig()
	corsConfig.AllowAllOrigins = true
	corsConfig.AllowHeaders = []string{"Origin", "Content-Length", "Content-Type", "X-API-Key", "Authorization"}
	router.Use(cors.New(corsConfig))

	// WebSocket路由 - 需要认证
	router.GET("/ws", middleware.AuthMiddleware(), func(c *gin.Context) {
		signalingServer.HandleWebSocket(c.Writer, c.Request)
	})

	// 健康检查
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status":       "ok",
			"online_users": len(signalingServer.GetOnlineUsers()),
		})
	})

	// 获取在线用户
	router.GET("/users", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"users": signalingServer.GetOnlineUsers(),
		})
	})

	// 启动服务器
	serverAddr := ":" + cfg.Port
	log.Printf("MicLink Signaling Server starting on %s", serverAddr)
	log.Println("Authentication: ENABLED")
	if cfg.EnableIPWhitelist {
		log.Println("IP Whitelist: ENABLED")
	}
	if err := router.Run(serverAddr); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
