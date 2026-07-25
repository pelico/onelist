package crud

import (
	"errors"

	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/channels"
	"github.com/msterzhang/onelist/config"

	"gorm.io/gorm"
)

// RepositoryPlayedsCRUD is the struct for the Played CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryPlayedsCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.Played]
}

// NewRepositoryPlayedsCRUD returns a new repository with DB connection
func NewRepositoryPlayedsCRUD(db *gorm.DB) *RepositoryPlayedsCRUD {
	return &RepositoryPlayedsCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.Played](db, "played"),
	}
}

// ReNew played by the Played
func (r *RepositoryPlayedsCRUD) ReNewByPlayed(played models.Played) (int64, error) {
	var rs *gorm.DB
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		playedDb := models.Played{}
		err := r.db.Model(&models.Played{}).Where("user_id = ? AND data_id = ? AND data_type = ?", played.UserId, played.DataId, played.DataType).Take(&playedDb).Error
		if !errors.Is(err, gorm.ErrRecordNotFound) && playedDb.Id != 0 {
			rs = r.db.Model(&models.Played{}).Where("user_id = ? AND data_id = ? AND data_type = ?", played.UserId, played.DataId, played.DataType).Delete(&models.Played{})
		} else {
			rs = r.db.Model(&models.Played{}).Create(&played)
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

func (r *RepositoryPlayedsCRUD) FindAllByUser(played models.Played, page int, size int) ([]models.Played, int, error) {
	var err error
	var num int64
	playeds := []models.Played{}
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		result := r.db.Debug().Model(&models.Played{}).Where("user_id = ? AND data_type = ?", played.UserId, played.DataType).Find(&playeds)
		result.Count(&num)
		if config.DBDRIVER == "sqlite" {
			err = result.Limit(size).Offset((page - 1) * size).Order("datetime(updated_at) desc").Scan(&playeds).Error
		} else {
			err = result.Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&playeds).Error
		}
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)
	if channels.OK(done) {
		return playeds, int(num), nil
	}
	return nil, 0, err
}
