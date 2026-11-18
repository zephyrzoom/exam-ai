#!/usr/bin/env node
// 知识库导入端到端：CDP 驱动选项页 webkitdirectory 导入真实 pdf/docx/txt，校验解析统计与检索命中。
// 用法: node poc/kb-import-test.mjs （需 Edge 已带 --load-extension 启动，样例在 /tmp/poc-kb-samples）
import crypto from 'node:crypto';

const CDP_HTTP = 'http://127.0.0.1:9222';
const EXT_PATH = new URL('./anti-cheat-test-extension', import.meta.url).pathname;
const extId = [...crypto.createHash('sha256').update(EXT_PATH).digest('hex').slice(0, 32)]
  .map(c => String.fromCharCode(97 + parseInt(c, 16))).join('');
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
}
async function waitFor(fn, timeout = 60000, step = 400) {
  const t0 = Date.now();
  for (;;) { const v = await fn(); if (v) return v; if (Date.now() - t0 > timeout) throw new Error('waitFor 超时'); await sleep(step); }
}
const results = [];
const record = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' — ' + detail : '')); };

const target = await (await fetch(CDP_HTTP + '/json/new?' + encodeURIComponent(`chrome-extension://${extId}/options.html`), { method: 'PUT' })).json();
const p = await CDP.connect(target.webSocketDebuggerUrl);
await p.send('Page.enable'); await p.send('DOM.enable');
await p.send('Page.reload');
await waitFor(() => p.eval('typeof chrome !== "undefined" && !!chrome.runtime?.getManifest && typeof KB !== "undefined" && typeof pdfjsLib !== "undefined" && typeof mammoth !== "undefined"').catch(() => false));
record('选项页加载解析库（pdfjs/mammoth/kb）', true);

// webkitdirectory 输入框不吃 CDP setFileInputFiles：页内构造 File 注入 input.files 并触发 change，
// 覆盖解析/分块/索引/检索全链路（OS 目录选择框为浏览器原生行为，不在被测代码内）。
import fs from 'node:fs';
const samples = ['java-oop.txt', 'java-map.docx', 'java-ref.pdf'].map(name => ({
  name, b64: fs.readFileSync('/tmp/poc-kb-samples/' + name).toString('base64')
}));
const injected = await p.eval(`(async () => {
  const dt = new DataTransfer();
  for (const s of ${JSON.stringify(samples)}) {
    const bin = atob(s.b64);
    const arr = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
    dt.items.add(new File([arr], s.name));
  }
  const input = document.getElementById('kbDir');
  input.files = dt.files;
  const n = input.files.length;
  input.dispatchEvent(new Event('change', { bubbles: true }));
  return n;
})()`, true);
record('目录文件注入输入框', injected === 3, 'files=' + injected);
const t0 = Date.now();
const meta = await waitFor(async () => {
  const m = await p.eval('chrome.storage.local.get("kbMeta").then(r => r.kbMeta)', true);
  return m && m.at >= t0 && m.docs === 3 ? m : null;
});
const status = await p.eval('document.getElementById("kbStatus").textContent');
record('三种格式文档全部解析导入', /已导入 3 个文档/.test(status), status);
record('分块统计写入 storage', meta?.docs === 3 && meta?.chunks >= 3, JSON.stringify(meta));

const hit1 = await p.eval(`(async () => KB.search(await KB.allChunks(), '下列哪项不是面向对象的基本特征？A.继承 B.封装 C.多态 D.编译', 2).map(h => h.c.doc))()`, true);
record('检索：面向对象题命中 txt', hit1?.[0] === 'java-oop.txt', JSON.stringify(hit1));
const hit2 = await p.eval(`(async () => KB.search(await KB.allChunks(), 'HashMap 与 Hashtable 线程安全区别', 2).map(h => h.c.doc))()`, true);
record('检索：HashMap 题命中 docx', hit2?.[0] === 'java-map.docx', JSON.stringify(hit2));
const hit3 = await p.eval(`(async () => KB.search(await KB.allChunks(), 'ConcurrentHashMap thread-safe concurrent', 2).map(h => h.c.doc))()`, true);
record('检索：英文题命中 pdf', hit3?.[0] === 'java-ref.pdf', JSON.stringify(hit3));

console.log(`\n==== ${results.filter(Boolean).length}/${results.length} 通过 ====`);
p.ws.close();
process.exit(results.every(Boolean) ? 0 : 1);