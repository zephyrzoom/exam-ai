// 本地知识库：分块 + 中文二元组 BM25 检索 + IndexedDB 存储。options 页与 background SW 共用。
const KB = (() => {
  const DB_NAME = 'poc-kb';
  const STORE = 'chunks';

  function tokenize(text) {
    const lower = String(text).toLowerCase();
    const tokens = [];
    for (const m of lower.matchAll(/[a-z0-9_]+/g)) tokens.push(m[0]);
    for (const run of lower.match(/[一-鿿]+/g) || []) {
      if (run.length === 1) { tokens.push(run); continue; }
      for (let i = 0; i + 1 < run.length; i++) tokens.push(run[i] + run[i + 1]);
    }
    return tokens;
  }

  function chunkText(text, size = 500, overlap = 100) {
    const clean = String(text).replace(/\r/g, '').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
    const chunks = [];
    if (!clean) return chunks;
    let start = 0, idx = 0;
    while (start < clean.length) {
      const end = Math.min(clean.length, start + size);
      chunks.push({ idx: idx++, text: clean.slice(start, end) });
      if (end >= clean.length) break;
      start = end - overlap;
    }
    return chunks;
  }

  function search(chunks, query, k = 4) {
    const qTokens = [...new Set(tokenize(query))];
    if (!qTokens.length || !chunks.length) return [];
    const docs = chunks.map(c => {
      const tf = {};
      const toks = tokenize(c.text);
      for (const t of toks) tf[t] = (tf[t] || 0) + 1;
      return { c, tf, len: toks.length };
    });
    const N = docs.length;
    const avgdl = docs.reduce((s, d) => s + d.len, 0) / N || 1;
    const df = {};
    for (const t of qTokens) df[t] = docs.reduce((s, d) => s + (d.tf[t] ? 1 : 0), 0);
    const k1 = 1.5, b = 0.75;
    return docs.map(d => {
      let score = 0;
      for (const t of qTokens) {
        const f = d.tf[t] || 0;
        if (!f) continue;
        const idf = Math.log(1 + (N - df[t] + 0.5) / (df[t] + 0.5));
        score += idf * (f * (k1 + 1)) / (f + k1 * (1 - b + b * d.len / avgdl));
      }
      return { c: d.c, score };
    }).filter(x => x.score > 0).sort((a, b2) => b2.score - a.score).slice(0, k);
  }

  function openDB() {
    return new Promise((res, rej) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'id', autoIncrement: true });
      };
      req.onsuccess = () => res(req.result);
      req.onerror = () => rej(req.error);
    });
  }

  async function addChunks(rows) {
    const db = await openDB();
    return new Promise((res, rej) => {
      const tx = db.transaction(STORE, 'readwrite');
      const st = tx.objectStore(STORE);
      rows.forEach(r => st.add(r));
      tx.oncomplete = () => { db.close(); res(); };
      tx.onerror = () => { db.close(); rej(tx.error); };
    });
  }

  async function allChunks() {
    const db = await openDB();
    return new Promise((res, rej) => {
      const rq = db.transaction(STORE).objectStore(STORE).getAll();
      rq.onsuccess = () => { db.close(); res(rq.result); };
      rq.onerror = () => { db.close(); rej(rq.error); };
    });
  }

  async function count() {
    const db = await openDB();
    return new Promise((res, rej) => {
      const rq = db.transaction(STORE).objectStore(STORE).count();
      rq.onsuccess = () => { db.close(); res(rq.result); };
      rq.onerror = () => { db.close(); rej(rq.error); };
    });
  }

  async function clear() {
    const db = await openDB();
    return new Promise((res, rej) => {
      const tx = db.transaction(STORE, 'readwrite');
      tx.objectStore(STORE).clear();
      tx.oncomplete = () => { db.close(); res(); };
      tx.onerror = () => { db.close(); rej(tx.error); };
    });
  }

  return { tokenize, chunkText, search, addChunks, allChunks, count, clear };
})();

if (typeof module !== 'undefined' && module.exports) module.exports = KB;