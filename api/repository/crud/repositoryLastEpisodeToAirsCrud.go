package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryLastEpisodeToAirsCRUD is the struct for the LastEpisodeToAir CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryLastEpisodeToAirsCRUD struct {
	*GenericCRUD[models.LastEpisodeToAir]
}

// NewRepositoryLastEpisodeToAirsCRUD returns a new repository with DB connection
func NewRepositoryLastEpisodeToAirsCRUD(db *gorm.DB) *RepositoryLastEpisodeToAirsCRUD {
	return &RepositoryLastEpisodeToAirsCRUD{
		GenericCRUD: NewGenericCRUD[models.LastEpisodeToAir](db, "lastepisodetoair"),
	}
}
