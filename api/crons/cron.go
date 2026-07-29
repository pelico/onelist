package crons

import (
	"log"
	"sync"

	"github.com/msterzhang/onelist/plugins/watch"
	"github.com/robfig/cron/v3"
)

var Cron *cron.Cron

// watchMu 防止 WatchPath 重叠执行（大型目录刮削可能超过调度间隔）
var watchMu sync.Mutex

func Run() {
	watch.UpdateGalleryImage()
}

// 5分钟运行一次
func RunFiveM() {
	watch.UpdateGalleryImage()
}

// 6小时运行一次
func RunSixH() {
	watchMu.Lock()
	defer watchMu.Unlock()
	watch.WatchPath()
}

// 凌晨两点运行
func DayWork() {
	watchMu.Lock()
	defer watchMu.Unlock()
	watch.WatchPath()
}

// 初始化定时任务
func Load() {
	go Run()
	Cron = cron.New()
	_, err := Cron.AddFunc("@every 6h", RunSixH)
	if err != nil {
		log.Fatal("添加任务失败:" + err.Error())
	}
	_, err = Cron.AddFunc("@every 5m", RunFiveM)
	if err != nil {
		log.Fatal("添加任务失败:" + err.Error())
	}
	_, err = Cron.AddFunc("30 2 * * *", DayWork)
	if err != nil {
		log.Fatalf("添加任务失败:%s", err.Error())
	}
	Cron.Start()
}
