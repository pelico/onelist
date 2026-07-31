<template>
    <div class="game-player-page">
        <!-- 顶部工具栏 -->
        <div class="game-toolbar">
            <div class="toolbar-left">
                <n-button @click="goBack" quaternary circle>
                    <template #icon>
                        <i class='bx bx-arrow-back'></i>
                    </template>
                </n-button>
                <span class="game-title">{{ gameName }}</span>
            </div>
            <div class="toolbar-right">
                <span class="play-time" v-if="elapsedSeconds > 0">
                    <i class='bx bx-time-five'></i> {{ formatTime(elapsedSeconds) }}
                </span>
                <n-button @click="toggleFullscreen" quaternary circle>
                    <template #icon>
                        <i class='bx bx-fullscreen'></i>
                    </template>
                </n-button>
            </div>
        </div>

        <!-- 游戏 iframe -->
        <div class="game-frame-wrapper">
            <iframe
                ref="gameIframe"
                :src="gameUrl"
                class="game-iframe"
                frameborder="0"
                allow="fullscreen; gamepad"
                @load="onIframeLoad"
            ></iframe>
        </div>
    </div>
</template>

<script>
import { defineComponent, getCurrentInstance, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { tvNavigation } from '../../plugins/tvNavigation';

export default defineComponent({
    name: 'GamePlayer',
    setup() {
        const { proxy } = getCurrentInstance();
        const route = useRoute();
        const router = useRouter();
        const gameIframe = ref(null);
        const gameName = ref(route.query.name || '游戏');
        const gameFile = ref(route.query.file || '');
        const gameUrl = ref('');
        const elapsedSeconds = ref(0);
        let heartbeatTimer = null;
        let elapsedTimer = null;
        let sessionStart = null;

        // 构建游戏 URL
        gameUrl.value = proxy.COMMON.apiUrl + '/v1/api/game/file/' + encodeURIComponent(gameFile.value);

        function goBack() {
            stopHeartbeat();
            router.back();
        }

        function onIframeLoad() {
            startHeartbeat();
            // iframe 加载完成后自动聚焦，让游戏立即获得键盘控制权
            if (tvNavigation.isTvMode && gameIframe.value) {
                tvNavigation.refresh();
                tvNavigation.setFocus(gameIframe.value);
            }
        }

        function startHeartbeat() {
            sessionStart = new Date();
            elapsedSeconds.value = 0;

            // 每秒更新显示时间
            elapsedTimer = setInterval(() => {
                elapsedSeconds.value++;
            }, 1000);

            // 每 10 秒上报一次心跳
            heartbeatTimer = setInterval(() => {
                sendHeartbeat();
            }, 10000);
        }

        function stopHeartbeat() {
            if (heartbeatTimer) {
                clearInterval(heartbeatTimer);
                heartbeatTimer = null;
            }
            if (elapsedTimer) {
                clearInterval(elapsedTimer);
                elapsedTimer = null;
            }
            // 离开时发送最后一次心跳
            if (sessionStart && elapsedSeconds.value > 3) {
                sendHeartbeat();
            }
        }

        function sendHeartbeat() {
            const userId = proxy.$cookies.get("user_id") || '';
            const body = {
                user_id: userId,
                data_type: 'game',
                data_id: 0,
                title: gameName.value,
                gallery_uid: 'games',
                gallery_title: '小游戏',
                duration: 10,
                position: elapsedSeconds.value,
                total_duration: 0,
                started_at: sessionStart ? sessionStart.toISOString() : new Date().toISOString()
            };

            proxy.axios.post(proxy.COMMON.apiUrl + '/v1/api/play-history/heartbeat', body, {
                headers: {
                    'Authorization': proxy.$cookies.get("Authorization"),
                    'Content-Type': 'application/json'
                }
            }).catch(() => {
                // 静默失败，不影响游戏体验
            });
        }

        function formatTime(seconds) {
            const m = Math.floor(seconds / 60);
            const s = seconds % 60;
            if (m > 0) {
                return m + '分' + s + '秒';
            }
            return s + '秒';
        }

        function toggleFullscreen() {
            const wrapper = document.querySelector('.game-frame-wrapper');
            if (!wrapper) return;
            if (document.fullscreenElement) {
                document.exitFullscreen();
            } else {
                wrapper.requestFullscreen().catch(() => {});
            }
        }

        onMounted(() => {
            // 注册 iframe 为 TV 导航可聚焦元素
            if (tvNavigation.isTvMode && gameIframe.value) {
                tvNavigation.registerGroup('game-player', [gameIframe.value]);
            }

            // 如果 iframe 已经加载（缓存），手动触发
            if (gameIframe.value && gameIframe.value.contentDocument && gameIframe.value.contentDocument.readyState === 'complete') {
                startHeartbeat();
                if (tvNavigation.isTvMode) {
                    tvNavigation.refresh();
                    tvNavigation.setFocus(gameIframe.value);
                }
            }
        });

        onUnmounted(() => {
            stopHeartbeat();
        });

        return {
            gameIframe,
            gameName,
            gameUrl,
            elapsedSeconds,
            goBack,
            onIframeLoad,
            formatTime,
            toggleFullscreen
        };
    }
});
</script>

<style scoped>
.game-player-page {
    display: flex;
    flex-direction: column;
    height: calc(100vh - 60px);
    background: #1a1a2e;
}

.game-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 16px;
    background: rgba(0, 0, 0, 0.6);
    color: #fff;
    flex-shrink: 0;
    z-index: 10;
}

.toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
}

.toolbar-right {
    display: flex;
    align-items: center;
    gap: 12px;
}

.game-title {
    font-size: 1.1em;
    font-weight: 500;
}

.play-time {
    font-size: 0.9em;
    color: rgba(255, 255, 255, 0.7);
    display: flex;
    align-items: center;
    gap: 4px;
}

.game-frame-wrapper {
    flex: 1;
    position: relative;
    overflow: hidden;
}

.game-frame-wrapper:fullscreen {
    background: #000;
}

.game-iframe {
    width: 100%;
    height: 100%;
    border: none;
    display: block;
}

/* 全屏时隐藏工具栏 */
.game-frame-wrapper:fullscreen + .game-toolbar,
:fullscreen .game-toolbar {
    display: none;
}

/* 深色模式下工具栏按钮 */
:deep(.n-button.n-button--quaternary) {
    color: #fff;
}

:deep(.n-button.n-button--quaternary:hover) {
    background: rgba(255, 255, 255, 0.1);
}
</style>

<style>
/* 非 scoped：游戏 iframe 的 TV 焦点指示器 */
.game-iframe.tv-focus-visible {
    outline: 3px solid #4ecca3;
    outline-offset: -3px;
}
</style>
