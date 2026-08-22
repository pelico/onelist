package crud

import (
	"errors"
	"fmt"

	"gorm.io/gorm"
)

// GenericCRUD provides the standard Save/Update/Delete/Find/Search operations
// for any model type T. It replaces the near-identical boilerplate that was
// duplicated across every per-model repository implementation.
//
// The per-model Repository*CRUD structs embed *GenericCRUD[T] so the standard
// methods are promoted automatically and still satisfy the existing
// repository interfaces - no controller changes required.
//
// Behaviour is preserved exactly: same query shapes, same ordering, same
// "Not Found" error messages. The pointless goroutine+channel wrapper from the
// originals is dropped because channels.OK(done) is purely synchronous.
type GenericCRUD[T any] struct {
	db   *gorm.DB
	name string // singular display name, e.g. "genre" -> "genre Not Found" / "genres Not Found"
}

// NewGenericCRUD returns a GenericCRUD bound to db.
// name is the singular lower-cased name used to build "Not Found" messages.
func NewGenericCRUD[T any](db *gorm.DB, name string) *GenericCRUD[T] {
	return &GenericCRUD[T]{db: db, name: name}
}

// Save creates a new record and returns it.
func (g *GenericCRUD[T]) Save(model T) (T, error) {
	var zero T
	err := g.db.Model(&zero).Create(&model).Error
	if err != nil {
		return zero, err
	}
	return model, nil
}

// UpdateByID updates the record identified by id.
func (g *GenericCRUD[T]) UpdateByID(id string, model T) (int64, error) {
	var zero T
	rs := g.db.Model(&zero).Where("id = ?", id).Select("*").Updates(&model)
	if rs.Error != nil {
		return 0, rs.Error
	}
	return rs.RowsAffected, nil
}

// DeleteByID removes the record identified by id.
func (g *GenericCRUD[T]) DeleteByID(id string) (int64, error) {
	var zero T
	rs := g.db.Model(&zero).Where("id = ?", id).Delete(&zero)
	if rs.Error != nil {
		return 0, rs.Error
	}
	return rs.RowsAffected, nil
}

// FindAll returns a page of records ordered by descending ID.
func (g *GenericCRUD[T]) FindAll(page int, size int) ([]T, int, error) {
	var zero T
	var num int64
	list := []T{}
	// 使用独立的查询实例，避免 Count 污染 Find 的 Statement
	g.db.Model(&zero).Count(&num)
	err := g.db.Model(&zero).Limit(size).Offset((page - 1) * size).Order("-ID").Find(&list).Error
	if err != nil {
		return nil, 0, err
	}
	return list, int(num), nil
}

// FindByID returns the record identified by id, or a friendly "Not Found" error.
func (g *GenericCRUD[T]) FindByID(id string) (T, error) {
	var zero T
	var model T
	err := g.db.Model(&zero).Where("id = ?", id).Take(&model).Error
	if err == nil {
		return model, nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return zero, fmt.Errorf("%s Not Found", g.name)
	}
	return zero, err
}

// Search returns records whose name matches q, ordered by descending updated_at.
func (g *GenericCRUD[T]) Search(q string, page int, size int) ([]T, int, error) {
	var zero T
	var num int64
	list := []T{}
	// 使用独立的查询实例，避免 Count 污染 Scan 的 Statement
	query := g.db.Model(&zero).Where("name LIKE ?", "%"+q+"%")
	query.Count(&num)
	err := g.db.Model(&zero).Where("name LIKE ?", "%"+q+"%").Limit(size).Offset((page - 1) * size).Order("-updated_at").Scan(&list).Error
	if err == nil {
		return list, int(num), nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return []T{}, 0, fmt.Errorf("%ss Not Found", g.name)
	}
	return []T{}, 0, err
}
