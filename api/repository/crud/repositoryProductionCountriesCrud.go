package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryProductionCountriesCRUD is the struct for the ProductionCountrie CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryProductionCountriesCRUD struct {
	*GenericCRUD[models.ProductionCountrie]
}

// NewRepositoryProductionCountriesCRUD returns a new repository with DB connection
func NewRepositoryProductionCountriesCRUD(db *gorm.DB) *RepositoryProductionCountriesCRUD {
	return &RepositoryProductionCountriesCRUD{
		GenericCRUD: NewGenericCRUD[models.ProductionCountrie](db, "productioncountrie"),
	}
}
