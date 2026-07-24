/**
 * 电视遥控器导航插件
 * 支持 Android TV / 智能电视遥控器操作
 * 
 * 按键映射:
 * - ArrowUp/ArrowDown/ArrowLeft/ArrowRight: 方向导航
 * - Enter/Space: 确认/播放暂停
 * - Escape/Backspace: 返回/退出
 * - MediaPlayPause/MediaPlay/MediaPause: 媒体控制
 */

class TvNavigation {
  constructor() {
    this.focusableElements = [];
    this.currentFocusIndex = -1;
    this.currentGroup = 'default';
    this.groups = {};
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
      wrapAround: false,
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
  }

  // 检测是否为电视环境
  detectTvMode() {
    const ua = navigator.userAgent.toLowerCase();
    const isTv = /tv|smart-tv|smarttv|googletv|appletv|hbbtv|netcast|viera|nettv|roku|firetv|fire-tv|aft|aftb|aftt|aftm|aftd|android tv/i.test(ua);
    const isAndroid = /android/i.test(ua) && !/mobile/i.test(ua);
    const isLargeScreen = window.screen.width >= 1280 && window.screen.height >= 720;
    const hasTouch = 'ontouchstart' in window;
    
    this.isTvMode = isTv || (isAndroid && isLargeScreen && !hasTouch);
    return this.isTvMode;
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
    
    console.log('[TvNavigation] Initialized, TV mode:', this.isTvMode);
    return this;
  }

  // 绑定全局键盘事件
  bindGlobalEvents() {
    document.addEventListener('keydown', this.handleKeyDown.bind(this));
    
    // 监听焦点变化
    document.addEventListener('focus', (e) => {
      if (this.isFocusable(e.target)) {
        this.updateFocusState(e.target);
      }
    }, true);
  }

  // 创建焦点指示器
  createFocusIndicator() {
    if (document.getElementById('tv-focus-indicator')) return;
    
    const indicator = document.createElement('div');
    indicator.id = 'tv-focus-indicator';
    indicator.className = 'tv-focus-indicator';
    indicator.setAttribute('aria-hidden', 'true');
    document.body.appendChild(indicator);
  }

  // 键盘事件处理
  handleKeyDown(e) {
    if (!this.isTvMode && !this.config.forceTvMode) return;
    
    const key = e.key;
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
      this.emit('navigate', { key, action: handlers[key].name });
    }
  }

  // 注册焦点元素组
  registerGroup(name, elements, options = {}) {
    this.groups[name] = {
      elements: Array.isArray(elements) ? elements : [elements],
      options: {
        vertical: false,
        wrap: false,
        cols: 0, // 网格列数，0 表示自动检测
        ...options
      }
    };
    
    // 为元素添加焦点属性
    this.groups[name].elements.forEach((el, index) => {
      if (el) {
        el.setAttribute('data-tv-group', name);
        el.setAttribute('data-tv-index', index);
        el.setAttribute('tabindex', '0');
      }
    });
    
    return this;
  }

  // 取消注册焦点组
  unregisterGroup(name) {
    if (this.groups[name]) {
      this.groups[name].elements.forEach(el => {
        if (el) {
          el.removeAttribute('data-tv-group');
          el.removeAttribute('data-tv-index');
        }
      });
      delete this.groups[name];
    }
    return this;
  }

  // 更新焦点组元素
  updateGroup(name, elements) {
    if (this.groups[name]) {
      this.unregisterGroup(name);
    }
    return this.registerGroup(name, elements, this.groups[name]?.options || {});
  }

  // 设置当前焦点组
  setCurrentGroup(name) {
    if (this.groups[name]) {
      this.currentGroup = name;
      const group = this.groups[name];
      
      if (group.elements.length > 0) {
        const startIndex = this.findFirstFocusable(group.elements);
        this.setFocus(group.elements[startIndex]);
      }
    }
    return this;
  }

  // 查找第一个可聚焦元素
  findFirstFocusable(elements) {
    for (let i = 0; i < elements.length; i++) {
      if (this.isFocusable(elements[i])) {
        return i;
      }
    }
    return 0;
  }

  // 检查元素是否可聚焦
  isFocusable(el) {
    if (!el) return false;
    const style = window.getComputedStyle(el);
    return style.display !== 'none' && 
           style.visibility !== 'hidden' && 
           !el.disabled &&
           el.offsetParent !== null;
  }

  // 方向导航
  navigate(direction) {
    const group = this.groups[this.currentGroup];
    if (!group || group.elements.length === 0) return;

    const currentIndex = this.currentFocusIndex >= 0 ? this.currentFocusIndex : 0;
    const { vertical, wrap, cols } = group.options;
    
    let nextIndex = currentIndex;
    const elements = group.elements.filter(el => this.isFocusable(el));
    const count = elements.length;
    
    // 自动检测列数
    const actualCols = cols || this.detectGridCols(elements);
    
    if (actualCols > 1) {
      // 网格导航
      nextIndex = this.navigateGrid(currentIndex, direction, count, actualCols, wrap);
    } else if (vertical) {
      // 垂直列表导航
      nextIndex = this.navigateList(currentIndex, direction, count, wrap, true);
    } else {
      // 水平列表导航
      nextIndex = this.navigateList(currentIndex, direction, count, wrap, false);
    }
    
    if (nextIndex !== currentIndex && nextIndex >= 0 && nextIndex < count) {
      this.setFocus(elements[nextIndex]);
      this.currentFocusIndex = nextIndex;
    }
  }

  // 检测网格列数
  detectGridCols(elements) {
    if (elements.length < 2) return 1;
    
    const firstTop = elements[0].getBoundingClientRect().top;
    for (let i = 1; i < elements.length; i++) {
      if (Math.abs(elements[i].getBoundingClientRect().top - firstTop) > 10) {
        return i;
      }
    }
    return elements.length;
  }

  // 列表导航
  navigateList(current, direction, count, wrap, vertical) {
    const isForward = vertical ? direction === 'down' : direction === 'right';
    const isBackward = vertical ? direction === 'up' : direction === 'left';
    
    if (isForward) {
      if (current < count - 1) {
        return current + 1;
      } else if (wrap) {
        return 0;
      }
    } else if (isBackward) {
      if (current > 0) {
        return current - 1;
      } else if (wrap) {
        return count - 1;
      }
    }
    
    return current;
  }

  // 网格导航
  navigateGrid(current, direction, count, cols, wrap) {
    const row = Math.floor(current / cols);
    const col = current % cols;
    const rows = Math.ceil(count / cols);
    
    let newRow = row;
    let newCol = col;
    
    switch (direction) {
      case 'up':
        newRow = row > 0 ? row - 1 : (wrap ? rows - 1 : 0);
        break;
      case 'down':
        newRow = row < rows - 1 ? row + 1 : (wrap ? 0 : row);
        break;
      case 'left':
        newCol = col > 0 ? col - 1 : (wrap ? cols - 1 : 0);
        break;
      case 'right':
        newCol = col < cols - 1 ? col + 1 : (wrap ? 0 : col);
        break;
    }
    
    let nextIndex = newRow * cols + newCol;
    
    // 处理最后一行元素不足的情况
    if (nextIndex >= count) {
      nextIndex = count - 1;
    }
    
    return nextIndex;
  }

  // 设置焦点
  setFocus(element, scroll = true) {
    if (!element) return;
    
    // 移除之前的焦点
    const prevFocused = document.querySelector('.' + this.config.focusVisibleClass);
    if (prevFocused) {
      prevFocused.classList.remove(this.config.focusVisibleClass);
    }
    
    // 设置新焦点
    element.classList.add(this.config.focusVisibleClass);
    element.focus({ preventScroll: true });
    
    // 滚动到可见区域
    if (scroll && this.config.scrollIntoView) {
      element.scrollIntoView({
        behavior: this.config.scrollBehavior,
        block: this.config.scrollBlock,
        inline: 'center'
      });
    }
    
    // 更新焦点指示器位置
    this.updateFocusIndicator(element);
    
    // 发送事件
    this.emit('focus', { element, index: this.currentFocusIndex });
  }

  // 更新焦点指示器
  updateFocusIndicator(element) {
    const indicator = document.getElementById('tv-focus-indicator');
    if (!indicator || !element) return;
    
    const rect = element.getBoundingClientRect();
    const padding = 4;
    
    indicator.style.display = 'block';
    indicator.style.left = (rect.left - padding) + 'px';
    indicator.style.top = (rect.top - padding) + 'px';
    indicator.style.width = (rect.width + padding * 2) + 'px';
    indicator.style.height = (rect.height + padding * 2) + 'px';
  }

  // 更新焦点状态
  updateFocusState(element) {
    const group = element.getAttribute('data-tv-group');
    const index = parseInt(element.getAttribute('data-tv-index'), 10);
    
    if (group && !isNaN(index)) {
      this.currentGroup = group;
      this.currentFocusIndex = index;
    }
    
    this.updateFocusIndicator(element);
  }

  // 确认/选择
  confirm(e) {
    const focused = document.activeElement;
    if (focused && focused !== document.body) {
      // 触发点击事件
      focused.click();
      
      // 如果是播放按钮，调用播放器控制
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
    
    // 检查播放器是否全屏
    if (document.fullscreenElement) {
      document.exitFullscreen();
      return;
    }
    
    // 调用路由返回
    if (window.history.length > 1) {
      window.history.back();
    }
    
    this.emit('back');
  }

  // 播放器控制
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
      if (index > -1) {
        callbacks.splice(index, 1);
      }
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
    document.removeEventListener('keydown', this.handleKeyDown);
    this.listeners.clear();
    
    const indicator = document.getElementById('tv-focus-indicator');
    if (indicator) {
      indicator.remove();
    }
  }
}

// 导出单例
export const tvNavigation = new TvNavigation();

// 导出 Vue 插件
export default {
  install(app, options = {}) {
    tvNavigation.init(options);
    
    // 提供全局属性
    app.config.globalProperties.$tvNavigation = tvNavigation;
    
    // 提供全局方法
    app.provide('tvNavigation', tvNavigation);
  }
};