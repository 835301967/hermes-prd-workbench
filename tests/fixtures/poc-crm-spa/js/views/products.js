window.Views = window.Views || {};
window.Views.products = {
  render: function (el) {
    var editing = null;
    var confirmId = null;
    function paint() {
      var list = PocStore.list('products');
      var html = '<div class="card"><div class="toolbar"><strong>产品目录</strong><button class="btn btn-primary" data-act="create">新增产品</button></div>';
      html += '<table><thead><tr><th>编号</th><th>名称</th><th>规格</th><th>单价</th><th>库存</th><th>操作</th></tr></thead><tbody>';
      list.forEach(function (row) {
        html += '<tr><td>' + row.id + '</td><td>' + row.name + '</td><td>' + row.spec + '</td><td>' + row.price + '</td><td>' + row.stock + '</td><td>';
        html += '<button class="btn btn-ghost" data-act="edit" data-id="' + row.id + '">编辑</button> ';
        html += '<button class="btn btn-ghost" data-act="del" data-id="' + row.id + '">删除</button></td></tr>';
      });
      html += '</tbody></table></div>';
      if (editing) {
        html += '<div class="mask"><div class="dlg"><h3>' + (editing.id ? '编辑产品' : '新增产品') + '</h3><div class="body">';
        html += '<label>名称<input id="f-name" value="' + (editing.name || '') + '"></label>';
        html += '<label>规格<input id="f-spec" value="' + (editing.spec || '') + '"></label>';
        html += '<label>单价<input id="f-price" value="' + (editing.price || 0) + '"></label>';
        html += '<label>库存<input id="f-stock" value="' + (editing.stock || 0) + '"></label>';
        html += '</div><div class="toolbar"><button class="btn btn-primary" data-act="save">保存</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      if (confirmId) {
        html += '<div class="mask"><div class="dlg"><h3>确认删除</h3><div class="body"><p>确认删除产品 ' + confirmId + '？</p></div><div class="toolbar"><button class="btn btn-primary" data-act="confirm-del">确认</button><button class="btn btn-ghost" data-act="cancel">取消</button></div></div></div>';
      }
      el.innerHTML = html;
      el.onclick = function (ev) {
        var btn = ev.target.closest('[data-act]');
        if (!btn) return;
        var act = btn.getAttribute('data-act');
        var id = btn.getAttribute('data-id');
        if (act === 'create') editing = { name: '', spec: '', price: 0, stock: 0 };
        if (act === 'edit') editing = Object.assign({}, PocStore.get('products', id));
        if (act === 'del') confirmId = id;
        if (act === 'cancel') { editing = null; confirmId = null; }
        if (act === 'save') {
          var patch = { name: el.querySelector('#f-name').value.trim() || '未命名产品', spec: el.querySelector('#f-spec').value.trim() || '-', price: Number(el.querySelector('#f-price').value) || 0, stock: Number(el.querySelector('#f-stock').value) || 0 };
          if (editing.id) PocStore.update('products', editing.id, patch);
          else PocStore.create('products', patch);
          editing = null;
        }
        if (act === 'confirm-del') { PocStore.remove('products', confirmId); confirmId = null; }
        paint();
      };
    }
    paint();
  }
};
