package controllers

import (
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/dir"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/api/utils/tools"
	"github.com/msterzhang/onelist/plugins/alist"
)

// 图片文件服务
func ImgServer(c *gin.Context) {
	path := c.Param("path")
	filePath := "images" + path
	c.Writer.WriteHeader(200)
	b, err := os.ReadFile(filePath)
	if err != nil {
		c.Writer.WriteHeader(http.StatusNotFound)
		c.Writer.Flush()
		return
	}
	_, err = c.Writer.Write(b)
	if err != nil {
		c.Writer.WriteHeader(http.StatusNoContent)
		c.Writer.Flush()
		return
	}
	c.Writer.Header().Add("Content-Type", "image/*")
	c.Writer.Flush()
}

// 本地文件服务
func FileServer(c *gin.Context) {
	file := c.Param("path")
	if len(file) < 1 {
		c.String(http.StatusBadRequest, "文件不存在!")
		return
	}
	// URL 解码：浏览器请求含中文/特殊字符的路径时会自动编码
	file, err := url.QueryUnescape(file)
	if err != nil {
		// 解码失败时使用原始路径
		file = c.Param("path")
	}
	file = file[1:]
	if !dir.FileExists(file) {
		logger.Warn("play", "本地文件不存在", "路径: "+file)
		c.String(http.StatusBadRequest, "文件不存在!")
		return
	}
	fileName := filepath.Base(file)
	c.Header("Content-Disposition", fmt.Sprintf(`attachment; filename="%s"`, fileName))
	c.Header("Content-Type", "application/octet-stream")
	c.File(file)
}

func GalleryImgServer(c *gin.Context) {
	path := c.Param("path")
	filePath := "./images" + path
	c.Writer.WriteHeader(200)
	b, err := os.ReadFile(filePath)
	if err != nil {
		c.Writer.WriteHeader(http.StatusNotFound)
		c.Writer.Flush()
		return
	}
	_, err = c.Writer.Write(b)
	if err != nil {
		c.Writer.WriteHeader(http.StatusNoContent)
		c.Writer.Flush()
		return
	}
	c.Writer.Header().Add("Content-Type", "image/*")
	c.Writer.Flush()
}

func FileUpload(c *gin.Context) {
	file, err := c.FormFile("file")
	if err != nil {
		c.String(http.StatusBadRequest, "没有获得文件!")
		return
	}
	id := tools.RandStringRunes(16)
	dst := "./images/w355_and_h200_multi_faces/" + id + path.Ext(file.Filename)
	data := "/gallery/w355_and_h200_multi_faces/" + id + path.Ext(file.Filename)
	c.SaveUploadedFile(file, dst)
	c.JSON(200, gin.H{"code": 200, "msg": "上传成功!", "data": data})
}

// GetPlaylist 获取同目录下视频文件列表（按文件名排序），用于列表播放
func GetPlaylist(c *gin.Context) {
	galleryUid := c.Query("gallery_uid")
	fileUrl := c.Query("url")

	if galleryUid == "" || fileUrl == "" {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误", "data": []string{}})
		return
	}

	// URL 解码
	fileUrl, _ = url.QueryUnescape(fileUrl)

	db := database.NewDb()
	gallery := models.Gallery{}
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", galleryUid).First(&gallery).Error
	if err != nil {
		logger.Warn("play", "播放列表: 媒体库不存在", "UID: "+galleryUid)
		c.JSON(200, gin.H{"code": 201, "msg": "媒体库不存在", "data": []string{}})
		return
	}

	var files []string
	if gallery.IsAlist {
		files = getAlistPlaylist(gallery, fileUrl)
	} else {
		// 本地文件路径：去掉开头的 /
		filePath := strings.TrimPrefix(fileUrl, "/")
		parentDir := filepath.Dir(filePath)
		files = getLocalPlaylist(parentDir)
	}

	logger.Info("play", "播放列表加载", "媒体库: "+galleryUid+", 文件数: "+fmt.Sprintf("%d", len(files)))
	c.JSON(200, gin.H{"code": 200, "data": files})
}

// getLocalPlaylist 获取本地目录中的视频文件列表（仅当前目录，不递归）
func getLocalPlaylist(dirPath string) []string {
	entries, err := os.ReadDir(dirPath)
	if err != nil {
		return []string{}
	}
	var videos []string
	for _, entry := range entries {
		if !entry.IsDir() && dir.IsVideoFile(entry.Name()) {
			// 返回 /file/ 前缀路径，与 FileServer 路由格式一致
			videos = append(videos, "/file/"+dirPath+"/"+entry.Name())
		}
	}
	sort.Strings(videos)
	return videos
}

// getAlistPlaylist 获取 Alist 目录中的视频文件列表
func getAlistPlaylist(gallery models.Gallery, fileUrl string) []string {
	// fileUrl 格式如 /d/电影/xxx.mp4，去掉 /d 前缀得到 alist 路径
	alistPath := strings.TrimPrefix(fileUrl, "/d")
	parentDir := alistPath
	if idx := strings.LastIndex(parentDir, "/"); idx >= 0 {
		parentDir = parentDir[:idx]
	} else {
		parentDir = "/"
	}
	if parentDir == "" {
		parentDir = "/"
	}

	auth, err := alist.AlistLogin(gallery)
	if err != nil {
		return []string{}
	}

	contents, err := alist.AlistFilesByPath(false, gallery, parentDir, auth)
	if err != nil {
		return []string{}
	}

	var videos []string
	videoExts := map[string]bool{
		".mp4": true, ".mkv": true, ".avi": true, ".mov": true,
		".wmv": true, ".flv": true, ".webm": true, ".rmvb": true,
		".rm": true, ".ts": true, ".m2ts": true, ".mpg": true,
		".mpeg": true, ".3gp": true, ".m4v": true,
	}
	for _, item := range contents {
		if !item.IsDir {
			ext := strings.ToLower(filepath.Ext(item.Name))
			if videoExts[ext] {
				// 构造 /d 前缀路径，与数据库中存储格式一致
				fullPath := "/d" + parentDir
				if !strings.HasSuffix(fullPath, "/") {
					fullPath += "/"
				}
				fullPath += item.Name
				videos = append(videos, fullPath)
			}
		}
	}
	sort.Strings(videos)
	return videos
}
