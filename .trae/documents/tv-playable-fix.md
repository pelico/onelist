# TV 剧集模式播放链路修复实施计划

## Context(背景)

用户按 TV 类型添加媒体库(挂载剧集文件夹,使用默认封面跳过刮削),扫描正常、列表正常,但:
1. **点开任何剧集卡片 → 详情页无限转圈 + 空白提示,无法播放**
2. 列表一部一卡显示文件名(基础记录 name=文件名),不像"剧"

排查确认三个根因(前两个直接致用户当前问题):

- **根因 1:详情接口不带关联数据**。`/v1/api/thetv/id` 与 `/v1/api/theseason/id` 走泛型 `FindByID`(genericCRUD.go:77,仅 `Take` 不 Preload),全库无任何 `Preload("TheSeasons")`/`Preload("Episodes")` → 返回的 `the_seasons`/`episodes` 永远为 null → 前端 [VideoData.vue L406](file:///workspace/web/src/views/video/VideoData.vue#L406) `data.value.the_seasons[0].id` 抛 TypeError → `loading.value=false` 永不执行(无限转圈),错误对象进 ShowMsg(空图标提示)。**刮削成功的剧也一样崩**,TV 模式整体不可用。
- **根因 2:未刮削基础 TV 记录没有季/集、没有文件关联**。`CreateBasicTvRecord`(controllerWorks.go:61)只建一行 TheTv(name=文件名),不建 Episode → 即使详情页修好也无内容可播;TheTv 无 url 字段,文件路径没存进库。这也是此前"清理无剧集的电视记录"误删的前提。
- **根因 3:刮削转换断链+重复记录(现存 bug)**。[TheTvDb](file:///workspace/plugins/thedb/thedb.go#L476) 先 Chunk 季集(`TheTvID = data.ID` = TMDB id)后 ChunkTheTv;基础记录 ID 是自增值,ChunkTheTv 原地更新后剧记录 id ≠ TMDB id → 季集挂错 id,刮削成功的剧看不到季。且同一部剧每个文件各自转换自己的基础记录 → 产生多条同名重复 TheTv。

## 改动清单

### A. 详情接口补 Preload(根因 1)

- `api/repository/crud/repositoryTheTvsCrud.go`:override `FindByID(id string) (models.TheTv, error)`(遮蔽 embedded 泛型方法,模式同 Gallery 的 FindAll),实现:`db.Preload("TheSeasons").Preload("Genres").Preload("ThePersons").Where("id = ?", id).Take(&thetv)`
- `api/repository/crud/repositoryTheSeasonsCrud.go`:override `FindByID`,`Preload("Episodes")`
- 所有调用点自动生效(GetTheTvById、GetTheSeasonById、VideoSeason、播放器);接口声明不变

### B. 基础记录带默认季+集(根因 2)

`api/controllers/controllerWorks.go` `CreateBasicTvRecord`(L61):
- 创建 TheTv 后补建:`TheSeason{ID: 0(自增), TheTvID: tv.ID, Name: "第 1 季", SeasonNumber: 1}` + `Episode{ID: 0(自增), TheSeasonID: season.ID, Name: 文件名去扩展名, Url: file, EpisodeNumber: 1, SeasonNumber: 1}`
- **存量兼容**:同名记录已存在时,查 `the_seasons WHERE the_tv_id = ?` 计数,为 0 则补建默认季集 → 用户重扫一次(刷新目录、不开全量)即修复存量 72 条,无需迁移脚本
- 自增主键与 TMDB id 空间共存(TheTv 基础记录现状即如此,自增值始终高于现有最大 id,不冲突)

修复后各链路自然自洽(无需改动):
- 播放:详情页显示 1 季 1 集 → season_id+speed → `episodes[0].url` 播放;TV 连播用季集列表 ✓
- `cleanupStaleRecords`:基础记录有剧集 → `remainingEps > 0` → 不再误删 ✓
- `DeleteWorkById`(controllerWorks.go L364-387):删 work 时先删 episodes 再删无剧集的 TheTv,对默认季集同样成立 ✓

### C. 刮削转换顺序重排(根因 3)

`plugins/thedb/thedb.go`:

- `ChunkTheTv(thetv, file)` 签名改为 `(uint, error)`(唯一调用点在 TheTvDb 内部):
  1. 优先按 **TMDB 名字**找已刮削记录(同库)→ 命中则 Updates(保留状态)
  2. 按 **TMDB ID** 找(同库 → 他库)→ 命中则 Updates
  3. 按**文件名**找基础记录 → 命中则**先删该记录的全部 TheSeason/Season/Episode(此时真实季集尚未写入,删的就是默认季集)**,再 Updates
  4. 都没有 → Create
  5. 收尾:若 `file` 对应的同名基础记录仍存在且 ID ≠ 最终 ID → 删除该基础记录及其默认季集(文件由真实集承载,消除重复记录)
- `TheTvDb`(L476):将 `ChunkTheTv` 调用移到 Chunk 季集**之前**,`season.TheTvID`/`theseason.TheTvID` 改用返回的 finalTvID(替代 `data.ID`)→ 断链修复;`TheTvDb` 对外签名与返回值语义不变(controllerErrFiles L235/L399、controllerTheTvs L318 三个调用方无需改动)

### D. 前端防御

`web/src/views/video/VideoData.vue`:
- `fetchSeason`(L405):`the_seasons` 为空数组/null 时直接 return,不取 `[0]`
- `fetchData`(L432):`loading.value = false` 移到 fetchSeason 调用之前执行(保证任何异常下 spinner 都会停)

## 不做的事

- 不动 `cleanupStaleRecords` / `DeleteWorkById`(B 完成后自然自洽)
- 不做存量数据迁移脚本(靠"刷新目录"重建)
- 不动首页聚合、TV 连播、playstats

## 验证

1. `go build ./...`;`cd web && npm run build`
2. 部署后对现有 TV 库点"刷新目录"(不开全量重扫)→ 基础记录补上默认季集
3. 点剧集卡片 → 详情页正常渲染(genres/演职员/1 个"第 1 季"卡片)→ 点集 → 能播放;TV 连播正常
4. 对任一文件触发刮削(重刮/加视频)→ 详情显示真实季集;sqlite 验证:`the_seasons.the_tv_id` = 剧记录自增 id(断链修复)、同名重复 TheTv 记录被合并、默认季集已清
5. 回归:电影模式播放不受影响;清理逻辑对改名文件的行为不变
