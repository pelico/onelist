package controllers

import "testing"

func TestPathDepthUnderRoot(t *testing.T) {
	cases := []struct {
		file string
		root string
		want int
	}{
		// alist：文件 url 带 /d 前缀，work.Path 不带，必须仍能对齐
		{"/d/7WKYSSD/儿童天空/动画/阿布故事/阿布故事 成语故事1 S01E01.mp4", "/7WKYSSD/儿童天空/动画/阿布故事", 0},
		{"/d/7WKYSSD/儿童天空/动画/阿布故事/阿布故事 成语故事1 S01E01.mp4", "/7WKYSSD/儿童天空/动画", 1},
		{"/d/7WKYSSD/儿童天空/动画/布鲁伊 S01/布鲁伊 S01E01.mp4", "/7WKYSSD/儿童天空/动画", 1},
		// 本地路径
		{"/media/tv/阿布故事/布鲁伊 S01E01.mp4", "/media/tv/阿布故事", 0},
		{"/media/tv/动画/博博鲁探险队/博博鲁探险队 不一样的黑熊 S01E01.mp4", "/media/tv/动画", 1},
		// 不在根目录下
		{"/media/other/布鲁伊 S01E01.mp4", "/media/tv", -1},
		{"/media/tv/布鲁伊 S01E01.mp4", "", -1},
	}
	for _, c := range cases {
		if got := pathDepthUnderRoot(splitPathSegments(c.file), splitPathSegments(c.root)); got != c.want {
			t.Errorf("pathDepthUnderRoot(%q, %q) = %d, want %d", c.file, c.root, got, c.want)
		}
	}
}

func TestIsTitlePrefix(t *testing.T) {
	cases := []struct {
		cand string
		name string
		want bool
	}{
		{"阿布故事", "阿布故事", true},
		{"阿布故事 成语故事1", "阿布故事", true},
		{"阿布故事-成语故事1", "阿布故事", true},
		{"阿布故事外传", "阿布故事", false},   // 无边界
		{"阿布故事 成语故事35", "阿布故事 成语故事3", false}, // 前缀切在数字中间
		{"布鲁伊", "小猪佩奇", false},
		{"", "阿布故事", false},
	}
	for _, c := range cases {
		if got := isTitlePrefix(c.cand, c.name); got != c.want {
			t.Errorf("isTitlePrefix(%q, %q) = %v, want %v", c.cand, c.name, got, c.want)
		}
	}
}

func TestTrimTitleTail(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"布鲁伊", "布鲁伊"},
		{"布鲁伊 ", "布鲁伊"},
		{"儿歌1", "儿歌"},
		{"星球大战 -", "星球大战"},
		{"成语故事", "成语故事"},
	}
	for _, c := range cases {
		if got := trimTitleTail(c.in); got != c.want {
			t.Errorf("trimTitleTail(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestResolveTvShowTitlesFlatWithRootName(t *testing.T) {
	// alist 平铺文件：挂载目录名即剧名，文件名"剧名+每集副标题+集数标记"也能正确聚合
	root := "/7WKYSSD/儿童天空/动画/阿布故事"
	files := []string{
		"/d/7WKYSSD/儿童天空/动画/阿布故事/阿布故事 成语故事1 S01E01.mp4",
		"/d/7WKYSSD/儿童天空/动画/阿布故事/阿布故事 成语故事2 S01E02.mp4",
		"/d/7WKYSSD/儿童天空/动画/阿布故事/阿布故事 特别篇 S01E03.mp4",
	}
	titles := ResolveTvShowTitles("", files, root)
	for _, f := range files {
		if titles[f] != "阿布故事" {
			t.Errorf("file %q resolved to %q, want 阿布故事", f, titles[f])
		}
	}
}

func TestResolveTvShowTitlesStandardFlat(t *testing.T) {
	// 标准命名（剧名+集数标记）平铺
	root := "/media/tv/布鲁伊"
	files := []string{
		"/media/tv/布鲁伊/布鲁伊 S01E01.mp4",
		"/media/tv/布鲁伊/布鲁伊 S01E02.mp4",
	}
	titles := ResolveTvShowTitles("", files, root)
	for _, f := range files {
		if titles[f] != "布鲁伊" {
			t.Errorf("file %q resolved to %q, want 布鲁伊", f, titles[f])
		}
	}
}

func TestResolveTvShowTitlesMixedShowsNotMerged(t *testing.T) {
	// 多部剧平铺在同一目录且与目录名无关：不能误合并
	root := "/media/tv/杂类"
	files := []string{
		"/media/tv/杂类/小猪佩奇 S01E01.mp4",
		"/media/tv/杂类/汪汪队立大功 S01E01.mp4",
	}
	titles := ResolveTvShowTitles("", files, root)
	if titles[files[0]] != "小猪佩奇" || titles[files[1]] != "汪汪队立大功" {
		t.Errorf("mixed flat shows should not merge: %v", titles)
	}
}

func TestResolveTvShowTitlesCommonPrefix(t *testing.T) {
	// 平铺且目录名无关：按公共前缀归组
	root := "/media/tv"
	files := []string{
		"/media/tv/博博鲁探险队 不一样的黑熊 S01E01.mp4",
		"/media/tv/博博鲁探险队 勇敢的小鹿 S01E02.mp4",
	}
	titles := ResolveTvShowTitles("", files, root)
	for _, f := range files {
		if titles[f] != "博博鲁探险队" {
			t.Errorf("file %q resolved to %q, want 博博鲁探险队", f, titles[f])
		}
	}
}

func TestResolveTvShowTitlesSubfolder(t *testing.T) {
	// 子文件夹文件：文件夹名优先（含 alist /d 前缀与季文件夹跳过）
	root := "/media/tv/动画"
	files := []string{
		"/media/tv/动画/布鲁伊 S02/布鲁伊 S02E01.mp4",
		"/media/tv/动画/博博鲁探险队/博博鲁探险队 不一样的黑熊 S01E01.mp4",
	}
	titles := ResolveTvShowTitles("", files, root)
	if titles[files[0]] != "布鲁伊" {
		t.Errorf("file %q resolved to %q, want 布鲁伊", files[0], titles[files[0]])
	}
	if titles[files[1]] != "博博鲁探险队" {
		t.Errorf("file %q resolved to %q, want 博博鲁探险队", files[1], titles[files[1]])
	}
}
