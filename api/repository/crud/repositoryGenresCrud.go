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

// RepositoryGenresCRUD is the struct for the Genre CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryGenresCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.Genre]
}

// NewRepositoryGenresCRUD returns a new repository with DB connection
func NewRepositoryGenresCRUD(db *gorm.DB) *RepositoryGenresCRUD {
	return &RepositoryGenresCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.Genre](db, "genre"),
	}
}

// FindById Filte themovies or thetvs
func (r *RepositoryGenresCRUD) FindByIdFilte(id string, galleryUid string, galleryType string, mode string, order string, page int, size int) (models.Genre, int, error) {
	var err error
	var num = 0
	genre := models.Genre{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		if galleryType == "movie" {
			orderSql := fmt.Sprintf("%s %s", mode, order)
			if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
				orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
			}
			err = r.db.Model(&models.Genre{}).Where("id = ?", id).Preload("TheMovies", func(db *gorm.DB) *gorm.DB {
				return db.Where("gallery_uid = ?", galleryUid).Order(orderSql).Limit(size).Offset((page - 1) * size)
			}).Take(&genre).Error
			if err != nil {
				ch <- false
				return
			}
			genreNum := models.Genre{}
			r.db.Model(&models.Genre{}).Where("id = ?", id).Preload("TheMovies", func(db *gorm.DB) *gorm.DB {
				return db.Where("gallery_uid = ?", galleryUid)
			}).Take(&genreNum)
			num = len(genreNum.TheMovies)
		} else if galleryType == "tv" {
			if mode == "release_date" {
				mode = "last_air_date"
			}
			orderSql := fmt.Sprintf("%s %s", mode, order)
			if config.DBDRIVER == "sqlite" && strings.Contains(mode, "_at") {
				orderSql = fmt.Sprintf("datetime(%s) %s", mode, order)
			}
			err = r.db.Model(&models.Genre{}).Where("id = ?", id).Preload("TheTvs", func(db *gorm.DB) *gorm.DB {
				return db.Where("gallery_uid = ?", galleryUid).Order(orderSql).Limit(size).Offset((page - 1) * size)
			}).Take(&genre).Error
			if err != nil {
				ch <- false
				return
			}
			genreNum := models.Genre{}
			r.db.Model(&models.Genre{}).Where("id = ?", id).Preload("TheTvs", func(db *gorm.DB) *gorm.DB {
				return db.Where("gallery_uid = ?", galleryUid)
			}).Take(&genreNum)
			num = len(genreNum.TheTvs)
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return genre, num, nil
	}

	if errors.Is(err, gorm.ErrRecordNotFound) {
		return models.Genre{}, num, errors.New("genre Not Found")
	}
	return models.Genre{}, num, err
}
