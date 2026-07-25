package logger

import (
	"fmt"
	"log"
	"time"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/config"
)

func Info(module string, message string, detail ...string) {
	log.Printf("[INFO][%s] %s", module, message)
	saveLog("info", module, message, detail...)
}

func Warn(module string, message string, detail ...string) {
	log.Printf("[WARN][%s] %s", module, message)
	saveLog("warn", module, message, detail...)
}

func Error(module string, message string, detail ...string) {
	log.Printf("[ERROR][%s] %s", module, message)
	saveLog("error", module, message, detail...)
}

func Debug(module string, message string, detail ...string) {
	log.Printf("[DEBUG][%s] %s", module, message)
	if config.IsDev {
		saveLog("debug", module, message, detail...)
	}
}

func saveLog(level string, module string, message string, detail ...string) {
	detailStr := ""
	if len(detail) > 0 {
		detailStr = detail[0]
	}
	db := database.NewDb()
	logEntry := models.Log{
		Level:   level,
		Module:  module,
		Message: message,
		Detail:  detailStr,
	}
	db.Model(&models.Log{}).Create(&logEntry)
}

func CleanOldLogs(retentionDays int) (int64, error) {
	if retentionDays <= 0 {
		retentionDays = 7
	}
	cutoffTime := time.Now().AddDate(0, 0, -retentionDays)
	db := database.NewDb()
	
	result := db.Model(&models.Log{}).Where("created_at < ?", cutoffTime).Delete(&models.Log{})
	if result.Error != nil {
		return 0, result.Error
	}
	if result.RowsAffected > 0 {
		log.Printf("[INFO][system] 清理了 %d 条过期日志", result.RowsAffected)
	}
	
	errFileResult := db.Model(&models.ErrFile{}).Where("created_at < ?", cutoffTime).Delete(&models.ErrFile{})
	if errFileResult.Error != nil {
		return 0, errFileResult.Error
	}
	if errFileResult.RowsAffected > 0 {
		log.Printf("[INFO][system] 清理了 %d 条过期错误文件记录", errFileResult.RowsAffected)
	}
	
	return result.RowsAffected + errFileResult.RowsAffected, nil
}

func GetLogs(level string, module string, page int, pageSize int, keyword string) ([]models.Log, int64, error) {
	db := database.NewDb()
	var logs []models.Log
	var total int64

	query := db.Model(&models.Log{})

	if level != "" && level != "all" {
		query = query.Where("level = ?", level)
	}
	if module != "" && module != "all" {
		query = query.Where("module = ?", module)
	}
	if keyword != "" {
		query = query.Where("message LIKE ? OR detail LIKE ?", "%"+keyword+"%", "%"+keyword+"%")
	}

	query.Count(&total)

	if page <= 0 {
		page = 1
	}
	if pageSize <= 0 || pageSize > 100 {
		pageSize = 20
	}

	offset := (page - 1) * pageSize
	err := query.Order("created_at DESC").Offset(offset).Limit(pageSize).Find(&logs).Error
	if err != nil {
		return nil, 0, err
	}
	return logs, total, nil
}

func StartLogCleaner(retentionDays int) {
	go func() {
		for {
			CleanOldLogs(retentionDays)
			time.Sleep(24 * time.Hour)
		}
	}()
	log.Printf("[INFO][system] 日志清理任务已启动，保留天数: %d天", retentionDays)
}

func ParseRetentionDays(envValue string) int {
	if envValue == "" {
		return 7
	}
	days := 0
	fmt.Sscanf(envValue, "%d", &days)
	if days <= 0 {
		return 7
	}
	return days
}
