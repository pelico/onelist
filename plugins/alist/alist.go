package alist

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"path/filepath"
	"strings"
	"time"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/config"
)

// 登录alist获取token
func AlistLogin(gallery models.Gallery) (string, error) {
	api := fmt.Sprintf("%s/api/auth/login", gallery.AlistHost)
	form := fmt.Sprintf(`{"username":"%s","password":"%s","otp_code":""}`, gallery.AlistUser, gallery.AlistPwd)
	req, err := http.NewRequest("POST", api, bytes.NewBufferString(form))
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", config.UA)
	req.Header.Set("Content-Type", "application/json;charset=UTF-8")
	client := http.Client{
		Timeout: 10 * time.Second,
	}
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	var data = AlistRspLogin{}
	err = json.Unmarshal(body, &data)
	if err != nil {
		return "", err
	}
	if data.Code == 200 {
		return data.Data.Token, nil
	}
	return "", errors.New(data.Message)
}

// 获取文件夹中文件及文件夹
func AlistFilesByPath(isRef bool, gallery models.Gallery, path string, Authorization string) ([]Content, error) {
	api := fmt.Sprintf("%s/api/fs/list", gallery.AlistHost)
	form := fmt.Sprintf(`{"path":"%s","password":"","page":1,"per_page":0,"refresh":%t}`, path, isRef)
	req, err := http.NewRequest("POST", api, bytes.NewBufferString(form))
	if err != nil {
		return []Content{}, err
	}
	req.Header.Set("User-Agent", config.UA)
	req.Header.Set("Authorization", Authorization)
	req.Header.Set("Content-Type", "application/json;charset=UTF-8")
	client := http.Client{
		Timeout: 10 * time.Second,
	}
	resp, err := client.Do(req)
	if err != nil {
		return []Content{}, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return []Content{}, err
	}
	var data = AListRspData{}
	err = json.Unmarshal(body, &data)
	if err != nil {
		return []Content{}, err
	}
	if data.Code == 200 {
		return data.Data.Content, nil
	}
	return []Content{}, errors.New(data.Message)
}

// 递归获取所有文件
func AlistList(isRef bool, gallery models.Gallery, path string, Authorization string, fileList []string) ([]string, error) {
	fs, err := AlistFilesByPath(isRef, gallery, path, Authorization)
	if err != nil {
		// 目录错误就重试一次
		fileList, err = AlistList(isRef, gallery, path, Authorization, fileList)
		if err != nil {
			if len(fileList) > 0 {
				return fileList, nil
			}
			return fileList, err
		}
	}
	for _, file := range fs {
		// 防止拼接path错误
		if path[len(path)-1:] != "/" {
			path += "/"
		}
		if file.IsDir {
			fileList, err = AlistList(isRef, gallery, path+file.Name+"/", Authorization, fileList)
			if err != nil {
				return fileList, err
			}
		} else {
			// 判断文件格式是否满足刮削条件
			fileAlistPath := "/d" + path + file.Name
			fileExt := filepath.Ext(fileAlistPath)
			if strings.Contains(config.VideoTypes, fileExt) {
				fileList = append(fileList, fileAlistPath)
			}
		}
	}
	return fileList, nil
}

// 根据目录获取alist中所有文件
func GetAlistFilesPath(path string, isRef bool, gallery models.Gallery) ([]string, error) {
	fileList := []string{}
	Authorization, err := AlistLogin(gallery)
	if err != nil {
		return []string{}, err
	}
	if gallery.AlistHost[len(gallery.AlistHost)-1:] == "/" {
		gallery.AlistHost = strings.TrimRight(gallery.AlistHost, "/")
	}
	return AlistList(isRef, gallery, path, Authorization, fileList)
}

// 目录树节点
type DirectoryNode struct {
	Name     string           `json:"name"`
	Path     string           `json:"path"`
	IsDir    bool             `json:"is_dir"`
	Children []DirectoryNode  `json:"children,omitempty"`
}

// 获取指定路径下的目录列表（只返回目录，不递归）
func GetAlistDirectoryList(gallery models.Gallery, path string) ([]DirectoryNode, error) {
	Authorization, err := AlistLogin(gallery)
	if err != nil {
		return []DirectoryNode{}, err
	}
	if gallery.AlistHost[len(gallery.AlistHost)-1:] == "/" {
		gallery.AlistHost = strings.TrimRight(gallery.AlistHost, "/")
	}
	contents, err := AlistFilesByPath(false, gallery, path, Authorization)
	if err != nil {
		return []DirectoryNode{}, err
	}
	var dirs []DirectoryNode
	for _, item := range contents {
		if item.IsDir {
			fullPath := path
			if fullPath[len(fullPath)-1:] != "/" {
				fullPath += "/"
			}
			fullPath += item.Name
			dirs = append(dirs, DirectoryNode{
				Name:  item.Name,
				Path:  fullPath,
				IsDir: true,
			})
		}
	}
	return dirs, nil
}

// 获取目录树结构（递归）
func GetAlistDirectoryTree(gallery models.Gallery, path string, depth int) ([]DirectoryNode, error) {
	Authorization, err := AlistLogin(gallery)
	if err != nil {
		return []DirectoryNode{}, err
	}
	if gallery.AlistHost[len(gallery.AlistHost)-1:] == "/" {
		gallery.AlistHost = strings.TrimRight(gallery.AlistHost, "/")
	}
	return alistDirectoryTreeRecursive(false, gallery, path, Authorization, depth)
}

func alistDirectoryTreeRecursive(isRef bool, gallery models.Gallery, path string, Authorization string, depth int) ([]DirectoryNode, error) {
	if depth <= 0 {
		return []DirectoryNode{}, nil
	}
	contents, err := AlistFilesByPath(isRef, gallery, path, Authorization)
	if err != nil {
		return []DirectoryNode{}, err
	}
	var dirs []DirectoryNode
	for _, item := range contents {
		if item.IsDir {
			fullPath := path
			if fullPath[len(fullPath)-1:] != "/" {
				fullPath += "/"
			}
			fullPath += item.Name
			node := DirectoryNode{
				Name:  item.Name,
				Path:  fullPath,
				IsDir: true,
			}
			if depth > 1 {
				children, err := alistDirectoryTreeRecursive(isRef, gallery, fullPath, Authorization, depth-1)
				if err == nil && len(children) > 0 {
					node.Children = children
				}
			}
			dirs = append(dirs, node)
		}
	}
	return dirs, nil
}

// 刮削失败后修改文件名时候同时提交到alist修改
func AlistRnameFile(name string, errfile models.ErrFile) error {
	gallery := models.Gallery{}
	db := database.NewDb()
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", errfile.GalleryUid).First(&gallery).Error
	if err != nil {
		return err
	}
	Authorization, err := AlistLogin(gallery)
	if err != nil {
		return err
	}
	api := fmt.Sprintf("%s/api/fs/rename", gallery.AlistHost)
	form := fmt.Sprintf(`{"path":"%s","name":"%s"}`, strings.ReplaceAll(errfile.File, "/d", ""), name)
	req, err := http.NewRequest("POST", api, bytes.NewBufferString(form))
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", config.UA)
	req.Header.Set("Authorization", Authorization)
	req.Header.Set("Content-Type", "application/json;charset=UTF-8")
	client := http.Client{
		Timeout: 10 * time.Second,
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}
	if strings.Contains(string(body), "200") {
		return nil
	}
	return errors.New(string(body))
}

// 刮削失败后修改文件名时候同时提交到alist修改
func AlistAliOpenVideo(file string, gallery_uid string) (AliOpenVideo, error) {
	gallery := models.Gallery{}
	db := database.NewDb()
	err := db.Model(&models.Gallery{}).Where("gallery_uid = ?", gallery_uid).First(&gallery).Error
	if err != nil {
		return AliOpenVideo{}, err
	}
	Authorization, err := AlistLogin(gallery)
	if err != nil {
		return AliOpenVideo{}, err
	}
	api := fmt.Sprintf("%s/api/fs/other", gallery.AlistHost)
	form := fmt.Sprintf(`{"path":"%s","password":"","method":"video_preview"}`, strings.ReplaceAll(file,"/d/","/"))
	req, err := http.NewRequest("POST", api, bytes.NewBufferString(form))
	if err != nil {
		return AliOpenVideo{}, err
	}
	req.Header.Set("User-Agent", config.UA)
	req.Header.Set("Authorization", Authorization)
	req.Header.Set("Content-Type", "application/json;charset=UTF-8")
	client := http.Client{
		Timeout: 10 * time.Second,
	}
	resp, err := client.Do(req)
	if err != nil {
		return AliOpenVideo{}, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return AliOpenVideo{}, err
	}
	var data = AliOpenVideo{}
	err = json.Unmarshal(body, &data)
	if err != nil {
		return AliOpenVideo{}, err
	}
	if data.Code == 200 {
		return data, nil
	}
	return AliOpenVideo{}, errors.New(data.Message)
}

type AlistFsGetRsp struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    AlistFsGetData  `json:"data"`
}

type AlistFsGetData struct {
	Name    string `json:"name"`
	Size    int64  `json:"size"`
	IsDir   bool   `json:"is_dir"`
	RawUrl  string `json:"raw_url"`
	Readme  string `json:"readme"`
	Provider string `json:"provider"`
}

func AlistFsGet(gallery models.Gallery, path string) (AlistFsGetData, error) {
	Authorization, err := AlistLogin(gallery)
	if err != nil {
		return AlistFsGetData{}, err
	}
	if gallery.AlistHost[len(gallery.AlistHost)-1:] == "/" {
		gallery.AlistHost = strings.TrimRight(gallery.AlistHost, "/")
	}
	api := fmt.Sprintf("%s/api/fs/get", gallery.AlistHost)
	form := fmt.Sprintf(`{"path":"%s","password":""}`, path)
	req, err := http.NewRequest("POST", api, bytes.NewBufferString(form))
	if err != nil {
		return AlistFsGetData{}, err
	}
	req.Header.Set("User-Agent", config.UA)
	req.Header.Set("Authorization", Authorization)
	req.Header.Set("Content-Type", "application/json;charset=UTF-8")
	client := http.Client{
		Timeout: 10 * time.Second,
	}
	resp, err := client.Do(req)
	if err != nil {
		return AlistFsGetData{}, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return AlistFsGetData{}, err
	}
	var data = AlistFsGetRsp{}
	err = json.Unmarshal(body, &data)
	if err != nil {
		return AlistFsGetData{}, err
	}
	if data.Code == 200 {
		return data.Data, nil
	}
	return AlistFsGetData{}, errors.New(data.Message)
}
