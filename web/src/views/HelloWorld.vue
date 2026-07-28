<template>
    <div class="content">
        <div v-if="!data" class="skeleton-gallery">
            <div class="skeleton-card" v-for="n in 5" :key="'sg-'+n"></div>
        </div>
        <div v-else class="card-list">
            <div class="card-shows medias">
                <div class="card-show-title-row">
                    <div class="card-show-title">
                        我的媒体
                    </div>
                    <div class="custom-arrow">
                        <n-button @click="galleryPrev" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-left'></i>
                        </n-button>
                        <n-button @click="galleryNext" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-right'></i>
                        </n-button>
                    </div>
                </div>
                <div class="card-show-content gallery-card">
                    <n-carousel ref="galleryCarousel" :show-dots="false" :show-arrow="false" :slides-per-view="per_view" :space-between="20" :loop="false"
                        draggable>
                        <div class="view-item" v-for="(item, index) in data" :key="index">
                            <router-link :to="{
                                path: '/list', query: {
                                    gallery_uid: item.gallery_uid,
                                    gallery_type: item.gallery_type
                                }
                            }">
                                <img v-if="item.image.length > 0" loading="lazy" class='gallery-img'
                                    :src='item.image.search(/gallery/) != -1 ? COMMON.apiUrl + item.image : COMMON.imgUrl + "/t/p/w355_and_h200_multi_faces" + item.image'>
                                <img v-else loading="lazy" class='gallery-img' src='/images/not_gellery.png'>
                                <div class="view-item-title">
                                    {{ item.title }}
                                </div>
                            </router-link>
                        </div>
                    </n-carousel>
                </div>
            </div>
            <div v-if="latestMovies && latestMovies.length > 0" class="card-shows">
                <div class="card-show-title-row">
                    <div class="card-show-title">
                        <i class='bx bx-film'></i> 最新电影
                    </div>
                    <div class="custom-arrow">
                        <n-button @click="latestMoviePrev" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-left'></i>
                        </n-button>
                        <n-button @click="latestMovieNext" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-right'></i>
                        </n-button>
                    </div>
                </div>
                <div class="card-show-content view-card">
                    <n-carousel ref="latestMovieCarousel" :show-dots="false" :show-arrow="false" :slides-per-view="per_card" :space-between="20" :loop="false"
                        draggable>
                        <div class="view-item" v-for="item in latestMovies" :key="'lm-'+item.id">
                            <div class="view-item-header">
                                <div class="view-item-tag-list">
                                    <div class="view-item-tag rating">
                                        {{ isNaN(Math.floor(item.vote_average * 100) /
                                            100) ? "" : Math.floor(item.vote_average * 100) / 100
                                        }}
                                    </div>
                                    <div v-if="item.played" class="view-item-tag count">
                                        <i class='bx bx-check'></i>
                                    </div>
                                </div>
                            </div>
                            <router-link :to="{
                                path: '/video', query: {
                                    id: item.id,
                                    gallery_type: 'movie'
                                }
                            }">
                                <img loading="lazy" v-img-fade class="carousel-img"
                                    :src='COMMON.getPosterUrl(item.poster_path, item.id)'>
                            </router-link>
                            <div class="view-item-title">
                                {{ item.title }}
                            </div>
                        </div>
                    </n-carousel>
                </div>
            </div>
            <div v-if="latestTvs && latestTvs.length > 0" class="card-shows">
                <div class="card-show-title-row">
                    <div class="card-show-title">
                        <i class='bx bx-tv'></i> 最新剧集
                    </div>
                    <div class="custom-arrow">
                        <n-button @click="latestTvPrev" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-left'></i>
                        </n-button>
                        <n-button @click="latestTvNext" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-right'></i>
                        </n-button>
                    </div>
                </div>
                <div class="card-show-content view-card">
                    <n-carousel ref="latestTvCarousel" :show-dots="false" :show-arrow="false" :slides-per-view="per_card" :space-between="20" :loop="false"
                        draggable>
                        <div class="view-item" v-for="item in latestTvs" :key="'lt-'+item.id">
                            <div class="view-item-header">
                                <div class="view-item-tag-list">
                                    <div class="view-item-tag rating">
                                        {{ isNaN(Math.floor(item.vote_average * 100) /
                                            100) ? "" : Math.floor(item.vote_average * 100) / 100
                                        }}
                                    </div>
                                    <div v-if="item.played" class="view-item-tag count">
                                        <i class='bx bx-check'></i>
                                    </div>
                                </div>
                            </div>
                            <router-link :to="{
                                path: '/video', query: {
                                    id: item.id,
                                    gallery_type: 'tv'
                                }
                            }">
                                <img loading="lazy" v-img-fade class="carousel-img"
                                    :src='COMMON.getPosterUrl(item.poster_path, item.id)'>
                            </router-link>
                            <div class="view-item-title">
                                {{ item.name }}
                            </div>
                        </div>
                    </n-carousel>
                </div>
            </div>
            <div class="card-shows" v-for="(key, index) in Object.keys(dict_data)" :key="index">
                <div class="card-show-title-row">
                    <div class="card-show-title">
                        {{ key }}
                    </div>
                    <div class="custom-arrow">
                        <n-button @click="carouselPrev(index)" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-left'></i>
                        </n-button>
                        <n-button @click="carouselNext(index)" color="#1890ff" text-color="#fff" circle size="small">
                            <i class='bx bx-chevron-right'></i>
                        </n-button>
                    </div>
                </div>
                <div class="card-show-content view-card">
                    <n-carousel :ref="el => setCarouselRef(el, index)" :show-dots="false" :show-arrow="false" :slides-per-view="per_card" :space-between="20" :loop="false"
                        draggable>
                        <div class="view-item" v-for="item in dict_data[key]" :key="item.id">
                            <div class="view-item-header">
                                <div class="view-item-tag-list">
                                    <div class="view-item-tag rating">
                                        {{ isNaN(Math.floor(item.vote_average * 100) /
                                            100) ? "" : Math.floor(item.vote_average * 100) / 100
                                        }}
                                    </div>
                                    <div v-if="item.played" class="view-item-tag count">
                                        <i class='bx bx-check'></i>
                                    </div>
                                </div>
                            </div>
                            <router-link :to="{
                                path: '/video', query: {
                                    id: item.id,
                                    gallery_type: item.title != null ? 'movie' : 'tv'
                                }
                            }">
                                <img loading="lazy" v-img-fade class="carousel-img"
                                    :src='COMMON.getPosterUrl(item.poster_path, item.id)'>
                            </router-link>
                            <div v-if="item.title != null" class="view-item-title">
                                {{ item.title }}
                            </div>
                            <div v-else class="view-item-title">
                                {{ item.name }}
                            </div>
                        </div>
                    </n-carousel>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import { getCurrentInstance, onMounted, ref, nextTick } from "vue";
import { tvNavigation } from '../plugins/tvNavigation';
export default {
    name: 'HelloWorld',
    setup() {
        const per_view = ref(5);
        const per_card = ref(8);

        const { proxy } = getCurrentInstance();
        if (proxy.COMMON.isMo) {
            per_view.value = 2;
            per_card.value = 3;
        }
        const data = ref(null);
        const dict_data = ref(null);
        const latestMovies = ref([]);
        const latestTvs = ref([]);
        var dataDict = new Object();

        const size = ref(24);

        const galleryCarousel = ref(null);
        const carouselRefs = ref([]);
        const latestMovieCarousel = ref(null);
        const latestTvCarousel = ref(null);
        
        function setCarouselRef(el, index) {
            if (el) {
                carouselRefs.value[index] = el;
            }
        }
        
        function galleryPrev() {
            if (galleryCarousel.value && galleryCarousel.value.prev) {
                galleryCarousel.value.prev();
            }
        }
        
        function galleryNext() {
            if (galleryCarousel.value && galleryCarousel.value.next) {
                galleryCarousel.value.next();
            }
        }
        
        function latestMoviePrev() {
            if (latestMovieCarousel.value && latestMovieCarousel.value.prev) {
                latestMovieCarousel.value.prev();
            }
        }
        
        function latestMovieNext() {
            if (latestMovieCarousel.value && latestMovieCarousel.value.next) {
                latestMovieCarousel.value.next();
            }
        }
        
        function latestTvPrev() {
            if (latestTvCarousel.value && latestTvCarousel.value.prev) {
                latestTvCarousel.value.prev();
            }
        }
        
        function latestTvNext() {
            if (latestTvCarousel.value && latestTvCarousel.value.next) {
                latestTvCarousel.value.next();
            }
        }
        
        function carouselPrev(index) {
            if (carouselRefs.value[index] && carouselRefs.value[index].prev) {
                carouselRefs.value[index].prev();
            }
        }
        
        function carouselNext(index) {
            if (carouselRefs.value[index] && carouselRefs.value[index].next) {
                carouselRefs.value[index].next();
            }
        }

        function fetchData() {
            proxy.axios.get(proxy.COMMON.apiUrl + `/v1/api/home?size=` + size.value + `&gallery_size=` + size.value, {
                headers: {
                    'content-type': 'application/json',
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    const homeData = res.data.data;
                    data.value = homeData.galleries || [];
                    latestMovies.value = homeData.latest_movies || [];
                    latestTvs.value = homeData.latest_tvs || [];
                    const items = homeData.gallery_items || {};
                    const newDict = {};
                    for (const gallery of data.value) {
                        if (items[gallery.gallery_uid] && items[gallery.gallery_uid].length > 0) {
                            newDict[gallery.title] = items[gallery.gallery_uid];
                        }
                    }
                    dict_data.value = newDict;
                    nextTick(() => {
                        setupTvNavigation();
                    });
                }
            }).catch((error) => {
               proxy.COMMON.ShowMsg(error);
            });
        }
        
        function setupTvNavigation() {
            // 收集首页所有可点击的卡片和翻页按钮
            const items = [];
            document.querySelectorAll('.card-shows').forEach((section) => {
                const buttons = section.querySelectorAll('.custom-arrow .n-button');
                buttons.forEach((btn, idx) => {
                    btn.setAttribute('tabindex', '0');
                    items.push(btn);
                });
                const cards = section.querySelectorAll('.view-item');
                cards.forEach((card) => {
                    const link = card.querySelector('a');
                    if (link) {
                        link.setAttribute('tabindex', '0');
                        items.push(link);
                    }
                });
            });
            if (items.length > 0) {
                tvNavigation.registerGroup('homepage', items, { vertical: false, wrap: false });
            }
        }

        onMounted(() => {
            fetchData();
        });
        return {
            data,
            dict_data,
            latestMovies,
            latestTvs,
            per_view,
            per_card,
            size,
            galleryCarousel,
            carouselRefs,
            latestMovieCarousel,
            latestTvCarousel,
            setCarouselRef,
            galleryPrev,
            galleryNext,
            latestMoviePrev,
            latestMovieNext,
            latestTvPrev,
            latestTvNext,
            carouselPrev,
            carouselNext,
        }
    },
    methods: {
    },
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.card-show-title-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.card-show-title {
    font-size: 1.2em;
    font-weight: 400;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.card-shows {
    margin-bottom: 20px;
}

.view-item {
    text-align: center;
}

.gallery-card .view-item {
    transform: translateY(0) scale(1);
    transition: all .2s ease-in-out;
}

.gallery-card .view-item:hover {
    transform: translateY(0) scale(0.99);
    transition: all .2s ease-in-out;
}

.gallery-card .view-item img {
    border-radius: 5px;
}

.medias .view-item-title {
    font-size: 1.2em;
    font-weight: 400;
}

.custom-arrow {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}

.skeleton-gallery {
    display: flex;
    gap: 20px;
    padding: 20px 0;
}

.skeleton-card {
    flex: 1;
    height: 120px;
    border-radius: 5px;
    background: linear-gradient(90deg, rgba(255,255,255,0.05) 25%, rgba(255,255,255,0.1) 50%, rgba(255,255,255,0.05) 75%);
    background-size: 200% 100%;
    animation: skeleton-loading 1.5s infinite;
}

@keyframes skeleton-loading {
    0% {
        background-position: 200% 0;
    }
    100% {
        background-position: -200% 0;
    }
}

@media (max-width: 750px) {
    .custom-arrow {
        display: flex;
    }
}

img.carousel-img {
    width: 100%;
    aspect-ratio: 16/10;
}

.view-card img.carousel-img {
    width: 100%;
    aspect-ratio: 11/16;
    border-radius: 5px;
}


.view-card .view-item {
    transform: translateY(0) scale(1);
    transition: all .2s ease-in-out;
}

.view-card .view-item:hover {
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
        font-size: 1.2em;
        font-weight: 400;
        padding-bottom: 10px;
    }

    .view-item-title {
        font-size: 0.5em;
        font-weight: 400;
    }
}
</style>
