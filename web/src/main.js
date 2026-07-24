import axios from 'axios'
import naive from 'naive-ui'
import { createApp } from 'vue'
import VueAxios from 'vue-axios'
import VueCookies from 'vue-cookies'
import App from './App.vue'
import router from './router'

import global from './components/common.vue'

// 电视遥控器导航支持
import tvNavigation from './plugins/tvNavigation'
import { setupTvFocusDirectives } from './directives/tvFocus'
import './styles/tv-focus.css'

import "node-snackbar/dist/snackbar.min.css"
import 'boxicons/css/boxicons.min.css'

const app = createApp(App);

app.config.globalProperties.$cookies = VueCookies;
app.config.globalProperties.COMMON = global;

// 注册电视导航插件
app.use(tvNavigation, { forceTvMode: false });

// 注册电视焦点指令
setupTvFocusDirectives(app);

app.use(naive).use(router).use(VueAxios, axios);
app.mount('#app');

