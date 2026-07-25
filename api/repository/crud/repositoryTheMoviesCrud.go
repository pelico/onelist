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
	var err error
	var num int64
	themovies := []models.TheMovie{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		subQuery := r.db.Model(&models.TheMovie{}).Select("MIN(id)").Where("gallery_uid = ?", galleryUid).Group("url")
		result := r.db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery)
		result.Count(&num)
		orderSql := fmt.Sprintf("%s %s", mode, order)
		if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
			orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
		}
		err = result.Order(orderSql).Limit(size).Offset((page - 1) * size).Scan(&themovies).Error
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

// FindByGalleryId themovies from the DB
func (r *RepositoryTheMoviesCRUD) FindByGalleryId(id string, page int, size int) ([]models.TheMovie, int, error) {
	var err error
	var num int64
	themovies := []models.TheMovie{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		subQuery := r.db.Model(&models.TheMovie{}).Select("MIN(id)").Where("gallery_uid = ?", id).Group("url")
		result := r.db.Model(&models.TheMovie{}).Where("id IN (?)", subQuery)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&themovies).Error
		} else {
			err = result.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&themovies).Error
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
