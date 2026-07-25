package crud

import (
	"errors"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

// RepositoryHeartsCRUD is the struct for the Heart CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryHeartsCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.Heart]
}

// NewRepositoryHeartsCRUD returns a new repository with DB connection
func NewRepositoryHeartsCRUD(db *gorm.DB) *RepositoryHeartsCRUD {
	return &RepositoryHeartsCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.Heart](db, "heart"),
	}
}

// ReNew heart by the Heart
func (r *RepositoryHeartsCRUD) ReNewHeartByHeart(heart models.Heart) (int64, error) {
	var rs *gorm.DB
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		heartDb := models.Heart{}
		err := r.db.Model(&models.Heart{}).Where("user_id = ? AND data_id = ? AND data_type = ?", heart.UserId, heart.DataId, heart.DataType).Take(&heartDb).Error
		if !errors.Is(err, gorm.ErrRecordNotFound) && heartDb.Id != 0 {
			rs = r.db.Model(&models.Heart{}).Where("user_id = ? AND data_id = ? AND data_type = ?", heart.UserId, heart.DataId, heart.DataType).Delete(&models.Heart{})
		} else {
			rs = r.db.Model(&models.Heart{}).Create(&heart)
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

func (r *RepositoryHeartsCRUD) FindAllByUser(heart models.Heart, page int, size int) ([]models.Heart, int, error) {
	var err error
	var num int64
	hearts := []models.Heart{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Model(&models.Heart{}).Where("user_id = ? AND data_type = ?", heart.UserId, heart.DataType).Find(&hearts)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Order("datetime(updated_at) desc").Limit(size).Offset((page - 1) * size).Scan(&hearts).Error
		} else {
			err = result.Order("-updated_at").Limit(size).Offset((page - 1) * size).Scan(&hearts).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return hearts, int(num), nil
	}
	return nil, 0, err
}
