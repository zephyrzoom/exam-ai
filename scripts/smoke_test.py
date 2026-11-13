#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""后端全流程冒烟测试"""
import json, urllib.request, urllib.error, sys, time

BASE = "http://localhost:8081/api"
results = []

def call(method, path, token=None, body=None, raw=False):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            if raw: return r.read()
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"code": e.code, "msg": e.read().decode()[:300]}

def check(name, res, cond=None):
    ok = res.get("code") == 0 if isinstance(res, dict) else False
    if cond is not None: ok = ok and cond(res)
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else " -> " + json.dumps(res, ensure_ascii=False)[:300]))
    return res.get("data") if ok else None

# 1. 登录
admin = check("管理员登录", call("POST", "/auth/login", body={"username": "admin", "password": "123456"}))
dept = check("部门登录(zhangsan)", call("POST", "/auth/login", body={"username": "zhangsan", "password": "123456"}))
at, dt = admin["token"], dept["token"]

# 2. 题库
qs = check("公共题库分页", call("GET", "/questions?bankType=PUBLIC&pageSize=5", dt))
newq = check("部门创建私有题", call("POST", "/questions", dt, {
    "bankType": "PRIVATE", "type": "SINGLE", "content": "冒烟测试题：1+1=?",
    "optionsJson": json.dumps(["1", "2", "3", "4"]), "answer": "B",
    "skill": "Java", "difficulty": 1, "knowledgePoint": "基础", "defaultScore": 3}))
check("私有题贡献公共", call("POST", f"/questions/{newq['id']}/contribute", dt))
contribs = check("PM查看待审核贡献", call("GET", "/questions/contributions", at))
check("PM审核通过", call("POST", f"/questions/{contribs['records'][0]['id']}/approve", at, {"pass": True}))
check("删除测试题", call("DELETE", f"/questions/{newq['id']}", dt))

# 3. AI 组卷
paper = check("AI组卷", call("POST", "/papers/ai-compose", dt, {
    "name": "冒烟测试-Java笔试卷", "jobRequirement": "Java初级开发", "assessTarget": "基础能力",
    "skill": "Java",
    "rules": [
        {"type": "SINGLE", "count": 5, "difficultyRatio": {"1": 40, "2": 30, "3": 30}},
        {"type": "MULTI", "count": 2, "difficultyRatio": {"2": 50, "3": 50}},
        {"type": "JUDGE", "count": 2},
        {"type": "ESSAY", "count": 1}
    ]}))
pid = paper["id"]
detail = check("试卷详情", call("GET", f"/papers/{pid}", dt))
print("  试卷题量:", detail["paper"]["questionCount"], "总分:", detail["paper"]["totalScore"])
check("无承诺发布被拒", call("POST", f"/papers/{pid}/publish", dt), lambda r: False) if False else None
r = call("POST", f"/papers/{pid}/publish", dt)
results.append(("未签承诺禁止发布", r.get("code") != 0)); print(("PASS " if r.get("code") != 0 else "FAIL ") + "未签承诺禁止发布")
check("签署保密承诺", call("POST", f"/papers/{pid}/pledge", dt, {"signerName": "张三"}))
check("发布试卷", call("POST", f"/papers/{pid}/publish", dt))
check("试卷调整日志", call("GET", f"/papers/{pid}/logs", dt), lambda r: len(r["data"]) >= 3)
word = call("GET", f"/papers/{pid}/export?format=word", dt, raw=True)
results.append(("Word导出", word[:2] == b"PK")); print(("PASS " if word[:2] == b"PK" else "FAIL ") + f"Word导出 {len(word)} bytes")
pdf = call("GET", f"/papers/{pid}/export?format=pdf", dt, raw=True)
results.append(("PDF导出", pdf[:4] == b"%PDF")); print(("PASS " if pdf[:4] == b"%PDF" else "FAIL ") + f"PDF导出 {len(pdf)} bytes")

# 4. 笔试项目
import datetime
now = datetime.datetime.now()
proj = check("创建笔试项目", call("POST", "/projects", dt, {
    "name": "冒烟测试-2026秋季笔试", "paperId": pid, "durationMin": 60,
    "location": "线上", "batch": "第一批",
    "startTime": (now - datetime.timedelta(minutes=5)).strftime("%Y-%m-%d %H:%M:%S"),
    "endTime": (now + datetime.timedelta(hours=3)).strftime("%Y-%m-%d %H:%M:%S"),
    "passScore": round(float(detail["paper"]["totalScore"]) * 0.6, 1)}))
projid = proj["id"]
sync = check("HR同步考生(mock)", call("POST", f"/projects/{projid}/candidates/hr-sync", dt))
print("  同步考生:", sync)
cands = check("考生名单", call("GET", f"/projects/{projid}/candidates", dt))
check("发布项目", call("POST", f"/projects/{projid}/publish", dt))
notice = check("发送考试通知(模拟)", call("POST", f"/projects/{projid}/notify", dt))
print("  通知状态:", notice["status"], "抄送:", notice["ccList"])

# 5. 考生考试
c0 = cands[0]
clogin = check("考生登录", call("POST", "/auth/candidate-login", body={"idCard": c0["idCard"], "name": c0["name"]}))
ct = clogin["token"]
check("考生项目列表", call("GET", "/auth/my-projects", ct))
exam = check("进入考试", call("POST", "/exam/enter", ct, {"projectId": projid}))
rid = exam["recordId"]
questions = exam["questions"]
print("  题目数:", len(questions), "剩余秒:", exam["remainSeconds"])
answers = []
for i, q in enumerate(questions):
    if q["type"] == "SINGLE":
        ans = "B" if i % 2 == 0 else "A"
    elif q["type"] == "MULTI":
        ans = "ABC"
    elif q["type"] == "JUDGE":
        ans = "A"
    else:
        ans = "HashMap 非线程安全，允许 null 键值，效率高；Hashtable 线程安全，不允许 null，效率低。并发推荐 ConcurrentHashMap。两者都实现 Map 接口。"
    answers.append({"questionId": q["questionId"], "answer": ans})
check("保存答案", call("POST", "/exam/save", ct, {"recordId": rid, "answers": answers}))
check("上报切屏事件", call("POST", "/exam/cheat", ct, {"recordId": rid, "eventType": "BLUR", "detail": "测试切屏"}))
check("主动交卷", call("POST", "/exam/submit", ct, {"recordId": rid}))

# 第二名考生考一半不交卷（验证自动判分/缺考逻辑用）
c1 = cands[1]
cl2 = check("考生2登录", call("POST", "/auth/candidate-login", body={"idCard": c1["idCard"], "name": c1["name"]}))
ex2 = check("考生2进入考试", call("POST", "/exam/enter", cl2["token"], {"projectId": projid}))
ans2 = [{"questionId": q["questionId"], "answer": ("A" if q["type"] in ("SINGLE", "JUDGE") else ("AB" if q["type"] == "MULTI" else ""))} for q in ex2["questions"]]
call("POST", "/exam/save", cl2["token"], {"recordId": ex2["recordId"], "answers": ans2})
check("考生2交卷", call("POST", "/exam/submit", cl2["token"], {"recordId": ex2["recordId"]}))

# 6. 判分复核
time.sleep(2)
glist = check("阅卷列表", call("GET", "/grading/records?state=pending", dt))
print("  待复核:", glist["total"])
gd = check("阅卷详情", call("GET", f"/grading/records/{rid}", dt))
essays = [a for a in gd["answers"] if a["type"] == "ESSAY"]
print("  主观题AI初评:", [(a["aiScore"], (a["aiComment"] or "")[:40]) for a in essays])
for a in essays:
    check(f"复核主观题#{a['questionId']}", call("POST", f"/grading/answers/{a['id']}/review", dt,
          {"score": float(a["aiScore"] or 0), "comment": "同意AI评分"}))
fin = check("合成总分", call("POST", f"/grading/records/{rid}/finalize", dt))
print("  总分:", fin["totalScore"], "客观:", fin["objectiveScore"], "主观:", fin["subjectiveScore"], "排名:", fin["rankNo"], "及格:", fin["passed"])
# 考生2全部确认
gd2 = check("考生2阅卷详情", call("GET", f"/grading/records/{ex2['recordId']}", dt))
for a in [x for x in gd2["answers"] if x["type"] == "ESSAY"]:
    call("POST", f"/grading/answers/{a['id']}/review", dt, {"score": 2, "comment": "未作答要点，低分"})
check("考生2合成总分", call("POST", f"/grading/records/{ex2['recordId']}/finalize", dt))

# 7. 成绩与分析
scores = check("成绩查询", call("GET", f"/projects/{projid}/scores", dt))
analysis = check("成绩分析", call("GET", f"/projects/{projid}/analysis", dt))
print("  基础统计:", json.dumps(analysis["basic"], ensure_ascii=False))
print("  题目难度/区分度:", [(q["difficulty"], q["discrimination"]) for q in analysis["questionStats"]][:3])
report = check("个人报告", call("GET", f"/records/{rid}/report", dt))
xlsx = call("GET", f"/projects/{projid}/scores/export", dt, raw=True)
results.append(("成绩Excel导出", len(xlsx) > 3000)); print(("PASS " if len(xlsx) > 3000 else "FAIL ") + f"成绩Excel导出 {len(xlsx)} bytes")

# 8. 入围与回传
check("保存入围方案", call("POST", f"/projects/{projid}/shortlist-plan", dt,
      {"name": "冒烟入围方案", "ruleType": "TOP_N", "topN": 1}))
prev = check("入围预览", call("GET", f"/projects/{projid}/shortlist/preview", dt))
print("  入围人数:", prev["shortlistCount"], "/", prev["gradedCount"])
check("确认入围", call("POST", f"/projects/{projid}/shortlist/confirm", dt))
push = check("回传HR(mock)", call("POST", f"/projects/{projid}/shortlist/push-hr", dt))
print("  回传:", push["count"], "条, mock:", push["mock"])
logs = check("HR同步日志", call("GET", f"/projects/{projid}/hr-sync-logs", dt))
print("  同步日志:", [(l["direction"], l["status"]) for l in logs])

# 9. 权限隔离
r = call("GET", "/papers", call("POST", "/auth/login", body={"username": "lisi", "password": "123456"})["data"]["token"])
results.append(("部门数据隔离", all(p["deptId"] == 3 for p in r["data"]["records"])))
print(("PASS " if all(p["deptId"] == 3 for p in r["data"]["records"]) else "FAIL ") + "部门数据隔离")
r = call("GET", "/questions?bankType=PUBLIC", ct)
results.append(("考生禁访管理接口", r.get("code") == 403)); print(("PASS " if r.get("code") == 403 else "FAIL ") + "考生禁访管理接口")

print("\n==== 结果: %d/%d 通过 ====" % (sum(1 for _, ok in results if ok), len(results)))
sys.exit(0 if all(ok for _, ok in results) else 1)