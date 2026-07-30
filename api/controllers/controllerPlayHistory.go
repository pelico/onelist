package controllers

import (
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
	ph := models.PlayHistory{}
	err := c.ShouldBind(&ph)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "参数解析失败!", "data": nil})
		return
	}
	if len(ph.UserId) == 0 {
		ph.UserId = c.GetString("UserId")
	}
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		result, err := hRepo.Heartbeat(ph)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "上报失败!", "data": result})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "上报成功!", "data": result})
	}(repo)
}

// PlayHistoryStats 获取播放统计（管理员）
func PlayHistoryStats(c *gin.Context) {
	userId := c.Query("user_id")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		list, err := hRepo.GetStats(userId, startDate, endDate)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": nil})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询成功!", "data": list})
	}(repo)
}

// PlayHistoryGalleryStats 按媒体库统计（管理员）
func PlayHistoryGalleryStats(c *gin.Context) {
	userId := c.Query("user_id")
	startDate := c.Query("start_date")
	endDate := c.Query("end_date")
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		stats, err := hRepo.GetGalleryStats(userId, startDate, endDate)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": nil})
			return
		}
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
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		stats, err := hRepo.GetTopMovies(userId, galleryUid, startDate, endDate, limit)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": nil})
			return
		}
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
	db := database.NewDb()
	repo := crud.NewRepositoryPlayHistoryCRUD(db)
	func(hRepo repository.PlayHistoryRepository) {
		list, num, err := hRepo.GetHistoryList(userId, page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "查询失败!", "data": list, "num": num})
			return
		}
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
