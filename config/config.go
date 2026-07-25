package config

import (
	"fmt"
	"os"
	"strconv"

	"github.com/joho/godotenv"
	"github.com/msterzhang/onelist/api/models"
	"gorm.io/gorm"
)

var (
	EnvFile               = "config.env"
	PORT                  = 0
	Title                 = ""
	FaviconicoUrl         = ""
	SECRETKEY             = []byte{}
	DBDRIVER              = ""
	DBURL                 = ""
	DBDATAURL             = ""
	DbName                = ""
	KeyDb                 = ""
	UserEmail             = ""
	UserPassword          = ""
	DownLoadImage         = ""
	DownLoadImageToMedia  = ""
	ImgUrl                = ""
	TheMovieDbApiUrl      = ""
	VideoTypes            = ""
	UA                    = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"
	IsDev                 = false
	LogRetentionDays      = ""
	Version               = "1.0.0"
	db                    *gorm.DB
)

// Load the server PORT
func Load() {
	var err error
	err = godotenv.Load(EnvFile)
	if err != nil {
		return
	}
	PORT, err = strconv.Atoi(os.Getenv("API_PORT"))
	if err != nil {
		PORT = 9000
	}
	Env := os.Getenv("Env")
	if Env == "Debug" {
		IsDev = true
	}
	Title = os.Getenv("Title")
	FaviconicoUrl = os.Getenv("FaviconicoUrl")
	LogRetentionDays = os.Getenv("LogRetentionDays")
	if Env == "Debug" {
		DBDATAURL = fmt.Sprintf("%s:%s@tcp(127.0.0.1:3306)/?charset=utf8mb4", os.Getenv("DB_USER"), os.Getenv("DB_PASSWORD_Debug"))
		DBURL = fmt.Sprintf("%s:%s@/%s",
			os.Getenv("DB_USER"),
			os.Getenv("DB_PASSWORD_Debug"),
			os.Getenv("DB_NAME"),
		) + "?charset=utf8mb4&parseTime=true&loc=Asia%2FShanghai"
	} else {
		DBDATAURL = fmt.Sprintf("%s:%s@/?charset=utf8mb4", os.Getenv("DB_USER"), os.Getenv("DB_PASSWORD_Release"))
		//当数据库为docker，注意替换：fmt.Sprintf("%s:%s@tcp(127.0.0.1:3306)
		DBURL = fmt.Sprintf("%s:%s@/%s",
			os.Getenv("DB_USER"),
			os.Getenv("DB_PASSWORD_Release"),
			os.Getenv("DB_NAME"),
		) + "?charset=utf8mb4&parseTime=true&loc=Asia%2FShanghai"
	}
	DBDRIVER = os.Getenv("DB_DRIVER")
	SECRETKEY = []byte(os.Getenv("API_SECRET"))
	DbName = os.Getenv("DbName")
	KeyDb = os.Getenv("KeyDb")
	UserEmail = os.Getenv("UserEmail")
	UserPassword = os.Getenv("UserPassword")
	DownLoadImage = os.Getenv("DownLoadImage")
	DownLoadImageToMedia = os.Getenv("DownLoadImageToMedia")
	ImgUrl = os.Getenv("ImgUrl")
	TheMovieDbApiUrl = os.Getenv("TheMovieDbApiUrl")
	if TheMovieDbApiUrl == "" {
		TheMovieDbApiUrl = "https://api.themoviedb.org/3"
	}
	VideoTypes = os.Getenv("VideoTypes")
}

// 获取配置
func GetConfig() models.Config {
	config := models.Config{
		Title:               Title,
		DownLoadImage:       DownLoadImage,
		DownLoadImageToMedia: DownLoadImageToMedia,
		ImgUrl:              ImgUrl,
		TheMovieDbApiUrl:    TheMovieDbApiUrl,
		KeyDb:               KeyDb,
		FaviconicoUrl:       FaviconicoUrl,
		VideoTypes:          VideoTypes,
		LogRetentionDays:    LogRetentionDays,
	}
	return config
}

// 设置配置
func SetConfig(config models.Config) {
	Title = config.Title
	DownLoadImage = config.DownLoadImage
	DownLoadImageToMedia = config.DownLoadImageToMedia
	ImgUrl = config.ImgUrl
	if config.TheMovieDbApiUrl != "" {
		TheMovieDbApiUrl = config.TheMovieDbApiUrl
	}
	KeyDb = config.KeyDb
	FaviconicoUrl = config.FaviconicoUrl
	VideoTypes = config.VideoTypes
	LogRetentionDays = config.LogRetentionDays
}

// 保存配置
func SaveConfig(config models.Config) (models.Config, error) {
	if db != nil {
		settings := map[string]string{
			"Title":                  config.Title,
			"DownLoadImage":          config.DownLoadImage,
			"DownLoadImageToMedia":   config.DownLoadImageToMedia,
			"ImgUrl":                 config.ImgUrl,
			"TheMovieDbApiUrl":       config.TheMovieDbApiUrl,
			"FaviconicoUrl":          config.FaviconicoUrl,
			"KeyDb":                  config.KeyDb,
			"VideoTypes":             config.VideoTypes,
			"LogRetentionDays":       config.LogRetentionDays,
		}
		for key, value := range settings {
			setting := models.Setting{}
			err := db.Where("`key` = ?", key).First(&setting).Error
			if err != nil {
				setting.Key = key
				setting.Value = value
				db.Create(&setting)
			} else {
				setting.Value = value
				db.Save(&setting)
			}
		}
	}
	SetConfig(config)
	return GetConfig(), nil
}

func SetDB(database *gorm.DB) {
	db = database
}

func LoadFromDB() {
	if db == nil {
		return
	}
	var settings []models.Setting
	db.Find(&settings)
	settingMap := make(map[string]string)
	for _, s := range settings {
		settingMap[s.Key] = s.Value
	}
	if v, ok := settingMap["Title"]; ok {
		Title = v
	}
	if v, ok := settingMap["DownLoadImage"]; ok {
		DownLoadImage = v
	}
	if v, ok := settingMap["DownLoadImageToMedia"]; ok {
		DownLoadImageToMedia = v
	}
	if v, ok := settingMap["ImgUrl"]; ok {
		ImgUrl = v
	}
	if v, ok := settingMap["TheMovieDbApiUrl"]; ok && v != "" {
		TheMovieDbApiUrl = v
	}
	if v, ok := settingMap["FaviconicoUrl"]; ok {
		FaviconicoUrl = v
	}
	if v, ok := settingMap["KeyDb"]; ok {
		KeyDb = v
	}
	if v, ok := settingMap["VideoTypes"]; ok {
		VideoTypes = v
	}
	if v, ok := settingMap["LogRetentionDays"]; ok {
		LogRetentionDays = v
	}
}
