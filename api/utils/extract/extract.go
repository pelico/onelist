package extract

import (
	"errors"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

// 预编译正则表达式，避免每次调用时重复编译（ARMv7l 性能优化）
var (
	reYear       = regexp.MustCompile(`\d{4}`)
	reDigits     = regexp.MustCompile(`\d+`)
	reParen      = regexp.MustCompile(`\s*\([^)]+\)`)
	reRoman      = regexp.MustCompile(`(?i)([\p{Han}]+)\.?(I{1,3}|IV|V|VI|VII|VIII|IX|X)$`)
	reHanDigit   = regexp.MustCompile(`[\p{Han}\d{1,2}]+`)
	reSeasonEp   = regexp.MustCompile(`[Ss](\d{1,2})[Ee](\d{1,4})`)
)

func removeEndingOne(s string) string {
	if len(s) > 0 && s[len(s)-1] == '1' {
		return s[:len(s)-1]
	}
	return s
}

// 罗马数字转阿拉伯数字
func romanToArabic(s string) string {
	romanMap := map[string]string{
		"I": "1", "II": "2", "III": "3", "IV": "4", "V": "5",
		"VI": "6", "VII": "7", "VIII": "8", "IX": "9", "X": "10",
	}
	if val, ok := romanMap[strings.ToUpper(s)]; ok {
		return val
	}
	return s
}

// 过滤电影文件名
func ExtractMovieName(s string) string {
	oldName := s
	// 删除发布年份和文件扩展名
	s = reYear.ReplaceAllString(s, "")
	// 兼容全是纯数字的
	if len(s) == 0 {
		matchenNumbers := reDigits.FindAllString(oldName, -1)
		if len(matchenNumbers) > 0 {
			s = matchenNumbers[0]
		}
	}
	// 删除括号及其内容
	s = reParen.ReplaceAllString(s, "")

	// 清理末尾的点号（年份被删后可能残留：鹿鼎记.II. → 鹿鼎记.II）
	s = strings.TrimRight(s, ".")

	// 处理罗马数字后缀（多种形式）：
	// 鹿鼎记.II → 鹿鼎记2
	// 鹿鼎记II → 鹿鼎记2
	// 鹿鼎记.III → 鹿鼎记3
	if match := reRoman.FindStringSubmatch(s); len(match) == 3 {
		romanPart := strings.ToUpper(match[2])
		// 仅当识别为有效罗马数字时替换，避免误伤"陆I"等普通中文含"I"
		if _, ok := map[string]bool{"I":true,"II":true,"III":true,"IV":true,"V":true,"VI":true,"VII":true,"VIII":true,"IX":true,"X":true}[romanPart]; ok {
			s = match[1] + romanToArabic(match[2])
		}
	}

	// 提取中文名称 + 阿拉伯数字
	matches := reHanDigit.FindAllString(s, -1)
	if len(matches) > 0 {
		name := removeEndingOne(matches[0])
		return name
	}
	return s
}

// 根据文件名获取剧集季及集信息
func ExtractNumberWithFile(file string) (int, int, error) {
	p, err := filepath.Abs(file)
	if err != nil {
		return 0, 0, err
	}
	SeasonNumber := 0
	EpisodeNumber := 0
	fileName := filepath.Base(p)
	match := reSeasonEp.FindStringSubmatch(fileName)
	if len(match) < 3 {
		return 0, 0, errors.New("get number error")
	}
	season := match[1]
	episode := match[2]
	SeasonNumber, err = strconv.Atoi(season)
	if err != nil {
		return 0, 0, err
	}
	EpisodeNumber, err = strconv.Atoi(episode)
	if err != nil {
		return 0, 0, err
	}
	return SeasonNumber, EpisodeNumber, nil
}
