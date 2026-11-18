const DEFAULTS = {
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  apiKey: '',
  model: 'qwen-vl-plus'
};
const $ = id => document.getElementById(id);

chrome.storage.sync.get(DEFAULTS).then(cfg => {
  $('baseUrl').value = cfg.baseUrl;
  $('apiKey').value = cfg.apiKey;
  $('model').value = cfg.model;
});

$('save').addEventListener('click', async () => {
  const baseUrl = $('baseUrl').value.trim().replace(/\/+$/, '') || DEFAULTS.baseUrl;
  const apiKey = $('apiKey').value.trim();
  const model = $('model').value.trim() || DEFAULTS.model;
  const status = $('status');
  try {
    const origin = new URL(baseUrl).origin;
    const covered = await chrome.permissions.contains({ origins: [origin + '/*'] });
    if (!covered) {
      const granted = await chrome.permissions.request({ origins: [origin + '/*'] });
      if (!granted) { status.textContent = '未授权接口域名，无法保存。'; return; }
    }
    await chrome.storage.sync.set({ baseUrl, apiKey, model });
    status.textContent = '已保存。';
  } catch (e) {
    status.textContent = '保存失败：' + (e?.message || e);
  }
});