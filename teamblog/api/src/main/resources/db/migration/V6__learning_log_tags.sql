-- 学习笔记的标签
--
-- 用逗号分隔的字符串而不是 jsonb/数组：标签量小、只做展示与筛选，
-- 换成数组类型要多写转换代码，收益不足以抵消复杂度。
ALTER TABLE learning_logs ADD COLUMN tags VARCHAR(200);
