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
	orderSql := fmt.Sprintf("%s %s", mode, order)
	if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
		orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
	}
	// 加上 id 作为次要排序键：不管主排序字段有多少并列值，最终排序结果都是唯一确定的，
	// 不会再出现"同一个查询、不同次执行结果不一样"的情况
	stableOrderSql := orderSql + ", id ASC"

	// 先查总数（按 url 去重）
	var total int64
	r.db.Model(&models.TheMovie{}).
		Select("url").
		Where("gallery_uid = ?", galleryUid).
		Group("url").
		Count(&total)
	if total == 0 {
		return []models.TheMovie{}, 0, nil
	}

	// 直接用 SQL 的 LIMIT/OFFSET 做分页，而不是每次把全量 ID 拉到内存里再切片，
	// 库大了以后也不会每翻一页就全表扫一遍
	var pageIds []int
	err := r.db.Model(&models.TheMovie{}).
		Select("MIN(id)").
		Where("gallery_uid = ?", galleryUid).
		Group("url").
		Order(stableOrderSql).
		Limit(size).
		Offset((page - 1) * size).
		Pluck("MIN(id)", &pageIds).Error
	if err != nil {
		return []models.TheMovie{}, 0, err
	}

	// 按 ID 列表查询完整记录
	themovies := []models.TheMovie{}
	if len(pageIds) > 0 {
		err = r.db.Model(&models.TheMovie{}).
			Where("id IN ?", pageIds).
			Order(stableOrderSql).
			Scan(&themovies).Error
		if err != nil {
			return []models.TheMovie{}, 0, err
		}
	}
	return themovies, int(total), nil
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

// Search 电影搜索：按 title 和 original_title 匹配（覆盖通用 Search 的 name 字段匹配）
func (r *RepositoryTheMoviesCRUD) Search(q string, page int, size int) ([]models.TheMovie, int, error) {
	var num int64
	list := []models.TheMovie{}
	subQuery := r.db.Model(&models.TheMovie{}).
		Select("MIN(id)").
		Where("title LIKE ? OR original_title LIKE ?", "%"+q+"%", "%"+q+"%").
		Group("url")
	result := r.db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery)
	result.Count(&num)
	err := result.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&list).Error
	if err != nil {
		return []models.TheMovie{}, 0, err
	}
	return list, int(num), nil
}
