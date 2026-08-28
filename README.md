# OneList

一个类似 Emby 的专注于刮削 Alist/OpenList 聚合网盘形成影视媒体库的程序。

![](./docs/imgs/01.png)

## 解决的痛点

- Alist/OpenList 挂载云盘后能在网页端看视频，却没有分类、没有海报墙
- 使用 WebDAV 挂载本地后，用 Jellyfin 或 Emby 刮削会下载视频截取封面导致封号
- 用 Jellyfin 或 Emby 之类，没有大带宽公网 IP，在外难以访问

## 特性

### 核心媒体库

- **影视刮削**：电影 / 电视剧自动从 TheMovieDB 抓取元数据（海报、简介、评分、演职员表、季集信息）
- **异步刮削**：视频先展示，刮削后台慢慢来，不用等刮削完成就能看到所有视频
- **并发控制**：限制同时刮削数量，避免 CPU 跑满
- **默认封面**：未刮削的视频自动显示默认封面，不再显示破图（支持自定义 `/config/picture` 目录图片）
- **多影库管理**：支持同时挂载多个 Alist/OpenList 影库，区分电影与电视剧

### Alist/OpenList 集成

- **目录浏览**：从 Alist/OpenList 直接浏览目录树并挂载（`alist_dir` / `alist_tree` / `alist_scan` 自动扫描）
- **文件反代**：视频统一走 onelist 同源地址 `/alist/proxy/`，解决跨域 / 外网播放问题，Tailscale 等内网穿透也能正常播放
- **阿里云盘 Open**：内置阿里云盘 Open 视频播放支持
- **文件改名清理**：视频文件改名后重新扫描，自动清理旧记录残留

### 播放与统计

- **多播放器适配**：内置 ArtPlayer，支持 H.264 / HLS / FLV，适配 IINA、Infuse、VLC、MXPlayer、NPlayer、PotPlayer 等外部播放器
- **播放历史**：心跳记录播放进度，今日观看时长统计
- **播放统计**：管理端可查看总览统计、影库统计、热门电影排行、每日时段分析
- **收藏 / 点赞 / 已看**：用户级收藏、点赞、播放记录管理

### TV 与遥控器

- **TV 遥控器适配**：方向键导航、确认播放、返回退出，支持 Android TV 浏览器操作
- **轻量 TV 前端**：`/tv` 路径提供纯 ES5 页面，兼容老浏览器 / 老电视

### 消息与通知

- **站内消息**：管理员向用户推送消息，SSE 实时流式推送
- **Webhook 推送**：支持外部系统通过 Webhook Token 调用接口推送消息
- **护眼屏保**：定时休息提醒，可配置播放时长、屏保时长、每日上限

### 其他实用功能

- **内置小游戏**：贪吃蛇、走迷宫、打地鼠、记忆翻牌、数学口算等
- **日志系统**：操作日志记录，支持配置保留天数自动清理
- **系统维护**：一键清理过期 / 孤立数据（`/v1/api/system/cleanup`）
- **壁纸系统**：护眼壁纸素材服务
- **多架构 Docker**：支持 amd64、arm64、armv7l、armv6

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Go 1.22 · Gin 1.8.2 · GORM 1.24.5 |
| 数据库 | SQLite（默认）/ MySQL |
| 认证 | JWT (`golang-jwt/jwt/v5`) |
| 缓存 | go-cache（内存缓存） |
| 定时任务 | robfig/cron/v3 |
| 前端 | Vue 3 · Naive UI · ArtPlayer · hls.js · flv.js |
| 部署 | Docker 多架构 · s6-overlay |

## 架构

```
┌──────────────┐    HTTP    ┌──────────────┐    SQL    ┌────────────┐
│   前端页面    │ ──────────>│   API 层     │ ─────────>│  数据库     │
│  (Vue 3)     │            │  (Gin)       │           │(SQLite/MySQL)│
└──────────────┘            └──────┬───────┘           └────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
              ┌──────────┐  ┌──────────┐  ┌────────────┐
              │Controller│  │ Service  │  │ Repository │
              └──────────┘  └──────────┘  └────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    ▼              ▼              ▼
              ┌──────────┐  ┌──────────┐  ┌────────────┐
              │  Models  │  │ Plugins  │  │   Utils    │
              └──────────┘  └────┬─────┘  │cache/gpool/│
                                 │        │extract/dir │
                    ┌────────────┼────────└────────────┘
                    ▼            ▼
              ┌──────────┐ ┌──────────┐
              │  Alist   │ │  TheDB   │
              │ 网盘集成 │ │ TMDB刮削 │
              └──────────┘ └──────────┘
```

**分层说明**：
- `api/controllers/` — REST 控制器（40+ 资源）
- `api/repository/` — 数据访问层（含通用 CRUD 基类）
- `api/models/` — GORM 数据模型
- `api/auth/` — JWT 认证与中间件
- `plugins/alist/` — Alist/OpenList 网盘交互
- `plugins/thedb/` — TheMovieDB 刮削
- `plugins/watch/` — 目录监控与自动刮削

## 快速开始

### Docker 安装（推荐）

```bash
docker run -d \
  --name onelist \
  --restart unless-stopped \
  -p 5245:5245 \
  -v /path/to/onelist/config:/config \
  ghcr.io/pelico/onelist:latest
```

ARM 设备（如树莓派）：

```bash
docker run -d \
  --name onelist \
  --restart unless-stopped \
  -p 5245:5245 \
  -v /path/to/onelist/config:/config \
  --platform linux/arm/v7 \
  ghcr.io/pelico/onelist:latest
```

### Docker Compose

```yaml
version: '3.3'
services:
  onelist:
    restart: always
    container_name: onelist
    image: 'ghcr.io/pelico/onelist:latest'
    volumes:
      - './config:/config'
    ports:
      - '5245:5245'
    environment:
      - PUID=0
      - PGID=0
      - TZ=Asia/Shanghai
```

## 配置说明

首次运行后，编辑 `/path/to/config/config.env`：

```env
# ========= 服务设置 =========
API_PORT=5245
API_SECRET=your-random-secret-key     # JWT 签名密钥，未配置则自动生成
Title=onelist
FaviconicoUrl=https://example.com/favicon.ico

# ========= 环境模式 =========
# Debug / Release，主要影响 MySQL 密码选择
Env=Debug

# ========= 管理员账户 =========
# 首次启动自动创建，之后通过 onelist -run admin 查看
UserEmail=admin@example.com
UserPassword=yourpassword

# ========= 数据库 =========
DB_DRIVER=sqlite                       # sqlite 或 mysql
DB_USER=root
DbName=onelist
# 以下仅 DB_DRIVER=mysql 时需要
DB_PASSWORD_Debug=123456
DB_PASSWORD_Release=123456

# ========= TheMovieDB =========
# 在 https://www.themoviedb.org/settings/api 申请
KeyDb=your_tmdb_api_key
# TMDB API 地址，留空使用官方地址，可填镜像或反代
TheMovieDbApiUrl=https://api.themoviedb.org/3

# ========= 图片设置 =========
# 下载刮削图片到本地（否=直接引用 TMDB CDN 链接，不占用本地空间）
DownLoadImage=否
ImgUrl=https://image.tmdb.org
# 自定义默认封面（否=内置默认图；是=使用 /config/picture 目录图片）
CustomDefaultImage=否

# ========= 视频类型 =========
VideoTypes=.mp4,.mkv,.flv,.avi,.wmv,.mov,.ts,.m2ts

# ========= 日志 =========
LogRetentionDays=30                     # 日志保留天数，0 为不清理

# ========= 护眼屏保 =========
ScreensaverEnabled=是
ScreensaverPlayDuration=3600            # 播放多久后触发屏保（秒），默认 1 小时
ScreensaverDuration=180                  # 屏保持续时长（秒），默认 3 分钟
ScreensaverDailyLimit=7200              # 每日屏保总时长上限（秒），默认 2 小时

# ========= Webhook 消息推送 =========
WebhookEnabled=否
WebhookToken=                           # 外部调用 /v1/api/webhook/message 时的 Token
```

修改后重启容器生效：

```bash
docker restart onelist
```

访问 `http://你的IP:5245`，用管理员账户登录。

## 命令行用法

```bash
# 初始化配置文件（生成 config.env，谨慎操作会覆盖已有配置）
onelist -run config

# 启动服务
onelist -run server

# 查看管理员账户及密码
onelist -run admin
```

## 添加媒体库

1. 登录后台 → 媒体中心 → 添加媒体库
2. 选择类型（电影 / 电视剧）
3. 填写 Alist/OpenList 地址、账号、密码（支持直接浏览目录树选择挂载路径）
4. 选择要刮削的文件夹，开启监控可自动刮削新增文件

> **文件命名规范**（命名决定刮削效果）：
> - 电影：`阿凡达2.mp4`
> - 电视剧：`权力的游戏S01E01.mp4`（识别 `SxxExx` 提取季集）

## 定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| 更新影库封面 | 启动时执行一次 | 为无封面的影库分配封面 |
| 更新影库封面 | 每 5 分钟 | 同上 |
| 监控目录刮削 | 每 6 小时 | 扫描挂载目录，刮削未入库的新文件 |
| 监控目录刮削 | 每天凌晨 2:30 | 同上（带互斥锁，防止重叠执行）|

## TMDB 访问问题

如果刮削失败，可能是网络访问不了 TMDB。解决方法：

**方法一**：在容器 hosts 中添加解析

```bash
docker exec -it onelist sh
echo "13.226.238.76 api.themoviedb.org" >> /etc/hosts
```

**方法二**：配置 TMDB API 镜像地址，在 `config.env` 中设置：

```env
TheMovieDbApiUrl=https://your-tmdb-mirror.com/3
```

**方法三**：使用代理

```bash
docker run -d \
  --name onelist \
  -e HTTP_PROXY=http://代理IP:端口 \
  -p 5245:5245 \
  -v /path/to/config:/config \
  ghcr.io/pelico/onelist:latest
```

## 常见问题

**Q: 添加 Alist 后无法挂载目录？**
A: 确保 Alist 域名格式正确，如 `http://192.168.1.100:5244`，不要有多余的 `/` 或路径。

**Q: 刮削成功但播放不了？**
A:
1. 确认 Alist 后台关闭"签名所有功能"
2. 确认视频编码是浏览器支持的（H.264）
3. 如果是外网访问，确认使用了本镜像的反代功能（无需额外配置，自动生效）

**Q: 外网通过 Tailscale / VPN 访问播放不了？**
A: 本镜像已内置 Alist 文件反代，视频统一走 onelist 同源地址，Tailscale 等内网穿透也能正常播放。

**Q: 占用空间越来越大？**
A: 默认配置 `DownLoadImage=否`，图片直接引用 TMDB CDN 链接，不占用本地空间。如需离线显示封面，可改为"是"。

**Q: 视频文件改名后重新扫描仍残留旧记录？**
A: 已修复文件改名残留清理逻辑，重扫后只保留最新名称。

**Q: 支持哪些设备？**
A:
- x86_64 / amd64（PC、NAS）
- ARM64（树莓派 4/5、Apple Silicon）
- ARMv7l（玩客云、旧树莓派）
- ARMv6（树莓派 Zero）

## 手动编译

需要 Go 1.22+ 和 Node.js 18+。

```bash
# 1. 构建前端
cd web
npm install
npm run build
# 把构建产物放到 public/dist
cd ..
rm -rf public/dist
cp -r web/dist public/dist

# 2. 构建后端
go mod tidy
go build -o ./bin/onelist -ldflags="-w -s" -tags=jsoniter .

# 3. 初始化配置并运行
./bin/onelist -run config
# 编辑 config.env
./bin/onelist -run server
```

也可使用 Docker 多阶段构建：

```bash
docker build -t onelist .
```

## 交流群

- QQ 群：765592050（原项目群）

## 致谢

本项目 Fork 自 [msterzhang/onelist](https://github.com/msterzhang/onelist)，感谢原作者的开源贡献。

---

> 开源不易，如果项目对你有帮助，欢迎 Star ⭐
