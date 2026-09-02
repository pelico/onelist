package controllers

import (
	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/config"
)

func GetWebConfig(c *gin.Context) {
	configData := config.GetConfig()
	configData.KeyDb=""
	configData.WebhookToken=""
	c.JSON(200, gin.H{"code": 200, "msg": "获取成功!", "data": configData})
}


func GetConfig(c *gin.Context) {
	configData := config.GetConfig()
	// 脱敏：普通用户不应看到 API 密钥和 Webhook Token
	configData.KeyDb = ""
	configData.WebhookToken = ""
	c.JSON(200, gin.H{"code": 200, "msg": "获取成功!", "data": configData})
}

func SaveConfig(c *gin.Context) {
	configData := models.Config{}
	err := c.ShouldBind(&configData)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": err.Error(), "data": ""})
		return
	}
	// 脱敏补偿：GetWebConfig 返回的 KeyDb/WebhookToken 是空字符串，
	// 前端保存时会原样提交空值。此处保留已有值，避免默认 key 被空值覆盖。
	current := config.GetConfig()
	if configData.KeyDb == "" {
		configData.KeyDb = current.KeyDb
	}
	if configData.WebhookToken == "" {
		configData.WebhookToken = current.WebhookToken
	}
	data, err := config.SaveConfig(configData)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": err.Error(), "data": ""})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "保存成功!", "data": data})
}
