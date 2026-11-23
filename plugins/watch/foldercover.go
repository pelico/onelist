package watch

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/utils/logger"
	"github.com/msterzhang/onelist/plugins/alist"

	"gorm.io/gorm"
)

// 文件夹级自定义封面:
// 视频所在文件夹内若存在固定名图片(poster/cover/folder × jpg/jpeg/png/webp),
// 该文件夹内所有影片统一使用这张图作为封面(优先级高于刮削海报)。
// 检测结果以"可直接作为 <img src> 的 URL"写入 TheMovie/TheTv.CustomPoster,
// 本地走 /custom-poster/*path 路由,alist 走现成的 /alist/proxy/:gallery_uid/*path 代理。
// 删掉图片重新扫描后标记自动清空,前端回退刮削海报,实现"恢复"。

var coverBaseNames = []string{"poster", "cover", "folder"} // 名字优先级:poster > cover > folder
var coverImageExts = map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".webp": true}
var coverExtOrder = map[string]int{".jpg": 0, ".jpeg": 1, ".png": 2, ".webp": 3} // 同名内扩展名顺序
const coverInChunk = 500                                                        // SQLite IN 参数上限兜底

// DetectAndApplyFolderCovers 检测文件列表中各文件夹的固定名封面图并写入 DB。
// 必须在该批文件刮削完成之后调用(ChunkTheMovie 全量更新会覆盖 custom_poster)。
// 所有错误只记录日志,绝不影响刮削主流程。
func DetectAndApplyFolderCovers(files []string, gallery models.Gallery) {
	defer func() {
		if r := recover(); r != nil {
			logger.Warn("work", "文件夹封面检测异常,已跳过", fmt.Sprintf("%v", r))
		}
	}()
	if len(files) == 0 {
		return
	}

	// 1) 按父目录分组(dir 与文件 url 存储格式同源)
	folderFiles := map[string][]string{} // dir -> 该文件夹下的视频 url
	var dirs []string
	for _, f := range files {
		idx := strings.LastIndex(f, "/")
		if idx <= 0 {
			continue // 无父目录的异常路径,跳过
		}
		d := f[:idx]
		if _, ok := folderFiles[d]; !ok {
			dirs = append(dirs, d)
		}
		folderFiles[d] = append(folderFiles[d], f)
	}
	if len(dirs) == 0 {
		return
	}

	// 2) 逐文件夹检测;known=false 表示检测结果未知,本轮既不写也不清
	coverOf := map[string]string{} // dir -> custom_poster 值(空串 = 确认无图)
	known := map[string]bool{}
	dirCache := map[string][]alist.Content{} // alist 每文件夹仅 1 次列表调用,本轮缓存
	var auth string
	if gallery.IsAlist {
		token, err := alist.AlistLogin(gallery)
		if err != nil {
			logger.Warn("work", "文件夹封面检测: alist 登录失败,本轮跳过", gallery.GalleryUid+", "+err.Error())
		}
		auth = token
	}
	for _, d := range dirs {
		imgName, ok := detectFolderCover(d, gallery, auth, dirCache)
		known[d] = ok
		if ok && imgName != "" {
			coverOf[d] = buildPosterURL(gallery, d, imgName)
		}
	}

	// 3) 应用到 DB
	db := database.NewDb()
	if gallery.GalleryType == "tv" {
		applyTvCovers(db, folderFiles, coverOf, known, gallery)
	} else {
		applyMovieCovers(db, folderFiles, coverOf, known)
	}
	logger.Info("work", "文件夹封面检测完成", "媒体库: "+gallery.GalleryUid+", 目录数: "+strconv.Itoa(len(dirs)))
}

// detectFolderCover 检测单个文件夹内的固定名封面图
// 返回值: imgName=命中的图片文件名(可能为空), known=检测结果是否可信(失败时为 false,调用方跳过)
func detectFolderCover(dir string, gallery models.Gallery, auth string, dirCache map[string][]alist.Content) (string, bool) {
	if gallery.IsAlist {
		// 数据库存的是 /d/xxx 播放路径,alist API 需要逻辑路径 /xxx
		logical := strings.TrimPrefix(dir, "/d")
		if !strings.HasPrefix(logical, "/") {
			logical = "/" + logical
		}
		contents, hit := dirCache[logical]
		if !hit {
			if auth == "" {
				return "", false
			}
			var err error
			// isRef=false 走 alist 缓存:前置的 GetAlistFilesPath(path, true) 刚做过 refresh,数据仍新鲜
			contents, err = alist.AlistFilesByPath(false, gallery, logical, auth)
			if err != nil {
				logger.Warn("work", "文件夹封面检测: alist 列目录失败,跳过", logical+", "+err.Error())
				return "", false
			}
			dirCache[logical] = contents
		}
		names := make([]string, 0, len(contents))
		for _, c := range contents {
			if !c.IsDir {
				names = append(names, c.Name)
			}
		}
		return pickCoverImage(names), true
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		logger.Warn("work", "文件夹封面检测: 读目录失败,跳过", dir+", "+err.Error())
		return "", false
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		if !e.IsDir() {
			names = append(names, e.Name())
		}
	}
	return pickCoverImage(names), true
}

// pickCoverImage 在文件名列表中按 poster>cover>folder、jpg>jpeg>png>webp 择优,大小写不敏感
func pickCoverImage(names []string) string {
	bestRank := -1
	best := ""
	for _, n := range names {
		if strings.HasPrefix(n, "._") {
			continue // macOS 元数据文件,与 dir.go/alist.go 扫描约定一致
		}
		ext := strings.ToLower(filepath.Ext(n))
		if !coverImageExts[ext] {
			continue
		}
		base := strings.ToLower(strings.TrimSuffix(n, filepath.Ext(n)))
		for i, want := range coverBaseNames {
			if base == want {
				rank := i*10 + coverExtOrder[ext]
				if bestRank == -1 || rank < bestRank {
					bestRank = rank
					best = n
				}
				break
			}
		}
	}
	return best
}

// buildPosterURL 生成写入 DB 的封面 URL(可直接作为 <img src>)
func buildPosterURL(gallery models.Gallery, dir string, imgName string) string {
	if gallery.IsAlist {
		// dir 带 /d 前缀,AlistProxy 内部会剥掉,与视频播放代理路径格式一致
		return "/alist/proxy/" + gallery.GalleryUid + dir + "/" + imgName
	}
	// 与 /file/* 播放路由同约定:服务端剥掉前导 / 后按 cwd 相对路径读文件
	return "/custom-poster" + dir + "/" + imgName
}

// applyMovieCovers 电影库:按 url IN 精确匹配写/清 custom_poster(规避 LIKE 通配符)
func applyMovieCovers(db *gorm.DB, folderFiles map[string][]string, coverOf map[string]string, known map[string]bool) {
	for d, fs := range folderFiles {
		if !known[d] {
			continue
		}
		for _, part := range chunkStrings(fs, coverInChunk) {
			if err := db.Model(&models.TheMovie{}).Where("url IN ?", part).Update("custom_poster", coverOf[d]).Error; err != nil {
				logger.Warn("work", "文件夹封面写入失败(电影)", d+", "+err.Error())
			}
		}
	}
}

// applyTvCovers 电视剧库双路匹配 + 跨文件夹聚合决策(有图优先,全部确认无图才清空):
// A 路(基础记录): name = 文件名去扩展名,与 CreateBasicTvRecord/ChunkTheTv 口径一致
// B 路(刮削记录): Episode.url -> TheSeason.TheTvID -> TheTv
func applyTvCovers(db *gorm.DB, folderFiles map[string][]string, coverOf map[string]string, known map[string]bool, gallery models.Gallery) {
	// ---- A 路:按 name 匹配基础记录,按封面 URL 二次分组批量更新 ----
	urlNames := map[string][]string{} // coverURL(空串=清空) -> names
	for d, fs := range folderFiles {
		if !known[d] {
			continue
		}
		for _, f := range fs {
			n := strings.TrimSuffix(filepath.Base(f), filepath.Ext(f))
			urlNames[coverOf[d]] = append(urlNames[coverOf[d]], n)
		}
	}
	for url, names := range urlNames {
		for _, part := range chunkStrings(names, coverInChunk) {
			if err := db.Model(&models.TheTv{}).Where("gallery_uid = ? AND name IN ?", gallery.GalleryUid, part).Update("custom_poster", url).Error; err != nil {
				logger.Warn("work", "文件夹封面写入失败(电视基础记录)", err.Error())
			}
		}
	}

	// ---- B 路:剧集文件经 Episode -> Season -> Tv 关联到刮削记录,聚合决策 ----
	var allFiles []string
	for _, fs := range folderFiles {
		allFiles = append(allFiles, fs...)
	}
	if len(allFiles) == 0 {
		return
	}
	eps := []models.Episode{}
	for _, part := range chunkStrings(allFiles, coverInChunk) {
		partEps := []models.Episode{}
		db.Model(&models.Episode{}).Where("url IN ?", part).Find(&partEps)
		eps = append(eps, partEps...)
	}
	if len(eps) == 0 {
		return
	}
	seasonIDSet := map[uint]bool{}
	epByUrl := map[string]models.Episode{}
	for _, ep := range eps {
		epByUrl[ep.Url] = ep
		seasonIDSet[ep.TheSeasonID] = true
	}
	seasonIDs := make([]uint, 0, len(seasonIDSet))
	for id := range seasonIDSet {
		seasonIDs = append(seasonIDs, id)
	}
	seasons := []models.TheSeason{}
	for _, part := range chunkUints(seasonIDs, coverInChunk) {
		partSeasons := []models.TheSeason{}
		db.Model(&models.TheSeason{}).Where("id IN ?", part).Find(&partSeasons)
		seasons = append(seasons, partSeasons...)
	}
	season2tv := map[int]uint{} // the_season_id(TheSeason.ID 为 int) -> the_tv_id
	for _, s := range seasons {
		season2tv[s.ID] = s.TheTvID
	}

	// 聚合:同一部剧的集可能分布在多个文件夹,有图优先,全部确认无图才清空
	tvCover := map[uint]string{} // tvID -> 封面 URL(空串=清空)
	tvKnown := map[uint]bool{}
	for d, fs := range folderFiles {
		if !known[d] {
			continue
		}
		for _, f := range fs {
			ep, ok := epByUrl[f]
			if !ok {
				continue
			}
			tvID, ok2 := season2tv[int(ep.TheSeasonID)]
			if !ok2 {
				continue
			}
			tvKnown[tvID] = true
			if coverOf[d] != "" {
				tvCover[tvID] = coverOf[d]
			} else if _, exist := tvCover[tvID]; !exist {
				tvCover[tvID] = ""
			}
		}
	}
	for tvID, url := range tvCover {
		if err := db.Model(&models.TheTv{}).Where("id = ?", tvID).Update("custom_poster", url).Error; err != nil {
			logger.Warn("work", "文件夹封面写入失败(电视刮削记录)", err.Error())
		}
	}
}

// chunkStrings 按上限分块(SQLite IN 参数上限兜底)
func chunkStrings(list []string, size int) [][]string {
	if len(list) == 0 {
		return nil
	}
	var parts [][]string
	for i := 0; i < len(list); i += size {
		end := i + size
		if end > len(list) {
			end = len(list)
		}
		parts = append(parts, list[i:end])
	}
	return parts
}

func chunkInts(list []int, size int) [][]int {
	if len(list) == 0 {
		return nil
	}
	var parts [][]int
	for i := 0; i < len(list); i += size {
		end := i + size
		if end > len(list) {
			end = len(list)
		}
		parts = append(parts, list[i:end])
	}
	return parts
}

func chunkUints(list []uint, size int) [][]uint {
	if len(list) == 0 {
		return nil
	}
	var parts [][]uint
	for i := 0; i < len(list); i += size {
		end := i + size
		if end > len(list) {
			end = len(list)
		}
		parts = append(parts, list[i:end])
	}
	return parts
}
