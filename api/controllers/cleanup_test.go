package controllers

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/msterzhang/onelist/api/models"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
	"gorm.io/gorm/schema"
)

func namingStrategy() schema.NamingStrategy {
	return schema.NamingStrategy{SingularTable: true}
}

func setupCleanupDb(t *testing.T) *gorm.DB {
	// 每个测试用独立命名的内存库（共享缓存模式下同名 memory 库会跨测试串数据）
	dsn := "file:" + t.Name() + "?mode=memory&cache=shared"
	db, err := gorm.Open(sqlite.Open(dsn), &gorm.Config{
		Logger:         logger.Default.LogMode(logger.Silent),
		NamingStrategy: namingStrategy(),
	})
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	sqlDB, _ := db.DB()
	sqlDB.SetMaxOpenConns(1)
	if err := db.AutoMigrate(
		&models.TheTv{}, &models.TheSeason{}, &models.Episode{}, &models.Season{},
		&models.TheMovie{}, &models.Work{}, &models.Gallery{}, &models.Played{}, &models.Star{}, &models.Heart{}, &models.ErrFile{},
	); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	return db
}

func mkTvWithEp(t *testing.T, db *gorm.DB, name, uid, epUrl string) *models.TheTv {
	tv := models.TheTv{Name: name, GalleryUid: uid, PosterPath: "/"}
	if err := db.Create(&tv).Error; err != nil {
		t.Fatalf("create tv: %v", err)
	}
	s := models.TheSeason{Name: "第 1 季", SeasonNumber: 1, TheTvID: uint(tv.ID)}
	if err := db.Create(&s).Error; err != nil {
		t.Fatalf("create season: %v", err)
	}
	ep := models.Episode{Name: name + " E01", Url: epUrl, EpisodeNumber: 1, SeasonNumber: 1, TheSeasonID: uint(s.ID)}
	if err := db.Create(&ep).Error; err != nil {
		t.Fatalf("create episode: %v", err)
	}
	return &tv
}

// T1: 同库两个挂载目录，fileSet 只含 work1 的文件 → 只删 work2 的剧，work1 的剧保留
func TestCleanupOrphanTvs_MultiWork(t *testing.T) {
	db := setupCleanupDb(t)
	g := models.Gallery{Title: "剧库", GalleryType: "tv"}
	db.Create(&g) // GalleryUid 由 BeforeCreate 生成

	w1 := mkTvWithEp(t, db, "布鲁伊", g.GalleryUid, "/data/剧/布鲁伊/S01E01.mp4")
	_ = mkTvWithEp(t, db, "瑞克和莫蒂", g.GalleryUid, "/data/剧/瑞克和莫蒂/S01E01.mp4")
	_ = w1

	fileSet := map[string]bool{"/data/剧/布鲁伊/S01E01.mp4": true}
	deleted := cleanupOrphanTvs(db, g, fileSet)

	if deleted != 1 {
		t.Fatalf("expect 1 deleted, got %d", deleted)
	}
	var count int64
	db.Model(&models.TheTv{}).Where("name = ?", "布鲁伊").Count(&count)
	if count != 1 {
		t.Fatalf("work1 tv should survive, got count %d", count)
	}
	db.Model(&models.TheTv{}).Where("name = ?", "瑞克和莫蒂").Count(&count)
	if count != 0 {
		t.Fatalf("work2 tv should be deleted, got count %d", count)
	}
	_ = w1
}

// T2: fileSet 为空（文件列表获取失败）→ 什么都不删
func TestCleanupOrphanTvs_EmptyFileSet(t *testing.T) {
	db := setupCleanupDb(t)
	g := models.Gallery{Title: "剧库", GalleryType: "tv"}
	db.Create(&g)
	mkTvWithEp(t, db, "布鲁伊", g.GalleryUid, "/data/剧/布鲁伊/S01E01.mp4")

	deleted := cleanupOrphanTvs(db, g, map[string]bool{})
	if deleted != 0 {
		t.Fatalf("expect 0 deleted with empty fileSet, got %d", deleted)
	}
	var count int64
	db.Model(&models.TheTv{}).Count(&count)
	if count != 1 {
		t.Fatalf("tv should survive, got count %d", count)
	}
}

// T3: 其他媒体库的剧集不受本库 fileSet 影响
func TestCleanupOrphanTvs_CrossGallery(t *testing.T) {
	db := setupCleanupDb(t)
	g1 := models.Gallery{Title: "剧库1", GalleryType: "tv"}
	g2 := models.Gallery{Title: "剧库2", GalleryType: "tv"}
	db.Create(&g1)
	db.Create(&g2)

	mkTvWithEp(t, db, "布鲁伊", g1.GalleryUid, "/data/1/布鲁伊/S01E01.mp4")
	mkTvWithEp(t, db, "阿布故事", g2.GalleryUid, "/data/2/阿布故事/S01E01.mp4")

	// 库1 的 fileSet 只有库1 的文件，不能误删库2 的剧
	fileSet := map[string]bool{"/data/1/布鲁伊/S01E01.mp4": true}
	deleted := cleanupOrphanTvs(db, g1, fileSet)
	if deleted != 0 {
		t.Fatalf("expect 0 deleted (all work1 files present), got %d", deleted)
	}
	var count int64
	db.Model(&models.TheTv{}).Where("gallery_uid = ?", g2.GalleryUid).Count(&count)
	if count != 1 {
		t.Fatalf("other gallery tv must survive, got count %d", count)
	}
	_ = filepath.Join
}

// T4: 本地文件 os.Stat 存在但不在 fileSet（路径形式差异）→ 保留
func TestCleanupOrphanTvs_LocalStatFallback(t *testing.T) {
	db := setupCleanupDb(t)
	dir := t.TempDir()
	epPath := filepath.Join(dir, "S01E01.mp4")
	if err := os.WriteFile(epPath, []byte("v"), 0644); err != nil {
		t.Fatalf("create file: %v", err)
	}

	g := models.Gallery{Title: "本地剧", GalleryType: "tv"}
	db.Create(&g)
	mkTvWithEp(t, db, "布鲁伊", g.GalleryUid, epPath)

	// fileSet 为空路径形式（work 遍历失败场景），但本地文件真实存在 → 保留
	deleted := cleanupOrphanTvs(db, g, map[string]bool{"placeholder": true})
	if deleted != 0 {
		t.Fatalf("local existing file must survive, got %d", deleted)
	}
}
