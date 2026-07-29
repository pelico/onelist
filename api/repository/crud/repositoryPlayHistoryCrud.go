package crud

import (
	"time"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/repository"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

type RepositoryPlayHistoryCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.PlayHistory]
}

func NewRepositoryPlayHistoryCRUD(db *gorm.DB) *RepositoryPlayHistoryCRUD {
	return &RepositoryPlayHistoryCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.PlayHistory](db, "play_history"),
	}
}

// Heartbeat 心跳上报：同一用户+同一影片+同一天，更新最后一条记录；不存在则新建
func (r *RepositoryPlayHistoryCRUD) Heartbeat(ph models.PlayHistory) (models.PlayHistory, error) {
	var result models.PlayHistory
	var retErr error
	// 自动填充媒体库名称（前端只传 gallery_uid）
	if ph.GalleryUid != "" && ph.GalleryTitle == "" {
		var gallery models.Gallery
		if err := r.db.Where("gallery_uid = ?", ph.GalleryUid).First(&gallery).Error; err == nil {
			ph.GalleryTitle = gallery.Title
		}
	}
	// 自动填充影片名称
	if ph.Title == "" {
		if ph.DataType == "tv" {
			var tv models.TheTv
			if err := r.db.Where("id = ?", ph.DataId).First(&tv).Error; err == nil {
				ph.Title = tv.Name
			}
		} else {
			var movie models.TheMovie
			if err := r.db.Where("id = ?", ph.DataId).First(&movie).Error; err == nil {
				ph.Title = movie.Title
			}
		}
	}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		today := time.Now().Format("2006-01-02")
		var existing models.PlayHistory
		var startDate, endDate time.Time
		if config.DBDRIVER == "sqlite" {
			startDate, _ = time.Parse("2006-01-02", today)
			endDate = startDate.AddDate(0, 0, 1)
		} else {
			startDate, _ = time.Parse("2006-01-02", today)
			endDate = startDate.AddDate(0, 0, 1)
		}
		err := r.db.Where("user_id = ? AND data_id = ? AND data_type = ? AND started_at >= ? AND started_at < ?",
			ph.UserId, ph.DataId, ph.DataType, startDate, endDate).
			Order("id desc").First(&existing).Error
		if err == nil && existing.Id != 0 {
			// 更新已有记录：累加观看时长，更新位置
			existing.Duration += ph.Duration
			existing.Position = ph.Position
			existing.TotalDuration = ph.TotalDuration
			retErr = r.db.Save(&existing).Error
			result = existing
		} else {
			// 新建记录
			ph.StartedAt = time.Now()
			retErr = r.db.Create(&ph).Error
			result = ph
		}
		ch <- retErr == nil
	}(done)
	if channels.OK(done) {
		return result, retErr
	}
	return result, retErr
}

// GetStats 获取指定时间范围内的播放记录（用于统计页面）
func (r *RepositoryPlayHistoryCRUD) GetStats(userId string, startDate string, endDate string) ([]models.PlayHistory, error) {
	var list []models.PlayHistory
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		query := r.db.Model(&models.PlayHistory{})
		if userId != "" {
			query = query.Where("user_id = ?", userId)
		}
		if startDate != "" {
			query = query.Where("started_at >= ?", startDate)
		}
		if endDate != "" {
			query = query.Where("started_at < ?", endDate)
		}
		retErr = query.Order("started_at desc").Find(&list).Error
		ch <- retErr == nil
	}(done)
	if channels.OK(done) {
		return list, retErr
	}
	return nil, retErr
}

// GetGalleryStats 按媒体库分组统计
func (r *RepositoryPlayHistoryCRUD) GetGalleryStats(userId string, startDate string, endDate string) ([]repository.GalleryStat, error) {
	var stats []repository.GalleryStat
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		query := r.db.Model(&models.PlayHistory{}).
			Select("gallery_uid, gallery_title, COALESCE(SUM(duration),0) as total_seconds, COUNT(*) as play_count")
		if userId != "" {
			query = query.Where("user_id = ?", userId)
		}
		if startDate != "" {
			query = query.Where("started_at >= ?", startDate)
		}
		if endDate != "" {
			query = query.Where("started_at < ?", endDate)
		}
		retErr = query.Group("gallery_uid, gallery_title").Order("total_seconds desc").Scan(&stats).Error
		ch <- retErr == nil
	}(done)
	if channels.OK(done) {
		return stats, retErr
	}
	return nil, retErr
}

// GetHistoryList 分页获取播放历史
func (r *RepositoryPlayHistoryCRUD) GetHistoryList(userId string, page int, size int) ([]models.PlayHistory, int, error) {
	var list []models.PlayHistory
	var num int64
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		query := r.db.Model(&models.PlayHistory{})
		if userId != "" {
			query = query.Where("user_id = ?", userId)
		}
		query.Count(&num)
		if config.DBDRIVER == "sqlite" {
			retErr = query.Limit(size).Offset((page - 1) * size).Order("datetime(started_at) desc").Find(&list).Error
		} else {
			retErr = query.Limit(size).Offset((page - 1) * size).Order("-started_at").Find(&list).Error
		}
		ch <- retErr == nil
	}(done)
	if channels.OK(done) {
		return list, int(num), retErr
	}
	return nil, 0, retErr
}

// Clean 清理N天前的记录
func (r *RepositoryPlayHistoryCRUD) Clean(days int) (int64, error) {
	var count int64
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		cutoff := time.Now().AddDate(0, 0, -days)
		result := r.db.Where("created_at < ?", cutoff).Delete(&models.PlayHistory{})
		retErr = result.Error
		count = result.RowsAffected
		ch <- retErr == nil
	}(done)
	if channels.OK(done) {
		return count, retErr
	}
	return count, retErr
}

// CleanAll 清理全部记录
func (r *RepositoryPlayHistoryCRUD) CleanAll() (int64, error) {
	var count int64
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Session(&gorm.Session{AllowGlobalUpdate: true}).Delete(&models.PlayHistory{})
		retErr = result.Error
		count = result.RowsAffected
		ch <- retErr == nil
	}(done)
	if channels.OK(done) {
		return count, retErr
	}
	return count, retErr
}
