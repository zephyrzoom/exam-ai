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

# 选项乱序回归：同一题两名考生选项集合一致、顺序不同
canon = {pq["questionId"]: pq["question"] for pq in detail["questions"]}
def objective_qs(ex):
    return [q for q in ex["questions"] if q["type"] in ("SINGLE", "MULTI")]
m1 = {q["questionId"]: q["options"] for q in objective_qs(exam)}
m2 = {q["questionId"]: q["options"] for q in objective_qs(ex2)}
same_set = all(sorted(m1[k]) == sorted(m2[k]) for k in m1)
diff_order = any(m1[k] != m2[k] for k in m1)
results.append(("选项乱序:选项集合一致", same_set)); print(("PASS " if same_set else "FAIL ") + "选项乱序:选项集合一致")
results.append(("选项乱序:不同考生顺序不同", diff_order)); print(("PASS " if diff_order else "FAIL ") + "选项乱序:不同考生顺序不同")

# 考生2按选项内容定位正确答案字母作答（验证乱序下判分映射）
def correct_display(q):
    cq = canon[q["questionId"]]
    opts = json.loads(cq["optionsJson"])
    pos = {opt: i for i, opt in enumerate(q["options"])}
    return "".join(sorted("ABCDEFGH"[pos[opts[ord(c) - 65]]] for c in cq["answer"]))
ans2 = [{"questionId": q["questionId"], "answer": (correct_display(q) if q["type"] != "ESSAY" else "")} for q in ex2["questions"]]
call("POST", "/exam/save", cl2["token"], {"recordId": ex2["recordId"], "answers": ans2})
ex2b = check("考生2中途重进", call("POST", "/exam/enter", cl2["token"], {"projectId": projid}))
saved_ok = all(ex2b["savedAnswers"].get(str(q["questionId"])) == a["answer"] for q, a in zip(ex2b["questions"], ans2))
order_stable = all(q["options"] == next(x["options"] for x in ex2["questions"] if x["questionId"] == q["questionId"]) for q in ex2b["questions"])
results.append(("选项乱序:重进答案按乱序回显", saved_ok and order_stable)); print(("PASS " if saved_ok and order_stable else "FAIL ") + "选项乱序:重进答案按乱序回显")
check("考生2交卷", call("POST", "/exam/submit", cl2["token"], {"recordId": ex2["recordId"]}))
exp_obj = sum(float(q["score"]) for q in ex2["questions"] if q["type"] != "ESSAY")

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
obj_ok = gd2 and float(gd2["record"]["objectiveScore"]) == exp_obj
results.append(("选项乱序:按内容作答判满分", obj_ok)); print(("PASS " if obj_ok else "FAIL ") + f"选项乱序:按内容作答判满分 (客观分 {gd2['record']['objectiveScore'] if gd2 else '-'} / 期望 {exp_obj})")
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

# 8.5 编组模式
import io, openpyxl

def upload(path, token, filename, content):
    boundary = "----smokeboundary123456"
    body = io.BytesIO()
    body.write(f"--{boundary}\r\n".encode())
    body.write(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode())
    body.write(b"Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n")
    body.write(content); body.write(b"\r\n")
    body.write(f"--{boundary}--\r\n".encode())
    req = urllib.request.Request(BASE + path, data=body.getvalue(), method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r: return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"code": e.code, "msg": e.read().decode()[:300]}

def proj_body(name, mode):
    return {"name": name, "paperId": pid, "mode": mode, "durationMin": 60, "location": "线上",
            "batch": "编组批", "startTime": (now - datetime.timedelta(minutes=5)).strftime("%Y-%m-%d %H:%M:%S"),
            "endTime": (now + datetime.timedelta(hours=3)).strftime("%Y-%m-%d %H:%M:%S"),
            "passScore": round(float(detail["paper"]["totalScore"]) * 0.6, 1)}

gproj = check("创建编组项目", call("POST", "/projects", dt, proj_body("冒烟测试-编组笔试", "GROUP")))
gpid = gproj["id"]
check("编组项目模式字段", call("GET", f"/projects/{gpid}", dt), lambda r: r["data"]["mode"] == "GROUP")
check("个人项目默认模式", call("GET", f"/projects/{projid}", dt), lambda r: r["data"]["mode"] == "INDIVIDUAL")

# 手动添加：A组2人(甲全对/乙全错)、B组1人(丙全对)、未分组1人(丁缺考)
manual = [("组甲", "110101199001011111", "A组"), ("组乙", "110101199001012222", "A组"),
          ("组丙", "110101199001013333", "B组"), ("组丁", "110101199001014444", None)]
for name, idc, grp in manual:
    body = {"name": name, "idCard": idc, "vendor": "测试供应商"}
    if grp: body["groupName"] = grp
    check(f"手动添加考生{name}", call("POST", f"/projects/{gpid}/candidates", dt, body))
# 编辑分组：把丙临时改到A组再改回B组，验证编辑接口
clist = check("编组考生名单", call("GET", f"/projects/{gpid}/candidates", dt))
bing = next(c for c in clist if c["name"] == "组丙")
check("编辑考生分组", call("PUT", f"/projects/{gpid}/candidates/{bing['id']}", dt,
      {"name": "组丙", "groupName": "B组", "vendor": "测试供应商"}), lambda r: r["data"]["groupName"] == "B组")
check("发布编组项目", call("POST", f"/projects/{gpid}/publish", dt))

def group_take(idc, name, correct):
    ct = check(f"考生{name}登录", call("POST", "/auth/candidate-login", body={"idCard": idc, "name": name}))["token"]
    ex = check(f"考生{name}进入考试", call("POST", "/exam/enter", ct, {"projectId": gpid}))
    rid = ex["recordId"]
    ans = []
    for q in ex["questions"]:
        cq = canon[q["questionId"]]
        if q["type"] == "ESSAY": a = ""
        elif correct: a = correct_display(q)
        elif q["type"] == "JUDGE": a = "B" if cq["answer"] == "A" else "A"
        elif q["type"] == "SINGLE": a = "B" if cq["answer"] != "B" else "A"
        else: a = next(l for l in "ABCDEFGH" if l not in cq["answer"])
        ans.append({"questionId": q["questionId"], "answer": a})
    call("POST", "/exam/save", ct, {"recordId": rid, "answers": ans})
    check(f"考生{name}交卷", call("POST", "/exam/submit", ct, {"recordId": rid}))
    return rid

rid_jia = group_take("110101199001011111", "组甲", True)
rid_yi = group_take("110101199001012222", "组乙", False)
rid_bing = group_take("110101199001013333", "组丙", True)
time.sleep(2)
def group_grade(rid):
    gd = call("GET", f"/grading/records/{rid}", dt)["data"]
    for a in gd["answers"]:
        if a["type"] == "ESSAY":
            call("POST", f"/grading/answers/{a['id']}/review", dt, {"score": 0, "comment": "空答案0分"})
    return check(f"合成总分{rid}", call("POST", f"/grading/records/{rid}/finalize", dt))
fin_jia = group_grade(rid_jia); fin_yi = group_grade(rid_yi); fin_bing = group_grade(rid_bing)
check("结束编组项目(丁缺考)", call("POST", f"/projects/{gpid}/finish", dt))

gscores = check("编组成绩查询", call("GET", f"/projects/{gpid}/scores", dt))
gname = {r["candidateName"]: r.get("groupName") for r in gscores}
results.append(("成绩含组名", gname.get("组甲") == "A组" and gname.get("组丙") == "B组" and gname.get("组丁") is None))
print(("PASS " if results[-1][1] else "FAIL ") + f"成绩含组名 {gname}")

groups = check("组平均成绩", call("GET", f"/projects/{gpid}/score-groups", dt))
gmap = {g["groupName"]: g for g in groups}
O = float(exp_obj)
t_jia, t_yi, t_bing = float(fin_jia["totalScore"]), float(fin_yi["totalScore"]), float(fin_bing["totalScore"])
expA, expB = round((t_jia + t_yi) / 2, 2), round(t_bing, 2)
results.append(("全对考生判满分", t_jia == O and t_bing == O)); print(("PASS " if t_jia == O and t_bing == O else "FAIL ") + f"全对考生判满分 (甲{t_jia}/丙{t_bing}/期望{O})")
ok_avg = ("A组" in gmap and "B组" in gmap and "未分组" in gmap
          and gmap["A组"]["memberCount"] == 2 and gmap["A组"]["gradedCount"] == 2
          and abs(float(gmap["A组"]["avgScore"]) - expA) < 0.01
          and gmap["B组"]["memberCount"] == 1 and abs(float(gmap["B组"]["avgScore"]) - expB) < 0.01
          and gmap["未分组"]["memberCount"] == 1 and gmap["未分组"]["gradedCount"] == 0
          and gmap["未分组"]["absentCount"] == 1 and gmap["未分组"].get("avgScore") is None)
results.append(("组平均成绩正确", ok_avg)); print(("PASS " if ok_avg else "FAIL ") + f"组平均成绩正确 期望A组{expA}/B组{expB} 实际{json.dumps(gmap, ensure_ascii=False)}")

# 成绩导出：编组双sheet+组名列；个人模式仍单sheet
gxlsx = call("GET", f"/projects/{gpid}/scores/export", dt, raw=True)
gwb = openpyxl.load_workbook(io.BytesIO(gxlsx))
ghead = [c.value for c in gwb["成绩"][1]]
grow = {r[0].value: r for r in gwb["组平均成绩"].iter_rows(min_row=2)}
ok_xlsx = (gwb.sheetnames == ["成绩", "组平均成绩"] and ghead[2] == "组名"
           and "A组" in grow and abs(float(grow["A组"][4].value) - expA) < 0.01
           and "B组" in grow and abs(float(grow["B组"][4].value) - expB) < 0.01
           and "未分组" in grow and grow["未分组"][4].value == "-")
results.append(("编组成绩Excel双sheet", ok_xlsx)); print(("PASS " if ok_xlsx else "FAIL ") + f"编组成绩Excel双sheet sheets={gwb.sheetnames} 组名列={ghead[2]} A组平均={grow['A组'][4].value if 'A组' in grow else '-'}")
iwb = openpyxl.load_workbook(io.BytesIO(call("GET", f"/projects/{projid}/scores/export", dt, raw=True)))
ok_single = iwb.sheetnames == ["成绩"] and "组名" not in [c.value for c in iwb["成绩"][1]]
results.append(("个人成绩Excel仍单sheet", ok_single)); print(("PASS " if ok_single else "FAIL ") + f"个人成绩Excel仍单sheet {iwb.sheetnames}")

# Excel 导入含组名列（独立项目，避免影响组平均）
iproj = check("创建导入测试项目", call("POST", "/projects", dt, proj_body("冒烟测试-导入编组", "GROUP")))
ipid = iproj["id"]
wb = openpyxl.Workbook(); ws = wb.active
ws.append(["姓名", "身份证号", "手机", "邮箱", "供应商", "组名"])
ws.append(["导入甲", "110101199002021111", "13800000001", "a@x.com", "供应商X", "C组"])
ws.append(["导入乙", "110101199002022222", "13800000002", "b@x.com", "供应商X", "C组"])
buf = io.BytesIO(); wb.save(buf)
check("Excel导入含组名", upload(f"/projects/{ipid}/candidates/import", dt, "import.xlsx", buf.getvalue()),
      lambda r: r["data"]["success"] == 2)
check("导入考生组名解析", call("GET", f"/projects/{ipid}/candidates", dt),
      lambda r: len(r["data"]) == 2 and all(c["groupName"] == "C组" for c in r["data"]))
cexp = openpyxl.load_workbook(io.BytesIO(call("GET", f"/projects/{ipid}/candidates/export", dt, raw=True)))
chead = [c.value for c in cexp["考生名单"][1]]
results.append(("名单导出含组名列", "组名" in chead)); print(("PASS " if "组名" in chead else "FAIL ") + f"名单导出含组名列 {chead}")

# 9. 权限隔离
r = call("GET", "/papers", call("POST", "/auth/login", body={"username": "lisi", "password": "123456"})["data"]["token"])
results.append(("部门数据隔离", all(p["deptId"] == 3 for p in r["data"]["records"])))
print(("PASS " if all(p["deptId"] == 3 for p in r["data"]["records"]) else "FAIL ") + "部门数据隔离")
r = call("GET", "/questions?bankType=PUBLIC", ct)
results.append(("考生禁访管理接口", r.get("code") == 403)); print(("PASS " if r.get("code") == 403 else "FAIL ") + "考生禁访管理接口")

print("\n==== 结果: %d/%d 通过 ====" % (sum(1 for _, ok in results if ok), len(results)))
sys.exit(0 if all(ok for _, ok in results) else 1)