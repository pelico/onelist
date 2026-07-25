package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryTheSeasonsCRUD is the struct for the TheSeason CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryTheSeasonsCRUD struct {
	*GenericCRUD[models.TheSeason]
}

// NewRepositoryTheSeasonsCRUD returns a new repository with DB connection
func NewRepositoryTheSeasonsCRUD(db *gorm.DB) *RepositoryTheSeasonsCRUD {
	return &RepositoryTheSeasonsCRUD{
		GenericCRUD: NewGenericCRUD[models.TheSeason](db, "theseason"),
	}
}
