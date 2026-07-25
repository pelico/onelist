package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryThePersonsCRUD is the struct for the ThePerson CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryThePersonsCRUD struct {
	*GenericCRUD[models.ThePerson]
}

// NewRepositoryThePersonsCRUD returns a new repository with DB connection
func NewRepositoryThePersonsCRUD(db *gorm.DB) *RepositoryThePersonsCRUD {
	return &RepositoryThePersonsCRUD{
		GenericCRUD: NewGenericCRUD[models.ThePerson](db, "theperson"),
	}
}
