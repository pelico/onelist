package models

import (
	"time"

	"gorm.io/gorm"
)

// 喜欢
type Heart struct {
	Id        uint      `json:"id" gorm:"primaryKey"`
	UserId    string    `json:"user_id" gorm:"uniqueIndex:idx_user_data"`
	DataType  string    `json:"data_type" gorm:"uniqueIndex:idx_user_data"`
	DataId    int       `json:"data_id" gorm:"uniqueIndex:idx_user_data"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

func (d *Heart) BeforeCreate(tx *gorm.DB) (err error) {
	d.CreatedAt = time.Now()
	d.UpdatedAt = time.Now()
	return
}

func (d *Heart) BeforeUpdate(tx *gorm.DB) (err error) {
	d.UpdatedAt = time.Now()
	return
}
