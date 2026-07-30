<template>
    <div class="play-stats">
        <!-- 顶部工具栏 -->
        <div class="content-header">
            <div class="content-header-title">
                <i class='bx bx-bar-chart-alt-2'></i> 播放统计
            </div>
            <div class="content-header-tool">
                <n-space justify="end" align="center">
                    <n-select v-model:value="selectedUser" :options="userOptions" placeholder="全部用户" clearable
                        style="width: 140px" size="small" />
                    <n-date-picker v-model:value="dateRange" type="daterange" clearable size="small"
                        style="width: 260px" />
                    <n-button @click="fetchAll" type="info" size="small">
                        <i class='bx bx-refresh'></i> 刷新
                    </n-button>
                    <n-popconfirm @positive-click="handleClean">
                        <template #trigger>
                            <n-button type="warning" size="small">
                                <i class='bx bx-trash'></i> 清理记录
                            </n-button>
                        </template>
                        确认清理 {{ cleanDays }} 天前的播放记录？
                    </n-popconfirm>
                    <n-input-number v-model:value="cleanDays" :min="1" :max="365" size="small" style="width: 100px">
                        <template #suffix>天</template>
                    </n-input-number>
                </n-space>
            </div>
        </div>

        <!-- 概览卡片 -->
        <div class="summary-cards">
            <div class="summary-card">
                <div class="card-icon" style="background: #e8f5e9;">
                    <i class='bx bx-time-five' style="color: #4caf50;"></i>
                </div>
                <div class="card-info">
                    <div class="card-value">{{ formatDuration(todayTotal) }}</div>
                    <div class="card-label">今日观看</div>
                </div>
            </div>
            <div class="summary-card">
                <div class="card-icon" style="background: #e3f2fd;">
                    <i class='bx bx-calendar' style="color: #2196f3;"></i>
                </div>
                <div class="card-info">
                    <div class="card-value">{{ formatDuration(weekTotal) }}</div>
                    <div class="card-label">本周观看</div>
                </div>
            </div>
            <div class="summary-card">
                <div class="card-icon" style="background: #fff3e0;">
                    <i class='bx bx-play-circle' style="color: #ff9800;"></i>
                </div>
                <div class="card-info">
                    <div class="card-value">{{ todayCount }}</div>
                    <div class="card-label">今日播放次数</div>
                </div>
            </div>
            <div class="summary-card">
                <div class="card-icon" style="background: #fce4ec;">
                    <i class='bx bx-data' style="color: #e91e63;"></i>
                </div>
                <div class="card-info">
                    <div class="card-value">{{ totalCount }}</div>
                    <div class="card-label">总记录数</div>
                </div>
            </div>
        </div>

        <!-- 图表区域 -->
        <div class="charts-row">
            <!-- 每日观看时长 + 播放时间段（集成） -->
            <div class="chart-card chart-card-wide">
                <div class="chart-title-row">
                    <span class="chart-title">近 {{ timePeriodDays }} 天观看时长</span>
                    <n-radio-group v-model:value="timePeriodDays" size="small" @update:value="onPeriodChange">
                        <n-radio-button :value="7">7 天</n-radio-button>
                        <n-radio-button :value="30">30 天</n-radio-button>
                    </n-radio-group>
                </div>
                <!-- 柱状图 -->
                <div class="bar-chart">
                    <div v-for="day in dailyChart" :key="day.label" class="bar-item">
                        <div class="bar-value">{{ formatDuration(day.seconds) }}</div>
                        <div class="bar-wrapper">
                            <div class="bar" :style="{ height: barHeight(day.seconds) + '%' }"
                                :class="{ 'bar-zero': day.seconds === 0 }"></div>
                        </div>
                        <div class="bar-label">{{ day.label }}</div>
                    </div>
                </div>
                <!-- 纵向播放时间段 -->
                <div v-if="dailyTimePeriods.length > 0" class="v-timeline-section">
                    <div class="v-timeline-title">每日播放时间段</div>
                    <div class="v-timeline">
                        <!-- 时间轴 -->
                        <div class="v-axis">
                            <span v-for="t in axisTicks" :key="t" class="v-axis-label"
                                :style="{ top: tickPosition(t) + '%' }">{{ formatAxisTime(t) }}</span>
                        </div>
                        <!-- 每日列 -->
                        <div v-for="day in dailyTimePeriods" :key="day.date" class="v-day-col">
                            <div class="v-day-label">{{ formatDateLabel(day.date) }}</div>
                            <div class="v-day-track">
                                <div v-for="(seg, si) in day.segments" :key="si"
                                    class="v-seg"
                                    :class="seg.is_gap ? 'v-seg-gap' : 'v-seg-play'"
                                    :style="getVSegStyle(seg)"
                                    :title="seg.is_gap ? '未观看 ' + seg.duration + '秒' : '播放 ' + seg.duration + '秒'">
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="timeline-legend">
                        <span class="legend-item"><span class="legend-color" style="background:#5c6bc0;"></span> 播放时段</span>
                        <span class="legend-item"><span class="legend-color" style="background:#bdbdbd;"></span> 未观看间隙</span>
                    </div>
                </div>
            </div>

            <!-- 媒体库观看比例 -->
            <div class="chart-card">
                <div class="chart-title">媒体库观看比例</div>
                <div v-if="galleryStats.length === 0" class="empty-chart">暂无数据</div>
                <div v-else class="gallery-list">
                    <div v-for="(item, index) in galleryStats" :key="index" class="gallery-item">
                        <div class="gallery-info">
                            <span class="gallery-dot" :style="{ background: pieColors[index % pieColors.length] }"></span>
                            <span class="gallery-name">{{ item.gallery_title || item.gallery_uid || '未知' }}</span>
                        </div>
                        <div class="gallery-bar-wrap">
                            <div class="gallery-bar"
                                :style="{ width: galleryPercent(item.total_seconds) + '%', background: pieColors[index % pieColors.length] }">
                            </div>
                        </div>
                        <div class="gallery-value">{{ formatDuration(item.total_seconds) }} ({{ item.play_count }}次)</div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 详细记录列表 -->
        <div class="history-section">
            <div class="chart-title">观看记录</div>
            <n-data-table :columns="columns" :data="historyList" :bordered="false" :loading="historyLoading"
                size="small" :scroll-x="900" />
            <div class="data-footer">
                <n-space justify="end">
                    <n-pagination v-model:page="historyPage" :page-count="historyPageCount" :page-slot="5"
                        @update:page="fetchHistory" />
                </n-space>
            </div>
        </div>
    </div>
</template>

<script>
import { defineComponent, ref, computed, onMounted, watch, getCurrentInstance } from 'vue'

export default defineComponent({
    name: 'PlayStats',
    setup() {
        const { proxy } = getCurrentInstance()
        const selectedUser = ref(null)
        const userOptions = ref([])
        const dateRange = ref(null)
        const cleanDays = ref(30)

        // 概览数据
        const todayTotal = ref(0)
        const weekTotal = ref(0)
        const todayCount = ref(0)
        const totalCount = ref(0)

        // 图表数据
        const dailyChart = ref([])
        const galleryStats = ref([])

        // 每日时间段
        const dailyTimePeriods = ref([])
        const timePeriodDays = ref(7)

        // 历史列表
        const historyList = ref([])
        const historyLoading = ref(false)
        const historyPage = ref(1)
        const historyPageCount = ref(1)

        const pieColors = ['#4caf50', '#2196f3', '#ff9800', '#e91e63', '#9c27b0', '#00bcd4', '#ff5722', '#607d8b', '#795548', '#3f51b5']

        // 计算日期范围的辅助
        function getDateParams() {
            const params = {}
            if (dateRange.value && dateRange.value.length === 2) {
                const start = new Date(dateRange.value[0])
                const end = new Date(dateRange.value[1])
                end.setDate(end.getDate() + 1)
                params.start_date = start.toISOString().split('T')[0]
                params.end_date = end.toISOString().split('T')[0]
            }
            if (selectedUser.value) {
                params.user_id = selectedUser.value
            }
            return params
        }

        function buildQuery(params) {
            let qs = ''
            for (const k in params) {
                if (params[k]) qs += `&${k}=${params[k]}`
            }
            return qs
        }

        // 格式化时长
        function formatDuration(seconds) {
            if (!seconds || seconds <= 0) return '0分钟'
            const h = Math.floor(seconds / 3600)
            const m = Math.floor((seconds % 3600) / 60)
            if (h > 0) return `${h}小时${m}分钟`
            return `${m}分钟`
        }

        // 柱状图高度百分比
        function barHeight(seconds) {
            if (!dailyChart.value || dailyChart.value.length === 0) return 0
            const max = Math.max(...dailyChart.value.map(d => d.seconds), 1)
            return Math.max((seconds / max) * 100, seconds > 0 ? 4 : 0)
        }

        // 媒体库比例
        function galleryPercent(seconds) {
            if (!galleryStats.value || galleryStats.value.length === 0) return 0
            const max = Math.max(...galleryStats.value.map(d => d.total_seconds), 1)
            return Math.max((seconds / max) * 100, seconds > 0 ? 5 : 0)
        }

        // 表格列定义
        const columns = [
            { title: '用户', key: 'user_id', width: 100 },
            { title: '影片', key: 'title', ellipsis: { tooltip: true } },
            { title: '媒体库', key: 'gallery_title', width: 120, ellipsis: { tooltip: true } },
            {
                title: '类型', key: 'data_type', width: 70,
                render(row) {
                    return row.data_type === 'tv' ? '电视剧' : '电影'
                }
            },
            {
                title: '观看时长', key: 'duration', width: 100,
                render(row) {
                    return formatDuration(row.duration)
                }
            },
            {
                title: '播放位置', key: 'position', width: 100,
                render(row) {
                    return formatDuration(row.position)
                }
            },
            {
                title: '观看时间', key: 'started_at', width: 160,
                render(row) {
                    if (!row.started_at) return ''
                    const d = new Date(row.started_at)
                    return d.toLocaleString('zh-CN')
                }
            }
        ]

        // API 请求
        function apiPost(url) {
            return proxy.axios.post(url, {}, {
                headers: { 'Authorization': proxy.$cookies.get("Authorization") }
            })
        }

        // 获取用户列表
        function fetchUsers() {
            apiPost(`${proxy.COMMON.apiUrl}/v1/api/user/list?page=1&size=100`).then(res => {
                if (res.data.code === 200 && res.data.data) {
                    userOptions.value = res.data.data.map(u => ({
                        label: u.user_email,
                        value: u.user_email
                    }))
                }
            }).catch(() => { })
        }

        // 获取统计数据
        function fetchStats() {
            const params = getDateParams()
            const qs = buildQuery(params)

            apiPost(`${proxy.COMMON.apiUrl}/v1/api/play-history/stats?${qs}`).then(res => {
                if (res.data.code === 200) {
                    const list = res.data.data || []
                    totalCount.value = list.length

                    const today = new Date().toISOString().split('T')[0]
                    const todayRecords = list.filter(r => r.started_at && r.started_at.startsWith(today))
                    todayTotal.value = todayRecords.reduce((sum, r) => sum + (r.duration || 0), 0)
                    todayCount.value = todayRecords.length

                    const now = new Date()
                    const weekStart = new Date(now)
                    weekStart.setDate(now.getDate() - now.getDay())
                    weekStart.setHours(0, 0, 0, 0)
                    const weekRecords = list.filter(r => {
                        if (!r.started_at) return false
                        return new Date(r.started_at) >= weekStart
                    })
                    weekTotal.value = weekRecords.reduce((sum, r) => sum + (r.duration || 0), 0)

                    // 每日柱状图（根据 timePeriodDays 动态天数）
                    const days = []
                    const dayNames = ['日', '一', '二', '三', '四', '五', '六']
                    const totalDays = timePeriodDays.value
                    for (let i = totalDays - 1; i >= 0; i--) {
                        const d = new Date()
                        d.setDate(d.getDate() - i)
                        const dateStr = d.toISOString().split('T')[0]
                        const daySeconds = list
                            .filter(r => r.started_at && r.started_at.startsWith(dateStr))
                            .reduce((sum, r) => sum + (r.duration || 0), 0)
                        let label
                        if (i === 0) label = '今天'
                        else if (i === 1) label = '昨天'
                        else if (totalDays <= 7) label = `周${dayNames[d.getDay()]}`
                        else label = `${d.getMonth() + 1}/${d.getDate()}`
                        days.push({ label, seconds: daySeconds, date: dateStr })
                    }
                    dailyChart.value = days
                }
            }).catch(() => { })
        }

        // 获取媒体库统计
        function fetchGalleryStats() {
            const params = getDateParams()
            const qs = buildQuery(params)
            apiPost(`${proxy.COMMON.apiUrl}/v1/api/play-history/gallery-stats?${qs}`).then(res => {
                if (res.data.code === 200) {
                    galleryStats.value = res.data.data || []
                }
            }).catch(() => { })
        }

        // 获取历史记录列表
        function fetchHistory() {
            historyLoading.value = true
            const params = getDateParams()
            let qs = buildQuery(params)
            qs += `&page=${historyPage.value}&size=15`
            apiPost(`${proxy.COMMON.apiUrl}/v1/api/play-history/list?${qs}`).then(res => {
                historyLoading.value = false
                if (res.data.code === 200) {
                    historyList.value = res.data.data || []
                    const num = res.data.num || 0
                    historyPageCount.value = Math.ceil(num / 15) || 1
                }
            }).catch(() => {
                historyLoading.value = false
            })
        }

        // 清理记录
        function handleClean() {
            apiPost(`${proxy.COMMON.apiUrl}/v1/api/play-history/clean?days=${cleanDays.value}`).then(res => {
                if (res.data.code === 200) {
                    proxy.COMMON.ShowMsg(`清理成功，删除了 ${res.data.data} 条记录`)
                    fetchAll()
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg)
                }
            }).catch(() => { })
        }

        // 获取每日播放时间段
        function fetchDailyTimePeriods() {
            const params = getDateParams()
            const qs = buildQuery(params)
            apiPost(`${proxy.COMMON.apiUrl}/v1/api/play-history/daily-time-periods?${qs}`).then(res => {
                if (res.data.code === 200) {
                    dailyTimePeriods.value = res.data.data || []
                }
            }).catch(() => { })
        }

        // 纵向时间轴：动态计算时间范围
        const vTimeMin = computed(() => {
            let min = 1440
            dailyTimePeriods.value.forEach(day => {
                day.segments.forEach(seg => {
                    if (!seg.is_gap) {
                        const [h, m] = seg.start.split(':').map(Number)
                        min = Math.min(min, h * 60 + m)
                    }
                })
            })
            return min === 1440 ? 0 : min
        })

        const vTimeMax = computed(() => {
            let max = 0
            dailyTimePeriods.value.forEach(day => {
                day.segments.forEach(seg => {
                    const [h, m] = seg.end.split(':').map(Number)
                    max = Math.max(max, h * 60 + m)
                })
            })
            return max === 0 ? 1440 : max
        })

        const vTimeRange = computed(() => Math.max(vTimeMax.value - vTimeMin.value, 60))

        // 时间轴刻度（取整点到整点）
        const axisTicks = computed(() => {
            const startHour = Math.floor(vTimeMin.value / 60)
            const endHour = Math.ceil(vTimeMax.value / 60)
            const ticks = []
            for (let h = startHour; h <= endHour; h++) {
                ticks.push(h * 60)
            }
            return ticks
        })

        function tickPosition(minutes) {
            const range = vTimeRange.value
            const min = vTimeMin.value
            return ((minutes - min) / range) * 100
        }

        function formatAxisTime(minutes) {
            const h = Math.floor(minutes / 60)
            return `${h}:00`
        }

        // 纵向 segment 样式
        function getVSegStyle(seg) {
            const toMin = (t) => {
                const [h, m] = t.split(':').map(Number)
                return h * 60 + m
            }
            const startMin = toMin(seg.start)
            const endMin = toMin(seg.end)
            const range = vTimeRange.value
            const min = vTimeMin.value
            const top = ((startMin - min) / range) * 100
            const height = Math.max(((endMin - startMin) / range) * 100, 0.5)
            return { top: top + '%', height: height + '%' }
        }

        function formatDateLabel(dateStr) {
            const d = new Date(dateStr)
            const today = new Date()
            today.setHours(0, 0, 0, 0)
            const diff = Math.floor((today - d) / 86400000)
            if (diff === 0) return '今天'
            if (diff === 1) return '昨天'
            return `${d.getMonth() + 1}/${d.getDate()}`
        }

        function onPeriodChange(val) {
            timePeriodDays.value = val
            fetchStats()
            fetchDailyTimePeriods()
        }

        function fetchAll() {
            fetchStats()
            fetchGalleryStats()
            fetchDailyTimePeriods()
            fetchHistory()
        }

        watch([selectedUser, dateRange], () => {
            historyPage.value = 1
            fetchAll()
        })

        onMounted(() => {
            fetchUsers()
            fetchAll()
        })

        return {
            selectedUser,
            userOptions,
            dateRange,
            cleanDays,
            todayTotal,
            weekTotal,
            todayCount,
            totalCount,
            dailyChart,
            galleryStats,
            dailyTimePeriods,
            timePeriodDays,
            historyList,
            historyLoading,
            historyPage,
            historyPageCount,
            columns,
            pieColors,
            axisTicks,
            formatDuration,
            barHeight,
            galleryPercent,
            getVSegStyle,
            tickPosition,
            formatAxisTime,
            formatDateLabel,
            onPeriodChange,
            handleClean,
            fetchHistory,
            fetchAll
        }
    }
})
</script>

<style scoped>
.play-stats {
    padding: 16px;
}

.content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    flex-wrap: wrap;
    gap: 10px;
}

.content-header-title {
    font-size: 20px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 6px;
}

/* 概览卡片 */
.summary-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    margin-bottom: 24px;
}

.summary-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px;
    border-radius: 12px;
    background: var(--n-color, #fff);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.card-icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    flex-shrink: 0;
}

.card-value {
    font-size: 20px;
    font-weight: 700;
    line-height: 1.2;
}

.card-label {
    font-size: 13px;
    opacity: 0.6;
    margin-top: 2px;
}

/* 图表区域 */
.charts-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
    margin-bottom: 24px;
}

@media (max-width: 768px) {
    .charts-row {
        grid-template-columns: 1fr;
    }
}

.chart-card {
    padding: 20px;
    border-radius: 12px;
    background: var(--n-color, #fff);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.chart-card-wide {
    grid-column: 1 / -1;
}

.chart-title {
    font-size: 15px;
    font-weight: 600;
}

.chart-title-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

/* 柱状图 */
.bar-chart {
    display: flex;
    justify-content: space-around;
    align-items: flex-end;
    height: 180px;
    padding-top: 20px;
}

.bar-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    flex: 1;
    gap: 6px;
}

.bar-value {
    font-size: 11px;
    opacity: 0.7;
    white-space: nowrap;
}

.bar-wrapper {
    width: 28px;
    height: 120px;
    display: flex;
    align-items: flex-end;
    justify-content: center;
}

.bar {
    width: 100%;
    border-radius: 4px 4px 0 0;
    background: linear-gradient(180deg, #4caf50, #81c784);
    transition: height 0.4s ease;
    min-height: 2px;
}

.bar-zero {
    background: #e0e0e0;
}

.bar-label {
    font-size: 12px;
    opacity: 0.7;
}

/* 纵向播放时间段 */
.v-timeline-section {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid rgba(0,0,0,0.06);
}

.v-timeline-title {
    font-size: 13px;
    font-weight: 600;
    color: #666;
    margin-bottom: 12px;
}

.v-timeline {
    display: flex;
    gap: 0;
    height: 220px;
}

.v-axis {
    position: relative;
    width: 44px;
    flex-shrink: 0;
}

.v-axis-label {
    position: absolute;
    right: 6px;
    font-size: 10px;
    color: #999;
    transform: translateY(-50%);
    white-space: nowrap;
}

.v-day-col {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 0;
}

.v-day-label {
    font-size: 11px;
    color: #888;
    margin-bottom: 4px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
    text-align: center;
}

.v-day-track {
    flex: 1;
    position: relative;
    width: 70%;
    max-width: 40px;
    background: rgba(0,0,0,0.03);
    border-radius: 4px;
}

.v-seg {
    position: absolute;
    left: 0;
    right: 0;
    border-radius: 3px;
    min-height: 2px;
}

.v-seg-play {
    background: #5c6bc0;
}

.v-seg-gap {
    background: #bdbdbd;
    opacity: 0.6;
}

/* 媒体库比例 */
.empty-chart {
    text-align: center;
    padding: 40px;
    opacity: 0.5;
}

.gallery-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.gallery-item {
    display: flex;
    align-items: center;
    gap: 10px;
}

.gallery-info {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 100px;
    flex-shrink: 0;
}

.gallery-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
}

.gallery-name {
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100px;
}

.gallery-bar-wrap {
    flex: 1;
    height: 16px;
    background: rgba(0, 0, 0, 0.04);
    border-radius: 8px;
    overflow: hidden;
}

.gallery-bar {
    height: 100%;
    border-radius: 8px;
    transition: width 0.4s ease;
}

.gallery-value {
    font-size: 12px;
    opacity: 0.7;
    white-space: nowrap;
    min-width: 100px;
    text-align: right;
}

/* 历史记录 */
.history-section {
    padding: 20px;
    border-radius: 12px;
    background: var(--n-color, #fff);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.data-footer {
    margin-top: 12px;
}

.timeline-legend {
    display: flex;
    gap: 20px;
    margin-top: 12px;
    justify-content: center;
}

.legend-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #666;
}

.legend-color {
    display: inline-block;
    width: 12px;
    height: 12px;
    border-radius: 2px;
}
</style>
