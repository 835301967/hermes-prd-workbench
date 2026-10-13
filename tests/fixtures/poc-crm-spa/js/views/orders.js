window.Views = window.Views || {};
window.Views.orders = {
  render: function (el) {
    var editing = null;
    var confirmId = null;
    function paint() {
      var list = PocStore.list('orders');
      var html = '<div class="card"><div class="toolbar"><strong>销售订单</strong><button class="btn btn-primary" data-act="create">新增订单</button></div>';
      html += '<table><thead><tr><th>单号</th><th>客户</th><th>金额</th><th>状态</th><th>操作</th></tr></thead><tbody>';
      list.forEach(function (row) {
        html += '<tr><td>' + row.id + '</td><td>' + row.customer + '</td><td>' + row.amount + '</td><td>' + row.status + '</td><td>';
        html += '<button class="btn btn-ghost" data-act="edit" data-id="' + row.id + '">编辑</button> ';
        html += '<button class="btn btn-ghost" data-act="del" data-id="' + row.id + '">删除</button></td></tr>';
      });
      html += '</tbody></table></div>';
      if (editing) {
        html += '<div class="mask"><div class="dlg"><h3>' + (editing.id ? '编辑订单' : '新增订单') + '</h3><div class="body">';
        html += '<label>客户<input id="f-customer" value="' + (editing.customer || '') + '"></label>';
        html += '<label>金额<input id="f-amount" value="' + (editing.amount || 0) + '"></label>';
        html += '<label>状态<select id="f-status"><option' + (editing.status === '草稿' ? ' selected' : '') + '>草稿</option><option' + (editing.status === '待审' ? ' selected' : '') + '>待审</option><option' + (editing.status === '已发' ? ' selected' : '') + '>已发</option></select></label>';
        html += '</div><div class="toolbar"><button class="btn btn-primary" data-act="save">保存</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      if (confirmId) {
        html += '<div class="mask"><div class="dlg"><h3>确认删除</h3><div class="body"><p>确认删除订单 ' + confirmId + '？</p></div><div class="toolbar"><button class="btn btn-primary" data-act="confirm-del">确认</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      el.innerHTML = html;
      el.onclick = function (ev) {
        var btn = ev.target.closest('[data-act]');
        if (!btn) return;
        var act = btn.getAttribute('data-act');
        var id = btn.getAttribute('data-id');
        if (act === 'create') editing = { customer: '', amount: 0, status: '草稿', owner: '李销' };
        if (act === 'edit') editing = Object.assign({}, PocStore.get('orders', id));
        if (act === 'del') confirmId = id;
        if (act === 'cancel') { editing = null; confirmId = null; }
        if (act === 'save') {
          var patch = { customer: el.querySelector('#f-customer').value.trim() || '未命名客户', amount: Number(el.querySelector('#f-amount').value) || 0, status: el.querySelector('#f-status').value };
          if (editing.id) PocStore.update('orders', editing.id, patch);
          else PocStore.create('orders', Object.assign({ owner: '李销' }, patch));
          editing = null;
        }
        if (act === 'confirm-del') { PocStore.remove('orders', confirmId); confirmId = null; }
        paint();
      };
    }
    paint();
  }
};
