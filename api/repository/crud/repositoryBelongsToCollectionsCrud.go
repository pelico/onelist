package crud

import (
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

// RepositoryBelongsToCollectionsCRUD is the struct for the BelongsToCollection CRUD.
// Standard CRUD operations are provided by the embedded GenericCRUD.
type RepositoryBelongsToCollectionsCRUD struct {
	*GenericCRUD[models.BelongsToCollection]
}

// NewRepositoryBelongsToCollectionsCRUD returns a new repository with DB connection
func NewRepositoryBelongsToCollectionsCRUD(db *gorm.DB) *RepositoryBelongsToCollectionsCRUD {
	return &RepositoryBelongsToCollectionsCRUD{
		GenericCRUD: NewGenericCRUD[models.BelongsToCollection](db, "belongstocollection"),
	}
}
