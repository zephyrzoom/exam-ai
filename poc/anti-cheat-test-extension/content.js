(() => {
  const HOST_ID = 'exam-ai-poc-host';

  function build() {
    const host = document.createElement('div');
    host.id = HOST_ID;
    host.style.cssText = 'position:fixed;right:24px;bottom:24px;z-index:2147483647;';
    const root = host.attachShadow({ mode: 'closed' });
    root.innerHTML = `
      <style>
        .btn { padding:10px 16px; border:none; border-radius:20px; background:#7c3aed; color:#fff;
               font-size:14px; cursor:pointer; box-shadow:0 4px 12px rgba(0,0,0,.25); }
        .btn:disabled { opacity:.6; cursor:wait; }
        .panel { position:absolute; bottom:calc(100% + 10px); right:0; width:340px; background:#fff;
                 border:1px solid #ddd; border-radius:10px; box-shadow:0 8px 24px rgba(0,0,0,.2);
                 font:13px/1.6 -apple-system,sans-serif; color:#333; }
        .hd { display:flex; justify-content:space-between; align-items:center; padding:8px 12px;
              border-bottom:1px solid #eee; font-weight:600; background:#f5f3ff; border-radius:10px 10px 0 0; }
        .hd button { border:none; background:none; font-size:16px; cursor:pointer; color:#999; }
        pre { margin:0; padding:12px; max-height:320px; overflow:auto; white-space:pre-wrap;
              word-break:break-word; user-select:text; }
      </style>
      <button class="btn" id="ask">PoC 识题</button>
      <div class="panel" id="panel" hidden>
        <div class="hd"><span>防作弊测试 PoC · 模型作答</span><button id="close" title="关闭">×</button></div>
        <pre id="out"></pre>
      </div>`;
    const btn = root.getElementById('ask');
    const panel = root.getElementById('panel');
    const out = root.getElementById('out');
    root.getElementById('close').addEventListener('click', () => { panel.hidden = true; });
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      btn.textContent = '截图识别中…';
      panel.hidden = false;
      out.textContent = '正在截图并请求视觉模型…';
      try {
        const res = await chrome.runtime.sendMessage({ type: 'POC_CAPTURE' });
        out.textContent = res?.ok
          ? res.text + (res.sources?.length ? '\n\n依据：' + res.sources.join('、') : '\n\n（知识库无匹配资料，模型自身知识作答）')
          : '失败：' + (res?.error || '未知错误');
      } catch (e) {
        out.textContent = '失败：' + (e?.message || e);
      } finally {
        btn.disabled = false;
        btn.textContent = 'PoC 识题';
      }
    });
    document.documentElement.appendChild(host);
  }

  if (!document.getElementById(HOST_ID)) build();
})();