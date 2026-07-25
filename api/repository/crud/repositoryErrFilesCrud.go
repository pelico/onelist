package crud

import (
	"errors"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

// RepositoryErrFilesCRUD is the struct for the ErrFile CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryErrFilesCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.ErrFile]
}

// NewRepositoryErrFilesCRUD returns a new repository with DB connection
func NewRepositoryErrFilesCRUD(db *gorm.DB) *RepositoryErrFilesCRUD {
	return &RepositoryErrFilesCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.ErrFile](db, "errfile"),
	}
}

// GetErrFilesByWorkId errfile from the DB
func (r *RepositoryErrFilesCRUD) GetErrFilesByWorkId(id string, page int, size int) ([]models.ErrFile, int, error) {
	var err error
	var num int64
	errfiles := []models.ErrFile{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Model(&models.ErrFile{}).Where("work_id = ?", id)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&errfiles).Error
		} else {
			err = result.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&errfiles).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return errfiles, int(num), nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []models.ErrFile{}, 0, errors.New("errfiles Not Found")
	}
	return []models.ErrFile{}, 0, err
}
