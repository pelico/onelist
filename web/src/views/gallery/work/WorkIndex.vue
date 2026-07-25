<template>
    <div v-if="loading" class="load">
    </div>
    <div v-else class="content">
        <div class="content-header">
            <div class="content-header-title">
                挂载目录
            </div>
            <div class="content-header-tool">
                <n-space justify="end" size="medium">
                    <n-button @click="reF()" type="info">
                        <template #icon>
                            <i class='bx bx-analyse'></i>
                        </template>
                        刷新
                    </n-button>
                    <n-button @click="CreateShow()" type="info">
                        <template #icon>
                            <i class='bx bx-plus-circle'></i>
                        </template>
                        新增挂载
                    </n-button>
                    <!-- <n-button @click="reF()" strong secondary circle>
                        <i class='bx bx-analyse'></i>
                    </n-button>
                    <n-button @click="CreateShow()" strong secondary circle>
                        <i class='bx bx-plus-circle'></i>
                    </n-button> -->
                </n-space>
            </div>
        </div>
        <div class="work-list">
            <n-grid x-gap="12" y-gap="12" cols="1 s:2 m:2 l:4 xl:4 2xl:8" responsive="screen">
                <n-gi v-for="(item, index) in data" :key="index">
                    <n-card title="目录">
                        <template #header-extra>
                            <n-button @click="Editwork(item)" strong secondary circle>
                                <i class='bx bx-detail'></i>
                            </n-button>
                        </template>
                        <div class="progres-content">
                            <n-progress type="circle"
                                :percentage="getProgress(item)"
                                :indicator-placement="'label'"
                                :color="item.is_ok ? '#18a058' : '#2080f0'">
                                <template #default>
                                    <span style="font-size: 13px;">{{ getProgressText(item) }}</span>
                                </template>
                            </n-progress>
                        </div>
                        <template #footer>
                            <div class="work-tool">
                                <div class="work-data">
                                    <n-space justify="start" size="medium" vertical>
                                        <div class="work-type">
                                            挂载目录：{{ item.path }}
                                        </div>
                                        <div class="work-alist">
                                            已完成：{{ item.is_ok ? "是" : "否" }}
                                        </div>
                                        <div class="work-watching">
                                            监控中：{{ item.watching ? "是" : "否" }}
                                        </div>
                                    </n-space>
                                </div>
                                <n-space justify="end" size="medium">
                                    <n-button @click="showReNew(item)" type="info">
                                        <template #icon>
                                            <i class='bx bx-plus-circle'></i>
                                        </template>
                                        重新刮削
                                    </n-button>
                                </n-space>
                            </div>
                        </template>
                    </n-card>
                </n-gi>
            </n-grid>
        </div>
        <n-modal class="create" v-model:show="showModal" transform-origin="center">
            <n-card style="width: 600px" title="创建" :bordered="false" size="huge" role="dialog" aria-modal="true">
                <template #header-extra>
                    <n-button @click="showModal = !showModal" strong secondary circle>
                        <i class='bx bx-x'></i>
                    </n-button>
                </template>
                <n-spin :show="show">
                    <n-form :model="work">
                        <n-form-item label="目录">
                            <n-input-group>
                                <n-input @focus="handleFocus" size="large" v-model:value="work.path"
                                    placeholder="点击右侧「浏览选择」从网盘选择，或手动输入" clearable />
                                <n-button v-if="isAlist" size="large" type="primary" @click="openDirBrowser">
                                    <template #icon>
                                        <i class='bx bx-folder-open'></i>
                                    </template>
                                    浏览选择
                                </n-button>
                            </n-input-group>
                        </n-form-item>
                        <n-form-item label="是否强制刷新alist缓存后再获取文件?">
                            <n-switch size="large" v-model:value="work.is_ref" placeholder="" clearable />
                        </n-form-item>
                        <n-form-item label="是否监控目录,每天晚上2点自动扫描?">
                            <n-switch size="large" v-model:value="work.watching" placeholder="" clearable />
                        </n-form-item>
                    </n-form>
                </n-spin>
                <template #footer>
                    <n-space justify="end" size="medium">
                        <n-button @click="Create()" type="info">
                            <template #icon>
                                <i class='bx bx-save'></i>
                            </template>
                            创建
                        </n-button>
                    </n-space>
                </template>
            </n-card>
        </n-modal>
        <n-modal class="update" v-model:show="updateModal" transform-origin="center">
            <n-card style="width: 600px" title="更新" :bordered="false" size="huge" role="dialog" aria-modal="true">
                <template #header-extra>
                    <n-button @click="updateModal = !updateModal" strong secondary circle>
                        <i class='bx bx-x'></i>
                    </n-button>
                </template>
                <n-form :model="work">
                    <n-form-item label="目录">
                        <n-input size="large" v-model:value="work.path" placeholder="" clearable />
                    </n-form-item>
                    <n-form-item label="封面图片">
                        <n-input size="large" v-model:value="work.image" placeholder="" clearable />
                    </n-form-item>
                    <n-form-item label="是否强制刷新alist缓存后再获取文件?">
                        <n-switch size="large" v-model:value="work.is_ref" placeholder="" clearable />
                    </n-form-item>
                    <n-form-item label="是否监控目录，每天晚上2点自动扫描?">
                        <n-switch size="large" v-model:value="work.watching" placeholder="" clearable />
                    </n-form-item>
                </n-form>

                <template #footer>
                    <n-space justify="end" size="medium">
                        <n-button @click="Delete()" type="warning">
                            <template #icon>
                                <i class='bx bx-trash'></i>
                            </template>
                            删除
                        </n-button>
                        <n-button @click="Update()" type="info">
                            <template #icon>
                                <i class='bx bx-save'></i>
                            </template>
                            更新
                        </n-button>
                    </n-space>
                </template>
            </n-card>
        </n-modal>
        <n-modal class="renew" transform-origin="center" v-model:show="renewModal">
            <n-card style="width: 600px" title="确认" :bordered="false" size="huge" role="dialog" aria-modal="true">
                <template #header-extra>
                    <n-button @click="renewModal = !renewModal" strong secondary circle>
                        <i class='bx bx-x'></i>
                    </n-button>
                </template>
                <h3>确定重新刮削此挂载目录吗？</h3>
                <n-form-item label="是否全部重新刮削(默认只刮削挂载目录中新增文件)?">
                    <n-switch size="large" v-model:value="allFile" placeholder="" clearable />
                </n-form-item>
                <template #footer>
                    <n-space justify="end" size="medium">
                        <n-button @click="ReNewWork()" type="info">
                            <template #icon>
                                <i class='bx bx-check'></i>
                            </template>
                            确定
                        </n-button>
                    </n-space>
                </template>
            </n-card>
        </n-modal>
        <!-- 网盘目录浏览选择弹窗 -->
        <n-modal class="dir-browser" v-model:show="dirModal" transform-origin="center">
            <n-card style="width: 700px; max-height: 80vh;" title="选择挂载目录" :bordered="false" size="huge" role="dialog"
                aria-modal="true">
                <template #header-extra>
                    <n-button @click="dirModal = false" strong secondary circle>
                        <i class='bx bx-x'></i>
                    </n-button>
                </template>
                <n-spin :show="dirLoading">
                    <div
                        style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
                        <n-breadcrumb>
                            <n-breadcrumb-item v-for="(seg, i) in breadcrumb" :key="i" @click="loadDirByPath(seg.path)">
                                {{ seg.name }}
                            </n-breadcrumb-item>
                        </n-breadcrumb>
                        <n-button @click="selectCurrentDir" type="success" size="small">
                            选择当前目录
                        </n-button>
                    </div>
                    <div class="dir-list" style="max-height: 50vh; overflow-y: auto;">
                        <div v-if="dirList.length === 0 && !dirLoading"
                            style="text-align: center; padding: 20px; color: #999;">
                            该目录下没有子目录
                        </div>
                        <div v-for="(dir, index) in dirList" :key="index" class="dir-item">
                            <span class="dir-icon" @click="enterDir(dir)">
                                <i class='bx bx-folder'></i>
                            </span>
                            <span class="dir-name" @click="enterDir(dir)">{{ dir.name }}</span>
                            <n-button @click="selectDir(dir)" type="primary" size="tiny" style="margin-left: auto;">
                                选择此目录
                            </n-button>
                        </div>
                    </div>
                </n-spin>
                <template #footer>
                    <n-space justify="end">
                        <n-button @click="dirModal = false">关闭</n-button>
                    </n-space>
                </template>
            </n-card>
        </n-modal>
    </div>
</template>
<script>
import { useMessage } from 'naive-ui';
import { getCurrentInstance, onMounted, onUnmounted, ref } from "vue";
export default {
    name: 'WorkIndex',
    setup() {
        const loading = ref(true);
        const show = ref(false);
        const allFile = ref(false);
        const showModal = ref(false);
        const updateModal = ref(false);
        const renewModal = ref(false);
        const gallery_uid = ref(null);
        const isAlist = ref(false);
        const dirModal = ref(false);
        const dirLoading = ref(false);
        const dirList = ref([]);
        const currentPath = ref('/');
        const breadcrumb = ref([{ name: '根目录', path: '/' }]);
        const error = ref(null);
        const data = ref(null);
        const { proxy } = getCurrentInstance();
        const page = ref(1);
        const size = ref(24);
        gallery_uid.value = proxy.$route.query.gallery_uid;
        const message = useMessage()
        const work = ref({
            "id": null,
            "gallery_id": null,
            "gallery_uid": "",
            "image": "",
            "path": "",
            "file_number": 0,
            "speed": 0,
            "is_ok": false,
            "watching": false,
            "is_ref": false,
        })

        function fetchData() {
            proxy.axios.post(proxy.COMMON.apiUrl + '/v1/api/work/gallery/list?page=' + page.value + '&size=' + size.value + "&id=" + gallery_uid.value, {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    data.value = res.data.data;
                    loading.value = false;
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg)
                }
            }).catch((error) => {
                proxy.COMMON.ShowMsg(error);
            });
        }

        const reF = async () => {
            fetchData();
        };
        let autoRefreshTimer = null;
        function startAutoRefresh() {
            stopAutoRefresh();
            autoRefreshTimer = setInterval(() => {
                if (data.value && data.value.some(w => !w.is_ok)) {
                    fetchData();
                } else {
                    stopAutoRefresh();
                }
            }, 3000);
        }
        function stopAutoRefresh() {
            if (autoRefreshTimer) {
                clearInterval(autoRefreshTimer);
                autoRefreshTimer = null;
            }
        }
        onMounted(() => {
            fetchData();
            startAutoRefresh();
        });
        onUnmounted(() => {
            stopAutoRefresh();
        });
        return {
            allFile,
            data,
            error,
            message,
            loading,
            gallery_uid,
            showModal,
            page,
            work,
            show,
            size,
            updateModal,
            renewModal,
            isAlist,
            dirModal,
            dirLoading,
            dirList,
            currentPath,
            breadcrumb,
            reF,
            typeOptions: ["movie", "tv"].map(
                (v) => ({
                    label: v,
                    value: v
                })
            )
        }
    },
    methods: {
        // 计算进度百分比，防止 NaN
        getProgress(item) {
            if (!item.file_number || item.file_number === 0) {
                return 0;
            }
            let pct = (item.speed * 100 / item.file_number);
            if (isNaN(pct) || pct < 0) pct = 0;
            if (pct > 100) pct = 100;
            return Math.round(pct);
        },
        // 进度文字显示
        getProgressText(item) {
            if (item.is_ok) return '完成';
            if (!item.file_number || item.file_number === 0) return '等待中';
            return this.getProgress(item) + '%';
        },
        handleFocus() {
            this.message.info("比如'http://alist.cn/云盘/电影'就应该输入'/云盘/电影',或者是本地文件夹绝对路径", { duration: 8000 })
        },
        CreateShow() {
            this.work = {
                "id": null,
                "gallery_id": null,
                "gallery_uid": "",
                "image": "",
                "path": "",
                "file_number": 0,
                "speed": 0,
                "is_ok": false,
                "watching": false,
                "is_ref": false,
            };
            this.work.gallery_uid = this.gallery_uid
            this.checkAlist();
            this.showModal = !this.showModal;
        },
        // 判断当前媒体库是否为 Alist 类型，决定是否显示「浏览选择」
        checkAlist() {
            this.isAlist = false;
            this.axios.post(`${this.COMMON.apiUrl}/v1/api/gallery/host?id=${this.gallery_uid}`, {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': this.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    this.isAlist = !!(res.data.data && res.data.data.length > 0);
                }
            }).catch(() => {
                this.isAlist = false;
            });
        },
        // 打开网盘目录浏览
        openDirBrowser() {
            this.currentPath = '/';
            this.breadcrumb = [{ name: '根目录', path: '/' }];
            this.dirModal = true;
            this.loadDir('/');
        },
        // 加载目录列表
        loadDir(path) {
            this.dirLoading = true;
            this.currentPath = path;
            let api = `${this.COMMON.apiUrl}/v1/api/gallery/alist_dir?id=${this.gallery_uid}&path=${encodeURIComponent(path)}`;
            this.axios.get(api, {
                headers: {
                    'Authorization': this.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    this.dirList = res.data.data || [];
                } else {
                    this.COMMON.ShowMsg(res.data.msg);
                    this.dirList = [];
                }
                this.dirLoading = false;
            }).catch((error) => {
                this.COMMON.ShowMsg("加载目录失败: " + error);
                this.dirList = [];
                this.dirLoading = false;
            });
        },
        // 通过面包屑跳转
        loadDirByPath(path) {
            let idx = this.breadcrumb.findIndex(b => b.path === path);
            if (idx >= 0) {
                this.breadcrumb = this.breadcrumb.slice(0, idx + 1);
            }
            this.loadDir(path);
        },
        // 进入子目录
        enterDir(dir) {
            this.breadcrumb.push({ name: dir.name, path: dir.path });
            this.loadDir(dir.path);
        },
        // 选择某个子目录作为挂载目录
        selectDir(dir) {
            this.work.path = dir.path;
            this.dirModal = false;
        },
        // 选择当前所在目录作为挂载目录
        selectCurrentDir() {
            this.work.path = this.currentPath;
            this.dirModal = false;
        },
        Editwork(work) {
            this.work = work;
            this.updateModal = !this.updateModal;
        },
        showReNew(work) {
            this.work = work;
            this.renewModal = !this.renewModal;
        },
        ReNewWork() {
            let mod = "new";
            if (this.allFile) {
                mod = "all";
            }
            this.Request(this.COMMON.apiUrl + '/v1/api/work/renew?id=' + this.work.id + "&mod=" + mod, {})
        },
        Request(api, data) {
            this.axios.post(api, data, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': this.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    this.COMMON.ShowMsg(res.data.msg)
                    this.showModal = false;
                    this.updateModal = false;
                    this.renewModal = false;
                    this.reF();
                } else {
                    this.COMMON.ShowMsg(res.data.msg)
                }
            }).catch((error) => {
                this.COMMON.ShowMsg(error);
            });
        },
        Create() {
            if (this.work.work_type == "tv") {
                this.work.is_tv = true;
            }
            let api = this.COMMON.apiUrl + '/v1/api/work/create';
            this.axios.post(api, this.work, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': this.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    this.COMMON.ShowMsg(res.data.msg)
                    this.showModal = false;
                    this.reF();
                    startAutoRefresh();
                } else {
                    this.COMMON.ShowMsg(res.data.msg)
                }
            }).catch((error) => {
                this.COMMON.ShowMsg(error);
            });
        },
        Update() {
            this.Request(this.COMMON.apiUrl + '/v1/api/work/update?id=' + this.work.id, this.work)
        },
        Delete() {
            this.Request(this.COMMON.apiUrl + '/v1/api/work/delete?id=' + this.work.id, {})
        },

    }
}
</script >

<style scoped>
.content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    min-height: 60px;
    margin-bottom: 12px;
}

.content-header-title {
    font-size: 1.4em;
}

.work-data {
    font-size: 18px;
}

.work-img {
    width: 100%;
}

.progres-content {
    text-align: center;
}

.dir-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    border-radius: 6px;
    cursor: pointer;
    transition: background 0.2s;
}

.dir-item:hover {
    background: #f5f7fa;
}

.dir-icon {
    font-size: 20px;
    color: #e6a23c;
}

.dir-name {
    flex: 1;
    font-size: 14px;
}
</style>