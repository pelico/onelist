package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryEpisodesCRUD is the struct for the Episode CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryEpisodesCRUD struct {
	*GenericCRUD[models.Episode]
}

// NewRepositoryEpisodesCRUD returns a new repository with DB connection
func NewRepositoryEpisodesCRUD(db *gorm.DB) *RepositoryEpisodesCRUD {
	return &RepositoryEpisodesCRUD{
		GenericCRUD: NewGenericCRUD[models.Episode](db, "episode"),
	}
}
