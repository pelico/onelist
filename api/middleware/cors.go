package middleware

import (
	"net/url"

	"github.com/gin-gonic/gin"
)

// isOriginAllowed 判断是否允许该跨域来源。
// 策略（保守白名单，不暴露凭证给任意第三方站点）：
//  1. Origin 为空（直接打开页面或 non-browser 客户端）→ 允许
//  2. Origin 与请求 Host 同源（scheme+host+port 一致）→ 允许并携带凭证（最常见主站访问）
//  3. 其他第三方 Origin → 退化为 * 不带凭证（兼容老的第三方前端/播放器预检场景，响应可读但不会带 token）
func isOriginAllowed(origin string, r interface{ Host() string }) (allowOrigin string, allowCreds bool) {
	if origin == "" {
		return "", false
	}
	if origin == "null" {
		return "*", false
	}
	o, err := url.Parse(origin)
	if err != nil {
		return "*", false
	}
	if o.Host == r.Host() {
		return origin, true
	}
	return "*", false
}

type hostGetter struct {
	host string
}

func (h hostGetter) Host() string { return h.host }

func CORSMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		origin := c.GetHeader("Origin")
		allowOrigin, allowCreds := isOriginAllowed(origin, hostGetter{host: c.Request.Host})
		if allowOrigin != "" {
			c.Writer.Header().Set("Access-Control-Allow-Origin", allowOrigin)
			if allowCreds {
				c.Writer.Header().Set("Access-Control-Allow-Credentials", "true")
			}
		}
		c.Writer.Header().Set("Vary", "Origin")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, token, accept, origin, Cache-Control, X-Requested-With")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS, GET, PUT")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	}
}
