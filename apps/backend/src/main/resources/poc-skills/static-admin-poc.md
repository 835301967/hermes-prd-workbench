# static-admin-poc（静态多文件 POC 演示系统）

你正在生成一份可给售前直接演示的中后台 POC。产物是多文件静态 SPA，不是 Vue/Vite 工程，也不是单文件 Demo。

## 交付物

必须写入沙箱（只能用 `write_poc_file`）：

```text
data/catalog.json
index.html
css/app.css
js/store.js
js/router.js
js/ui.js
js/app.js
js/views/<key>.js
data/mock.json
```

按 `catalog.modules` 的数量写视图文件，不设 8～15 的固定个数。沙箱磁盘上限仍是单文件 100KB；**模型单轮写入预算是 js/html/css ≤ 20000 字符、JSON ≤ 28000 字符**。禁止为了避开截断而删 catalog/菜单。禁止 `package.json`、`src/`、`.env*`、Vue SFC、`import`、CDN、`fetch`、`XMLHttpRequest`。

提交：`submit_result(mode=POC, payload={mode,title,summary,workspaceFile:"index.html",files:[...相对路径]})`。不要把文件正文塞进 payload。

## 唯一事实源：data/catalog.json

若工作区已有 `data/required-modules.json`（控制面按当前稿 catalog 预置），**先 `read_poc_file` 读取，禁止覆盖**。`catalog.modules[].key` / `title` / `core` 必须按该清单原样抄写：STANDARD/THOROUGH 不得漏 key，也不得改成 `platforms` / `subjects` / `vouchers` 这类另一套菜单。允许额外增加二级台账，但不得替换清单里的模块。

**必须先写 catalog，再写任何视图。** 列、字段、状态、动作只从当前稿和 POC 补充材料抽取，禁止编造、禁止用「名称 / 编号 / 负责人」凑一套万能表头。

```json
{
  "modules": [
    {
      "key": "customers",
      "title": "客户档案",
      "core": true,
      "columns": ["编号", "客户", "负责人", "等级", "状态"],
      "fields": ["id", "name", "owner", "level", "status", "phone", "city"],
      "labels": {
        "id": "编号",
        "name": "客户",
        "owner": "负责人",
        "level": "等级",
        "status": "状态",
        "phone": "电话",
        "city": "城市"
      },
      "types": { "level": "select", "status": "select", "phone": "text" },
      "pageSize": 10,
      "states": ["公海", "跟进中", "成交"],
      "actions": ["create", "edit", "detail", "flow", "del"]
    }
  ]
}
```

`fields` 只允许英文数据键（给 JS/mock 用）。**界面上出现的所有名词必须是中文**：表头用 `columns`，详情和表单用 `labels`。禁止把 `id` / `period` / `status` / `module` 直接画到页面上。

对齐铁律（`verify_poc` 会查）：

- `catalog.modules[].key` = `js/views/<key>.js` 文件名 = `window.POC_MENUS[].key` = `window.Views[key]` = `data/mock.json` 的数组 key = `index.html` 的 `script src`
- 有 `data/required-modules.json` 时，STANDARD/THOROUGH 的 catalog key 必须覆盖其中全部模块
- 未写入 catalog 的模块禁止出现在侧栏；catalog 有的模块必须可点，禁止空菜单
- `columns` 必须出现在对应视图源码（表头）里；不得整表只有「名称、编号、负责人」
- `fields` 必须出现在该模块 mock 的每一行对象上
- 每个 field 必须有中文 `labels[field]`，且 label 含汉字，不能等于英文字段名
- 核心域 mock ≥ 4 行，次要域 ≥ 3 行；行内取值要像这份材料，不要 `客户A` / `测试1`
- `js/ui.js` 必须带列表分页（共 N 条、上一页/下一页），详情/表单必须走 `label()`，禁止 `esc(f)+'：'`

核心域数量与 catalog 覆盖范围以完整度 Prompt 为准。核心域做深，次要域同样可点，不要因为完整度就把字段改成英文、拿掉分页或把详情做成键值英文墙。

- 每个 `core=true` 模块做深：筛选、分页列表、新增/编辑弹窗、中文详情、删除确认、有状态则流转、空态；字段类型按材料（日期/金额/枚举）
- 其余 catalog 模块同样：分页列表 + 筛选 + 中文表单/详情 + 删除确认，字段仍必须来自 catalog

## 技术铁律

- 原生 JS，经典 `<script src="...">`，禁止 `type="module"`。
- 路由只用 hash：`#/<key>`。不要做 `customers.html` 多页面跳转。
- 数据只放 `data/mock.json` 与 `js/store.js` 内存 CRUD。`window.__POC_MOCK__` 可作为预览注入，store 以它为优先、catalog 模块 key 为内存表名。
- `index.html` 只放壳：顶栏、侧栏、Tab、`#view`、按 catalog 顺序引入 css/js。
- **先切换视图再改标题**：hashchange / 菜单点击后先 `Views[key].render(#view)`，不要先 `innerHTML` 改父节点标题再取已经被销毁的子节点 id。
- 标题、副标题用 `textContent`；禁止对仍包含子节点的容器使用 `innerHTML` 拼接后再 `getElementById` 那个子节点。
- 共享台账必须读取当前模块 key（`data-route` / catalog `key`），不要把所有菜单写死成同一个列表。

## 单轮写入预算（防 Token 截断，不砍覆盖）

示例财务这类多一级菜单 POC 失败，几乎都是**某一次 `write_poc_file` 把模型 `max_tokens` 打满**，不是沙箱 100KB，也不是 PRD 分片提交能解决的。

- 每次 `write_poc_file` **只写 1 个完整文件**。js/html/css 的 `content` ≤ 20000 字符，JSON ≤ 28000 字符。
- **先写 `js/ui.js` 的 `PocUI.ledger` 工厂**；每个 `js/views/<key>.js` 只注册该模块的 catalog 配置再调用工厂。禁止把筛选/表格/弹窗/删除的整套 CRUD 在每个视图里复制一份。
- `data/mock.json` 只保留 `catalog.fields` 对应的短字段，取值用材料里的中文业务词；核心域尽量 ≥10 行以便看出分页，不要写长说明、不要嵌套大对象。
- 工具返回 `truncated: true` 时，**禁止原样重试同一段 content**；改写更小的完整文件。不要从 catalog 删模块来「优化密度」。

## 必须原样写入的外壳 CSS（可补业务色，不得改掉尺寸骨架）

把下面写入 `css/app.css`：

```css
:root{--primary:#0E67DA;--primary-hover:#287AE6;--primary-soft:#EBF3FE;--sider:#F4F7FF;--th:#F2F3F5;--border:#EAEAEA;--text:#333;--muted:#666;--bg:#F7F8FA}
*{box-sizing:border-box}html,body{margin:0;height:100%;font:13px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;color:var(--text);background:var(--bg)}
.app{display:flex;flex-direction:column;height:100%}
.top{height:68px;background:#fff;box-shadow:0 2px 8px rgba(80,80,80,.1);display:flex;align-items:center;padding:0 20px;gap:16px;z-index:2}
.brand{font-weight:700;color:var(--primary);font-size:16px}
.body{flex:1;display:flex;min-height:0}
.sider{width:240px;background:var(--sider);border-right:1px solid var(--border);overflow:auto}
.menu{display:block;width:100%;text-align:left;border:0;background:transparent;padding:10px 20px;cursor:pointer;color:var(--text)}
.menu.on{background:#fff;color:var(--primary);font-weight:600;box-shadow:inset 3px 0 0 var(--primary)}
.main{flex:1;display:flex;flex-direction:column;min-width:0}
.tabs{height:40px;background:#F2F4F7;display:flex;align-items:center;padding:0 8px;gap:6px}
.tab{height:32px;padding:0 12px;border:0;border-radius:4px 4px 0 0;background:transparent;cursor:pointer}
.tab.on{background:#fff}
.view{flex:1;overflow:auto;padding:16px}
.card{background:#fff;border:1px solid var(--border);border-radius:4px}
.toolbar{display:flex;gap:8px;align-items:center;padding:12px 16px;flex-wrap:wrap}
.filters{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
input,select,textarea{height:32px;border:1px solid #DCDCDC;border-radius:3px;padding:0 8px}
table{width:100%;border-collapse:collapse}th{background:var(--th);font-weight:600;height:40px;padding:4px 16px;text-align:left}td{height:40px;padding:4px 16px;border-bottom:1px solid var(--border)}
.btn{height:32px;border:0;border-radius:3px;padding:0 12px;cursor:pointer}
.btn-primary{background:var(--primary);color:#fff}.btn-primary:hover{background:var(--primary-hover)}
.btn-ghost{background:#fff;border:1px solid #DCDCDC}
.mask{position:fixed;inset:0;background:rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;z-index:20}
.dlg{background:#fff;border-radius:4px;min-width:480px;max-width:720px;max-height:80vh;overflow:auto}
.dlg h3{margin:0;padding:12px 16px;border-bottom:1px solid var(--border)}
.dlg .body{padding:16px;display:grid;grid-template-columns:1fr 1fr;gap:12px}
.toast{position:fixed;right:16px;top:80px;background:#333;color:#fff;padding:8px 12px;border-radius:4px}
.empty{padding:40px;text-align:center;color:var(--muted)}
.pager{display:flex;align-items:center;justify-content:flex-end;gap:8px;padding:8px 16px;border-top:1px solid var(--border);color:var(--muted);flex-wrap:wrap}
.pager .pager-info{margin-right:auto}
.dlg label,.dlg .kv{display:flex;flex-direction:column;gap:4px;font-size:12px;color:var(--muted)}
.dlg .kv b{font-weight:400}
.dlg .kv span,.dlg label input,.dlg label select,.dlg label textarea{color:var(--text);font-size:13px}
textarea{min-height:72px;padding:6px 8px;font:inherit}
```

## index.html 骨架

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>POC</title>
  <link rel="stylesheet" href="css/app.css">
</head>
<body>
  <div class="app">
    <header class="top"><span class="brand">系统名</span><span id="topUser">演示账号</span></header>
    <div class="body">
      <aside class="sider" id="sider"></aside>
      <section class="main">
        <div class="tabs" id="tabs"></div>
        <div class="view" id="view"></div>
      </section>
    </div>
  </div>
  <script src="js/store.js"></script>
  <script src="js/router.js"></script>
  <script src="js/ui.js"></script>
  <script src="js/views/coreKey.js"></script>
  <script src="js/app.js"></script>
</body>
</html>
```

侧栏用 `button.menu[data-route='key']`，点击用 `e.target.closest('[data-route]')`。`js/app.js` 必须同时做三件事：

1. 切菜单前 `document.getElementById('view').innerHTML = ''`，再 `Views[key].render(view)`
2. **click 里直接调用 render**（`renderPoc()` / `renderRoute(key)`），hash 只作地址同步；禁止只写 `location.hash` 指望 `hashchange`（沙箱 iframe 可能丢该事件）
3. 先 render 当前视图，再用 `textContent` 更新顶栏/Tab

每个 `Views[key].render(el)` 必须对**传入的 el** 写 `el.innerHTML = ...`。禁止 `if (!rootEl) { rootEl = document.createElement('div'); el.appendChild(rootEl); }` 惰性挂载——否则第二个菜单会叠在第一个下面，后面的菜单看起来像点了没反应。

catalog 有几个模块，这里就引入几个 `js/views/<key>.js`，写在 `app.js` 之前。

## store / router 约定

`js/router.js` 的 `window.POC_MENUS` 必须从 catalog 原样抄 key / title / core，不得另发明一组菜单：

```js
window.POC_MENUS = [
  { key: 'coreKey', title: '核心模块', core: true },
  { key: 'secKey', title: '次要模块', core: false }
];
window.Views = window.Views || {};
```

每个 `js/views/<key>.js` 注册 `window.Views[key] = { render(el){...} }`。菜单 key、文件名、Views key 三者必须相同。列名必须出现在该视图源码里（写在下面的 spec.columns 即可）。

`data/mock.json` 按 catalog 模块给行数据；`js/store.js` 按 seed 的全部 key 建内存表，不要把模块名写死：

```js
window.PocStore = (function () {
  var seed = window.__POC_MOCK__ || {};
  var data = {};
  Object.keys(seed).forEach(function (key) {
    data[key] = (seed[key] || []).map(function (row) { return Object.assign({}, row); });
  });
  var seq = 100;
  function list(key) { return (data[key] || []).slice(); }
  function get(key, id) { return (data[key] || []).find(function (row) { return row.id === id; }) || null; }
  function create(key, row) {
    seq += 1;
    if (!data[key]) data[key] = [];
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
  function remove(key, id) { data[key] = (data[key] || []).filter(function (row) { return row.id !== id; }); }
  return { list: list, get: get, create: create, update: update, remove: remove };
})();
```

## 共享台账工厂 js/ui.js（必须先写）

把下面写入 `js/ui.js`。核心域与次要域都走它；差异只在 spec（`core`、`actions`、`states`）。不要每个视图再复制一套 CRUD。

```js
window.PocUI = (function () {
  var FALLBACK = { id: '编号', key: '标识', status: '状态', state: '状态', module: '模块', period: '期间', result: '结果', blockers: '阻塞项', amount: '金额', owner: '负责人', name: '名称', title: '标题', date: '日期', type: '类型' };
  function toast(text) {
    var n = document.createElement('div');
    n.className = 'toast';
    n.textContent = text;
    document.body.appendChild(n);
    setTimeout(function () { n.remove(); }, 1400);
  }
  function esc(v) {
    return String(v == null ? '' : v)
      .split('&').join('&amp;')
      .split('<').join('&lt;')
      .split('>').join('&gt;')
      .split('"').join('&quot;');
  }
  function isZh(s) { return /[\u4e00-\u9fff]/.test(String(s || '')); }
  function nextState(states, current) {
    if (!states || !states.length) return current;
    var i = states.indexOf(current);
    return states[(i + 1) % states.length];
  }
  function ledger(el, spec) {
    var key = spec.key, keyword = '', status = '', page = 1, pageSize = Number(spec.pageSize) || 10;
    var editing = null, detail = null, confirmId = null;
    var columns = spec.columns || [], fields = spec.fields || [], states = spec.states || [], actions = spec.actions || [];
    var labels = spec.labels || {}, types = spec.types || {};
    function has(act) { return actions.indexOf(act) >= 0; }
    function label(f) {
      if (isZh(labels[f])) return labels[f];
      var idx = fields.indexOf(f);
      if (idx >= 0 && isZh(columns[idx])) return columns[idx];
      if (isZh(f)) return f;
      return FALLBACK[f] || '字段';
    }
    function fieldType(f) {
      var t = String(types[f] || '').toLowerCase();
      if (t) return t;
      if ((f === 'status' || f === 'state') && states.length) return 'select';
      if (/date|time|day/i.test(f)) return 'date';
      if (/amount|price|qty|stock|count|money|rate/i.test(f)) return 'number';
      if (/remark|note|result|desc|reason|comment/i.test(f)) return 'textarea';
      return 'text';
    }
    function control(f, value) {
      var t = fieldType(f);
      var v = value == null ? '' : value;
      if (t === 'select' && states.length) {
        var html = '<select id="f-' + esc(f) + '"><option value=""></option>';
        states.forEach(function (s) { html += '<option' + (String(v) === s ? ' selected' : '') + '>' + esc(s) + '</option>'; });
        return html + '</select>';
      }
      if (t === 'textarea') return '<textarea id="f-' + esc(f) + '">' + esc(v) + '</textarea>';
      var tp = (t === 'number' || t === 'date') ? t : 'text';
      return '<input id="f-' + esc(f) + '" type="' + tp + '" value="' + esc(v) + '">';
    }
    function rows() {
      return PocStore.list(key).filter(function (row) {
        var blob = fields.map(function (f) { return row[f]; }).join(' ');
        return (!keyword || blob.indexOf(keyword) >= 0) && (!status || String(row.status || row.state || '') === status);
      });
    }
    function pager(total, pages) {
      return '<div class="pager"><span class="pager-info">共 ' + total + ' 条</span>'
        + '<button class="btn btn-ghost" data-act="page" data-page="prev"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>'
        + '<span>第 ' + page + ' / ' + pages + ' 页</span>'
        + '<button class="btn btn-ghost" data-act="page" data-page="next"' + (page >= pages ? ' disabled' : '') + '>下一页</button>'
        + '<select id="poc-ps"><option' + (pageSize === 10 ? ' selected' : '') + '>10</option><option' + (pageSize === 20 ? ' selected' : '') + '>20</option><option' + (pageSize === 50 ? ' selected' : '') + '>50</option></select></div>';
    }
    function paint() {
      var all = rows();
      var total = all.length;
      var pages = Math.max(1, Math.ceil((total || 1) / pageSize));
      if (page > pages) page = pages;
      if (page < 1) page = 1;
      var list = all.slice((page - 1) * pageSize, page * pageSize);
      var html = '<div class="card"><div class="toolbar"><div class="filters">';
      html += '<input id="poc-kw" placeholder="筛选" value="' + esc(keyword) + '">';
      if (states.length) {
        html += '<select id="poc-st"><option value="">全部状态</option>';
        states.forEach(function (s) { html += '<option' + (status === s ? ' selected' : '') + '>' + esc(s) + '</option>'; });
        html += '</select>';
      }
      html += '</div>';
      if (has('create')) html += '<button class="btn btn-primary" data-act="create">新增</button>';
      html += '</div>';
      if (!list.length) html += '<div class="empty">暂无数据，可清空筛选或新增</div>';
      else {
        html += '<table><thead><tr>';
        columns.forEach(function (c) { html += '<th>' + esc(c) + '</th>'; });
        html += '<th>操作</th></tr></thead><tbody>';
        list.forEach(function (row) {
          html += '<tr>';
          fields.slice(0, columns.length).forEach(function (f) { html += '<td>' + esc(row[f]) + '</td>'; });
          html += '<td>';
          if (has('detail')) html += '<button class="btn btn-ghost" data-act="detail" data-id="' + esc(row.id) + '">详情</button> ';
          if (has('edit')) html += '<button class="btn btn-ghost" data-act="edit" data-id="' + esc(row.id) + '">编辑</button> ';
          if (has('flow')) html += '<button class="btn btn-ghost" data-act="flow" data-id="' + esc(row.id) + '">流转</button> ';
          if (has('del')) html += '<button class="btn btn-ghost" data-act="del" data-id="' + esc(row.id) + '">删除</button>';
          html += '</td></tr>';
        });
        html += '</tbody></table>';
      }
      html += pager(total, pages) + '</div>';
      if (editing) {
        html += '<div class="mask"><div class="dlg"><h3>' + (editing.id ? '编辑' : '新增') + spec.title + '</h3><div class="body">';
        fields.forEach(function (f) {
          if (f === 'id') return;
          html += '<label>' + esc(label(f)) + control(f, editing[f]) + '</label>';
        });
        html += '</div><div class="toolbar"><button class="btn btn-primary" data-act="save">保存</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      if (detail) {
        html += '<div class="mask"><div class="dlg"><h3>' + esc(spec.title || '') + '详情</h3><div class="body">';
        fields.forEach(function (f) {
          html += '<div class="kv"><b>' + esc(label(f)) + '</b><span>' + esc(detail[f] == null || detail[f] === '' ? '-' : detail[f]) + '</span></div>';
        });
        html += '</div><div class="toolbar"><button class="btn btn-ghost" data-act="cancel">关闭</button></div></div></div>';
      }
      if (confirmId) {
        html += '<div class="mask"><div class="dlg"><h3>确认删除</h3><div class="body"><p>确认删除 ' + esc(confirmId) + '？</p></div><div class="toolbar"><button class="btn btn-primary" data-act="confirm-del">确认</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      el.innerHTML = html;
      var kw = el.querySelector('#poc-kw');
      var st = el.querySelector('#poc-st');
      var ps = el.querySelector('#poc-ps');
      if (kw) kw.oninput = function () { keyword = kw.value.trim(); page = 1; paint(); };
      if (st) st.onchange = function () { status = st.value; page = 1; paint(); };
      if (ps) ps.onchange = function () { pageSize = Number(ps.value) || 10; page = 1; paint(); };
      el.onclick = function (ev) {
        var btn = ev.target.closest('[data-act]');
        if (!btn) return;
        var act = btn.getAttribute('data-act');
        var id = btn.getAttribute('data-id');
        if (act === 'page') {
          if (btn.getAttribute('data-page') === 'prev') page -= 1;
          else page += 1;
          paint();
          return;
        }
        if (act === 'create') { var blank = {}; fields.forEach(function (f) { blank[f] = ''; }); editing = blank; }
        if (act === 'edit') editing = Object.assign({}, PocStore.get(key, id));
        if (act === 'detail') detail = PocStore.get(key, id);
        if (act === 'del') confirmId = id;
        if (act === 'cancel') { editing = null; detail = null; confirmId = null; }
        if (act === 'flow') {
          var row = PocStore.get(key, id);
          if (row && states.length) {
            var patch = {};
            if (Object.prototype.hasOwnProperty.call(row, 'status')) patch.status = nextState(states, row.status);
            else patch.state = nextState(states, row.state);
            PocStore.update(key, id, patch);
            toast('已流转');
          }
        }
        if (act === 'save') {
          var data = {};
          fields.forEach(function (f) {
            if (f === 'id') return;
            var node = el.querySelector('#f-' + f);
            var raw = node ? String(node.value).trim() : '';
            data[f] = fieldType(f) === 'number' ? (Number(raw) || 0) : raw;
          });
          if (editing.id) PocStore.update(key, editing.id, data);
          else PocStore.create(key, data);
          editing = null;
        }
        if (act === 'confirm-del') { PocStore.remove(key, confirmId); confirmId = null; toast('已删除'); }
        paint();
      };
    }
    paint();
  }
  return { ledger: ledger, toast: toast };
})();
```

每个视图只写配置，**列名必须出现在该文件的 spec.columns 中，并带上中文 labels**：

```js
window.Views = window.Views || {};
window.Views.voucher = {
  render: function (el) {
    PocUI.ledger(el, {
      key: 'voucher',
      title: '记账凭证',
      core: true,
      columns: ['凭证号', '日期', '科目', '金额', '状态'],
      fields: ['id', 'date', 'subject', 'amount', 'status'],
      labels: { id: '凭证号', date: '日期', subject: '科目', amount: '金额', status: '状态' },
      types: { date: 'date', amount: 'number', status: 'select' },
      pageSize: 10,
      states: ['草稿', '已审', '过账'],
      actions: ['create', 'edit', 'detail', 'flow', 'del']
    });
  }
};
```

## 写法顺序

1. 若存在则 `read_poc_file("data/required-modules.json")`，按其中 key 写 `data/catalog.json`（不要覆盖 required-modules）
2. `css/app.css` + `index.html` 壳（script src 按 catalog 全量列出，并引入 `js/ui.js`）
3. `data/mock.json` + `js/store.js` + `js/router.js` + `js/ui.js`
4. 每个核心/次要视图单独一次 `write_poc_file`，只写 catalog 配置调用 `PocUI.ledger`
5. `js/app.js` 接线：先 render 当前 Views，再改标题
6. 全部文件写入完毕后，再调用 `verify_poc` 执行整体自检与诊断；诊断通过后立刻 `submit_result`，最多定向修复两次。严禁在文件尚未全部写完前提前调用 `verify_poc`，以免消耗修复额度。

## 讲解词

`scriptMarkdown` 按侧栏真实菜单顺序写售前话术：进系统 → 核心主路径 CRUD/流转 → 切一个次要台账。详尽完整度时必须写 5～8 步，每步标题必须能在 `catalog.modules[].title` 中找到。快速/标准缺少该字段不影响提交。
