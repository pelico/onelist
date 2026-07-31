<template>
    <div class="content">
        <div class="content-header">
            <div class="content-header-title">消息中心</div>
            <div class="content-header-tool">
                <n-space justify="end" size="medium">
                    <n-button @click="loadHistory()" type="info">
                        <template #icon><i class='bx bx-analyse'></i></template>
                        刷新
                    </n-button>
                </n-space>
            </div>
        </div>

        <!-- 移动端 Tab 切换 -->
        <div class="mobile-tabs">
            <div class="mobile-tab" :class="{ active: activeTab === 'send' }" @click="activeTab = 'send'">
                <i class='bx bx-send'></i>
                <span>发消息</span>
            </div>
            <div class="mobile-tab" :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'">
                <i class='bx bx-list-ul'></i>
                <span>记录</span>
            </div>
            <div class="mobile-tab" :class="{ active: activeTab === 'setting' }" @click="activeTab = 'setting'">
                <i class='bx bx-cog'></i>
                <span>设置</span>
            </div>
            <div class="mobile-tab" :class="{ active: activeTab === 'webhook' }" @click="activeTab = 'webhook'">
                <i class='bx bx-link-alt'></i>
                <span>Webhook</span>
            </div>
        </div>

        <!-- 发送消息区域 -->
        <div class="send-section showContainer" :class="{ 'mobile-hide': isMobile && activeTab !== 'send' }">
            <div class="show-header">
                <div class="show-title"><h3>发送消息</h3></div>
            </div>
            <div class="form-body">
                <n-form :model="sendForm" label-placement="top" :label-width="undefined">
                    <n-form-item label="选择用户">
                        <n-select
                            v-model:value="sendForm.user_id"
                            :options="userOptions"
                            placeholder="选择要发送消息的用户"
                            filterable
                            size="large"
                            :loading="usersLoading"
                        />
                    </n-form-item>
                    <n-form-item label="消息内容">
                        <n-input
                            v-model:value="sendForm.content"
                            type="textarea"
                            placeholder="输入消息内容，例如：您已经看了很久了，请注意休息！"
                            :rows="3"
                            size="large"
                            @keydown="onContentKeydown"
                        />
                    </n-form-item>
                    <n-form-item label="消息类型">
                        <n-radio-group v-model:value="sendForm.priority">
                            <n-space>
                                <n-radio value="normal">普通通知</n-radio>
                                <n-radio value="forced">强制弹窗</n-radio>
                            </n-space>
                        </n-radio-group>
                    </n-form-item>
                    <n-form-item>
                        <n-button type="info" size="large" :loading="sending" @click="sendMessage()"
                            :disabled="!sendForm.user_id || !sendForm.content" style="width: 100%">
                            发送
                        </n-button>
                    </n-form-item>
                </n-form>
            </div>
        </div>

        <!-- 消息记录 -->
        <div class="history-section showContainer" :class="{ 'mobile-hide': isMobile && activeTab !== 'history' }">
            <div class="show-header">
                <div class="show-title"><h3>消息记录</h3></div>
                <div class="show-header-tool">
                    <n-space>
                        <n-select
                            v-model:value="filterUserId"
                            :options="userOptions"
                            placeholder="筛选用户"
                            clearable
                            filterable
                            size="small"
                            style="width: 140px"
                            @update:value="loadHistory()"
                        />
                        <n-popconfirm @positive-click="clearMessages()" :disabled="clearing">
                            <template #trigger>
                                <n-button size="small" type="warning" :loading="clearing">
                                    清空
                                </n-button>
                            </template>
                            确定清空所有消息记录？此操作不可恢复。
                        </n-popconfirm>
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
                    :single-line="false"
                />
            </div>
        </div>

        <!-- 消息设置 -->
        <div class="msg-setting-section showContainer" :class="{ 'mobile-hide': isMobile && activeTab !== 'setting' }">
            <div class="show-header">
                <div class="show-title"><h3>消息设置</h3></div>
            </div>
            <div class="form-body">
                <n-form label-placement="top" :label-width="undefined">
                    <n-form-item label="发送者名称">
                        <n-space :wrap="false" style="width: 100%">
                            <n-input
                                v-model:value="senderName"
                                placeholder="管理员"
                                size="large"
                                style="flex: 1"
                                @keydown.enter="saveSenderName()"
                            />
                            <n-button
                                type="info"
                                size="large"
                                :loading="senderNameSaving"
                                @click="saveSenderName()"
                            >保存</n-button>
                        </n-space>
                        <div style="margin-top: 8px; color: #999; font-size: 0.85em;">
                            显示为"{{ senderName || '管理员' }}发来一条消息"，留空默认"管理员"
                        </div>
                    </n-form-item>
                </n-form>
            </div>
        </div>

        <!-- Webhook 配置 -->
        <div class="webhook-section showContainer" :class="{ 'mobile-hide': isMobile && activeTab !== 'webhook' }">
            <div class="show-header">
                <div class="show-title"><h3>Webhook 接口</h3></div>
            </div>
            <div class="form-body">
                <n-alert type="info" :bordered="false" style="margin-bottom: 16px;">
                    通过 Webhook 接口，外部软件可以直接推送消息给指定用户，无需登录管理界面。
                </n-alert>
                <n-form label-placement="top" :label-width="undefined">
                    <n-form-item label="启用状态">
                        <n-space>
                            <n-switch :value="webhookEnabled" @update:value="toggleWebhook" />
                            <span style="color: #999;">{{ webhookEnabled ? '已启用' : '未启用' }}</span>
                        </n-space>
                    </n-form-item>
                    <n-form-item label="Webhook URL" v-if="webhookEnabled">
                        <n-input :value="webhookUrl" readonly copyable size="large" />
                    </n-form-item>
                    <n-form-item label="Token" v-if="webhookEnabled">
                        <n-input :value="webhookToken" readonly type="password" show-password-on="click" size="large" />
                    </n-form-item>
                    <n-form-item v-if="webhookEnabled">
                        <n-button @click="regenerateToken()" :loading="regenerating" type="warning" size="medium">
                            重新生成 Token
                        </n-button>
                    </n-form-item>
                    <n-form-item label="调用示例" v-if="webhookEnabled">
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
import { getUserList, sendMessage, getMessageHistory, clearMessages, getWebhookInfo, toggleWebhook, regenerateWebhookToken, getConfig, saveConfig } from "../../api/index";

export default {
    name: "MessageCenter",
    setup() {
        const { proxy } = getCurrentInstance();
        const message = useMessage();

        // 移动端 Tab 切换
        const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry/i.test(navigator.userAgent) || window.innerWidth <= 768;
        const activeTab = ref('send');

        // 用户列表
        const userOptions = ref([]);
        const usersLoading = ref(false);

        // 发送表单
        const sendForm = ref({
            user_id: null,
            content: "",
            priority: "normal"
        });
        const sending = ref(false);

        // 消息记录
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

        // 发送者名称配置
        const senderName = ref("管理员");
        const senderNameSaving = ref(false);

        const webhookExample = computed(() => {
            return JSON.stringify({
                "POST": webhookUrl.value,
                "Header": { "X-Webhook-Token": webhookToken.value || "YOUR_TOKEN" },
                "Body": {
                    "user_id": "用户UUID",
                    "content": "消息内容",
                    "priority": "normal 或 forced"
                }
            }, null, 2);
        });

        // 加载用户列表
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
                console.error("加载用户列表失败:", e);
            }
            usersLoading.value = false;
        }

        // 发送消息
        async function sendMessageAction() {
            if (!sendForm.value.user_id || !sendForm.value.content) return;
            sending.value = true;
            try {
                const res = await sendMessage({
                    user_id: sendForm.value.user_id,
                    content: sendForm.value.content,
                    priority: sendForm.value.priority
                });
                if (res.code === 200) {
                    message.success("消息发送成功！");
                    sendForm.value.content = "";
                    loadHistory();
                } else {
                    message.error(res.msg || "发送失败");
                }
            } catch (e) {
                message.error("发送失败: " + (e.message || e));
            }
            sending.value = false;
        }

        // Enter 发送（阻止换行）
        function onContentKeydown(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessageAction();
            }
        }

        // 加载消息记录
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
                console.error("加载消息记录失败:", e);
            }
            historyLoading.value = false;
        }

        // 清空消息
        async function clearMessagesAction() {
            clearing.value = true;
            try {
                const params = {};
                if (filterUserId.value) {
                    params.user_id = filterUserId.value;
                }
                const res = await clearMessages(params);
                if (res.code === 200) {
                    message.success(res.msg || "已清空");
                    loadHistory();
                } else {
                    message.error(res.msg || "清空失败");
                }
            } catch (e) {
                message.error("清空失败");
            }
            clearing.value = false;
        }

        // 加载 Webhook 信息
        async function loadWebhookInfo() {
            try {
                const res = await getWebhookInfo();
                if (res.code === 200 && res.data) {
                    webhookEnabled.value = res.data.enabled === "是";
                    webhookToken.value = res.data.token || "";
                    webhookUrl.value = res.data.url || "";
                }
            } catch (e) {
                console.error("加载 Webhook 信息失败:", e);
            }
        }

        // 切换 Webhook
        async function toggleWebhookAction(val) {
            try {
                const res = await toggleWebhook({ enabled: val ? "是" : "否" });
                if (res.code === 200) {
                    webhookEnabled.value = val;
                    message.success(val ? "Webhook 已启用" : "Webhook 已关闭");
                } else {
                    message.error(res.msg || "操作失败");
                }
            } catch (e) {
                message.error("操作失败");
            }
        }

        // 重新生成 Token
        async function regenerateTokenAction() {
            regenerating.value = true;
            try {
                const res = await regenerateWebhookToken();
                if (res.code === 200 && res.data) {
                    webhookToken.value = res.data.token;
                    message.success("Token 已重新生成");
                } else {
                    message.error(res.msg || "生成失败");
                }
            } catch (e) {
                message.error("生成失败");
            }
            regenerating.value = false;
        }

        // 加载发送者名称
        async function loadSenderName() {
            try {
                const res = await getConfig();
                if (res.code === 200 && res.data) {
                    senderName.value = res.data.sender_name || "管理员";
                }
            } catch (e) {
                console.error("加载发送者名称失败:", e);
            }
        }

        // 保存发送者名称
        async function saveSenderNameAction() {
            senderNameSaving.value = true;
            try {
                // 先获取完整配置，再更新 sender_name
                const res = await getConfig();
                if (res.code === 200 && res.data) {
                    const cfg = res.data;
                    cfg.sender_name = senderName.value;
                    const saveRes = await saveConfig(cfg);
                    if (saveRes.code === 200) {
                        message.success("发送者名称已保存！");
                    } else {
                        message.error(saveRes.msg || "保存失败");
                    }
                }
            } catch (e) {
                message.error("保存失败: " + (e.message || e));
            }
            senderNameSaving.value = false;
        }

        // 消息记录表格列
        const historyColumns = [
            {
                title: "时间",
                key: "created_at",
                width: 170,
                render(row) {
                    return h("span", {}, new Date(row.created_at).toLocaleString("zh-CN"));
                }
            },
            {
                title: "用户",
                key: "user_name",
                width: 140,
                ellipsis: { tooltip: true }
            },
            {
                title: "发送者",
                key: "sender_name",
                width: 100,
                ellipsis: { tooltip: true },
                render(row) {
                    return h("span", {}, row.sender_name || "管理员");
                }
            },
            {
                title: "内容",
                key: "content",
                ellipsis: { tooltip: true }
            },
            {
                title: "类型",
                key: "priority",
                width: 100,
                render(row) {
                    const typeMap = {
                        normal: { label: "普通", type: "info" },
                        forced: { label: "强制", type: "error" }
                    };
                    const t = typeMap[row.priority] || typeMap.normal;
                    return h(NTag, { type: t.type, size: "small", bordered: false }, { default: () => t.label });
                }
            },
            {
                title: "来源",
                key: "sender_type",
                width: 90,
                render(row) {
                    const typeMap = {
                        admin: { label: "管理员", type: "success" },
                        webhook: { label: "Webhook", type: "warning" }
                    };
                    const t = typeMap[row.sender_type] || { label: row.sender_type, type: "default" };
                    return h(NTag, { type: t.type, size: "small", bordered: false }, { default: () => t.label });
                }
            },
            {
                title: "状态",
                key: "read_at",
                width: 90,
                render(row) {
                    return h(NTag, {
                        type: row.read_at ? "default" : "warning",
                        size: "small",
                        bordered: false
                    }, { default: () => row.read_at ? "已读" : "未读" });
                }
            }
        ];

        onMounted(() => {
            loadUsers();
            loadHistory();
            loadWebhookInfo();
            loadSenderName();
        });

        return {
            isMobile,
            activeTab,
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
            regenerateToken: regenerateTokenAction,
            senderName,
            senderNameSaving,
            saveSenderName: saveSenderNameAction
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
.form-body {
    padding: 16px 24px;
}
.send-section, .history-section, .msg-setting-section, .webhook-section {
    margin-bottom: 8px;
}

/* 移动端 Tab 切换 */
.mobile-tabs {
    display: none;
}

/* 移动端优化 */
@media screen and (max-width: 768px) {
    .content-header {
        padding: 0 12px;
        min-height: 50px;
    }
    .content-header-title {
        font-size: 1.1em;
    }
    .showContainer {
        margin: 8px 12px;
    }
    .show-header {
        padding: 10px 14px;
    }
    .data-table {
        padding: 0 12px 12px;
        overflow-x: auto;
    }
    .form-body {
        padding: 12px 14px;
    }

    /* Tab 切换栏 */
    .mobile-tabs {
        display: flex;
        margin: 8px 12px;
        background: var(--n-color, #fff);
        border-radius: 8px;
        overflow: hidden;
        border: 1px solid rgba(0,0,0,0.08);
    }
    .mobile-tab {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 10px 4px;
        cursor: pointer;
        color: #999;
        font-size: 12px;
        gap: 3px;
        transition: all 0.2s;
        border-bottom: 2px solid transparent;
    }
    .mobile-tab i {
        font-size: 20px;
    }
    .mobile-tab.active {
        color: #2080f0;
        border-bottom-color: #2080f0;
        background: rgba(32, 128, 240, 0.04);
    }

    /* 隐藏非当前 Tab 的内容 */
    .mobile-hide {
        display: none !important;
    }
}
</style>
