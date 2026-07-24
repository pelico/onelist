/**
 * 电视焦点指令
 * 用于标记可聚焦元素并自动注册到导航系统
 */
import { tvNavigation } from '../plugins/tvNavigation';

export const tvFocus = {
  mounted(el, binding) {
    const options = binding.value || {};
    const group = options.group || 'default';
    const index = options.index;
    
    el.setAttribute('tabindex', options.tabindex || '0');
    el.setAttribute('data-tv-group', group);
    
    if (index !== undefined) {
      el.setAttribute('data-tv-index', index);
    }
    
    // 自动聚焦
    if (options.autoFocus && tvNavigation.isTvMode) {
      setTimeout(() => {
        tvNavigation.setFocus(el);
      }, 100);
    }
  },
  
  updated(el, binding) {
    const options = binding.value || {};
    const group = options.group || 'default';
    const index = options.index;
    
    el.setAttribute('data-tv-group', group);
    
    if (index !== undefined) {
      el.setAttribute('data-tv-index', index);
    }
  },
  
  unmounted(el) {
    el.removeAttribute('tabindex');
    el.removeAttribute('data-tv-group');
    el.removeAttribute('data-tv-index');
  }
};

export const tvGroup = {
  mounted(el, binding) {
    const groupName = binding.value || binding.arg || 'default';
    const options = binding.modifiers || {};
    
    el.setAttribute('data-tv-group-container', groupName);
    
    // 延迟注册，等待子元素渲染
    setTimeout(() => {
      const children = el.querySelectorAll('[data-tv-index]');
      if (children.length > 0) {
        tvNavigation.registerGroup(groupName, Array.from(children), {
          vertical: options.vertical || false,
          wrap: options.wrap || false,
          cols: parseInt(binding.arg, 10) || 0
        });
      }
    }, 50);
  },
  
  unmounted(el) {
    const groupName = el.getAttribute('data-tv-group-container');
    if (groupName) {
      tvNavigation.unregisterGroup(groupName);
    }
  }
};

// 注册指令
export function setupTvFocusDirectives(app) {
  app.directive('tv-focus', tvFocus);
  app.directive('tv-group', tvGroup);
}