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

// 获取电影/视频海报URL，无海报时返回默认图
function getPosterUrl(posterPath) {
    if (!posterPath || posterPath.length === 0 || posterPath === '/') {
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

// 暴露出这些属性和方法
export default {
    apiUrl,
    title,
    isMo,
    imgUrl,
    ShowMsg,
    getPosterUrl,
    setMsgHandler
}
</script>
