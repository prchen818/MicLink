package main

import (
	"log"
	"net/http"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"miclink-server/internal/signaling"
)

func main() {
	// 创建信令服务器
	signalingServer := signaling.NewServer()
	
	// 启动信令服务器
	go signalingServer.Run()

	// 创建HTTP服务器
	router := gin.Default()

	// 配置CORS
	config := cors.DefaultConfig()
	config.AllowAllOrigins = true
	config.AllowHeaders = []string{"Origin", "Content-Length", "Content-Type"}
	router.Use(cors.New(config))

	// WebSocket路由
	router.GET("/ws", func(c *gin.Context) {
		signalingServer.HandleWebSocket(c.Writer, c.Request)
	})

	// 健康检查
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status": "ok",
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
	log.Println("MicLink Signaling Server starting on :8080")
	if err := router.Run(":8080"); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
