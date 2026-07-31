package config

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
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
	CustomDefaultImage    = "是"
	// 护眼屏保
	ScreensaverEnabled      = "是"
	ScreensaverPlayDuration = "3600"  // 默认1小时
	ScreensaverDuration     = "180"   // 默认3分钟
	ScreensaverDailyLimit   = "7200"  // 默认2小时
	// Webhook 消息推送
	WebhookEnabled          = "否"
	WebhookToken            = ""
	// 消息发送者名称
	SenderName              = "管理员"
	Version                 = "v1.0 @2026 Optimized by wanchuan"
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
	if len(SECRETKEY) == 0 {
		// API_SECRET 未配置时自动生成随机密钥，避免空密钥签名
		key := make([]byte, 32)
		if _, err := rand.Read(key); err == nil {
			SECRETKEY = []byte(hex.EncodeToString(key))
			log.Println("[WARN] API_SECRET 未配置，已使用自动生成的随机密钥。请在 config.env 中设置 API_SECRET。")
		}
	}
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
	if v := os.Getenv("CustomDefaultImage"); v != "" {
		CustomDefaultImage = v
	}
	// 护眼屏保
	if v := os.Getenv("ScreensaverEnabled"); v != "" {
		ScreensaverEnabled = v
	}
	if v := os.Getenv("ScreensaverPlayDuration"); v != "" {
		ScreensaverPlayDuration = v
	}
	if v := os.Getenv("ScreensaverDuration"); v != "" {
		ScreensaverDuration = v
	}
	if v := os.Getenv("ScreensaverDailyLimit"); v != "" {
		ScreensaverDailyLimit = v
	}
	// Webhook
	if v := os.Getenv("WebhookEnabled"); v != "" {
		WebhookEnabled = v
	}
	if v := os.Getenv("WebhookToken"); v != "" {
		WebhookToken = v
	}
}

// 获取配置
func GetConfig() models.Config {
	config := models.Config{
		Title:                 Title,
		DownLoadImage:         DownLoadImage,
		DownLoadImageToMedia:  DownLoadImageToMedia,
		ImgUrl:                ImgUrl,
		TheMovieDbApiUrl:      TheMovieDbApiUrl,
		KeyDb:                 KeyDb,
		FaviconicoUrl:         FaviconicoUrl,
		VideoTypes:            VideoTypes,
		LogRetentionDays:      LogRetentionDays,
		CustomDefaultImage:    CustomDefaultImage,
		ScreensaverEnabled:    ScreensaverEnabled,
		ScreensaverPlayDuration: ScreensaverPlayDuration,
		ScreensaverDuration:   ScreensaverDuration,
		ScreensaverDailyLimit: ScreensaverDailyLimit,
		WebhookEnabled:       WebhookEnabled,
		WebhookToken:         WebhookToken,
		SenderName:           SenderName,
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
	CustomDefaultImage = config.CustomDefaultImage
	// 护眼屏保
	if config.ScreensaverEnabled != "" {
		ScreensaverEnabled = config.ScreensaverEnabled
	}
	if config.ScreensaverPlayDuration != "" {
		ScreensaverPlayDuration = config.ScreensaverPlayDuration
	}
	if config.ScreensaverDuration != "" {
		ScreensaverDuration = config.ScreensaverDuration
	}
	if config.ScreensaverDailyLimit != "" {
		ScreensaverDailyLimit = config.ScreensaverDailyLimit
	}
	// Webhook
	if config.WebhookEnabled != "" {
		WebhookEnabled = config.WebhookEnabled
	}
	if config.WebhookToken != "" {
		WebhookToken = config.WebhookToken
	}
	// 消息发送者名称（允许设为空字符串，表示使用默认"管理员"）
	SenderName = config.SenderName
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
			"CustomDefaultImage":     config.CustomDefaultImage,
			"ScreensaverEnabled":     config.ScreensaverEnabled,
			"ScreensaverPlayDuration": config.ScreensaverPlayDuration,
			"ScreensaverDuration":    config.ScreensaverDuration,
			"ScreensaverDailyLimit":  config.ScreensaverDailyLimit,
			"WebhookEnabled":       config.WebhookEnabled,
			"WebhookToken":         config.WebhookToken,
			"SenderName":           config.SenderName,
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
	if v, ok := settingMap["CustomDefaultImage"]; ok {
		CustomDefaultImage = v
	}
	// 护眼屏保
	if v, ok := settingMap["ScreensaverEnabled"]; ok {
		ScreensaverEnabled = v
	}
	if v, ok := settingMap["ScreensaverPlayDuration"]; ok {
		ScreensaverPlayDuration = v
	}
	if v, ok := settingMap["ScreensaverDuration"]; ok {
		ScreensaverDuration = v
	}
	if v, ok := settingMap["ScreensaverDailyLimit"]; ok {
		ScreensaverDailyLimit = v
	}
	// Webhook
	if v, ok := settingMap["WebhookEnabled"]; ok {
		WebhookEnabled = v
	}
	if v, ok := settingMap["WebhookToken"]; ok {
		WebhookToken = v
	}
	// 消息发送者名称
	if v, ok := settingMap["SenderName"]; ok && v != "" {
		SenderName = v
	}
}
