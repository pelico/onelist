package security

import (
	"encoding/base64"
	"errors"

	"golang.org/x/crypto/bcrypt"
)

// Hash make a password hash using bcrypt
func Hash(password string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

// VerifyPassword verify the password against the hash.
// Supports both bcrypt (new) and base64 (legacy) hashes for backward compatibility.
func VerifyPassword(hashedPassword, password string) error {
	// bcrypt 哈希以 "$2a$", "$2b$", "$2y$" 开头
	if len(hashedPassword) > 4 && hashedPassword[0] == '$' && hashedPassword[1] == '2' {
		err := bcrypt.CompareHashAndPassword([]byte(hashedPassword), []byte(password))
		if err != nil {
			return errors.New("密码错误")
		}
		return nil
	}
	// 兼容旧版 base64 编码
	e, err := base64.StdEncoding.DecodeString(hashedPassword)
	if err != nil {
		return errors.New("密码错误")
	}
	if string(e) != password {
		return errors.New("密码错误")
	}
	return nil
}
