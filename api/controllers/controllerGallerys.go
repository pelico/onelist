package controllers

import (
	"strconv"
	"strings"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/repository"
	"github.com/msterzhang/onelist/api/repository/crud"
	"github.com/msterzhang/onelist/plugins/alist"

	"github.com/gin-gonic/gin"
)

func CreateGallery(c *gin.Context) {
	gallery := models.Gallery{}
	err := c.ShouldBind(&gallery)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "创建失败,表单解析出错!", "data": gallery})
		return
	}
	if !strings.Contains(gallery.AlistHost, "http") && gallery.IsAlist {
		c.JSON(200, gin.H{"code": 201, "msg": "域名应该含有'http'!", "data": gallery})
		return
	}
	db := database.NewDb()
	gallery.AlistHost = strings.TrimRight(gallery.AlistHost, "/")
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		gallery, err := galleryRepository.Save(gallery)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "创建失败!", "data": gallery})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "创建成功!", "data": gallery})
	}(repo)
}

func DeleteGalleryById(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	
	gallery := models.Gallery{}
	err := db.Model(&models.Gallery{}).Where("id = ?", id).First(&gallery).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": gallery})
		return
	}
	
	galleryUid := gallery.GalleryUid
	
	tx := db.Begin()
	
	// 删除关联的 Work 记录
	if err := tx.Model(&models.Work{}).Where("gallery_uid = ?", galleryUid).Delete(&models.Work{}).Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "删除 Work 记录失败!", "data": gallery})
		return
	}
	
	// 删除关联的 ErrFile 记录
	if err := tx.Model(&models.ErrFile{}).Where("gallery_uid = ?", galleryUid).Delete(&models.ErrFile{}).Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "删除 ErrFile 记录失败!", "data": gallery})
		return
	}
	
	// 删除电影相关记录
	var movies []models.TheMovie
	if err := tx.Model(&models.TheMovie{}).Where("gallery_uid = ?", galleryUid).Find(&movies).Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "查询电影记录失败!", "data": gallery})
		return
	}
	for _, movie := range movies {
		if err := tx.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Played{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除电影播放记录失败!", "data": gallery})
			return
		}
		if err := tx.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Star{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除电影收藏记录失败!", "data": gallery})
			return
		}
		if err := tx.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Heart{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除电影最爱记录失败!", "data": gallery})
			return
		}
	}
	if err := tx.Model(&models.TheMovie{}).Where("gallery_uid = ?", galleryUid).Delete(&models.TheMovie{}).Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "删除电影记录失败!", "data": gallery})
		return
	}
	
	// 删除电视剧相关记录
	var tvs []models.TheTv
	if err := tx.Model(&models.TheTv{}).Where("gallery_uid = ?", galleryUid).Find(&tvs).Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "查询电视剧记录失败!", "data": gallery})
		return
	}
	for _, tv := range tvs {
		if err := tx.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Played{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除电视剧播放记录失败!", "data": gallery})
			return
		}
		if err := tx.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Star{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除电视剧收藏记录失败!", "data": gallery})
			return
		}
		if err := tx.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Heart{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除电视剧最爱记录失败!", "data": gallery})
			return
		}
		
		var seasons []models.TheSeason
		if err := tx.Model(&models.TheSeason{}).Where("the_tv_id = ?", tv.ID).Find(&seasons).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "查询季记录失败!", "data": gallery})
			return
		}
		for _, season := range seasons {
			if err := tx.Model(&models.Episode{}).Where("the_season_id = ?", season.ID).Delete(&models.Episode{}).Error; err != nil {
				tx.Rollback()
				c.JSON(200, gin.H{"code": 201, "msg": "删除剧集记录失败!", "data": gallery})
				return
			}
		}
		if err := tx.Model(&models.TheSeason{}).Where("the_tv_id = ?", tv.ID).Delete(&models.TheSeason{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除季记录失败!", "data": gallery})
			return
		}
		if err := tx.Model(&models.Season{}).Where("the_tv_id = ?", tv.ID).Delete(&models.Season{}).Error; err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除 Season 记录失败!", "data": gallery})
			return
		}
	}
	if err := tx.Model(&models.TheTv{}).Where("gallery_uid = ?", galleryUid).Delete(&models.TheTv{}).Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "删除电视剧记录失败!", "data": gallery})
		return
	}
	
	if err := tx.Model(&models.Gallery{}).Where("id = ?", id).Delete(&models.Gallery{}).Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "删除资源失败!", "data": gallery})
		return
	}
	
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		c.JSON(200, gin.H{"code": 201, "msg": "提交事务失败!", "data": gallery})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "删除资源成功!", "data": gallery})
}

func UpdateGalleryById(c *gin.Context) {
	id := c.Query("id")
	gallery := models.Gallery{}
	err := c.ShouldBind(&gallery)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "创建失败,表单解析出错!", "data": gallery})
		return
	}
	db := database.NewDb()
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		gallery, err := galleryRepository.UpdateByID(id, gallery)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": gallery})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "更新资源成功!", "data": gallery})
	}(repo)
}

func GetGalleryById(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		gallery, err := galleryRepository.FindByID(id)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": gallery})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": gallery})
	}(repo)
}

func GetGalleryList(c *gin.Context) {
	page, errPage := strconv.Atoi(c.Query("page"))
	size, errSize := strconv.Atoi(c.Query("size"))
	if errPage != nil {
		page = 1
	}
	if errSize != nil {
		size = 8
	}
	db := database.NewDb()
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		gallerys, num, err := galleryRepository.FindAll(page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": gallerys, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": gallerys, "num": num})
	}(repo)
}

func GetGalleryListAdmin(c *gin.Context) {
	page, errPage := strconv.Atoi(c.Query("page"))
	size, errSize := strconv.Atoi(c.Query("size"))
	if errPage != nil {
		page = 1
	}
	if errSize != nil {
		size = 8
	}
	db := database.NewDb()
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		gallerys, num, err := galleryRepository.FindAllByAdmin(page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": gallerys, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": gallerys, "num": num})
	}(repo)
}


func GetGalleryHostByUid(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		gallery, err := galleryRepository.FindByUID(id)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": "", "is_ali_open": false})
			return
		}
		if gallery.IsAlist {
			c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": gallery.AlistHost, "is_ali_open": gallery.IsAliOpen})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": "", "is_ali_open": gallery.IsAliOpen})
	}(repo)
}

func SearchGallery(c *gin.Context) {
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
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		gallerys, num, err := galleryRepository.Search(q, page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": gallerys, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": gallerys, "num": num})
	}(repo)
}

// 浏览Alist目录结构
func GetAlistDirectory(c *gin.Context) {
	id := c.Query("id")
	if len(id) == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误!", "data": ""})
		return
	}
	path := c.Query("path")
	if len(path) == 0 {
		path = "/"
	}
	db := database.NewDb()
	gallery := models.Gallery{}
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", id).First(&gallery).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "媒体库不存在!", "data": ""})
		return
	}
	if !gallery.IsAlist {
		c.JSON(200, gin.H{"code": 201, "msg": "该媒体库不是Alist类型!", "data": ""})
		return
	}
	dirs, err := alist.GetAlistDirectoryList(gallery, path)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "获取目录失败: " + err.Error(), "data": ""})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "获取目录成功!", "data": dirs})
}

// 获取Alist目录树（递归）
func GetAlistDirectoryTree(c *gin.Context) {
	id := c.Query("id")
	if len(id) == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误!", "data": ""})
		return
	}
	path := c.Query("path")
	if len(path) == 0 {
		path = "/"
	}
	depthStr := c.Query("depth")
	depth := 2
	if len(depthStr) > 0 {
		depth, _ = strconv.Atoi(depthStr)
	}
	db := database.NewDb()
	gallery := models.Gallery{}
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", id).First(&gallery).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "媒体库不存在!", "data": ""})
		return
	}
	if !gallery.IsAlist {
		c.JSON(200, gin.H{"code": 201, "msg": "该媒体库不是Alist类型!", "data": ""})
		return
	}
	tree, err := alist.GetAlistDirectoryTree(gallery, path, depth)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "获取目录树失败: " + err.Error(), "data": ""})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "获取目录树成功!", "data": tree})
}

// 扫描Alist目录并创建刮削任务
func AutoScanAlist(c *gin.Context) {
	id := c.Query("id")
	if len(id) == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误!", "data": ""})
		return
	}
	path := c.Query("path")
	if len(path) == 0 {
		path = "/"
	}
	db := database.NewDb()
	gallery := models.Gallery{}
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", id).First(&gallery).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "媒体库不存在!", "data": ""})
		return
	}
	if !gallery.IsAlist {
		c.JSON(200, gin.H{"code": 201, "msg": "该媒体库不是Alist类型!", "data": ""})
		return
	}
	dirs, err := alist.GetAlistDirectoryList(gallery, path)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "获取目录失败: " + err.Error(), "data": ""})
		return
	}
	if len(dirs) == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": "该目录下没有子目录!", "data": ""})
		return
	}
	for _, dir := range dirs {
		files, err := alist.GetAlistFilesPath(dir.Path, false, gallery)
		if err != nil || len(files) == 0 {
			continue
		}
		work := models.Work{
			GalleryID:  gallery.Id,
			GalleryUid: gallery.GalleryUid,
			Path:       dir.Path,
			FileNumber: len(files),
			Speed:      0,
			IsOk:       false,
			Watching:   true,
			IsRef:      false,
		}
		err = db.Model(&models.Work{}).Create(&work).Error
		if err == nil {
			go RunWork(files, work, gallery)
		}
	}
	c.JSON(200, gin.H{"code": 200, "msg": "扫描完成，已创建刮削任务!", "data": len(dirs)})
}
