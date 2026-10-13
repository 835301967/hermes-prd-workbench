window.POC_MENUS = [
  { key: 'customers', title: '客户档案', core: true },
  { key: 'orders', title: '销售订单', core: false },
  { key: 'products', title: '产品目录', core: false }
];
window.Views = window.Views || {};
window.PocRouter = {
  key: function () {
    var hash = (location.hash || '#/customers').replace(/^#\/?/, '');
    var found = window.POC_MENUS.find(function (item) { return item.key === hash; });
    return found ? found.key : 'customers';
  },
  go: function (key) {
    location.hash = '#/' + key;
  }
};
window.addEventListener('hashchange', function () {
  if (typeof window.renderPoc === 'function') window.renderPoc();
});
