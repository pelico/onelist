package repository

import "github.com/msterzhang/onelist/api/models"

type PlayHistoryRepository interface {
	Save(models.PlayHistory) (models.PlayHistory, error)
	Heartbeat(models.PlayHistory) (models.PlayHistory, error)
	GetStats(userId string, startDate string, endDate string) ([]models.PlayHistory, error)
	GetGalleryStats(userId string, startDate string, endDate string) ([]GalleryStat, error)
	GetHistoryList(userId string, page int, size int) ([]models.PlayHistory, int, error)
	GetTodayDuration(userId string) (int, error)
	Clean(days int) (int64, error)
	CleanAll() (int64, error)
}

// GalleryStat 媒体库观看统计
type GalleryStat struct {
	GalleryUid   string `json:"gallery_uid"`
	GalleryTitle string `json:"gallery_title"`
	TotalSeconds int64  `json:"total_seconds"`
	PlayCount    int64  `json:"play_count"`
}
