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

// RepositoryTheMoviesCRUD is the struct for the TheMovie CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryTheMoviesCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.TheMovie]
}

// NewRepositoryTheMoviesCRUD returns a new repository with DB connection
func NewRepositoryTheMoviesCRUD(db *gorm.DB) *RepositoryTheMoviesCRUD {
	return &RepositoryTheMoviesCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.TheMovie](db, "themovie"),
	}
}

// Stor themovie from the DB
func (r *RepositoryTheMoviesCRUD) Sort(galleryUid string, mode string, order string, page int, size int) ([]models.TheMovie, int, error) {
	// 第一步：收集去重后的所有 ID（按 url 分组）
	var ids []int
	orderSql := fmt.Sprintf("%s %s", mode, order)
	if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
		orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
	}
	err := r.db.Model(&models.TheMovie{}).
		Select("MIN(id)").
		Where("gallery_uid = ?", galleryUid).
		Group("url").
		Order(orderSql).
		Pluck("MIN(id)", &ids).Error
	if err != nil {
		return []models.TheMovie{}, 0, err
	}

	total := len(ids)
	if total == 0 {
		return []models.TheMovie{}, 0, nil
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
	themovies := []models.TheMovie{}
	err = r.db.Model(&models.TheMovie{}).
		Where("id IN ?", pageIds).
		Order(orderSql).
		Scan(&themovies).Error
	if err != nil {
		return []models.TheMovie{}, 0, err
	}

	return themovies, total, nil
}

// FindByGalleryId themovies from the DB
func (r *RepositoryTheMoviesCRUD) FindByGalleryId(id string, page int, size int) ([]models.TheMovie, int, error) {
	var err error
	var num int64
	themovies := []models.TheMovie{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		subQuery := r.db.Model(&models.TheMovie{}).Select("MIN(id)").Where("gallery_uid = ?", id).Group("url")
		countResult := r.db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery)
		countResult.Count(&num)
		scanResult := r.db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery)
		if config.DBDRIVER == "sqlite" {
			err = scanResult.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&themovies).Error
		} else {
			err = scanResult.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&themovies).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return themovies, int(num), nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []models.TheMovie{}, 0, errors.New("themovies Not Found")
	}
	return []models.TheMovie{}, 0, err
}

// GetLatest 获取最新添加的电影
func (r *RepositoryTheMoviesCRUD) GetLatest(size int) ([]models.TheMovie, error) {
	var err error
	themovies := []models.TheMovie{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		subQuery := r.db.Model(&models.TheMovie{}).Select("MIN(id)").Group("url")
		result := r.db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Order("datetime(created_at) desc").Scan(&themovies).Error
		} else {
			err = result.Limit(size).Order("-created_at").Scan(&themovies).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return themovies, nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []models.TheMovie{}, errors.New("themovies Not Found")
	}
	return []models.TheMovie{}, err
}
