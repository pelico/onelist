package controllers

import (
	"errors"
	"net/url"
	"path/filepath"
	"strconv"
	"strings"
	"sync/atomic"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/repository"
	"github.com/msterzhang/onelist/api/repository/crud"
	"github.com/msterzhang/onelist/api/utils/dir"
	"github.com/msterzhang/onelist/api/utils/gpool"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/plugins/alist"
	"github.com/msterzhang/onelist/plugins/thedb"
	"gorm.io/gorm"

	"github.com/gin-gonic/gin"
)

func SaveErrFile(file string, errMsg string, galleryUid string, workId uint, isTv bool) {
	db := database.NewDb()
	errFile := models.ErrFile{File: file, GalleryUid: galleryUid, WorkId: workId, IsTv: isTv, ErrMsg: errMsg}
	err := db.Model(&models.ErrFile{}).Create(&errFile).Error
	if err != nil {
		return
	}
}

// 创建基础电影记录（文件名作标题，可立即播放）
func CreateBasicMovieRecord(file string, galleryUid string) error {
	db := database.NewDb()
	fileName := filepath.Base(file)
	title := strings.TrimSuffix(fileName, filepath.Ext(fileName))
	
	movie := models.TheMovie{
		Title:      title,
		Url:        file,
		GalleryUid: galleryUid,
	}
	return db.Model(&models.TheMovie{}).Create(&movie).Error
}

// 创建基础电视剧记录（文件名作标题，可立即播放）
func CreateBasicTvRecord(file string, galleryUid string) error {
	db := database.NewDb()
	fileName := filepath.Base(file)
	title := strings.TrimSuffix(fileName, filepath.Ext(fileName))
	
	tv := models.TheTv{
		Name:       title,
		GalleryUid: galleryUid,
	}
	return db.Model(&models.TheTv{}).Create(&tv).Error
}

// 并发刮削配置
var scrapeConcurrency = 3

// 开始刮削任务（先创建基础记录，再并发刮削更新）
func RunWork(files []string, work models.Work, gallery models.Gallery) {
	db := database.NewDb()
	logger.Info("work", "开始刮削任务", "路径: "+work.Path+", 文件数: "+strconv.Itoa(len(files)))

	// 先创建基础记录，让视频可立即播放
	for _, file := range files {
		if gallery.GalleryType == "tv" {
			_ = CreateBasicTvRecord(file, gallery.GalleryUid)
		} else {
			_ = CreateBasicMovieRecord(file, gallery.GalleryUid)
		}
	}

	// 更新进度为已创建基础记录
	work.Speed = len(files)
	db.Model(&models.Work{}).Where("id = ?", work.Id).Select("*").Updates(&work)

	// 并发刮削（限制并发数）
	pool := gpool.New(scrapeConcurrency)
	var successCount int64 = 0
	var errCount int64 = 0
	for _, file := range files {
		pool.Add(1)
		go func(f string) {
			defer pool.Done()
			var scrapeErr error
			if gallery.GalleryType == "tv" {
				_, scrapeErr = thedb.RunTheTvWork(f, gallery.GalleryUid)
			} else {
				_, scrapeErr = thedb.RunTheMovieWork(f, gallery.GalleryUid)
			}
			if scrapeErr != nil {
				atomic.AddInt64(&errCount, 1)
				logger.Warn("work", "刮削失败: "+f, scrapeErr.Error())
				SaveErrFile(f, scrapeErr.Error(), gallery.GalleryUid, work.Id, gallery.GalleryType == "tv")
			} else {
				atomic.AddInt64(&successCount, 1)
			}
		}(file)
	}
	pool.Wait()

	work.IsOk = true
	db.Model(&models.Work{}).Where("id = ?", work.Id).Select("*").Updates(&work)
	logger.Info("work", "刮削任务完成", "路径: "+work.Path+", 成功: "+strconv.FormatInt(successCount, 10)+", 失败: "+strconv.FormatInt(errCount, 10))
}

// 只刮削目录中新增的文件（先创建基础记录，再并发刮削）
func RunWorkNew(files []string, work models.Work, gallery models.Gallery) {
	db := database.NewDb()
	
	// 先创建基础记录
	for _, file := range files {
		if gallery.GalleryType == "tv" {
			episode := models.Episode{}
			err := db.Model(&models.Episode{}).Where("url = ?", file).First(&episode).Error
			if errors.Is(err, gorm.ErrRecordNotFound) {
				_ = CreateBasicTvRecord(file, gallery.GalleryUid)
			}
		} else {
			themovie := models.TheMovie{}
			err := db.Model(&models.TheMovie{}).Where("url = ?", file).First(&themovie).Error
			if errors.Is(err, gorm.ErrRecordNotFound) {
				_ = CreateBasicMovieRecord(file, gallery.GalleryUid)
			}
		}
	}
	
	// 更新进度
	work.Speed = len(files)
	db.Model(&models.Work{}).Where("id = ?", work.Id).Select("*").Updates(&work)
	
	// 并发刮削
	pool := gpool.New(scrapeConcurrency)
	for _, file := range files {
		pool.Add(1)
		go func(f string) {
			defer pool.Done()
			var scrapeErr error
			if gallery.GalleryType == "tv" {
				_, scrapeErr = thedb.RunTheTvWork(f, gallery.GalleryUid)
			} else {
				_, scrapeErr = thedb.RunTheMovieWork(f, gallery.GalleryUid)
			}
			if scrapeErr != nil {
				SaveErrFile(f, scrapeErr.Error(), gallery.GalleryUid, work.Id, gallery.GalleryType == "tv")
			}
		}(file)
	}
	pool.Wait()
	
	work.IsOk = true
	db.Model(&models.Work{}).Where("id = ?", work.Id).Select("*").Updates(&work)
}

// 清理Alist路径，移除URL前缀并解码URL编码
func cleanAlistPath(path string) string {
	path = strings.TrimSpace(path)
	// 尝试URL解码（处理中文路径被编码的问题）
	if decoded, err := url.QueryUnescape(path); err == nil {
		path = decoded
	}
	// 如果路径包含 http:// 或 https://，提取相对路径部分
	if strings.Contains(path, "http://") || strings.Contains(path, "https://") {
		// 移除 http:// 或 https://
		path = strings.TrimPrefix(path, "http://")
		path = strings.TrimPrefix(path, "https://")
		// 移除域名部分（第一个/之后的内容）
		idx := strings.Index(path, "/")
		if idx >= 0 {
			path = path[idx:]
		} else {
			path = "/"
		}
	}
	// 确保路径以 / 开头
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return path
}

func CreateWork(c *gin.Context) {
	work := models.Work{}
	err := c.ShouldBind(&work)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "创建失败,表单解析出错!", "data": work})
		return
	}
	// 清理Alist路径格式
	work.Path = cleanAlistPath(work.Path)
	gallery := models.Gallery{}
	db := database.NewDb()
	err = db.Model(&models.Gallery{}).Where("gallery_uid = ?", work.GalleryUid).First(&gallery).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "Gallery not found!", "data": work})
		return
	}
	work.GalleryUid = gallery.GalleryUid
	work.FileNumber = 0
	work.Speed = 0
	work.IsOk = false
	err = db.Model(&models.Work{}).Create(&work).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": err.Error(), "data": work})
		return
	}
	go func() {
		var files []string
		if gallery.IsAlist {
			files, err = alist.GetAlistFilesPath(work.Path, work.IsRef, gallery)
			if err != nil {
				db.Model(&models.Work{}).Where("id = ?", work.Id).Update("is_ok", true)
				return
			}
		} else {
			files = dir.GetFilesByPath(work.Path)
		}
		if len(files) == 0 {
			db.Model(&models.Work{}).Where("id = ?", work.Id).Update("is_ok", true)
			return
		}
		db.Model(&models.Work{}).Where("id = ?", work.Id).Update("file_number", len(files))
		work.FileNumber = len(files)
		RunWork(files, work, gallery)
	}()
	c.JSON(200, gin.H{"code": 200, "msg": "创建刮削任务成功!", "data": work})
}

// 重新刮削
func ReNewWork(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	work := models.Work{}
	err := db.Model(&models.Work{}).Where("id = ?", id).First(&work).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "Work not found!", "data": work})
		return
	}
	gallery := models.Gallery{}
	err = db.Model(&models.Gallery{}).Where("gallery_uid = ?", work.GalleryUid).First(&gallery).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "Gallery not found!", "data": work})
		return
	}
	work.GalleryUid = gallery.GalleryUid
	var files = []string{}
	if gallery.IsAlist {
		files, err = alist.GetAlistFilesPath(work.Path, work.IsRef, gallery)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": err.Error(), "data": ""})
			return
		}
	} else {
		files = dir.GetFilesByPath(work.Path)
	}
	if len(files) == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": errors.New("files is 0"), "data": ""})
		return
	}
	work.FileNumber = len(files)
	work.Speed = 0
	err = db.Model(&models.Work{}).Where("id = ?", work.Id).Select("*").Updates(&work).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": err.Error(), "data": ""})
		return
	}
	mod := c.Query("mod")
	if mod == "new" {
		go RunWorkNew(files, work, gallery)
		c.JSON(200, gin.H{"code": 200, "msg": "重启刮削任务成功,只刮削新增文件!", "data": work})
		return
	}
	go RunWork(files, work, gallery)
	c.JSON(200, gin.H{"code": 200, "msg": "重启刮削任务成功!", "data": work})
}

func DeleteWorkById(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	repo := crud.NewRepositoryWorksCRUD(db)
	func(workRepository repository.WorkRepository) {
		work, err := workRepository.DeleteByID(id)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": work})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "删除资源成功!", "data": work})
	}(repo)
}

func UpdateWorkById(c *gin.Context) {
	id := c.Query("id")
	work := models.Work{}
	err := c.ShouldBind(&work)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "创建失败,表单解析出错!", "data": work})
		return
	}
	// 清理Alist路径格式
	work.Path = cleanAlistPath(work.Path)
	db := database.NewDb()
	repo := crud.NewRepositoryWorksCRUD(db)
	func(workRepository repository.WorkRepository) {
		work, err := workRepository.UpdateByID(id, work)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": work})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "更新资源成功!", "data": work})
	}(repo)
}

func GetWorkById(c *gin.Context) {
	id := c.Query("id")
	db := database.NewDb()
	repo := crud.NewRepositoryWorksCRUD(db)
	func(workRepository repository.WorkRepository) {
		work, err := workRepository.FindByID(id)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": work})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": work})
	}(repo)
}

func GetWorkList(c *gin.Context) {
	page, errPage := strconv.Atoi(c.Query("page"))
	size, errSize := strconv.Atoi(c.Query("size"))
	if errPage != nil {
		page = 1
	}
	if errSize != nil {
		size = 8
	}
	db := database.NewDb()
	repo := crud.NewRepositoryWorksCRUD(db)
	func(workRepository repository.WorkRepository) {
		works, num, err := workRepository.FindAll(page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": works, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": works, "num": num})
	}(repo)
}

func SearchWork(c *gin.Context) {
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
	repo := crud.NewRepositoryWorksCRUD(db)
	func(workRepository repository.WorkRepository) {
		works, num, err := workRepository.Search(q, page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": works, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": works, "num": num})
	}(repo)
}

func GetWorkListByGalleryId(c *gin.Context) {
	id := c.Query("id")
	if len(id) == 0 {
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
	repo := crud.NewRepositoryWorksCRUD(db)
	func(workRepository repository.WorkRepository) {
		works, num, err := workRepository.GetWorkListByGalleryId(id, page, size)
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": works, "num": num})
			return
		}
		c.JSON(200, gin.H{"code": 200, "msg": "查询资源成功!", "data": works, "num": num})
	}(repo)
}

// 启动时恢复未完成的刮削任务
func ResumePendingWorks() {
	db := database.NewDb()
	var works []models.Work
	// 查找所有未完成的任务（排除文件数为0的空任务）
	err := db.Model(&models.Work{}).Where("is_ok = ? AND file_number > 0", false).Find(&works).Error
	if err != nil {
		logger.Warn("work", "恢复未完成任务失败", err.Error())
		return
	}
	if len(works) == 0 {
		return
	}
	logger.Info("work", "发现未完成刮削任务", "数量: "+strconv.Itoa(len(works)))

	for _, work := range works {
		// 查找关联的 gallery
		gallery := models.Gallery{}
		err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", work.GalleryUid).First(&gallery).Error
		if err != nil {
			logger.Warn("work", "恢复任务失败,Gallery不存在", "WorkId: "+strconv.Itoa(int(work.Id)))
			db.Model(&models.Work{}).Where("id = ?", work.Id).Update("is_ok", true)
			continue
		}
		// 重新获取文件列表
		var files []string
		if gallery.IsAlist {
			files, err = alist.GetAlistFilesPath(work.Path, work.IsRef, gallery)
		} else {
			files = dir.GetFilesByPath(work.Path)
		}
		if err != nil || len(files) == 0 {
			logger.Warn("work", "恢复任务失败,获取文件列表为空", "路径: "+work.Path)
			db.Model(&models.Work{}).Where("id = ?", work.Id).Update("is_ok", true)
			continue
		}
		// 异步重新执行刮削
		go RunWork(files, work, gallery)
		logger.Info("work", "已恢复刮削任务", "路径: "+work.Path)
	}
}
