<template>
    <div v-if="loading" class="load">
    </div>
    <div v-else class="content">
        <div class="seriesTab">
            <div class="seriesTab-list">
                <div class="seriesTab-item page-info">
                    {{ pageText }}
                </div>
                <div class="seriesTab-item action-buttons">
                    <n-button @click="BackPage()" strong secondary circle size="medium">
                        <i class='bx bx-up-arrow-alt'></i>
                    </n-button>
                    <n-button :loading="loadingMore" :disabled="!hasMore" @click="NextPage()" strong secondary circle size="medium">
                        <i class='bx bx-down-arrow-alt'></i>
                    </n-button>
                    <n-button @click="showSort = !showSort" type="primary" circle size="medium">
                        <i class='bx bx-align-middle'></i>
                        <span class="btn-text">排序</span>
                    </n-button>
                    <n-button @click="showFilter = !showFilter" type="success" circle size="medium">
                        <i class='bx bx-filter'></i>
                        <span class="btn-text">筛选</span>
                    </n-button>
                </div>
            </div>
        </div>
        <div class="card-show-content view-card-list">
            <div class="view-item" v-for="(item, index) in data" :key="index">
                <router-link :to="{
                    path: '/video', query: {
                        id: item.id,
                        gallery_type: gallery_type
                    }
                }">
                    <div class="view-item-header">
                        <div class="view-item-tag-list">
                            <div class="view-item-tag rating">{{ isNaN(Math.floor(item.vote_average * 100) / 100) ?
                                "" :
                                Math.floor(item.vote_average * 100) / 100
                            }}
                            </div>
                            <div v-if="item.played" class="view-item-tag count">
                                <i class='bx bx-check'></i>
                            </div>
                        </div>
                    </div>
                    <img loading="lazy" v-img-fade class="carousel-img"
                        :src='COMMON.getPosterUrl(item.poster_path)'>
                    <div v-if="item.video != null" class="view-item-title">
                        {{ item.title }}
                    </div>
                    <div v-else class="view-item-title">
                        {{ item.name }}
                    </div>
                </router-link>
            </div>
        </div>
        <!-- 无限滚动哨兵：滚动到此处自动加载下一页 -->
        <div ref="sentinelRef" class="scroll-sentinel">
            <div v-if="loadingMore" class="load-more-hint">
                <i class='bx bx-loader-alt bx-spin'></i> 加载中...
            </div>
            <div v-else-if="hasMore" class="load-more-hint">向下滚动加载更多</div>
            <div v-else-if="data && data.length > 0" class="load-more-hint">已经到底啦</div>
        </div>
        <n-modal v-model:show="showSort" transform-origin="center">
            <n-card style="width: 600px;" title="排序" :bordered="false" size="huge" role="dialog" aria-modal="true">
                <template #header-extra>
                    <n-button @click="showSort = !showSort" strong secondary circle>
                        <i class='bx bx-x'></i>
                    </n-button>
                </template>
                <div class="sort-list">
                    <div class="sort-title">
                        排序方式
                    </div>
                    <div class="sort-list">
                        <n-radio-group v-model:value="mode" name="radiogroup">
                            <n-space vertical>
                                <n-radio @change="handleChange" class="sort-item" v-for="item in modes"
                                    :checked="mode === item.value" :key="item.value" :value="item.value">
                                    {{ item.label }}
                                </n-radio>
                            </n-space>
                        </n-radio-group>
                    </div>
                    <div class="sort-title">
                        排序顺序
                    </div>
                    <div class="sort-list">
                        <n-radio-group v-model:value="order" name="radiogroup">
                            <n-space vertical>
                                <n-radio @change="handleChange" class="sort-item" v-for="item in orders"
                                    :checked="order === item.value" :key="item.value" :value="item.value">
                                    {{ item.label }}
                                </n-radio>
                            </n-space>
                        </n-radio-group>
                    </div>
                </div>
            </n-card>
        </n-modal>
        <n-modal v-model:show="showFilter" transform-origin="center">
            <n-card style="width: 600px" title="筛选" :bordered="false" size="huge" role="dialog" aria-modal="true">
                <template #header-extra>
                    <n-button @click="showFilter = !showFilter" strong secondary circle>
                        <i class='bx bx-x'></i>
                    </n-button>
                </template>
                <div class="sort-title">
                    风格
                </div>
                <div class="sort-list">
                    <n-radio-group v-model:value="genre" name="radiogroup">
                        <n-space vertical>
                            <n-radio @change="filterChange" class="sort-item" v-for="item in filters" :key="item"
                                :value="item">
                                {{ item.name }}
                            </n-radio>
                        </n-space>
                    </n-radio-group>
                </div>
            </n-card>
        </n-modal>
    </div>
</template>

<script>
import { getCurrentInstance, onMounted, onUnmounted, ref, nextTick, inject, computed } from "vue";
import { onBeforeRouteUpdate } from 'vue-router';

export default {
    name: "VideoList",
    setup() {
        const gallery_uid = ref(null);
        const gallery_type = ref(null);
        const size = ref(null);
        const page = ref(null);
        const data = ref(null);

        const filters = ref(null);
        const error = ref(null);
        const loading = ref(true);
        const loadingMore = ref(false);
        const { proxy } = getCurrentInstance();
        const num = ref(null);
        const search = ref(false);
        const mode = ref("updated_at");
        const order = ref("DESC");
        const genre = ref("");
        const year = ref("");
        gallery_uid.value = proxy.$route.query.gallery_uid;
        gallery_type.value = proxy.$route.query.gallery_type;
        size.value = 48;
        page.value = 1;

        // 当前筛选的 genre id；null 表示浏览（sort）模式
        const currentFilterId = ref(null);

        const per_card = ref(8);
        if (proxy.COMMON.isMo) {
            per_card.value = 3;
        }
        const pageText = ref(null);

        // 电视导航支持
        const tvNavigation = inject('tvNavigation', null);
        const videoGridRef = ref(null);

        // 无限滚动哨兵
        const sentinelRef = ref(null);
        let observer = null;
        // 软上限：超过该数量后停止自动加载，提示用户使用筛选缩小范围
        const MAX_LOADED = 480;

        const hasMore = computed(() => {
            return data.value != null && num.value != null && data.value.length < num.value && data.value.length < MAX_LOADED;
        });

        let page_str = localStorage.getItem("page")
        if (page_str != null) {
            page.value = parseInt(page_str);
        }
        function init(gallery_uid) {
            let sider_items = document.querySelectorAll(".sider-item a");
            sider_items.forEach(event => {
                event.classList.remove('active')
            });
            sider_items.forEach(event => {
                let title = event.querySelector(".title")
                if (title.dataset.id == gallery_uid) {
                    event.classList.add('active');
                }
            })
        }

        function initPageText() {
            const loaded = (data.value || []).length;
            const total = num.value || 0;
            if (loaded === 0) {
                pageText.value = "共 " + total + " 部";
            } else {
                pageText.value = "已加载 " + loaded + " / " + total + " 部";
            }
            localStorage.setItem("page", page.value);
        }

        function fetchGenreData() {
            let api = `${proxy.COMMON.apiUrl}/v1/api/genre/list?page=1&size=100`;
            proxy.axios.post(api, {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    filters.value = res.data.data;
                }
                loading.value = false;
            }).catch((error) => {
                proxy.COMMON.ShowMsg(error);
            });
        }

        // 构造请求地址：根据 currentFilterId 自动切换 sort / filte 接口
        function buildApi(p) {
            const base = proxy.COMMON.apiUrl;
            const common = `gallery_uid=${gallery_uid.value}&mode=${mode.value}&order=${order.value}&page=${p}&size=${size.value}`;
            if (currentFilterId.value) {
                return `${base}/v1/api/genre/filte?id=${currentFilterId.value}&gallery_type=${gallery_type.value}&${common}`;
            }
            if (gallery_type.value == "movie") {
                return `${base}/v1/api/themovie/sort?${common}`;
            }
            return `${base}/v1/api/thetv/sort?${common}`;
        }

        // 从响应里抽取影片数组（sort 接口直接是数组，filte 接口在 the_movies/the_tvs 里）
        function extractItems(res) {
            if (currentFilterId.value) {
                if (gallery_type.value == "movie") {
                    return (res.data.data && res.data.data.the_movies) || [];
                }
                return (res.data.data && res.data.data.the_tvs) || [];
            }
            return res.data.data || [];
        }

        function fetchData(append = false) {
            if (append) {
                if (loadingMore.value) return;
                if (!hasMore.value) return;
                page.value++;
                loadingMore.value = true;
            } else {
                page.value = 1;
            }
            const api = buildApi(page.value);
            proxy.axios.post(api, {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    const items = extractItems(res);
                    if (!append && currentFilterId.value && items.length === 0) {
                        proxy.COMMON.ShowMsg("未查询到相关内容!");
                    }
                    if (append) {
                        data.value = (data.value || []).concat(items);
                    } else {
                        data.value = items;
                    }
                    num.value = res.data.num;
                    if (!append) {
                        init(gallery_uid.value);
                        fetchGenreData();
                    }
                    initPageText();

                    // 注册视频网格到电视导航系统
                    nextTick(() => {
                        setupTvNavigation();
                        // 若内容不足以撑满视口，继续加载下一页
                        if (append && hasMore.value && sentinelRef.value) {
                            const rect = sentinelRef.value.getBoundingClientRect();
                            if (rect.top < (window.innerHeight + 300)) {
                                fetchMore();
                            }
                        }
                    });
                }
                loadingMore.value = false;
            }).catch((error) => {
                loadingMore.value = false;
                proxy.COMMON.ShowMsg(error);
            });
        }

        function fetchMore() {
            fetchData(true);
        }

        // 设置电视导航
        function setupTvNavigation() {
            if (!tvNavigation || !tvNavigation.isTvMode) return;

            const gridContainer = document.querySelector('.view-card-list');
            if (gridContainer) {
                const items = gridContainer.querySelectorAll('.view-item');
                if (items.length > 0) {
                    // 检测列数
                    const cols = tvNavigation.detectGridCols(Array.from(items).map(el => el.querySelector('a') || el));

                    // 注册网格组
                    tvNavigation.registerGroup('videoGrid', Array.from(items).map(el => el.querySelector('a') || el), {
                        cols: cols,
                        wrap: false
                    });

                    // 设置为当前组
                    tvNavigation.setCurrentGroup('videoGrid');
                }
            }

            // 注册工具栏按钮
            const toolButtons = document.querySelectorAll('.seriesTab-list .n-button');
            if (toolButtons.length > 0) {
                tvNavigation.registerGroup('videoTools', Array.from(toolButtons), {
                    vertical: false,
                    wrap: true
                });
            }
        }

        // 无限滚动观察器
        function setupObserver() {
            if (observer) observer.disconnect();
            if (!sentinelRef.value) return;
            observer = new IntersectionObserver((entries) => {
                if (entries[0] && entries[0].isIntersecting) {
                    fetchMore();
                }
            }, { rootMargin: '300px' });
            observer.observe(sentinelRef.value);
        }

        onBeforeRouteUpdate((to, from) => {
            gallery_uid.value = to.query.gallery_uid;
            gallery_type.value = to.query.gallery_type;
            currentFilterId.value = null;
            fetchData(false);
        });

        const refresh = () => {
            currentFilterId.value = null;
            fetchData(false);
        };

        onMounted(() => {
            fetchData(false);
            nextTick(() => setupObserver());
        });

        onUnmounted(() => {
            // 清理观察器与导航组
            if (observer) observer.disconnect();
            if (tvNavigation) {
                tvNavigation.unregisterGroup('videoGrid');
                tvNavigation.unregisterGroup('videoTools');
            }
        });

        return {
            gallery_type,
            per_card,
            data,
            loading,
            loadingMore,
            hasMore,
            sentinelRef,
            error,
            page,
            size,
            num,
            search,
            pageText,
            filters,
            genre,
            year,
            refresh,
            videoGridRef,
            fetchMore,
            handleChange(e) {
                currentFilterId.value = null;
                fetchData(false);
            },
            filterChange(e) {
                currentFilterId.value = genre.value.id;
                fetchData(false);
            },
            showSort: ref(false),
            showFilter: ref(false),
            mode: mode,
            modes: [
                {
                    value: 'updated_at',
                    label: '更新时间'
                },
                {
                    value: 'created_at',
                    label: '加入时间'
                },
                {
                    value: 'vote_average',
                    label: '评分'
                },
                {
                    value: 'release_date',
                    label: '发行时间'
                }
            ].map((s) => {
                s.value = s.value.toLowerCase()
                return s
            }),
            order: order,
            orders: [
                {
                    value: "ASC",
                    label: "升序"
                },
                {
                    value: 'DESC',
                    label: '降序'
                }
            ].map((s) => {
                s.value = s.value.toLowerCase()
                return s
            })
        }
    },
    methods: {
        BackPage() {
            // 回到顶部
            const scroller = document.querySelector('.n-layout .n-layout-scroll-container');
            if (scroller && scroller.scrollTo) {
                scroller.scrollTo({ top: 0, behavior: 'smooth' });
            } else {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            }
        },
        NextPage() {
            this.fetchMore();
        },
    }
}
</script>

<style scoped>
.seriesTab {
    margin-top: 20px;
    margin-bottom: 20px;
    padding: 12px 20px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 10px;
}

.seriesTab .seriesTab-list {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    gap: 15px;
}

.seriesTab-item.page-info {
    font-size: 1.1em;
    color: #fff;
    font-weight: 500;
}

.seriesTab-item.action-buttons {
    display: flex;
    align-items: center;
    gap: 8px;
}

.btn-text {
    margin-left: 4px;
    font-size: 0.9em;
}

.sort-title {
    font-size: 1.2em;
    margin-top: 12px;
    margin-bottom: 12px;
}

.sort-list .sort-item {
    font-size: 1.2em;
    margin-top: 4px;
    margin-bottom: 4px;
}

.view-card-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, 11.5%);
    grid-gap: 6px;
    justify-content: space-between;
}

@media (max-width: 750px) {
    .view-card-list {
        grid-template-columns: repeat(auto-fill, 31%);
    }
}

.card-show-title {
    font-size: 1.2em;
    font-weight: 400;
    padding-bottom: 16px;
}

.card-shows {
    margin-bottom: 20px;
}

.view-item {
    text-align: center;
}

.view-card-list img.carousel-img {
    width: 100%;
    aspect-ratio: 11/16;
    border-radius: 5px;
}


.view-card-list .view-item {
    transform: translateY(0) scale(1);
    transition: all .2s ease-in-out;
}

.view-card-list .view-item:hover {
    transform: translateY(-4px) scale(0.95);
    transition: all .2s ease-in-out;
}

.view-item-header {
    position: absolute;
    width: 95%;
    padding-left: 4px;
}

.view-item-tag-list {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.view-item-tag-list .count {
    background-color: #2d8cf0 !important;
    border-radius: 50%;
    width: 20px;
    height: 20px;
    color: white;
    padding: 4px;
    text-align: center;
}

.rating {
    color: yellow;
}

.project {
    margin-top: 10px;
}

.project .n-pagination {
    float: right;
}

/* 无限滚动哨兵 */
.scroll-sentinel {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 16px;
}

.load-more-hint {
    color: #888;
    font-size: 0.9em;
    opacity: 0.8;
}

@media (max-width: 767px) {
    .card-show-title {
        font-size: 0.8em;
        font-weight: 400;
        padding-bottom: 10px;
    }

    .view-item-title {
        font-size: 0.5em;
        font-weight: 400;
    }

    .custom-arrow.next {
        bottom: 60px;
    }
}
</style>
