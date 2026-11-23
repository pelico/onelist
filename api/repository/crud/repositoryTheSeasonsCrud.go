package crud

import (
	"errors"
	"fmt"

	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryTheSeasonsCRUD is the struct for the TheSeason CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryTheSeasonsCRUD struct {
	db *gorm.DB
	*GenericCRUD[models.TheSeason]
}

// NewRepositoryTheSeasonsCRUD returns a new repository with DB connection
func NewRepositoryTheSeasonsCRUD(db *gorm.DB) *RepositoryTheSeasonsCRUD {
	return &RepositoryTheSeasonsCRUD{
		db:          db,
		GenericCRUD: NewGenericCRUD[models.TheSeason](db, "theseason"),
	}
}

// FindByID 按 id 查询季并加载分集列表。泛型默认实现不 Preload，episodes 恒为 null。
func (r *RepositoryTheSeasonsCRUD) FindByID(id string) (models.TheSeason, error) {
	theseason := models.TheSeason{}
	err := r.db.Preload("Episodes").Where("id = ?", id).Take(&theseason).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return models.TheSeason{}, fmt.Errorf("%s Not Found", r.GenericCRUD.name)
		}
		return models.TheSeason{}, err
	}
	return theseason, nil
}
