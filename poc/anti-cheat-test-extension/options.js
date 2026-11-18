const DEFAULTS = {
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  apiKey: '',
  model: 'qwen-vl-plus',
  examOrigins: 'http://localhost:5173,http://127.0.0.1:5173,http://localhost,http://127.0.0.1'
};
const $ = id => document.getElementById(id);

chrome.storage.sync.get(DEFAULTS).then(cfg => {
  $('baseUrl').value = cfg.baseUrl;
  $('apiKey').value = cfg.apiKey;
  $('model').value = cfg.model;
  $('examOrigins').value = cfg.examOrigins;
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