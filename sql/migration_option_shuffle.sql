-- 同一试卷不同考生选项乱序（单选/多选）：
-- answer_record 记录每位考生每题的选项顺序映射（JSON 数组存原始下标），
-- 进入考试时生成，存答案时按映射把显示字母还原为标准字母，判分与阅卷不受影响。
-- 存量记录 option_order 为 NULL，按原始顺序展示，行为不变。
ALTER TABLE answer_record ADD COLUMN option_order VARCHAR(64) COMMENT '考生选项乱序映射，JSON数组存原始下标，如[2,0,3,1]' AFTER sort_order;