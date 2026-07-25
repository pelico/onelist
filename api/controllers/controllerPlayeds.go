package controllers

import (
	"strconv"
	"time"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/repository"
	"github.com/msterzhang/onelist/api/repository/crud"
	"github.com/msterzhang/onelist/api/service"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/config"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func CreatePlayed(c *gin.Context) {
	played := models.Played{}
	err := c.ShouldBind(&played)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "创建失败,表单解析出错!", "data": played})
		return
	}
	if len(played.UserId) == 0 {
		played.UserId = c.GetString("UserId")
	}
	db := database.NewDb()
	repo := crud.NewRepositoryPlayedsCRUD(db)
	func(playedRepository repository.PlayedRepository) {
		played, err := playedRepository.Save(played)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "创建失败!", "data": played})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "创建成功!", "data": played})
	}(repo)
}

func DeletePlayedById(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	repo := crud.NewRepositoryPlayedsCRUD(db)
	func(playedRepository repository.PlayedRepository) {
		played, err := playedRepository.DeleteByID(id)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": played})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "删除资源成功!", "data": played})
	}(repo)
}

func ReNewPlayedByPlayed(c *gin.Context) {
	played := models.Played{}
	err := c.ShouldBind(&played)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "创建失败,表单解析出错!", "data": played})
		return
	}
	if len(played.UserId) == 0 {
		played.UserId = c.GetString("UserId")
	}
	db := database.NewDb()
	repo := crud.NewRepositoryPlayedsCRUD(db)
	func(playedRepository repository.PlayedRepository) {
		played, err := playedRepository.ReNewByPlayed(played)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": played})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "处理成功!", "data": played})
	}(repo)
}

func UpdatePlayedById(c *gin.Context) {
	id := c.Query("id")
	played := models.Played{}
	err := c.ShouldBind(&played)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "创建失败,表单解析出错!", "data": played})
		return
	}
	db := database.NewDb()
	repo := crud.NewRepositoryPlayedsCRUD(db)
	func(playedRepository repository.PlayedRepository) {
		played, err := playedRepository.UpdateByID(id, played)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": played})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "更新资源成功!", "data": played})
	}(repo)
}

func GetPlayedById(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	repo := crud.NewRepositoryPlayedsCRUD(db)
	func(playedRepository repository.PlayedRepository) {
		played, err := playedRepository.FindByID(id)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": played})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": played})
	}(repo)
}

func GetPlayedList(c *gin.Context) {
	page, errPage := strconv.Atoi(c.Query("page"))
	size, errSize := strconv.Atoi(c.Query("size"))
	if errPage != nil {
		page = 1
	}
	if errSize != nil {
		size = 8
	}
	db := database.NewDb()
	repo := crud.NewRepositoryPlayedsCRUD(db)
	func(playedRepository repository.PlayedRepository) {
		playeds, num, err := playedRepository.FindAll(page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": playeds, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": playeds, "num": num})
	}(repo)
}

func SearchPlayed(c *gin.Context) {
	q := c.Query("q")
	if len(q) == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误!", "data": ""})
		return
	}
	page, errPage := strconv.Atoi(c.Query("page"))
	size, errSize := strconv.Atoi(c.Query("size"))
	if errPage != nil {
		page = 1
	}
	if errSize != nil {
		size = 8
	}
	db := database.NewDb()
	repo := crud.NewRepositoryPlayedsCRUD(db)
	func(playedRepository repository.PlayedRepository) {
		playeds, num, err := playedRepository.Search(q, page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": playeds, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": playeds, "num": num})
	}(repo)
}

func GetPlayedDataList(c *gin.Context) {
	dataType := c.Query("data_type")
	if len(dataType) == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误!", "data": ""})
		return
	}
	page, errPage := strconv.Atoi(c.Query("page"))
	size, errSize := strconv.Atoi(c.Query("size"))
	if errPage != nil {
		page = 1
	}
	if errSize != nil {
		size = 8
	}
	UserId := c.GetString("UserId")
	db := database.NewDb()
	
	if dataType == "tv" {
		var tvIds []int
		db.Model(&models.TheTv{}).Select("id").Scan(&tvIds)
		
		var playeds []models.Played
		var num int64
		subQuery := db.Model(&models.Played{}).Where("user_id = ? AND data_type = ? AND data_id IN ?", UserId, dataType, tvIds)
		subQuery.Count(&num)
		if config.DBDRIVER == "sqlite" {
			subQuery.Order("datetime(updated_at) desc").Limit(size).Offset((page - 1) * size).Scan(&playeds)
		} else {
			subQuery.Order("-updated_at").Limit(size).Offset((page - 1) * size).Scan(&playeds)
		}
		
		thetvs := []models.TheTv{}
		for _, playedDb := range playeds {
			thetv := models.TheTv{}
			err := db.Model(&models.TheTv{}).Where("id = ?", playedDb.DataId).First(&thetv).Error
			if err != nil {
				continue
			}
			thetv = service.TheTvService(thetv, UserId)
			thetvs = append(thetvs, thetv)
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": thetvs, "num": int(num)})
		return
	}
	
	var movieIds []int
	db.Model(&models.TheMovie{}).Select("id").Scan(&movieIds)
	
	var playeds []models.Played
	var num int64
	subQuery := db.Model(&models.Played{}).Where("user_id = ? AND data_type = ? AND data_id IN ?", UserId, dataType, movieIds)
	subQuery.Count(&num)
	if config.DBDRIVER == "sqlite" {
		subQuery.Order("datetime(updated_at) desc").Limit(size).Offset((page - 1) * size).Scan(&playeds)
	} else {
		subQuery.Order("-updated_at").Limit(size).Offset((page - 1) * size).Scan(&playeds)
	}
	
	themovies := []models.TheMovie{}
	for _, playedDb := range playeds {
		themovie := models.TheMovie{}
		err := db.Model(&models.TheMovie{}).Where("id = ?", playedDb.DataId).First(&themovie).Error
		if err != nil {
			continue
		}
		themovie = service.TheMovieService(themovie, UserId)
		themovies = append(themovies, themovie)
	}
	c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": themovies, "num": int(num)})
}

func CleanPlayed(c *gin.Context) {
	cleanAll := c.Query("all")
	retentionDaysStr := c.Query("days")
	
	db := database.NewDb()
	var count int64
	
	if cleanAll == "true" {
		result := db.Session(&gorm.Session{AllowGlobalUpdate: true}).Model(&models.Played{}).Delete(&models.Played{})
		if result.Error != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "清理失败: " + result.Error.Error(), "data": 0})
			return
		}
		count = result.RowsAffected
		logger.Info("played", "清理全部播放记录", "数量: "+strconv.FormatInt(count, 10))
	} else {
		retentionDays, _ := strconv.Atoi(retentionDaysStr)
		if retentionDays <= 0 {
			retentionDays = 7
		}
		cutoffTime := time.Now().AddDate(0, 0, -retentionDays)
		result := db.Model(&models.Played{}).Where("updated_at < ?", cutoffTime).Delete(&models.Played{})
		if result.Error != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "清理失败: " + result.Error.Error(), "data": 0})
			return
		}
		count = result.RowsAffected
		logger.Info("played", "清理过期播放记录", "保留天数: "+strconv.Itoa(retentionDays)+", 清理数量: "+strconv.FormatInt(count, 10))
	}
	
	c.JSON(200, gin.H{"code": 200, "msg": "清理成功", "data": count})
}
