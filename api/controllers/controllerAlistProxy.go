package controllers

import (
	"io"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/plugins/alist"
)

func AlistProxy(c *gin.Context) {
	galleryUid := c.Param("gallery_uid")
	filePath := c.Param("path")

	if galleryUid == "" || filePath == "" {
		c.String(http.StatusBadRequest, "参数错误")
		return
	}

	filePath = filePath[1:]

	db := database.NewDb()
	gallery := models.Gallery{}
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", galleryUid).First(&gallery).Error
	if err != nil {
		c.String(http.StatusNotFound, "媒体库不存在")
		return
	}

	if gallery.AlistHost == "" {
		c.String(http.StatusBadRequest, "未配置 Alist 地址")
		return
	}

	fsData, err := alist.AlistFsGet(gallery, filePath)
	if err != nil {
		c.String(http.StatusInternalServerError, "获取文件链接失败: "+err.Error())
		return
	}

	if fsData.IsDir {
		c.String(http.StatusBadRequest, "不能代理目录")
		return
	}

	rawUrl := fsData.RawUrl
	if rawUrl == "" {
		c.String(http.StatusInternalServerError, "未获取到文件直链")
		return
	}

	proxyReq, err := http.NewRequest("GET", rawUrl, nil)
	if err != nil {
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
		c.String(http.StatusBadGateway, "代理请求失败: "+err.Error())
		return
	}
	defer resp.Body.Close()

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
