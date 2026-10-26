-- 编辑功能补全：记录最后修改时间。
-- 为什么不用 DEFAULT now() 一把梭：老数据从没被编辑过，
-- 给它一个"编辑于现在"的时间是错的，应该回填成创建时间。

ALTER TABLE learning_logs ADD COLUMN updated_at timestamptz;

UPDATE learning_logs SET updated_at = created_at;

ALTER TABLE learning_logs ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE learning_logs ALTER COLUMN updated_at SET DEFAULT now();

-- set_updated_at() 在 V1 里已创建，这里直接复用（单一真相源：数据库触发器维护）
CREATE TRIGGER trg_learning_logs_updated
    BEFORE UPDATE ON learning_logs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
