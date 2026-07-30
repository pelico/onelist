<template>
    <teleport to="body">
        <div v-if="visible" class="screensaver-overlay" @keydown="blockInput" @click="blockInput" @touchstart="blockInput">
            <!-- 倒计时角标（右上角，黑色半透明背景） -->
            <div class="screensaver-countdown">
                <template v-if="mode === 'rest'">
                    <span class="countdown-icon">&#128336;</span>
                    <span>休息中 {{ formattedCountdown }}</span>
                </template>
                <template v-else>
                    <span>今日播放时间已到</span>
                    <br />
                    <span class="countdown-sub">明天再继续吧</span>
                </template>
                <br />
                <span class="countdown-stat">今天已看 {{ todayWatchedMinutes }} 分钟</span>
            </div>

            <!-- 屏保内容区域 -->
            <div class="screensaver-content">
                <!-- 视频类型 -->
                <video
                    v-if="currentWallpaper && currentWallpaper.type === 'video'"
                    ref="wallpaperVideo"
                    class="screensaver-media"
                    :src="wallpaperFileUrl"
                    autoplay
                    muted
                    loop
                    playsinline
                />
                <!-- 图片类型 -->
                <img
                    v-else-if="currentWallpaper && currentWallpaper.type === 'image'"
                    class="screensaver-media"
                    :src="wallpaperFileUrl"
                    alt="屏保图片"
                />
                <!-- HTML 类型（用 iframe 隔离渲染） -->
                <iframe
                    v-else-if="currentWallpaper && currentWallpaper.type === 'html'"
                    class="screensaver-media screensaver-html"
                    :src="wallpaperFileUrl"
                    frameborder="0"
                    sandbox="allow-same-origin"
                />
                <!-- 无素材时的默认提示 -->
                <div v-else class="screensaver-default">
                    <div class="screensaver-default-icon">&#127811;</div>
                    <div class="screensaver-default-text">爱护眼睛，先休息一会吧</div>
                </div>
            </div>
        </div>
    </teleport>
</template>

<script>
import { computed, defineComponent, onBeforeUnmount, ref, watch } from 'vue';

export default defineComponent({
    name: 'ScreensaverOverlay',
    props: {
        visible: { type: Boolean, default: false },
        mode: { type: String, default: 'rest' },       // 'rest' = 休息倒计时, 'locked' = 每日锁定
        countdown: { type: Number, default: 0 },        // 剩余秒数（rest 模式用）
        wallpaperFiles: { type: Array, default: () => [] },
        todayWatchedMinutes: { type: Number, default: 0 }  // 今日已看分钟数
    },
    emits: ['countdown-end'],
    setup(props, { emit }) {
        const wallpaperVideo = ref(null);
        const currentWallpaper = ref(null);

        const wallpaperFileUrl = computed(() => {
            if (!currentWallpaper.value) return '';
            return currentWallpaper.value.url;
        });

        const formattedCountdown = computed(() => {
            const s = Math.max(0, props.countdown);
            const m = Math.floor(s / 60);
            const sec = s % 60;
            return m > 0 ? `${m}分${sec.toString().padStart(2, '0')}秒` : `${sec}秒`;
        });

        function pickRandomWallpaper() {
            if (!props.wallpaperFiles || props.wallpaperFiles.length === 0) {
                currentWallpaper.value = null;
                return;
            }
            const idx = Math.floor(Math.random() * props.wallpaperFiles.length);
            currentWallpaper.value = props.wallpaperFiles[idx];
        }

        function blockInput(e) {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
        }

        // 监听 visible 变化：出现时随机选素材，消失时清理
        watch(() => props.visible, (val) => {
            if (val) {
                pickRandomWallpaper();
            } else {
                currentWallpaper.value = null;
            }
        });

        // 输入屏蔽：组件挂载期间始终拦截所有键盘/点击事件
        function onKeydown(e) {
            if (props.visible) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
            }
        }
        function onClick(e) {
            if (props.visible) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
            }
        }

        // 用 capture 阶段拦截，确保优先于其他处理器
        window.addEventListener('keydown', onKeydown, true);
        window.addEventListener('click', onClick, true);
        window.addEventListener('touchstart', onClick, true);

        onBeforeUnmount(() => {
            window.removeEventListener('keydown', onKeydown, true);
            window.removeEventListener('click', onClick, true);
            window.removeEventListener('touchstart', onClick, true);
        });

        return {
            wallpaperVideo,
            currentWallpaper,
            wallpaperFileUrl,
            formattedCountdown,
            blockInput
        };
    }
});
</script>

<style scoped>
.screensaver-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    z-index: 99999;
    background: #000;
    display: flex;
    align-items: center;
    justify-content: center;
    /* 确保最高层级，覆盖一切 */
    isolation: isolate;
}

.screensaver-countdown {
    position: absolute;
    top: 24px;
    right: 24px;
    background: rgba(0, 0, 0, 0.75);
    color: #fff;
    padding: 12px 20px;
    border-radius: 8px;
    font-size: 1.1em;
    z-index: 100000;
    backdrop-filter: blur(6px);
    line-height: 1.5;
    user-select: none;
    pointer-events: none;
}

.countdown-icon {
    margin-right: 6px;
}

.countdown-sub {
    font-size: 0.85em;
    color: #aaa;
}

.countdown-stat {
    font-size: 0.8em;
    color: #888;
    margin-top: 4px;
    display: inline-block;
}

.screensaver-content {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
}

.screensaver-media {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
}

.screensaver-media.screensaver-html {
    width: 100%;
    height: 100%;
    border: none;
}

.screensaver-default {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: linear-gradient(135deg, #a8e6cf 0%, #dcedc1 50%, #c5e1a5 100%);
    color: #2e7d32;
    font-size: 1.6em;
    text-align: center;
    user-select: none;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
}

.screensaver-default-icon {
    font-size: 3em;
    margin-bottom: 16px;
}

.screensaver-default-text {
    font-size: 1em;
    font-weight: 500;
    letter-spacing: 0.05em;
}
</style>
