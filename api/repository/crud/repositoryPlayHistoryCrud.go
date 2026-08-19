package crud

import (
	"sort"
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

// Heartbeat 心跳上报：同一用户+同一影片+最近5分钟内有活跃心跳，更新最后一条记录；否则新建
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
		var existing models.PlayHistory
		// 查找最近5分钟内有活跃心跳的同一影片记录（连续观看会话）
		// 超过5分钟未更新则视为新的观看会话，创建独立记录
		fiveMinutesAgo := time.Now().Add(-5 * time.Minute)
		err := r.db.Where("user_id = ? AND data_id = ? AND data_type = ? AND updated_at >= ?",
			ph.UserId, ph.DataId, ph.DataType, fiveMinutesAgo).
			Order("id desc").First(&existing).Error
		if err == nil && existing.Id != 0 {
			// 更新已有记录：用位置增量计算实际观看时长（排除快进/跳过/暂停时间）
			positionDelta := ph.Position - existing.Position
			if positionDelta < 0 {
				positionDelta = 0 // 回退进度（拖动进度条），不计入
			}
			actualDuration := positionDelta
			if ph.Duration > 0 && ph.Duration < actualDuration {
				actualDuration = ph.Duration // 快进时 position 跳变超过心跳间隔，取心跳间隔
			}
			existing.Duration += actualDuration
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

// GetTopMovies 按影片分组统计Top排行
func (r *RepositoryPlayHistoryCRUD) GetTopMovies(userId string, galleryUid string, startDate string, endDate string, limit int) ([]repository.MoviePlayStat, error) {
	var stats []repository.MoviePlayStat
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		query := r.db.Model(&models.PlayHistory{}).
			Select("data_id, data_type, title, gallery_uid, gallery_title, COALESCE(SUM(duration),0) as total_seconds, COUNT(*) as play_count")
		if userId != "" {
			query = query.Where("user_id = ?", userId)
		}
		if galleryUid != "" {
			query = query.Where("gallery_uid = ?", galleryUid)
		}
		if startDate != "" {
			query = query.Where("started_at >= ?", startDate)
		}
		if endDate != "" {
			query = query.Where("started_at < ?", endDate)
		}
		if limit <= 0 {
			limit = 10
		}
		retErr = query.Group("data_id, data_type, title, gallery_uid, gallery_title").Order("total_seconds desc").Limit(limit).Scan(&stats).Error
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

// GetTodayDuration 获取用户今日累计播放秒数
func (r *RepositoryPlayHistoryCRUD) GetTodayDuration(userId string) (int, error) {
	var total int
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		today := time.Now().Format("2006-01-02")
		startDate, _ := time.Parse("2006-01-02", today)
		endDate := startDate.AddDate(0, 0, 1)
		retErr = r.db.Model(&models.PlayHistory{}).
			Where("user_id = ? AND started_at >= ? AND started_at < ?", userId, startDate, endDate).
			Select("COALESCE(SUM(duration),0)").
			Scan(&total).Error
		ch <- retErr == nil
	}(done)
	if channels.OK(done) {
		return total, retErr
	}
	return total, retErr
}

// GetDailyTimePeriods 获取每日播放时间段（用于时间段统计图表）
func (r *RepositoryPlayHistoryCRUD) GetDailyTimePeriods(userId string, startDate string, endDate string) ([]repository.DailyTimePeriod, error) {
	var heartbeats []models.PlayHistory
	var retErr error
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		query := r.db.Model(&models.PlayHistory{}).Select("started_at, updated_at, duration")
		if userId != "" {
			query = query.Where("user_id = ?", userId)
		}
		if startDate != "" {
			query = query.Where("started_at >= ?", startDate)
		}
		if endDate != "" {
			query = query.Where("started_at < ?", endDate)
		}
		retErr = query.Order("started_at asc").Find(&heartbeats).Error
		ch <- retErr == nil
	}(done)
	if !channels.OK(done) {
		return nil, retErr
	}

	// 按日期分组
	dayMap := make(map[string][]models.PlayHistory)
	for _, h := range heartbeats {
		day := h.StartedAt.Format("2006-01-02")
		dayMap[day] = append(dayMap[day], h)
	}

	var result []repository.DailyTimePeriod
	for day, hbs := range dayMap {
		if len(hbs) == 0 {
			continue
		}
		// 已按 started_at asc 排序
		firstTime := hbs[0].StartedAt
		lastTime := hbs[len(hbs)-1].StartedAt

		// 合并连续心跳为播放段（间隔≤5分钟视为连续）
		type seg struct {
			start    time.Time
			end      time.Time
			totalDur int // 累加实际播放秒数
		}
		var segments []seg
		cur := seg{start: hbs[0].StartedAt, end: hbs[0].UpdatedAt, totalDur: hbs[0].Duration}
		for i := 1; i < len(hbs); i++ {
			gap := hbs[i].StartedAt.Sub(cur.end)
			if gap <= 5*time.Minute {
				// 连续播放，扩展当前段
				if hbs[i].UpdatedAt.After(cur.end) {
					cur.end = hbs[i].UpdatedAt
				}
				cur.totalDur += hbs[i].Duration
			} else {
				// 间隙超过5分钟，结束当前段，开始新段
				segments = append(segments, cur)
				cur = seg{start: hbs[i].StartedAt, end: hbs[i].UpdatedAt, totalDur: hbs[i].Duration}
			}
		}
		segments = append(segments, cur)

		// 构建时间段列表：播放段 + 间隙交替
		var timeSegments []repository.TimeSegment
		for i, s := range segments {
			dur := s.totalDur
			if dur < 30 {
				dur = 30 // 最小30秒
			}
			timeSegments = append(timeSegments, repository.TimeSegment{
				Start:    s.start.Format("15:04"),
				End:      s.end.Format("15:04"),
				Duration: dur,
				IsGap:    false,
			})
			// 在两个播放段之间添加间隙
			if i < len(segments)-1 {
				nextStart := segments[i+1].start
				gapDur := int(nextStart.Sub(s.end).Seconds())
				timeSegments = append(timeSegments, repository.TimeSegment{
					Start:    s.end.Format("15:04"),
					End:      nextStart.Format("15:04"),
					Duration: gapDur,
					IsGap:    true,
				})
			}
		}

		result = append(result, repository.DailyTimePeriod{
			Date:     day,
			Earliest: firstTime.Format("15:04"),
			Latest:   lastTime.Format("15:04"),
			Segments: timeSegments,
		})
	}

	// 按日期排序
	sort.Slice(result, func(i, j int) bool {
		return result[i].Date < result[j].Date
	})

	return result, nil
}
