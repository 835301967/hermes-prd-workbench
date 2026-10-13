#!/usr/bin/env node
// Static structural checks only. Never eval or execute generated JavaScript.
const fs = require('fs');
async function verifyHtml(html) {
  const errors = [];
  const warnings = [];

  if (!html || typeof html !== 'string' || !html.trim()) {
    return { valid: false, errors: ['HTML 内容为空'], warnings: [] };
  }

  // 1. Basic document structure
  const lower = html.toLowerCase();
  if (!lower.includes('<!doctype html>')) {
    errors.push('缺少 <!DOCTYPE html> 声明');
  }
  if (!lower.includes('<html') || !lower.includes('</html>')) {
    errors.push('HTML 根标签 <html> 或 </html> 未闭合');
  }
  if (!lower.includes('<body') || !lower.includes('</body>')) {
    errors.push('缺少 <body> 或 </body> 标签');
  }

  // These checks intentionally do not depend on happy-dom. The production
  // Hermes profile only ships Node.js, so accepting an inert page must not
  // depend on an optional DOM package being installed.
  const inlineScripts = extractExecutableScripts(html);
  const scriptText = inlineScripts.join('\n');
  const staticHtml = html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
  const inlineClickHandlers = collectInlineClickHandlers(staticHtml);
  const clickWired = /addEventListener\s*\(\s*['"]click['"]/i.test(scriptText)
    || /\.onclick\s*=/i.test(scriptText)
    || /\bonclick\s*=/i.test(html);
  const elementDefinitions = collectElementDefinitions(staticHtml);
  const actionBindings = collectAttributeBindings(elementDefinitions, ['data-act', 'data-action', 'data-a']);
  const navigationBindings = collectAttributeBindings(elementDefinitions, ['data-view', 'data-v', 'data-tab']);
  const actionValues = actionBindings.map(binding => binding.value);
  const navigationValues = navigationBindings.map(binding => binding.value);

  if (/__(?:SEED|TODO|PLACEHOLDER|FILL)[A-Z0-9_]*__/i.test(html)) {
    errors.push('页面仍包含生成占位符（如 __SEED_*__ / __TODO_*__），说明 Demo 只完成了骨架而未完成可运行脚本。');
  }
  if ((actionValues.length > 0 || navigationValues.length > 0) && !clickWired) {
    errors.push(`交互未绑定：页面声明了 ${actionValues.length} 个 data-act/data-action 动作和 ${navigationValues.length} 个导航目标，但没有 click 事件监听器或 onclick 处理器，所有菜单与按钮都会失效。`);
  }
  if (actionValues.length > 0 && clickWired) {
    const inlineActions = new Set(inlineClickHandlers.map(handler => handler.action).filter(Boolean));
    const missingActions = uniqueBindings(actionBindings).filter(binding =>
      !inlineActions.has(binding.value)
      && !scriptHandlesBinding(scriptText, binding)
    ).map(binding => binding.value);
    if (missingActions.length > 0) {
      errors.push(`动作未实现：${missingActions.slice(0, 8).join('、')}${missingActions.length > 8 ? ' 等' : ''} 仅出现在 data-act/data-action 属性中，脚本没有对应处理分支。`);
    }
  }

  const undefinedHandlers = findUndefinedInlineHandlers(html, scriptText);
  if (undefinedHandlers.length > 0) {
    errors.push(`行内事件处理器未定义：${undefinedHandlers.slice(0, 8).join('、')}${undefinedHandlers.length > 8 ? ' 等' : ''}。用户触发输入或点击时会抛出 ReferenceError。`);
  }

  return { valid: errors.length === 0, errors, warnings, diagnostic: true, behaviorProof: false, checks: ['structure', 'placeholders', 'declared-bindings'] };
}

function collectElementDefinitions(html) {
  const definitions = [];
  const pattern = /<([A-Za-z][\w:-]*)([^>]*)>/g;
  let match;
  while ((match = pattern.exec(html)) !== null) {
    const attrs = {};
    const attrPattern = /([A-Za-z_:][\w:.-]*)\s*=\s*(["'])(.*?)\2/g;
    let attr;
    while ((attr = attrPattern.exec(match[2])) !== null) attrs[attr[1].toLowerCase()] = attr[3];
    definitions.push({ tag: match[1].toLowerCase(), attrs });
  }
  return definitions;
}

function collectAttributeBindings(definitions, names) {
  const bindings = [];
  for (const definition of definitions) {
    for (const name of names) {
      const value = String(definition.attrs[name] || '').trim();
      if (value) {
        bindings.push({
          name: name.toLowerCase(), value, tag: definition.tag,
          id: definition.attrs.id || '', classes: String(definition.attrs.class || '').split(/\s+/).filter(Boolean),
          attrs: definition.attrs
        });
      }
    }
  }
  return bindings;
}

function unique(values) {
  return Array.from(new Set(values));
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function scriptMentionsValue(script, value) {
  const escaped = escapeRegExp(value);
  return new RegExp(`['"]${escaped}['"]`).test(script)
    || new RegExp(`\\b${escaped}\\s*:`).test(script);
}

function extractExecutableScripts(html) {
  const scripts = [];
  const pattern = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  let match;
  while ((match = pattern.exec(html)) !== null) {
    const attrs = match[1];
    if (/\bsrc\s*=/i.test(attrs)) continue;
    const typeMatch = attrs.match(/\btype\s*=\s*(["']?)([^"'\s>]+)\1/i);
    const type = typeMatch ? typeMatch[2].toLowerCase() : '';
    if (type && !['text/javascript', 'application/javascript', 'text/ecmascript', 'application/ecmascript', 'module'].includes(type)) continue;
    scripts.push(match[2]);
  }
  return scripts;
}

function findUndefinedInlineHandlers(html, script) {
  const builtIns = new Set(['alert', 'confirm', 'open', 'print', 'scrollTo', 'setTimeout', 'clearTimeout']);
  const names = [];
  const handlerPattern = /\bon(?:click|input|change|submit|blur|focus|keydown|keyup)\s*=\s*(["'])\s*([A-Za-z_$][\w$]*)\s*\(/gi;
  let match;
  while ((match = handlerPattern.exec(html)) !== null) {
    const name = match[2];
    if (!builtIns.has(name)) names.push(name);
  }
  return unique(names).filter(name => {
    const escaped = escapeRegExp(name);
    return !new RegExp(`\\bfunction\\s+${escaped}\\s*\\(`).test(script)
      && !new RegExp(`\\b(?:var|let|const)\\s+${escaped}\\s*=\\s*(?:function\\b|(?:async\\s*)?\\([^)]*\\)\\s*=>|(?:async\\s+)?[A-Za-z_$][\\w$]*\\s*=>)`).test(script)
      && !new RegExp(`(?:\\bwindow\\s*\\.\\s*|\\bglobalThis\\s*\\.\\s*)${escaped}\\s*=\\s*(?:function\\b|(?:async\\s*)?\\([^)]*\\)\\s*=>|(?:async\\s+)?[A-Za-z_$][\\w$]*\\s*=>)`).test(script)
      && !new RegExp(`\\b${escaped}\\s*=\\s*(?:function\\b|\\([^)]*\\)\\s*=>|[A-Za-z_$][\\w$]*\\s*=>)`).test(script);
  });
}

function collectInlineClickHandlers(html) {
  const handlers = [];
  const tagPattern = /<[A-Za-z][^>]*>/g;
  let tagMatch;
  while ((tagMatch = tagPattern.exec(html)) !== null) {
    const tag = tagMatch[0];
    const click = tag.match(/\bonclick\s*=\s*(["'])([\s\S]*?)\1/i);
    if (!click) continue;
    const read = name => {
      const match = tag.match(new RegExp(`\\b${name}\\s*=\\s*(["'])(.*?)\\1`, 'i'));
      return match ? match[2].trim() : '';
    };
    handlers.push({
      code: click[2],
      action: read('data-act') || read('data-action') || read('data-a'),
      navigation: read('data-view') || read('data-v') || read('data-tab')
    });
  }
  return handlers;
}

function toDatasetKey(name) { return name.replace(/-([a-z])/g, (_, c) => c.toUpperCase()); }
function uniqueBindings(bindings) {
  const seen = new Set();
  return bindings.filter(binding => {
    const key = `${binding.name}\u0000${binding.value}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function scriptHandlesBinding(script, binding) {
  const value = escapeRegExp(binding.value);
  const attr = escapeRegExp(binding.name);
  const datasetKey = escapeRegExp(toDatasetKey(binding.name.slice(5)));
  const readsAttribute = new RegExp(`(?:dataset\\s*\\.\\s*${datasetKey}|getAttribute\\s*\\(\\s*['"]${attr}['"]\\s*\\))`).test(script);
  const branchesOnValue = new RegExp(`(?:={2,3}|!==?)\\s*['"]${value}['"]|['"]${value}['"]\\s*(?:={2,3}|!==?)|\\bcase\\s+['"]${value}['"]\\s*:|['"]?${value}['"]?\\s*:\\s*(?:function\\b|\\(|[A-Za-z_$])`).test(script);
  const boundSelector = new RegExp(`\\[${attr}\\s*=\\s*['"]?${value}['"]?\\]`).test(script);
  return boundSelector || (readsAttribute && branchesOnValue);
}


if (require.main === module) {
  try {
    verifyHtml(fs.readFileSync(process.argv[2], 'utf8')).then(result => {
      process.stdout.write(JSON.stringify(result));
      process.exitCode = result.valid ? 0 : 1;
    });
  } catch (_) {
    process.stdout.write(JSON.stringify({valid:false, errors:['Static diagnostic input unavailable']}));
    process.exitCode = 1;
  }
}
module.exports = { verifyHtml };
