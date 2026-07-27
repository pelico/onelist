package service

import (
	"errors"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"

	"gorm.io/gorm"
)

func TheTvService(theTv models.TheTv, userId string) models.TheTv {
	star := models.Star{}
	played := models.Played{}
	heart := models.Heart{}
	db := database.NewDb()
	err := db.Model(&models.Star{}).Where("user_id = ? AND data_id = ? AND data_type = ?", userId, theTv.ID, "tv").First(&star).Error
	if !errors.Is(err, gorm.ErrRecordNotFound) && star.Id != 0 {
		theTv.Star = true
	}
	err = db.Model(&models.Played{}).Where("user_id = ? AND data_id = ? AND data_type = ?", userId, theTv.ID, "tv").First(&played).Error
	if !errors.Is(err, gorm.ErrRecordNotFound) && played.Id != 0 {
		theTv.Played = true
	}
	err = db.Model(&models.Heart{}).Where("user_id = ? AND data_id = ? AND data_type = ?", userId, theTv.ID, "tv").First(&heart).Error
	if !errors.Is(err, gorm.ErrRecordNotFound) && heart.Id != 0 {
		theTv.Heart = true
	}
	return theTv
}

func TheTvsService(theTvs []models.TheTv, userId string) []models.TheTv {
	if len(theTvs) == 0 || userId == "" {
		return theTvs
	}

	db := database.NewDb()
	ids := make([]int, 0, len(theTvs))
	for _, tv := range theTvs {
		ids = append(ids, tv.ID)
	}

	starMap := make(map[int]bool)
	var stars []models.Star
	db.Model(&models.Star{}).Where("user_id = ? AND data_type = ? AND data_id IN (?)", userId, "tv", ids).Find(&stars)
	for _, s := range stars {
		starMap[s.DataId] = true
	}

	playedMap := make(map[int]bool)
	var playeds []models.Played
	db.Model(&models.Played{}).Where("user_id = ? AND data_type = ? AND data_id IN (?)", userId, "tv", ids).Find(&playeds)
	for _, p := range playeds {
		playedMap[p.DataId] = true
	}

	heartMap := make(map[int]bool)
	var hearts []models.Heart
	db.Model(&models.Heart{}).Where("user_id = ? AND data_type = ? AND data_id IN (?)", userId, "tv", ids).Find(&hearts)
	for _, h := range hearts {
		heartMap[h.DataId] = true
	}

	for i := range theTvs {
		theTvs[i].Star = starMap[theTvs[i].ID]
		theTvs[i].Played = playedMap[theTvs[i].ID]
		theTvs[i].Heart = heartMap[theTvs[i].ID]
	}

	return theTvs
}

func TheMovieService(theMovie models.TheMovie, userId string) models.TheMovie {
	star := models.Star{}
	played := models.Played{}
	heart := models.Heart{}
	db := database.NewDb()
	err := db.Model(&models.Star{}).Where("user_id = ? AND data_id = ? AND data_type = ?", userId, theMovie.ID, "movie").First(&star).Error
	if !errors.Is(err, gorm.ErrRecordNotFound) && star.Id != 0 {
		theMovie.Star = true
	}
	err = db.Model(&models.Played{}).Where("user_id = ? AND data_id = ? AND data_type = ?", userId, theMovie.ID, "movie").First(&played).Error
	if !errors.Is(err, gorm.ErrRecordNotFound) && played.Id != 0 {
		theMovie.Played = true
	}
	err = db.Model(&models.Heart{}).Where("user_id = ? AND data_id = ? AND data_type = ?", userId, theMovie.ID, "movie").First(&heart).Error
	if !errors.Is(err, gorm.ErrRecordNotFound) && heart.Id != 0 {
		theMovie.Heart = true
	}
	return theMovie
}

func TheMoviesService(theMovies []models.TheMovie, userId string) []models.TheMovie {
	if len(theMovies) == 0 || userId == "" {
		return theMovies
	}

	db := database.NewDb()
	ids := make([]int, 0, len(theMovies))
	for _, movie := range theMovies {
		ids = append(ids, movie.ID)
	}

	starMap := make(map[int]bool)
	var stars []models.Star
	db.Model(&models.Star{}).Where("user_id = ? AND data_type = ? AND data_id IN (?)", userId, "movie", ids).Find(&stars)
	for _, s := range stars {
		starMap[s.DataId] = true
	}

	playedMap := make(map[int]bool)
	var playeds []models.Played
	db.Model(&models.Played{}).Where("user_id = ? AND data_type = ? AND data_id IN (?)", userId, "movie", ids).Find(&playeds)
	for _, p := range playeds {
		playedMap[p.DataId] = true
	}

	heartMap := make(map[int]bool)
	var hearts []models.Heart
	db.Model(&models.Heart{}).Where("user_id = ? AND data_type = ? AND data_id IN (?)", userId, "movie", ids).Find(&hearts)
	for _, h := range hearts {
		heartMap[h.DataId] = true
	}

	for i := range theMovies {
		theMovies[i].Star = starMap[theMovies[i].ID]
		theMovies[i].Played = playedMap[theMovies[i].ID]
		theMovies[i].Heart = heartMap[theMovies[i].ID]
	}

	return theMovies
}
