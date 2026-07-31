/**
 * 电视遥控器导航插件 (Spatial Navigation 版)
 * 支持 Android TV / 智能电视 / 遥控器 / 键盘方向键操作
 *
 * 按键映射:
 * - ArrowUp/ArrowDown/ArrowLeft/ArrowRight: 方向导航
 * - Enter/Space: 确认
 * - Escape/Backspace: 返回/关闭弹窗
 * - MediaPlayPause/MediaPlay/MediaPause: 媒体控制
 */

class TvNavigation {
  constructor() {
    this.focusables = [];
    this.currentFocus = null;
    this.isTvMode = false;
    this.listeners = new Map();
    this.history = [];
    this.config = {
      focusClass: 'tv-focus',
      focusVisibleClass: 'tv-focus-visible',
      scrollIntoView: true,
      scrollBehavior: 'smooth',
      scrollBlock: 'center',
      autoFocus: true,
      soundEnabled: false
    };
    this.playerInstance = null;
    this.playerControls = {
      play: null,
      pause: null,
      togglePlay: null,
      seekForward: null,
      seekBackward: null,
      volumeUp: null,
      volumeDown: null,
      toggleMute: null,
      toggleFullscreen: null,
      nextEpisode: null,
      prevEpisode: null
    };
    this._indicator = null;
    this._resizeTimer = null;
    this._lastDirection = null;
    this._mutationObserver = null;

    // 绑定 this
    this.handleKeyDown = this.handleKeyDown.bind(this);
    this.handleFocus = this.handleFocus.bind(this);
    this.handleResize = this.handleResize.bind(this);
    this.refresh = this.refresh.bind(this);
  }

  // 检测是否为电视环境，URL ?tv=1 会自动持久化到 localStorage
  detectTvMode() {
    const ua = navigator.userAgent.toLowerCase();
    const isTv = /tv|smart-tv|smarttv|googletv|appletv|hbbtv|netcast|viera|nettv|roku|firetv|fire-tv|aft|aftb|aftt|aftm|aftd|android tv/i.test(ua);
    const isAndroid = /android/i.test(ua) && !/mobile/i.test(ua);
    const isLargeScreen = window.screen.width >= 1280 && window.screen.height >= 720;
    const hasTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

    const urlParams = new URLSearchParams(window.location.search);
    const forceTvFromUrl = urlParams.get('tv') === '1' || urlParams.get('tv') === 'true';

    // URL 参数开启时，自动持久化，避免跳转后丢失
    if (forceTvFromUrl) {
      try { localStorage.setItem('forceTvMode', 'true'); } catch (e) {}
    }

    const forceTvFromStorage = (() => {
      try {
        const val = localStorage.getItem('forceTvMode');
        // 未设置过时默认启用，管理员可在设置中关闭
        return val === null || val === 'true';
      } catch (e) { return true; }
    })();

    this.isTvMode = forceTvFromUrl || forceTvFromStorage || isTv || (isAndroid && isLargeScreen && !hasTouch);
    return this.isTvMode;
  }

  // 切换 TV 模式（供设置页调用）
  setTvMode(enabled) {
    this.isTvMode = !!enabled;
    try { localStorage.setItem('forceTvMode', this.isTvMode ? 'true' : 'false'); } catch (e) {}

    if (this.isTvMode) {
      document.body.classList.add('tv-mode');
      this.refresh();
      const first = this.getFirstFocusable();
      if (first) this.setFocus(first);
    } else {
      document.body.classList.remove('tv-mode');
      this.clearFocus();
    }
    return this;
  }

  // 初始化
  init(config = {}) {
    this.config = { ...this.config, ...config };
    this.detectTvMode();

    if (this.isTvMode) {
      document.body.classList.add('tv-mode');
    }

    this.bindGlobalEvents();
    this.createFocusIndicator();
    this.startMutationObserver();

    if (this.isTvMode) {
      // 页面加载后自动聚焦第一个可聚焦元素
      setTimeout(() => {
        this.refresh();
        const first = this.getFirstFocusable();
        if (first && !this.currentFocus) this.setFocus(first);
      }, 300);
    }

    console.log('[TvNavigation] Initialized, TV mode:', this.isTvMode);
    return this;
  }

  // 绑定全局键盘事件
  bindGlobalEvents() {
    document.addEventListener('keydown', this.handleKeyDown, true);
    document.addEventListener('focus', this.handleFocus, true);
    window.addEventListener('resize', this.handleResize);
    window.addEventListener('scroll', this.refresh, true);
  }

  // 创建焦点指示器
  createFocusIndicator() {
    if (document.getElementById('tv-focus-indicator')) return;

    const indicator = document.createElement('div');
    indicator.id = 'tv-focus-indicator';
    indicator.className = 'tv-focus-indicator';
    indicator.setAttribute('aria-hidden', 'true');
    document.body.appendChild(indicator);
    this._indicator = indicator;
  }

  // 监听 DOM 变化，自动刷新焦点列表
  startMutationObserver() {
    if (this._mutationObserver || typeof MutationObserver === 'undefined') return;

    this._mutationObserver = new MutationObserver((mutations) => {
      // DOM 变化较大时延迟刷新，避免频繁扫描
      clearTimeout(this._mutationTimer);
      this._mutationTimer = setTimeout(() => {
        this.refresh();
      }, 150);
    });

    this._mutationObserver.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['style', 'class', 'hidden', 'disabled']
    });
  }

  // 键盘事件处理
  handleKeyDown(e) {
    if (!this.isTvMode && !this.config.forceTvMode) return;

    const key = e.key;

    // 输入框/文本域内：不拦截 Enter/Space（保留原生输入行为），方向键仍可用于导航
    const tag = (e.target.tagName || '').toLowerCase();
    const isInputField = tag === 'input' || tag === 'textarea';
    if (isInputField && (key === 'Enter' || key === ' ')) {
      return;
    }

    // 游戏 iframe 获得焦点时：放行方向键/Enter/Space 给游戏内部处理
    // Escape/Backspace 仍由父页面处理（用于返回上一页）
    const activeEl = document.activeElement;
    if (activeEl && activeEl.tagName === 'IFRAME' && activeEl.classList.contains('game-iframe')) {
      if (key === 'Escape' || key === 'Backspace') {
        // 继续走下面的 handlers，由父页面处理返回
      } else {
        return; // 方向键、Enter、Space 等全部放行给 iframe 内的游戏
      }
    }

    const handlers = {
      'ArrowUp': () => this.navigate('up'),
      'ArrowDown': () => this.navigate('down'),
      'ArrowLeft': () => this.navigate('left'),
      'ArrowRight': () => this.navigate('right'),
      'Enter': () => this.confirm(e),
      ' ': () => this.confirm(e),
      'Escape': () => this.back(),
      'Backspace': () => this.back(),
      'MediaPlayPause': () => this.togglePlay(),
      'MediaPlay': () => this.play(),
      'MediaPause': () => this.pause(),
      'MediaStop': () => this.stop(),
      'MediaTrackPrevious': () => this.prevEpisode(),
      'MediaTrackNext': () => this.nextEpisode(),
      'AudioVolumeUp': () => this.volumeUp(),
      'AudioVolumeDown': () => this.volumeDown(),
      'AudioVolumeMute': () => this.toggleMute()
    };

    if (handlers[key]) {
      e.preventDefault();
      e.stopPropagation();
      handlers[key]();
      this.emit('navigate', { key });
    }
  }

  // 焦点变化处理
  handleFocus(e) {
    if (!this.isTvMode) return;
    if (e.target === this.currentFocus) {
      this.updateFocusIndicator(e.target);
      return;
    }
    if (this.isFocusable(e.target)) {
      this.setFocus(e.target, false);
    }
  }

  // 窗口大小变化处理
  handleResize() {
    clearTimeout(this._resizeTimer);
    this._resizeTimer = setTimeout(() => {
      this.refresh();
      if (this.currentFocus) this.updateFocusIndicator(this.currentFocus);
    }, 100);
  }

  // 注册焦点元素组（兼容旧 API，元素会被纳入全局导航）
  registerGroup(name, elements, options = {}) {
    const arr = Array.isArray(elements) ? elements.filter(Boolean) : [elements].filter(Boolean);
    arr.forEach((el, index) => {
      el.setAttribute('data-tv-group', name);
      el.setAttribute('data-tv-index', index);
      el.setAttribute('tabindex', '0');
    });
    this.refresh();
    return this;
  }

  // 取消注册焦点组
  unregisterGroup(name) {
    this.focusables.forEach(el => {
      if (el.getAttribute('data-tv-group') === name) {
        el.removeAttribute('data-tv-group');
        el.removeAttribute('data-tv-index');
      }
    });
    this.refresh();
    return this;
  }

  // 设置当前焦点组（兼容旧 API）
  setCurrentGroup(name) {
    const el = this.focusables.find(el => el.getAttribute('data-tv-group') === name);
    if (el) this.setFocus(el);
    return this;
  }

  // 刷新可聚焦元素列表
  refresh() {
    this.focusables = this.scanFocusables();
    return this;
  }

  // 扫描页面上所有可聚焦元素
  scanFocusables() {
    const candidates = document.querySelectorAll(
      'a, button, input, textarea, select, iframe, [tabindex]:not([tabindex="-1"])'
    );

    const list = [];
    candidates.forEach(el => {
      if (this.isFocusable(el)) {
        list.push(el);
      }
    });

    // 按 DOM 位置排序，作为兜底
    list.sort((a, b) => {
      const pos = a.compareDocumentPosition(b);
      return pos & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1;
    });

    return list;
  }

  // 检查元素是否可聚焦
  isFocusable(el) {
    if (!el || el === document.body) return false;

    const style = window.getComputedStyle(el);
    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
    if (el.disabled) return false;
    if (el.offsetParent === null && style.position !== 'fixed') return false;

    const rect = el.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) return false;

    return true;
  }

  // 获取第一个可聚焦元素
  getFirstFocusable() {
    return this.focusables[0] || null;
  }

  // 方向导航（Spatial Navigation）
  navigate(direction) {
    this.refresh();

    const focusables = this.focusables.filter(el => this.isFocusable(el));
    if (focusables.length === 0) return;

    let current = this.currentFocus;
    if (!current || !this.isFocusable(current) || !focusables.includes(current)) {
      current = this.getFirstFocusable();
      if (current) {
        this.setFocus(current);
      }
      return;
    }

    const next = this.findNearestInDirection(current, direction, focusables);
    if (next && next !== current) {
      this.setFocus(next);
    }
  }

  // 找到指定方向上最近的元素（网格对齐优先算法）
  findNearestInDirection(current, direction, candidates) {
    const currentRect = this.getFocusNavElement(current).getBoundingClientRect();
    const isVertical = direction === 'up' || direction === 'down';
    let best = null;
    let bestScore = Infinity;

    candidates.forEach(el => {
      if (el === current) return;
      const navEl = this.getFocusNavElement(el);
      const rect = navEl.getBoundingClientRect();

      // 方向过滤：候选元素必须整体在目标方向一侧（边界判断）
      switch (direction) {
        case 'right': if (rect.left < currentRect.right - 4) return; break;
        case 'left':  if (rect.right > currentRect.left + 4) return; break;
        case 'down':  if (rect.top < currentRect.bottom - 4) return; break;
        case 'up':    if (rect.bottom > currentRect.top + 4) return; break;
      }

      let primaryDistance, overlap;
      if (isVertical) {
        primaryDistance = direction === 'down'
          ? rect.top - currentRect.bottom
          : currentRect.top - rect.bottom;
        const overlapStart = Math.max(rect.left, currentRect.left);
        const overlapEnd = Math.min(rect.right, currentRect.right);
        overlap = Math.max(0, overlapEnd - overlapStart);
      } else {
        primaryDistance = direction === 'right'
          ? rect.left - currentRect.right
          : currentRect.left - rect.right;
        const overlapStart = Math.max(rect.top, currentRect.top);
        const overlapEnd = Math.min(rect.bottom, currentRect.bottom);
        overlap = Math.max(0, overlapEnd - overlapStart);
      }

      // 同列/同行候选享有绝对优先，斜对角候选附加更大偏移惩罚
      const crossOffset = isVertical
        ? Math.abs((rect.left + rect.width / 2) - (currentRect.left + currentRect.width / 2))
        : Math.abs((rect.top + rect.height / 2) - (currentRect.top + currentRect.height / 2));
      const score = overlap > 0 ? primaryDistance : primaryDistance + crossOffset * 3;

      if (score < bestScore) {
        bestScore = score;
        best = el;
      }
    });

    return best;
  }

  // 获取焦点显示目标元素（优先找外层容器，比如 .view-item / li 等）
  getFocusVisualElement(element) {
    if (!element) return null;
    const container = element.closest('.view-item, .tab-item, .dir-item, .show-card-item, .season-card, .episode-card-item, .gallery-card');
    return container || element;
  }

  // 获取导航定位用的元素（计算位置用外层容器，避免 scale 偏移影响方向判断）
  getFocusNavElement(element) {
    if (!element) return null;
    const container = element.closest('.view-item, .tab-item, .dir-item, .show-card-item, .season-card, .episode-card-item, .gallery-card');
    return container || element;
  }

  // 设置焦点
  setFocus(element, scroll = true) {
    if (!element || !this.isFocusable(element)) return;

    this.clearFocus();

    this.currentFocus = element;
    const visualEl = this.getFocusVisualElement(element);
    visualEl.classList.add(this.config.focusVisibleClass);
    element.focus({ preventScroll: true });

    if (scroll && this.config.scrollIntoView) {
      visualEl.scrollIntoView({
        behavior: this.config.scrollBehavior,
        block: this.config.scrollBlock,
        inline: 'center'
      });
    }

    this.updateFocusIndicator(visualEl);
    this.emit('focus', { element });
  }

  // 清除焦点
  clearFocus() {
    const prev = document.querySelector('.' + this.config.focusVisibleClass);
    if (prev) {
      prev.classList.remove(this.config.focusVisibleClass);
    }
    this.currentFocus = null;
  }

  // 检查元素或其子元素是否为输入控件
  containsInput(el) {
    if (!el) return false;
    const tag = (el.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return true;
    return !!(el.querySelector && el.querySelector('input, textarea, select'));
  }

  // 更新焦点指示器
  updateFocusIndicator(element) {
    if (!this._indicator || !element) return;
    if (!this.isTvMode) {
      this._indicator.style.display = 'none';
      return;
    }

    // 输入框/文本域：隐藏指示器覆盖层，避免遮挡输入区域
    // 这些元素已有 CSS box-shadow 焦点样式（tv-focus.css 中定义）
    if (this.containsInput(element)) {
      this._indicator.style.display = 'none';
      return;
    }

    const visualEl = this.getFocusVisualElement(element);
    const rect = visualEl.getBoundingClientRect();
    const padding = 2;

    this._indicator.style.display = 'block';
    this._indicator.style.left = (rect.left - padding) + 'px';
    this._indicator.style.top = (rect.top - padding) + 'px';
    this._indicator.style.width = (rect.width + padding * 2) + 'px';
    this._indicator.style.height = (rect.height + padding * 2) + 'px';
  }

  // 确认/选择
  confirm(e) {
    const focused = this.currentFocus || document.activeElement;
    if (focused && focused !== document.body) {
      focused.click();
      if (focused.getAttribute('data-action') === 'play') {
        this.togglePlay();
      }
    }
  }

  // 返回
  back() {
    // 检查是否有打开的模态框
    const modal = document.querySelector('.n-modal-container:not([style*="display: none"])');
    if (modal) {
      const closeBtn = modal.querySelector('.n-card-header-extra button, [data-action="close"]');
      if (closeBtn) {
        closeBtn.click();
        return;
      }
    }

    if (document.fullscreenElement) {
      document.exitFullscreen();
      return;
    }

    if (window.history.length > 1) {
      window.history.back();
    }

    this.emit('back');
  }

  // 播放器控制（保持原有 API）
  setPlayerInstance(player) {
    this.playerInstance = player;
    return this;
  }

  setPlayerControls(controls) {
    Object.assign(this.playerControls, controls);
    return this;
  }

  togglePlay() {
    if (this.playerControls.togglePlay) {
      this.playerControls.togglePlay();
    } else if (this.playerInstance) {
      if (this.playerInstance.playing) {
        this.playerInstance.pause();
      } else {
        this.playerInstance.play();
      }
    }
  }

  play() {
    if (this.playerControls.play) {
      this.playerControls.play();
    } else if (this.playerInstance) {
      this.playerInstance.play();
    }
  }

  pause() {
    if (this.playerControls.pause) {
      this.playerControls.pause();
    } else if (this.playerInstance) {
      this.playerInstance.pause();
    }
  }

  stop() {
    if (this.playerInstance) {
      this.playerInstance.pause();
      this.playerInstance.seek = 0;
    }
  }

  volumeUp() {
    if (this.playerControls.volumeUp) {
      this.playerControls.volumeUp();
    } else if (this.playerInstance) {
      this.playerInstance.volume = Math.min(1, this.playerInstance.volume + 0.1);
    }
  }

  volumeDown() {
    if (this.playerControls.volumeDown) {
      this.playerControls.volumeDown();
    } else if (this.playerInstance) {
      this.playerInstance.volume = Math.max(0, this.playerInstance.volume - 0.1);
    }
  }

  toggleMute() {
    if (this.playerControls.toggleMute) {
      this.playerControls.toggleMute();
    } else if (this.playerInstance) {
      this.playerInstance.muted = !this.playerInstance.muted;
    }
  }

  toggleFullscreen() {
    if (this.playerControls.toggleFullscreen) {
      this.playerControls.toggleFullscreen();
    } else if (this.playerInstance) {
      if (document.fullscreenElement) {
        document.exitFullscreen();
      } else {
        this.playerInstance.fullscreen = true;
      }
    }
  }

  seekForward(seconds = 10) {
    if (this.playerControls.seekForward) {
      this.playerControls.seekForward(seconds);
    } else if (this.playerInstance) {
      this.playerInstance.seek = Math.min(this.playerInstance.duration, this.playerInstance.seek + seconds);
    }
  }

  seekBackward(seconds = 10) {
    if (this.playerControls.seekBackward) {
      this.playerControls.seekBackward(seconds);
    } else if (this.playerInstance) {
      this.playerInstance.seek = Math.max(0, this.playerInstance.seek - seconds);
    }
  }

  nextEpisode() {
    if (this.playerControls.nextEpisode) {
      this.playerControls.nextEpisode();
    }
  }

  prevEpisode() {
    if (this.playerControls.prevEpisode) {
      this.playerControls.prevEpisode();
    }
  }

  // 事件系统
  on(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, []);
    }
    this.listeners.get(event).push(callback);
    return this;
  }

  off(event, callback) {
    if (this.listeners.has(event)) {
      const callbacks = this.listeners.get(event);
      const index = callbacks.indexOf(callback);
      if (index > -1) callbacks.splice(index, 1);
    }
    return this;
  }

  emit(event, data) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach(callback => callback(data));
    }
    return this;
  }

  // 销毁
  destroy() {
    document.removeEventListener('keydown', this.handleKeyDown, true);
    document.removeEventListener('focus', this.handleFocus, true);
    window.removeEventListener('resize', this.handleResize);
    window.removeEventListener('scroll', this.refresh, true);

    if (this._mutationObserver) {
      this._mutationObserver.disconnect();
      this._mutationObserver = null;
    }

    this.listeners.clear();
    this.clearFocus();

    const indicator = document.getElementById('tv-focus-indicator');
    if (indicator) indicator.remove();
  }
}

// 导出单例
export const tvNavigation = new TvNavigation();

// 导出 Vue 插件
export default {
  install(app, options = {}) {
    tvNavigation.init(options);
    app.config.globalProperties.$tvNavigation = tvNavigation;
    app.provide('tvNavigation', tvNavigation);
  }
};
