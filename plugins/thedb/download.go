package thedb

import (
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/msterzhang/onelist/api/utils/dir"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/config"
)

var imgpath = "images"

func GetImgCdn() string {
	if config.ImgUrl != "" {
		url := strings.TrimSpace(config.ImgUrl)
		url = strings.ReplaceAll(url, " ", "")
		if !strings.HasPrefix(url, "http://") && !strings.HasPrefix(url, "https://") {
			url = "https://" + url
		}
		return url
	}
	return "https://tmdb-image-prod.b-cdn.net"
}

var dirs = []string{"w220_and_h330_face", "w440_and_h660_face", "w600_and_h900_bestv2", "w710_and_h400_multi_faces", "w227_and_h127_bestv2", "w1920_and_h1080_bestv2", "w355_and_h200_multi_faces"}

// 下载竖向海报图
var keys = []string{"w220_and_h330_face", "w440_and_h660_face", "w600_and_h900_bestv2"}

// 电视分季信息图片
var keysSeason = []string{"w220_and_h330_face"}

// 电视分集信息图片
var keysEpisode = []string{"w227_and_h127_bestv2", "w710_and_h400_multi_faces", "w1920_and_h1080_bestv2"}

// 下载横向海报图及背景图
var keysBackImge = []string{"w355_and_h200_multi_faces", "w1920_and_h1080_bestv2"}

// 初始化图片保存目录（仅在首次下载时执行一次）
var initDirOnce sync.Once

func initDir() {
	initDirOnce.Do(func() {
		for _, item := range dirs {
			imgsPath := imgpath + "/" + item
			if !dir.DirExists(imgsPath) {
				err := os.MkdirAll(imgsPath, os.ModePerm)
				if err != nil {
					log.Printf("[ERROR] 创建图片保存文件夹失败: %s, 错误: %v", imgsPath, err)
					return
				}
			}
		}
	})
}

// 下载电视剧及电影竖向海报图
func DownImages(id string) error {
	if len(id) == 0 {
		return nil
	}
	logger.Info("thedb", "开始下载竖向海报图", "图片ID: "+id)
	initDir()
	for _, key := range keys {
		url := fmt.Sprintf("%s/t/p/%s/%s", GetImgCdn(), key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			logger.Info("thedb", "跳过已存在的竖向海报图", "文件: "+file)
			continue
		}
		err := Download(url, file)
		if err != nil {
			logger.Warn("thedb", "下载竖向海报图失败", "URL: "+url+", 错误: "+err.Error())
			continue
		}
		logger.Info("thedb", "下载竖向海报图成功", "文件: "+file)
	}
	return nil
}

// 下载电视分季所需图片
func DownSeasonImages(id string) error {
	if len(id) == 0 {
		return nil
	}
	logger.Info("thedb", "开始下载分季图片", "图片ID: "+id)
	initDir()
	for _, key := range keysSeason {
		url := fmt.Sprintf("%s/t/p/%s/%s", GetImgCdn(), key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			logger.Info("thedb", "跳过已存在的分季图片", "文件: "+file)
			continue
		}
		err := Download(url, file)
		if err != nil {
			logger.Warn("thedb", "下载分季图片失败", "URL: "+url+", 错误: "+err.Error())
			continue
		}
		logger.Info("thedb", "下载分季图片成功", "文件: "+file)
	}
	return nil
}

// 下载电视分集所需图片
func DownEpisodeImages(id string) error {
	if len(id) == 0 {
		return nil
	}
	logger.Info("thedb", "开始下载分集图片", "图片ID: "+id)
	initDir()
	for _, key := range keysEpisode {
		url := fmt.Sprintf("%s/t/p/%s/%s", GetImgCdn(), key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			logger.Info("thedb", "跳过已存在的分集图片", "文件: "+file)
			continue
		}
		err := Download(url, file)
		if err != nil {
			logger.Warn("thedb", "下载分集图片失败", "URL: "+url+", 错误: "+err.Error())
			continue
		}
		logger.Info("thedb", "下载分集图片成功", "文件: "+file)
	}
	return nil
}

// 下载影人图片
func DownPersonImage(id string) error {
	if len(id) == 0 {
		return nil
	}
	logger.Info("thedb", "开始下载影人图片", "图片ID: "+id)
	initDir()
	url := fmt.Sprintf("%s/t/p/%s/%s", GetImgCdn(), "w220_and_h330_face", id)
	file := fmt.Sprintf("%s/%s/%s", imgpath, "w220_and_h330_face", id)
	if dir.FileExists(file) {
		logger.Info("thedb", "跳过已存在的影人图片", "文件: "+file)
		return nil
	}
	err := Download(url, file)
	if err != nil {
		logger.Warn("thedb", "下载影人图片失败", "URL: "+url+", 错误: "+err.Error())
		return err
	}
	logger.Info("thedb", "下载影人图片成功", "文件: "+file)
	return nil
}

// 下载封面及大背景图
func DownBackImage(id string) error {
	if len(id) == 0 {
		return nil
	}
	logger.Info("thedb", "开始下载背景图", "图片ID: "+id)
	initDir()
	for _, key := range keysBackImge {
		url := fmt.Sprintf("%s/t/p/%s/%s", GetImgCdn(), key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			logger.Info("thedb", "跳过已存在的背景图", "文件: "+file)
			continue
		}
		err := Download(url, file)
		if err != nil {
			logger.Warn("thedb", "下载背景图失败", "URL: "+url+", 错误: "+err.Error())
			continue
		}
		logger.Info("thedb", "下载背景图成功", "文件: "+file)
	}
	return nil
}

// 下载图片
func Download(url string, fileName string) error {
	logger.Info("thedb", "发起图片下载请求", "URL: "+url)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		logger.Error("thedb", "创建图片下载请求失败", "URL: "+url+", 错误: "+err.Error())
		return err
	}
	resp, err := sharedHTTPClient.Do(req)
	if err != nil {
		logger.Error("thedb", "图片下载请求失败", "URL: "+url+", 错误: "+err.Error())
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		logger.Warn("thedb", "图片下载状态异常", "URL: "+url+", 状态码: "+fmt.Sprintf("%d", resp.StatusCode))
		return fmt.Errorf("status code: %d", resp.StatusCode)
	}
	file, err := os.OpenFile(fileName, os.O_WRONLY|os.O_CREATE, 0666)
	if err != nil {
		logger.Error("thedb", "创建图片文件失败", "文件: "+fileName+", 错误: "+err.Error())
		return err
	}
	defer file.Close()
	_, err = io.Copy(file, resp.Body)
	if err != nil {
		logger.Error("thedb", "写入图片文件失败", "文件: "+fileName+", 错误: "+err.Error())
		return err
	}
	logger.Info("thedb", "图片下载成功", "文件: "+fileName+", URL: "+url)
	return nil
}

func DownloadImageToMedia(mediaPath string, posterUrl string, backdropUrl string) {
	logger.Info("thedb", "开始下载图片到媒体目录", "媒体文件: "+mediaPath)
	mediaDir := filepath.Dir(mediaPath)
	fileName := filepath.Base(mediaPath)
	ext := filepath.Ext(fileName)
	nameWithoutExt := fileName[:len(fileName)-len(ext)]

	metadataDir := filepath.Join(mediaDir, nameWithoutExt)
	if !dir.DirExists(metadataDir) {
		err := os.MkdirAll(metadataDir, os.ModePerm)
		if err != nil {
			logger.Error("thedb", "创建媒体元数据目录失败", "目录: "+metadataDir+", 错误: "+err.Error())
			return
		}
		logger.Info("thedb", "创建媒体元数据目录成功", "目录: "+metadataDir)
	}

	if posterUrl != "" {
		posterFileName := filepath.Join(metadataDir, "poster.jpg")
		if !dir.FileExists(posterFileName) {
			fullUrl := GetImgCdn() + "/t/p/w600_and_h900_bestv2" + posterUrl
			logger.Info("thedb", "开始下载封面到媒体目录", "URL: "+fullUrl+", 文件: "+posterFileName)
			err := Download(fullUrl, posterFileName)
			if err != nil {
				logger.Warn("thedb", "下载封面到媒体目录失败", "文件: "+posterFileName+", 错误: "+err.Error())
			} else {
				logger.Info("thedb", "下载封面到媒体目录成功", "文件: "+posterFileName)
			}
		} else {
			logger.Info("thedb", "跳过已存在的媒体封面", "文件: "+posterFileName)
		}
	}

	if backdropUrl != "" {
		backdropFileName := filepath.Join(metadataDir, "backdrop.jpg")
		if !dir.FileExists(backdropFileName) {
			fullUrl := GetImgCdn() + "/t/p/w1920_and_h1080_bestv2" + backdropUrl
			logger.Info("thedb", "开始下载背景图到媒体目录", "URL: "+fullUrl+", 文件: "+backdropFileName)
			err := Download(fullUrl, backdropFileName)
			if err != nil {
				logger.Warn("thedb", "下载背景图到媒体目录失败", "文件: "+backdropFileName+", 错误: "+err.Error())
			} else {
				logger.Info("thedb", "下载背景图到媒体目录成功", "文件: "+backdropFileName)
			}
		} else {
			logger.Info("thedb", "跳过已存在的媒体背景图", "文件: "+backdropFileName)
		}
	}
}
