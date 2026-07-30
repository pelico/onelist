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
                <n-form-item label="封面来源" path="img_url">
                    <n-input v-model:value="config.img_url" size="large" placeholder="留空使用本地缓存；否则使用 https://image.tmdb.org 等远程CDN地址" clearable />
                    <span class="form-hint">封面即海报图片，留空时会读取本地缓存目录 images/ 下的文件</span>
                </n-form-item>
                <n-form-item label="是否下载封面到本地" path="download_image">
                    <n-switch :value="downloadImageBool" @update:value="onDownloadImageChange" size="large">
                        <template #checked>是</template>
                        <template #unchecked>否</template>
                    </n-switch>
                    <span class="form-hint">开启后将封面下载到本地缓存目录</span>
                </n-form-item>
                <n-form-item label="是否将封面保存到媒体目录" path="download_image_to_media">
                    <n-switch :value="downloadImageToMediaBool" @update:value="onDownloadImageToMediaChange" size="large">
                        <template #checked>是</template>
                        <template #unchecked>否</template>
                    </n-switch>
                    <span class="form-hint">开启后封面、描述信息等将保存到影片所在目录，其他媒体软件(如Emby/Jellyfin)扫描时可识别</span>
                </n-form-item>
                <n-form-item label="允许刮削的视频文件类型" path="video_types">
                    <n-input v-model:value="config.video_types" size="large" placeholder="例如 mp4,mkv,avi,rmvb" clearable />
                </n-form-item>
                <n-form-item label="TheMovieDb API 地址" path="themoviedb_api_url">
                    <n-input v-model:value="config.themoviedb_api_url" size="large" placeholder="例如 https://api.themoviedb.org/3" clearable />
                    <span class="form-hint">可填写镜像或反代地址解决网络超时问题</span>
                </n-form-item>
                <n-form-item label="TheMovieDb api 密匙" path="key_db">
                    <n-input v-model:value="config.key_db" size="large" type="password" show-password-on="click" placeholder="请输入 TheMovieDb api 密匙" clearable />
                </n-form-item>
                <n-form-item label="日志保留天数" path="log_retention_days">
                    <n-input-number v-model:value="logRetentionDaysNum" size="large" :min="1" :max="365" placeholder="默认 7 天" style="width: 100%" />
                    <span class="form-hint">超过该天数的日志将被自动清理（重启服务后生效）</span>
                </n-form-item>
                <n-form-item label="强制启用遥控器模式" path="force_tv_mode">
                    <n-switch :value="forceTvMode" @update:value="onForceTvModeChange" size="large">
                        <template #checked>是</template>
                        <template #unchecked>否</template>
                    </n-switch>
                    <span class="form-hint">开启后强制启用电视遥控器导航模式，适合 Android TV / 电视盒子等无触摸设备。也可在访问地址后加 ?tv=1 临时启用。</span>
                </n-form-item>
                <n-form-item label="自定义默认封面" path="custom_default_image">
                    <n-switch :value="customDefaultImageBool" @update:value="onCustomDefaultImageChange" size="large">
                        <template #checked>是</template>
                        <template #unchecked>否</template>
                    </n-switch>
                    <span class="form-hint">开启后，未刮削海报的影片将使用 /config/picture 目录下的图片作为封面。将图片放入该目录，会均匀分配到各影片上。目录为空则使用内置默认图。</span>
                </n-form-item>
                <n-divider>护眼屏保</n-divider>
                <n-form-item label="启用护眼屏保" path="screensaver_enabled">
                    <n-switch :value="screensaverEnabledBool" @update:value="onScreensaverEnabledChange" size="large">
                        <template #checked>是</template>
                        <template #unchecked>否</template>
                    </n-switch>
                    <span class="form-hint">关闭后管理员账号不受屏保限制，非管理员账号始终开启</span>
                </n-form-item>
                <n-form-item label="连续播放时长（分钟）" path="screensaver_play_duration">
                    <n-input-number v-model:value="screensaverPlayDurationMin" size="large" :min="1" :max="600" placeholder="默认 60 分钟" style="width: 100%" />
                    <span class="form-hint">连续播放达到此时长后触发屏保休息</span>
                </n-form-item>
                <n-form-item label="屏保休息时长（秒）" path="screensaver_duration">
                    <n-input-number v-model:value="screensaverDurationSec" size="large" :min="10" :max="3600" placeholder="默认 180 秒" style="width: 100%" />
                    <span class="form-hint">屏保展示多久后自动恢复播放</span>
                </n-form-item>
                <n-form-item label="每日最大播放时长（小时）" path="screensaver_daily_limit">
                    <n-input-number v-model:value="screensaverDailyLimitHour" size="large" :min="0.5" :max="24" :step="0.5" placeholder="默认 2 小时" style="width: 100%" />
                    <span class="form-hint">当天累计播放超过此时长后锁定播放，次日自动解锁</span>
                </n-form-item>
                <div class="screensaver-hint">
                    屏保素材：将视频、图片或 HTML 文件放入 <code>wallpaper/</code> 目录，屏保启动时随机展示。支持 .mp4/.webm/.mov、.jpg/.png/.gif、.html 等格式。
                </div>
                <n-button size="large" class="btn-save" @click="Save()" type="info" :loading="saving">
                    保存
                </n-button>
            </n-form>
            <n-divider />
            <div class="cleanup-section">
                <div class="cleanup-title">媒体库维护</div>
                <n-popconfirm @positive-click="cleanupLibrary" :disabled="cleaning">
                    <template #trigger>
                        <n-button size="large" type="warning" :loading="cleaning">
                            一键清理媒体库（清除失效/重复记录）
                        </n-button>
                    </template>
                    该操作会扫描所有媒体库，删除文件已不存在的记录，并合并同名重复记录。可能需要一些时间，是否继续？
                </n-popconfirm>
            </div>
            <n-divider />
            <div class="about-section">
                <div class="about-title">关于</div>
                <div class="about-version">{{ versionInfo }}</div>
            </div>
        </div>
    </div>
</template>

<script>
import { computed, getCurrentInstance, onMounted, ref } from "vue";
import { tvNavigation } from "../../plugins/tvNavigation";
export default {
    name: "SettingIndex",
    setup() {
        const config = ref({
            "title": null,
            "download_image": null,
            "download_image_to_media": null,
            "img_url": null,
            "themoviedb_api_url": null,
            "key_db": null,
            "faviconico_url": null,
            "video_types": null,
            "log_retention_days": null,
            "custom_default_image": null,
            "screensaver_enabled": null,
            "screensaver_play_duration": null,
            "screensaver_duration": null,
            "screensaver_daily_limit": null
        })
        const { proxy } = getCurrentInstance();
        const load = ref(true);
        const saving = ref(false);
        const cleaning = ref(false);
        const versionInfo = ref("v1.0 @2026 Optimized by wanchuan");
        const forceTvMode = ref(localStorage.getItem('forceTvMode') !== 'false');

        const downloadImageBool = computed(() => config.value.download_image === "是");
        const downloadImageToMediaBool = computed(() => config.value.download_image_to_media === "是");
        const customDefaultImageBool = computed(() => config.value.custom_default_image === "是");

        const screensaverEnabledBool = computed(() => config.value.screensaver_enabled === "是");

        // 后端存储秒，前端显示分钟
        const screensaverPlayDurationMin = computed({
            get: () => {
                const n = parseInt(config.value.screensaver_play_duration);
                return isNaN(n) || n <= 0 ? 60 : Math.round(n / 60);
            },
            set: (val) => {
                config.value.screensaver_play_duration = val != null ? String(Math.round(val * 60)) : "";
            }
        });

        // 后端存储秒，前端显示秒
        const screensaverDurationSec = computed({
            get: () => {
                const n = parseInt(config.value.screensaver_duration);
                return isNaN(n) || n <= 0 ? 180 : n;
            },
            set: (val) => {
                config.value.screensaver_duration = val != null ? String(val) : "";
            }
        });

        // 后端存储秒，前端显示小时
        const screensaverDailyLimitHour = computed({
            get: () => {
                const n = parseInt(config.value.screensaver_daily_limit);
                return isNaN(n) || n <= 0 ? 7200 : n / 3600;
            },
            set: (val) => {
                config.value.screensaver_daily_limit = val != null ? String(Math.round(val * 3600)) : "";
            }
        });

        const logRetentionDaysNum = computed({
            get: () => {
                const n = parseInt(config.value.log_retention_days);
                return isNaN(n) || n <= 0 ? 7 : n;
            },
            set: (val) => {
                config.value.log_retention_days = val != null ? String(val) : "";
            }
        });

        function onDownloadImageChange(val) {
            config.value.download_image = val ? "是" : "否";
        }

        function onDownloadImageToMediaChange(val) {
            config.value.download_image_to_media = val ? "是" : "否";
        }

        function onCustomDefaultImageChange(val) {
            config.value.custom_default_image = val ? "是" : "否";
        }

        function onScreensaverEnabledChange(val) {
            config.value.screensaver_enabled = val ? "是" : "否";
        }

        function onForceTvModeChange(val) {
            forceTvMode.value = val;
            tvNavigation.setTvMode(val);
            proxy.COMMON.ShowMsg(val ? '已启用遥控器模式' : '已关闭遥控器模式');
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
                    if (config.value.custom_default_image != null) {
                        localStorage.setItem("custom_default_image", config.value.custom_default_image);
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

        function cleanupLibrary() {
            cleaning.value = true;
            proxy.axios.post(proxy.COMMON.apiUrl + '/v1/api/system/cleanup', {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                cleaning.value = false;
                proxy.COMMON.ShowMsg(res.data.msg || "清理完成");
            }).catch((error) => {
                cleaning.value = false;
                proxy.COMMON.ShowMsg(error);
            });
        }

        return {
            config,
            load,
            saving,
            cleaning,
            versionInfo,
            forceTvMode,
            downloadImageBool,
            downloadImageToMediaBool,
            customDefaultImageBool,
            screensaverEnabledBool,
            screensaverPlayDurationMin,
            screensaverDurationSec,
            screensaverDailyLimitHour,
            logRetentionDaysNum,
            onDownloadImageChange,
            onDownloadImageToMediaChange,
            onCustomDefaultImageChange,
            onScreensaverEnabledChange,
            onForceTvModeChange,
            saveF,
            cleanupLibrary
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

.screensaver-hint {
    color: #999;
    font-size: 0.85em;
    margin-bottom: 16px;
    line-height: 1.6;
}

.screensaver-hint code {
    background: rgba(255, 255, 255, 0.08);
    padding: 1px 6px;
    border-radius: 3px;
    font-size: 0.95em;
}

.cleanup-section {
    max-width: 640px;
}

.cleanup-title {
    font-size: 1.1em;
    font-weight: 500;
    margin-bottom: 12px;
}

.about-section {
    max-width: 640px;
    color: #999;
}

.about-title {
    font-size: 1.1em;
    font-weight: 500;
    margin-bottom: 8px;
    color: inherit;
}

.about-version {
    font-size: 0.95em;
}

:deep(.n-input-number .n-input-wrapper input) {
    padding-right: 36px !important;
}
</style>
