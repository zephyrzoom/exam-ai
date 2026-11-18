const DEFAULTS = {
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  apiKey: '',
  model: 'qwen-vl-plus'
};

const PROMPT = '这是一张在线考试页面的截图。请直接作答当前页面显示的题目：' +
  '单选题只给一个选项字母，多选题给多个字母（逗号分隔），判断题回答对或错，填空/简答直接给答案。不要解释过程。';

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
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