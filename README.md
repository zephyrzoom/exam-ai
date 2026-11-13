# AI 考试系统

面向招聘笔试场景的在线考试系统，覆盖 **考生名单确认 → AI 组卷 → 考试通知 → 在线考试 → 判分复核 → 入围回传** 全流程。

技术栈：**Java 8 + Spring Boot 2.7 + MyBatis-Plus + MySQL 5.7/8.0 + Vue 3 + Vite + Element Plus + ECharts**

## 目录结构

```
exam-ai/
├── backend/          # Spring Boot 后端（Maven）
├── frontend/         # Vue3 前端（Vite）
├── sql/
│   ├── schema.sql    # 建库建表脚本
│   └── data.sql      # 种子数据（部门/账号/54道示例试题）
└── 需求.txt          # 原始需求文档
```

## 快速开始

### 环境要求

- JDK 8、Maven 3.5+
- MySQL 5.7+（本机 3306）
- Node.js 18+（推荐 20）

### 1. 初始化数据库

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/data.sql
```

> 务必带 `--default-character-set=utf8mb4`，否则中文乱码。
> 数据库连接默认用户 `root`，密码通过环境变量 `DB_PASSWORD` 注入（启动后端前 `export DB_PASSWORD=你的密码`），也可直接修改 `backend/src/main/resources/application.yml`。

### 2. 启动后端（端口 8081）

```bash
cd backend
mvn package -DskipTests
java -jar target/exam-ai-backend.jar
```

### 3. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173 （开发环境已配置 `/api` 代理到 8081）。

### 演示账号（密码均 123456）

| 账号 | 角色 | 说明 |
|---|---|---|
| admin | 项目管理部招聘负责人 | 维护公共题库、审核贡献、查询导出全部成绩、用户与部门管理 |
| zhangsan | 研发部招聘负责人 | 维护本部门私有题库、组卷、创建笔试项目、阅卷、入围回传 |
| lisi / wangwu | 数据部 / 测试部招聘负责人 | 同上，数据按部门隔离 |
| 考生 | 身份证号 + 姓名登录 | 名单由人事系统同步/Excel 导入/手动添加产生 |

## 功能与需求对照

| 需求 | 实现 |
|---|---|
| 权限管理 | JWT 鉴权；PM_ADMIN / DEPT_ADMIN / CANDIDATE 三角色；私有题库、试卷、项目按部门隔离 |
| 试题管理 | 单选/多选/判断/主观简答；技能/难度(1-5)/知识点标签筛选；Excel 导入导出；公共题库与部门私有题库；私有贡献公共需 PM 审核 |
| 组卷管理 | AI 组卷（岗位要求/测评目标/题型构成/数量/难度比例，从公共题库加权随机抽题）；人工增删改查、调序、改分，全部留痕；按名称/创建时间/状态筛选；已发布试卷可编辑/复制/删除；Word/PDF 导出；**签署保密承诺书后方可发布** |
| 考试管理 | 笔试项目（试卷/时长/地点/批次/开考与截止时间/及格线）；人事系统对接拉取名单（含身份证号、供应商）+ Excel 导入 + 手动添加；邮件通知考生并抄送属地招聘管理员与部门负责人；到场率/平均分/及格人数/入围人数/分数分布统计；名单与成绩报表导出 |
| 在线考试 | 身份证号+姓名登录；倒计时、主动交卷、超时自动交卷（服务端定时任务兜底）；答案自动保存（15s + 变更防抖）、断线恢复作答；防作弊：切屏/失焦/复制/粘贴/右键/退出全屏事件记录并计数，阅卷端可见 |
| 成绩管理 | 客观题自动判分（多选全对满分/漏选半分/错选 0 分）；主观题 **AI 初评 + 阅卷人逐题复核**（展示考生答案/参考答案/评分要点/AI 分数与说明，显示阅卷人）；评分全程追溯日志；复核完成后合成总分；个人成绩报告（可打印）；整体分析（基础统计/分数分布/及格率/逐题难度与区分度）；成绩查询与 Excel 导出 |
| 入围回传 | 入围方案（TOP_N / 比例 / 分数线，须达及格线）；预览→确认→回传人事系统（成绩/及格线/是否及格/排名/入围结果/方案标识/缺考标识/切屏次数），报文存档可查 |

## 外部系统对接配置（application.yml / 环境变量）

系统对三个外部依赖均提供**可插拔实现**，未配置时使用内置兜底，开箱即可演示：

| 配置 | 作用 | 未配置时行为 |
|---|---|---|
| `exam.ai.base-url` / `exam.ai.api-key` / `exam.ai.model`（或环境变量 `AI_BASE_URL`/`AI_API_KEY`/`AI_MODEL`） | OpenAI 兼容大模型接口，用于主观题 AI 初评 | 使用内置"评分要点匹配引擎"初评，并标注[规则引擎初评] |
| `exam.hr.base-url`（`HR_BASE_URL`） | 人事系统：`GET {base}/candidates?projectId=` 拉名单；`POST {base}/exam-results` 回传结果 | Mock：生成演示考生名单；回传报文存档至 `hr_sync_log` |
| `exam.mail.enabled` + `spring.mail.*`（`MAIL_ENABLED`/`MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM`） | 真实 SMTP 发送考试通知 | 模拟发送，通知内容存档至 `notify_log` 可查看 |

## 判分规则说明

- 单选/判断：与标准答案一致得满分，否则 0 分。
- 多选：全对满分；漏选（所选均为正确项子集）半分；错选 0 分。
- 主观：AI 初评给出分数与要点命中说明 → 阅卷人确认或修正 → 以复核分为准 → 全部复核后合成总分并计算项目内排名与及格。
- 区分度：总分前 27% 与后 27% 考生该题得分率之差；难度：全体考生该题平均得分率。

## 主要 API

```
POST /api/auth/login                管理端登录
POST /api/auth/candidate-login      考生登录（身份证号+姓名）
GET  /api/questions                 题库分页（bankType/type/skill/difficulty/knowledgePoint/keyword）
POST /api/questions/import          Excel 导入
POST /api/questions/{id}/contribute 私有贡献公共
POST /api/papers/ai-compose         AI 组卷
POST /api/papers/{id}/pledge        签署保密承诺书
POST /api/papers/{id}/publish       发布试卷
GET  /api/papers/{id}/export        导出 word/pdf
POST /api/projects/{id}/candidates/hr-sync  人事系统拉取名单
POST /api/projects/{id}/notify      发送考试通知
POST /api/exam/enter|save|submit    考生进入/保存/交卷
POST /api/exam/cheat                防作弊事件上报
POST /api/grading/answers/{id}/review       主观题复核
POST /api/grading/records/{id}/finalize     合成总分
GET  /api/projects/{id}/analysis    整体成绩分析
POST /api/projects/{id}/shortlist/confirm   确认入围
POST /api/projects/{id}/shortlist/push-hr   回传人事系统
```

## 测试

后端全流程冒烟脚本（登录→题库→组卷→承诺发布→项目→名单→通知→考试→判分→复核→分析→入围→回传→权限隔离，47 项断言）见会话交付说明；浏览器端已逐页面实测通过。