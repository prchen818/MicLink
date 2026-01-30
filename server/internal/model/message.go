package model

// MessageType 消息类型
type MessageType string

const (
	TypeJoin         MessageType = "join"
	TypeLeave        MessageType = "leave"
	TypeUserList     MessageType = "user_list"
	TypeCall         MessageType = "call"
	TypeCallResponse MessageType = "call_response"
	TypeOffer        MessageType = "offer"
	TypeAnswer       MessageType = "answer"
	TypeICECandidate MessageType = "ice_candidate"
	TypeHangup       MessageType = "hangup"
	TypeError        MessageType = "error"
)

// Message 通用消息结构
type Message struct {
	Type    MessageType     `json:"type"`
	From    string          `json:"from,omitempty"`
	To      string          `json:"to,omitempty"`
	Payload interface{}     `json:"payload,omitempty"`
}

// JoinPayload 加入房间payload
type JoinPayload struct {
	UserID string `json:"userId"`
}

// CallPayload 呼叫payload
type CallPayload struct {
	Mode    string `json:"mode"`    // "auto", "p2p_only", "relay_only"
	Quality string `json:"quality"` // "low", "medium", "high"
}

// CallResponsePayload 呼叫响应payload
type CallResponsePayload struct {
	Accepted bool `json:"accepted"`
}

// SDPPayload SDP payload
type SDPPayload struct {
	SDP string `json:"sdp"`
}

// ICECandidatePayload ICE候选payload
type ICECandidatePayload struct {
	Candidate      string `json:"candidate"`
	SDPMid         string `json:"sdpMid,omitempty"`
	SDPMLineIndex  int    `json:"sdpMLineIndex,omitempty"`
}

// UserListPayload 用户列表payload
type UserListPayload struct {
	Users []string `json:"users"`
}

// ErrorPayload 错误payload
type ErrorPayload struct {
	Message string `json:"message"`
}
