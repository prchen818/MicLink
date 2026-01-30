package middleware

import (
	"log"
	"net/http"
	"strings"

	"miclink-server/internal/config"

	"github.com/gin-gonic/gin"
)

// AuthMiddleware WebSocket认证中间件
func AuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		cfg := config.GlobalConfig

		// 检查IP白名单
		clientIP := c.ClientIP()
		if !cfg.IsIPAllowed(clientIP) {
			log.Printf("IP not allowed: %s", clientIP)
			c.JSON(http.StatusForbidden, gin.H{
				"error": "Access denied: IP not in whitelist",
			})
			c.Abort()
			return
		}

		// 从查询参数或Header中获取API密钥
		apiKey := c.Query("api_key")
		if apiKey == "" {
			apiKey = c.GetHeader("X-API-Key")
		}
		if apiKey == "" {
			// 尝试从Authorization header获取
			auth := c.GetHeader("Authorization")
			if strings.HasPrefix(auth, "Bearer ") {
				apiKey = strings.TrimPrefix(auth, "Bearer ")
			}
		}

		// 验证API密钥
		if !cfg.ValidateAPIKey(apiKey) {
			log.Printf("Invalid API key from IP: %s", clientIP)
			c.JSON(http.StatusUnauthorized, gin.H{
				"error": "Invalid API key",
			})
			c.Abort()
			return
		}

		log.Printf("Authentication successful for IP: %s", clientIP)
		c.Next()
	}
}
