package config

import (
	"log"
	"os"
	"strings"
)

// Config 服务器配置
type Config struct {
	// 服务器端口
	Port string

	// API密钥，用于客户端认证
	APIKey string

	// 允许的IP白名单（可选，逗号分隔）
	AllowedIPs []string

	// 是否启用IP白名单检查
	EnableIPWhitelist bool
}

var GlobalConfig *Config

// LoadConfig 加载配置
func LoadConfig() *Config {
	port := getEnv("SERVER_PORT", "8080")
	apiKey := getEnv("API_KEY", "")
	allowedIPsStr := getEnv("ALLOWED_IPS", "")
	enableIPWhitelist := getEnv("ENABLE_IP_WHITELIST", "false") == "true"

	// 如果没有设置API_KEY，生成一个警告
	if apiKey == "" {
		log.Println("WARNING: API_KEY not set! Using default key (NOT SECURE for production)")
		apiKey = "miclink-default-key-change-in-production"
	}

	// 解析IP白名单
	var allowedIPs []string
	if allowedIPsStr != "" {
		allowedIPs = strings.Split(allowedIPsStr, ",")
		for i := range allowedIPs {
			allowedIPs[i] = strings.TrimSpace(allowedIPs[i])
		}
	}

	GlobalConfig = &Config{
		Port:              port,
		APIKey:            apiKey,
		AllowedIPs:        allowedIPs,
		EnableIPWhitelist: enableIPWhitelist,
	}

	log.Printf("Server configuration loaded:")
	log.Printf("  Port: %s", GlobalConfig.Port)
	log.Printf("  API Key: %s", maskAPIKey(GlobalConfig.APIKey))
	log.Printf("  IP Whitelist Enabled: %v", GlobalConfig.EnableIPWhitelist)
	if enableIPWhitelist && len(allowedIPs) > 0 {
		log.Printf("  Allowed IPs: %v", GlobalConfig.AllowedIPs)
	}

	return GlobalConfig
}

// getEnv 获取环境变量，如果不存在则返回默认值
func getEnv(key, defaultValue string) string {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}
	return value
}

// maskAPIKey 遮蔽API密钥，只显示前后各4位
func maskAPIKey(key string) string {
	if len(key) <= 8 {
		return "****"
	}
	return key[:4] + "..." + key[len(key)-4:]
}

// IsIPAllowed 检查IP是否在白名单中
func (c *Config) IsIPAllowed(ip string) bool {
	if !c.EnableIPWhitelist {
		return true
	}

	if len(c.AllowedIPs) == 0 {
		return true
	}

	// 移除端口号（如果有）
	if idx := strings.LastIndex(ip, ":"); idx != -1 {
		ip = ip[:idx]
	}

	for _, allowedIP := range c.AllowedIPs {
		if ip == allowedIP {
			return true
		}
	}

	return false
}

// ValidateAPIKey 验证API密钥
func (c *Config) ValidateAPIKey(key string) bool {
	return key == c.APIKey
}
