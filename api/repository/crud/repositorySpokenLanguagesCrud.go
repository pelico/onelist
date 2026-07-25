package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositorySpokenLanguagesCRUD is the struct for the SpokenLanguage CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositorySpokenLanguagesCRUD struct {
	*GenericCRUD[models.SpokenLanguage]
}

// NewRepositorySpokenLanguagesCRUD returns a new repository with DB connection
func NewRepositorySpokenLanguagesCRUD(db *gorm.DB) *RepositorySpokenLanguagesCRUD {
	return &RepositorySpokenLanguagesCRUD{
		GenericCRUD: NewGenericCRUD[models.SpokenLanguage](db, "spokenlanguage"),
	}
}
