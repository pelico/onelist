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
	var err error
	var num int64
	theTvs := []models.TheTv{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		subQuery := r.db.Model(&models.TheTv{}).Select("MIN(id)").Where("gallery_uid = ?", galleryUid).Group("name")
		result := r.db.Model(&models.TheTv{}).Where("id IN (?)", subQuery)
		result.Count(&num)
		if mode == "release_date" {
			mode = "last_air_date"
		}
		orderSql := fmt.Sprintf("%s %s", mode, order)
		if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
			orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
		}
		err = result.Order(orderSql).Limit(size).Offset((page - 1) * size).Scan(&theTvs).Error
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return theTvs, int(num), nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []models.TheTv{}, 0, errors.New("theTvs Not Found")
	}
	return []models.TheTv{}, 0, err
}

// FindByGalleryId thetv from the DB
func (r *RepositoryTheTvsCRUD) FindByGalleryId(id string, page int, size int) ([]models.TheTv, int, error) {
	var err error
	var num int64
	thetvs := []models.TheTv{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Model(&models.TheTv{}).Where("gallery_uid = ?", id)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&thetvs).Error
		} else {
			err = result.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&thetvs).Error
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
