#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为切屏防护 PoC 准备全新测试数据（试卷/项目/考生），输出考生登录信息与考试页 URL。"""
import json, urllib.request, datetime

BASE = "http://localhost:8081/api"

def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 method=method)
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.loads(r.read().decode())
    if j.get("code") != 0:
        raise SystemExit(f"{method} {path} 失败: {json.dumps(j, ensure_ascii=False)[:300]}")
    return j["data"]

dt = call("POST", "/auth/login", body={"username": "zhangsan", "password": "123456"})["token"]
paper = call("POST", "/papers/ai-compose", dt, {
    "name": "PoC切屏防护验证卷", "jobRequirement": "Java初级开发", "assessTarget": "基础能力",
    "skill": "Java",
    "rules": [
        {"type": "SINGLE", "count": 5, "difficultyRatio": {"1": 40, "2": 30, "3": 30}},
        {"type": "MULTI", "count": 2, "difficultyRatio": {"2": 50, "3": 50}},
        {"type": "JUDGE", "count": 2},
        {"type": "ESSAY", "count": 1}
    ]})
pid = paper["id"]
detail = call("GET", f"/papers/{pid}", dt)
call("POST", f"/papers/{pid}/pledge", dt, {"signerName": "张三"})
call("POST", f"/papers/{pid}/publish", dt)

now = datetime.datetime.now()
fmt = "%Y-%m-%d %H:%M:%S"
proj = call("POST", "/projects", dt, {
    "name": "PoC切屏防护验证-" + now.strftime("%m%d%H%M"), "paperId": pid, "durationMin": 60,
    "location": "线上", "batch": "PoC",
    "startTime": (now - datetime.timedelta(minutes=5)).strftime(fmt),
    "endTime": (now + datetime.timedelta(hours=3)).strftime(fmt),
    "passScore": round(float(detail["paper"]["totalScore"]) * 0.6, 1)})
projid = proj["id"]
call("POST", f"/projects/{projid}/candidates/hr-sync", dt)
cands = call("GET", f"/projects/{projid}/candidates", dt)
call("POST", f"/projects/{projid}/publish", dt)

c0 = cands[0]
call("POST", "/auth/candidate-login", body={"idCard": c0["idCard"], "name": c0["name"]})
print(json.dumps({"idCard": c0["idCard"], "name": c0["name"], "projectId": projid,
                  "url": f"http://localhost:5173/candidate/exam/{projid}"}, ensure_ascii=False))