<template>
    <div v-if="loading" class="load"></div>
    <div v-else class="content">
        <div class="setting-header">
            <div class="setting-title">系统日志</div>
        </div>

        <div class="log-toolbar">
            <n-space align="center">
                <n-select v-model:value="filter.level" :options="levelOptions" placeholder="级别"
                    style="width: 120px" size="medium" clearable @update:value="onFilterChange" />
                <n-select v-model:value="filter.module" :options="moduleOptions" placeholder="模块"
                    style="width: 140px" size="medium" clearable @update:value="onFilterChange" />
                <n-input v-model:value="filter.keyword" placeholder="搜索关键词..." style="width: 240px"
                    size="medium" clearable @keyup.enter="onFilterChange" @clear="onFilterChange" />
                <n-button @click="onFilterChange" strong secondary>
                    <i class='bx bx-search'></i>
                </n-button>
                <n-button @click="cleanLogs" type="warning" secondary :loading="cleaning">
                    <i class='bx bx-trash'></i> 清理全部日志
                </n-button>
                <n-button @click="fetchData" secondary>
                    <i class='bx bx-refresh'></i>
                </n-button>
            </n-space>
            <div class="log-total">共 {{ total }} 条日志</div>
        </div>

        <div class="log-table-wrap">
            <n-data-table :columns="columns" :data="logs" :pagination="pagination"
                :bordered="false" size="small" striped :row-key="row => row.id" />
        </div>
    </div>
</template>

<script>
import { getCurrentInstance, onMounted, ref, reactive, h } from "vue";
import { NTag } from "naive-ui";

export default {
    name: "LogIndex",
    setup() {
        const { proxy } = getCurrentInstance();
        const loading = ref(true);
        const cleaning = ref(false);
        const logs = ref([]);
        const total = ref(0);

        const filter = reactive({
            level: null,
            module: null,
            keyword: "",
        });

        const pagination = reactive({
            page: 1,
            pageSize: 20,
            itemCount: 0,
            showSizePicker: true,
            pageSizes: [20, 50, 100],
            onChange: (page) => {
                pagination.page = page;
                fetchData();
            },
            onUpdatePageSize: (size) => {
                pagination.pageSize = size;
                pagination.page = 1;
                fetchData();
            }
        });

        const levelOptions = [
            { label: "INFO", value: "info" },
            { label: "WARN", value: "warn" },
            { label: "ERROR", value: "error" },
            { label: "DEBUG", value: "debug" },
        ];

        const moduleOptions = [
            { label: "system", value: "system" },
            { label: "alist", value: "alist" },
            { label: "thedb", value: "thedb" },
            { label: "work", value: "work" },
            { label: "proxy", value: "proxy" },
        ];

        const levelTagType = {
            info: "info",
            warn: "warning",
            error: "error",
            debug: "default",
        };

        const columns = [
            {
                title: "级别",
                key: "level",
                width: 90,
                render(row) {
                    return h(NTag, {
                        type: levelTagType[row.level] || "default",
                        size: "small",
                        bordered: false
                    }, { default: () => row.level.toUpperCase() });
                }
            },
            { title: "模块", key: "module", width: 100 },
            { title: "信息", key: "message", ellipsis: { tooltip: true } },
            { title: "详情", key: "detail", ellipsis: { tooltip: true } },
            {
                title: "时间",
                key: "created_at",
                width: 180,
                render(row) {
                    if (!row.created_at) return "";
                    return formatTime(row.created_at);
                }
            },
        ];

        function formatTime(t) {
            const d = new Date(t);
            if (isNaN(d.getTime())) return t;
            const pad = (n) => String(n).padStart(2, "0");
            return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
        }

        function fetchData() {
            loading.value = true;
            const params = new URLSearchParams();
            if (filter.level) params.set("level", filter.level);
            if (filter.module) params.set("module", filter.module);
            if (filter.keyword) params.set("keyword", filter.keyword);
            params.set("page", pagination.page);
            params.set("page_size", pagination.pageSize);

            proxy.axios.get(proxy.COMMON.apiUrl + `/v1/api/log/list?${params.toString()}`, {
                headers: { 'Authorization': proxy.$cookies.get("Authorization") }
            }).then(res => {
                loading.value = false;
                if (res.data.code == 200) {
                    logs.value = res.data.data || [];
                    total.value = res.data.total || 0;
                    pagination.itemCount = total.value;
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg || "加载失败");
                }
            }).catch((error) => {
                loading.value = false;
                proxy.COMMON.ShowMsg(error);
            });
        }

        function onFilterChange() {
            pagination.page = 1;
            fetchData();
        }

        function cleanLogs() {
            cleaning.value = true;
            proxy.axios.post(proxy.COMMON.apiUrl + '/v1/api/log/clean?all=true', {}, {
                headers: { 'Authorization': proxy.$cookies.get("Authorization") }
            }).then(res => {
                cleaning.value = false;
                if (res.data.code == 200) {
                    const count = res.data.data || 0;
                    proxy.COMMON.ShowMsg(`清理成功，共删除 ${count} 条日志`);
                    fetchData();
                } else {
                    proxy.COMMON.ShowMsg(res.data.msg || "清理失败");
                }
            }).catch((error) => {
                cleaning.value = false;
                proxy.COMMON.ShowMsg(error);
            });
        }

        onMounted(() => {
            fetchData();
        });

        return {
            loading,
            cleaning,
            logs,
            filter,
            columns,
            pagination,
            levelOptions,
            moduleOptions,
            fetchData,
            onFilterChange,
            cleanLogs,
        };
    }
}
</script>

<style scoped>
.setting-header {
    margin-bottom: 20px;
    margin-top: 20px;
}

.setting-title {
    font-size: 1.4em;
}

.log-toolbar {
    margin-bottom: 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.log-total {
    color: #888;
    font-size: 14px;
}

.log-table-wrap {
    background: var(--n-color, #fff);
    border-radius: 6px;
    padding: 8px;
}
</style>
