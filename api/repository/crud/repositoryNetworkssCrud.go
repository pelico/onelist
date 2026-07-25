package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryNetworkssCRUD is the struct for the Networks CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryNetworkssCRUD struct {
	*GenericCRUD[models.Networks]
}

// NewRepositoryNetworkssCRUD returns a new repository with DB connection
func NewRepositoryNetworkssCRUD(db *gorm.DB) *RepositoryNetworkssCRUD {
	return &RepositoryNetworkssCRUD{
		GenericCRUD: NewGenericCRUD[models.Networks](db, "networks"),
	}
}
