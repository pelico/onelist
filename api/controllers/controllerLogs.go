package controllers

import (
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/msterzhang/onelist/api/utils/logger"
)

func GetLogs(c *gin.Context) {
	level := c.Query("level")
	module := c.Query("module")
	keyword := c.Query("keyword")
	pageStr := c.Query("page")
	pageSizeStr := c.Query("page_size")

	page, _ := strconv.Atoi(pageStr)
	pageSize, _ := strconv.Atoi(pageSizeStr)

	logs, total, err := logger.GetLogs(level, module, page, pageSize, keyword)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "获取日志失败: " + err.Error(), "data": nil})
		return
	}
	c.JSON(200, gin.H{
		"code":  200,
		"msg":   "获取成功",
		"data":  logs,
		"total": total,
	})
}

func CleanLogs(c *gin.Context) {
	retentionDaysStr := c.Query("days")
	cleanAll := c.Query("all")

	if cleanAll == "true" {
		count, err := logger.CleanAllLogs()
		if err != nil {
			c.JSON(200, gin.H{"code": 201, "msg": "清理日志失败: " + err.Error(), "data": nil})
			return
		}
		c.JSON(200, gin.H{
			"code": 200,
			"msg":  "清理成功",
			"data": count,
		})
		return
	}

	retentionDays, _ := strconv.Atoi(retentionDaysStr)
	if retentionDays <= 0 {
		retentionDays = 7
	}
	count, err := logger.CleanOldLogs(retentionDays)
	if err != nil {
		c.JSON(200, gin.H{"code": 201, "msg": "清理日志失败: " + err.Error(), "data": nil})
		return
	}
	c.JSON(200, gin.H{
		"code": 200,
		"msg":  "清理成功",
		"data": count,
	})
}
