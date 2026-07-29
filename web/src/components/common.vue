<script type="text/javascript">
import { ref } from 'vue';

// 定义一些公共的属性和方法
let title = 'OneList';
let apiUrl = process.env.NODE_ENV === 'production' ? "" : 'http://127.0.0.1:5245';
let imgUrl = "https://image.tmdb.org"
let customDefaultImage = true;
const isMo = /Android|webOS|iPhone|iPad|iPod|BlackBerry/i.test(navigator.userAgent);

// 全局消息回调，由 App.vue 注入 naive-ui 的 message
let _msgHandler = null;
function setMsgHandler(handler) {
    _msgHandler = handler;
}

function ShowMsg(msg) {
    if (_msgHandler) {
        _msgHandler(msg);
    } else {
        // 降级：控制台输出
        console.warn('[msg]', msg);
    }
}

// 自定义封面版本号：用 ref 保证响应式，递增时触发组件重新渲染以刷新图片 URL
let customImageVersion = ref(0);
function getPosterUrl(posterPath, videoId) {
    if (!posterPath || posterPath.length === 0 || posterPath === '/') {
        if (customDefaultImage && videoId) {
            return '/custom-image/' + videoId + '?v=' + customImageVersion.value;
        }
        return '/images/not_video.jpg';
    }
    // 优先使用本地路径，由后端 ImgServer 从 images/ 目录提供
    // 加载失败时由模板 @error 回退到远程 TMDB
    return "/t/p/w220_and_h330_face" + posterPath;
}

// 海报图片加载失败时回退到远程 TMDB（每个元素只回退一次，避免无限循环）
function onPosterError(event, posterPath) {
    const el = event.target;
    if (el._fallbackTried) return;
    el._fallbackTried = true;
    el.src = imgUrl + "/t/p/w220_and_h330_face" + posterPath;
    el.style.opacity = '1'; // 确保远程也失败时图片可见
}

function initConfig() {
    if (localStorage.getItem("title") != null) {
        title = localStorage.getItem("title")
    }
    if (localStorage.getItem("img_url") != null) {
        imgUrl = localStorage.getItem("img_url")
        if (process.env.NODE_ENV != 'production' && localStorage.getItem("img_url").length == 0) {
            imgUrl = apiUrl;
        }
    }
    // 自定义封面默认开启；仅在管理员明确设置过 "否" 时才关闭
    const customVal = localStorage.getItem("custom_default_image");
    if (customVal === "否") {
        customDefaultImage = false;
    }
}

initConfig()

// 热更新配置：直接更新内存中的 title/imgUrl，并派发事件让 App.vue 同步响应式状态
// 这样修改配置后无需 location.reload() 即可生效
function applyConfig(cfg) {
    if (!cfg) return;
    if (cfg.title != null) {
        title = cfg.title;
        api.title = title;
    }
    if (cfg.img_url !== undefined && cfg.img_url !== null) {
        let v = cfg.img_url;
        // 与 initConfig 保持一致：开发模式下空值回退到本地 apiUrl
        if (process.env.NODE_ENV != 'production' && v.length == 0) {
            v = apiUrl;
        }
        imgUrl = v;
        api.imgUrl = imgUrl;
    }
    if (cfg.custom_default_image !== undefined && cfg.custom_default_image !== null) {
        const newVal = cfg.custom_default_image === '是';
        // 开关状态变化时递增版本号，强制浏览器重新请求图片
        if (newVal !== customDefaultImage) {
            customImageVersion.value++;
        }
        customDefaultImage = newVal;
        localStorage.setItem('custom_default_image', cfg.custom_default_image);
    }
    if (typeof window !== 'undefined' && window.dispatchEvent) {
        window.dispatchEvent(new CustomEvent('onelist:config-changed', { detail: cfg }));
    }
}

// 暴露出这些属性和方法
const api = {
    apiUrl,
    title,
    isMo,
    imgUrl,
    ShowMsg,
    getPosterUrl,
    onPosterError,
    setMsgHandler,
    applyConfig
}

export default api
</script>
