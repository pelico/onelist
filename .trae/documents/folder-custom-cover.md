# 文件夹级自定义封面(动画片段统一封面)实施计划

## Context(背景)

影片封面目前两种形式:① TMDB 刮削海报(`TheMovie/TheTv.PosterPath`);② 自定义随机封面(`PosterPath` 为空且开关开启时,`/custom-image/:id` 从 `picture/` 目录哈希洗牌随机分配)。用户希望对动画片做**文件夹级统一封面**:视频所在文件夹内若放了一张固定名字的图片(`poster.*` / `cover.*` / `folder.*`),该文件夹内所有影片都用这张图做封面,实现统一显示。

已与用户确认的决策:
- **优先级:文件夹封面 > 刮削海报 > 随机图 > 默认图**(覆盖刮削结果;删图重扫即恢复)
- **命名约定**:poster / cover / folder × jpg/jpeg/png/webp,大小写不敏感,同名优先级 poster > cover > folder
- **本地与 alist 网盘媒体库都支持**

核心思路:扫描时检测文件夹封面图并把"可直接作为 `<img src>` 的 URL"写入 TheMovie/TheTv 新字段 `CustomPoster`(AutoMigrate 自动加列);前端 `getPosterUrl` 优先返回该字段。请求时零磁盘扫描、零额外 DB 查询。

## 改动清单

### A. 模型加字段(2 处,各 1 行)
- `api/models/TheMovie.go`:Url 字段后加 `CustomPoster string \`json:"custom_poster"\``
- `api/models/TheTv.go`:PosterPath 字段后同上
- 空串 = 无文件夹封面;auto/load.go 已对两表 AutoMigrate,无需迁移代码

### B. 新建核心逻辑 `plugins/watch/foldercover.go`(package watch)

函数:
- `DetectAndApplyFolderCovers(files []string, gallery models.Gallery)` 入口
- `detectCover(dir, gallery, auth, dirCache) (imgName string, known bool)` 单文件夹检测;`known=false`(ReadDir 失败 / alist 列表失败 / 登录失败)表示"未知",本轮不写不清
- `pickCover(names []string) string` 按 poster>cover>folder、jpg>jpeg>png>webp 择优,过滤 `._` 元数据文件,大小写不敏感
- `buildPosterURL(gallery, dir, imgName) string`:
  - 本地 → `/custom-poster{dir}/{imgName}`(dir 与 DB url 同源带 `/` 前缀)
  - alist → `/alist/proxy/{gallery_uid}{dir}/{imgName}`(dir 带 `/d` 前缀,AlistProxy 内部会剥掉,零改动复用)
- `applyMovieCovers` / `applyTvCovers` + IN 参数 500 分块(SQLite 上限兜底)

逻辑要点:
1. 按 `strings.LastIndex(f, "/")` 取父目录分组
2. alist 检测:`AlistFilesByPath(false, gallery, logicalDir, auth)`,logicalDir = 剥 `/d` 前缀;每文件夹 1 次调用,本轮 `dirCache` 缓存;isRef=false 走 alist 缓存不增加 refresh 成本
3. movie 库:按 `url IN (该文件夹文件列表)` 精确匹配写/清 `custom_poster`(规避 LIKE 通配符)
4. tv 库双路匹配:
   - A 路(基础记录):`name = TrimSuffix(Base(file), Ext)` + `gallery_uid` 匹配 TheTv(与 CreateBasicTvRecord/ChunkTheTv 口径一致);按封面 URL 二次分组批量 UPDATE
   - B 路(刮削记录):`Episode.url IN (files)` → `TheSeason.ID = Episode.TheSeasonID` → `TheTv.ID = TheSeason.TheTvID`
   - 跨文件夹聚合决策:**有图优先,全部确认无图才清空**(同一部剧的集可能分布在多季文件夹)
5. 检测失败 ≠ 无图:unknown 文件夹本轮跳过,防网络抖动误清标记

### C. 调用点(3 处,各插 1 行)
1. `api/controllers/controllerWorks.go` `RunWork`:`cleanupStaleRecords(...)` 之后、`work.IsOk = true` 之前(必须排在 cleanup 后,避免去重删掉带标记的记录)
2. 同文件 `RunWorkNew`:UseDefaultCover if/else 结束后、`work.IsOk = true`(201 行)之前
3. `plugins/watch/watch.go` `RunWork`:for 循环结束后、`if work.Watching` 块内
- import 增加 `plugins/watch`(无 import 环)

### D. 新路由与图片服务
- `api/controllers/controllerFile.go` 新增 `FolderPosterServer`:仿 `FileServer`(QueryUnescape 带回退、剥前导 `/`、Clean、拒 `..`)+ 图片扩展白名单(.jpg/.jpeg/.png/.webp)+ `Content-Type: image/*` + `Cache-Control: no-cache`
- `api/server.go` 409 行附近注册:`r.GET("/custom-poster/*path", controllers.FolderPosterServer)`(无 JWT,与 /file、/alist/proxy 先例一致)

### E. 前端
- `web/src/components/common.vue`:
  - `getPosterUrl(posterPath, videoId, customPoster)` 首行:`if (customPoster && customPoster.length > 0) return customPoster;`
  - `onPosterError(event, posterPath, customPoster)`:customPoster 存在且 posterPath 有效 → 回退 TMDB 远程;posterPath 无效 → 落 `/images/not_video.jpg`;保留 `_fallbackTried` 防循环
- 16 处调用点统一补第三参(`item.custom_poster`):
  - HelloWorld.vue(3 处)、VideoList.vue、VideoSearch.vue、VideoData.vue(3 处)、VideoPlayer.vue(2 处)、VideoSeason.vue、VideoPerson.vue(2 处,99 行保持 `item.Id || item.id`)、UserStar/UserPlayed/UserHeart.vue 各 1 处
  - `:src` 与 `@error` 同位置一起改;Person 类 item 无 custom_poster 字段时 undefined 自然跳过

## 零回归保障(核心约束)

现有功能全部保持原行为,改动面最小化:

1. **增量式改动**:新字段(空串不影响任何现有查询/更新)、新文件 foldercover.go、新路由 /custom-poster,均不触碰现有逻辑。
2. **对现有代码的全部修改**仅 3 类,每处都可独立回退:
   - 3 个调用点各插 1 行(检测失败只打日志,不影响刮削主流程;`url` 无 `/` 时跳过该文件,防切片越界)
   - controllerFile.go 新增 1 个函数 + server.go 新增 1 行路由
   - common.vue 两个函数加参数 + 16 个调用点补传 `item.custom_poster`
3. **前端向后兼容**:`customPoster` 为 undefined/空串时(`item` 来自未含新字段的接口也如此),`if (customPoster && ...)` 判 false → 走原有 posterPath → custom-image → 默认图链路,行为与改造前逐字节一致;onPosterError 第三参为空时回退 TMDB 逻辑不变,`_fallbackTried` 防循环保留。
4. **DB 写入安全**:仅 `Update("custom_poster", ...)` 单列更新,不触碰 Star/Heart/Played/PosterPath;原刮削海报永远保留在库中,前端优先级只是展示层选择;写入幂等,重复执行无副作用。
5. **检测失败不误伤**:ReadDir/alist 列表/登录失败 = "未知"状态,本轮不写不清,绝不出现"网络抖动把已有封面清掉"的回归。
6. **刮削主流程零风险**:检测在 wg.Wait 之后、检测函数内部所有错误路径都只记录日志并返回,不 panic、不阻塞刮削完成(work.IsOk 照常置位)。

## 时序与并发确认
- 检测必须在刮削 `wg.Wait()` 之后:`ChunkTheMovie` 是 `Select("*")` 全量更新,先写会被覆盖
- `Update("custom_poster", ...)` 单列更新不触碰 Star/Heart/Played/PosterPath,原刮削海报始终保留在 DB
- cron(6h/2:30)与手动重扫可并发:决策幂等,写入无副作用,不加新锁
- UseDefaultCover(跳过刮削)的 work 检测照常执行,文件夹封面仍生效

## 边界情况
- 中文/空格路径:DB 存原始路径,FolderPosterServer 仿 FileServer 解码;alist 侧 AlistProxy 不重复解码,行为与视频播放一致
- alist 登录/列表失败 → unknown 跳过;本地 ReadDir 失败 → 跳过,残留记录由 cleanupStaleRecords 清理
- 换图内容不改名:no-cache 缓解;alist 上游可能短暂陈旧,可接受

## 验证
1. `go build ./...`;`cd web && npm run build`
2. 本地端到端:`media-test/测试电影/` 放 `poster.jpg` + `a.mp4` → UI 建影库/加目录/创建任务 → 列表封面即 poster.jpg,F12 确认 `/custom-poster/...` 200;sqlite 查 custom_poster 字段值
3. 删 poster.jpg → renew/等待 cron → DB 清空、前端回退刮削海报或默认图
4. tv 场景:同目录 2 个剧集文件 + poster.jpg,验证基础记录与 Episode→Season→Tv 两条路径;多季目录验证"有图优先"
5. alist 场景:网盘目录放 poster.jpg → DB 值为 `/alist/proxy/...` → curl 验证 200
6. 回归:16 处调用点 grep 确认;无 custom_poster 的旧数据展示行为不变

## 明确不做(避免过度设计)
- 不做 per-work 开关(关闭方式 = 删图 + 重扫)
- 不新增定时任务(复用现有 cron)
- 不改 GetAlistFilesPath/AlistList 签名捎带图片
- 不做向上回溯父目录找封面、不做图片落盘缓存
- 不动 ChunkTheMovie/ChunkTheTv、不修 FileServer % 双重解码等既有问题
