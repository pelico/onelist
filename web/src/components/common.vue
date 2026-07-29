<script type="text/javascript">
// 定义一些公共的属性和方法
let title = 'OneList';
let apiUrl = process.env.NODE_ENV === 'production' ? "" : 'http://127.0.0.1:5245';
let imgUrl = "https://image.tmdb.org"
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

// 未刮削视频的默认封面图片列表，随机分配
// 将自定义图片放在 web/public/images/default/ 目录下，文件名从 1.jpg 到 N.jpg
// 如果数组为空则回退到 not_video.jpg
const defaultPosters = [
    '/images/default/1.jpg',
    '/images/default/2.jpg',
    '/images/default/3.jpg',
    '/images/default/4.jpg',
    '/images/default/5.jpg',
    '/images/default/6.jpg',
    '/images/default/7.jpg',
    '/images/default/8.jpg',
];

// 获取电影/视频海报URL，无海报时从默认图列表中随机选取
function getPosterUrl(posterPath) {
    if (!posterPath || posterPath.length === 0 || posterPath === '/') {
        if (defaultPosters.length > 0) {
            const idx = Math.floor(Math.random() * defaultPosters.length);
            return defaultPosters[idx];
        }
        return '/images/not_video.jpg';
    }
    return imgUrl + "/t/p/w220_and_h330_face" + posterPath;
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
    setMsgHandler,
    applyConfig
}

export default api
</script>
