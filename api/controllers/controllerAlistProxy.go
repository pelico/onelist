package controllers

import (
	"io"
	"net/http"
	"path/filepath"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/plugins/alist"
)

// isSubtitleFile 判断是否为字幕文件（前端播放时会主动探测字幕，不存在属正常情况）
func isSubtitleFile(path string) bool {
	ext := strings.ToLower(filepath.Ext(path))
	switch ext {
	case ".srt", ".ass", ".ssa", ".vtt", ".sub", ".idx":
		return true
	}
	return false
}

func AlistProxy(c *gin.Context) {
	galleryUid := c.Param("gallery_uid")
	filePath := c.Param("path")

	if galleryUid == "" || filePath == "" {
		c.String(http.StatusBadRequest, "参数错误")
		return
	}

	// Gin 路由匹配时已对 c.Param("path") 做过一次 URL 解码，
	// 这里不需要再 url.QueryUnescape（重复解码会导致文件名含 % 字符时出错）

	// data.value.url 存储时带有 Alist 下载路由前缀 /d，
	// 而 Alist /api/fs/get 需要的是不含 /d 的逻辑路径（如 /电影/xxx.mp4）
	filePath = strings.TrimPrefix(filePath, "/d")
	if !strings.HasPrefix(filePath, "/") {
		filePath = "/" + filePath
	}

	logger.Debug("play", "播放请求", "媒体库: "+galleryUid+", 路径: "+filePath)

	db := database.NewDb()
	gallery := models.Gallery{}
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", galleryUid).First(&gallery).Error
	if err != nil {
		logger.Warn("play", "媒体库不存在", "UID: "+galleryUid)
		c.String(http.StatusNotFound, "媒体库不存在")
		return
	}

	if gallery.AlistHost == "" {
		logger.Warn("play", "Alist 地址未配置", "UID: "+galleryUid)
		c.String(http.StatusBadRequest, "未配置 Alist 地址")
		return
	}

	fsData, err := alist.AlistFsGet(gallery, filePath)
	if err != nil {
		if isSubtitleFile(filePath) {
			// 字幕文件不存在属正常情况（前端会主动探测多种字幕格式）
			logger.Debug("play", "字幕文件不存在，跳过", "路径: "+filePath)
		} else {
			logger.Warn("play", "获取文件信息失败", "路径: "+filePath+", 错误: "+err.Error())
		}
		// 错误响应禁止浏览器缓存，确保重试时能重新请求后端
		c.Header("Cache-Control", "no-store, no-cache, must-revalidate")
		c.String(http.StatusNotFound, "文件不存在: "+err.Error())
		return
	}

	if fsData.IsDir {
		c.String(http.StatusBadRequest, "不能代理目录")
		return
	}

	rawUrl := fsData.RawUrl
	if rawUrl == "" {
		logger.Warn("play", "文件直链为空", "路径: "+filePath)
		c.String(http.StatusInternalServerError, "未获取到文件直链")
		return
	}

	logger.Debug("play", "文件直链获取成功", "路径: "+filePath+", 文件名: "+fsData.Name)

	proxyReq, err := http.NewRequest("GET", rawUrl, nil)
	if err != nil {
		logger.Error("play", "创建代理请求失败", "URL: "+rawUrl+", 错误: "+err.Error())
		c.String(http.StatusInternalServerError, "创建请求失败: "+err.Error())
		return
	}

	rangeHeader := c.GetHeader("Range")
	if rangeHeader != "" {
		proxyReq.Header.Set("Range", rangeHeader)
	}
	// 不转发 If-None-Match / If-Modified-Since 给上游：
	// 这些是浏览器缓存验证头，代理应始终从上游获取最新内容，
	// 否则上游返回 304 时代理会透传给浏览器，但浏览器并无实际缓存内容，导致播放失败。

	client := &http.Client{
		// 不设整体 Timeout：媒体流式传输可能持续数小时
		Transport: &http.Transport{
			DisableCompression:      true,
			TLSHandshakeTimeout:     10 * time.Second,
			ResponseHeaderTimeout:   30 * time.Second,
			IdleConnTimeout:         120 * time.Second,
			MaxIdleConns:            20,
			MaxIdleConnsPerHost:     10,
		},
	}

	resp, err := client.Do(proxyReq)
	if err != nil {
		logger.Error("play", "代理请求失败", "URL: "+rawUrl+", 错误: "+err.Error())
		c.String(http.StatusBadGateway, "代理请求失败: "+err.Error())
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		logger.Warn("play", "上游返回错误", "URL: "+rawUrl+", 状态码: "+resp.Status)
	}

	for key, values := range resp.Header {
		lowKey := strings.ToLower(key)
		if lowKey == "content-length" || lowKey == "content-type" ||
			lowKey == "content-range" || lowKey == "accept-ranges" ||
			lowKey == "etag" || lowKey == "last-modified" ||
			lowKey == "cache-control" {
			for _, v := range values {
				c.Writer.Header().Add(key, v)
			}
		}
	}

	c.Status(resp.StatusCode)

	buf := make([]byte, 32*1024)
	_, _ = io.CopyBuffer(c.Writer, resp.Body, buf)
}
