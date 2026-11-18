const DEFAULTS = {
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  apiKey: '',
  model: 'qwen-vl-plus',
  examOrigins: 'http://localhost:5173,http://127.0.0.1:5173,http://localhost,http://127.0.0.1'
};
const $ = id => document.getElementById(id);
const TEXT_EXTS = ['txt', 'md', 'markdown', 'text', 'csv', 'log'];

chrome.storage.sync.get(DEFAULTS).then(cfg => {
  $('baseUrl').value = cfg.baseUrl;
  $('apiKey').value = cfg.apiKey;
  $('model').value = cfg.model;
  $('examOrigins').value = cfg.examOrigins;
});
chrome.storage.local.get('kbMeta').then(({ kbMeta }) => {
  if (kbMeta) $('kbStatus').textContent = `已导入 ${kbMeta.docs} 个文档 / ${kbMeta.chunks} 个分块（跳过 ${kbMeta.skipped}）`;
});

async function parseFile(f) {
  const ext = (f.name.split('.').pop() || '').toLowerCase();
  if (ext === 'pdf') {
    const doc = await pdfjsLib.getDocument({ data: await f.arrayBuffer() }).promise;
    let text = '';
    for (let p = 1; p <= doc.numPages; p++) {
      const tc = await (await doc.getPage(p)).getTextContent();
      text += tc.items.map(it => it.str).join(' ') + '\n';
    }
    return text;
  }
  if (ext === 'docx') {
    return (await mammoth.extractRawText({ arrayBuffer: await f.arrayBuffer() })).value;
  }
  if (TEXT_EXTS.includes(ext)) return f.text();
  return null;
}

$('kbDir').addEventListener('change', async e => {
  const files = [...e.target.files];
  e.target.value = '';
  if (!files.length) return;
  const status = $('kbStatus');
  pdfjsLib.GlobalWorkerOptions.workerSrc = chrome.runtime.getURL('vendor/pdf.worker.min.js');
  await KB.clear();
  let docs = 0, chunks = 0, skipped = 0;
  for (let i = 0; i < files.length; i++) {
    const f = files[i];
    const name = f.webkitRelativePath || f.name;
    status.textContent = `解析中 ${i + 1}/${files.length}：${name}`;
    try {
      const text = await parseFile(f);
      const parts = text == null ? [] : KB.chunkText(text);
      if (!parts.length) { skipped++; continue; }
      await KB.addChunks(parts.map(c => ({ doc: name, idx: c.idx, text: c.text })));
      docs++; chunks += parts.length;
    } catch (err) {
      skipped++;
      console.warn('解析失败', name, err);
    }
  }
  const kbMeta = { docs, chunks, skipped, at: Date.now() };
  await chrome.storage.local.set({ kbMeta });
  status.textContent = `已导入 ${docs} 个文档 / ${chunks} 个分块（跳过 ${skipped}）`;
});

$('kbClear').addEventListener('click', async () => {
  await KB.clear();
  await chrome.storage.local.remove('kbMeta');
  $('kbStatus').textContent = '未导入知识库（作答将仅用模型自身知识）';
});

$('save').addEventListener('click', async () => {
  const baseUrl = $('baseUrl').value.trim().replace(/\/+$/, '') || DEFAULTS.baseUrl;
  const apiKey = $('apiKey').value.trim();
  const model = $('model').value.trim() || DEFAULTS.model;
  const examOrigins = $('examOrigins').value.trim();
  const status = $('status');
  try {
    const list = examOrigins.split(/[\s,;，；]+/).map(s => s.trim()).filter(Boolean);
    if (!list.length) throw new Error('考试地址不能为空');
    for (const s of list) {
      const u = new URL(s);
      if (u.protocol !== 'http:' && u.protocol !== 'https:') throw new Error('仅支持 http/https 地址：' + s);
    }
    const origin = new URL(baseUrl).origin;
    const covered = await chrome.permissions.contains({ origins: [origin + '/*'] });
    if (!covered) {
      const granted = await chrome.permissions.request({ origins: [origin + '/*'] });
      if (!granted) { status.textContent = '未授权接口域名，无法保存。'; return; }
    }
    await chrome.storage.sync.set({ baseUrl, apiKey, model, examOrigins });
    const res = await chrome.runtime.sendMessage({ type: 'POC_SYNC' });
    status.textContent = res?.ok ? '已保存，匹配地址已更新（已打开的页面需刷新）。' : '保存成功但脚本注册失败：' + res?.error;
  } catch (e) {
    status.textContent = '保存失败：' + (e?.message || e);
  }
});