# OneList Code Wiki

## 项目概述

OneList 是一个类似 Emby 的专注于刮削 Alist 聚合网盘形成影视媒体库的程序。它解决了以下痛点：

- Alist 挂载云盘后能在网页端看视频，却没有分类和海报墙
- 使用 WebDAV 挂载本地后，用 Jellyfin 或 Emby 刮削会下载视频截取封面导致封号
- 使用 Jellyfin 或 Emby 之类的软件，没有大带宽公网 IP 在外难以访问

**技术栈**：
- Go 1.20
- Gin 1.8.2（Web 框架）
- GORM 1.24.5（ORM）
- SQLite/MySQL（数据库）
- JWT 3.2.0（认证）
- TheMovieDB API（影视数据刮削）

---

## 项目架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        OneList 项目架构                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    HTTP    ┌──────────────┐    SQL    ┌────────┐ │
│  │   前端页面    │ ──────────>│   API 层     │ ─────────>│  数据库 │ │
│  │  (dist/*)    │            │  (gin)       │            │(SQLite/│ │
│  └──────────────┘            └──────┬───────┘            │ MySQL) │ │
│                                     │                       └────────┘ │
│                    ┌────────────────┼────────────────┐               │
│                    ▼                ▼                ▼               │
│            ┌───────────┐    ┌───────────┐    ┌───────────┐          │
│            │Controller │    │ Service   │    │Repository │          │
│            │  控制器   │    │   服务层  │    │   数据层  │          │
│            └───────────┘    └───────────┘    └───────────┘          │
│                                     │                                │
│                    ┌────────────────┴────────────────┐               │
│                    ▼                                 ▼               │
│            ┌───────────┐                   ┌────────────────┐        │
│            │  Models   │                   │   Plugins      │        │
│            │  数据模型 │                   │    插件层      │        │
│            └───────────┘                   └───────┬────────┘        │
│                                                     │                │
│                          ┌──────────────────────────┼────────────────┐│
│                          ▼                          ▼                ││
│                   ┌───────────┐            ┌───────────┐            ││
│                   │   Alist   │            │  TheDB    │            ││
│                   │ 网盘集成  │            │ 影视刮削   │            ││
│                   └───────────┘            └───────────┘            ││
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                        Utils (工具层)                        │   │
│  │  cache | channels | dir | extract | gpool | tools           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 目录结构

```
onelist/
├── api/                    # API 层
│   ├── auth/               # 认证模块（JWT）
│   ├── controllers/        # REST API 控制器
│   ├── crons/              # 定时任务
│   ├── database/           # 数据库连接
│   ├── middleware/         # 中间件（CORS）
│   ├── models/             # 数据模型
│   ├── repository/         # 数据访问层（含 CRUD）
│   ├── security/           # 安全模块（密码加密）
│   ├── service/            # 业务服务层
│   ├── utils/              # 工具库
│   │   ├── cache/          # 缓存
│   │   ├── channels/       # 通道工具
│   │   ├── dir/            # 目录操作
│   │   ├── extract/        # 文件名提取
│   │   ├── gpool/          # goroutine 池
│   │   └── tools/          # 通用工具
│   └── server.go           # 服务启动入口
├── auto/                   # 自动初始化
├── config/                 # 配置管理
├── docker/                 # Docker 配置
├── docs/                   # 文档
├── initconfig/             # 初始化配置
├── plugins/                # 插件层
│   ├── alist/              # Alist 网盘集成
│   ├── thedb/              # TheMovieDB 刮削
│   └── watch/              # 目录监控
├── public/                 # 静态资源
├── wrapper/                # 包装器
├── .gitignore
├── Dockerfile
├── README.md
├── build.sh
├── config.env
├── docker-compose.yml
├── go.mod
├── go.sum
└── main.go                 # 项目主入口
```

---

## 核心模块详解

### 1. 入口模块

#### main.go

项目主入口，使用 `urfave/cli` 构建命令行接口。

**命令参数**：
| 参数 | 说明 |
|------|------|
| `-run config` | 初始化配置文件 |
| `-run server` | 启动服务 |
| `-run admin` | 查询管理员账户 |

**关键函数**：
- `main()` - 程序入口，解析命令行参数

[main.go](file:///workspace/main.go)

---

### 2. 配置模块

#### config/config.go

负责加载和管理系统配置，使用 `godotenv` 读取 `config.env` 文件。

**全局变量**：

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `PORT` | int | API 服务端口 |
| `Title` | string | 网站标题 |
| `FaviconicoUrl` | string | 网站图标 URL |
| `SECRETKEY` | []byte | JWT 签名密钥 |
| `DBDRIVER` | string | 数据库驱动（sqlite/mysql） |
| `DBURL` | string | 数据库连接 URL |
| `DbName` | string | 数据库名称 |
| `KeyDb` | string | TheMovieDB API Key |
| `UserEmail` | string | 管理员邮箱 |
| `UserPassword` | string | 管理员密码 |
| `DownLoadImage` | string | 是否下载图片 |
| `ImgUrl` | string | 图片服务 URL |
| `VideoTypes` | string | 支持的视频文件类型 |

**核心函数**：
- `Load()` - 加载配置文件
- `GetConfig()` - 获取当前配置
- `SetConfig()` - 设置配置
- `SaveConfig()` - 保存配置到文件

[config/config.go](file:///workspace/config/config.go)

#### config.env

环境配置文件，包含服务设置、数据库设置、管理员账户等。

[config.env](file:///workspace/config.env)

---

### 3. 服务器模块

#### api/server.go

Gin 服务器的核心配置，包含路由注册、中间件配置和服务启动。

**初始化流程**：
1. `InitServer()` - 初始化配置、数据库和定时任务
2. 创建 Gin 引擎，注册中间件
3. 注册静态文件服务
4. 注册 API 路由
5. 启动 HTTP 服务

**路由分组**：

| 路由前缀 | 模块 | 权限 |
|----------|------|------|
| `/v1/api/user` | 用户管理 | 公开/认证/管理员 |
| `/v1/api/genre` | 标签管理 | 认证/管理员 |
| `/v1/api/productioncompanie` | 制作公司 | 管理员 |
| `/v1/api/productioncountrie` | 制作国家 | 管理员 |
| `/v1/api/spokenlanguage` | 发布语言 | 管理员 |
| `/v1/api/thecredit` | 剧组人员 | 管理员 |
| `/v1/api/castitem` | 演员 | 管理员 |
| `/v1/api/crewitem` | 制作团队 | 管理员 |
| `/v1/api/belongstocollection` | 电影系列 | 管理员 |
| `/v1/api/themovie` | 电影管理 | 认证 |
| `/v1/api/theperson` | 演员管理 | 认证 |
| `/v1/api/thetv` | 电视剧管理 | 认证 |
| `/v1/api/episode` | 剧集管理 | 认证 |
| `/v1/api/theseason` | 季管理 | 认证 |
| `/v1/api/season` | 季信息 | 认证 |
| `/v1/api/lastepisodetoair` | 上一集 | 认证 |
| `/v1/api/nextepisodetoair` | 下一集 | 认证 |
| `/v1/api/networks` | 电视台 | 认证 |
| `/v1/api/gallery` | 影库管理 | 公开/认证/管理员 |
| `/v1/api/work` | 刮削任务 | 管理员 |
| `/v1/api/errfile` | 错误文件 | 管理员 |
| `/v1/api/star` | 收藏 | 认证 |
| `/v1/api/heart` | 点赞 | 认证 |
| `/v1/api/played` | 播放记录 | 认证 |
| `/v1/api/app` | 客户端API | 认证 |
| `/v1/api/aliopen` | 阿里云盘Open | 公开 |
| `/v1/api/config` | 系统设置 | 认证/管理员 |

[api/server.go](file:///workspace/api/server.go)

---

### 4. 认证模块

#### api/auth/auth.go

负责用户登录认证逻辑。

**核心函数**：
- `Login(email, password string)` - 用户登录，返回用户信息和 token
- `LoginAdmin(email, password string)` - 管理员登录，验证管理员权限

[api/auth/auth.go](file:///workspace/api/auth/auth.go)

#### api/auth/token.go

JWT Token 的生成、解析和刷新。

**核心函数**：
- `JWTAuth()` - 用户认证中间件
- `JWTAuthAdmin()` - 管理员认证中间件
- `GenerateJWT(user models.User)` - 生成 JWT Token
- `ParseToken(tokenString string)` - 解析 JWT Token
- `RefreshToken(tokenString string)` - 刷新 JWT Token

**错误类型**：
- `ErrTokenExpired` - Token 过期
- `ErrTokenNotValidYet` - Token 未激活
- `ErrTokenMalformed` - Token 格式错误
- `ErrTokenInvalid` - Token 无效

[api/auth/token.go](file:///workspace/api/auth/token.go)

#### api/security/password.go

密码加密与验证（使用 Base64 编码）。

**核心函数**：
- `Hash(password string)` - 密码加密
- `DecodePassword(hashedPassword string)` - 密码解密
- `VerifyPassword(hashedPassword, password string)` - 密码验证

[api/security/password.go](file:///workspace/api/security/password.go)

---

### 5. 数据库模块

#### api/database/db.go

数据库连接管理，支持 SQLite 和 MySQL。

**核心函数**：
- `InitDb()` - 初始化数据库连接
- `NewDb()` - 获取数据库实例

**配置特点**：
- 使用 GORM 的 `AutoMigrate` 自动创建表
- 支持连接池配置（最大空闲连接 10，最大打开连接 100）
- 连接生命周期 1 小时

[api/database/db.go](file:///workspace/api/database/db.go)

---

### 6. 数据模型

#### 核心模型关系

```
User ──────┬──────> Star ──────> TheMovie / TheTv
           ├──────> Heart ─────> TheMovie / TheTv
           └──────> Played ────> TheMovie / TheTv

Gallery ───────> Work ───────> ErrFile
    │
    ├───> TheMovie ───> Genre / ProductionCompanie / ProductionCountrie
    │      │                / SpokenLanguage / BelongsToCollection
    │      └───> TheCredit ──> CastItem / CrewItem
    │
    └───> TheTv ───> Season / TheSeason / Episode
           │         / Networks / LastEpisodeToAir / NextEpisodeToAir
           └───> TheCredit ──> CastItem / CrewItem

ThePerson <──> TheMovie (多对多)
ThePerson <──> TheTv (多对多)
```

#### 主要模型

##### User（用户模型）

| 字段 | 类型 | 说明 |
|------|------|------|
| `Id` | uint | 主键 |
| `UserName` | string | 用户名 |
| `UserId` | string | 用户唯一标识（UUID） |
| `UserEmail` | string | 用户邮箱（唯一） |
| `UserPassword` | string | 用户密码（Base64加密） |
| `IsAdmin` | bool | 是否管理员 |
| `IsLock` | bool | 是否锁定 |
| `CreatedAt` | time.Time | 创建时间 |
| `UpdatedAt` | time.Time | 更新时间 |

[api/models/User.go](file:///workspace/api/models/User.go)

##### Gallery（影库模型）

| 字段 | 类型 | 说明 |
|------|------|------|
| `Id` | uint | 主键 |
| `Title` | string | 影库标题 |
| `GalleryType` | string | 影库类型（movie/tv） |
| `IsTv` | bool | 是否电视剧 |
| `IsAliOpen` | bool | 是否阿里云盘Open |
| `GalleryUid` | string | 唯一标识（UUID） |
| `Image` | string | 封面图片 |
| `IsAlist` | bool | 是否 Alist 挂载 |
| `AlistHost` | string | Alist 域名 |
| `AlistUser` | string | Alist 用户名 |
| `AlistPwd` | string | Alist 密码 |
| `Works` | []Work | 关联的刮削任务 |

[api/models/Gallery.go](file:///workspace/api/models/Gallery.go)

##### TheMovie（电影模型）

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID` | int | TMDB ID（唯一） |
| `GalleryUid` | string | 所属影库 |
| `Adult` | bool | 是否成人内容 |
| `BackdropPath` | string | 背景图路径 |
| `PosterPath` | string | 海报路径 |
| `Title` | string | 标题 |
| `OriginalTitle` | string | 原始标题 |
| `Overview` | string | 简介 |
| `ReleaseDate` | string | 上映日期 |
| `VoteAverage` | float64 | 评分 |
| `VoteCount` | int | 投票数 |
| `Popularity` | float64 | 热度 |
| `Genres` | []Genre | 类型标签 |
| `ProductionCompanies` | []ProductionCompanie | 制作公司 |
| `ProductionCountries` | []ProductionCountrie | 制作国家 |
| `SpokenLanguages` | []SpokenLanguage | 语言 |
| `TheCredit` | TheCredit | 演职员表 |
| `Url` | string | 视频文件路径 |

[api/models/TheMovie.go](file:///workspace/api/models/TheMovie.go)

##### TheTv（电视剧模型）

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID` | int | TMDB ID（唯一） |
| `GalleryUid` | string | 所属影库 |
| `Name` | string | 名称 |
| `OriginalName` | string | 原始名称 |
| `Overview` | string | 简介 |
| `FirstAirDate` | string | 首播日期 |
| `LastAirDate` | string | 最后播出日期 |
| `NumberOfSeasons` | int | 季数 |
| `NumberOfEpisodes` | int | 集数 |
| `VoteAverage` | float64 | 评分 |
| `Genres` | []Genre | 类型标签 |
| `Networks` | []Networks | 播出平台 |
| `Seasons` | []Season | 季信息 |
| `TheSeasons` | []TheSeason | 季详情 |
| `TheCredit` | TheCredit | 演职员表 |

[api/models/TheTv.go](file:///workspace/api/models/TheTv.go)

##### Work（刮削任务模型）

| 字段 | 类型 | 说明 |
|------|------|------|
| `Id` | uint | 主键 |
| `GalleryID` | uint | 关联影库ID |
| `GalleryUid` | string | 影库标识 |
| `Path` | string | 刮削目录路径 |
| `FileNumber` | int | 文件总数 |
| `Speed` | int | 刮削进度 |
| `IsOk` | bool | 是否刮削完成 |
| `Watching` | bool | 是否监控 |
| `IsRef` | bool | 是否强制刷新 |

[api/models/Work.go](file:///workspace/api/models/Work.go)

##### 其他模型

- **Genre** - 电影/电视剧类型标签
- **ProductionCompanie** - 制作公司
- **ProductionCountrie** - 制作国家
- **SpokenLanguage** - 语言
- **BelongsToCollection** - 电影系列
- **TheCredit** - 演职员表
- **CastItem** - 演员
- **CrewItem** - 制作人员
- **ThePerson** - 人物信息
- **Season** - 季信息
- **TheSeason** - 季详情（含分集）
- **Episode** - 分集信息
- **Networks** - 播出平台
- **LastEpisodeToAir** - 上一集信息
- **NextEpisodeToAir** - 下一集信息
- **Star** - 收藏记录
- **Heart** - 点赞记录
- **Played** - 播放记录
- **ErrFile** - 刮削错误文件

---

### 7. 控制器模块

控制器采用统一的 RESTful 风格，每个资源对应一个控制器文件，包含以下标准操作：

| 操作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建 | POST | `/create` | 创建资源 |
| 更新 | POST | `/update` | 更新资源 |
| 删除 | POST | `/delete` | 删除资源 |
| 查询单个 | POST | `/id` | 根据ID查询 |
| 查询列表 | POST | `/list` | 分页查询 |
| 搜索 | POST | `/search` | 关键词搜索 |

#### 示例：controllerTheMovies.go

电影资源控制器，包含额外的业务方法：

- `GetTheMovieListByGalleryId()` - 按影库查询电影
- `AddThemovie()` - 手动添加电影（触发刮削）
- `SortThemovie()` - 电影排序

[api/controllers/controllerTheMovies.go](file:///workspace/api/controllers/controllerTheMovies.go)

#### 示例：controllerGallerys.go

影库控制器，包含管理员专属方法：

- `GetGalleryListAdmin()` - 管理员查看所有影库
- `GetGalleryHostByUid()` - 获取影库挂载信息

[api/controllers/controllerGallerys.go](file:///workspace/api/controllers/controllerGallerys.go)

---

### 8. 插件模块

#### alist（Alist 网盘集成）

负责与 Alist 网盘进行交互，获取文件列表和视频播放信息。

**核心函数**：
- `AlistLogin(gallery models.Gallery)` - 登录 Alist 获取 Token
- `AlistFilesByPath(isRef, gallery, path, Authorization)` - 获取目录文件列表
- `AlistList(isRef, gallery, path, Authorization, fileList)` - 递归获取所有文件
- `GetAlistFilesPath(path, isRef, gallery)` - 根据目录获取文件路径
- `AlistRnameFile(name, errfile)` - 重命名文件
- `AlistAliOpenVideo(file, gallery_uid)` - 获取阿里云盘 Open 视频信息

**数据结构**：
- `AlistRspLogin` - 登录响应
- `Content` - 文件信息
- `AListRspData` - 文件列表响应
- `AliOpenVideo` - 阿里云盘视频信息

[plugins/alist/alist.go](file:///workspace/plugins/alist/alist.go)

#### thedb（TheMovieDB 刮削）

负责从 TheMovieDB API 获取影视数据并保存到本地数据库。

**核心函数**：
- `SearchTheDb(key, tv bool)` - 搜索影视资源
- `GetCredits(id, tv bool)` - 获取演职员表
- `GetMovieData(id)` - 获取电影详情
- `GetTvData(id)` - 获取电视剧详情
- `GetTheSeasonData(id, item)` - 获取季详情
- `GetThePersonData(id)` - 获取人物信息
- `TheMovieDb(id, file, GalleryUid)` - 刮削并保存电影
- `TheTvDb(id, file, GalleryUid)` - 刮削并保存电视剧
- `RunTheMovieWork(file, GalleryUid)` - 自动刮削电影
- `RunTheTvWork(file, GalleryUid)` - 自动刮削电视剧

**文件提取逻辑**：
- 使用正则表达式提取电影名称（去除年份、扩展名、括号内容）
- 使用 `SxxExx` 格式提取电视剧季数和集数

[plugins/thedb/thedb.go](file:///workspace/plugins/thedb/thedb.go)

#### watch（目录监控）

负责监控挂载目录，自动刮削新添加的文件。

**核心函数**：
- `RunWork(work models.Work)` - 执行单个刮削任务
- `WatchPath()` - 监控所有挂载目录
- `UpdateGalleryImage()` - 自动为影库添加封面

[plugins/watch/watch.go](file:///workspace/plugins/watch/watch.go)

---

### 9. 工具模块

#### extract（文件名提取）

用于从文件名中提取电影名称和电视剧季集信息。

**核心函数**：
- `ExtractMovieName(s string)` - 提取电影名称
- `ExtractNumberWithFile(file string)` - 提取季数和集数

[api/utils/extract/extract.go](file:///workspace/api/utils/extract/extract.go)

#### dir（目录操作）

用于遍历本地目录获取文件列表。

**核心函数**：
- `GetFilesByPath(path string)` - 获取目录所有文件
- `DirExists(path string)` - 检查目录是否存在
- `FileExists(path string)` - 检查文件是否存在

[api/utils/dir/dir.go](file:///workspace/api/utils/dir/dir.go)

#### cache（缓存）

基于 `go-cache` 的内存缓存。

**核心函数**：
- `InitCache()` - 初始化缓存（10分钟过期，60分钟清理）
- `NewCache()` - 获取缓存实例

[api/utils/cache/cache.go](file:///workspace/api/utils/cache/cache.go)

#### gpool（协程池）

用于控制并发数量的 goroutine 池。

**核心函数**：
- `New(size int)` - 创建协程池
- `Add(delta int)` - 添加任务
- `Done()` - 完成任务
- `Wait()` - 等待所有任务完成

[api/utils/gpool/gpool.go](file:///workspace/api/utils/gpool/gpool.go)

#### tools（通用工具）

**核心函数**：
- `RandStringRunes(n int)` - 生成随机字符串

[api/utils/tools/tools.go](file:///workspace/api/utils/tools.go)

---

### 10. 定时任务模块

#### api/crons/cron.go

使用 `robfig/cron` 管理定时任务。

**定时任务**：

| 任务 | 执行频率 | 说明 |
|------|----------|------|
| `Run()` | 启动时执行一次 | 更新影库封面 |
| `RunFiveM()` | 每5分钟 | 更新影库封面 |
| `RunSixH()` | 每6小时 | 监控目录并刮削新文件 |
| `DayWork()` | 每天凌晨2:30 | 监控目录并刮削新文件 |

[api/crons/cron.go](file:///workspace/api/crons/cron.go)

---

### 11. 初始化模块

#### auto/load.go

负责数据库初始化和管理员账户创建。

**初始化流程**：
1. 如果使用 MySQL，创建数据库
2. 初始化 GORM 连接
3. 自动迁移所有表结构
4. 创建默认管理员账户
5. 初始化缓存系统

**自动迁移的表**：
- TheTv, Season, LastEpisodeToAir, NextEpisodeToAir, Networks
- Genre, ProductionCompanie, ProductionCountrie, SpokenLanguage
- BelongsToCollection, TheMovie, TheCredit, CastItem, CrewItem
- Episode, TheSeason, ThePerson, Work, Gallery, ErrFile
- User, Star, Heart, Played

[auto/load.go](file:///workspace/auto/load.go)

#### initconfig/initconfig.go

负责生成初始配置文件和查询管理员信息。

**核心函数**：
- `InitConfigEnv()` - 生成 config.env 配置文件
- `AdminData()` - 查询管理员账户信息

---

## 依赖关系

### 核心依赖

| 库名 | 版本 | 用途 |
|------|------|------|
| `gin-gonic/gin` | 1.8.2 | Web 框架 |
| `gorm.io/gorm` | 1.24.5 | ORM |
| `gorm.io/driver/sqlite` | 1.4.4 | SQLite 驱动 |
| `gorm.io/driver/mysql` | 1.4.7 | MySQL 驱动 |
| `dgrijalva/jwt-go` | 3.2.0 | JWT 认证 |
| `joho/godotenv` | 1.5.1 | 环境变量加载 |
| `patrickmn/go-cache` | 2.1.0 | 内存缓存 |
| `robfig/cron/v3` | 3.0.1 | 定时任务 |
| `urfave/cli` | 1.22.12 | 命令行接口 |
| `google/uuid` | 1.3.0 | UUID 生成 |

[go.mod](file:///workspace/go.mod)

---

## 项目运行方式

### 1. 初始化配置

```bash
./onelist -run config
```

会生成 `config.env` 配置文件，需要修改以下参数：
- `API_PORT` - 服务端口（默认 5245）
- `API_SECRET` - JWT 签名密钥
- `UserEmail` - 管理员邮箱
- `UserPassword` - 管理员密码
- `DB_DRIVER` - 数据库类型（sqlite/mysql）
- `KeyDb` - TheMovieDB API Key

### 2. 启动服务

```bash
./onelist -run server
```

### 3. 查询管理员信息

```bash
./onelist -run admin
```

### 4. Docker 运行

```bash
# Docker 方式
docker run -p 5245:5245 msterzhang/onelist

# Docker Compose 方式
docker-compose up -d
```

---

## 关键业务流程

### 1. 用户登录流程

```
用户请求 ──> LoginUser() ──> auth.Login() ──> security.VerifyPassword()
                                                 │
                                                 ▼
                                       GenerateJWT() ──> 返回 Token
```

### 2. 刮削流程（电影）

```
创建 Work ──> 遍历 Alist 文件 ──> extract.ExtractMovieName() ──> SearchTheDb()
                                                                       │
                                                                       ▼
                                                          GetMovieData() + GetCredits()
                                                                       │
                                                                       ▼
                                                          ChunkTheMovie() + ChunkPerson()
                                                                       │
                                                                       ▼
                                                          保存到数据库
```

### 3. 刮削流程（电视剧）

```
创建 Work ──> 遍历 Alist 文件 ──> extract.ExtractNumberWithFile() ──> SearchTheDb()
                                                                              │
                                                                              ▼
                                                         GetTvData() + GetCredits() + GetTheSeasonData()
                                                                              │
                                                                              ▼
                                                         ChunkTheTv() + ChunkTheSeason() + ChunkEpisode()
                                                                              │
                                                                              ▼
                                                         保存到数据库
```

### 4. 定时监控流程

```
Cron 触发 ──> WatchPath() ──> RunWork() ──> 检查文件是否已刮削
                                              │
                              ┌───────────────┴───────────────┐
                              ▼                               ▼
                      已存在（跳过）                    不存在（刮削）
                              └───────────────┬───────────────┘
                                              ▼
                                      RunTheMovieWork() / RunTheTvWork()
```

---

## 配置说明

### config.env 完整配置项

```env
# 服务设置
API_PORT=5245
FaviconicoUrl=https://example.com/favicon.ico
API_SECRET=your-secret-key
Title=OneList

# 环境模式
Env=Debug

# 管理员账户
UserEmail=admin@example.com
UserPassword=password

# 数据库设置
DB_DRIVER=sqlite
DB_USER=root
DbName=onelist

# MySQL 密码（当 DB_DRIVER=mysql 时）
DB_PASSWORD_Debug=123456
DB_PASSWORD_Release=123456

# 图片设置
DownLoadImage=是
ImgUrl=https://image.tmdb.org

# 视频类型
VideoTypes=.mp4,.mkv,.flv

# TheMovieDB Key
KeyDb=your-tmdb-api-key
```

### 数据库配置

**SQLite（默认）**：
```env
DB_DRIVER=sqlite
```

**MySQL**：
```env
DB_DRIVER=mysql
DB_PASSWORD_Debug=your-password
DB_PASSWORD_Release=your-password
```

---

## 注意事项

1. **TheMovieDB 访问**：推荐使用国外主机，国内主机可能需要修改 hosts 文件
2. **Alist 配置**：需要使用最新版 Alist，并关闭"签名所有功能"
3. **文件命名规范**：
   - 电影：直接使用电影名称
   - 电视剧：`权力的游戏S01E01.mp4`
4. **权限管理**：系统分为普通用户和管理员，管理员拥有所有权限
5. **缓存机制**：使用内存缓存，重启后清空

---

## 扩展建议

1. **密码安全**：当前使用 Base64 编码存储密码，建议改用 bcrypt 或 scrypt
2. **API 限流**：增加请求频率限制，防止 API 被滥用
3. **日志系统**：完善日志记录，便于问题排查
4. **多语言支持**：支持多种语言的影视数据
5. **通知系统**：刮削完成后发送通知
6. **数据备份**：增加数据库备份功能
