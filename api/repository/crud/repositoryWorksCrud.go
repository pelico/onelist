package crud

import (
	"errors"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

// RepositoryWorksCRUD is the struct for the Work CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryWorksCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.Work]
}

// NewRepositoryWorksCRUD returns a new repository with DB connection
func NewRepositoryWorksCRUD(db *gorm.DB) *RepositoryWorksCRUD {
	return &RepositoryWorksCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.Work](db, "work"),
	}
}

// Search work from the DB
func (r *RepositoryWorksCRUD) GetWorkListByGalleryId(id string, page int, size int) ([]models.Work, int, error) {
	var err error
	var num int64
	works := []models.Work{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Model(&models.Work{}).Where("gallery_uid = ?", id)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&works).Error
		} else {
			err = result.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&works).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return works, int(num), nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []models.Work{}, 0, errors.New("works Not Found")
	}
	return []models.Work{}, 0, err
}
