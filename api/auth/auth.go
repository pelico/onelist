package auth

import (
	"errors"
	"strings"
	"sync"
	"time"

	"github.com/msterzhang/onelist/api/database"
	"github.com/msterzhang/onelist/api/models"
	"github.com/msterzhang/onelist/api/security"
	"github.com/msterzhang/onelist/api/utils/captcha"
)

const (
	MaxFailedAttempts    = 3
	LockDuration         = 15 * time.Minute
	IPHardLockDuration   = 15 * time.Minute // IP 被硬锁定时间
	IPHardLockThreshold  = 6                // 同一 IP 累计失败多少次后硬锁定
	ipEntryTTL           = 30 * time.Minute // IP 计数器条目有效期
)

var (
	ipLoginAttempts = make(map[string]*ipAttemptEntry)
	ipMutex         sync.RWMutex
)

func init() {
	// 定期清理过期的 IP 登录尝试条目，防止内存泄漏
	go func() {
		ticker := time.NewTicker(10 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			cleanupStaleIPEntries()
		}
	}()
}

func cleanupStaleIPEntries() {
	ipMutex.Lock()
	defer ipMutex.Unlock()
	now := time.Now()
	for ip, entry := range ipLoginAttempts {
		if now.Sub(entry.lastFailTime) > ipEntryTTL {
			delete(ipLoginAttempts, ip)
		}
	}
}

// ipAttemptEntry 同一 IP 的失败尝试记录
type ipAttemptEntry struct {
	count        int
	lastFailTime time.Time
	hardLockUtil time.Time // 硬锁定到期时间，零值表示未硬锁
}

func getIPAttempts(ip string) int {
	ipMutex.RLock()
	defer ipMutex.RUnlock()
	if entry, ok := ipLoginAttempts[ip]; ok {
		return entry.count
	}
	return 0
}

func incrementIPAttempts(ip string) int {
	ipMutex.Lock()
	defer ipMutex.Unlock()
	now := time.Now()
	entry, ok := ipLoginAttempts[ip]
	if !ok || now.Sub(entry.lastFailTime) > ipEntryTTL {
		entry = &ipAttemptEntry{lastFailTime: now}
		ipLoginAttempts[ip] = entry
	}
	entry.count++
	entry.lastFailTime = now
	// 达到硬锁定阈值就硬锁
	if entry.count >= IPHardLockThreshold {
		entry.hardLockUtil = now.Add(IPHardLockDuration)
	}
	return entry.count
}

// isIPHardLocked 检查 IP 是否处于硬锁定状态
func isIPHardLocked(ip string) bool {
	ipMutex.RLock()
	defer ipMutex.RUnlock()
	entry, ok := ipLoginAttempts[ip]
	if !ok {
		return false
	}
	return !entry.hardLockUtil.IsZero() && time.Now().Before(entry.hardLockUtil)
}

func resetIPAttempts(ip string) {
	ipMutex.Lock()
	defer ipMutex.Unlock()
	delete(ipLoginAttempts, ip)
}

// Login 登录鉴权：
// 1. 先做 IP 级别的硬性验证码校验（无论账号是否存在，达到阈值都必须带正确验证码）
// 2. 再做账号级校验：锁定检查、密码校验
func Login(email, password string, captcha string, requireCaptcha bool, clientIP string) (models.User, string, bool, error) {
	user := models.User{}
	var err error
	db := database.NewDb()

	// ---------- IP 硬性预校验（无论账号是否存在） ----------
	// 1) 硬锁定：直接拒绝
	if isIPHardLocked(clientIP) {
		err = errors.New("请求过于频繁，请稍后再试")
	} else {
		// 2) IP 失败计数达到阈值：必须带正确验证码
		ipCount := getIPAttempts(clientIP)
		if ipCount >= MaxFailedAttempts-1 {
			if !requireCaptcha || !verifyCaptcha(captcha) {
				err = errors.New("验证码错误")
			}
		}
	}

	// ---------- 账号级校验 ----------
	if err == nil {
		err = db.Model(&models.User{}).Where("user_email = ?", email).Take(&user).Error
	}
	if err == nil {
		if user.IsLock && time.Now().Before(user.LastFailedAttempt.Add(LockDuration)) {
			err = errors.New("账号已锁定，请稍后再试")
		}
	}
	if err == nil && user.IsLock && time.Now().After(user.LastFailedAttempt.Add(LockDuration)) {
		db.Model(&models.User{}).Where("id = ?", user.Id).Updates(map[string]interface{}{
			"is_lock":         false,
			"failed_attempts": 0,
		})
		user.IsLock = false
	}
	if err == nil {
		failedCount := user.FailedAttempts
		if failedCount == 0 {
			failedCount = getIPAttempts(clientIP)
		}
		if failedCount >= MaxFailedAttempts && requireCaptcha && !verifyCaptcha(captcha) {
			err = errors.New("验证码错误")
		}
	}
	if err == nil {
		err = security.VerifyPassword(user.UserPassword, password)
	}

	// 登录成功路径
	if err == nil {
		db.Model(&models.User{}).Where("id = ?", user.Id).Updates(map[string]interface{}{
			"failed_attempts":     0,
			"last_failed_attempt": time.Time{},
		})
		resetIPAttempts(clientIP)
		user.UserPassword = ""
		token, jwtErr := GenerateJWT(user)
		return user, token, false, jwtErr
	}

	// 失败路径：累计失败次数 / 返回新验证码需求
	var newAttempts int
	if user.Id != 0 {
		newAttempts = user.FailedAttempts + 1
		isLock := newAttempts >= MaxFailedAttempts
		db.Model(&models.User{}).Where("id = ?", user.Id).Updates(map[string]interface{}{
			"failed_attempts":     newAttempts,
			"last_failed_attempt": time.Now(),
			"is_lock":             isLock,
		})
	} else {
		newAttempts = incrementIPAttempts(clientIP)
	}

	requireCaptcha = newAttempts >= MaxFailedAttempts-1
	return models.User{}, "", requireCaptcha, errors.New("用户名或密码错误")
}

func verifyCaptcha(code string) bool {
	if code == "" {
		return false
	}
	code = strings.ToUpper(strings.TrimSpace(code))
	// VerifyCaptcha 内部已做一次消费（delete），天然防重放
	return captcha.VerifyCaptcha(code)
}

// LoginAdmin 管理员登录
func LoginAdmin(email, password string) (string, error) {
	user := models.User{}
	db := database.NewDb()
	err := db.Model(&models.User{}).Where("user_email = ?", email).Take(&user).Error
	if err != nil {
		return "", errors.New("用户不存在")
	}
	if user.IsLock {
		return "", errors.New("账号已锁定，请联系管理员解封")
	}
	if !user.IsAdmin {
		return "", errors.New("非管理员，禁止登录")
	}
	if err := security.VerifyPassword(user.UserPassword, password); err != nil {
		return "", err
	}
	user.UserPassword = ""
	return GenerateJWT(user)
}
