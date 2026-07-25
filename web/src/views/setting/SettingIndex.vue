<template>
    <div v-if="load" class="load"></div>
    <div v-else class="content setting-content">
        <div class="setting-header">
            <div class="setting-title">
                系统设置
            </div>
        </div>
        <div class="setting-content-form">
            <n-form :model="config" label-placement="top" label-align="left" :label-width="140" :style="{
                maxWidth: '640px'
            }">
                <n-form-item label="影视库名字" path="title">
                    <n-input v-model:value="config.title" size="large" placeholder="请输入影视库名字" clearable />
                </n-form-item>
                <n-form-item label="影视库图标(Favicon URL)" path="faviconico_url">
                    <n-input v-model:value="config.faviconico_url" size="large" placeholder="图标 URL，例如 /favicon.ico" clearable />
                </n-form-item>
                <n-form-item label="是否下载刮削图片到本地" path="download_image">
                    <n-switch :value="downloadImageBool" @update:value="onDownloadImageChange" size="large">
                        <template #checked>是</template>
                        <template #unchecked>否</template>
                    </n-switch>
                    <span class="form-hint">开启后将把刮削到的图片保存到本地</span>
                </n-form-item>
                <n-form-item label="图片来源" path="img_url">
                    <n-input v-model:value="config.img_url" size="large" placeholder="留空使用本地缓存图片；否则使用 https://image.tmdb.org 等" clearable />
                </n-form-item>
                <n-form-item label="允许刮削的视频文件类型" path="video_types">
                    <n-input v-model:value="config.video_types" size="large" placeholder="例如 mp4,mkv,avi,rmvb" clearable />
                </n-form-item>
                <n-form-item label="TheMovieDb api 密匙" path="key_db">
                    <n-input v-model:value="config.key_db" size="large" type="password" show-password-on="click" placeholder="请输入 TheMovieDb api 密匙" clearable />
                </n-form-item>
                <n-button size="large" class="btn-save" @click="Save()" type="info" :loading="saving">
                    保存
                </n-button>
            </n-form>
        </div>
    </div>
</template>

<script>
import { computed, getCurrentInstance, onMounted, ref } from "vue";
export default {
    name: "SettingIndex",
    setup() {
        const config = ref({
            "title": null,
            "download_image": null,
            "img_url": null,
            "key_db": null,
            "faviconico_url": null,
            "video_types": null
        })
        const { proxy } = getCurrentInstance();
        const load = ref(true);
        const saving = ref(false);

        // download_image 字段在后端是字符串 "是"/"否"，前端用 switch 更直观
        const downloadImageBool = computed(() => config.value.download_image === "是");

        function onDownloadImageChange(val) {
            config.value.download_image = val ? "是" : "否";
        }

        function getConfig() {
            proxy.axios.post(proxy.COMMON.apiUrl + `/v1/api/config/data`, {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    config.value = res.data.data;
                    load.value = false;
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg)
                }
            }).catch((error) => {
                proxy.COMMON.ShowMsg(error);
                load.value = false;
            });
        }

        function saveConfig() {
            saving.value = true;
            proxy.axios.post(proxy.COMMON.apiUrl + `/v1/api/config/save`, config.value, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                saving.value = false;
                if (res.data.code == 200) {
                    config.value = res.data.data;
                    // 同步 localStorage，保证刷新后立即生效
                    if (config.value.title != null) {
                        localStorage.setItem("title", config.value.title);
                    }
                    if (config.value.img_url != null) {
                        localStorage.setItem("img_url", config.value.img_url);
                    }
                    // 热更新：直接更新内存配置并派发事件，无需 location.reload()
                    proxy.COMMON.applyConfig(config.value);
                    proxy.COMMON.ShowMsg("保存成功!")
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg)
                }
            }).catch((error) => {
                saving.value = false;
                proxy.COMMON.ShowMsg(error);
            });
        }
        onMounted(() => {
            getConfig();
        });
        const saveF = async () => {
            saveConfig();
        };

        return {
            config,
            load,
            saving,
            downloadImageBool,
            onDownloadImageChange,
            saveF
        }
    },
    methods: {
        Save() {
            this.saveF();
        }
    }
}
</script>

<style scoped>
.setting-header {
    margin-bottom: 20px;
    margin-top: 20px;
}

.setting-title {
    font-size: 1.4em;
}

.btn-save {
    width: 100%;
}

.form-hint {
    margin-left: 12px;
    color: #999;
    font-size: 0.85em;
}
</style>
