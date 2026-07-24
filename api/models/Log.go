package models

import (
	"time"

	"gorm.io/gorm"
)

type Log struct {
	Id        uint      `json:"id" gorm:"primaryKey"`
	Level     string    `json:"level" gorm:"size:20;index"` // info, warn, error, debug
	Module    string    `json:"module" gorm:"size:50;index"` // alist, thedb, work, system, proxy
	Message   string    `json:"message" gorm:"type:text"`
	Detail    string    `json:"detail" gorm:"type:text"`
	CreatedAt time.Time `json:"created_at" gorm:"index"`
}

func (l *Log) BeforeCreate(tx *gorm.DB) (err error) {
	l.CreatedAt = time.Now()
	return
}
