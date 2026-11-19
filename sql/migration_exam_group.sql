-- 考试支持编组模式：项目区分个人/编组，考生可归属一个组（按项目内组名标识）。
-- 编组模式下成绩列表展示组名与组平均成绩，成绩导出含「组名」列与「组平均成绩」sheet。
-- 存量项目默认个人模式，行为不变；存量考生组名为空（未分组）。
ALTER TABLE exam_project ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'INDIVIDUAL' COMMENT '考试模式 INDIVIDUAL个人/GROUP编组' AFTER pass_score;
ALTER TABLE exam_candidate ADD COLUMN group_name VARCHAR(64) COMMENT '编组模式下的组名，空为未分组' AFTER vendor;