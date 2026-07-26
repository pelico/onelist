package crud

import (
	"errors"
	"fmt"
	"strings"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

// RepositoryTheTvsCRUD is the struct for the TheTv CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryTheTvsCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.TheTv]
}

// NewRepositoryTheTvsCRUD returns a new repository with DB connection
func NewRepositoryTheTvsCRUD(db *gorm.DB) *RepositoryTheTvsCRUD {
	return &RepositoryTheTvsCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.TheTv](db, "thetv"),
	}
}

// Stor theTv from the DB
func (r *RepositoryTheTvsCRUD) Sort(galleryUid string, mode string, order string, page int, size int) ([]models.TheTv, int, error) {
	// 第一步：收集去重后的所有 ID（按 name 分组）
	var ids []int
	if mode == "release_date" {
		mode = "last_air_date"
	}
	orderSql := fmt.Sprintf("%s %s", mode, order)
	if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
		orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
	}
	err := r.db.Model(&models.TheTv{}).
		Select("MIN(id)").
		Where("gallery_uid = ?", galleryUid).
		Group("name").
		Order(orderSql).
		Pluck("MIN(id)", &ids).Error
	if err != nil {
		return []models.TheTv{}, 0, err
	}

	total := len(ids)
	if total == 0 {
		return []models.TheTv{}, 0, nil
	}

	// 第二步：对 ID 列表分页
	start := (page - 1) * size
	if start >= total {
		start = 0
	}
	end := start + size
	if end > total {
		end = total
	}
	pageIds := ids[start:end]

	// 第三步：按 ID 列表查询完整记录
	theTvs := []models.TheTv{}
	err = r.db.Model(&models.TheTv{}).
		Where("id IN ?", pageIds).
		Order(orderSql).
		Scan(&theTvs).Error
	if err != nil {
		return []models.TheTv{}, 0, err
	}

	return theTvs, total, nil
}

// FindByGalleryId thetv from the DB
func (r *RepositoryTheTvsCRUD) FindByGalleryId(id string, page int, size int) ([]models.TheTv, int, error) {
	var err error
	var num int64
	thetvs := []models.TheTv{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		subQuery := r.db.Model(&models.TheTv{}).Select("MIN(id)").Where("gallery_uid = ?", id).Group("name")
		countResult := r.db.Model(&models.TheTv{}).Where("id IN (?)", subQuery)
		countResult.Count(&num)
		scanResult := r.db.Model(&models.TheTv{}).Where("id IN (?)", subQuery)
		if config.DBDRIVER == "sqlite" {
			err = scanResult.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&thetvs).Error
		} else {
			err = scanResult.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&thetvs).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return thetvs, int(num), nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []models.TheTv{}, 0, errors.New("thetvs Not Found")
	}
	return []models.TheTv{}, 0, err
}

// GetLatest 获取最新添加的剧集
func (r *RepositoryTheTvsCRUD) GetLatest(size int) ([]models.TheTv, error) {
	var err error
	thetvs := []models.TheTv{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		subQuery := r.db.Model(&models.TheTv{}).Select("MIN(id)").Group("name")
		result := r.db.Model(&models.TheTv{}).Where("id IN (?)", subQuery)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Order("datetime(created_at) desc").Scan(&thetvs).Error
		} else {
			err = result.Limit(size).Order("-created_at").Scan(&thetvs).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return thetvs, nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []models.TheTv{}, errors.New("thetvs Not Found")
	}
	return []models.TheTv{}, err
}
