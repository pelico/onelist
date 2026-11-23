package controllers

import (
	"errors"
	"fmt"
	"net/url"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
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
	"github.com/msterzhang/onelist/plugins/watch"
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
	
	var exist models.TheMovie
	err := db.Model(&models.TheMovie{}).Where("url = ?", file).First(&exist).Error
	if err == nil {
		return nil
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return err
	}
	
	movie := models.TheMovie{
		Title:       title,
		Url:         file,
		GalleryUid:  galleryUid,
		PosterPath:  "/",
	}
	return db.Model(&models.TheMovie{}).Create(&movie).Error
}

// tvEpisodeMarkRe 匹配文件名中的集数标记：SxxExx / EPxx / 第N集(话/期)。
// 取最后一段匹配作为集数标记，其前面的内容作为剧名。
var tvEpisodeMarkRe = regexp.MustCompile(`(?i)(?:S(\d{1,4})\s*E(\d{1,4})|EP\s?(\d{1,4})|第(\d{1,4})[集话期])`)

// tvCollectionMarkRe 匹配文件名尾部的合集标记（如 第001-030首合集），同样从剧名中剔除
var tvCollectionMarkRe = regexp.MustCompile(`第\d{1,4}[-~—至]\d{1,4}[首集话]?(?:合集|合辑)?\s*$`)

// seasonDirRe 整个文件夹名就是季标记（如 "S01" / "Season 1" / "第2季"）
var seasonDirRe = regexp.MustCompile(`(?i)^(s\d{1,4}(?:-s\d{1,4})?|season\s*\d{1,4}|第\s*\d{1,4}\s*季)$`)

// seasonSuffixRe 文件夹/文件名尾部的季标记（如 "博博鲁探险队 S01" → "博博鲁探险队"）
var seasonSuffixRe = regexp.MustCompile(`(?i)\s*[-_. ]\s*(s\d{1,4}(?:-s\d{1,4})?|season\s*\d{1,4}|第\d{1,4}季)\s*$`)

// splitPathSegments 按 / 和 \ 拆分路径段（兼容 alist 的 URL 风格路径与本地路径）
func splitPathSegments(p string) []string {
	return strings.FieldsFunc(p, func(r rune) bool { return r == '/' || r == '\\' })
}

func pathHasPrefix(parts []string, prefix []string) bool {
	if len(parts) < len(prefix) {
		return false
	}
	for i := range prefix {
		if parts[i] != prefix[i] {
			return false
		}
	}
	return true
}

// parseEpisodeNumber 从文件名提取集数（取最后一个集数标记），无标记返回 0
func parseEpisodeNumber(name string) int {
	if locs := tvEpisodeMarkRe.FindAllStringSubmatchIndex(name, -1); len(locs) > 0 {
		last := locs[len(locs)-1]
		// 子分组：1=S 2=E 3=EP 4=第N集，取集数值
		switch {
		case last[4] >= 0:
			n, _ := strconv.Atoi(name[last[4]:last[5]])
			return n
		case last[6] >= 0:
			n, _ := strconv.Atoi(name[last[6]:last[7]])
			return n
		case last[8] >= 0:
			n, _ := strconv.Atoi(name[last[8]:last[9]])
			return n
		}
	}
	return 0
}

// resolveTvShowTitle 解析剧集的聚合剧名与集数。
// 聚合优先级：
//  1. 文件位于扫描根下的子文件夹时，用子文件夹名作剧名（一部剧一个文件夹时，
//     文件名带每集副标题、标题再乱也能正确聚合），季文件夹（S01/Season 1/第1季）跳过，
//     文件夹名尾部的季标记剥掉（"布鲁伊 S01" 与 "布鲁伊 S02" 合并为 "布鲁伊"）；
//  2. 文件平铺在扫描根目录时，按文件名集数标记解析剧名（"布鲁伊 S01E01" → "布鲁伊"）；
//  3. 无标记时整个文件名作剧名（单集剧）。
func resolveTvShowTitle(file string, rootPath string) (string, int) {
	segs := splitPathSegments(file)
	name := segs[len(segs)-1]
	if idx := strings.LastIndex(name, "."); idx > 0 {
		name = name[:idx]
	}
	epNumber := parseEpisodeNumber(name)

	dirParts := splitPathSegments(file)
	rootParts := splitPathSegments(rootPath)
	// 从最里层子文件夹向外找第一个"非季文件夹"的层作剧名（最后一层是文件名，故从 len-2 开始）
	if len(dirParts) > len(rootParts) && pathHasPrefix(dirParts, rootParts) {
		for i := len(dirParts) - 2; i >= len(rootParts); i-- {
			if seasonDirRe.MatchString(dirParts[i]) {
				continue
			}
			show := strings.TrimSpace(seasonSuffixRe.ReplaceAllString(dirParts[i], ""))
			if show != "" {
				return show, epNumber
			}
		}
	}

	// 平铺在根目录（或路径不符合预期）：按文件名标记解析
	show := name
	if locs := tvEpisodeMarkRe.FindAllStringSubmatchIndex(name, -1); len(locs) > 0 {
		show = strings.TrimSpace(name[:locs[len(locs)-1][0]])
	}
	if show == "" {
		show = name
	}
	trimmed := tvCollectionMarkRe.ReplaceAllString(show, "")
	if strings.TrimSpace(trimmed) != "" {
		show = strings.TrimSpace(trimmed)
	}
	return show, epNumber
}

// 创建基础电视剧记录（按剧名聚合：同一剧的多集共用一条 TheTv，每集一条 Episode）
func CreateBasicTvRecord(file string, galleryUid string, rootPath string) error {
	db := database.NewDb()
	fileName := splitPathSegments(file)
	fullTitle := fileName[len(fileName)-1]
	if idx := strings.LastIndex(fullTitle, "."); idx > 0 {
		fullTitle = fullTitle[:idx]
	}
	showTitle, epNumber := resolveTvShowTitle(file, rootPath)

	// TheTv 没有 url 字段，按 name + gallery_uid 查重
	var exist models.TheTv
	err := db.Model(&models.TheTv{}).Where("name = ? AND gallery_uid = ?", showTitle, galleryUid).First(&exist).Error
	if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		return err
	}
	tvId := uint(exist.ID)
	if errors.Is(err, gorm.ErrRecordNotFound) {
		tv := models.TheTv{
			Name:       showTitle,
			GalleryUid: galleryUid,
			PosterPath: "/",
		}
		if err := db.Model(&models.TheTv{}).Create(&tv).Error; err != nil {
			return err
		}
		tvId = uint(tv.ID)
	}
	return ensureTvEpisode(db, tvId, file, fullTitle, epNumber)
}

// ensureTvEpisode 确保文件对应的一集挂在该剧的默认季下。
// 存量兼容：旧版本按文件名建了"一文件一剧"的记录，重扫时按 Episode.Url 识别，
// 自动把集迁移合并到剧级记录，并清理因此变空的旧季/旧剧（刷新一次目录即可完成聚合）。
func ensureTvEpisode(db *gorm.DB, tvId uint, file string, epName string, epNumber int) error {
	// 目标季：该剧的第一个季（通常是默认"第 1 季"），没有则创建
	var season models.TheSeason
	err := db.Model(&models.TheSeason{}).
		Where("the_tv_id = ?", tvId).
		Order("season_number ASC, id ASC").
		First(&season).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		season = models.TheSeason{
			Name:         "第 1 季",
			SeasonNumber: 1,
			TheTvID:      tvId,
		}
		if err := db.Create(&season).Error; err != nil {
			return fmt.Errorf("create season: %w", err)
		}
	} else if err != nil {
		return fmt.Errorf("query season: %w", err)
	}

	// 同 url 的集已存在？
	var ep models.Episode
	err = db.Model(&models.Episode{}).Where("url = ?", file).First(&ep).Error
	if err == nil {
		if uint(ep.TheSeasonID) != uint(season.ID) {
			// 迁移：把集从旧的文件名级记录挂到当前剧的默认季
			oldSeasonId := ep.TheSeasonID
			ep.TheSeasonID = uint(season.ID)
			if epNumber > 0 {
				ep.EpisodeNumber = epNumber
			}
			if err := db.Save(&ep).Error; err != nil {
				return fmt.Errorf("migrate episode save: %w", err)
			}
			if err := cleanupEmptySeason(db, oldSeasonId); err != nil {
				return fmt.Errorf("cleanup season: %w", err)
			}
		}
		if err := updateTvCounts(db, tvId); err != nil {
			return fmt.Errorf("update counts(after migrate): %w", err)
		}
		return nil
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("query episode: %w", err)
	}

	// 新集：集号取文件名解析值，解析不出则取该季现有最大集号 + 1
	if epNumber <= 0 {
		var maxNum int
		db.Model(&models.Episode{}).
			Where("the_season_id = ?", season.ID).
			Select("COALESCE(MAX(episode_number), 0)").
			Scan(&maxNum)
		epNumber = maxNum + 1
	}
	episode := models.Episode{
		Name:          epName,
		Url:           file,
		EpisodeNumber: epNumber,
		SeasonNumber:  season.SeasonNumber,
		TheSeasonID:   uint(season.ID),
	}
	if err := db.Model(&models.Episode{}).Create(&episode).Error; err != nil {
		return fmt.Errorf("create episode: %w", err)
	}
	if err := updateTvCounts(db, tvId); err != nil {
		return fmt.Errorf("update counts: %w", err)
	}
	return nil
}

// cleanupEmptySeason 季下已无集时删除该季；若其所属剧因此没有任何季了，连同删除该剧
func cleanupEmptySeason(db *gorm.DB, seasonId uint) error {
	var epCount int64
	db.Model(&models.Episode{}).Where("the_season_id = ?", seasonId).Count(&epCount)
	if epCount > 0 {
		return nil
	}
	var season models.TheSeason
	if err := db.Model(&models.TheSeason{}).Where("id = ?", seasonId).First(&season).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil
		}
		return err
	}
	if err := db.Model(&models.TheSeason{}).Delete(&season).Error; err != nil {
		return err
	}
	var seasonCount int64
	db.Model(&models.TheSeason{}).Where("the_tv_id = ?", season.TheTvID).Count(&seasonCount)
	if seasonCount == 0 {
		return db.Model(&models.TheTv{}).Where("id = ?", season.TheTvID).Delete(&models.TheTv{}).Error
	}
	return nil
}

// updateTvCounts 同步剧集的季数/集数统计字段
func updateTvCounts(db *gorm.DB, tvId uint) error {
	var seasonCount, epCount int64
	db.Model(&models.TheSeason{}).Where("the_tv_id = ?", tvId).Count(&seasonCount)
	db.Model(&models.Episode{}).
		Joins("JOIN the_season ON the_season.id = episode.the_season_id").
		Where("the_season.the_tv_id = ?", tvId).
		Count(&epCount)
	if err := db.Model(&models.TheTv{}).Where("id = ?", tvId).
		Updates(map[string]interface{}{
			"number_of_seasons":  seasonCount,
			"number_of_episodes": epCount,
		}).Error; err != nil {
		return err
	}
	return nil
}

// 全局刮削并发池，所有刮削任务共用，避免多目录扫描时并发数爆炸
var scrapePool = gpool.New(3)

// 开始刮削任务（先创建基础记录，再并发刮削更新）
func RunWork(files []string, work models.Work, gallery models.Gallery) {
	db := database.NewDb()
	logger.Info("work", "开始刮削任务", "路径: "+work.Path+", 文件数: "+strconv.Itoa(len(files)))

	// 先创建基础记录，让视频可立即播放
	for _, file := range files {
		if gallery.GalleryType == "tv" {
			if err := CreateBasicTvRecord(file, gallery.GalleryUid, work.Path); err != nil {
				logger.Error("work", "创建剧集基础记录失败: "+file, err.Error())
			}
		} else {
			if err := CreateBasicMovieRecord(file, gallery.GalleryUid); err != nil {
				logger.Error("work", "创建电影基础记录失败: "+file, err.Error())
			}
		}
	}

	// 并发刮削（限制并发数）—— 使用默认封面时跳过刮削
	var successCount int64 = 0
	var errCount int64 = 0
	if !work.UseDefaultCover {
		var wg sync.WaitGroup
		for _, file := range files {
			wg.Add(1)
			scrapePool.Add(1)
			go func(f string) {
				defer wg.Done()
				defer scrapePool.Done()
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
				// 实时更新进度
				currentSpeed := atomic.LoadInt64(&successCount) + atomic.LoadInt64(&errCount)
				db.Model(&models.Work{}).Where("id = ?", work.Id).Update("speed", currentSpeed)
			}(file)
		}
		wg.Wait()
	} else {
		successCount = int64(len(files))
		logger.Info("work", "使用默认封面,跳过刮削", "路径: "+work.Path+", 文件数: "+strconv.Itoa(len(files)))
		// 使用默认封面时直接设置进度为完成
		db.Model(&models.Work{}).Where("id = ?", work.Id).Update("speed", len(files))
	}

	// 清理不在当前文件列表中的旧记录
	cleanupStaleRecords(db, files, work, gallery)

	// 文件夹级自定义封面:检测固定名图片并写入 CustomPoster(在刮削完成后执行,避免被全量更新覆盖)
	watch.DetectAndApplyFolderCovers(files, gallery)

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
				_ = CreateBasicTvRecord(file, gallery.GalleryUid, work.Path)
			}
		} else {
			themovie := models.TheMovie{}
			err := db.Model(&models.TheMovie{}).Where("url = ?", file).First(&themovie).Error
			if errors.Is(err, gorm.ErrRecordNotFound) {
				_ = CreateBasicMovieRecord(file, gallery.GalleryUid)
			}
		}
	}
	
	// 并发刮削 —— 使用默认封面时跳过刮削
	var successCount int64 = 0
	var errCount int64 = 0
	if !work.UseDefaultCover {
		var wg sync.WaitGroup
		for _, file := range files {
			wg.Add(1)
			scrapePool.Add(1)
			go func(f string) {
				defer wg.Done()
				defer scrapePool.Done()
				var scrapeErr error
				if gallery.GalleryType == "tv" {
					_, scrapeErr = thedb.RunTheTvWork(f, gallery.GalleryUid)
				} else {
					_, scrapeErr = thedb.RunTheMovieWork(f, gallery.GalleryUid)
				}
				if scrapeErr != nil {
					atomic.AddInt64(&errCount, 1)
					SaveErrFile(f, scrapeErr.Error(), gallery.GalleryUid, work.Id, gallery.GalleryType == "tv")
				} else {
					atomic.AddInt64(&successCount, 1)
				}
				// 实时更新进度
				currentSpeed := atomic.LoadInt64(&successCount) + atomic.LoadInt64(&errCount)
				db.Model(&models.Work{}).Where("id = ?", work.Id).Update("speed", currentSpeed)
			}(file)
		}
		wg.Wait()
	} else {
		logger.Info("work", "使用默认封面,跳过刮削", "路径: "+work.Path+", 文件数: "+strconv.Itoa(len(files)))
		// 使用默认封面时直接设置进度为完成
		db.Model(&models.Work{}).Where("id = ?", work.Id).Update("speed", len(files))
	}

	// 文件夹级自定义封面:检测固定名图片并写入 CustomPoster(在刮削完成后执行,避免被全量更新覆盖)
	watch.DetectAndApplyFolderCovers(files, gallery)

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
				logger.Error("work", "获取文件列表失败", "路径: "+work.Path+", 错误: "+err.Error())
				db.Model(&models.Work{}).Where("id = ?", work.Id).Update("is_ok", true)
				return
			}
		} else {
			files = dir.GetFilesByPath(work.Path)
		}
		if len(files) == 0 {
			logger.Info("work", "未发现视频文件", "路径: "+work.Path)
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
		c.JSON(200, gin.H{"code": 201, "msg": "未扫描到任何文件:挂载目录可能未就绪、路径有误或目录为空,请稍后重试!", "data": ""})
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

	work := models.Work{}
	err := db.Model(&models.Work{}).Where("id = ?", id).First(&work).Error
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "没有查询到资源!", "data": work})
		return
	}

	gallery := models.Gallery{}
	db.Model(&models.Gallery{}).Where("gallery_uid = ?", work.GalleryUid).First(&gallery)

	tx := db.Begin()

	tx.Model(&models.ErrFile{}).Where("work_id = ?", work.Id).Delete(&models.ErrFile{})

	// 构建路径前缀用于匹配数据库中的 url
	// Alist 返回的文件路径带 /d 前缀（如 /d/movies/file.mp4），需要与 work.Path 对齐
	pathPrefix := work.Path
	if gallery.IsAlist {
		pathPrefix = "/d" + pathPrefix
	}
	if !strings.HasSuffix(pathPrefix, "/") {
		pathPrefix += "/"
	}

	if gallery.GalleryType == "tv" {
		var episodes []models.Episode
		tx.Model(&models.Episode{}).Where("url LIKE ?", pathPrefix+"%").Find(&episodes)
		for _, ep := range episodes {
			tx.Model(&models.Episode{}).Where("id = ?", ep.ID).Delete(&models.Episode{})
		}
		var tvs []models.TheTv
		tx.Model(&models.TheTv{}).Where("gallery_uid = ?", work.GalleryUid).Find(&tvs)
		for _, tv := range tvs {
			var remainingEps int64
			tx.Model(&models.Episode{}).
				Joins("JOIN the_season ON episode.the_season_id = the_season.id").
				Joins("JOIN the_tv ON the_season.the_tv_id = the_tv.id").
				Where("the_tv.id = ?", tv.ID).
				Count(&remainingEps)
			if remainingEps == 0 {
				tx.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Played{})
				tx.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Star{})
				tx.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Heart{})
				tx.Model(&models.TheSeason{}).Where("the_tv_id = ?", tv.ID).Delete(&models.TheSeason{})
				tx.Model(&models.Season{}).Where("the_tv_id = ?", tv.ID).Delete(&models.Season{})
				tx.Model(&models.TheTv{}).Where("id = ?", tv.ID).Delete(&models.TheTv{})
			}
		}
	} else {
		var movies []models.TheMovie
		tx.Model(&models.TheMovie{}).Where("gallery_uid = ? AND url LIKE ?", work.GalleryUid, pathPrefix+"%").Find(&movies)
		for _, movie := range movies {
			tx.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Played{})
			tx.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Star{})
			tx.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Heart{})
			tx.Model(&models.TheMovie{}).Where("id = ?", movie.ID).Delete(&models.TheMovie{})
		}
	}

	tx.Model(&models.Work{}).Where("id = ?", id).Delete(&models.Work{})
	tx.Commit()

	logger.Info("work", "删除挂载目录，已清理关联记录", "路径: "+work.Path)
	c.JSON(200, gin.H{"code": 200, "msg": "删除资源成功!", "data": work})
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

func cleanupStaleRecords(db *gorm.DB, currentFiles []string, work models.Work, gallery models.Gallery) {
	fileSet := make(map[string]bool)
	for _, f := range currentFiles {
		fileSet[f] = true
	}

	// 构建当前文件名集合（去扩展名），用于比对 TV 基础记录的 name
	fileNames := make(map[string]bool)
	for _, f := range currentFiles {
		fileName := strings.TrimSuffix(filepath.Base(f), filepath.Ext(f))
		fileNames[fileName] = true
	}

	// 构建路径前缀用于匹配数据库中的 url
	// Alist 返回的文件路径带 /d 前缀（如 /d/movies/file.mp4），需要与 work.Path 对齐
	pathPrefix := work.Path
	if gallery.IsAlist {
		pathPrefix = "/d" + pathPrefix
	}
	// 确保路径以 / 结尾，避免 /movies% 匹配到 /moviesxxx
	if !strings.HasSuffix(pathPrefix, "/") {
		pathPrefix += "/"
	}
	logger.Info("work", "清理失效记录", "路径前缀: "+pathPrefix+", 当前文件数: "+strconv.Itoa(len(currentFiles)))

	if gallery.GalleryType == "tv" {
		// 只清理本任务路径范围内的剧集，避免影响同一影库下其他任务的记录
		var episodes []models.Episode
		db.Model(&models.Episode{}).Where("url LIKE ?", pathPrefix+"%").Find(&episodes)
		deletedCount := 0
		for _, ep := range episodes {
			if !fileSet[ep.Url] {
				db.Model(&models.Episode{}).Where("id = ?", ep.ID).Delete(&models.Episode{})
				deletedCount++
			}
		}
		if deletedCount > 0 {
			logger.Info("work", "清理失效剧集记录", "路径: "+work.Path+", 清理数: "+strconv.Itoa(deletedCount))
		}

		// 检查所有电视节目，清理无剧集且文件名不匹配的旧记录
		var tvs []models.TheTv
		db.Model(&models.TheTv{}).Where("gallery_uid = ?", work.GalleryUid).Find(&tvs)
		for _, tv := range tvs {
			// 检查该节目在本任务路径范围内是否还有剧集
			var scopedEps int64
			db.Model(&models.Episode{}).
				Joins("JOIN the_season ON episode.the_season_id = the_season.id").
				Joins("JOIN the_tv ON the_season.the_tv_id = the_tv.id").
				Where("the_tv.id = ? AND episode.url LIKE ?", tv.ID, pathPrefix+"%").
				Count(&scopedEps)
			if scopedEps > 0 {
				continue // 本任务范围内仍有剧集，跳过
			}
			// 本任务范围内无剧集，再检查全局是否也无剧集
			var remainingEps int64
			db.Model(&models.Episode{}).
				Joins("JOIN the_season ON episode.the_season_id = the_season.id").
				Joins("JOIN the_tv ON the_season.the_tv_id = the_tv.id").
				Where("the_tv.id = ?", tv.ID).
				Count(&remainingEps)
			if remainingEps == 0 {
				// 无剧集：可能是未刮削的基础记录，也可能是剧集被清理的旧记录
				// 如果 name 匹配当前文件名，说明是刚创建的基础记录，保留
				if fileNames[tv.Name] {
					continue
				}
				// name 不匹配当前文件名，说明是改名/删除后的孤儿记录，删除
				db.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Played{})
				db.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Star{})
				db.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "tv", tv.ID).Delete(&models.Heart{})
				db.Model(&models.TheSeason{}).Where("the_tv_id = ?", tv.ID).Delete(&models.TheSeason{})
				db.Model(&models.Season{}).Where("the_tv_id = ?", tv.ID).Delete(&models.Season{})
				db.Model(&models.TheTv{}).Where("id = ?", tv.ID).Delete(&models.TheTv{})
				logger.Debug("work", "清理无剧集的电视记录", "名称: "+tv.Name)
			}
		}
	} else {
		// 只清理本任务路径范围内的电影，避免影响同一影库下其他任务的记录
		var movies []models.TheMovie
		db.Model(&models.TheMovie{}).Where("gallery_uid = ? AND url LIKE ?", work.GalleryUid, pathPrefix+"%").Find(&movies)
		deletedCount := 0
		for _, movie := range movies {
			if !fileSet[movie.Url] {
				db.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Played{})
				db.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Star{})
				db.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Heart{})
				db.Model(&models.TheMovie{}).Where("id = ?", movie.ID).Delete(&models.TheMovie{})
				deletedCount++
			}
		}
		if deletedCount > 0 {
			logger.Info("work", "清理失效电影记录", "路径: "+work.Path+", 清理数: "+strconv.Itoa(deletedCount))
		}

		// 去重：同一 url 可能存在多条记录（基础记录 + 刮削后按 TMDB ID 更新了旧记录）
		// 保留 id 最小的（最早创建的），删除其余重复记录
		urlSeen := make(map[string]uint) // url -> 保留的 id
		for _, movie := range movies {
			if !fileSet[movie.Url] {
				continue // 已经被清理的不用管
			}
			if keptId, exists := urlSeen[movie.Url]; exists {
				// 已有保留记录，删除当前这条重复记录
				db.Model(&models.Played{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Played{})
				db.Model(&models.Star{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Star{})
				db.Model(&models.Heart{}).Where("data_type = ? AND data_id = ?", "movie", movie.ID).Delete(&models.Heart{})
				db.Model(&models.TheMovie{}).Where("id = ?", movie.ID).Delete(&models.TheMovie{})
				logger.Debug("work", "去重电影记录", "url: "+movie.Url, "保留id: "+strconv.Itoa(int(keptId))+", 删除id: "+strconv.Itoa(int(movie.ID)))
			} else {
				urlSeen[movie.Url] = movie.ID
			}
		}
	}
}
