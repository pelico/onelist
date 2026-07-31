package controllers

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/config"
	"gorm.io/gorm"
)

// SendMessage 管理员发送消息给指定用户
func SendMessage(c *gin.Context) {
	var req struct {
		UserId   string `json:"user_id" binding:"required"`
		Content  string `json:"content" binding:"required"`
		Priority string `json:"priority"` // "normal" | "forced"
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误: " + err.Error(), "data": nil})
		return
	}
	if req.Priority == "" {
		req.Priority = "normal"
	}
	if req.Priority != "normal" && req.Priority != "forced" {
		c.JSON(200, gin.H{"code": 201, "msg": "priority 只能是 normal 或 forced", "data": nil})
		return
	}

	db := database.NewDb()

	// 查询目标用户名字
	var user models.User
	if err := db.Where("user_id = ?", req.UserId).First(&user).Error; err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "用户不存在", "data": nil})
		return
	}

	msg := models.Message{
		UserId:     req.UserId,
		UserName:   user.UserName,
		Content:    req.Content,
		Priority:   req.Priority,
		SenderType: "admin",
		SenderName: config.SenderName,
	}
	if err := db.Create(&msg).Error; err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "发送失败: " + err.Error(), "data": nil})
		return
	}

	// SSE 实时推送
	pushMessageToUser(req.UserId, msg)

	c.JSON(200, gin.H{"code": 200, "msg": "发送成功!", "data": msg})
}

// WebhookMessage Webhook 接口，供外部软件推送消息
func WebhookMessage(c *gin.Context) {
	// 检查 webhook 是否启用
	if config.WebhookEnabled != "是" {
		c.JSON(200, gin.H{"code": 201, "msg": "Webhook 未启用", "data": nil})
		return
	}

	// 验证 token
	token := c.GetHeader("X-Webhook-Token")
	if token == "" {
		token = c.Query("token")
	}
	if config.WebhookToken == "" || token != config.WebhookToken {
		c.JSON(403, gin.H{"code": 403, "msg": "Token 无效", "data": nil})
		return
	}

	var req struct {
		UserId   string `json:"user_id" binding:"required"`
		Content  string `json:"content" binding:"required"`
		Priority string `json:"priority"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误: " + err.Error(), "data": nil})
		return
	}
	if req.Priority == "" {
		req.Priority = "normal"
	}
	if req.Priority != "normal" && req.Priority != "forced" {
		c.JSON(200, gin.H{"code": 201, "msg": "priority 只能是 normal 或 forced", "data": nil})
		return
	}

	db := database.NewDb()

	// 查询目标用户名字
	var user models.User
	if err := db.Where("user_id = ?", req.UserId).First(&user).Error; err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "用户不存在", "data": nil})
		return
	}

	msg := models.Message{
		UserId:     req.UserId,
		UserName:   user.UserName,
		Content:    req.Content,
		Priority:   req.Priority,
		SenderType: "webhook",
		SenderName: config.SenderName,
	}
	if err := db.Create(&msg).Error; err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "发送失败: " + err.Error(), "data": nil})
		return
	}

	// SSE 实时推送
	pushMessageToUser(req.UserId, msg)

	c.JSON(200, gin.H{"code": 200, "msg": "发送成功!", "data": msg})
}

// GetMyMessages 获取当前用户的未读消息
func GetMyMessages(c *gin.Context) {
	userId := c.GetString("UserId")
	db := database.NewDb()

	var messages []models.Message
	db.Where("user_id = ? AND read_at IS NULL", userId).Order("created_at DESC").Limit(50).Find(&messages)

	c.JSON(200, gin.H{"code": 200, "msg": "获取成功!", "data": messages})
}

// GetMessageHistory 管理员获取消息记录
func GetMessageHistory(c *gin.Context) {
	userId := c.Query("user_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	if page <= 0 {
		page = 1
	}
	if pageSize <= 0 || pageSize > 100 {
		pageSize = 20
	}

	db := database.NewDb()
	query := db.Model(&models.Message{})
	if userId != "" {
		query = query.Where("user_id = ?", userId)
	}

	var total int64
	query.Count(&total)

	var messages []models.Message
	query.Order("created_at DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&messages)

	c.JSON(200, gin.H{
		"code": 200,
		"msg":  "获取成功!",
		"data": gin.H{
			"list":      messages,
			"total":     total,
			"page":      page,
			"page_size": pageSize,
		},
	})
}

// MarkMessageRead 标记消息已读
func MarkMessageRead(c *gin.Context) {
	msgIdStr := c.Query("id")
	msgId, err := strconv.ParseUint(msgIdStr, 10, 64)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "无效的消息ID", "data": nil})
		return
	}
	userId := c.GetString("UserId")
	db := database.NewDb()

	now := time.Now()
	result := db.Model(&models.Message{}).Where("id = ? AND user_id = ?", msgId, userId).Update("read_at", &now)
	if result.RowsAffected == 0 {
		c.JSON(200, gin.H{"code": 201, "msg": "消息不存在或已读", "data": nil})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "已读", "data": nil})
}

// MarkAllMessagesRead 标记当前用户所有消息已读
func MarkAllMessagesRead(c *gin.Context) {
	userId := c.GetString("UserId")
	db := database.NewDb()

	now := time.Now()
	db.Model(&models.Message{}).Where("user_id = ? AND read_at IS NULL", userId).Update("read_at", &now)
	c.JSON(200, gin.H{"code": 200, "msg": "全部已读", "data": nil})
}

// ClearMessages 管理员清除消息记录
func ClearMessages(c *gin.Context) {
	userId := c.Query("user_id") // 可选，指定则只清某用户
	db := database.NewDb()

	query := db.Where("1 = 1")
	if userId != "" {
		query = query.Where("user_id = ?", userId)
	}
	result := query.Delete(&models.Message{})
	if result.Error != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "清除失败: " + result.Error.Error(), "data": nil})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": fmt.Sprintf("已清除 %d 条消息", result.RowsAffected), "data": nil})
}

// SSEStream SSE 端点：客户端建立长连接接收实时消息
func SSEStream(c *gin.Context) {
	userId := c.GetString("UserId")
	if userId == "" {
		c.JSON(401, gin.H{"code": 401, "msg": "未登录", "data": nil})
		return
	}

	c.Writer.Header().Set("Content-Type", "text/event-stream")
	c.Writer.Header().Set("Cache-Control", "no-cache")
	c.Writer.Header().Set("Connection", "keep-alive")
	c.Writer.Header().Set("X-Accel-Buffering", "no")
	c.Writer.Flush()

	ch := sseHub.Subscribe(userId)
	defer sseHub.Unsubscribe(userId, ch)

	// 推送未读消息列表（连接建立时）
	db := database.NewDb()
	var unread []models.Message
	db.Where("user_id = ? AND read_at IS NULL", userId).Order("created_at DESC").Limit(20).Find(&unread)
	if len(unread) > 0 {
		data, _ := json.Marshal(unread)
		fmt.Fprintf(c.Writer, "event: init\ndata: %s\n\n", string(data))
		c.Writer.Flush()
	}

	// 心跳
	clientGone := c.Request.Context().Done()
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-clientGone:
			return
		case <-ticker.C:
			fmt.Fprintf(c.Writer, ": heartbeat\n\n")
			c.Writer.Flush()
		case data := <-ch:
			fmt.Fprintf(c.Writer, "event: message\ndata: %s\n\n", data)
			c.Writer.Flush()
		}
	}
}

// pushMessageToUser 内部方法：将消息通过 SSE 推送给指定用户
func pushMessageToUser(userId string, msg models.Message) {
	data, err := json.Marshal(msg)
	if err != nil {
		return
	}
	sseHub.Broadcast(userId, string(data))
}

// GetWebhookInfo 获取 webhook 信息（管理员）
func GetWebhookInfo(c *gin.Context) {
	c.JSON(200, gin.H{
		"code": 200,
		"msg":  "获取成功!",
		"data": gin.H{
			"enabled": config.WebhookEnabled,
			"token":   config.WebhookToken,
			"url":     getWebhookURL(c),
		},
	})
}

// RegenerateWebhookToken 重新生成 webhook token
func RegenerateWebhookToken(c *gin.Context) {
	newToken := generateRandomToken(32)
	config.WebhookToken = newToken

	// 持久化到数据库
	db := database.NewDb()
	saveSetting(db, "WebhookToken", newToken)

	c.JSON(200, gin.H{"code": 200, "msg": "已重新生成!", "data": gin.H{"token": newToken}})
}

// ToggleWebhook 开关 webhook
func ToggleWebhook(c *gin.Context) {
	var req struct {
		Enabled string `json:"enabled"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "参数错误", "data": nil})
		return
	}
	if req.Enabled != "是" && req.Enabled != "否" {
		c.JSON(200, gin.H{"code": 201, "msg": "enabled 只能是 是 或 否", "data": nil})
		return
	}
	config.WebhookEnabled = req.Enabled
	db := database.NewDb()
	saveSetting(db, "WebhookEnabled", req.Enabled)
	c.JSON(200, gin.H{"code": 200, "msg": "已更新!", "data": gin.H{"enabled": req.Enabled}})
}

// --- 辅助函数 ---

func getWebhookURL(c *gin.Context) string {
	scheme := "http"
	if c.Request.TLS != nil {
		scheme = "https"
	}
	return fmt.Sprintf("%s://%s/v1/api/webhook/message", scheme, c.Request.Host)
}

func generateRandomToken(length int) string {
	b := make([]byte, length)
	_, err := rand.Read(b)
	if err != nil {
		// 降级方案（几乎不会发生）
		return fmt.Sprintf("token_%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(b)
}

func saveSetting(db *gorm.DB, key, value string) {
	var setting models.Setting
	err := db.Where("`key` = ?", key).First(&setting).Error
	if err != nil {
		setting.Key = key
		setting.Value = value
		db.Create(&setting)
	} else {
		setting.Value = value
		db.Save(&setting)
	}
}
