<template>
    <div v-if="loading" class="load"></div>
    <div v-else class="content">
        <div class="content-header">
            <h2 class="content-header-title">
                <i class='bx bx-joystick'></i> 小游戏
            </h2>
        </div>

        <div v-if="games.length === 0" class="empty-state">
            <i class='bx bx-joystick' style="font-size: 48px; color: #ccc;"></i>
            <p>还没有游戏，在 config 目录下的 games 文件夹放入 HTML 游戏文件即可</p>
        </div>

        <div v-else class="showContainer">
            <div class="card-show-list">
                <div class="view-item" v-for="(item, index) in games" :key="index">
                    <router-link :to="{ path: '/game', query: { file: item.file, name: item.name } }">
                        <div class="view-item-header">
                            <div class="view-item-tag-list">
                                <div class="view-item-tag game-tag">
                                    <i class='bx bx-joystick'></i>
                                </div>
                            </div>
                        </div>
                        <div class="game-thumb">
                            <i class='bx bx-joystick-alt'></i>
                        </div>
                        <div class="view-item-title">
                            {{ item.name }}
                        </div>
                    </router-link>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import { defineComponent, getCurrentInstance, onMounted, ref } from 'vue';
import { tvNavigation } from '../../plugins/tvNavigation';

export default defineComponent({
    name: 'GameList',
    setup() {
        const { proxy } = getCurrentInstance();
        const loading = ref(true);
        const games = ref([]);

        function getGames() {
            proxy.axios.get(proxy.COMMON.apiUrl + '/v1/api/game/list', {
                headers: {
                    'Authorization': proxy.$cookies.get("Authorization")
                }
            }).then(res => {
                if (res.data.code == 200) {
                    games.value = res.data.data || [];
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg);
                }
                loading.value = false;
            }).catch(err => {
                proxy.COMMON.ShowMsg(err);
                loading.value = false;
            });
        }

        onMounted(() => {
            getGames();
            // TV 遥控器导航
            setTimeout(() => {
                if (tvNavigation.isTvMode) {
                    const links = document.querySelectorAll('.view-item a');
                    if (links.length > 0) {
                        tvNavigation.registerGroup('games', Array.from(links), { vertical: false, wrap: true });
                        tvNavigation.setCurrentGroup('games');
                    }
                }
            }, 500);
        });

        return { loading, games };
    }
});
</script>

<style scoped>
.content-header {
    padding: 0 24px;
    min-height: 60px;
    display: flex;
    align-items: center;
}

.content-header-title {
    font-size: 1.4em;
    font-weight: 500;
    display: flex;
    align-items: center;
    gap: 10px;
}

.empty-state {
    text-align: center;
    padding: 80px 20px;
    color: #999;
}

.empty-state p {
    margin-top: 16px;
    font-size: 1.1em;
}

.showContainer {
    margin: 0 24px 24px;
}

.card-show-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, 11.5%);
    gap: 16px;
}

.view-item {
    position: relative;
    border-radius: 8px;
    overflow: hidden;
    background: #f5f5f5;
    transition: transform 0.2s, box-shadow 0.2s;
}

.view-item:hover {
    transform: scale(1.05);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.view-item a {
    display: block;
    text-decoration: none;
    color: inherit;
}

.view-item-header {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 2;
    padding: 6px;
}

.view-item-tag-list {
    display: flex;
    gap: 4px;
}

.view-item-tag {
    font-size: 11px;
    padding: 2px 6px;
    border-radius: 4px;
    color: #fff;
    display: flex;
    align-items: center;
    gap: 3px;
}

.game-tag {
    background: #2080f0;
}

.game-thumb {
    width: 100%;
    aspect-ratio: 2/3;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    display: flex;
    align-items: center;
    justify-content: center;
}

.game-thumb i {
    font-size: 48px;
    color: rgba(255, 255, 255, 0.8);
}

.view-item-title {
    padding: 8px 6px;
    font-size: 13px;
    text-align: center;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    background: #fff;
}

.dark .view-item {
    background: #2a2a2a;
}

.dark .view-item-title {
    background: #2a2a2a;
    color: #fff;
}

/* TV 焦点样式 */
.tv-focus-visible {
    outline: 3px solid #2080f0;
    outline-offset: 2px;
}

/* 响应式 */
@media screen and (max-width: 768px) {
    .content-header {
        padding: 0 12px;
    }

    .showContainer {
        margin: 0 12px 12px;
    }

    .card-show-list {
        grid-template-columns: repeat(3, 1fr);
        gap: 10px;
    }

    .game-thumb i {
        font-size: 32px;
    }
}
</style>
