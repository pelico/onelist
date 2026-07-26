package dir

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/msterzhang/onelist/config"
)

// IsVideoFile 判断文件是否为视频文件（使用配置中的 VideoTypes）
func IsVideoFile(filename string) bool {
	ext := strings.ToLower(filepath.Ext(filename))
	if ext == "" {
		return false
	}
	// 如果配置了 VideoTypes，使用配置；否则使用默认列表
	if config.VideoTypes != "" {
		return strings.Contains(config.VideoTypes, ext)
	}
	// 默认视频扩展名列表
	defaultVideoExtensions := map[string]bool{
		".mp4":  true,
		".mkv":  true,
		".avi":  true,
		".mov":  true,
		".wmv":  true,
		".flv":  true,
		".webm": true,
		".rmvb": true,
		".rm":   true,
		".ts":   true,
		".m2ts": true,
		".mpg":  true,
		".mpeg": true,
		".3gp":  true,
		".m4v":  true,
	}
	return defaultVideoExtensions[ext]
}

// 递归遍历目录中的文件（只返回视频文件）
func GetFilesPath(path string, fileList []string) []string {
	fs, err := os.ReadDir(path)
	if err != nil {
		return fileList
	}
	for _, file := range fs {
		// 防止拼接path错误
		if path[len(path)-1:] != "/" {
			path += "/"
		}
		if file.IsDir() {
			fileList = GetFilesPath(path+file.Name()+"/", fileList)
		} else {
			// 只添加视频文件
			if IsVideoFile(file.Name()) {
				fileList = append(fileList, path+file.Name())
			}
		}
	}
	return fileList
}

// 获取目录中的所有文件
func GetFilesByPath(path string) []string {
	fileList := []string{}
	return GetFilesPath(path, fileList)
}

func DirExists(path string) bool {
	_, err := os.Stat(path)
	if err != nil {
		return os.IsExist(err)
	}
	return true
}

func FileExists(path string) bool {
	_, err := os.Stat(path)
	if err != nil {
		return os.IsExist(err)
	}
	return true
}
