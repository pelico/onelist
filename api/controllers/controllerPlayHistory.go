package controllers

import (
	"fmt"
	"strconv"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/repository"
	"github.com/msterzhang/onelist/api/repository/crud"
	"github.com/msterzhang/onelist/api/utils/logger"

	"github.com/gin-gonic/gin"
)

// PlayHistoryHeartbeat 心跳上报（所有登录用户）
func PlayHistoryHeartbeat(c *gin.Context) {
	// 记录请求来源，帮助诊断
	clientIP := c.ClientIP()
	authHeader := c.GetHeader("Authorization")
	logger.Info("play_history", "心跳请求到达", 
		"client_ip: "+clientIP+
		", has_auth: "+strconv.FormatBool(len(authHeader) > 0)+
		", auth_prefix: "+func() string { if len(authHeader) > 20 { return authHeader[:20]+"..." }; return authHeader }())
	
	ph := models.PlayHistory{}
	err := c.ShouldBind(&ph)
	if err != nil {
		logger.Info("play_history", "心跳参数绑定失败", "error: "+err.Error()+", client_ip: "+clientIP)
		c.JSON(200, gin.H{"code": 201, "msg": "参数解析失败!", "data": nil})
		return
	}
	
	// 记录绑定后的数据
	logger.Info("play_history", fmt.Sprintf("心跳数据绑定成功 | ip=%s type=%s id=%d title=%s gallery=%s dur=%d pos=%d",
		clientIP, ph.DataType, ph.DataId, ph.Title, ph.GalleryUid, ph.Duration, ph.Position))
	
	if len(ph.UserId) == 0 {
		ph.UserId = c.GetString("UserId")
		logger.Info("play_history", fmt.Sprintf("心跳UserId自动填充 | user_id=%s (len=%d)", ph.UserId, len(ph.UserId)))
	} else {
		logger.Info("play_history", fmt.Sprintf("心跳UserId已有 | user_id=%s", ph.UserId))
	}
	
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		result, err := hRepo.Heartbeat(ph)
		if err != nil {
			logger.Info("play_history", fmt.Sprintf("心跳上报失败 | error=%s", err.Error()))
			c.JSON(200, gin.H{"code": 201, "msg": "上报失败!", "data": result})
			return
		}
		logger.Info("play_history", fmt.Sprintf("心跳上报成功 | id=%d user_id=%s type=%s data_id=%d title=%s dur=%d gallery=%s",
			result.Id, result.UserId, result.DataType, result.DataId, result.Title, result.Duration, result.GalleryUid))
		c.JSON(200, gin.H{"code": 200, "msg": "上报成功!", "data": result})
	}(repo)
}

// PlayHistoryStats 获取播放统计（管理员）
func PlayHistoryStats(c *gin.Context) {
	userId := c.Query("user_id")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")
	logger.Info("play_history", fmt.Sprintf("统计查询 | user_id=%s start=%s end=%s", userId, startDate, endDate))
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		list, err := hRepo.GetStats(userId, startDate, endDate)
		if err != nil {
			logger.Info("play_history", fmt.Sprintf("统计查询失败 | error=%s", err.Error()))
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": nil})
			return
		}
		logger.Info("play_history", fmt.Sprintf("统计查询成功 | 返回%d条记录", len(list)))
		c.JSON(200, gin.H{"code": 200, "msg": "查询成功!", "data": list})
	}(repo)
}

// PlayHistoryGalleryStats 按媒体库统计（管理员）
func PlayHistoryGalleryStats(c *gin.Context) {
	userId := c.Query("user_id")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")
	logger.Info("play_history", fmt.Sprintf("媒体库统计查询 | user_id=%s start=%s end=%s", userId, startDate, endDate))
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		stats, err := hRepo.GetGalleryStats(userId, startDate, endDate)
		if err != nil {
			logger.Info("play_history", fmt.Sprintf("媒体库统计查询失败 | error=%s", err.Error()))
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": nil})
			return
		}
		logger.Info("play_history", fmt.Sprintf("媒体库统计查询成功 | 返回%d条", len(stats)))
		c.JSON(200, gin.H{"code": 200, "msg": "查询成功!", "data": stats})
	}(repo)
}

// PlayHistoryTopMovies 影片Top排行（管理员）
func PlayHistoryTopMovies(c *gin.Context) {
	userId := c.Query("user_id")
	galleryUid := c.Query("gallery_uid")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")
	limit, err := strconv.Atoi(c.Query("limit"))
	if err != nil || limit <= 0 {
		limit = 10
	}
	logger.Info("play_history", fmt.Sprintf("Top影片查询 | user_id=%s gallery=%s start=%s end=%s limit=%d", userId, galleryUid, startDate, endDate, limit))
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		stats, err := hRepo.GetTopMovies(userId, galleryUid, startDate, endDate, limit)
		if err != nil {
			logger.Info("play_history", fmt.Sprintf("Top影片查询失败 | error=%s", err.Error()))
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": nil})
			return
		}
		logger.Info("play_history", fmt.Sprintf("Top影片查询成功 | 返回%d条", len(stats)))
		c.JSON(200, gin.H{"code": 200, "msg": "查询成功!", "data": stats})
	}(repo)
}

// PlayHistoryList 播放历史列表（管理员）
func PlayHistoryList(c *gin.Context) {
	userId := c.Query("user_id")
	page, errPage := strconv.Atoi(c.Query("page"))
	size, errSize := strconv.Atoi(c.Query("size"))
	if errPage != nil {
		page = 1
	}
	if errSize != nil {
		size = 20
	}
	logger.Info("play_history", fmt.Sprintf("历史列表查询 | user_id=%s page=%d size=%d", userId, page, size))
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		list, num, err := hRepo.GetHistoryList(userId, page, size)
		if err != nil {
			logger.Info("play_history", fmt.Sprintf("历史列表查询失败 | error=%s", err.Error()))
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": list, "num": num})
			return
		}
		logger.Info("play_history", fmt.Sprintf("历史列表查询成功 | 总数=%d 本页%d条", num, len(list)))
		c.JSON(200, gin.H{"code": 200, "msg": "查询成功!", "data": list, "num": num})
	}(repo)
}

// PlayHistoryClean 清理播放历史（管理员）
func PlayHistoryClean(c *gin.Context) {
	cleanAll := c.Query("all")
	daysStr := c.Query("days")
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		var count int64
		var err error
		if cleanAll == "true" {
			count, err = hRepo.CleanAll()
			if err != nil {
				c.JSON(200, gin.H{"code": 201, "msg": "清理失败: " + err.Error(), "data": 0})
				return
			}
			logger.Info("play_history", "清理全部播放历史", "数量: "+strconv.FormatInt(count, 10))
		} else {
			days, _ := strconv.Atoi(daysStr)
			if days <= 0 {
				days = 30
			}
			count, err = hRepo.Clean(days)
			if err != nil {
				c.JSON(200, gin.H{"code": 201, "msg": "清理失败: " + err.Error(), "data": 0})
				return
			}
			logger.Info("play_history", "清理过期播放历史", "保留天数: "+strconv.Itoa(days)+", 清理数量: "+strconv.FormatInt(count, 10))
		}
		c.JSON(200, gin.H{"code": 200, "msg": "清理成功", "data": count})
	}(repo)
}

// PlayHistoryTodayDuration 获取当前用户今日累计播放秒数（所有登录用户）
func PlayHistoryTodayDuration(c *gin.Context) {
	userId := c.GetString("UserId")
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		total, err := hRepo.GetTodayDuration(userId)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": 0})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询成功!", "data": total})
	}(repo)
}

// PlayHistoryDailyTimePeriods 获取每日播放时间段（管理员）
func PlayHistoryDailyTimePeriods(c *gin.Context) {
	userId := c.Query("user_id")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		periods, err := hRepo.GetDailyTimePeriods(userId, startDate, endDate)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": nil})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询成功!", "data": periods})
	}(repo)
}
