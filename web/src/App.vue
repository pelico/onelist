<template>
    <div v-if="load" class="load"></div>
    <n-config-provider preflight-style-disabled=true :theme="theme" v-else>
        <n-message-provider>
                <MessageBridge />
                <n-notification-provider>
                <n-dialog-provider>
                    <n-layout v-if="login" :class='[dark ? "dark" : "light", "home"]'>
                        <n-layout-header bordered>
                            <div class="header-content">
                                <n-space>
                                    <div @click="toggDrawer" class="menu-toggle-btn">
                                        <n-button circle>
                                            <i class='bx bx-menu'></i>
                                        </n-button>
                                    </div>
                                    <div @click="Home" class="title header-title">
                                        {{ title }}
                                    </div>
                                </n-space>
                                <n-space justify="end">
                                    <n-button quaternary @click="toggDark()" circle>
                                        <template #icon>
                                            <i v-if="dark" class='bx bx-sun'></i>
                                            <i v-else class='bx bx-moon'></i>
                                        </template>
                                    </n-button>
                                    <n-button @click="(showSaerch = !showSaerch)" circle>
                                        <template #icon>
                                            <i class='bx bx-search'></i>
                                        </template>
                                    </n-button>

                                    <n-dropdown trigger="hover" placement="bottom-start" :options="options"
                                        @select="handleSelect">
                                        <n-avatar circle size="medium"
                                            src="https://wework.qpic.cn/wwpic/622138_d-QTzJ_oQAyVDjO_1656146831/0">

                                        </n-avatar>
                                    </n-dropdown>
                                </n-space>
                            </div>
                        </n-layout-header>
                        <n-layout position="absolute" style="top: 60px; bottom: 0" has-sider>
                            <n-layout-sider :collapsed="collapsed" collapse-mode="width" :collapsed-width="0" :width="240"
                                :native-scrollbar="false" bordered>
                                <div class="sider-item">
                                    <div class="navigation">
                                        <ul class="nav-links">
                                            <li>
                                                <router-link to="/">
                                                    <span class="icon">
                                                        <i class='bx bx-home'></i>
                                                    </span>
                                                    <span class="title">主页</span>
                                                </router-link>
                                            </li>
                                            <li>
                                                <router-link to="/heart">
                                                    <span class="icon">
                                                        <i class='bx bx-heart'></i>
                                                    </span>
                                                    <span class="title">最爱</span>
                                                </router-link>
                                            </li>
                                            <li>
                                                <router-link to="/played">
                                                    <span class="icon">
                                                        <i class='bx bx-detail'></i>
                                                    </span>
                                                    <span class="title">已播放</span>
                                                </router-link>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div class="sider-item gallery-list">
                                    <div class="sider-item-title">媒体库</div>
                                    <div class="navigation more">
                                        <ul class="nav-links">
                                            <li v-for="(item, index) in data" :key="index">
                                                <router-link :to="{
                                                    path: '/list', query: {
                                                        gallery_uid: item.gallery_uid,
                                                        gallery_type: item.gallery_type
                                                    }
                                                }">
                                                    <span v-if="item.gallery_type == 'movie'" class="icon">
                                                        <i class='bx bxs-movie'></i>
                                                    </span>
                                                    <span v-else class="icon">
                                                        <i class='bx bx-desktop'></i>
                                                    </span>
                                                    <span :data-id="item.gallery_uid" class="title">{{ item.title }}</span>
                                                </router-link>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div v-show="is_admin" class="sider-item">
                            <div class="sider-item-title">管理</div>
                            <div class="navigation">
                                <ul class="nav-links">
                                    <li>
                                        <router-link to="/gallerys">
                                            <span class="icon">
                                                <i class='bx bxs-grid'></i>
                                            </span>
                                            <span class="title">媒体中心</span>
                                        </router-link>
                                    </li>
                                    <li>
                                        <router-link to="/setting">
                                            <span class="icon">
                                                <i class='bx bx-cog'></i>
                                            </span>
                                            <span class="title">系统设置</span>
                                        </router-link>
                                    </li>
                                    <li>
                                        <router-link to="/logs">
                                            <span class="icon">
                                                <i class='bx bx-file-find'></i>
                                            </span>
                                            <span class="title">系统日志</span>
                                        </router-link>
                                    </li>
                                    <li>
                                        <router-link to="/users">
                                            <span class="icon">
                                                <i class='bx bx-user'></i>
                                            </span>
                                            <span class="title">用户管理</span>
                                        </router-link>
                                    </li>
                                </ul>
                            </div>
                        </div>
                        <div class="desktop-footer">
                            <div class="version-info">{{ versionInfo }}</div>
                        </div>
                    </n-layout-sider>
                            <n-layout :native-scrollbar="false">
                                <router-view v-on:refApp="RefAppData()" />
                            </n-layout>
                        </n-layout>
                        <!-- 移动端底部导航栏 -->
                        <div class="mobile-bottom-nav">
                            <router-link to="/" class="bottom-nav-item">
                                <i class='bx bx-home'></i>
                                <span>主页</span>
                            </router-link>
                            <div class="bottom-nav-item" @click="showSaerch = !showSaerch">
                                <i class='bx bx-search'></i>
                                <span>搜索</span>
                            </div>
                            <router-link to="/heart" class="bottom-nav-item">
                                <i class='bx bx-heart'></i>
                                <span>最爱</span>
                            </router-link>
                            <router-link to="/played" class="bottom-nav-item">
                                <i class='bx bx-detail'></i>
                                <span>已播</span>
                            </router-link>
                            <router-link v-if="is_admin" to="/setting" class="bottom-nav-item">
                                <i class='bx bx-cog'></i>
                                <span>设置</span>
                            </router-link>
                        </div>

                        <!-- 搜索弹窗（增强版：带实时建议） -->
                        <n-modal v-model:show="showSaerch" transform-origin="center">
                            <n-card style="width: 600px; max-width: 90vw" title="搜索" :bordered="false" size="huge" role="dialog"
                                aria-modal="true">
                                <template #header-extra>
                                    <n-button @click="(showSaerch = !showSaerch)" strong secondary circle>
                                        <i class='bx bx-x'></i>
                                    </n-button>
                                </template>
                                <n-auto-complete
                                    ref="searchRef"
                                    v-model:value="q"
                                    :input-props="{ autocomplete: 'off' }"
                                    :options="searchOptions"
                                    placeholder="输入影片名称..."
                                    clearable
                                    size="large"
                                    @select="onSearchSelect"
                                    @keyup.enter="Search()">
                                    <template #prefix>
                                        <i class='bx bx-search'></i>
                                    </template>
                                </n-auto-complete>
                            </n-card>
                        </n-modal>
                    </n-layout>
                    <div v-else>
                        <Login @is-login="LoginUser()" :login="login" :title="title"></Login>
                    </div>
                </n-dialog-provider>
            </n-notification-provider>
        </n-message-provider>
    </n-config-provider>
</template>

<script>
import { darkTheme, useMessage } from 'naive-ui';
import { defineComponent, getCurrentInstance, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import Login from './components/Login';
import global from './components/common.vue';
import { tvNavigation } from './plugins/tvNavigation';

// 消息桥接组件：必须渲染在 n-message-provider 内部才能正确获取 message API
// （App 组件本身渲染 n-message-provider，因此在 App 的 setup 中调用 useMessage() 找不到 provider）
const MessageBridge = defineComponent({
    name: 'MessageBridge',
    setup() {
        const message = useMessage();
        global.setMsgHandler((msg) => {
            // 弹新消息前先清掉旧的，避免错误消息堆叠成截图里那种长条
            try { message.destroyAll(); } catch (e) { /* ignore */ }
            message.info(msg, { duration: 3000 });
        });
        return () => null;
    }
});

export default defineComponent({
    name: 'App',
    components: {
        Login,
        MessageBridge,
    },
    setup() {
        const dark = ref(false);
        const theme = ref(null);
        const data = ref(null);
        const collapsed = ref(false);
        const isMo = /Android|webOS|iPhone|iPad|iPod|BlackBerry/i.test(navigator.userAgent);
        if (isMo) {
            collapsed.value = true;
        }
        const load = ref(true);
        const login = ref(false);
        const is_admin = ref(false);
        const showDrawer = ref(false);
        const showIcon = ref(false);
        const showSaerch = ref(false);
        const title = ref(null);
        const q = ref(null);
        const versionInfo = ref("v1.0 @2026 Optimized by wanchuan");
        const { proxy } = getCurrentInstance();
        title.value = proxy.COMMON.title;
        document.title = title.value;

        // 搜索建议
        const searchOptions = ref([]);
        let searchTimer = null;
        const searchRef = ref(null);

        // 搜索弹窗打开时自动聚焦输入框，TV 模式下注册焦点组
        watch(showSaerch, (val) => {
            if (val) {
                setTimeout(() => {
                    searchRef.value?.focus?.();
                    if (tvNavigation.isTvMode) {
                        registerSearchNav();
                    }
                }, 100);
            } else {
                tvNavigation.unregisterGroup('search');
                if (tvNavigation.isTvMode) {
                    registerSidebarNav();
                }
            }
        });

        const them = proxy.$cookies.get("dark");
        const collapsedItem = proxy.$cookies.get("collapsed");
        if (them == "true") {
            dark.value = true;
            theme.value = darkTheme;
        }
        if (collapsedItem == "true") {
            collapsed.value = true;
        }

        function initUrlActive() {
            let sider_items = document.querySelectorAll(".sider-item a");
            sider_items.forEach(event => {
                sider_items.forEach(event => {
                    event.classList.remove('active')
                });
                event.addEventListener('click', () => {
                    event.classList.add('active');
                })
            })
        }

        function getConfig() {
            proxy.axios.get(proxy.COMMON.apiUrl + `/v1/api/configs`).then(res => {
                if (res.data.code == 200) {
                    let config = res.data.data;
                    localStorage.setItem("title", config.title);
                    localStorage.setItem("img_url", config.img_url);
                    // 同步自定义封面等配置到 localStorage 和内存状态
                    proxy.COMMON.applyConfig(config);
                    let link = config.faviconico_url;
                    let favicon = document.querySelector('link[rel="icon"]');
                    if (favicon !== null) {
                        favicon.href = link;
                    } else {
                        favicon = document.createElement("link");
                        favicon.rel = "icon";
                        favicon.href = link;
                        document.head.appendChild(favicon);
                    }
                    getUserData()
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg)
                }
            }).catch((error) => {
                proxy.COMMON.ShowMsg(error);
                load.value = false;
            });
        }

        function registerSidebarNav() {
            nextTick(() => {
                const links = document.querySelectorAll('.sider-item .nav-links li a');
                if (links.length === 0) return;
                tvNavigation.registerGroup('sidebar', Array.from(links), { vertical: true, wrap: true });
                // 仅在 TV 模式且页面尚无焦点时，将焦点落到侧边栏第一项
                if (tvNavigation.isTvMode && !tvNavigation.currentFocus) {
                    tvNavigation.setCurrentGroup('sidebar');
                }
            });
        }

        function registerSearchNav() {
            nextTick(() => {
                const input = document.querySelector('.n-auto-complete input');
                const closeBtn = document.querySelector('.n-card-header-extra button');
                const items = [];
                if (input) { input.setAttribute('tabindex', '0'); items.push(input); }
                if (closeBtn) { closeBtn.setAttribute('tabindex', '0'); items.push(closeBtn); }
                // 下拉建议项
                document.querySelectorAll('.n-base-select-option').forEach((opt, idx) => {
                    opt.setAttribute('tabindex', '0');
                    items.push(opt);
                });
                if (items.length > 0) {
                    tvNavigation.registerGroup('search', items, { vertical: true, wrap: false });
                    tvNavigation.setCurrentGroup('search');
                }
            });
        }

        function getData() {
            proxy.axios.post(proxy.COMMON.apiUrl + `/v1/api/gallery/list`, {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    data.value = res.data.data;
                    setTimeout(() => {
                        initUrlActive();
                        registerSidebarNav();
                    }, 1500);
                    load.value = false;
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg)
                }
            }).catch((error) => {
                proxy.COMMON.ShowMsg(error);
                load.value = false;
            });
        }

        function getUserData() {
            if (proxy.$cookies.get("Authorization") != null) {
                proxy.axios.get(proxy.COMMON.apiUrl + `/v1/api/user/data`, {
                    headers: {
                        'content-type': 'application/json',
                        'Authorization': proxy.$cookies.get("Authorization")
                    }
                }).then(res => {

                    if (res.data.code == 200) {
                        login.value = true;
                        is_admin.value = res.data.data.is_admin;
                        proxy.$cookies.set('is_admin', is_admin.value, 60 * 60 * 24 * 7);
                        getData();
                    } else {
                        proxy.COMMON.ShowMsg(res.data.msg)
                    }
                }).catch((error) => {
                    proxy.COMMON.ShowMsg("登录已过期,请重新登录!")
                    load.value = false;
                });
            } else {
                load.value = false;
            }
        }
        const reF = async () => {
            getConfig()
        };
        const reFApp = async () => {
            getData()
        };

        // 搜索建议：输入时防抖请求
        watch(q, (newVal) => {
            if (searchTimer) clearTimeout(searchTimer);
            if (!newVal || newVal.trim().length < 1) {
                searchOptions.value = [];
                return;
            }
            searchTimer = setTimeout(() => {
                proxy.axios.post(proxy.COMMON.apiUrl + '/v1/api/themovie/search?q=' + encodeURIComponent(newVal), {}, {
                    headers: { 'Authorization': proxy.$cookies.get("Authorization") }
                }).then(res => {
                    if (res.data.code == 200 && res.data.data) {
                        searchOptions.value = res.data.data.slice(0, 8).map(item => ({
                            label: item.title || item.name,
                            value: item.title || item.name
                        }));
                    }
                }).catch(() => {});
                // 同时搜索电视剧
                proxy.axios.post(proxy.COMMON.apiUrl + '/v1/api/thetv/search?q=' + encodeURIComponent(newVal), {}, {
                    headers: { 'Authorization': proxy.$cookies.get("Authorization") }
                }).then(res => {
                    if (res.data.code == 200 && res.data.data) {
                        let tvOpts = res.data.data.slice(0, 5).map(item => ({
                            label: item.name,
                            value: item.name
                        }));
                        searchOptions.value = [...searchOptions.value, ...tvOpts];
                    }
                }).catch(() => {});
            }, 400);
        });

        // 选中建议项时跳转搜索
        function onSearchSelect(value) {
            if (value) {
                q.value = value;
                Search();
            }
        }

        // 配置热更新：监听 common.vue 派发的事件，同步响应式状态
        function onConfigChanged(e) {
            const cfg = e && e.detail;
            if (!cfg) return;
            if (cfg.title != null) {
                title.value = cfg.title;
                document.title = cfg.title;
            }
            if (cfg.faviconico_url != null && cfg.faviconico_url !== '') {
                let favicon = document.querySelector('link[rel="icon"]');
                if (favicon !== null) {
                    favicon.href = cfg.faviconico_url;
                } else {
                    favicon = document.createElement("link");
                    favicon.rel = "icon";
                    favicon.href = cfg.faviconico_url;
                    document.head.appendChild(favicon);
                }
            }
        }

        onMounted(() => {
            getConfig();
            window.addEventListener('onelist:config-changed', onConfigChanged);
            // 兜底：即使数据加载异常，也尝试注册固定导航项
            setTimeout(() => {
                registerSidebarNav();
            }, 2000);
        });
        onUnmounted(() => {
            window.removeEventListener('onelist:config-changed', onConfigChanged);
        });
        return {
            dark,
            collapsed,
            title,
            versionInfo,
            load,
            is_admin,
            showIcon,
            showDrawer,
            login,
            darkTheme,
            theme,
            data,
            showSaerch,
            searchRef,
            searchOptions,
            onSearchSelect,
            q,
            reF,
            reFApp,
            options: [
                {
                    label: '注销登录',
                    key: "out"
                },
            ],
            handleSelect(key) {
                if (key == "out") {
                    proxy.$cookies.remove("Authorization");
                    proxy.$cookies.remove("is_admin");
                    proxy.COMMON.ShowMsg("注销登录成功！")
                    setTimeout(function () {
                        location.reload();
                    }, 1000)
                }
            },
        }
    },
    methods: {
        Search() {
            this.$router.push({
                path: "/search",
                query: {
                    q: this.q
                }
            })
        },
        Home() {
            this.$router.push({
                path: "/",
            })
        },
        toggDrawer() {
            this.collapsed = !this.collapsed;
            this.$cookies.set("collapsed", this.collapsed);
        },
        toggDark() {
            if (this.theme == null) {
                this.theme = darkTheme;
                this.$cookies.set("dark", "true", 60 * 60 * 24 * 30);
                this.dark = true;
                return
            }
            if (this.theme.name == "dark") {
                this.theme = null;
                this.$cookies.set("dark", "false", 60 * 60 * 24 * 30);
                this.dark = false;
            } else {
                this.theme = darkTheme;
                this.$cookies.set("dark", "true", 60 * 60 * 24 * 30);
                this.dark = true;
            }
        },
        LoginUser() {
            this.login = !this.login;
            this.reF();
        },
        LoginOut() {
            this.$cookies.remove("Authorization");
            this.$cookies.remove("is_admin");
            this.login = false;
        },
        RefAppData() {
            this.reFApp();
        }
    }
})
</script>

<style>
body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    overflow: auto;
    overflow-x: hidden;
    background-position: top left;
}

p {
    margin: 0;
    padding: 0;
}

ul li .title,
.sider-item-title {
    white-space: nowrap;
    font-size: 1.5em;
}


.load {
    position: absolute;
    top: 50%;
    left: 50%;
    border: 4px solid #2d8cf0;
    border-radius: 50%;
    border-bottom-color: transparent;
    z-index: 1;
    height: 100px;
    width: 100px;
    display: inline-block;
    animation: spin 1s linear infinite;
    margin-left: -50px;
    margin-top: -50px;
}

@keyframes spin {
    0% {
        transform: rotate(0deg);
    }

    100% {
        transform: rotate(360deg);
    }
}

.gallery-img {
    width: 100%;
    aspect-ratio: 355/200;
}

.title {
    font-size: 1.4em;
}

.header-title {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 40vw;
    line-height: 60px;
}

.content {
    padding: 12px;
}

a,
a:hover {
    text-decoration: none;
}

a {
    color: black;

}

.dark a {
    color: #fff;
}

.home {
    min-height: 100vh;
    width: -webkit-fill-available;
    font-family: -apple-system, BlinkMacSystemFont, Helvetica Neue, PingFang SC, Microsoft YaHei, Source Han Sans SC, Noto Sans CJK SC, WenQuanYi Micro Hei, sans-serif;
    font-size: 15px;
}

.n-layout-header {
    max-width: 100%;
    padding-left: 10px;
    padding-right: 10px;
    height: 60px;
    line-height: 60px;
    position: sticky;
    top: 0;
    z-index: 2;
}

.header-content {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
}

span.n-avatar {
    position: relative;
    top: 8px;
}

.navigation ul {
    list-style: none;
    margin: 0px;
    padding: 0px;
}

.navigation ul li {
    position: relative;
    list-style: none;
    width: 100%;
}

.navigation ul li:hover,
.tv-mode .navigation ul li.tv-focus-visible {
    background: rgba(0, 0, 0, .1);
}

.navigation ul li a {
    position: relative;
    display: block;
    width: 100%;
    display: flex;
    text-decoration: none;
    color: black;
    font-weight: 500;
}

.navigation ul {
    overflow: visible;
}

.navigation ul li a .icon i {
    position: relative;
    display: block;
    min-width: 80px;
    font-size: 26px;
    line-height: 65px;
    text-align: center;
}


.navigation ul li a .title {
    position: relative;
    display: block;
    height: 60px;
    line-height: 60px;
    white-space: nowrap;
    font-size: 18px;
}

.sider-item-title {
    font-size: 1.4em;
    font-weight: 400;
    padding: 20px;
}


.dark .navigation ul li a .title,
.dark .navigation ul li a .icon {
    color: hsla(0, 0%, 100%, .7);
}

.light .navigation ul li a .title,
.light .navigation ul li a .icon {
    color: black;
}

.dark .navigation ul li:hover,
.light .navigation ul li:hover,
.tv-mode .dark .navigation ul li.tv-focus-visible,
.tv-mode .light .navigation ul li.tv-focus-visible {
    background: rgba(0, 0, 0, .1);
}

.dark .navigation ul li a.active {
    background-color: #2d8cf0 !important;
}

.light .navigation ul li a.active .title,
.light .navigation ul li a.active .icon,
.light .navigation.more ul li a.active .title,
.light .navigation.more ul li a.active .icon {
    color: #2d8cf0 !important;
}

.n-layout-footer {
    text-align: center;
}

.desktop-footer {
    margin-top: auto;
    padding: 16px 20px;
    font-size: 12px;
    color: #999;
    text-align: center;
    border-top: 1px solid rgba(128, 128, 128, 0.15);
}

.version-info {
    line-height: 1.5;
    word-break: break-word;
}

.dark .desktop-footer {
    color: hsla(0, 0%, 100%, 0.45);
    border-top-color: rgba(255, 255, 255, 0.08);
}

/* 移动端底部导航栏 */
.mobile-bottom-nav {
    display: none;
}

.bottom-nav-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 8px 12px;
    cursor: pointer;
    text-decoration: none;
    color: inherit;
    flex: 1;
    min-width: 0;
}

.bottom-nav-item i {
    font-size: 22px;
    margin-bottom: 2px;
}

.bottom-nav-item span {
    font-size: 11px;
    white-space: nowrap;
}

.router-link-active.bottom-nav-item {
    color: #2d8cf0;
}

/* 响应式：小屏设备显示底部导航，隐藏侧边栏和footer */
@media screen and (max-width: 768px) {
    .mobile-bottom-nav {
        display: flex;
        position: fixed;
        bottom: 0;
        left: 0;
        right: 0;
        height: 56px;
        background: var(--n-color, #fff);
        border-top: 1px solid var(--n-border-color, #efeff5);
        z-index: 100;
        padding-bottom: env(safe-area-inset-bottom, 0);
    }

    .desktop-footer {
        display: none !important;
    }

    .n-layout-sider {
        display: none !important;
    }

    .n-layout[position="absolute"] {
        bottom: 56px !important;
    }

    /* 小屏隐藏部分header按钮 */
    .header-content .n-space:last-child .n-button:first-child {
        display: none;
    }

    /* 移动端隐藏侧边栏汉堡按钮（侧边栏已隐藏，按钮无意义） */
    .menu-toggle-btn {
        display: none !important;
    }

    /* 移动端标题缩小并限制宽度 */
    .header-title {
        font-size: 1.1em;
        max-width: 50vw;
    }
}
</style>

