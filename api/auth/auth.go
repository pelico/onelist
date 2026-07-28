package auth

import (
	"errors"
	"strings"
	"sync"
	"time"

	"github.com/msterzhang/onelist/api/security"
	"github.com/msterzhang/onelist/api/utils/captcha"
	"github.com/msterzhang/onelist/api/utils/channels"

	"github.com/msterzhang/onelist/api/models"

	"github.com/msterzhang/onelist/api/database"

	"gorm.io/gorm"
)

const (
	MaxFailedAttempts = 3
	LockDuration      = 15 * time.Minute
)

var (
	ipLoginAttempts = make(map[string]int)
	ipMutex         sync.RWMutex
)

func getIPAttempts(ip string) int {
	ipMutex.RLock()
	defer ipMutex.RUnlock()
	return ipLoginAttempts[ip]
}

func incrementIPAttempts(ip string) int {
	ipMutex.Lock()
	defer ipMutex.Unlock()
	ipLoginAttempts[ip]++
	return ipLoginAttempts[ip]
}

func resetIPAttempts(ip string) {
	ipMutex.Lock()
	defer ipMutex.Unlock()
	delete(ipLoginAttempts, ip)
}

// SignIn method
func Login(email, password string, captcha string, requireCaptcha bool, clientIP string) (models.User, string, bool, error) {
	user := models.User{}
	var err error
	var db *gorm.DB
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		db = database.NewDb()
		if err != nil {
			ch <- false
			return
		}
		err = db.Model(&models.User{}).Where("user_email = ?", email).Take(&user).Error
		if err != nil {
			ch <- false
			return
		}
		if user.IsLock && time.Now().Before(user.LastFailedAttempt.Add(LockDuration)) {
			err = errors.New("账号已锁定，请稍后再试")
			ch <- false
			return
		}
		if user.IsLock && time.Now().After(user.LastFailedAttempt.Add(LockDuration)) {
			db.Model(&models.User{}).Where("id = ?", user.Id).Updates(map[string]interface{}{
				"is_lock":         false,
				"failed_attempts": 0,
			})
		}
		failedCount := user.FailedAttempts
		if failedCount == 0 {
			failedCount = getIPAttempts(clientIP)
		}
		if failedCount >= MaxFailedAttempts && requireCaptcha && !verifyCaptcha(captcha) {
			err = errors.New("验证码错误")
			ch <- false
			return
		}
		err = security.VerifyPassword(user.UserPassword, password)
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)

	if channels.OK(done) {
		db.Model(&models.User{}).Where("id = ?", user.Id).Updates(map[string]interface{}{
			"failed_attempts":     0,
			"last_failed_attempt": time.Time{},
		})
		resetIPAttempts(clientIP)
		user.UserPassword = ""
		err, token := GenerateJWT(user)
		return user, err, false, token
	}

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
	return captcha.VerifyCaptcha(code)
}

// SignIn method
func LoginAdmin(email, password string) (string, error) {
	user := models.User{}
	var err error
	var db *gorm.DB
	done := make(chan bool)
	go func(ch chan<- bool) {
		defer close(ch)
		db = database.NewDb()
		if err != nil {
			ch <- false
			return
		}
		err = db.Debug().Model(&models.User{}).Where("user_email = ?", email).Take(&user).Error
		if err != nil {
			err = errors.New("用户不存在")
			ch <- false
			return
		}
		if user.IsLock {
			err = errors.New("账号已锁定，请联系管理员解封")
			ch <- false
			return
		}
		if !user.IsAdmin {
			err = errors.New("非管理员，禁止登录")
			ch <- false
			return
		}
		err = security.VerifyPassword(user.UserPassword, password)
		if err != nil {
			ch <- false
			return
		}
		ch <- true
	}(done)

	if channels.OK(done) {
		user.UserPassword = ""
		return GenerateJWT(user)
	}
	return "", err
}
