<template>
    <teleport to="body">
        <!-- 强制弹窗模式 -->
        <div v-if="forcedVisible" class="msg-overlay msg-forced" @keydown="blockInput" @click="blockInput">
            <div class="msg-forced-box">
                <div class="msg-forced-icon">&#9993;</div>
                <div class="msg-forced-title">{{ senderLabel(forcedMessage) }}发来一条消息</div>
                <div class="msg-forced-content">{{ forcedMessage.content }}</div>
                <div class="msg-forced-time">{{ formatTime(forcedMessage.created_at) }}</div>
                <n-button ref="forcedBtnRef" type="info" size="large" class="msg-forced-btn" @click="ackForced">我知道了</n-button>
            </div>
        </div>

        <!-- 普通通知（右上角堆叠 toast） -->
        <div v-if="normalMessages.length > 0" class="msg-toast-container">
            <transition-group name="msg-toast">
                <div
                    v-for="msg in normalMessages"
                    :key="msg.id"
                    class="msg-toast"
                    @click="dismissToast(msg)"
                >
                    <div class="msg-toast-icon">&#128172;</div>
                    <div class="msg-toast-body">
                        <div class="msg-toast-title">{{ senderLabel(msg) }}消息</div>
                        <div class="msg-toast-content">{{ msg.content }}</div>
                        <div class="msg-toast-time">{{ formatTime(msg.created_at) }}</div>
                    </div>
                    <div class="msg-toast-close">&times;</div>
                </div>
            </transition-group>
        </div>
    </teleport>
</template>

<script>
import { defineComponent, ref, computed, watch, nextTick } from 'vue';
import { tvNavigation } from '../../plugins/tvNavigation';

export default defineComponent({
    name: 'MessageOverlay',
    props: {
        // 强制消息（一次只显示一条）
        forcedMessage: { type: Object, default: null },
        // 普通通知列表
        normalMessages: { type: Array, default: () => [] }
    },
    emits: ['ack-forced', 'dismiss-toast'],
    setup(props, { emit }) {
        const forcedVisible = computed(() => !!props.forcedMessage);
        const forcedBtnRef = ref(null);

        // 获取发送者名称，兼容旧消息没有 sender_name 字段的情况
        function senderLabel(msg) {
            return (msg && msg.sender_name) || '管理员';
        }

        // 强制弹窗出现时自动聚焦按钮，TV 遥控器可直接按 OK 关闭
        watch(forcedVisible, (visible) => {
            if (visible) {
                nextTick(() => {
                    const btn = forcedBtnRef.value?.$el || forcedBtnRef.value;
                    if (!btn) return;
                    // 优先使用 TV 导航插件的 setFocus，确保遥控器焦点指示器正常显示
                    if (tvNavigation && tvNavigation.isTvMode && typeof tvNavigation.setFocus === 'function') {
                        tvNavigation.refresh();
                        tvNavigation.setFocus(btn);
                    } else if (typeof btn.focus === 'function') {
                        btn.focus();
                    }
                });
            }
        });

        function ackForced() {
            emit('ack-forced', props.forcedMessage);
        }

        function dismissToast(msg) {
            emit('dismiss-toast', msg);
        }

        function formatTime(t) {
            if (!t) return '';
            const d = new Date(t);
            const now = new Date();
            const diff = now - d;
            if (diff < 60000) return '刚刚';
            if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前';
            if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前';
            return d.toLocaleDateString('zh-CN');
        }

        function blockInput(e) {
            // 强制模式下，阻止除按钮外的所有交互
            if (e.target.classList.contains('msg-forced-btn')) return;
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
        }

        return {
            forcedVisible,
            forcedBtnRef,
            senderLabel,
            ackForced,
            dismissToast,
            formatTime,
            blockInput
        };
    }
});
</script>

<style>
/* 非 scoped：teleport 到 body 的元素不在组件 DOM 树内 */

/* ===== 强制弹窗 ===== */
.msg-overlay.msg-forced {
    position: fixed;
    top: 0; left: 0;
    width: 100vw; height: 100vh;
    z-index: 99998;
    background: rgba(0,0,0,0.65);
    display: flex;
    align-items: center;
    justify-content: center;
    backdrop-filter: blur(4px);
}
.msg-forced-box {
    background: #fff;
    border-radius: 16px;
    padding: 40px 48px;
    max-width: 480px;
    width: 90%;
    text-align: center;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    animation: msg-forced-in 0.3s ease-out;
}
@keyframes msg-forced-in {
    from { transform: scale(0.85); opacity: 0; }
    to { transform: scale(1); opacity: 1; }
}
.msg-forced-icon {
    font-size: 3em;
    margin-bottom: 12px;
}
/* 标题弱化：小字、浅色 */
.msg-forced-title {
    font-size: 0.88em;
    font-weight: 400;
    color: #999;
    margin-bottom: 16px;
}
/* 消息内容加粗突出 */
.msg-forced-content {
    font-size: 1.1em;
    font-weight: 600;
    color: #333;
    line-height: 1.6;
    margin-bottom: 12px;
    white-space: pre-wrap;
    word-break: break-word;
}
.msg-forced-time {
    font-size: 0.8em;
    color: #aaa;
    margin-bottom: 24px;
}
.msg-forced-btn {
    min-width: 160px;
}

/* ===== 普通通知 toast ===== */
.msg-toast-container {
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 99997;
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-width: 380px;
    pointer-events: none;
}
.msg-toast {
    background: #fff;
    border-radius: 12px;
    padding: 14px 16px;
    display: flex;
    align-items: flex-start;
    gap: 12px;
    box-shadow: 0 6px 24px rgba(0,0,0,0.15);
    cursor: pointer;
    pointer-events: auto;
    animation: msg-toast-in 0.3s ease-out;
    border-left: 4px solid #2080f0;
}
@keyframes msg-toast-in {
    from { transform: translateX(100%); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
}
.msg-toast-icon {
    font-size: 1.5em;
    flex-shrink: 0;
    margin-top: 2px;
}
.msg-toast-body {
    flex: 1;
    min-width: 0;
}
/* toast 标题弱化 */
.msg-toast-title {
    font-weight: 400;
    font-size: 0.82em;
    color: #999;
    margin-bottom: 4px;
}
/* toast 内容加粗 */
.msg-toast-content {
    font-size: 0.92em;
    font-weight: 600;
    color: #333;
    line-height: 1.4;
    word-break: break-word;
    white-space: pre-wrap;
}
.msg-toast-time {
    font-size: 0.75em;
    color: #aaa;
    margin-top: 4px;
}
.msg-toast-close {
    flex-shrink: 0;
    font-size: 1.2em;
    color: #ccc;
    cursor: pointer;
    line-height: 1;
    padding: 0 2px;
}
.msg-toast-close:hover {
    color: #999;
}

/* toast 过渡动画 */
.msg-toast-enter-active {
    animation: msg-toast-in 0.3s ease-out;
}
.msg-toast-leave-active {
    animation: msg-toast-in 0.25s ease-in reverse;
}

/* ===== 暗色模式适配 ===== */
.dark .msg-forced-box {
    background: #2a2a2a;
    color: #eee;
}
.dark .msg-forced-title { color: #888; }
.dark .msg-forced-content { color: #eee; }
.dark .msg-toast {
    background: #2a2a2a;
    border-left-color: #4098fc;
}
.dark .msg-toast-title { color: #888; }
.dark .msg-toast-content { color: #eee; }
</style>
