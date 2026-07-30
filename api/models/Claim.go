package models

import (
	"github.com/golang-jwt/jwt/v5"
)

// Claim is the token payload
type Claim struct {
	User User `json:"user"`
	jwt.RegisteredClaims
}
