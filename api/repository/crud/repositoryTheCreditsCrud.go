package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryTheCreditsCRUD is the struct for the TheCredit CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryTheCreditsCRUD struct {
	*GenericCRUD[models.TheCredit]
}

// NewRepositoryTheCreditsCRUD returns a new repository with DB connection
func NewRepositoryTheCreditsCRUD(db *gorm.DB) *RepositoryTheCreditsCRUD {
	return &RepositoryTheCreditsCRUD{
		GenericCRUD: NewGenericCRUD[models.TheCredit](db, "thecredit"),
	}
}
