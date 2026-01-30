package signaling

import (
	"encoding/json"
	"log"
	"sync"

	"github.com/gorilla/websocket"
	"miclink-server/internal/model"
)

// Client WebSocket客户端
type Client struct {
	ID     string
	Conn   *websocket.Conn
	Send   chan []byte
	Server *Server
	mu     sync.Mutex
}

// NewClient 创建新客户端
func NewClient(id string, conn *websocket.Conn, server *Server) *Client {
	return &Client{
		ID:     id,
		Conn:   conn,
		Send:   make(chan []byte, 256),
		Server: server,
	}
}

// ReadPump 读取客户端消息
func (c *Client) ReadPump() {
	defer func() {
		c.Server.Unregister <- c
		c.Conn.Close()
	}()

	for {
		_, message, err := c.Conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("WebSocket error: %v", err)
			}
			break
		}

		// 解析消息
		var msg model.Message
		if err := json.Unmarshal(message, &msg); err != nil {
			log.Printf("Error parsing message: %v", err)
			continue
		}

		// 设置发送者
		msg.From = c.ID

		// 处理消息
		c.Server.HandleMessage(c, &msg)
	}
}

// WritePump 向客户端发送消息
func (c *Client) WritePump() {
	defer func() {
		c.Conn.Close()
	}()

	for message := range c.Send {
		c.mu.Lock()
		err := c.Conn.WriteMessage(websocket.TextMessage, message)
		c.mu.Unlock()
		
		if err != nil {
			log.Printf("Error writing message: %v", err)
			return
		}
	}
}

// SendMessage 发送消息给客户端
func (c *Client) SendMessage(msg *model.Message) error {
	data, err := json.Marshal(msg)
	if err != nil {
		return err
	}

	select {
	case c.Send <- data:
		return nil
	default:
		return nil
	}
}
