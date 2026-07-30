package models

type Config struct {
	Title                string `json:"title"`
	DownLoadImage        string `json:"download_image"`
	DownLoadImageToMedia string `json:"download_image_to_media"`
	ImgUrl               string `json:"img_url"`
	TheMovieDbApiUrl     string `json:"themoviedb_api_url"`
	KeyDb                string `json:"key_db"`
	FaviconicoUrl        string `json:"faviconico_url"`
	VideoTypes           string `json:"video_types"`
	LogRetentionDays     string `json:"log_retention_days"`
	CustomDefaultImage   string `json:"custom_default_image"`
	// 护眼屏保
	ScreensaverEnabled      string `json:"screensaver_enabled"`       // 是/否，非管理员始终为"是"
	ScreensaverPlayDuration string `json:"screensaver_play_duration"` // 连续播放多少秒后触发屏保
	ScreensaverDuration     string `json:"screensaver_duration"`      // 屏保展示多少秒
	ScreensaverDailyLimit   string `json:"screensaver_daily_limit"`   // 每日最大播放秒数
}
