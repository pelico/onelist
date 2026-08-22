import axios from 'axios'

// 基础URL
const baseUrl = process.env.NODE_ENV === 'production' ? '' : 'http://127.0.0.1:5245'

// 创建axios实例，统一处理
const instance = axios.create({
  baseURL: baseUrl,
  timeout: 30000
})

// 请求拦截器：自动加 Authorization
instance.interceptors.request.use(config => {
  const token = getCookie('Authorization')
  if (token) {
    config.headers['Authorization'] = token
  }
  if (!config.headers['Content-Type']) {
    config.headers['Content-Type'] = 'application/json'
  }
  return config
})

// 响应拦截器：统一处理错误
instance.interceptors.response.use(
  response => {
    // 后端 JWT 过期返回 code=203，清除过期 cookie 并跳转登录
    if (response.data && response.data.code === 203) {
      document.cookie = 'Authorization=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
      document.cookie = 'UserId=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
      window.location.href = '/login'
      return Promise.reject(new Error('登录已过期'))
    }
    return response.data
  },
  error => {
    // HTTP 401 未授权：清除 cookie 并跳转登录
    if (error.response && error.response.status === 401) {
      document.cookie = 'Authorization=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
      document.cookie = 'UserId=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
      window.location.href = '/login'
      return Promise.reject(new Error('未授权，请重新登录'))
    }
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 读取cookie
function getCookie(name) {
  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) return parts.pop().split(';').shift()
  return null
}

// 封装请求方法
const request = {
  get(url, params) {
    return instance.get(url, { params })
  },
  post(url, data, params) {
    return instance.post(url, data || {}, { params })
  }
}

export { request, baseUrl }
export default instance
