package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryCrewItemsCRUD is the struct for the CrewItem CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryCrewItemsCRUD struct {
	*GenericCRUD[models.CrewItem]
}

// NewRepositoryCrewItemsCRUD returns a new repository with DB connection
func NewRepositoryCrewItemsCRUD(db *gorm.DB) *RepositoryCrewItemsCRUD {
	return &RepositoryCrewItemsCRUD{
		GenericCRUD: NewGenericCRUD[models.CrewItem](db, "crewitem"),
	}
}
