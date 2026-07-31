package controllers

import (
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
)

// GameFile 游戏文件信息
type GameFile struct {
	Name string `json:"name"` // 显示名称（去掉 .html 后缀）
	File string `json:"file"` // 文件名（含 .html）
	URL  string `json:"url"`  // 访问地址
}

// ListGames 列出 games 目录下的所有 HTML 游戏
func ListGames(c *gin.Context) {
	gamesDir := "games"
	// 确保目录存在
	if _, err := os.Stat(gamesDir); os.IsNotExist(err) {
		os.MkdirAll(gamesDir, 0755)
		c.JSON(200, gin.H{"code": 200, "msg": "获取成功!", "data": []GameFile{}})
		return
	}

	entries, err := os.ReadDir(gamesDir)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "读取目录失败!", "data": []GameFile{}})
		return
	}

	var games []GameFile
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		ext := strings.ToLower(filepath.Ext(name))
		if ext != ".html" && ext != ".htm" {
			continue
		}
		// 显示名称：去掉 .html 后缀，用空格替换下划线和连字符
		displayName := strings.TrimSuffix(name, ext)
		displayName = strings.ReplaceAll(displayName, "_", " ")
		displayName = strings.ReplaceAll(displayName, "-", " ")

		games = append(games, GameFile{
			Name: displayName,
			File: name,
			URL:  "/v1/api/game/file/" + url.PathEscape(name),
		})
	}

	if games == nil {
		games = []GameFile{}
	}

	c.JSON(200, gin.H{"code": 200, "msg": "获取成功!", "data": games})
}

// ServeGame 提供游戏 HTML 文件的访问
func ServeGame(c *gin.Context) {
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

	filePath := filepath.Join("games", fileName)
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		c.String(http.StatusBadRequest, "文件不存在")
		return
	}

	c.Header("Content-Type", "text/html; charset=utf-8")
	c.Header("X-Frame-Options", "SAMEORIGIN")
	c.File(filePath)
}
