package thedb

import (
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/msterzhang/onelist/api/utils/dir"
	"github.com/msterzhang/onelist/api/utils/logger"
)

var imgpath = "images"
var imgcdn = "https://tmdb-image-prod.b-cdn.net"

var dirs = []string{"w220_and_h330_face", "w440_and_h660_face", "w600_and_h900_bestv2", "w710_and_h400_multi_faces", "w227_and_h127_bestv2", "w1920_and_h1080_bestv2", "w355_and_h200_multi_faces"}

// 下载竖向海报图
var keys = []string{"w220_and_h330_face", "w440_and_h660_face", "w600_and_h900_bestv2"}

// 电视分季信息图片
var keysSeason = []string{"w220_and_h330_face"}

// 电视分集信息图片
var keysEpisode = []string{"w227_and_h127_bestv2", "w710_and_h400_multi_faces", "w1920_and_h1080_bestv2"}

// 下载横向海报图及背景图
var keysBackImge = []string{"w355_and_h200_multi_faces", "w1920_and_h1080_bestv2"}

// 初始化图片保存目录
func initDir() {
	for _, item := range dirs {
		imgsPath := imgpath + "/" + item
		if !dir.DirExists(imgsPath) {
			err := os.MkdirAll(imgsPath, os.ModePerm)
			if err != nil {
				log.Panic("创建图片保存文件夹失败!")
			}
		}
	}
}

// 下载电视剧及电影竖向海报图
func DownImages(id string) error {
	if len(id) == 0 {
		return nil
	}
	initDir()
	for _, key := range keys {
		url := fmt.Sprintf("%s/t/p/%s/%s", imgcdn, key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			continue
		}
		err := Download(url, file)
		if err != nil {
			continue
		}
	}
	return nil
}

// 下载电视分季所需图片
func DownSeasonImages(id string) error {
	if len(id) == 0 {
		return nil
	}
	initDir()
	for _, key := range keysSeason {
		url := fmt.Sprintf("%s/t/p/%s/%s", imgcdn, key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			continue
		}
		err := Download(url, file)
		if err != nil {
			continue
		}
	}
	return nil
}

// 下载电视分集所需图片
func DownEpisodeImages(id string) error {
	if len(id) == 0 {
		return nil
	}
	initDir()
	for _, key := range keysEpisode {
		url := fmt.Sprintf("%s/t/p/%s/%s", imgcdn, key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			continue
		}
		err := Download(url, file)
		if err != nil {
			continue
		}
	}
	return nil
}

// 下载影人图片
func DownPersonImage(id string) error {
	if len(id) == 0 {
		return nil
	}
	initDir()
	url := fmt.Sprintf("%s/t/p/%s/%s", imgcdn, "w220_and_h330_face", id)
	file := fmt.Sprintf("%s/%s/%s", imgpath, "w220_and_h330_face", id)
	if dir.FileExists(file) {
		return nil
	}
	err := Download(url, file)
	if err != nil {
		return err
	}
	return nil
}

// 下载封面及大背景图
func DownBackImage(id string) error {
	if len(id) == 0 {
		return nil
	}
	initDir()
	for _, key := range keysBackImge {
		url := fmt.Sprintf("%s/t/p/%s/%s", imgcdn, key, id)
		file := fmt.Sprintf("%s/%s/%s", imgpath, key, id)
		if dir.FileExists(file) {
			continue
		}
		err := Download(url, file)
		if err != nil {
			continue
		}
	}
	return nil
}

// 下载图片
func Download(url string, fileName string) error {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return err
	}
	client := http.Client{
		Timeout: timeOut,
	}
	resp, err := client.Do(req)
	if err != nil {
		logger.Warn("thedb", "图片下载失败: "+url, err.Error())
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		logger.Warn("thedb", "图片下载状态异常: "+url, "状态码: "+fmt.Sprintf("%d", resp.StatusCode))
		return fmt.Errorf("status code: %d", resp.StatusCode)
	}
	file, err := os.OpenFile(fileName, os.O_WRONLY|os.O_CREATE, 0666)
	if err != nil {
		return err
	}
	defer file.Close()
	io.Copy(file, resp.Body)
	logger.Debug("thedb", "图片下载成功: "+fileName)
	return nil
}

func DownloadImageToMedia(mediaPath string, posterUrl string, backdropUrl string) {
	mediaDir := filepath.Dir(mediaPath)
	fileName := filepath.Base(mediaPath)
	ext := filepath.Ext(fileName)
	nameWithoutExt := fileName[:len(fileName)-len(ext)]

	metadataDir := filepath.Join(mediaDir, nameWithoutExt)
	if !dir.DirExists(metadataDir) {
		err := os.MkdirAll(metadataDir, os.ModePerm)
		if err != nil {
			logger.Warn("thedb", "创建媒体元数据目录失败: "+metadataDir, err.Error())
			return
		}
	}

	if posterUrl != "" {
		posterFileName := filepath.Join(metadataDir, "poster.jpg")
		if !dir.FileExists(posterFileName) {
			fullUrl := imgcdn + "/t/p/w600_and_h900_bestv2" + posterUrl
			err := Download(fullUrl, posterFileName)
			if err != nil {
				logger.Warn("thedb", "下载封面到媒体目录失败: "+posterFileName, err.Error())
			}
		}
	}

	if backdropUrl != "" {
		backdropFileName := filepath.Join(metadataDir, "backdrop.jpg")
		if !dir.FileExists(backdropFileName) {
			fullUrl := imgcdn + "/t/p/w1920_and_h1080_bestv2" + backdropUrl
			err := Download(fullUrl, backdropFileName)
			if err != nil {
				logger.Warn("thedb", "下载背景图到媒体目录失败: "+backdropFileName, err.Error())
			}
		}
	}
}
