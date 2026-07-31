<template>
    <teleport to="body">
        <!-- 寮哄埗寮圭獥妯″紡 -->
        <div v-if="forcedVisible" class="msg-overlay msg-forced" @keydown="blockInput" @click="blockInput">
            <div class="msg-forced-box">
                <div class="msg-forced-icon">&#9993;</div>
                <div class="msg-forced-title">绠＄悊鍛樺彂鏉ヤ竴鏉℃秷鎭?/div>
                <div class="msg-forced-content">{{ forcedMessage.content }}</div>
                <div class="msg-forced-time">{{ formatTime(forcedMessage.created_at) }}</div>
                <n-button ref="forcedBtnRef" type="info" size="large" class="msg-forced-btn" @click="ackForced">鎴戠煡閬撲簡</n-button>
            </div>
        </div>

        <!-- 鏅€氶€氱煡锛堝彸涓婅鍫嗗彔 toast锛?-->
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
                        <div class="msg-toast-title">绠＄悊鍛樻秷鎭?/div>
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

export default defineComponent({
    name: 'MessageOverlay',
    props: {
        // 寮哄埗娑堟伅锛堜竴娆″彧鏄剧ず涓€鏉★級
        forcedMessage: { type: Object, default: null },
        // 鏅€氶€氱煡鍒楄〃
        normalMessages: { type: Array, default: () => [] }
    },
    emits: ['ack-forced', 'dismiss-toast'],
    setup(props, { emit }) {
        const forcedVisible = computed(() => !!props.forcedMessage);
        const forcedBtnRef = ref(null);

        // 寮哄埗寮圭獥鍑虹幇鏃惰嚜鍔ㄨ仛鐒︽寜閽紝TV 閬ユ帶鍣ㄥ彲鐩存帴鎸?OK 鍏抽棴
        watch(forcedVisible, (visible) => {
            if (visible) {
                nextTick(() => {
                    const btn = forcedBtnRef.value?.$el || forcedBtnRef.value;
                    if (btn && typeof btn.focus === 'function') {
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
            if (diff < 60000) return '鍒氬垰';
            if (diff < 3600000) return Math.floor(diff / 60000) + ' 鍒嗛挓鍓?;
            if (diff < 86400000) return Math.floor(diff / 3600000) + ' 灏忔椂鍓?;
            return d.toLocaleDateString('zh-CN');
        }

        function blockInput(e) {
            // 寮哄埗妯″紡涓嬶紝闃绘闄ゆ寜閽鐨勬墍鏈変氦浜?            if (e.target.classList.contains('msg-forced-btn')) return;
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
        }

        return {
            forcedVisible,
            forcedBtnRef,
            ackForced,
            dismissToast,
            formatTime,
            blockInput
        };
    }
});
</script>

<style>
/* 闈?scoped锛歵eleport 鍒?body 鐨勫厓绱犱笉鍦ㄧ粍浠?DOM 鏍戝唴 */

/* ===== 寮哄埗寮圭獥 ===== */
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
.msg-forced-title {
    font-size: 1.2em;
    font-weight: 600;
    color: #333;
    margin-bottom: 16px;
}
.msg-forced-content {
    font-size: 1.05em;
    color: #555;
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

/* ===== 鏅€氶€氱煡 toast ===== */
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
.msg-toast-title {
    font-weight: 600;
    font-size: 0.9em;
    color: #333;
    margin-bottom: 4px;
}
.msg-toast-content {
    font-size: 0.88em;
    color: #555;
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

/* toast 杩囨浮鍔ㄧ敾 */
.msg-toast-enter-active {
    animation: msg-toast-in 0.3s ease-out;
}
.msg-toast-leave-active {
    animation: msg-toast-in 0.25s ease-in reverse;
}

/* ===== 鏆楄壊妯″紡閫傞厤 ===== */
.dark .msg-forced-box {
    background: #2a2a2a;
    color: #eee;
}
.dark .msg-forced-title { color: #eee; }
.dark .msg-forced-content { color: #ccc; }
.dark .msg-toast {
    background: #2a2a2a;
    border-left-color: #4098fc;
}
.dark .msg-toast-title { color: #eee; }
.dark .msg-toast-content { color: #ccc; }
</style>
