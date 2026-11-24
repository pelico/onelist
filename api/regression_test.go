package api

import (
	"bytes"
	"encoding/json"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/auto"
	"github.com/msterzhang/onelist/config"
	"github.com/gin-gonic/gin"
)

// ============ 全链路回归测试（本地媒体库，不依赖 alist/TMDB） ============
// 覆盖：登录/首页 → TV 库扫描聚合 → 详情选集 → 播放统计 → 一键清理 → 电影模式

var regPass, regFail int

func regCheck(t *testing.T, name string, ok bool, detail ...any) {
	if ok {
		regPass++
		t.Logf("PASS %s", name)
	} else {
		regFail++
		t.Logf("FAIL %s %v", name, detail)
	}
}

type RegServer struct {
	ts    *httptest.Server
	token string
}

func (s *RegServer) call(t *testing.T, method, path string, body any) map[string]any {
	var buf *bytes.Buffer
	if body != nil {
		b, _ := json.Marshal(body)
		buf = bytes.NewBuffer(b)
	} else {
		buf = bytes.NewBuffer(nil)
	}
	req, _ := http.NewRequest(method, s.ts.URL+path, buf)
	req.Header.Set("Content-Type", "application/json")
	if s.token != "" {
		req.Header.Set("Authorization", s.token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("request %s: %v", path, err)
	}
	defer resp.Body.Close()
	out := map[string]any{}
	_ = json.NewDecoder(resp.Body).Decode(&out)
	return out
}

func setupRegression(t *testing.T) *RegServer {
	gin.SetMode(gin.TestMode)
	dir := t.TempDir()

	config.DBDRIVER = "sqlite"
	config.DbName = filepath.Join(dir, "onelist")
	config.UserEmail = "admin@regression.test"
	config.UserPassword = "reg123456"
	config.SECRETKEY = []byte("regression-secret-key-for-test-only")
	config.VideoTypes = ".mp4"
	config.IsDev = false

	auto.InitDatabase()

	s := &RegServer{ts: httptest.NewServer(NewRouter())}
	t.Cleanup(s.ts.Close)
	return s
}

// waitTv 轮询等待某个剧出现且集数达到预期（扫描是异步 goroutine）
func waitTv(name string, minEpisodes int, timeout time.Duration) bool {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		db := database.NewDb()
		var tv struct {
			ID                int
			NumberOfEpisodes  int
		}
		err := db.Table("the_tv").
			Select("id, number_of_episodes").
			Where("name = ?", name).
			Order("id ASC").First(&tv).Error
		if err == nil && tv.NumberOfEpisodes >= minEpisodes {
			return true
		}
		time.Sleep(200 * time.Millisecond)
	}
	return false
}

func TestRegressionAll(t *testing.T) {
	regPass, regFail = 0, 0
	s := setupRegression(t)

	// ---------- S0 登录 + 首页 ----------
	res := s.call(t, "POST", "/v1/api/user/login", map[string]any{
		"user_email": config.UserEmail, "user_password": config.UserPassword,
	})
	token, _ := res["data"].(string)
	s.token = token
	regCheck(t, "S0 登录", res["code"] == float64(200) && token != "", res)

	res = s.call(t, "GET", "/v1/api/home", nil)
	regCheck(t, "S0 首页", res["code"] == float64(200), res)

	// ---------- S1 建 TV 库 + 挂载 + 扫描聚合 ----------
	tvDir := t.TempDir()
	abuDir := filepath.Join(tvDir, "阿布故事")
	os.MkdirAll(abuDir, 0755)
	os.MkdirAll(filepath.Join(tvDir, "布鲁伊 S01"), 0755)
	os.MkdirAll(filepath.Join(tvDir, "布鲁伊 S02"), 0755)
	os.WriteFile(filepath.Join(abuDir, "阿布故事 成语故事1 S01E01.mp4"), []byte("v"), 0644)
	os.WriteFile(filepath.Join(abuDir, "阿布故事 成语故事2 S01E01.mp4"), []byte("v"), 0644)
	os.WriteFile(filepath.Join(tvDir, "布鲁伊 S01", "S01E01.mp4"), []byte("v"), 0644)
	os.WriteFile(filepath.Join(tvDir, "布鲁伊 S02", "S01E01.mp4"), []byte("v"), 0644)

	res = s.call(t, "POST", "/v1/api/gallery/create", map[string]any{
		"title": "回归剧库", "gallery_type": "tv", "is_tv": true,
	})
	regCheck(t, "S1 建 TV 库", res["code"] == float64(200), res)

	res = s.call(t, "POST", "/v1/api/gallery/admin/list", nil)
	galleries, _ := res["data"].([]any)
	var tvUid string
	for _, g := range galleries {
		gm, _ := g.(map[string]any)
		if gm["gallery_type"] == "tv" {
			tvUid, _ = gm["gallery_uid"].(string)
		}
	}
	regCheck(t, "S1 获取影库 uid", tvUid != "", res)

	res = s.call(t, "POST", "/v1/api/work/create", map[string]any{
		"path": tvDir, "gallery_uid": tvUid, "use_default_cover": true,
	})
	regCheck(t, "S1 挂载目录", res["code"] == float64(200), res)

	// 等待扫描聚合完成：文件夹优先 → "阿布故事"2 集、"布鲁伊 S01/S02"合并为"布鲁伊"2 集
	okAbu := waitTv("阿布故事", 2, 15*time.Second)
	okBlu := waitTv("布鲁伊", 2, 15*time.Second)
	regCheck(t, "S1 扫描聚合(文件夹优先/副标题/季文件夹剥离)", okAbu && okBlu, okAbu, okBlu)

	// ---------- S2 列表 + 详情选集 ----------
	res = s.call(t, "POST", "/v1/api/thetv/gallery/list?id="+tvUid+"&page=1&size=10", nil)
	list, _ := res["data"].([]any)
	num := res["num"]
	regCheck(t, "S2 列表聚合 2 部剧", len(list) == 2 && num == float64(2), len(list), num)

	var abuId, bluId float64
	var abuEps, bluEps int
	for _, item := range list {
		m := item.(map[string]any)
		id, _ := m["id"].(float64)
		switch m["name"] {
		case "阿布故事":
			abuId = id
			abuEps = int(m["number_of_episodes"].(float64))
		case "布鲁伊":
			bluId = id
			bluEps = int(m["number_of_episodes"].(float64))
		}
	}
	regCheck(t, "S2 阿布故事 2 集", abuId > 0 && abuEps == 2, abuId, abuEps)
	regCheck(t, "S2 布鲁伊 2 集(两个季文件夹合并)", bluId > 0 && bluEps == 2, bluId, bluEps)

	res = s.call(t, "POST", "/v1/api/thetv/id?id="+strconv.Itoa(int(abuId)), nil)
	detail, _ := res["data"].(map[string]any)
	seasons, _ := detail["the_seasons"].([]any)
	epCount := 0
	for _, sea := range seasons {
		sm := sea.(map[string]any)
		eps, _ := sm["episodes"].([]any)
		epCount += len(eps)
	}
	regCheck(t, "S2 详情 the_seasons.episodes 2 集", len(seasons) >= 1 && epCount == 2, len(seasons), epCount)

	// ---------- S3 播放统计：心跳按剧上报 ----------
	for i := 1; i <= 2; i++ {
		res = s.call(t, "POST", "/v1/api/play-history/heartbeat", map[string]any{
			"data_type": "tv", "data_id": int(abuId), "title": "阿布故事",
			"gallery_uid": tvUid, "position": i * 30, "duration": 30, "total_duration": 1200,
		})
	}
	regCheck(t, "S3 TV 心跳上报", res["code"] == float64(200), res)

	db := database.NewDb()
	var durSum int
	db.Table("play_history").
		Select("COALESCE(SUM(duration),0)").
		Where("data_type = ? AND data_id = ?", "tv", int(abuId)).
		Scan(&durSum)
	regCheck(t, "S3 心跳时长累计 60s(位置增量)", durSum == 60, durSum)

	res = s.call(t, "POST", "/v1/api/play-history/top-movies", nil)
	tops, _ := res["data"].([]any)
	found := false
	for _, item := range tops {
		m := item.(map[string]any)
		if m["title"] == "阿布故事" && m["data_type"] == "tv" {
			found = true
		}
	}
	regCheck(t, "S3 Top 排行按剧聚合", found && len(tops) >= 1, tops)

	// ---------- S4 一键清理 ----------
	// a) 文件都在 → 不删任何记录
	res = s.call(t, "POST", "/v1/api/system/cleanup", nil)
	msg, _ := res["msg"].(string)
	regCheck(t, "S4a 全部文件在: 失效 0", res["code"] == float64(200) && strings.Contains(msg, "失效记录 0 条"), msg)

	// b) 删除布鲁伊 S02 的文件 → 仅对应集+空剧清理，阿布故事不受影响
	os.Remove(filepath.Join(tvDir, "布鲁伊 S02", "S01E01.mp4"))
	res = s.call(t, "POST", "/v1/api/system/cleanup", nil)
	regCheck(t, "S4b cleanup 执行成功", res["code"] == float64(200), res["msg"])
	okBlu2 := waitTv("布鲁伊", 1, 2*time.Second)
	db = database.NewDb()
	var bluEp, abuEp int64
	db.Table("episode").
		Joins("JOIN the_season ON the_season.id = episode.the_season_id").
		Where("the_season.the_tv_id IN (SELECT id FROM the_tv WHERE name = ?)", "布鲁伊").
		Count(&bluEp)
	db.Table("episode").
		Joins("JOIN the_season ON the_season.id = episode.the_season_id").
		Where("the_season.the_tv_id IN (SELECT id FROM the_tv WHERE name = ?)", "阿布故事").
		Count(&abuEp)
	regCheck(t, "S4b 失效集清理(布鲁伊剩1集,阿布故事2集不动)", okBlu2 && bluEp == 1 && abuEp == 2, bluEp, abuEp)

	// c) 挂载一个不存在的路径（模拟 alist 列文件失败）→ 其余记录不受影响
	res = s.call(t, "POST", "/v1/api/work/create", map[string]any{
		"path": filepath.Join(tvDir, "不存在目录XYZ"), "gallery_uid": tvUid, "use_default_cover": true,
	})
	time.Sleep(500 * time.Millisecond)
	res = s.call(t, "POST", "/v1/api/system/cleanup", nil)
	msg, _ = res["msg"].(string)
	var tvCount int64
	db.Table("the_tv").Where("gallery_uid = ?", tvUid).Count(&tvCount)
	regCheck(t, "S4c 无效挂载不引发误删(剧数保持2)", strings.Contains(msg, "失效记录 0 条") && tvCount == 2, msg, tvCount)

	// d) 所有 work 的 path 改成不存在路径 → fileSet 为空 → 全库防护跳过，不误删
	database.NewDb().Exec("UPDATE work SET path = ?", filepath.Join(tvDir, "不存在目录XYZ"))
	os.Remove(filepath.Join(tvDir, "布鲁伊 S01", "S01E01.mp4"))
	res = s.call(t, "POST", "/v1/api/system/cleanup", nil)
	tvCount = 0
	db.Table("the_tv").Where("gallery_uid = ?", tvUid).Count(&tvCount)
	regCheck(t, "S4d 文件列表为空防护(不误删)", tvCount == 2, tvCount)

	// ---------- S5 电影模式 ----------
	movDir := t.TempDir()
	os.WriteFile(filepath.Join(movDir, "流浪地球.mp4"), []byte("v"), 0644)
	os.WriteFile(filepath.Join(movDir, "疯狂的石头.mp4"), []byte("v"), 0644)

	res = s.call(t, "POST", "/v1/api/gallery/create", map[string]any{
		"title": "回归电影库", "gallery_type": "movie",
	})
	regCheck(t, "S5 建电影库", res["code"] == float64(200), res)

	res = s.call(t, "POST", "/v1/api/gallery/admin/list", nil)
	galleries, _ = res["data"].([]any)
	var movUid string
	for _, g := range galleries {
		gm, _ := g.(map[string]any)
		if gm["gallery_type"] == "movie" {
			movUid, _ = gm["gallery_uid"].(string)
		}
	}
	res = s.call(t, "POST", "/v1/api/work/create", map[string]any{
		"path": movDir, "gallery_uid": movUid, "use_default_cover": true,
	})
	regCheck(t, "S5 挂载电影目录", res["code"] == float64(200), res)

	// 等待电影扫描
	movOk := false
	deadline := time.Now().Add(15 * time.Second)
	for time.Now().Before(deadline) {
		var mc int64
		database.NewDb().Table("the_movie").Where("gallery_uid = ?", movUid).Count(&mc)
		if mc >= 2 {
			movOk = true
			break
		}
		time.Sleep(200 * time.Millisecond)
	}
	regCheck(t, "S5 电影扫描 2 条", movOk, nil)

	res = s.call(t, "POST", "/v1/api/themovie/gallery/list?id="+movUid+"&page=1&size=10", nil)
	movList, _ := res["data"].([]any)
	regCheck(t, "S5 电影列表", len(movList) == 2, len(movList))

	// 心跳（电影）+ 清理（删一个文件 → 只删对应记录）
	res = s.call(t, "POST", "/v1/api/play-history/heartbeat", map[string]any{
		"data_type": "movie", "data_id": 1, "title": "流浪地球",
		"gallery_uid": movUid, "position": 60, "duration": 30, "total_duration": 3600,
	})
	os.Remove(filepath.Join(movDir, "疯狂的石头.mp4"))
	res = s.call(t, "POST", "/v1/api/system/cleanup", nil)
	msg, _ = res["msg"].(string)
	var mc int64
	db.Table("the_movie").Where("gallery_uid = ?", movUid).Count(&mc)
	regCheck(t, "S5 电影失效清理(剩1条)", strings.Contains(msg, "失效记录 1 条") && mc == 1, msg, mc)

	// ---------- S6 封面上传 + 图片服务 ----------
	// 测试进程工作目录下会生成 ./images，结束后清理
	t.Cleanup(func() { os.RemoveAll("./images") })
	var upBody bytes.Buffer
	w := multipart.NewWriter(&upBody)
	fw, _ := w.CreateFormFile("file", "cover.png")
	_, _ = fw.Write([]byte("png-bytes-for-test"))
	_ = w.Close()
	upReq, _ := http.NewRequest("POST", s.ts.URL+"/file/gallery/upload", &upBody)
	upReq.Header.Set("Content-Type", w.FormDataContentType())
	upReq.Header.Set("Authorization", s.token)
	upResp, err := http.DefaultClient.Do(upReq)
	if err != nil {
		t.Fatalf("upload: %v", err)
	}
	upOut := map[string]any{}
	_ = json.NewDecoder(upResp.Body).Decode(&upOut)
	upResp.Body.Close()
	imgPath, _ := upOut["data"].(string)
	regCheck(t, "S6 封面上传", upOut["code"] == float64(200) && strings.HasPrefix(imgPath, "/gallery/"), upOut)

	imgResp, err := http.Get(s.ts.URL + imgPath)
	if err == nil {
		imgBytes, _ := io.ReadAll(imgResp.Body)
		imgResp.Body.Close()
		regCheck(t, "S6 图片服务回读", imgResp.StatusCode == 200 && string(imgBytes) == "png-bytes-for-test", imgResp.StatusCode)
	} else {
		regCheck(t, "S6 图片服务回读", false, err)
	}

	// ---------- 汇总 ----------
	t.Logf("========== 回归结果: PASS %d / FAIL %d ==========", regPass, regFail)
	if regFail > 0 {
		t.Fatalf("回归测试存在失败项: %d", regFail)
	}
}
