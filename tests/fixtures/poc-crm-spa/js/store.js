window.PocStore = (function () {
  var fallback = {
    customers: [
      { id: 'C-001', name: '示例精细化工', owner: '李销', level: 'A', status: '跟进中', phone: '000-0000-0000', city: '广州' },
      { id: 'C-002', name: '南沙仓储', owner: '王销', level: 'B', status: '公海', phone: '000-0000-0000', city: '广州' },
      { id: 'C-003', name: '黄埔贸易', owner: '李销', level: 'A', status: '成交', phone: '000-0000-0000', city: '广州' },
      { id: 'C-004', name: '番禺实验室', owner: '赵销', level: 'C', status: '跟进中', phone: '000-0000-0000', city: '广州' }
    ],
    orders: [
      { id: 'SO-001', customer: '示例精细化工', amount: 128000, status: '待审', owner: '李销' },
      { id: 'SO-002', customer: '黄埔贸易', amount: 56000, status: '已发', owner: '李销' },
      { id: 'SO-003', customer: '南沙仓储', amount: 9800, status: '草稿', owner: '王销' }
    ],
    products: [
      { id: 'P-001', name: '工业清洗剂A', spec: '25kg', price: 380, stock: 120 },
      { id: 'P-002', name: '防锈油B', spec: '18L', price: 260, stock: 40 },
      { id: 'P-003', name: '表面处理液C', spec: '10kg', price: 510, stock: 8 }
    ]
  };
  var seed = window.__POC_MOCK__ || fallback;
  var data = {
    customers: (seed.customers || []).map(function (row) { return Object.assign({}, row); }),
    orders: (seed.orders || []).map(function (row) { return Object.assign({}, row); }),
    products: (seed.products || []).map(function (row) { return Object.assign({}, row); })
  };
  var seq = 100;
  function list(key) { return (data[key] || []).slice(); }
  function get(key, id) {
    return (data[key] || []).find(function (row) { return row.id === id; }) || null;
  }
  function create(key, row) {
    seq += 1;
    var item = Object.assign({ id: key.slice(0, 1).toUpperCase() + '-' + seq }, row);
    data[key].unshift(item);
    return item;
  }
  function update(key, id, patch) {
    var row = get(key, id);
    if (!row) return null;
    Object.keys(patch).forEach(function (k) { row[k] = patch[k]; });
    return row;
  }
  function remove(key, id) {
    data[key] = (data[key] || []).filter(function (row) { return row.id !== id; });
  }
  return { list: list, get: get, create: create, update: update, remove: remove };
})();
