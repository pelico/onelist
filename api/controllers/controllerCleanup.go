package controllers

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/plugins/alist"
	"gorm.io/gorm"
)

// CleanupLibrary 一键清理：清除失效记录（文件已不存在）+ 重复记录（同名只留最新）
func CleanupLibrary(c *gin.Context) {
	db := database.NewDb()

	var galleries []models.Gallery
	db.Find(&galleries)

	validGalleryUids := make([]string, 0, len(galleries))
	for _, g := range galleries {
		validGalleryUids = append(validGalleryUids, g.GalleryUid)
	}

	orphanCount := 0
	dupCount := 0

	// ---------- 第 0 步：清理 gallery_uid 不属于任何现存媒体库的记录 ----------
	var danglingMovies []models.TheMovie
	db.Where("gallery_uid NOT IN (?)", validGalleryUids).Find(&danglingMovies)
	for _, m := range danglingMovies {
		deleteMovieAndRelations(db, m.ID)
		orphanCount++
		logger.Debug("cleanup", "清理无归属失效电影", "标题: "+m.Title+", gallery_uid: "+m.GalleryUid)
	}

	var danglingTvs []models.TheTv
	db.Where("gallery_uid NOT IN (?)", validGalleryUids).Find(&danglingTvs)
	for _, tv := range danglingTvs {
		deleteTvAndRelations(db, tv.ID)
		orphanCount++
		logger.Debug("cleanup", "清理无归属失效剧集", "名称: "+tv.Name+", gallery_uid: "+tv.GalleryUid)
	}

	// ---------- 第 1/2 步：针对现存媒体库内部的文件缺失 + 同库内重复 ----------
	for _, gallery := range galleries {
		fileSet := buildFileSet(db, gallery)
		if gallery.GalleryType == "movie" {
			orphanCount += cleanupOrphanMovies(db, gallery, fileSet)
		} else {
			orphanCount += cleanupOrphanTvs(db, gallery, fileSet)
		}
	}

	// ---------- 第 3 步：全局去重 ----------
	dupCount += dedupMoviesGlobal(db)
	dupCount += dedupTvsGlobal(db)

	msg := fmt.Sprintf("清理完成：失效记录 %d 条，重复记录 %d 条", orphanCount, dupCount)
	logger.Info("cleanup", "一键清理", msg)
	c.JSON(200, gin.H{"code": 200, "msg": msg, "data": ""})
}

// buildFileSet 获取媒体库下所有 Work 路径的当前文件集合
func buildFileSet(db *gorm.DB, gallery models.Gallery) map[string]bool {
	fileSet := make(map[string]bool)

	var works []models.Work
	db.Where("gallery_uid = ?", gallery.GalleryUid).Find(&works)

	for _, work := range works {
		if gallery.IsAlist {
			files, err := alist.GetAlistFilesPath(work.Path, false, gallery)
			if err != nil {
				logger.Warn("cleanup", "获取文件列表失败", "路径: "+work.Path+", 错误: "+err.Error())
				continue
			}
			for _, f := range files {
				fileSet[f] = true
			}
		} else {
			// 本地文件系统
			err := filepath.Walk(work.Path, func(path string, info os.FileInfo, err error) error {
				if err != nil {
					return nil
				}
				if !info.IsDir() {
					fileSet[path] = true
				}
				return nil
			})
			if err != nil {
				logger.Warn("cleanup", "遍历本地目录失败", "路径: "+work.Path+", 错误: "+err.Error())
			}
		}
	}

	return fileSet
}

// cleanupOrphanMovies 清理电影失效记录（文件已不存在）
func cleanupOrphanMovies(db *gorm.DB, gallery models.Gallery, fileSet map[string]bool) int {
	var movies []models.TheMovie
	db.Where("gallery_uid = ?", gallery.GalleryUid).Find(&movies)

	deleted := 0
	for _, m := range movies {
		if m.Url == "" {
			continue
		}
		if !fileSet[m.Url] {
			// 检查是否是本地文件且存在
			if !gallery.IsAlist {
				if _, err := os.Stat(m.Url); err == nil {
					continue
				}
			}
			deleteMovieAndRelations(db, m.ID)
			deleted++
			logger.Debug("cleanup", "清理失效电影记录", "标题: "+m.Title+", url: "+m.Url)
		}
	}
	return deleted
}

// cleanupOrphanTvs 清理剧集失效记录
func cleanupOrphanTvs(db *gorm.DB, gallery models.Gallery, fileSet map[string]bool) int {
	deleted := 0

	// 先清理不存在的剧集文件
	var episodes []models.Episode
	db.Find(&episodes)
	for _, ep := range episodes {
		if ep.Url == "" {
			continue
		}
		if !fileSet[ep.Url] {
			if !gallery.IsAlist {
				if _, err := os.Stat(ep.Url); err == nil {
					continue
				}
			}
			db.Unscoped().Where("id = ?", ep.ID).Delete(&models.Episode{})
		}
	}

	// 再清理没有任何剧集的 TV 记录
	var tvs []models.TheTv
	db.Where("gallery_uid = ?", gallery.GalleryUid).Find(&tvs)
	for _, tv := range tvs {
		var epCount int64
		db.Model(&models.Episode{}).
			Joins("JOIN the_seasons ON episodes.the_season_id = the_seasons.id").
			Where("the_seasons.the_tv_id = ?", tv.ID).
			Count(&epCount)
		if epCount == 0 {
			deleteTvAndRelations(db, tv.ID)
			deleted++
			logger.Debug("cleanup", "清理失效剧集记录", "名称: "+tv.Name)
		}
	}
	return deleted
}

// dedupMoviesGlobal 电影全局去重：同 title 只留 updated_at 最新的一条
func dedupMoviesGlobal(db *gorm.DB) int {
	var movies []models.TheMovie
	db.Order("updated_at desc").Find(&movies)

	seen := make(map[string]bool)
	deleted := 0
	for _, m := range movies {
		key := strings.TrimSpace(m.Title)
		if key == "" {
			continue
		}
		if seen[key] {
			deleteMovieAndRelations(db, m.ID)
			deleted++
			logger.Debug("cleanup", "去重电影记录", "标题: "+m.Title+", 删除id: "+fmt.Sprintf("%d", m.ID))
		} else {
			seen[key] = true
		}
	}
	return deleted
}

// dedupTvsGlobal 剧集全局去重：同 name 只留 updated_at 最新的一条
func dedupTvsGlobal(db *gorm.DB) int {
	var tvs []models.TheTv
	db.Order("updated_at desc").Find(&tvs)

	seen := make(map[string]bool)
	deleted := 0
	for _, tv := range tvs {
		key := strings.TrimSpace(tv.Name)
		if key == "" {
			continue
		}
		if seen[key] {
			deleteTvAndRelations(db, tv.ID)
			deleted++
			logger.Debug("cleanup", "去重剧集记录", "名称: "+tv.Name+", 删除id: "+fmt.Sprintf("%d", tv.ID))
		} else {
			seen[key] = true
		}
	}
	return deleted
}

// deleteMovieAndRelations 删除电影记录及关联数据
func deleteMovieAndRelations(db *gorm.DB, movieID int) {
	db.Where("data_type = ? AND data_id = ?", "movie", movieID).Delete(&models.Played{})
	db.Where("data_type = ? AND data_id = ?", "movie", movieID).Delete(&models.Star{})
	db.Where("data_type = ? AND data_id = ?", "movie", movieID).Delete(&models.Heart{})
	db.Where("data_type = ? AND data_id = ?", "movie", movieID).Delete(&models.ErrFile{})
	db.Unscoped().Where("id = ?", movieID).Delete(&models.TheMovie{})
}

// deleteTvAndRelations 删除剧集记录及关联数据
func deleteTvAndRelations(db *gorm.DB, tvID int) {
	db.Where("data_type = ? AND data_id = ?", "tv", tvID).Delete(&models.Played{})
	db.Where("data_type = ? AND data_id = ?", "tv", tvID).Delete(&models.Star{})
	db.Where("data_type = ? AND data_id = ?", "tv", tvID).Delete(&models.Heart{})
	db.Where("data_type = ? AND data_id = ?", "tv", tvID).Delete(&models.ErrFile{})

	var seasons []models.TheSeason
	db.Where("the_tv_id = ?", tvID).Find(&seasons)
	for _, s := range seasons {
		db.Unscoped().Where("the_season_id = ?", s.ID).Delete(&models.Episode{})
	}
	db.Unscoped().Where("the_tv_id = ?", tvID).Delete(&models.TheSeason{})
	db.Unscoped().Where("the_tv_id = ?", tvID).Delete(&models.Season{})
	db.Unscoped().Where("id = ?", tvID).Delete(&models.TheTv{})
}
