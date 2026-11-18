#!/usr/bin/env node
// PoC 端到端验证：真实加载插件 → 考试页点击悬浮按钮截图 → 校验切屏计数与阳性对照。
// 用法: node poc/e2e-test.mjs '<setup_test_data.py 输出的 JSON>'
import fs from 'node:fs';
import crypto from 'node:crypto';

const setup = JSON.parse(process.argv[2]);
const EXT_PATH = new URL('./anti-cheat-test-extension', import.meta.url).pathname;
const extId = [...crypto.createHash('sha256').update(EXT_PATH).digest('hex').slice(0, 32)]
  .map(c => String.fromCharCode(97 + parseInt(c, 16))).join('');
const CDP_HTTP = 'http://127.0.0.1:9222';
const sleep = ms => new Promise(r => setTimeout(r, ms));

let msgId = 0;
class CDP {
  constructor(ws) {
    this.ws = ws; this.pending = new Map();
    ws.addEventListener('message', e => {
      const m = JSON.parse(e.data);
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id); this.pending.delete(m.id);
        m.error ? rej(new Error(m.error.message)) : res(m.result);
      }
    });
  }
  static async connect(url) {
    const ws = new WebSocket(url);
    await new Promise((r, j) => { ws.addEventListener('open', r, { once: true }); ws.addEventListener('error', j, { once: true }); });
    return new CDP(ws);
  }
  send(method, params = {}) {
    return new Promise((res, rej) => { const id = ++msgId; this.pending.set(id, { res, rej }); this.ws.send(JSON.stringify({ id, method, params })); });
  }
  async eval(expression, awaitPromise = false) {
    const r = await this.send('Runtime.evaluate', { expression, awaitPromise, returnByValue: true });
    if (r.exceptionDetails) throw new Error('evaluate 异常: ' + JSON.stringify(r.exceptionDetails).slice(0, 300));
    return r.result.value;
  }
  close() { this.ws.close(); }
}
async function waitFor(fn, timeout = 20000, step = 300) {
  const t0 = Date.now();
  for (;;) { const v = await fn(); if (v) return v; if (Date.now() - t0 > timeout) throw new Error('waitFor 超时'); await sleep(step); }
}
const results = [];
const record = (name, ok, detail = '') => { results.push({ name, ok }); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' — ' + detail : '')); };

// 关掉所有旧标签页（含上一轮遗留的已布防考试页，切走它会真实触发 visibilitychange 污染计数）
for (const t of (await (await fetch(CDP_HTTP + '/json/list')).json()).filter(t => t.type === 'page')) {
  await fetch(CDP_HTTP + '/json/close/' + t.id).catch(() => {});
}

// MV3 SW 闲置即休眠且不出现在 target 列表；选项页是常驻扩展上下文，用它做配置与截图校验。
// 真实按钮点击仍走 content → SW → captureVisibleTab 完整链路（sendMessage 会唤醒 SW）。
const optTarget = await (await fetch(CDP_HTTP + '/json/new?' + encodeURIComponent(`chrome-extension://${extId}/options.html`), { method: 'PUT' })).json();
const opt = await CDP.connect(optTarget.webSocketDebuggerUrl);
await opt.send('Page.enable');
await opt.send('Page.reload');
await waitFor(async () => { try { return await opt.eval('typeof chrome !== "undefined" && !!chrome.runtime?.getManifest'); } catch { return false; } }, 15000);
const manifestName = await opt.eval('chrome.runtime.getManifest().name');
if (!String(manifestName).includes('exam-ai')) throw new Error('PoC 插件未加载: ' + manifestName);
console.log('PoC 插件已加载, extension id =', extId);
const examOrigin = new URL(setup.url).origin;
await opt.eval(`chrome.storage.sync.set({baseUrl:'https://dashscope.aliyuncs.com/compatible-mode/v1',apiKey:'sk-poc-dummy-key',model:'qwen-vl-plus',examOrigins:'${examOrigin}'})`, true);
const syncRes = await opt.eval(`chrome.runtime.sendMessage({type:'POC_SYNC'})`, true);
record('按配置地址动态注册内容脚本', syncRes?.ok === true, JSON.stringify(syncRes));
const matches = await opt.eval(`chrome.scripting.getRegisteredContentScripts({ids:['poc-exam-content']}).then(s => s[0]?.matches)`, true);
record('注册 matches 与配置地址一致', JSON.stringify(matches) === JSON.stringify([examOrigin + '/*']), JSON.stringify(matches));

// 考试页标签最后创建，保证它是活动标签且此后不再切换
const page = await (await fetch(CDP_HTTP + '/json/new?' + encodeURIComponent('about:blank'), { method: 'PUT' })).json();
const p = await CDP.connect(page.webSocketDebuggerUrl);
await p.send('Page.enable'); await p.send('Runtime.enable'); await p.send('DOM.enable');
await fetch(CDP_HTTP + '/json/activate/' + page.id);

await p.send('Page.navigate', { url: new URL('/candidate-login', setup.url).href });
await waitFor(() => p.eval('document.readyState === "complete"'));
await waitFor(() => p.eval('!!document.getElementById("exam-ai-poc-host")'));
record('非考试页（登录页）也注入悬浮按钮', true);
const login = await p.eval(`(async () => {
  const r = await fetch('/api/auth/candidate-login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(${JSON.stringify({ idCard: setup.idCard, name: setup.name })}) });
  const j = await r.json();
  if (j.code !== 0) return 'LOGIN_FAIL ' + JSON.stringify(j);
  localStorage.setItem('token', j.data.token);
  localStorage.setItem('loginType', 'candidate');
  localStorage.setItem('userName', j.data.candidate.name);
  return 'OK';
})()`, true);
record('考生登录', login === 'OK', login);

await p.send('Page.navigate', { url: setup.url });
await waitFor(() => p.eval('!!document.querySelector(".question-panel")'));
await waitFor(() => p.eval('!!document.getElementById("exam-ai-poc-host")'));
record('考试页加载 + PoC 悬浮按钮注入', true);

await p.eval(`window.__pocEvents = [];
window.addEventListener('blur', () => __pocEvents.push(['blur', Date.now()]));
window.addEventListener('focus', () => __pocEvents.push(['focus', Date.now()]));
document.addEventListener('visibilitychange', () => __pocEvents.push(['vis:' + document.visibilityState, Date.now()]));
'ok'`);

const enterExpr = `(async () => {
  const r = await fetch('/api/exam/enter', { method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + localStorage.getItem('token') },
    body: JSON.stringify({ projectId: ${setup.projectId} }) });
  const j = await r.json();
  return j.code === 0 ? j.data.switchCount : -1;
})()`;
const before = await p.eval(enterExpr, true);
record('基线 switchCount = 0', before === 0, 'switchCount=' + before);

const tClick = Date.now();
const rect = await p.eval('(() => { const r = document.getElementById("exam-ai-poc-host").getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()');
await p.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: rect.x, y: rect.y, button: 'left', clickCount: 1 });
await p.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: rect.x, y: rect.y, button: 'left', clickCount: 1 });

async function panelHTML() {
  const { root } = await p.send('DOM.getDocument', { depth: -1, pierce: true });
  let preId = null;
  (function walk(n) {
    if (preId) return;
    if (n.nodeName === 'PRE') { preId = n.nodeId; return; }
    (n.children || []).forEach(walk); (n.shadowRoots || []).forEach(walk);
  })(root);
  if (!preId) return null;
  return (await p.send('DOM.getOuterHTML', { nodeId: preId })).outerHTML;
}
const html = await waitFor(async () => { const h = await panelHTML(); return h && !h.includes('正在截图') ? h : null; }, 30000);
const text = html.replace(/<[^>]+>/g, '');
record('真实截图 + 模型请求链路（dummy key 预期 HTTP 401）', /401/.test(text), text.slice(0, 160));

const capLen = await opt.eval(`(async () => (await chrome.tabs.captureVisibleTab(null, { format: 'png' })).length)()`, true);
record('captureVisibleTab 截到真实页面 PNG', capLen > 10000, 'dataUrl 长度=' + capLen);

const after = await p.eval(enterExpr, true);
const warn = await p.eval('!!document.querySelector(".cheat-warn")');
console.log('焦点事件流:', await p.eval('JSON.stringify(window.__pocEvents)'), '| 点击时刻:', tClick);
record('PoC 点击 + 截图后 switchCount 仍为 0', after === 0, 'switchCount=' + after);
record('考试页无切屏警告条', !warn);
fs.writeFileSync('/tmp/exam-poc-clean.png', Buffer.from((await p.send('Page.captureScreenshot', { format: 'png' })).data, 'base64'));

await p.eval('window.dispatchEvent(new Event("blur"))');
await sleep(600);
const pc = await p.eval(enterExpr, true);
const pcWarn = await p.eval('!!document.querySelector(".cheat-warn")');
record('阳性对照：失焦事件被记录 switchCount = 1 且出现警告', pc === 1 && pcWarn, 'switchCount=' + pc + ', 警告条=' + pcWarn);
fs.writeFileSync('/tmp/exam-poc-positive.png', Buffer.from((await p.send('Page.captureScreenshot', { format: 'png' })).data, 'base64'));

console.log(`\n==== ${results.filter(r => r.ok).length}/${results.length} 通过 ====`);
opt.close(); p.close();
process.exit(results.every(r => r.ok) ? 0 : 1);