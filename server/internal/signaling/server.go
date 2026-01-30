package signaling

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"

	"miclink-server/internal/model"

	"github.com/gorilla/websocket"
)

// Server 信令服务器
type Server struct {
	Clients    map[string]*Client
	Register   chan *Client
	Unregister chan *Client
	Upgrader   websocket.Upgrader
	mu         sync.RWMutex
}

// NewServer 创建新服务器
func NewServer() *Server {
	return &Server{
		Clients:    make(map[string]*Client),
		Register:   make(chan *Client),
		Unregister: make(chan *Client),
		Upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool {
				return true // 允许所有来源（生产环境需要限制）
			},
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
		},
	}
}

// Run 运行服务器
func (s *Server) Run() {
	for {
		select {
		case client := <-s.Register:
			s.mu.Lock()
			s.Clients[client.ID] = client
			s.mu.Unlock()
			log.Printf("Client registered: %s", client.ID)
			s.BroadcastUserList()

		case client := <-s.Unregister:
			s.mu.Lock()
			if _, ok := s.Clients[client.ID]; ok {
				delete(s.Clients, client.ID)
				close(client.Send)
				log.Printf("Client unregistered: %s", client.ID)
			}
			s.mu.Unlock()
			s.BroadcastUserList()
		}
	}
}

// HandleWebSocket 处理WebSocket连接
func (s *Server) HandleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := s.Upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("Error upgrading connection: %v", err)
		return
	}

	// 先接收join消息获取用户ID
	_, message, err := conn.ReadMessage()
	if err != nil {
		log.Printf("Error reading first message: %v", err)
		conn.Close()
		return
	}

	var joinMsg model.Message
	if err := json.Unmarshal(message, &joinMsg); err != nil {
		log.Printf("Error parsing join message: %v", err)
		conn.Close()
		return
	}

	if joinMsg.Type != model.TypeJoin {
		log.Printf("First message must be join")
		conn.Close()
		return
	}

	userID := joinMsg.From
	if userID == "" {
		log.Printf("User ID is empty")
		conn.Close()
		return
	}

	// 检查用户ID是否已存在
	s.mu.RLock()
	if _, exists := s.Clients[userID]; exists {
		s.mu.RUnlock()
		errorMsg := &model.Message{
			Type: model.TypeError,
			Payload: model.ErrorPayload{
				Message: "User ID already exists",
			},
		}
		data, _ := json.Marshal(errorMsg)
		conn.WriteMessage(websocket.TextMessage, data)
		conn.Close()
		return
	}
	s.mu.RUnlock()

	// 创建客户端
	client := NewClient(userID, conn, s)
	s.Register <- client

	// 启动读写协程
	go client.WritePump()
	go client.ReadPump()
}

// HandleMessage 处理客户端消息
func (s *Server) HandleMessage(client *Client, msg *model.Message) {
	log.Printf("Message from %s: type=%s, to=%s", msg.From, msg.Type, msg.To)

	switch msg.Type {
	case model.TypeCall, model.TypeCallResponse, model.TypeOffer,
		model.TypeAnswer, model.TypeICECandidate, model.TypeHangup:
		// 转发消息给目标用户
		s.RelayMessage(msg)
	default:
		log.Printf("Unknown message type: %s", msg.Type)
	}
}

// RelayMessage 转发消息
func (s *Server) RelayMessage(msg *model.Message) {
	if msg.To == "" {
		log.Printf("Target user ID is empty")
		return
	}

	s.mu.RLock()
	targetClient, exists := s.Clients[msg.To]
	s.mu.RUnlock()

	if !exists {
		log.Printf("Target user not found: %s", msg.To)
		return
	}

	log.Printf("Relaying message from %s to %s: type=%s", msg.From, msg.To, msg.Type)
	targetClient.SendMessage(msg)
	log.Printf("Message relayed successfully to %s", msg.To)
}

// BroadcastUserList 广播在线用户列表
func (s *Server) BroadcastUserList() {
	s.mu.RLock()
	users := make([]string, 0, len(s.Clients))
	for userID := range s.Clients {
		users = append(users, userID)
	}
	s.mu.RUnlock()

	msg := &model.Message{
		Type: model.TypeUserList,
		Payload: model.UserListPayload{
			Users: users,
		},
	}

	data, err := json.Marshal(msg)
	if err != nil {
		log.Printf("Error marshaling user list: %v", err)
		return
	}

	s.mu.RLock()
	for _, client := range s.Clients {
		select {
		case client.Send <- data:
		default:
			// 发送失败，跳过
		}
	}
	s.mu.RUnlock()
}

// GetOnlineUsers 获取在线用户列表
func (s *Server) GetOnlineUsers() []string {
	s.mu.RLock()
	defer s.mu.RUnlock()

	users := make([]string, 0, len(s.Clients))
	for userID := range s.Clients {
		users = append(users, userID)
	}
	return users
}
