package controllers

import (
	"fmt"
	"sync"
)

// SSEHub 管理所有用户的 SSE 连接
type SSEHub struct {
	mu       sync.RWMutex
	clients  map[string]map[chan string]struct{} // userId -> set of channels
}

var sseHub = &SSEHub{
	clients: make(map[string]map[chan string]struct{}),
}

// Subscribe 注册一个 SSE 客户端
func (h *SSEHub) Subscribe(userId string) chan string {
	h.mu.Lock()
	defer h.mu.Unlock()
	ch := make(chan string, 8)
	if _, ok := h.clients[userId]; !ok {
		h.clients[userId] = make(map[chan string]struct{})
	}
	h.clients[userId][ch] = struct{}{}
	return ch
}

// Unsubscribe 移除一个 SSE 客户端
func (h *SSEHub) Unsubscribe(userId string, ch chan string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if subs, ok := h.clients[userId]; ok {
		delete(subs, ch)
		if len(subs) == 0 {
			delete(h.clients, userId)
		}
	}
	close(ch)
}

// Broadcast 向指定用户的所有连接推送消息（JSON 字符串）
func (h *SSEHub) Broadcast(userId string, data string) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if subs, ok := h.clients[userId]; ok {
		for ch := range subs {
			select {
			case ch <- data:
			default:
				// 客户端消费太慢，丢弃
				fmt.Printf("[SSE] 丢弃消息: user=%s\n", userId)
			}
		}
	}
}
