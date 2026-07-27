package controllers

import (
	"strconv"
	"sync"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/service"
	"github.com/msterzhang/onelist/config"

	"github.com/gin-gonic/gin"
)

type HomeData struct {
	Galleries    []models.Gallery             `json:"galleries"`
	LatestMovies []models.TheMovie            `json:"latest_movies"`
	LatestTvs    []models.TheTv               `json:"latest_tvs"`
	GalleryItems map[string]interface{}       `json:"gallery_items"`
}

func GetHomeData(c *gin.Context) {
	sizeStr := c.Query("size")
	gallerySizeStr := c.Query("gallery_size")
	size := 24
	gallerySize := 24
	if sizeStr != "" {
		if s, err := strconv.Atoi(sizeStr); err == nil {
			size = s
		}
	}
	if gallerySizeStr != "" {
		if s, err := strconv.Atoi(gallerySizeStr); err == nil {
			gallerySize = s
		}
	}

	db := database.NewDb()
	homeData := HomeData{
		GalleryItems: make(map[string]interface{}),
	}

	var galleries []models.Gallery
	db.Model(&models.Gallery{}).Order("id desc").Find(&galleries)
	homeData.Galleries = galleries

	// 收集所有有效 GalleryUid，用于过滤掉孤儿记录
	validGalleryUids := make([]string, 0, len(galleries))
	for _, g := range galleries {
		validGalleryUids = append(validGalleryUids, g.GalleryUid)
	}

	var wg sync.WaitGroup
	var mu sync.Mutex

	wg.Add(2)

	go func() {
		defer wg.Done()
		themovies := []models.TheMovie{}
		subQuery := db.Model(&models.TheMovie{}).Select("MAX(id)").Where("gallery_uid IN (?)", validGalleryUids).Group("url")
		result := db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery)
		var err error
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Order("datetime(created_at) desc").Scan(&themovies).Error
		} else {
			err = result.Limit(size).Order("-created_at").Scan(&themovies).Error
		}
		if err == nil {
			userId := c.GetString("UserId")
			themovies = service.TheMoviesService(themovies, userId)
			mu.Lock()
			homeData.LatestMovies = themovies
			mu.Unlock()
		}
	}()

	go func() {
		defer wg.Done()
		thetvs := []models.TheTv{}
		subQuery := db.Model(&models.TheTv{}).Select("MAX(id)").Where("gallery_uid IN (?)", validGalleryUids).Group("name")
		result := db.Model(&models.TheTv{}).Where("id IN (?)", subQuery)
		var err error
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Order("datetime(created_at) desc").Scan(&thetvs).Error
		} else {
			err = result.Limit(size).Order("-created_at").Scan(&thetvs).Error
		}
		if err == nil {
			userId := c.GetString("UserId")
			thetvs = service.TheTvsService(thetvs, userId)
			mu.Lock()
			homeData.LatestTvs = thetvs
			mu.Unlock()
		}
	}()

	wg.Wait()

	for _, gallery := range galleries {
		if gallery.GalleryType == "movie" {
			themovies := []models.TheMovie{}
			subQuery := db.Model(&models.TheMovie{}).Select("MAX(id)").Where("gallery_uid = ?", gallery.GalleryUid).Group("url")
			var err error
			if config.DBDRIVER == "sqlite" {
				err = db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery).
					Limit(gallerySize).Order("datetime(created_at) desc").Scan(&themovies).Error
			} else {
				err = db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery).
					Limit(gallerySize).Order("-created_at").Scan(&themovies).Error
			}
			if err == nil {
				homeData.GalleryItems[gallery.GalleryUid] = themovies
			}
		} else {
			thetvs := []models.TheTv{}
			subQuery := db.Model(&models.TheTv{}).Select("MAX(id)").Where("gallery_uid = ?", gallery.GalleryUid).Group("name")
			var err error
			if config.DBDRIVER == "sqlite" {
				err = db.Model(&models.TheTv{}).Where("id IN (?)", subQuery).
					Limit(gallerySize).Order("datetime(created_at) desc").Scan(&thetvs).Error
			} else {
				err = db.Model(&models.TheTv{}).Where("id IN (?)", subQuery).
					Limit(gallerySize).Order("-created_at").Scan(&thetvs).Error
			}
			if err == nil {
				homeData.GalleryItems[gallery.GalleryUid] = thetvs
			}
		}
	}

	c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": homeData})
}
