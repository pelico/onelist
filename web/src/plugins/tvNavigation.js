/**
 * 鐢佃閬ユ帶鍣ㄥ鑸彃浠?(Spatial Navigation 鐗?
 * 鏀寔 Android TV / 鏅鸿兘鐢佃 / 閬ユ帶鍣?/ 閿洏鏂瑰悜閿搷浣?
 *
 * 鎸夐敭鏄犲皠:
 * - ArrowUp/ArrowDown/ArrowLeft/ArrowRight: 鏂瑰悜瀵艰埅
 * - Enter/Space: 纭
 * - Escape/Backspace: 杩斿洖/鍏抽棴寮圭獥
 * - MediaPlayPause/MediaPlay/MediaPause: 濯掍綋鎺у埗
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

    // 缁戝畾 this
    this.handleKeyDown = this.handleKeyDown.bind(this);
    this.handleFocus = this.handleFocus.bind(this);
    this.handleResize = this.handleResize.bind(this);
    this.refresh = this.refresh.bind(this);
  }

  // 妫€娴嬫槸鍚︿负鐢佃鐜锛孶RL ?tv=1 浼氳嚜鍔ㄦ寔涔呭寲鍒?localStorage
  detectTvMode() {
    const ua = navigator.userAgent.toLowerCase();
    const isTv = /tv|smart-tv|smarttv|googletv|appletv|hbbtv|netcast|viera|nettv|roku|firetv|fire-tv|aft|aftb|aftt|aftm|aftd|android tv/i.test(ua);
    const isAndroid = /android/i.test(ua) && !/mobile/i.test(ua);
    const isLargeScreen = window.screen.width >= 1280 && window.screen.height >= 720;
    const hasTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

    const urlParams = new URLSearchParams(window.location.search);
    const forceTvFromUrl = urlParams.get('tv') === '1' || urlParams.get('tv') === 'true';

    // URL 鍙傛暟寮€鍚椂锛岃嚜鍔ㄦ寔涔呭寲锛岄伩鍏嶈烦杞悗涓㈠け
    if (forceTvFromUrl) {
      try { localStorage.setItem('forceTvMode', 'true'); } catch (e) {}
    }

    const forceTvFromStorage = (() => {
      try {
        const val = localStorage.getItem('forceTvMode');
        // 鏈缃繃鏃堕粯璁ゅ惎鐢紝绠＄悊鍛樺彲鍦ㄨ缃腑鍏抽棴
        return val === null || val === 'true';
      } catch (e) { return true; }
    })();

    this.isTvMode = forceTvFromUrl || forceTvFromStorage || isTv || (isAndroid && isLargeScreen && !hasTouch);
    return this.isTvMode;
  }

  // 鍒囨崲 TV 妯″紡锛堜緵璁剧疆椤佃皟鐢級
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

  // 鍒濆鍖?
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
      // 椤甸潰鍔犺浇鍚庤嚜鍔ㄨ仛鐒︾涓€涓彲鑱氱劍鍏冪礌
      setTimeout(() => {
        this.refresh();
        const first = this.getFirstFocusable();
        if (first && !this.currentFocus) this.setFocus(first);
      }, 300);
    }

    console.log('[TvNavigation] Initialized, TV mode:', this.isTvMode);
    return this;
  }

  // 缁戝畾鍏ㄥ眬閿洏浜嬩欢
  bindGlobalEvents() {
    document.addEventListener('keydown', this.handleKeyDown, true);
    document.addEventListener('focus', this.handleFocus, true);
    window.addEventListener('resize', this.handleResize);
    window.addEventListener('scroll', this.refresh, true);
  }

  // 鍒涘缓鐒︾偣鎸囩ず鍣?
  createFocusIndicator() {
    if (document.getElementById('tv-focus-indicator')) return;

    const indicator = document.createElement('div');
    indicator.id = 'tv-focus-indicator';
    indicator.className = 'tv-focus-indicator';
    indicator.setAttribute('aria-hidden', 'true');
    document.body.appendChild(indicator);
    this._indicator = indicator;
  }

  // 鐩戝惉 DOM 鍙樺寲锛岃嚜鍔ㄥ埛鏂扮劍鐐瑰垪琛?
  startMutationObserver() {
    if (this._mutationObserver || typeof MutationObserver === 'undefined') return;

    this._mutationObserver = new MutationObserver((mutations) => {
      // DOM 鍙樺寲杈冨ぇ鏃跺欢杩熷埛鏂帮紝閬垮厤棰戠箒鎵弿
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

  // 閿洏浜嬩欢澶勭悊
  handleKeyDown(e) {
    if (!this.isTvMode && !this.config.forceTvMode) return;

    const key = e.key;

    // 杈撳叆妗?鏂囨湰鍩熷唴锛氫笉鎷︽埅 Enter/Space锛堜繚鐣欏師鐢熻緭鍏ヨ涓猴級锛屾柟鍚戦敭浠嶅彲鐢ㄤ簬瀵艰埅
    const tag = (e.target.tagName || '').toLowerCase();
    const isInputField = tag === 'input' || tag === 'textarea';
    if (isInputField && (key === 'Enter' || key === ' ')) {
      return;
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

  // 鐒︾偣鍙樺寲澶勭悊
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

  // 绐楀彛澶у皬鍙樺寲澶勭悊
  handleResize() {
    clearTimeout(this._resizeTimer);
    this._resizeTimer = setTimeout(() => {
      this.refresh();
      if (this.currentFocus) this.updateFocusIndicator(this.currentFocus);
    }, 100);
  }

  // 娉ㄥ唽鐒︾偣鍏冪礌缁勶紙鍏煎鏃?API锛屽厓绱犱細琚撼鍏ュ叏灞€瀵艰埅锛?
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

  // 鍙栨秷娉ㄥ唽鐒︾偣缁?
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

  // 璁剧疆褰撳墠鐒︾偣缁勶紙鍏煎鏃?API锛?
  setCurrentGroup(name) {
    const el = this.focusables.find(el => el.getAttribute('data-tv-group') === name);
    if (el) this.setFocus(el);
    return this;
  }

  // 鍒锋柊鍙仛鐒﹀厓绱犲垪琛?
  refresh() {
    this.focusables = this.scanFocusables();
    return this;
  }

  // 鎵弿椤甸潰涓婃墍鏈夊彲鑱氱劍鍏冪礌
  scanFocusables() {
    const candidates = document.querySelectorAll(
      'a, button, input, textarea, select, [tabindex]:not([tabindex="-1"])'
    );

    const list = [];
    candidates.forEach(el => {
      if (this.isFocusable(el)) {
        list.push(el);
      }
    });

    // 鎸?DOM 浣嶇疆鎺掑簭锛屼綔涓哄厹搴?
    list.sort((a, b) => {
      const pos = a.compareDocumentPosition(b);
      return pos & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1;
    });

    return list;
  }

  // 妫€鏌ュ厓绱犳槸鍚﹀彲鑱氱劍
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

  // 鑾峰彇绗竴涓彲鑱氱劍鍏冪礌
  getFirstFocusable() {
    return this.focusables[0] || null;
  }

  // 鏂瑰悜瀵艰埅锛圫patial Navigation锛?
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

  // 鎵惧埌鎸囧畾鏂瑰悜涓婃渶杩戠殑鍏冪礌锛堢綉鏍煎榻愪紭鍏堢畻娉曪級
  findNearestInDirection(current, direction, candidates) {
    const currentRect = this.getFocusNavElement(current).getBoundingClientRect();
    const isVertical = direction === 'up' || direction === 'down';
    let best = null;
    let bestScore = Infinity;

    candidates.forEach(el => {
      if (el === current) return;
      const navEl = this.getFocusNavElement(el);
      const rect = navEl.getBoundingClientRect();

      // 鏂瑰悜杩囨护锛氬€欓€夊厓绱犲繀椤绘暣浣撳湪鐩爣鏂瑰悜涓€渚э紙杈圭晫鍒ゆ柇锛?
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

      // 鍚屽垪/鍚岃鍊欓€変韩鏈夌粷瀵逛紭鍏堬紝鏂滃瑙掑€欓€夐檮鍔犳洿澶у亸绉绘儵缃?
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

  // 鑾峰彇鐒︾偣鏄剧ず鐩爣鍏冪礌锛堜紭鍏堟壘澶栧眰瀹瑰櫒锛屾瘮濡?.view-item / li 绛夛級
  getFocusVisualElement(element) {
    if (!element) return null;
    const container = element.closest('.view-item, .tab-item, .dir-item, .show-card-item, .season-card, .episode-card-item, .gallery-card');
    return container || element;
  }

  // 鑾峰彇瀵艰埅瀹氫綅鐢ㄧ殑鍏冪礌锛堣绠椾綅缃敤澶栧眰瀹瑰櫒锛岄伩鍏?scale 鍋忕Щ褰卞搷鏂瑰悜鍒ゆ柇锛?
  getFocusNavElement(element) {
    if (!element) return null;
    const container = element.closest('.view-item, .tab-item, .dir-item, .show-card-item, .season-card, .episode-card-item, .gallery-card');
    return container || element;
  }

  // 璁剧疆鐒︾偣
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

  // 娓呴櫎鐒︾偣
  clearFocus() {
    const prev = document.querySelector('.' + this.config.focusVisibleClass);
    if (prev) {
      prev.classList.remove(this.config.focusVisibleClass);
    }
    this.currentFocus = null;
  }

  // 妫€鏌ュ厓绱犳垨鍏跺瓙鍏冪礌鏄惁涓鸿緭鍏ユ帶浠?
  containsInput(el) {
    if (!el) return false;
    const tag = (el.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return true;
    return !!(el.querySelector && el.querySelector('input, textarea, select'));
  }

  // 鏇存柊鐒︾偣鎸囩ず鍣?
  updateFocusIndicator(element) {
    if (!this._indicator || !element) return;
    if (!this.isTvMode) {
      this._indicator.style.display = 'none';
      return;
    }

    // 杈撳叆妗?鏂囨湰鍩燂細闅愯棌鎸囩ず鍣ㄨ鐩栧眰锛岄伩鍏嶉伄鎸¤緭鍏ュ尯鍩?
    // 杩欎簺鍏冪礌宸叉湁 CSS box-shadow 鐒︾偣鏍峰紡锛坱v-focus.css 涓畾涔夛級
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

  // 纭/閫夋嫨
  confirm(e) {
    const focused = this.currentFocus || document.activeElement;
    if (focused && focused !== document.body) {
      focused.click();
      if (focused.getAttribute('data-action') === 'play') {
        this.togglePlay();
      }
    }
  }

  // 杩斿洖
  back() {
    // 妫€鏌ユ槸鍚︽湁鎵撳紑鐨勬ā鎬佹
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

  // 鎾斁鍣ㄦ帶鍒讹紙淇濇寔鍘熸湁 API锛?
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

  // 浜嬩欢绯荤粺
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

  // 閿€姣?
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

// 瀵煎嚭鍗曚緥
export const tvNavigation = new TvNavigation();

// 瀵煎嚭 Vue 鎻掍欢
export default {
  install(app, options = {}) {
    tvNavigation.init(options);
    app.config.globalProperties.$tvNavigation = tvNavigation;
    app.provide('tvNavigation', tvNavigation);
  }
};
