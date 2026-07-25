package crud

import (
	"errors"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

// RepositoryGallerysCRUD is the struct for the Gallery CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryGallerysCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.Gallery]
}

// NewRepositoryGallerysCRUD returns a new repository with DB connection
func NewRepositoryGallerysCRUD(db *gorm.DB) *RepositoryGallerysCRUD {
	return &RepositoryGallerysCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.Gallery](db, "gallery"),
	}
}

// FindAllByAdmin returns all the gallerys from the DB
func (r *RepositoryGallerysCRUD) FindAllByAdmin(page int, size int) ([]models.Gallery, int, error) {
	var err error
	var num int64
	gallerys := []models.Gallery{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Model(&models.Gallery{}).Find(&gallerys)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&gallerys).Error
		} else {
			err = result.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&gallerys).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return gallerys, int(num), nil
	}
	return nil, 0, err
}

// FindByUID return gallery from the DB
func (r *RepositoryGallerysCRUD) FindByUID(id string) (models.Gallery, error) {
	var err error
	gallery := models.Gallery{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		err = r.db.Model(&models.Gallery{}).Where("gallery_uid = ?", id).Take(&gallery).Error
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return gallery, nil
	}

	if errors.Is(err, gorm.ErrRecordNotFound) {
		return models.Gallery{}, errors.New("gallery Not Found")
	}
	return models.Gallery{}, err
}
