package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryUsersCRUD is the struct for the User CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryUsersCRUD struct {
	*GenericCRUD[models.User]
}

// NewRepositoryUsersCRUD returns a new repository with DB connection
func NewRepositoryUsersCRUD(db *gorm.DB) *RepositoryUsersCRUD {
	return &RepositoryUsersCRUD{
		GenericCRUD: NewGenericCRUD[models.User](db, "user"),
	}
}
