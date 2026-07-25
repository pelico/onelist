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
	
	tx.Model(&models.Work{}).Where("gallery_uid = ?", galleryUid).Delete(&models.Work{})
	tx.Model(&models.ErrFile{}).Where("gallery_uid = ?", galleryUid).Delete(&models.ErrFile{})
	
	var movies []models.TheMovie
	tx.Model(&models.TheMovie{}).Where("gallery_uid = ?", galleryUid).Find(&movies)
	for _, movie := range movies {
		tx.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Played{})
		tx.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Star{})
		tx.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Heart{})
	}
	tx.Model(&models.TheMovie{}).Where("gallery_uid = ?", galleryUid).Delete(&models.TheMovie{})
	
	var tvs []models.TheTv
	tx.Model(&models.TheTv{}).Where("gallery_uid = ?", galleryUid).Find(&tvs)
	for _, tv := range tvs {
		tx.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Played{})
		tx.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Star{})
		tx.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Heart{})
		
		var seasons []models.TheSeason
		tx.Model(&models.TheSeason{}).Where("the_tv_id = ?", tv.ID).Find(&seasons)
		for _, season := range seasons {
			tx.Model(&models.Episode{}).Where("the_season_id = ?", season.ID).Delete(&models.Episode{})
		}
		tx.Model(&models.TheSeason{}).Where("the_tv_id = ?", tv.ID).Delete(&models.TheSeason{})
		tx.Model(&models.Season{}).Where("the_tv_id = ?", tv.ID).Delete(&models.Season{})
	}
	tx.Model(&models.TheTv{}).Where("gallery_uid = ?", galleryUid).Delete(&models.TheTv{})
	
	repo := crud.NewRepositoryGallerysCRUD(db)
	func(galleryRepository repository.GalleryRepository) {
		_, err := galleryRepository.DeleteByID(id)
		if err != nil {
			tx.Rollback()
			c.JSON(200, gin.H{"code": 201, "msg": "删除资源失败!", "data": gallery})
			return
		}
	}(repo)
	
	tx.Commit()
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
