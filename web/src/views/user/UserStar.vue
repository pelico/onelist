<template>
    <div v-if="loading" class="load">
    </div>
    <div v-else class="content">
        <div class="page-header">
            <div class="page-title">我的收藏</div>
            <div class="type-tabs">
                <div :class="['tab-item', { active: data_type === 'movie' }]" @click="switchType('movie')">
                    电影
                </div>
                <div :class="['tab-item', { active: data_type === 'tv' }]" @click="switchType('tv')">
                    剧集
                </div>
            </div>
        </div>
        <div class="seriesTab">
            <div class="seriesTab-list">
                <div class="seriesTab-item page-info">
                    {{ pageText }}
                </div>
                <div class="seriesTab-item">
                    <n-button @click="BackPage()" color="#1890ff" text-color="#fff" circle>
                        <i class='bx bx-left-arrow-alt'></i>
                    </n-button>
                </div>
                <div class="seriesTab-item">
                    <n-button @click="NextPage()" color="#1890ff" text-color="#fff" circle>
                        <i class='bx bx-right-arrow-alt'></i>
                    </n-button>
                </div>
            </div>
        </div>
        <div v-if="data && data.length === 0" class="empty-tip">
            <i class='bx bx-star'></i>
            <p>暂无收藏内容</p>
            <span>去发现好看的影片并加入收藏吧</span>
        </div>
        <div v-else class="card-show-content view-card-list">
            <div class="view-item" v-for="(item, index) in data" :key="index">
                <router-link :to="{
                    path: '/video', query: {
                        id: item.id,
                        gallery_type: data_type
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
    </div>
</template>

<script>
import { getCurrentInstance, onMounted, ref } from "vue";
import { onBeforeRouteUpdate } from 'vue-router';

export default {
    name: "UserStar",
    setup() {
        const data_type = ref("movie");
        const size = ref(48);
        const page = ref(1);
        const data = ref(null);
        const error = ref(null);
        const loading = ref(true);
        const { proxy } = getCurrentInstance();
        const num = ref(0);
        const per_card = ref(8);
        if (proxy.COMMON.isMo) {
            per_card.value = 3;
        }
        const pageText = ref("");

        function initPageText() {
            let si = size.value;
            if (num.value < size.value) {
                si = num.value;
            }
            pageText.value = num.value + " 的 " + (page.value - 1) * size.value + "-" + ((page.value - 1) * size.value + si);
        }

        function fetchData() {
            loading.value = true;
            let api = proxy.COMMON.apiUrl + `/v1/api/star/data/list?data_type=${data_type.value}&page=${page.value}&size=${size.value}`;
            proxy.axios.post(api, {}, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    data.value = res.data.data || [];
                    num.value = res.data.num || 0;
                    loading.value = false;
                    initPageText();
                } else {
                    loading.value = false;
                    data.value = [];
                    num.value = 0;
                    proxy.COMMON.ShowMsg(res.data.msg || "加载失败");
                }
            }).catch((error) => {
                loading.value = false;
                data.value = [];
                num.value = 0;
                proxy.COMMON.ShowMsg(error);
            });
        }

        function switchType(type) {
            if (data_type.value === type) return;
            data_type.value = type;
            page.value = 1;
            fetchData();
        }

        onBeforeRouteUpdate((to, from) => {
            fetchData();
        });

        const reF = () => {
            fetchData();
        };

        onMounted(() => {
            fetchData()
        });

        return {
            data_type,
            per_card,
            data,
            loading,
            error,
            page,
            size,
            num,
            pageText,
            reF,
            switchType,
        }
    },
    methods: {
       BackPage() {
            this.page = this.page - 1;
            if (this.page <= 0) {
                this.COMMON.ShowMsg("已经是第1页啦!")
                this.page = 1;
            }
            this.reF();
        },
        NextPage() {
            const maxPage = Math.ceil(this.num / this.size);
            if (this.page >= maxPage) {
                this.COMMON.ShowMsg("已经是最后一页啦!");
                return;
            }
            this.page = this.page + 1;
            this.reF();
        },
    }
}
</script>

<style scoped>
.page-header {
    margin-bottom: 16px;
    margin-top: 20px;
}

.page-title {
    font-size: 1.4em;
    font-weight: 500;
    margin-bottom: 12px;
}

.type-tabs {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
}

.tab-item {
    padding: 6px 18px;
    border-radius: 20px;
    font-size: 0.95em;
    cursor: pointer;
    background: var(--n-color-hover-color, #f5f5f5);
    transition: all 0.2s;
}

.tab-item:hover,
.tv-mode .tab-item.tv-focus-visible {
    background: var(--n-primary-color-hover, #e6f1ff);
}

.tab-item.active {
    background: #2d8cf0;
    color: white;
}

.dark .tab-item {
    background: #2a2a2a;
    color: #ccc;
}

.dark .tab-item.active {
    background: #2d8cf0;
    color: white;
}

.empty-tip {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 80px 20px;
    color: #999;
    text-align: center;
}

.empty-tip i {
    font-size: 4em;
    margin-bottom: 16px;
    opacity: 0.5;
}

.empty-tip p {
    font-size: 1.2em;
    margin: 8px 0;
}

.empty-tip span {
    font-size: 0.9em;
    opacity: 0.7;
}

.seriesTab {
    margin-top: 20px;
    margin-bottom: 20px;
    text-align: center;
}

.seriesTab .seriesTab-list {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    align-items: center;
    gap: 10px;
}

.seriesTab-item.page-info {
    font-size: 1.1em;
    color: #1890ff !important;
    font-weight: 500;
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

.view-card-list .view-item:hover,
.tv-mode .view-card-list .view-item.tv-focus-visible {
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
