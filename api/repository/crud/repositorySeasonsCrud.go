package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositorySeasonsCRUD is the struct for the Season CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositorySeasonsCRUD struct {
	*GenericCRUD[models.Season]
}

// NewRepositorySeasonsCRUD returns a new repository with DB connection
func NewRepositorySeasonsCRUD(db *gorm.DB) *RepositorySeasonsCRUD {
	return &RepositorySeasonsCRUD{
		GenericCRUD: NewGenericCRUD[models.Season](db, "season"),
	}
}
