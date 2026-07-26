package controllers

import (
	"io"
	"net/http"
	"net/url"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/plugins/alist"
)

func AlistProxy(c *gin.Context) {
	galleryUid := c.Param("gallery_uid")
	filePath := c.Param("path")

	if galleryUid == "" || filePath == "" {
		c.String(http.StatusBadRequest, "参数错误")
		return
	}

	// URL 解码：浏览器请求含中文/特殊字符的路径时会自动编码
	if decoded, err := url.QueryUnescape(filePath); err == nil {
		filePath = decoded
	}

	// data.value.url 存储时带有 Alist 下载路由前缀 /d，
	// 而 Alist /api/fs/get 需要的是不含 /d 的逻辑路径（如 /电影/xxx.mp4）
	filePath = strings.TrimPrefix(filePath, "/d")
	if !strings.HasPrefix(filePath, "/") {
		filePath = "/" + filePath
	}

	logger.Info("play", "播放请求", "媒体库: "+galleryUid+", 路径: "+filePath)

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
		logger.Warn("play", "获取文件信息失败", "路径: "+filePath+", 错误: "+err.Error())
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

	logger.Info("play", "文件直链获取成功", "路径: "+filePath+", 文件名: "+fsData.Name)

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
	ifNoneMatch := c.GetHeader("If-None-Match")
	if ifNoneMatch != "" {
		proxyReq.Header.Set("If-None-Match", ifNoneMatch)
	}
	ifModifiedSince := c.GetHeader("If-Modified-Since")
	if ifModifiedSince != "" {
		proxyReq.Header.Set("If-Modified-Since", ifModifiedSince)
	}

	client := &http.Client{
		Timeout: 0,
		Transport: &http.Transport{
			DisableCompression: true,
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
