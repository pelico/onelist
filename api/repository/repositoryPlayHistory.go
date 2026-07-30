package repository

import "github.com/msterzhang/onelist/api/models"

type PlayHistoryRepository interface {
	Save(models.PlayHistory) (models.PlayHistory, error)
	Heartbeat(models.PlayHistory) (models.PlayHistory, error)
	GetStats(userId string, startDate string, endDate string) ([]models.PlayHistory, error)
	GetGalleryStats(userId string, startDate string, endDate string) ([]GalleryStat, error)
	GetHistoryList(userId string, page int, size int) ([]models.PlayHistory, int, error)
	GetTodayDuration(userId string) (int, error)
	GetDailyTimePeriods(userId string, startDate string, endDate string) ([]DailyTimePeriod, error)
	GetTopMovies(userId string, galleryUid string, startDate string, endDate string, limit int) ([]MoviePlayStat, error)
	Clean(days int) (int64, error)
	CleanAll() (int64, error)
}

// MoviePlayStat 影片观看统计（Top排行）
type MoviePlayStat struct {
	DataId       int    `json:"data_id"`
	DataType     string `json:"data_type"`
	Title        string `json:"title"`
	GalleryUid   string `json:"gallery_uid"`
	GalleryTitle string `json:"gallery_title"`
	TotalSeconds int64  `json:"total_seconds"`
	PlayCount    int64  `json:"play_count"`
}

// GalleryStat 媒体库观看统计
type GalleryStat struct {
	GalleryUid   string `json:"gallery_uid"`
	GalleryTitle string `json:"gallery_title"`
	TotalSeconds int64  `json:"total_seconds"`
	PlayCount    int64  `json:"play_count"`
}

// TimeSegment 时间段（播放或未观看）
type TimeSegment struct {
	Start    string `json:"start"`     // HH:MM
	End      string `json:"end"`       // HH:MM
	Duration int    `json:"duration"`  // 秒数
	IsGap    bool   `json:"is_gap"`    // true=未观看间隙, false=播放时段
}

// DailyTimePeriod 每日播放时间段
type DailyTimePeriod struct {
	Date     string        `json:"date"`      // YYYY-MM-DD
	Earliest string        `json:"earliest"`  // 最早播放时间 HH:MM
	Latest   string        `json:"latest"`    // 最晚播放时间 HH:MM
	Segments []TimeSegment `json:"segments"`  // 时间段列表
}
