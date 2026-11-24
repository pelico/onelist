# OneList

一个类似 Emby 的、专注于刮削 Alist/OpenList 聚合网盘形成影视媒体库的程序。

![](./docs/imgs/01.png)

## 解决的痛点

- Alist/OpenList 挂载云盘后网页看视频没有分类、没有海报墙
- Jellyfin/Emby 刮削 WebDAV 会下载视频截取封面，容易导致网盘封号
- 没有大带宽公网 IP 时，Jellyfin/Emby 在外难以访问

## 特性

- **影视刮削**：电影 / 电视剧自动抓取 TheMovieDB 元数据（海报、简介、评分、季集）
- **异步刮削**：视频先展示，刮削后台进行；未刮削的用默认封面，可自定义封面
- **多影库管理**：同时挂载多个 Alist/OpenList 影库，区分电影与电视剧；电视剧按剧聚合展示，详情页直接选集播放
- **文件反代**：视频统一走 onelist 同源地址，外网 / Tailscale 内网穿透也能正常播放
- **播放与统计**：播放历史、今日时长、影库统计、热门排行；收藏 / 点赞 / 已看
- **TV 遥控器适配**：`/tv` 路径提供轻量 TV 端页面，方向键选集、确认播放
- **安卓 TV 客户端**：Release 页提供 APK
- **消息与屏保**：站内消息 SSE 推送、Webhook、护眼屏保；内置若干小游戏
- **多架构 Docker**：amd64 / arm64 / armv7l

## 快速开始

### Docker Run

```bash
docker run -d \
  --name onelist \
  --restart unless-stopped \
  -p 5245:5245 \
  -v /path/to/onelist/config:/config \
  ghcr.io/pelico/onelist:latest
```

镜像自动匹配 CPU 架构（amd64 / arm64 / armv7l），无需指定 `--platform`。

### Docker Compose

```yaml
services:
  onelist:
    restart: always
    container_name: onelist
    image: ghcr.io/pelico/onelist:latest
    volumes:
      - './config:/config'
    ports:
      - '5245:5245'
    environment:
      - PUID=0
      - PGID=0
      - TZ=Asia/Shanghai
```

### 安卓 TV 客户端

在 [Releases](https://github.com/pelico/onelist/releases) 页下载 `OneList-TV-*.apk` 安装到电视 / 盒子即可，客户端加载的是服务端页面，**功能更新只需升级服务端，无需更新 APK**。

## 配置

首次启动自动生成 `/path/to/config/config.env`，常用项：

```env
UserEmail=admin@example.com        # 管理员账户（首次启动自动创建）
UserPassword=yourpassword
KeyDb=your_tmdb_api_key            # TMDB API Key，https://www.themoviedb.org/settings/api 申请
TheMovieDbApiUrl=https://api.themoviedb.org/3   # TMDB 可填镜像/反代地址
VideoTypes=.mp4,.mkv,.flv,.avi,.wmv,.mov,.ts
DownLoadImage=否                   # 否=图片引用 TMDB CDN，不占本地空间
LogRetentionDays=30
```

修改后 `docker restart onelist` 生效，访问 `http://IP:5245` 登录。

## 添加媒体库

1. 登录后台 → 媒体中心 → 添加媒体库
2. 选择类型（电影 / 电视剧），填写 Alist/OpenList 地址与账号
3. 浏览目录树选择挂载路径；选默认封面可跳过刮削立即观看

> **文件命名**：电影 `阿凡达2.mp4`；电视剧 `权力的游戏S01E01.mp4` 或放入 `剧名/S01/` 目录。命名不规范时按文件夹名聚合。

## TMDB 访问不了

```env
# config.env 中配置 TMDB 镜像地址
TheMovieDbApiUrl=https://your-tmdb-mirror.com/3
```

或在容器 `/etc/hosts` 加解析，或容器加 `HTTP_PROXY` 环境变量走代理。

## 常见问题

**添加 Alist 后挂载不到目录？** 地址格式 `http://192.168.1.100:5244`，不要多余的 `/` 或路径。

**刮削成功但播放不了？** Alist 后台关闭"签名所有"；确认编码为 H.264；外网播放走本镜像内置反代（自动生效）。

**播放统计没有数据？** 播放心跳按影片/剧集记录，电影按影片上报、电视剧按剧上报。

## 手动编译

Go 1.22+ 与 Node.js 18+：

```bash
# 前端
cd web && npm install && npm run build
rm -rf ../public/dist && cp -r dist ../public/dist && cd ..

# 后端
go build -o ./bin/onelist -ldflags="-w -s" -tags=jsoniter .
./bin/onelist -run config && ./bin/onelist -run server
```

或直接 `docker build -t onelist .`。

## 致谢

Fork 自 [msterzhang/onelist](https://github.com/msterzhang/onelist)，感谢原作者的开源贡献。
