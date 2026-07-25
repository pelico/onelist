package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryProductionCompaniesCRUD is the struct for the ProductionCompanie CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryProductionCompaniesCRUD struct {
	*GenericCRUD[models.ProductionCompanie]
}

// NewRepositoryProductionCompaniesCRUD returns a new repository with DB connection
func NewRepositoryProductionCompaniesCRUD(db *gorm.DB) *RepositoryProductionCompaniesCRUD {
	return &RepositoryProductionCompaniesCRUD{
		GenericCRUD: NewGenericCRUD[models.ProductionCompanie](db, "productioncompanie"),
	}
}
