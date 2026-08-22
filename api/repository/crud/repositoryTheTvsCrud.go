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

// allowedTvSortModes 白名单：仅允许这些列作为排序字段，防止 SQL 注入
var allowedTvSortModes = map[string]bool{
	"vote_average": true, "last_air_date": true, "updated_at": true,
	"created_at": true, "name": true, "id": true, "popularity": true,
}

// Stor theTv from the DB
func (r *RepositoryTheTvsCRUD) Sort(galleryUid string, mode string, order string, page int, size int) ([]models.TheTv, int, error) {
	if mode == "release_date" {
		mode = "last_air_date"
	}
	// 校验排序参数，防止 SQL 注入（复用 movies 的 order 白名单）
	if !allowedTvSortModes[mode] || !allowedSortOrders[order] {
		return []models.TheTv{}, 0, fmt.Errorf("invalid sort parameter")
	}
	orderSql := fmt.Sprintf("%s %s", mode, order)
	if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
		orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
	}
	// 加上 id 作为次要排序键：不管主排序字段有多少并列值，最终排序结果都是唯一确定的
	stableOrderSql := orderSql + ", id ASC"

	// 先查总数（按 name 去重）
	var total int64
	r.db.Model(&models.TheTv{}).
		Select("name").
		Where("gallery_uid = ?", galleryUid).
		Group("name").
		Count(&total)
	if total == 0 {
		return []models.TheTv{}, 0, nil
	}

	// 直接用 SQL 的 LIMIT/OFFSET 做分页
	var pageIds []int
	err := r.db.Model(&models.TheTv{}).
		Select("MIN(id)").
		Where("gallery_uid = ?", galleryUid).
		Group("name").
		Order(stableOrderSql).
		Limit(size).
		Offset((page - 1) * size).
		Pluck("MIN(id)", &pageIds).Error
	if err != nil {
		return []models.TheTv{}, 0, err
	}

	// 按 ID 列表查询完整记录
	theTvs := []models.TheTv{}
	if len(pageIds) > 0 {
		err = r.db.Model(&models.TheTv{}).
			Where("id IN ?", pageIds).
			Order(stableOrderSql).
			Scan(&theTvs).Error
		if err != nil {
			return []models.TheTv{}, 0, err
		}
	}
	return theTvs, int(total), nil
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

// Search 剧集搜索：按 name 匹配并去重（覆盖通用 Search）
func (r *RepositoryTheTvsCRUD) Search(q string, page int, size int) ([]models.TheTv, int, error) {
	var num int64
	list := []models.TheTv{}
	subQuery := r.db.Model(&models.TheTv{}).
		Select("MIN(id)").
		Where("name LIKE ?", "%"+q+"%").
		Group("name")
	// 使用独立的查询实例，避免 Count 污染 Scan 的 Statement
	r.db.Model(&models.TheTv{}).Where("id IN (?)", subQuery).Count(&num)
	err := r.db.Model(&models.TheTv{}).Where("id IN (?)", subQuery).Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&list).Error
	if err != nil {
		return []models.TheTv{}, 0, err
	}
	return list, int(num), nil
}
