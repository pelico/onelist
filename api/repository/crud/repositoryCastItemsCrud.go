package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryCastItemsCRUD is the struct for the CastItem CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryCastItemsCRUD struct {
	*GenericCRUD[models.CastItem]
}

// NewRepositoryCastItemsCRUD returns a new repository with DB connection
func NewRepositoryCastItemsCRUD(db *gorm.DB) *RepositoryCastItemsCRUD {
	return &RepositoryCastItemsCRUD{
		GenericCRUD: NewGenericCRUD[models.CastItem](db, "castitem"),
	}
}
