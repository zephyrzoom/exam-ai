const DEFAULTS = {
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  apiKey: '',
  model: 'qwen-vl-plus',
  examOrigins: 'http://localhost:5173,http://127.0.0.1:5173,http://localhost,http://127.0.0.1'
};
const SCRIPT_ID = 'poc-exam-content';

function parseOrigins(raw) {
  const list = String(raw || '').split(/[\s,;，；]+/).map(s => s.trim()).filter(Boolean);
  if (!list.length) throw new Error('考试地址不能为空');
  return [...new Set(list.map(s => {
    const u = new URL(s);
    if (u.protocol !== 'http:' && u.protocol !== 'https:') throw new Error('仅支持 http/https 地址：' + s);
    return u.origin;
  }))];
}

async function syncContentScripts() {
  const cfg = await chrome.storage.sync.get(DEFAULTS);
  const matches = parseOrigins(cfg.examOrigins).map(o => o + '/*');
  const existing = await chrome.scripting.getRegisteredContentScripts({ ids: [SCRIPT_ID] });
  if (existing.length && JSON.stringify(existing[0].matches) === JSON.stringify(matches)) return;
  if (existing.length) await chrome.scripting.unregisterContentScripts({ ids: [SCRIPT_ID] });
  await chrome.scripting.registerContentScripts([{ id: SCRIPT_ID, matches, js: ['content.js'], runAt: 'document_idle' }]);
}
// SW 每次唤醒都校准一次注册状态（动态注册本身跨浏览器重启持久）
syncContentScripts().catch(e => console.warn('PoC 内容脚本注册失败', e));

const PROMPT = '这是一张在线考试页面的截图。请直接作答当前页面显示的题目：' +
  '单选题只给一个选项字母，多选题给多个字母（逗号分隔），判断题回答对或错，填空/简答直接给答案。不要解释过程。';

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg?.type === 'POC_SYNC') {
    syncContentScripts().then(() => sendResponse({ ok: true }))
      .catch(e => sendResponse({ ok: false, error: String(e?.message || e) }));
    return true;
  }
  if (msg?.type !== 'POC_CAPTURE') return;
  (async () => {
    const cfg = await chrome.storage.sync.get(DEFAULTS);
    if (!cfg.apiKey) {
      return { ok: false, error: '未配置 API Key。请在进入考试前打开插件选项页完成配置（考试中途打开插件弹窗本身会触发失焦）。' };
    }
    const dataUrl = await chrome.tabs.captureVisibleTab(sender.tab.windowId, { format: 'png' });
    const resp = await fetch(cfg.baseUrl.replace(/\/+$/, '') + '/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + cfg.apiKey },
      body: JSON.stringify({
        model: cfg.model,
        messages: [{
          role: 'user',
          content: [
            { type: 'text', text: PROMPT },
            { type: 'image_url', image_url: { url: dataUrl } }
          ]
        }]
      })
    });
    if (!resp.ok) {
      return { ok: false, error: '截图成功，模型接口 HTTP ' + resp.status + '：' + (await resp.text()).slice(0, 300) };
    }
    const json = await resp.json();
    const text = json.choices?.[0]?.message?.content?.trim();
    return text ? { ok: true, text } : { ok: false, error: '模型未返回内容：' + JSON.stringify(json).slice(0, 300) };
  })().then(sendResponse).catch(e => sendResponse({ ok: false, error: String(e?.message || e) }));
  return true;
});