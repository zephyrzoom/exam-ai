#!/usr/bin/env node
// kb.js 检索单测：node poc/kb.test.mjs
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const KB = require('./anti-cheat-test-extension/kb.js');

const results = [];
const record = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' — ' + detail : '')); };

const docs = [
  { doc: 'java-oop.txt', idx: 0, text: '面向对象的基本特征包括封装、继承、多态。封装隐藏内部实现，继承实现代码复用，多态允许同一接口不同实现。编译是程序构建过程，不属于面向对象特征。' },
  { doc: 'java-map.docx', idx: 0, text: 'HashMap 非线程安全，允许 null 键值，效率高；Hashtable 线程安全但效率低；并发场景推荐 ConcurrentHashMap，它通过分段锁保证线程安全。' },
  { doc: 'cooking.txt', idx: 0, text: '红烧肉的做法：五花肉切块焯水，加冰糖炒糖色，放入酱油料酒小火炖煮四十分钟，最后大火收汁。' }
];

const parts = KB.chunkText('第一段内容。\n\n\n第二段内容。'.repeat(60));
record('分块：长文本切出多块', parts.length > 1, '块数=' + parts.length);
record('分块：相邻块有重叠', parts.length > 1 && parts[0].text.slice(-100) === parts[1].text.slice(0, 100));

const q1 = '下列哪项不是面向对象的基本特征？ A.继承 B.封装 C.多态 D.编译';
const h1 = KB.search(docs, q1, 2);
record('中文检索：面向对象题命中 oop 文档', h1[0]?.c.doc === 'java-oop.txt', JSON.stringify(h1.map(h => [h.c.doc, +h.score.toFixed(2)])));

const q2 = 'HashMap 与 Hashtable 的线程安全区别是什么';
const h2 = KB.search(docs, q2, 2);
record('中文检索：HashMap 题命中 map 文档', h2[0]?.c.doc === 'java-map.docx', JSON.stringify(h2.map(h => [h.c.doc, +h.score.toFixed(2)])));

const h3 = KB.search(docs, '红烧肉 糖色', 2);
record('中文检索：无关查询不串档', h3[0]?.c.doc === 'cooking.txt');

record('空知识库返回空', KB.search([], q1).length === 0);
record('英文词元检索', KB.search([{ doc: 'en.txt', idx: 0, text: 'ConcurrentHashMap is thread-safe and recommended for concurrency.' }], 'thread-safe ConcurrentHashMap', 1)[0]?.c.doc === 'en.txt');

console.log(`\n==== ${results.filter(Boolean).length}/${results.length} 通过 ====`);
process.exit(results.every(Boolean) ? 0 : 1);