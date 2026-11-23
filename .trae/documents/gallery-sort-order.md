# 媒体库显示顺序调整(排序)实施计划

## Context(背景)

当前媒体库在首页横排、管理页等处的顺序由查询硬编码决定:首页 `Order("id desc")`(controllerHome.go:44)、管理页 `updated_at desc`(repositoryGallerysCrud.go:39-41)、用户侧列表 `Order("-ID")`(genericCRUD.go:69)。用户希望在前端能自己调整媒体库的显示顺序(如把常用库排前面)。

方案:Gallery 表加 `sort_num` 排序字段(AutoMigrate 自动加列),管理页每张卡片加"上移/下移"按钮(不用拖拽库——零新依赖、TV 遥控器友好),新顺序经 admin 接口批量写入,所有展示点统一按 `sort_num asc, id desc` 排序。

**零回归要点**:旧数据 sort_num 全为 0,`sort_num asc, id desc` 退化为 `id desc`,与现有顺序完全一致。

## 改动清单

### A. 模型(1 行)
- `api/models/Gallery.go`:UpdatedAt 前加 `SortNum int \`json:"sort_num"\``
- auto/load.go 已含 Gallery AutoMigrate,自动加列,无迁移代码

### B. 后端排序接口
- `api/repository/crud/repositoryGallerysCrud.go` 新增 `SortGallerys(ids []uint) error`:事务内按 ids 索引写 `sort_num = i`(第 0 个排最前);`api/repository/repositoryGallerys.go` 接口声明加一行
- `api/controllers/controllerGallerys.go` 新增 `SortGalleryByIds`:绑定 `{ids: [id...]}`,调 repo,返回成功
- `api/server.go` gallery 组(274-284 行)加一行:`gallery.POST("/sort", auth.JWTAuthAdmin(), controllers.SortGalleryByIds)`

### C. 排序生效点(全部统一为 `sort_num asc, id desc`,共 4 处)
| 位置 | 现状 | 用途 |
|---|---|---|
| api/controllers/controllerHome.go:44 | `Order("id desc")` | 首页"我的媒体"横排 |
| api/repository/crud/repositoryGallerysCrud.go:38-42 | `updated_at desc` | 管理页列表(排序预览) |
| api/controllers/controllerApp.go(gallery 查询处) | `updated_at desc` | App/TV 索引接口 |
| 用户侧 `/gallery/list`(genericCRUD.go:69 FindAll `Order("-ID")`) | 泛型方法,**不能直接改**(会影响其他表) |

处理第 4 处:在 `RepositoryGallerysCRUD` 上定义同名 `FindAll(page, size)` 方法遮蔽 embedded 泛型方法(Go 外层方法优先),签名与 repositoryGallerys.go 接口声明一致,内部按新排序查询。

### D. 创建行为(防打乱已排顺序)
- `CreateGallery`(controllerGallerys.go):创建时 `sort_num = MAX(sort_num)+1`(无记录则 0),新库排在已排序列表末尾;若用户从未排过序(全 0),仍按 id desc 显示,行为不变

### E. 前端管理页
- `web/src/api/index.js`:加 `export const sortGallery = (ids) => request.post('/v1/api/gallery/sort', { ids })`
- `web/src/views/gallery/GalleryIndex.vue`:
  - 每张卡片 header-extra(编辑按钮旁)加上移/下移两个圆按钮(`bx-up-arrow` / `bx-down-arrow`,boxicons 项目已有);首张禁用上移、末张禁用下移
  - `MoveUp/MoveDown(item, index)`:本地 `data` 数组换位 → 调 `sortGallery(新顺序 id 数组)` → 成功静默、失败 ShowMsg 并刷新回滚
  - admin/list 返回已含新字段 sort_num,编辑表单提交原样带回,不会被 UpdateByID 的 `Select("*")` 清零(GenericCRUD.UpdateByID 是全量更新,这是关键防清零点)

## 边界与不做的事
- 不做拖拽排序(避免引入 sortablejs 新依赖,TV 遥控器场景按钮更友好)
- 不做逐库单独排序接口,一次传全量 id 顺序,写入幂等
- controllerCleanup.go:22 与 playstats 的 galleries 查询无顺序语义,不动
- 首页 gallery_items(每库条目)与媒体库顺序无关,不动

## 验证
1. `go build ./...`;`cd web && npm run build`
2. 管理页:上移/下移某库 → 刷新页面顺序保持;首页"我的媒体"横排顺序同步变化
3. 回归:未排序(默认全 0)时首页/管理页顺序与改动前一致;编辑媒体库(改标题/封面)保存后 sort_num 不丢失
4. sqlite 查 galleries 表 sort_num 值与前端显示顺序一致
