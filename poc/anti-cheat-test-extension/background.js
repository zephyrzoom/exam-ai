importScripts('kb.js');

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

async function doSyncContentScripts() {
  const cfg = await chrome.storage.sync.get(DEFAULTS);
  const matches = parseOrigins(cfg.examOrigins).map(o => o + '/*');
  const existing = await chrome.scripting.getRegisteredContentScripts({ ids: [SCRIPT_ID] });
  if (existing.length && JSON.stringify(existing[0].matches) === JSON.stringify(matches)) return;
  if (existing.length) await chrome.scripting.unregisterContentScripts({ ids: [SCRIPT_ID] });
  await chrome.scripting.registerContentScripts([{ id: SCRIPT_ID, matches, js: ['content.js'], runAt: 'document_idle' }]);
}
// SW 唤醒校准与 POC_SYNC 可能并发触发，串行化避免 Duplicate script ID
let syncChain = Promise.resolve();
function syncContentScripts() {
  const run = syncChain.then(doSyncContentScripts);
  syncChain = run.catch(() => {});
  return run;
}
syncContentScripts().catch(e => console.warn('PoC 内容脚本注册失败', e));

async function callVision(cfg, parts) {
  const resp = await fetch(cfg.baseUrl.replace(/\/+$/, '') + '/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + cfg.apiKey },
    body: JSON.stringify({ model: cfg.model, messages: [{ role: 'user', content: parts }] })
  });
  if (!resp.ok) throw new Error('模型接口 HTTP ' + resp.status + '：' + (await resp.text()).slice(0, 300));
  const json = await resp.json();
  const text = json.choices?.[0]?.message?.content?.trim();
  if (!text) throw new Error('模型未返回内容：' + JSON.stringify(json).slice(0, 300));
  return text;
}

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
    const image = { type: 'image_url', image_url: { url: dataUrl } };

    let question;
    try {
      question = await callVision(cfg, [
        { type: 'text', text: '提取截图中当前显示题目的完整文字：题干与全部选项（保留选项字母）。只输出题目文字，不要作答。若页面没有题目，输出页面主要文字。' },
        image
      ]);
    } catch (e) {
      return { ok: false, error: '截图成功，题目提取失败：' + (e?.message || e) };
    }

    const hits = KB.search(await KB.allChunks(), question, 4);
    const context = hits.map((h, i) => `【资料${i + 1}｜${h.c.doc} 片段${h.c.idx + 1}】\n${h.c.text}`).join('\n\n');
    const prompt = (context
      ? `知识库检索到的参考资料：\n${context}\n\n请优先依据上述资料作答；资料不足时再用自身知识。`
      : '知识库中无匹配资料，请用自身知识作答。')
      + '单选题只给一个选项字母，多选题给多个字母（逗号分隔），判断题回答对或错，填空/简答直接给答案。不要解释过程。';
    try {
      const text = await callVision(cfg, [{ type: 'text', text: prompt }, image]);
      return { ok: true, text, sources: hits.map(h => `${h.c.doc} #${h.c.idx + 1}`) };
    } catch (e) {
      return { ok: false, error: '题目提取与检索成功，作答失败：' + (e?.message || e) };
    }
  })().then(sendResponse).catch(e => sendResponse({ ok: false, error: String(e?.message || e) }));
  return true;
});