package models

import "time"

type Message struct {
	Id         uint       `json:"id" gorm:"primaryKey"`
	UserId     string     `json:"user_id" gorm:"index"`          // 目标用户的 UserId (UUID)
	UserName   string     `json:"user_name"`                     // 冗余存储，方便展示
	Content    string     `json:"content" gorm:"type:text"`      // 消息内容
	Priority   string     `json:"priority" gorm:"default:normal"` // normal=角标通知, forced=强制覆盖层
	SenderType string     `json:"sender_type" gorm:"default:admin"` // admin / webhook
	ReadAt     *time.Time `json:"read_at"`
	CreatedAt  time.Time  `json:"created_at"`
}
