<template>
    <div class="content">
        <div class="content-header">
            <div class="content-header-title">娑堟伅涓績</div>
            <div class="content-header-tool">
                <n-space justify="end" size="medium">
                    <n-button @click="loadHistory()" type="info">
                        <template #icon><i class='bx bx-analyse'></i></template>
                        鍒锋柊
                    </n-button>
                </n-space>
            </div>
        </div>

        <!-- 鍙戦€佹秷鎭尯鍩?-->
        <div class="send-section showContainer">
            <div class="show-header">
                <div class="show-title"><h3>鍙戦€佹秷鎭?/h3></div>
            </div>
            <div style="padding: 16px 24px;">
                <n-form :model="sendForm" label-placement="left" label-width="auto">
                    <n-form-item label="閫夋嫨鐢ㄦ埛">
                        <n-select
                            v-model:value="sendForm.user_id"
                            :options="userOptions"
                            placeholder="閫夋嫨瑕佸彂閫佹秷鎭殑鐢ㄦ埛"
                            filterable
                            size="large"
                            :loading="usersLoading"
                        />
                    </n-form-item>
                    <n-form-item label="娑堟伅鍐呭">
                        <n-input
                            v-model:value="sendForm.content"
                            type="textarea"
                            placeholder="杈撳叆娑堟伅鍐呭锛屼緥濡傦細鎮ㄥ凡缁忕湅浜嗗緢涔呬簡锛岃娉ㄦ剰浼戞伅锛?
                            :rows="3"
                            size="large"
                            @keydown="onContentKeydown"
                        />
                    </n-form-item>
                    <n-form-item label="娑堟伅绫诲瀷">
                        <n-radio-group v-model:value="sendForm.priority">
                            <n-space>
                                <n-radio value="normal">
                                    <n-tooltip trigger="hover">
                                        <template #trigger>鏅€氶€氱煡锛堝彸涓婅瑙掓爣鎻愮ず锛?/template>
                                        鏅€氶€氱煡
                                    </n-tooltip>
                                </n-radio>
                                <n-radio value="forced">
                                    <n-tooltip trigger="hover">
                                        <template #trigger>寮哄埗寮圭獥锛堝叏灞忚鐩栵紝闇€纭鍏抽棴锛?/template>
                                        寮哄埗寮圭獥
                                    </n-tooltip>
                                </n-radio>
                            </n-space>
                        </n-radio-group>
                    </n-form-item>
                    <n-form-item>
                        <n-button type="info" size="large" :loading="sending" @click="sendMessage()"
                            :disabled="!sendForm.user_id || !sendForm.content">
                            鍙戦€?                        </n-button>
                    </n-form-item>
                </n-form>
            </div>
        </div>

        <!-- 娑堟伅璁板綍 -->
        <div class="history-section showContainer">
            <div class="show-header">
                <div class="show-title"><h3>娑堟伅璁板綍</h3></div>
                <div class="show-header-tool">
                    <n-space>
                        <n-select
                            v-model:value="filterUserId"
                            :options="userOptions"
                            placeholder="绛涢€夌敤鎴?
                            clearable
                            filterable
                            size="small"
                            style="width: 180px"
                            @update:value="loadHistory()"
                        />
                        <n-popconfirm @positive-click="clearMessages()" :disabled="clearing">
                            <template #trigger>
                                <n-button size="small" type="warning" :loading="clearing">
                                    娓呯┖璁板綍
                                </n-button>
                            </template>
                            纭畾娓呯┖鎵€鏈夋秷鎭褰曪紵姝ゆ搷浣滀笉鍙仮澶嶃€?                        </n-popconfirm>
                    </n-space>
                </div>
            </div>
            <div class="data-table">
                <n-data-table
                    :columns="historyColumns"
                    :data="historyData"
                    :pagination="historyPagination"
                    :bordered="true"
                    :loading="historyLoading"
                />
            </div>
        </div>

        <!-- Webhook 閰嶇疆 -->
        <div class="webhook-section showContainer">
            <div class="show-header">
                <div class="show-title"><h3>Webhook 鎺ュ彛</h3></div>
            </div>
            <div style="padding: 16px 24px;">
                <n-alert type="info" :bordered="false" style="margin-bottom: 16px;">
                    閫氳繃 Webhook 鎺ュ彛锛屽閮ㄨ蒋浠跺彲浠ョ洿鎺ユ帹閫佹秷鎭粰鎸囧畾鐢ㄦ埛锛屾棤闇€鐧诲綍绠＄悊鐣岄潰銆?                </n-alert>
                <n-form label-placement="left" label-width="auto">
                    <n-form-item label="鍚敤鐘舵€?>
                        <n-switch :value="webhookEnabled" @update:value="toggleWebhook" />
                        <span style="margin-left: 8px; color: #999;">{{ webhookEnabled ? '宸插惎鐢? : '鏈惎鐢? }}</span>
                    </n-form-item>
                    <n-form-item label="Webhook URL" v-if="webhookEnabled">
                        <n-input :value="webhookUrl" readonly copyable size="large" />
                    </n-form-item>
                    <n-form-item label="Token" v-if="webhookEnabled">
                        <n-input :value="webhookToken" readonly type="password" show-password-on="click" size="large" />
                    </n-form-item>
                    <n-form-item v-if="webhookEnabled">
                        <n-space>
                            <n-button @click="regenerateToken()" :loading="regenerating" type="warning" size="small">
                                閲嶆柊鐢熸垚 Token
                            </n-button>
                        </n-space>
                    </n-form-item>
                    <n-form-item label="璋冪敤绀轰緥" v-if="webhookEnabled">
                        <n-code :code="webhookExample" language="json" />
                    </n-form-item>
                </n-form>
            </div>
        </div>
    </div>
</template>

<script>
import { computed, getCurrentInstance, h, onMounted, reactive, ref } from "vue";
import { NButton, NTag, useMessage } from "naive-ui";
import { getUserList, sendMessage, getMessageHistory, clearMessages, getWebhookInfo, toggleWebhook, regenerateWebhookToken } from "../../api/index";

export default {
    name: "MessageCenter",
    setup() {
        const { proxy } = getCurrentInstance();
        const message = useMessage();

        // 鐢ㄦ埛鍒楄〃
        const userOptions = ref([]);
        const usersLoading = ref(false);

        // 鍙戦€佽〃鍗?        const sendForm = ref({
            user_id: null,
            content: "",
            priority: "normal"
        });
        const sending = ref(false);

        // 娑堟伅璁板綍
        const historyData = ref([]);
        const historyLoading = ref(false);
        const filterUserId = ref(null);
        const clearing = ref(false);
        const historyPagination = reactive({
            page: 1,
            pageSize: 20,
            pageCount: 1,
            itemCount: 0,
            showSizePicker: false,
            onUpdatePage: (page) => {
                historyPagination.page = page;
                loadHistory();
            }
        });

        // Webhook
        const webhookEnabled = ref(false);
        const webhookToken = ref("");
        const webhookUrl = ref("");
        const regenerating = ref(false);

        const webhookExample = computed(() => {
            return JSON.stringify({
                "POST": webhookUrl.value,
                "Header": { "X-Webhook-Token": webhookToken.value || "YOUR_TOKEN" },
                "Body": {
                    "user_id": "鐢ㄦ埛UUID",
                    "content": "娑堟伅鍐呭",
                    "priority": "normal 鎴?forced"
                }
            }, null, 2);
        });

        // 鍔犺浇鐢ㄦ埛鍒楄〃
        async function loadUsers() {
            usersLoading.value = true;
            try {
                const res = await getUserList({ page: 1, page_size: 200 });
                if (res.code === 200 && res.data) {
                    const list = res.data.list || res.data || [];
                    userOptions.value = list.map(u => ({
                        label: `${u.user_name} (${u.user_email})`,
                        value: u.user_id
                    }));
                }
            } catch (e) {
                console.error("鍔犺浇鐢ㄦ埛鍒楄〃澶辫触:", e);
            }
            usersLoading.value = false;
        }

        // 鍙戦€佹秷鎭?        async function sendMessageAction() {
            if (!sendForm.value.user_id || !sendForm.value.content) return;
            sending.value = true;
            try {
                const res = await sendMessage({
                    user_id: sendForm.value.user_id,
                    content: sendForm.value.content,
                    priority: sendForm.value.priority
                });
                if (res.code === 200) {
                    message.success("娑堟伅鍙戦€佹垚鍔燂紒");
                    sendForm.value.content = "";
                    loadHistory();
                } else {
                    message.error(res.msg || "鍙戦€佸け璐?);
                }
            } catch (e) {
                message.error("鍙戦€佸け璐? " + (e.message || e));
            }
            sending.value = false;
        }

        // Enter 鍙戦€侊紙闃绘鎹㈣锛?        function onContentKeydown(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessageAction();
            }
        }

        // 鍔犺浇娑堟伅璁板綍
        async function loadHistory() {
            historyLoading.value = true;
            try {
                const params = {
                    page: historyPagination.page,
                    page_size: historyPagination.pageSize
                };
                if (filterUserId.value) {
                    params.user_id = filterUserId.value;
                }
                const res = await getMessageHistory(params);
                if (res.code === 200 && res.data) {
                    historyData.value = res.data.list || [];
                    historyPagination.itemCount = res.data.total || 0;
                    historyPagination.pageCount = Math.ceil((res.data.total || 0) / historyPagination.pageSize);
                }
            } catch (e) {
                console.error("鍔犺浇娑堟伅璁板綍澶辫触:", e);
            }
            historyLoading.value = false;
        }

        // 娓呯┖娑堟伅
        async function clearMessagesAction() {
            clearing.value = true;
            try {
                const params = {};
                if (filterUserId.value) {
                    params.user_id = filterUserId.value;
                }
                const res = await clearMessages(params);
                if (res.code === 200) {
                    message.success(res.msg || "宸叉竻绌?);
                    loadHistory();
                } else {
                    message.error(res.msg || "娓呯┖澶辫触");
                }
            } catch (e) {
                message.error("娓呯┖澶辫触");
            }
            clearing.value = false;
        }

        // 鍔犺浇 Webhook 淇℃伅
        async function loadWebhookInfo() {
            try {
                const res = await getWebhookInfo();
                if (res.code === 200 && res.data) {
                    webhookEnabled.value = res.data.enabled === "鏄?;
                    webhookToken.value = res.data.token || "";
                    webhookUrl.value = res.data.url || "";
                }
            } catch (e) {
                console.error("鍔犺浇 Webhook 淇℃伅澶辫触:", e);
            }
        }

        // 鍒囨崲 Webhook
        async function toggleWebhookAction(val) {
            try {
                const res = await toggleWebhook({ enabled: val ? "鏄? : "鍚? });
                if (res.code === 200) {
                    webhookEnabled.value = val;
                    message.success(val ? "Webhook 宸插惎鐢? : "Webhook 宸插叧闂?);
                } else {
                    message.error(res.msg || "鎿嶄綔澶辫触");
                }
            } catch (e) {
                message.error("鎿嶄綔澶辫触");
            }
        }

        // 閲嶆柊鐢熸垚 Token
        async function regenerateTokenAction() {
            regenerating.value = true;
            try {
                const res = await regenerateWebhookToken();
                if (res.code === 200 && res.data) {
                    webhookToken.value = res.data.token;
                    message.success("Token 宸查噸鏂扮敓鎴?);
                } else {
                    message.error(res.msg || "鐢熸垚澶辫触");
                }
            } catch (e) {
                message.error("鐢熸垚澶辫触");
            }
            regenerating.value = false;
        }

        // 娑堟伅璁板綍琛ㄦ牸鍒?        const historyColumns = [
            {
                title: "鏃堕棿",
                key: "created_at",
                width: 170,
                render(row) {
                    return h("span", {}, new Date(row.created_at).toLocaleString("zh-CN"));
                }
            },
            {
                title: "鐢ㄦ埛",
                key: "user_name",
                width: 140,
                ellipsis: { tooltip: true }
            },
            {
                title: "鍐呭",
                key: "content",
                ellipsis: { tooltip: true }
            },
            {
                title: "绫诲瀷",
                key: "priority",
                width: 100,
                render(row) {
                    const typeMap = {
                        normal: { label: "鏅€?, type: "info" },
                        forced: { label: "寮哄埗", type: "error" }
                    };
                    const t = typeMap[row.priority] || typeMap.normal;
                    return h(NTag, { type: t.type, size: "small", bordered: false }, { default: () => t.label });
                }
            },
            {
                title: "鏉ユ簮",
                key: "sender_type",
                width: 90,
                render(row) {
                    const typeMap = {
                        admin: { label: "绠＄悊鍛?, type: "success" },
                        webhook: { label: "Webhook", type: "warning" }
                    };
                    const t = typeMap[row.sender_type] || { label: row.sender_type, type: "default" };
                    return h(NTag, { type: t.type, size: "small", bordered: false }, { default: () => t.label });
                }
            },
            {
                title: "鐘舵€?,
                key: "read_at",
                width: 90,
                render(row) {
                    return h(NTag, {
                        type: row.read_at ? "default" : "warning",
                        size: "small",
                        bordered: false
                    }, { default: () => row.read_at ? "宸茶" : "鏈" });
                }
            }
        ];

        onMounted(() => {
            loadUsers();
            loadHistory();
            loadWebhookInfo();
        });

        return {
            userOptions,
            usersLoading,
            sendForm,
            sending,
            sendMessage: sendMessageAction,
            onContentKeydown,
            historyData,
            historyLoading,
            historyColumns,
            historyPagination,
            filterUserId,
            clearing,
            clearMessages: clearMessagesAction,
            loadHistory,
            webhookEnabled,
            webhookToken,
            webhookUrl,
            webhookExample,
            regenerating,
            toggleWebhook: toggleWebhookAction,
            regenerateToken: regenerateTokenAction
        };
    }
};
</script>

<style scoped>
.content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    min-height: 60px;
    padding: 0 24px;
}
.content-header-title {
    font-size: 1.4em;
    font-weight: 600;
}
.showContainer {
    background: var(--n-color, #fff);
    border-radius: 8px;
    margin: 16px 24px;
    overflow: hidden;
}
.show-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 24px;
    border-bottom: 1px solid rgba(0,0,0,0.06);
}
.show-title h3 {
    margin: 0;
    font-size: 1.1em;
}
.show-header-tool {
    display: flex;
    align-items: center;
}
.data-table {
    padding: 0 24px 16px;
}
.send-section, .history-section, .webhook-section {
    margin-bottom: 8px;
}
</style>
