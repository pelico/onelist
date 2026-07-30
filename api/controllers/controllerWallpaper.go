package controllers

import (
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
)

// WallpaperFile 屏保素材文件信息
type WallpaperFile struct {
	Name string `json:"name"` // 文件名
	Type string `json:"type"` // video / image / html
	URL  string `json:"url"`  // 访问地址
}

// ListWallpaper 列出 wallpaper 目录下的所有素材文件
func ListWallpaper(c *gin.Context) {
	wallpaperDir := "wallpaper"
	// 确保目录存在
	if _, err := os.Stat(wallpaperDir); os.IsNotExist(err) {
		os.MkdirAll(wallpaperDir, 0755)
		c.JSON(200, gin.H{"code": 200, "msg": "获取成功!", "data": []WallpaperFile{}})
		return
	}

	entries, err := os.ReadDir(wallpaperDir)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "读取目录失败!", "data": []WallpaperFile{}})
		return
	}

	videoExts := map[string]bool{".mp4": true, ".webm": true, ".mov": true, ".avi": true, ".mkv": true}
	imageExts := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".webp": true, ".bmp": true, ".gif": true}
	htmlExts := map[string]bool{".html": true, ".htm": true}

	var files []WallpaperFile
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		ext := strings.ToLower(filepath.Ext(name))
		fileType := ""
		switch {
		case videoExts[ext]:
			fileType = "video"
		case imageExts[ext]:
			fileType = "image"
		case htmlExts[ext]:
			fileType = "html"
		default:
			continue // 跳过不支持的文件类型
		}
		files = append(files, WallpaperFile{
			Name: name,
			Type: fileType,
			URL:  "/v1/api/wallpaper/file/" + url.PathEscape(name),
		})
	}

	if files == nil {
		files = []WallpaperFile{}
	}

	c.JSON(200, gin.H{"code": 200, "msg": "获取成功!", "data": files})
}

// ServeWallpaper 提供屏保素材文件的访问
func ServeWallpaper(c *gin.Context) {
	fileName := c.Param("path")
	if fileName == "" {
		c.String(http.StatusBadRequest, "文件不存在")
		return
	}

	// URL 解码
	fileName, err := url.QueryUnescape(fileName)
	if err != nil {
		fileName = c.Param("path")
	}
	// 去掉开头的 /
	fileName = strings.TrimPrefix(fileName, "/")

	// 安全检查：防止路径穿越
	if strings.Contains(fileName, "..") || strings.Contains(fileName, "\\") {
		c.String(http.StatusBadRequest, "非法文件名")
		return
	}

	filePath := filepath.Join("wallpaper", fileName)
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		c.String(http.StatusBadRequest, "文件不存在")
		return
	}

	// 根据文件类型设置 Content-Type
	ext := strings.ToLower(filepath.Ext(fileName))
	switch ext {
	case ".mp4":
		c.Header("Content-Type", "video/mp4")
	case ".webm":
		c.Header("Content-Type", "video/webm")
	case ".mov":
		c.Header("Content-Type", "video/quicktime")
	case ".avi":
		c.Header("Content-Type", "video/x-msvideo")
	case ".mkv":
		c.Header("Content-Type", "video/x-matroska")
	case ".jpg", ".jpeg":
		c.Header("Content-Type", "image/jpeg")
	case ".png":
		c.Header("Content-Type", "image/png")
	case ".webp":
		c.Header("Content-Type", "image/webp")
	case ".bmp":
		c.Header("Content-Type", "image/bmp")
	case ".gif":
		c.Header("Content-Type", "image/gif")
	case ".html", ".htm":
		c.Header("Content-Type", "text/html; charset=utf-8")
	}

	c.File(filePath)
}
