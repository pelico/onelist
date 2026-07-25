package crud

import (
	"errors"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

// RepositoryStarsCRUD is the struct for the Star CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryStarsCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.Star]
}

// NewRepositoryStarsCRUD returns a new repository with DB connection
func NewRepositoryStarsCRUD(db *gorm.DB) *RepositoryStarsCRUD {
	return &RepositoryStarsCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.Star](db, "star"),
	}
}

// ReNew star by the Star
func (r *RepositoryStarsCRUD) ReNewStarByStar(star models.Star) (int64, error) {
	var rs *gorm.DB
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		starDb := models.Star{}
		err := r.db.Model(&models.Star{}).Where("user_id = ? AND data_id = ? AND data_type = ?", star.UserId, star.DataId, star.DataType).Take(&starDb).Error
		if !errors.Is(err, gorm.ErrRecordNotFound) && starDb.Id != 0 {
			rs = r.db.Model(&models.Star{}).Where("user_id = ? AND data_id = ? AND data_type = ?", star.UserId, star.DataId, star.DataType).Delete(&models.Star{})
		} else {
			rs = r.db.Model(&models.Star{}).Create(&star)
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		if rs.Error != nil {
			return 0, rs.Error
		}
		return rs.RowsAffected, nil
	}
	return 0, rs.Error
}

func (r *RepositoryStarsCRUD) FindAllByUser(star models.Star, page int, size int) ([]models.Star, int, error) {
	var err error
	var num int64
	stars := []models.Star{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Model(&models.Star{}).Where("user_id = ? AND data_type = ?", star.UserId, star.DataType).Find(&stars)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Order("datetime(updated_at) desc").Limit(size).Offset((page - 1) * size).Scan(&stars).Error
		} else {
			err = result.Order("-updated_at").Limit(size).Offset((page - 1) * size).Scan(&stars).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return stars, int(num), nil
	}
	return nil, 0, err
}
