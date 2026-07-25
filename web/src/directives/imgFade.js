// 图片加载淡入指令
// 用法: <img v-img-fade :src="..." />
export const imgFadeDirective = {
    mounted(el) {
        el.style.opacity = '0'
        el.style.transition = 'opacity 0.4s ease'
        const show = () => {
            el.style.opacity = '1'
        }
        if (el.complete && el.naturalWidth > 0) {
            // 图片已缓存
            show()
        } else {
            el.addEventListener('load', show, { once: true })
            el.addEventListener('error', show, { once: true }) // 加载失败也显示，避免一直透明
        }
    },
    updated(el) {
        // src 变化时重置
        if (el.complete && el.naturalWidth > 0) {
            el.style.opacity = '1'
        }
    }
}

// 批量注册到所有 img 元素的自动淡入（不需要手动加指令）
export function setupImgFade(app) {
    app.directive('img-fade', imgFadeDirective)
}
