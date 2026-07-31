import { request } from './request'

// 系统配置
export const getConfigs = () => request.get('/v1/api/configs')

// 获取/保存配置（需鉴权）
export const getConfig = () => request.post('/v1/api/config/data')
export const saveConfig = (config) => request.post('/v1/api/config/save', config)

// 用户
export const getUserData = () => request.get('/v1/api/user/data')
export const login = (form) => request.post('/v1/api/user/login', form)
export const createUser = (form) => request.post('/v1/api/user/create', form)
export const getUserList = (params) => request.post('/v1/api/user/list', {}, params)
export const deleteUser = (id) => request.post('/v1/api/user/delete?id=' + id, {})
export const getUserById = (id) => request.post('/v1/api/user/id?id=' + id, {})

// 媒体库
export const getGalleryList = (params) => request.post('/v1/api/gallery/list', {}, params)
export const getAdminGalleryList = (params) => request.post('/v1/api/gallery/admin/list', {}, params)
export const getGalleryHost = (id) => request.post('/v1/api/gallery/host?id=' + id, {})
export const createGallery = (form) => request.post('/v1/api/gallery/create', form)
export const updateGallery = (id, form) => request.post('/v1/api/gallery/update?id=' + id, form)
export const deleteGallery = (id) => request.post('/v1/api/gallery/delete?id=' + id, {})

// 挂载目录（Work）
export const getWorkList = (galleryUid, params) => request.post('/v1/api/work/gallery/list?id=' + galleryUid, {}, params)
export const createWork = (form) => request.post('/v1/api/work/create', form)
export const updateWork = (id, form) => request.post('/v1/api/work/update?id=' + id, form)
export const deleteWork = (id) => request.post('/v1/api/work/delete?id=' + id, {})
export const renewWork = (id, mod) => request.post('/v1/api/work/renew?id=' + id + '&mod=' + mod, {})

// Alist 目录浏览
export const getAlistDir = (galleryUid, path) => request.get('/v1/api/gallery/alist_dir', { id: galleryUid, path })

// 电影
export const getMovieById = (id) => request.post('/v1/api/themovie/id?id=' + id, {})
export const getMovieList = (galleryUid, params) => request.post('/v1/api/themovie/gallery/list?id=' + galleryUid, {}, params)
export const searchMovie = (q, params) => request.post('/v1/api/themovie/search?q=' + q, {}, params)

// 电视剧
export const getTvById = (id) => request.post('/v1/api/thetv/id?id=' + id, {})
export const getTvList = (galleryUid, params) => request.post('/v1/api/thetv/gallery/list?id=' + galleryUid, {}, params)
export const searchTv = (q, params) => request.post('/v1/api/thetv/search?q=' + q, {}, params)

// 季
export const getSeasonById = (id) => request.post('/v1/api/theseason/id?id=' + id, {})

// 演员
export const getPersonById = (id) => request.post('/v1/api/theperson/id?id=' + id, {})

// 播放记录
export const renewPlayed = (dataType, dataId) => request.post('/v1/api/played/renew', { data_type: dataType, data_id: dataId })
export const getPlayedList = (dataType, params) => request.post('/v1/api/played/data/list?data_type=' + dataType, {}, params)

// 收藏/最爱
export const getHeartList = (dataType, params) => request.post('/v1/api/heart/data/list?data_type=' + dataType, {}, params)
export const getStarList = (dataType, params) => request.post('/v1/api/star/data/list?data_type=' + dataType, {}, params)

// 出错文件
export const getErrFileList = (workId, params) => request.post('/v1/api/errfile/ref/work/list?id=' + workId, {}, params)

// 阿里云盘
export const getAliOpenVideo = (form) => request.post('/v1/api/aliopen/video', form)

// 日志
export const getLogs = (params) => request.get('/v1/api/log/list', params)
export const cleanLogs = (days) => request.post('/v1/api/log/clean?days=' + (days || 7), {})

// 消息推送
export const sendMessage = (data) => request.post('/v1/api/message/send', data)
export const getMyMessages = () => request.get('/v1/api/message/mine')
export const markMessageRead = (id) => request.post('/v1/api/message/read?id=' + id, {})
export const markAllMessagesRead = () => request.post('/v1/api/message/read-all', {})
export const getMessageHistory = (params) => request.get('/v1/api/message/admin/history', params)
export const clearMessages = (params) => request.post('/v1/api/message/admin/clear', {}, params)
export const getWebhookInfo = () => request.get('/v1/api/message/admin/webhook')
export const toggleWebhook = (data) => request.post('/v1/api/message/admin/webhook/toggle', data)
export const regenerateWebhookToken = () => request.post('/v1/api/message/admin/webhook/regenerate', {})
