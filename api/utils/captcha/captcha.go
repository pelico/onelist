package captcha

import (
	"image"
	"image/color"
	"image/draw"
	"math/rand"
	"sync"
	"time"

	"golang.org/x/image/font"
	"golang.org/x/image/font/basicfont"
	"golang.org/x/image/math/fixed"
)

var (
	captchaCache sync.Map
	letters      = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
)

type CaptchaData struct {
	Code     string
	ExpireAt time.Time
}

func GenerateCaptcha() (string, image.Image) {
	rand.Seed(time.Now().UnixNano())
	code := make([]byte, 4)
	for i := range code {
		code[i] = letters[rand.Intn(len(letters))]
	}

	width, height := 120, 40
	img := image.NewRGBA(image.Rect(0, 0, width, height))
	draw.Draw(img, img.Bounds(), &image.Uniform{color.White}, image.Point{}, draw.Src)

	for i := 0; i < 10; i++ {
		drawLine(img, randomColor(100, 200),
			rand.Intn(width), rand.Intn(height),
			rand.Intn(width), rand.Intn(height))
	}

	for i, ch := range code {
		x := 10 + i*28 + rand.Intn(6)
		y := height/2 + 6 + rand.Intn(6)
		drawChar(img, string(ch), x, y, randomColor(50, 150))
	}

	for i := 0; i < 50; i++ {
		img.Set(rand.Intn(width), rand.Intn(height), randomColor(150, 200))
	}

	captchaCache.Store(string(code), CaptchaData{
		Code:     string(code),
		ExpireAt: time.Now().Add(5 * time.Minute),
	})

	return string(code), img
}

func drawLine(img *image.RGBA, c color.Color, x1, y1, x2, y2 int) {
	dx := abs(x2 - x1)
	dy := abs(y2 - y1)
	sx, sy := 1, 1
	if x1 >= x2 {
		sx = -1
	}
	if y1 >= y2 {
		sy = -1
	}
	err := dx - dy
	for {
		img.Set(x1, y1, c)
		if x1 == x2 && y1 == y2 {
			break
		}
		e2 := err * 2
		if e2 > -dy {
			err -= dy
			x1 += sx
		}
		if e2 < dx {
			err += dx
			y1 += sy
		}
	}
}

func drawChar(img *image.RGBA, ch string, x, y int, c color.Color) {
	d := &font.Drawer{
		Dst:  img,
		Src:  image.NewUniform(c),
		Face: basicfont.Face7x13,
		Dot:  fixed.P(x, y),
	}
	d.DrawString(ch)
}

func abs(x int) int {
	if x < 0 {
		return -x
	}
	return x
}

func VerifyCaptcha(code string) bool {
	if v, ok := captchaCache.Load(code); ok {
		data := v.(CaptchaData)
		if time.Now().Before(data.ExpireAt) {
			captchaCache.Delete(code)
			return true
		}
		captchaCache.Delete(code)
	}
	return false
}

func randomColor(min, max int) color.RGBA {
	return color.RGBA{
		R: uint8(min + rand.Intn(max-min)),
		G: uint8(min + rand.Intn(max-min)),
		B: uint8(min + rand.Intn(max-min)),
		A: 255,
	}
}
