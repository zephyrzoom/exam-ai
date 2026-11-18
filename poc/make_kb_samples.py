#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成知识库导入测试样例：txt / docx / pdf 各一份（docx、pdf 为手工构造的最小合法文件）。"""
import os, zipfile

OUT = "/tmp/poc-kb-samples"
os.makedirs(OUT, exist_ok=True)

with open(f"{OUT}/java-oop.txt", "w", encoding="utf-8") as f:
    f.write("面向对象的基本特征包括封装、继承、多态。封装隐藏内部实现细节，继承实现代码复用，"
            "多态允许同一接口在不同实现类中表现不同行为。编译是程序构建过程，不属于面向对象的基本特征。"
            "抽象有时也被列为第四特征，但经典三大特征为封装、继承、多态。\n" * 3)

docx_text = ("HashMap 非线程安全，允许 null 键值，效率高；Hashtable 线程安全但不允许 null，效率低；"
             "并发场景推荐 ConcurrentHashMap，它通过分段锁/CAS 保证线程安全。两者都实现 Map 接口。")
with zipfile.ZipFile(f"{OUT}/java-map.docx", "w") as z:
    z.writestr("[Content_Types].xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
               '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
               '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
               '<Default Extension="xml" ContentType="application/xml"/>'
               '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
               '</Types>')
    z.writestr("_rels/.rels",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
               '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
               '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
               '</Relationships>')
    z.writestr("word/document.xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
               '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
               f'<w:body><w:p><w:r><w:t>{docx_text}</w:t></w:r></w:p></w:body></w:document>')

lines = [
    "Java Collections Reference",
    "HashMap is not thread-safe and permits null keys and values.",
    "Hashtable is thread-safe but legacy and slow.",
    "ConcurrentHashMap is recommended for concurrent access.",
    "ArrayList is not synchronized; use Collections.synchronizedList if needed.",
]
content = "BT /F1 12 Tf 72 720 Td 16 TL\n" + "".join(f"({l}) Tj T*\n" for l in lines) + "ET"
objs = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    f"<< /Length {len(content)} >>\nstream\n{content}\nendstream",
]
pdf = "%PDF-1.4\n"
offsets = []
for i, o in enumerate(objs, 1):
    offsets.append(len(pdf))
    pdf += f"{i} 0 obj\n{o}\nendobj\n"
xref_pos = len(pdf)
pdf += f"xref\n0 {len(objs)+1}\n0000000000 65535 f \n"
pdf += "".join(f"{off:010d} 00000 n \n" for off in offsets)
pdf += f"trailer\n<< /Size {len(objs)+1} /Root 1 0 R >>\nstartxref\n{xref_pos}\n%%EOF"
with open(f"{OUT}/java-ref.pdf", "wb") as f:
    f.write(pdf.encode("latin-1"))

print("\n".join(sorted(os.listdir(OUT))))