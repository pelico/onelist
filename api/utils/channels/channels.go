package channels

import (
	"log"
	"time"
)

// OK returns if a operation was successful, with 30s timeout to prevent goroutine leak
func OK(done chan bool) bool {
	select {
	case ok := <-done:
		return ok
	case <-time.After(30 * time.Second):
		log.Printf("[WARN] channels.OK: 操作超时(30s)")
		return false
	}
}
