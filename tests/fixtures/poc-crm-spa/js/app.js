(function () {
  function toast(text) {
    var n = document.createElement('div');
    n.className = 'toast';
    n.textContent = text;
    document.body.appendChild(n);
    setTimeout(function () { n.remove(); }, 1400);
  }
  window.renderPoc = function () {
    var key = PocRouter.key();
    var sider = document.getElementById('sider');
    var tabs = document.getElementById('tabs');
    var view = document.getElementById('view');
    sider.innerHTML = window.POC_MENUS.map(function (item) {
      return '<button class="menu' + (item.key === key ? ' on' : '') + '" data-route="' + item.key + '">' + item.title + '</button>';
    }).join('');
    tabs.innerHTML = window.POC_MENUS.map(function (item) {
      return '<button class="tab' + (item.key === key ? ' on' : '') + '" data-route="' + item.key + '">' + item.title + '</button>';
    }).join('');
    var handler = window.Views[key];
    view.innerHTML = '';
    if (handler && typeof handler.render === 'function') handler.render(view);
    else view.innerHTML = '<div class="empty">未实现该模块</div>';
  };
  document.addEventListener('click', function (ev) {
    var btn = ev.target.closest('[data-route]');
    if (!btn) return;
    PocRouter.go(btn.getAttribute('data-route'));
    window.renderPoc();
  });
  if (!location.hash) location.hash = '#/customers';
  window.renderPoc();
  toast('POC 已就绪');
})();
