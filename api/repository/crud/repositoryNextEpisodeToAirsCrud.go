package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryNextEpisodeToAirsCRUD is the struct for the NextEpisodeToAir CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryNextEpisodeToAirsCRUD struct {
	*GenericCRUD[models.NextEpisodeToAir]
}

// NewRepositoryNextEpisodeToAirsCRUD returns a new repository with DB connection
func NewRepositoryNextEpisodeToAirsCRUD(db *gorm.DB) *RepositoryNextEpisodeToAirsCRUD {
	return &RepositoryNextEpisodeToAirsCRUD{
		GenericCRUD: NewGenericCRUD[models.NextEpisodeToAir](db, "nextepisodetoair"),
	}
}
