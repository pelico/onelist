# OneList

一个类似 Emby 的专注于刮削 Alist/OpenList 聚合网盘形成影视媒体库的程序。

![](./docs/imgs/01.png)

## 主要解决以下痛点

* Alist/OpenList 挂载云盘后能在网页端看视频，却没有分类，没有海报墙
* 使用 WebDAV 挂载本地后，用 Jellyfin 或者 Emby 刮削会下载视频截取封面导致封号
* 用 Jellyfin 或者 Emby 之类，没有大带宽公网 IP，在外难以访问

## 本 Fork 新增特性

- **Docker 自动构建**：支持多平台镜像（amd64, arm64, armv7l, armv6），可从 GitHub Container Registry 直接拉取
- **Alist 目录浏览**：新增从 Alist/OpenList 直接浏览目录并挂载的功能
- **异步刮削**：视频先展示，刮削后台慢慢来，不用等刮削完成就能看到所有视频
- **Alist 文件反代**：解决跨域/外网播放问题，Tailscale 等内网穿透也能正常播放
- **TV 遥控器适配**：方向键导航、确认播放、返回退出，支持 Android TV 浏览器操作
- **默认封面**：未刮削的视频自动显示默认封面，不再显示破图
- **并发刮削控制**：限制同时刮削数量，避免 CPU 全部跑满

## 更新日志

### 2025-07 v2 — 稳定性修复

- **文件改名残留清理**：修复视频文件改名后（如 ABC → DDDABC），重新扫描仍残留旧记录的问题。基础记录查重逻辑和过期记录清理函数均已修正，确保重扫后只保留最新名称
- **日间模式 UI 修复**：
  - 翻页按钮颜色不生效 → 改用 Naive UI 组件原生 `color`/`text-color` props 传入颜色，绕过框架主题引擎的 CSS 优先级冲突
  - 媒体库"已加载 x / x 部"文字在日间模式下不可见 → 添加 `!important` 确保文字颜色在主题切换时始终生效
  - 涉及页面：主页、已播放、最爱、搜索
- **登录安全与体验修复**：
  - 账号/密码输入框中间空格实时清理 → 从 `.trim`（仅首尾）改为 `@input` + `replace(/\s+/g, '')`（所有空格）
  - 密码错误超 3 次不显示验证码 → 修复后端 `ShouldBindJSON` 解析 JSON body（原 `PostForm` 无法读取 JSON），并对不存在的用户名按 IP 累计失败次数，确保验证码正确触发

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

### 首次运行配置

1. 容器启动后，编辑 `/path/to/onelist/config/config.env`：

```env
# 服务端口
API_PORT=5245

# 管理员账户（首次启动后自动创建）
UserEmail=admin@example.com
UserPassword=yourpassword

# TheMovieDb API Key
# 在 https://www.themoviedb.org/settings/api 申请
KeyDb=your_tmdb_api_key

# 图片设置（默认不下载到本地，直接使用 TMDB CDN）
DownLoadImage=否
ImgUrl=https://image.tmdb.org

# 允许刮削的视频文件类型
VideoTypes=.mp4,.mkv,.flv,.avi,.wmv,.mov,.ts,.m2ts

# 数据库（默认 sqlite，足够个人使用）
DB_DRIVER=sqlite
```

2. 重启容器生效：
```bash
docker restart onelist
```

3. 访问 `http://你的IP:5245`，用上面的管理员账户登录

### 添加媒体库

1. 进入后台 → 媒体中心 → 添加媒体库
2. 选择类型（电影/电视剧）
3. 填写 Alist/OpenList 地址、账号、密码
4. 挂载目录，选择要刮削的文件夹

> **注意**：挂载目录中的文件名决定了刮削效果
> - 电影：`阿凡达2.mp4`
> - 电视剧：`权力的游戏S01E01.mp4`

### TMDB 访问问题

如果刮削失败，可能是网络访问不了 TMDB。解决方法：

**方法一**：在容器 hosts 中添加解析
```bash
docker exec -it onelist sh
echo "13.226.238.76 api.themoviedb.org" >> /etc/hosts
```

**方法二**：使用代理
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

**Q: 外网通过 Tailscale/VPN 访问播放不了？**
A: 本镜像已内置 Alist 文件反代，视频统一走 onelist 同源地址，Tailscale 等内网穿透也能正常播放。

**Q: 占用空间越来越大？**
A: 默认配置 `DownLoadImage=否`，图片直接引用 TMDB CDN 链接，不占用本地空间。如需离线显示封面，可在设置中改为"是"。

**Q: 支持哪些设备？**
A: 
- x86_64 / amd64（PC、NAS）
- ARM64（树莓派 4/5、Apple Silicon）
- ARMv7l（玩客云、旧树莓派）
- ARMv6（树莓派 Zero）

## 手动安装

如需手动编译安装，参考 [docker_install.md](./docs/docker_install.md)

## 交流群

- QQ 群：765592050（原项目群）

## 致谢

本项目 Fork 自 [msterzhang/onelist](https://github.com/msterzhang/onelist)，感谢原作者的开源贡献。

---

> 开源不易，如果项目对你有帮助，欢迎 Star ⭐
