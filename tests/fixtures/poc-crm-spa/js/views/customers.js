window.Views = window.Views || {};
window.Views.customers = {
  render: function (el) {
    var keyword = '';
    var status = '';
    var editing = null;
    var detail = null;
    var confirmId = null;
    function rows() {
      return PocStore.list('customers').filter(function (row) {
        var hit = !keyword || String(row.name + row.owner + row.city).indexOf(keyword) >= 0;
        return hit && (!status || row.status === status);
      });
    }
    function toast(text) {
      var n = document.createElement('div');
      n.className = 'toast';
      n.textContent = text;
      document.body.appendChild(n);
      setTimeout(function () { n.remove(); }, 1600);
    }
    function nextStatus(current) {
      if (current === '公海') return '跟进中';
      if (current === '跟进中') return '成交';
      return '公海';
    }
    function paint() {
      var list = rows();
      var html = '<div class="card"><div class="toolbar"><div class="filters">';
      html += '<input id="kw" placeholder="客户/负责人/城市" value="' + keyword + '">';
      html += '<select id="st"><option value="">全部状态</option><option' + (status === '跟进中' ? ' selected' : '') + '>跟进中</option><option' + (status === '成交' ? ' selected' : '') + '>成交</option><option' + (status === '公海' ? ' selected' : '') + '>公海</option></select>';
      html += '</div><button class="btn btn-primary" data-act="create">新增客户</button></div>';
      if (!list.length) {
        html += '<div class="empty">没有匹配客户，可清空筛选或新增</div>';
      } else {
        html += '<table><thead><tr><th>编号</th><th>客户</th><th>负责人</th><th>等级</th><th>状态</th><th>操作</th></tr></thead><tbody>';
        list.forEach(function (row) {
          html += '<tr><td>' + row.id + '</td><td>' + row.name + '</td><td>' + row.owner + '</td><td>' + row.level + '</td><td><span class="status">' + row.status + '</span></td><td>';
          html += '<button class="btn btn-ghost" data-act="detail" data-id="' + row.id + '">详情</button> ';
          html += '<button class="btn btn-ghost" data-act="edit" data-id="' + row.id + '">编辑</button> ';
          html += '<button class="btn btn-ghost" data-act="flow" data-id="' + row.id + '">流转</button> ';
          html += '<button class="btn btn-ghost" data-act="del" data-id="' + row.id + '">删除</button></td></tr>';
        });
        html += '</tbody></table>';
      }
      html += '</div>';
      if (editing) {
        html += '<div class="mask"><div class="dlg"><h3>' + (editing.id ? '编辑客户' : '新增客户') + '</h3><div class="body">';
        html += '<label>名称<input id="f-name" value="' + (editing.name || '') + '"></label>';
        html += '<label>负责人<input id="f-owner" value="' + (editing.owner || '') + '"></label>';
        html += '<label>城市<input id="f-city" value="' + (editing.city || '') + '"></label>';
        html += '<label>等级<select id="f-level"><option' + (editing.level === 'A' ? ' selected' : '') + '>A</option><option' + (editing.level === 'B' ? ' selected' : '') + '>B</option><option' + (editing.level === 'C' ? ' selected' : '') + '>C</option></select></label>';
        html += '</div><div class="toolbar"><button class="btn btn-primary" data-act="save">保存</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      if (detail) {
        html += '<div class="mask"><div class="dlg"><h3>客户详情</h3><div class="body">';
        html += '<div>编号：' + detail.id + '</div><div>名称：' + detail.name + '</div><div>负责人：' + detail.owner + '</div><div>状态：' + detail.status + '</div><div>电话：' + (detail.phone || '-') + '</div><div>城市：' + detail.city + '</div>';
        html += '</div><div class="toolbar"><button class="btn btn-ghost" data-act="cancel">关闭</button></div></div></div>';
      }
      if (confirmId) {
        html += '<div class="mask"><div class="dlg"><h3>确认删除</h3><div class="body"><p>删除后不可恢复，确认删除 ' + confirmId + '？</p></div><div class="toolbar"><button class="btn btn-primary" data-act="confirm-del">确认</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      el.innerHTML = html;
      var kw = el.querySelector('#kw');
      var st = el.querySelector('#st');
      if (kw) kw.oninput = function () { keyword = kw.value.trim(); paint(); };
      if (st) st.onchange = function () { status = st.value; paint(); };
      el.onclick = function (ev) {
        var btn = ev.target.closest('[data-act]');
        if (!btn) return;
        var act = btn.getAttribute('data-act');
        var id = btn.getAttribute('data-id');
        if (act === 'create') editing = { name: '', owner: '', city: '', level: 'B', status: '跟进中' };
        if (act === 'edit') editing = Object.assign({}, PocStore.get('customers', id));
        if (act === 'detail') detail = PocStore.get('customers', id);
        if (act === 'del') confirmId = id;
        if (act === 'cancel') { editing = null; detail = null; confirmId = null; }
        if (act === 'flow') {
          var row = PocStore.get('customers', id);
          if (row) {
            PocStore.update('customers', id, { status: nextStatus(row.status) });
            toast('已流转到 ' + PocStore.get('customers', id).status);
          }
        }
        if (act === 'save') {
          var patch = {
            name: el.querySelector('#f-name').value.trim() || '未命名客户',
            owner: el.querySelector('#f-owner').value.trim() || '未分配',
            city: el.querySelector('#f-city').value.trim() || '广州',
            level: el.querySelector('#f-level').value
          };
          if (editing.id) PocStore.update('customers', editing.id, patch);
          else PocStore.create('customers', Object.assign({ status: '跟进中', phone: '000-0000-0000' }, patch));
          editing = null;
          toast('已保存客户');
        }
        if (act === 'confirm-del') {
          PocStore.remove('customers', confirmId);
          confirmId = null;
          toast('已删除客户');
        }
        paint();
      };
    }
    paint();
  }
};
