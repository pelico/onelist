package controllers

import (
	"fmt"
	"hash/fnv"
	"math/rand"
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
	"github.com/msterzhang/onelist/config"
	"github.com/msterzhang/onelist/plugins/alist"
)

// isPathSafe 检查清理后的路径是否仍在允许的基础目录内，防止路径遍历攻击
func isPathSafe(cleanPath, baseDir string) bool {
	absBase, err1 := filepath.Abs(baseDir)
	absPath, err2 := filepath.Abs(cleanPath)
	if err1 != nil || err2 != nil {
		return false
	}
	return strings.HasPrefix(absPath, absBase)
}

// 图片文件服务
func ImgServer(c *gin.Context) {
	rawPath := c.Param("path")
	filePath := filepath.Clean("images" + rawPath)
	if !isPathSafe(filePath, "images") {
		c.String(http.StatusForbidden, "非法路径")
		return
	}
	if !dir.FileExists(filePath) {
		c.Writer.WriteHeader(http.StatusNotFound)
		c.Writer.Flush()
		return
	}
	c.Header("Content-Type", "image/*")
	c.File(filePath)
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
	file = filepath.Clean(file)
	// 禁止路径遍历：清理后仍含 ".." 说明是恶意路径
	if strings.Contains(file, "..") {
		c.String(http.StatusForbidden, "非法路径")
		return
	}
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
	rawPath := c.Param("path")
	filePath := filepath.Clean("./images" + rawPath)
	if !isPathSafe(filePath, "images") {
		c.String(http.StatusForbidden, "非法路径")
		return
	}
	if !dir.FileExists(filePath) {
		c.Writer.WriteHeader(http.StatusNotFound)
		c.Writer.Flush()
		return
	}
	c.Header("Content-Type", "image/*")
	c.File(filePath)
}

func FileUpload(c *gin.Context) {
	file, err := c.FormFile("file")
	if err != nil {
		c.String(http.StatusBadRequest, "没有获得文件!")
		return
	}
	// 仅允许图片扩展名，防止上传恶意文件类型
	ext := strings.ToLower(path.Ext(file.Filename))
	allowedExts := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".webp": true, ".gif": true, ".bmp": true}
	if !allowedExts[ext] {
		c.String(http.StatusBadRequest, "仅支持图片文件 (jpg/jpeg/png/webp/gif/bmp)")
		return
	}
	id := tools.RandStringRunes(16)
	dst := "./images/w355_and_h200_multi_faces/" + id + ext
	data := "/gallery/w355_and_h200_multi_faces/" + id + ext
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
		filePath = filepath.Clean(filePath)
		// 禁止路径遍历
		if strings.Contains(filePath, "..") {
			c.JSON(200, gin.H{"code": 201, "msg": "非法路径", "data": []string{}})
			return
		}
		parentDir := filepath.Dir(filePath)
		files = getLocalPlaylist(parentDir)
	}

	logger.Debug("play", "播放列表加载", "媒体库: "+galleryUid+", 文件数: "+fmt.Sprintf("%d", len(files)))
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

// 获取 picture 目录下的图片列表（每次请求实时扫描，确保新增图片立即可用）
func getPictureImages() []string {
	pictureDir := filepath.Join("picture")
	entries, err := os.ReadDir(pictureDir)
	if err != nil {
		return []string{}
	}
	var images []string
	validExts := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".webp": true, ".bmp": true, ".gif": true}
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		ext := strings.ToLower(filepath.Ext(entry.Name()))
		if validExts[ext] {
			images = append(images, filepath.Join(pictureDir, entry.Name()))
		}
	}
	sort.Strings(images)
	return images
}

// 用 FNV-1a 做充分混合的哈希，避免连续 ID 产生线性/周期性规律
func hashSeed(s string) uint64 {
	h := fnv.New64a()
	h.Write([]byte(s))
	return h.Sum64()
}

// 以 round 为种子，对 [0, n) 做一次确定性 Fisher-Yates 洗牌
// 保证同一 round 内 n 张图片各用一次，不同 round 洗牌结果不同
func shuffledPermutation(round uint64, n int) []int {
	perm := make([]int, n)
	for i := range perm {
		perm[i] = i
	}
	r := rand.New(rand.NewSource(int64(round)))
	r.Shuffle(n, func(i, j int) { perm[i], perm[j] = perm[j], perm[i] })
	return perm
}

// 自定义默认封面图片服务：FNV-1a 哈希 + 按轮次 Fisher-Yates 洗牌，保证均匀且不重复
func CustomImgServer(c *gin.Context) {
	// 未开启自定义封面时返回默认图
	if config.CustomDefaultImage != "是" {
		c.Redirect(http.StatusFound, "/images/not_video.jpg")
		return
	}

	images := getPictureImages()
	n := len(images)
	if n == 0 {
		// picture 目录为空，回退到默认图
		c.Redirect(http.StatusFound, "/images/not_video.jpg")
		return
	}

	// FNV-1a 哈希影片 ID，彻底打掉连续 ID 的线性相关性
	id := hashSeed(c.Param("seed"))
	// 读取前端版本号（开关切换时递增），使每次切换产生不同的洗牌结果
	version := uint64(0)
	if v := c.Query("v"); v != "" {
		fmt.Sscanf(v, "%d", &version)
	}
	// 按 n 部影片一轮分组，轮内做 Fisher-Yates 洗牌
	// 加入 version 偏移，保证开关切换后同一影片分到不同的图
	round := id/uint64(n) + version
	position := int(id % uint64(n))

	perm := shuffledPermutation(round, n)
	imgPath := images[perm[position]]

	if !dir.FileExists(imgPath) {
		c.Redirect(http.StatusFound, "/images/not_video.jpg")
		return
	}
	c.Header("Content-Type", "image/*")
	// 不缓存，确保开关切换或图片更新后立即生效
	c.Header("Cache-Control", "no-cache, no-store, must-revalidate")
	c.File(imgPath)
}
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
