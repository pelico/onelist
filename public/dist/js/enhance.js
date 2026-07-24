// OneList 增强插件 - 目录浏览和自动刮削
(function() {
  'use strict';

  var token = localStorage.getItem('onelist_token') || '';

  function setToken(t) {
    token = t;
    localStorage.setItem('onelist_token', t);
  }

  function getToken() {
    if (!token) {
      var t = localStorage.getItem('token');
      if (t) {
        token = t;
      }
    }
    return token;
  }

  function request(url, method, data) {
    return new Promise(function(resolve, reject) {
      var t = getToken();
      fetch(url, {
        method: method || 'GET',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + t
        },
        body: data ? JSON.stringify(data) : undefined
      })
      .then(function(res) { return res.json(); })
      .then(function(res) {
        if (res.code === 200) {
          resolve(res.data);
        } else {
          reject(res.msg || '请求失败');
        }
      })
      .catch(reject);
    });
  }

  function createModal(title, contentHtml, onOk) {
    var mask = document.createElement('div');
    mask.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);z-index:9999;display:flex;align-items:center;justify-content:center;';
    var modal = document.createElement('div');
    modal.style.cssText = 'background:#fff;border-radius:8px;min-width:500px;max-width:80%;max-height:80vh;overflow:hidden;display:flex;flex-direction:column;';
    var header = document.createElement('div');
    header.style.cssText = 'padding:16px 20px;border-bottom:1px solid #eee;font-size:16px;font-weight:bold;display:flex;justify-content:space-between;align-items:center;';
    header.innerHTML = '<span>' + title + '</span>';
    var closeBtn = document.createElement('span');
    closeBtn.innerHTML = '✕';
    closeBtn.style.cssText = 'cursor:pointer;color:#999;';
    closeBtn.onclick = function() { mask.remove(); };
    header.appendChild(closeBtn);
    var body = document.createElement('div');
    body.style.cssText = 'padding:20px;flex:1;overflow:auto;';
    body.innerHTML = contentHtml;
    var footer = document.createElement('div');
    footer.style.cssText = 'padding:12px 20px;border-top:1px solid #eee;text-align:right;';
    var okBtn = document.createElement('button');
    okBtn.innerHTML = '确定';
    okBtn.style.cssText = 'padding:8px 20px;background:#409eff;color:#fff;border:none;border-radius:4px;cursor:pointer;';
    okBtn.onclick = function() {
      if (onOk) {
        onOk(function() { mask.remove(); });
      } else {
        mask.remove();
      }
    };
    footer.appendChild(okBtn);
    modal.appendChild(header);
    modal.appendChild(body);
    modal.appendChild(footer);
    mask.appendChild(modal);
    document.body.appendChild(mask);
    return { mask: mask, body: body, close: function() { mask.remove(); } };
  }

  function showToast(msg, type) {
    var toast = document.createElement('div');
    toast.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);padding:10px 20px;border-radius:4px;z-index:10000;color:#fff;font-size:14px;';
    if (type === 'error') {
      toast.style.background = '#f56c6c';
    } else if (type === 'success') {
      toast.style.background = '#67c23a';
    } else {
      toast.style.background = '#409eff';
    }
    toast.innerHTML = msg;
    document.body.appendChild(toast);
    setTimeout(function() { toast.remove(); }, 3000);
  }

  var currentGallery = null;
  var currentPath = '/';
  var selectedDirs = [];

  function renderTree(dirs, container, pathPrefix) {
    var ul = document.createElement('ul');
    ul.style.cssText = 'list-style:none;padding-left:20px;margin:0;';
    dirs.forEach(function(dir) {
      var li = document.createElement('li');
      li.style.cssText = 'margin:4px 0;';
      var item = document.createElement('div');
      item.style.cssText = 'display:flex;align-items:center;gap:8px;padding:4px 8px;cursor:pointer;border-radius:4px;';
      item.onmouseover = function() { item.style.background = '#f5f7fa'; };
      item.onmouseout = function() { item.style.background = 'transparent'; };

      var checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.style.cssText = 'cursor:pointer;';
      checkbox.onclick = function(e) {
        e.stopPropagation();
        if (checkbox.checked) {
          if (selectedDirs.indexOf(dir.path) === -1) {
            selectedDirs.push(dir.path);
          }
        } else {
          selectedDirs = selectedDirs.filter(function(p) { return p !== dir.path; });
        }
      };

      var icon = document.createElement('span');
      icon.innerHTML = dir.children && dir.children.length > 0 ? '📁' : '📂';
      icon.style.cssText = 'font-size:14px;';

      var name = document.createElement('span');
      name.innerHTML = dir.name;
      name.style.cssText = 'font-size:14px;';

      var scanBtn = document.createElement('button');
      scanBtn.innerHTML = '扫描此目录';
      scanBtn.style.cssText = 'margin-left:auto;padding:2px 8px;font-size:12px;background:#67c23a;color:#fff;border:none;border-radius:3px;cursor:pointer;';
      scanBtn.onclick = function(e) {
        e.stopPropagation();
        scanDirectory(dir.path);
      };

      item.appendChild(checkbox);
      item.appendChild(icon);
      item.appendChild(name);
      item.appendChild(scanBtn);
      li.appendChild(item);

      if (dir.children && dir.children.length > 0) {
        var childContainer = document.createElement('div');
        childContainer.style.display = 'none';
        item.onclick = function() {
          if (childContainer.style.display === 'none') {
            childContainer.style.display = 'block';
            icon.innerHTML = '📂';
          } else {
            childContainer.style.display = 'none';
            icon.innerHTML = '📁';
          }
        };
        renderTree(dir.children, childContainer, dir.path);
        li.appendChild(childContainer);
      } else {
        item.onclick = function() {
          if (checkbox.checked) {
            checkbox.checked = false;
            selectedDirs = selectedDirs.filter(function(p) { return p !== dir.path; });
          } else {
            checkbox.checked = true;
            if (selectedDirs.indexOf(dir.path) === -1) {
              selectedDirs.push(dir.path);
            }
          }
        };
      }

      ul.appendChild(li);
    });
    container.appendChild(ul);
  }

  function loadDirectoryTree(galleryUid, depth) {
    showToast('正在加载目录...', 'info');
    request('/v1/api/gallery/alist_tree?id=' + galleryUid + '&path=/&depth=' + (depth || 3), 'GET')
      .then(function(tree) {
        showDirectoryBrowser(tree);
      })
      .catch(function(err) {
        showToast('加载目录失败: ' + err, 'error');
      });
  }

  function showDirectoryBrowser(tree) {
    selectedDirs = [];
    var html = '<div style="margin-bottom:12px;"><button id="scanSelectedBtn" style="padding:8px 16px;background:#e6a23c;color:#fff;border:none;border-radius:4px;cursor:pointer;">批量刮削选中目录</button> <span id="selectedCount" style="margin-left:10px;color:#666;font-size:13px;">已选择 0 个目录</span></div><div id="treeContainer"></div>';

    var modal = createModal('Alist 目录浏览', html, function(close) {
      if (selectedDirs.length > 0) {
        if (confirm('确定要刮削选中的 ' + selectedDirs.length + ' 个目录吗？')) {
          batchScan(selectedDirs, close);
        }
      } else {
        close();
      }
    });

    var treeContainer = modal.body.querySelector('#treeContainer');
    var selectedCount = modal.body.querySelector('#selectedCount');
    var scanBtn = modal.body.querySelector('#scanSelectedBtn');

    function updateCount() {
      selectedCount.innerHTML = '已选择 ' + selectedDirs.length + ' 个目录';
    }

    var observer = new MutationObserver(updateCount);
    observer.observe(treeContainer, { childList: true, subtree: true, attributes: true });

    renderTree(tree, treeContainer, '/');
    updateCount();
  }

  function scanDirectory(path) {
    if (!currentGallery) return;
    if (!confirm('确定要扫描目录 ' + path + ' 并创建刮削任务吗？')) return;
    showToast('正在扫描...', 'info');
    request('/v1/api/gallery/alist_scan?id=' + currentGallery.gallery_uid + '&path=' + encodeURIComponent(path), 'POST')
      .then(function() {
        showToast('扫描任务已创建！', 'success');
        setTimeout(function() { location.reload(); }, 1500);
      })
      .catch(function(err) {
        showToast('扫描失败: ' + err, 'error');
      });
  }

  function batchScan(paths, close) {
    if (!currentGallery) return;
    showToast('正在批量扫描...', 'info');
    var completed = 0;
    var failed = 0;
    paths.forEach(function(path) {
      request('/v1/api/gallery/alist_scan?id=' + currentGallery.gallery_uid + '&path=' + encodeURIComponent(path), 'POST')
        .then(function() {
          completed++;
          if (completed + failed === paths.length) {
            showToast('批量扫描完成！成功 ' + completed + ' 个，失败 ' + failed + ' 个', 'success');
            if (close) close();
            setTimeout(function() { location.reload(); }, 1500);
          }
        })
        .catch(function() {
          failed++;
          if (completed + failed === paths.length) {
            showToast('批量扫描完成！成功 ' + completed + ' 个，失败 ' + failed + ' 个', completed > 0 ? 'success' : 'error');
            if (close) close();
            setTimeout(function() { location.reload(); }, 1500);
          }
        });
    });
  }

  function addBrowserButton() {
    var observer = new MutationObserver(function() {
      var galleryCards = document.querySelectorAll('.el-card');
      galleryCards.forEach(function(card) {
        if (card.querySelector('.browse-btn')) return;
        var title = card.querySelector('.gallery-title');
        if (title) {
          var btn = document.createElement('button');
          btn.className = 'browse-btn';
          btn.innerHTML = '📂 浏览目录';
          btn.style.cssText = 'margin-left:10px;padding:4px 12px;font-size:12px;background:#409eff;color:#fff;border:none;border-radius:4px;cursor:pointer;';
          btn.onclick = function(e) {
            e.stopPropagation();
            var uid = card.getAttribute('data-uid') || card.dataset.uid;
            if (!uid) {
              loadGalleries(function(galleries) {
                var gallery = galleries.find(function(g) { return g.title === title.textContent; });
                if (gallery) {
                  currentGallery = gallery;
                  loadDirectoryTree(gallery.gallery_uid, 3);
                }
              });
            } else {
              loadGalleries(function(galleries) {
                var gallery = galleries.find(function(g) { return g.gallery_uid === uid; });
                if (gallery) {
                  currentGallery = gallery;
                  loadDirectoryTree(gallery.gallery_uid, 3);
                }
              });
            }
          };
          title.parentNode.appendChild(btn);
        }
      });
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  function loadGalleries(callback) {
    request('/v1/api/gallery/list?page=1&size=100', 'POST')
      .then(function(data) {
        if (callback) callback(data || []);
      })
      .catch(function() {
        if (callback) callback([]);
      });
  }

  function injectStyle() {
    var style = document.createElement('style');
    style.innerHTML = `
      .browse-btn {
        transition: all 0.3s;
      }
      .browse-btn:hover {
        opacity: 0.8;
      }
    `;
    document.head.appendChild(style);
  }

  function init() {
    injectStyle();
    addBrowserButton();

    var origFetch = window.fetch;
    window.fetch = function() {
      var args = arguments;
      return origFetch.apply(this, args).then(function(res) {
        var url = args[0];
        if (typeof url === 'string' && url.indexOf('/login') > -1) {
          res.clone().json().then(function(data) {
            if (data && data.data && data.data.token) {
              setToken(data.data.token);
            }
          });
        }
        return res;
      });
    };

    console.log('%c OneList 增强插件已加载 ', 'background:#409eff;color:#fff;padding:4px 8px;border-radius:4px;');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
