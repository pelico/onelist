package models

import (
	"time"

	"gorm.io/gorm"
)

// 播放历史记录（播放统计）
type PlayHistory struct {
	Id            uint      `json:"id" gorm:"primaryKey"`
	UserId        string    `json:"user_id" gorm:"index"`
	DataType      string    `json:"data_type"`      // movie / tv
	DataId        uint      `json:"data_id"`         // 数据库自增 ID
	Title         string    `json:"title"`           // 影片名称（冗余，方便统计）
	GalleryUid    string    `json:"gallery_uid"`     // 所属媒体库 UID
	GalleryTitle  string    `json:"gallery_title"`   // 媒体库名称（冗余）
	Duration      int       `json:"duration"`        // 本次观看秒数
	Position      int       `json:"position"`        // 播放到第几秒
	TotalDuration int       `json:"total_duration"`  // 影片总时长
	StartedAt     time.Time `json:"started_at"`      // 本次开始观看时间
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}

func (d *PlayHistory) BeforeCreate(tx *gorm.DB) (err error) {
	d.CreatedAt = time.Now()
	d.UpdatedAt = time.Now()
	return
}

func (d *PlayHistory) BeforeUpdate(tx *gorm.DB) (err error) {
	d.UpdatedAt = time.Now()
	return
}
