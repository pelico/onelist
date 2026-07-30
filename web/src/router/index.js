import { createRouter, createWebHistory } from 'vue-router';
import VueCookies from 'vue-cookies';

const routes = [
    {
        path: '/',
        name: 'Home',
        component: () => import('../views/HelloWorld.vue')
    },
    {
        path: '/gallerys',
        name: 'GalleryIndex',
        component: () => import('../views/gallery/GalleryIndex.vue'),
        meta: { requiresAdmin: true }
    },
    {
        path: '/gallerys/works',
        name: 'WorkIndex',
        component: () => import('../views/gallery/work/WorkIndex.vue'),
        meta: { requiresAdmin: true }
    },
    {
        path: '/list',
        name: 'VideoList',
        component: () => import('../views/video/VideoList.vue')
    },
    {
        path: '/video',
        name: 'VideoData',
        component: () => import('../views/video/VideoData.vue')
    },
    {
        path: '/season',
        name: 'season',
        component: () => import('../views/video/VideoSeason.vue')
    },
    {
        path: '/player',
        name: 'VideoPlayer',
        component: () => import('../views/video/VideoPlayer.vue')
    },
    {
        path: '/person',
        name: 'VideoPerson',
        component: () => import('../views/video/VideoPerson.vue')
    },
    {
        path: '/heart',
        name: 'UserHeart',
        component: () => import('../views/user/UserHeart.vue')
    },
    {
        path: '/star',
        name: 'UserStar',
        component: () => import('../views/user/UserStar.vue')
    },
    {
        path: '/played',
        name: 'UserPlayed',
        component: () => import('../views/user/UserPlayed.vue')
    },
    {
        path: '/users',
        name: 'UserIndex',
        component: () => import('../views/user/UserIndex.vue'),
        meta: { requiresAdmin: true }
    },
    {
        path: '/search',
        name: 'VideoSearch',
        component: () => import('../views/video/VideoSearch.vue')
    },
    {
        path: '/setting',
        name: 'SettingIndex',
        component: () => import('../views/setting/SettingIndex.vue'),
        meta: { requiresAdmin: true }
    },
    {
        path: '/logs',
        name: 'LogIndex',
        component: () => import('../views/log/LogIndex.vue'),
        meta: { requiresAdmin: true }
    },
    {
        path: '/play-stats',
        name: 'PlayStats',
        component: () => import('../views/playstats/PlayStats.vue'),
        meta: { requiresAdmin: true }
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
    // linkActiveClass: 'active',
    scrollBehavior(to, from, savedPosition) {
        return { x: 0, y: 0 }
    }
});

// 路由守卫：非管理员禁止访问管理页面
router.beforeEach((to, from, next) => {
    if (to.meta.requiresAdmin) {
        const isAdmin = VueCookies.get('is_admin') === 'true';
        if (!isAdmin) {
            next('/');
            return;
        }
    }
    next();
});

router.afterEach((to, from, next) => {
    window.scrollTo(0, 0);
});

export default router