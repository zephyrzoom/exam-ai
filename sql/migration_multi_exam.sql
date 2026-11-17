-- 修复：一个考生同时生效多个考试时只能看到一个考试项目
-- 原因：Mock HR 名单按 projectId 做随机种子，同一人在不同项目身份证号不一致，
--       考生端按身份证号聚合考试列表，只能命中一个项目。
-- 1) 统一演示考生身份证号为按人员固定种子的新算法值（与修复后的 HrClient.mockCandidates 一致）
UPDATE exam_candidate SET id_card='110101199909267427' WHERE source='HR' AND name='陈嘉豪';
UPDATE exam_candidate SET id_card='110101199912037665' WHERE source='HR' AND name='林晓芸';
UPDATE exam_candidate SET id_card='110101199907149590' WHERE source='HR' AND name='黄志远';
UPDATE exam_candidate SET id_card='110101199911197026' WHERE source='HR' AND name='苏婉婷';
UPDATE exam_candidate SET id_card='110101199903208717' WHERE source='HR' AND name='刘子墨';
UPDATE exam_candidate SET id_card='110101199907146146' WHERE source='HR' AND name='周雨桐';
UPDATE exam_candidate SET id_card='110101199910098069' WHERE source='HR' AND name='吴俊熙';
UPDATE exam_candidate SET id_card='110101199905028314' WHERE source='HR' AND name='郑雅琪';

-- 2) 考试记录唯一键由「每考生一条」改为「每考生每项目一条」，支持同一人参加多个项目
ALTER TABLE exam_record DROP INDEX uk_candidate, ADD UNIQUE KEY uk_project_candidate (project_id, candidate_id);