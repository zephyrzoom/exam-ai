-- ============================================================
-- AI 考试系统 数据库初始化脚本（MySQL 5.7+ / utf8mb4）
-- 用法: mysql -uroot -p < schema.sql  然后 mysql -uroot -p exam_ai < data.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS exam_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE exam_ai;

DROP TABLE IF EXISTS cheat_event;
DROP TABLE IF EXISTS hr_sync_log;
DROP TABLE IF EXISTS notify_log;
DROP TABLE IF EXISTS shortlist_plan;
DROP TABLE IF EXISTS review_log;
DROP TABLE IF EXISTS answer_record;
DROP TABLE IF EXISTS exam_record;
DROP TABLE IF EXISTS exam_candidate;
DROP TABLE IF EXISTS exam_project;
DROP TABLE IF EXISTS paper_log;
DROP TABLE IF EXISTS paper_question;
DROP TABLE IF EXISTS paper;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS dept;

CREATE TABLE dept (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL COMMENT '部门名称',
  code VARCHAR(32) COMMENT '部门编码',
  created_at DATETIME
) ENGINE=InnoDB COMMENT='部门';

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录名',
  password VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
  real_name VARCHAR(64) NOT NULL COMMENT '姓名',
  role VARCHAR(32) NOT NULL COMMENT 'PM_ADMIN/DEPT_ADMIN',
  dept_id BIGINT COMMENT '所属部门',
  email VARCHAR(128),
  phone VARCHAR(32),
  status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME,
  updated_at DATETIME
) ENGINE=InnoDB COMMENT='管理用户';

CREATE TABLE question (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  bank_type VARCHAR(16) NOT NULL COMMENT 'PUBLIC公共/PRIVATE私有',
  dept_id BIGINT COMMENT '私有题库所属部门',
  type VARCHAR(16) NOT NULL COMMENT 'SINGLE/MULTI/JUDGE/ESSAY',
  content TEXT NOT NULL COMMENT '题干',
  options_json TEXT COMMENT '选项JSON数组',
  answer TEXT COMMENT '参考答案',
  analysis TEXT COMMENT '解析',
  score_points TEXT COMMENT '主观题评分要点',
  skill VARCHAR(64) COMMENT '技能标签: Java/C++/数据库等',
  difficulty TINYINT COMMENT '1入门 2简单 3中等 4较难 5困难',
  knowledge_point VARCHAR(128) COMMENT '知识点: 数组/链表/排序/字符串等',
  default_score DECIMAL(5,1) DEFAULT 5 COMMENT '默认分值',
  status VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/PENDING待审核/DISABLED',
  source_question_id BIGINT COMMENT '贡献来源题目ID',
  creator_id BIGINT,
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_bank (bank_type, dept_id, status),
  KEY idx_skill (skill),
  KEY idx_difficulty (difficulty)
) ENGINE=InnoDB COMMENT='试题';

CREATE TABLE paper (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  dept_id BIGINT,
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  total_score DECIMAL(6,1) DEFAULT 0,
  question_count INT DEFAULT 0,
  description VARCHAR(512),
  job_requirement VARCHAR(512) COMMENT '岗位要求',
  assess_target VARCHAR(512) COMMENT '测评目标',
  creator_id BIGINT,
  pledge_user_id BIGINT COMMENT '保密承诺签署人',
  pledge_time DATETIME,
  pledge_text TEXT COMMENT '保密承诺书内容',
  published_at DATETIME,
  deleted TINYINT DEFAULT 0,
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_dept (dept_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='试卷';

CREATE TABLE paper_question (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  paper_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  score DECIMAL(5,1) NOT NULL,
  sort_order INT DEFAULT 0,
  KEY idx_paper (paper_id)
) ENGINE=InnoDB COMMENT='试卷题目';

CREATE TABLE paper_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  paper_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL COMMENT 'CREATE/ADD/REMOVE/UPDATE/REORDER/PUBLISH/COPY/AI_COMPOSE/PLEDGE',
  detail VARCHAR(1024),
  operator_id BIGINT,
  created_at DATETIME,
  KEY idx_paper (paper_id)
) ENGINE=InnoDB COMMENT='试卷调整日志';

CREATE TABLE exam_project (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  paper_id BIGINT NOT NULL,
  dept_id BIGINT,
  duration_min INT NOT NULL COMMENT '考试时长(分钟)',
  location VARCHAR(256) COMMENT '考试地点',
  batch VARCHAR(64) COMMENT '批次',
  start_time DATETIME COMMENT '开考时间',
  end_time DATETIME COMMENT '截止时间',
  pass_score DECIMAL(6,1) COMMENT '及格线',
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/FINISHED',
  creator_id BIGINT,
  deleted TINYINT DEFAULT 0,
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_dept (dept_id)
) ENGINE=InnoDB COMMENT='笔试项目';

CREATE TABLE exam_candidate (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  id_card VARCHAR(32) NOT NULL COMMENT '身份证号',
  phone VARCHAR(32),
  email VARCHAR(128),
  vendor VARCHAR(128) COMMENT '所属供应商',
  dept_name VARCHAR(64) COMMENT '用人部门',
  source VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'HR/EXCEL/MANUAL',
  hr_id VARCHAR(64) COMMENT '人事系统人员ID',
  status VARCHAR(16) DEFAULT 'NOT_ATTENDED' COMMENT 'NOT_ATTENDED/ATTENDED/SUBMITTED',
  created_at DATETIME,
  UNIQUE KEY uk_project_idcard (project_id, id_card)
) ENGINE=InnoDB COMMENT='考生名单';

CREATE TABLE exam_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  candidate_id BIGINT NOT NULL,
  paper_id BIGINT NOT NULL,
  start_time DATETIME,
  deadline DATETIME COMMENT '自动交卷截止时间',
  submit_time DATETIME,
  submit_type VARCHAR(16) COMMENT 'MANUAL/AUTO',
  status VARCHAR(16) DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/SUBMITTED/GRADED',
  objective_score DECIMAL(6,1) DEFAULT 0,
  subjective_score DECIMAL(6,1) DEFAULT 0,
  total_score DECIMAL(6,1) DEFAULT 0,
  ai_done TINYINT DEFAULT 0,
  review_done TINYINT DEFAULT 0,
  rank_no INT,
  passed TINYINT,
  shortlisted TINYINT DEFAULT 0,
  absent TINYINT DEFAULT 0,
  switch_count INT DEFAULT 0 COMMENT '切屏次数',
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uk_candidate (candidate_id),
  KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='考试记录';

CREATE TABLE answer_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  record_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  type VARCHAR(16),
  answer TEXT COMMENT '考生答案',
  full_score DECIMAL(5,1),
  score DECIMAL(5,1) COMMENT '最终得分',
  ai_score DECIMAL(5,1) COMMENT 'AI初评分',
  ai_comment TEXT COMMENT 'AI评分说明',
  review_score DECIMAL(5,1) COMMENT '复核分',
  reviewer_id BIGINT COMMENT '阅卷人',
  review_time DATETIME,
  review_comment VARCHAR(512),
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/AI_DONE/REVIEWED',
  sort_order INT DEFAULT 0,
  KEY idx_record (record_id)
) ENGINE=InnoDB COMMENT='答题记录';

CREATE TABLE review_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  answer_id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  reviewer_id BIGINT,
  action VARCHAR(16) COMMENT 'AI_SCORE/CONFIRM/ADJUST',
  old_score DECIMAL(5,1),
  new_score DECIMAL(5,1),
  comment VARCHAR(512),
  created_at DATETIME,
  KEY idx_record (record_id)
) ENGINE=InnoDB COMMENT='评分追溯日志';

CREATE TABLE shortlist_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128),
  project_id BIGINT NOT NULL,
  rule_type VARCHAR(16) COMMENT 'TOP_N/RATIO/SCORE_LINE',
  top_n INT,
  ratio DECIMAL(5,2) COMMENT '入围比例%',
  min_score DECIMAL(6,1),
  status VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED',
  confirmed_by BIGINT,
  confirmed_at DATETIME,
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='入围方案';

CREATE TABLE notify_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  type VARCHAR(16) DEFAULT 'EMAIL',
  to_list TEXT,
  cc_list TEXT,
  subject VARCHAR(256),
  content TEXT,
  status VARCHAR(16) COMMENT 'SENT/SIMULATED/FAILED',
  error VARCHAR(512),
  operator_id BIGINT,
  created_at DATETIME,
  KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='考试通知记录';

CREATE TABLE hr_sync_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT,
  direction VARCHAR(8) COMMENT 'PULL拉取名单/PUSH回传结果',
  payload MEDIUMTEXT,
  status VARCHAR(16) COMMENT 'SUCCESS/FAILED/MOCK',
  message VARCHAR(512),
  created_at DATETIME,
  KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='人事系统同步日志';

CREATE TABLE cheat_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  record_id BIGINT NOT NULL,
  event_type VARCHAR(32) COMMENT 'BLUR/VISIBILITY/COPY/PASTE/CONTEXT_MENU/FULLSCREEN_EXIT',
  detail VARCHAR(256),
  created_at DATETIME,
  KEY idx_record (record_id)
) ENGINE=InnoDB COMMENT='防作弊事件';