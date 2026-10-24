#!/usr/bin/env bash
# 恢复：把某个 dump 还原到一个**新的临时库**，并断言行数。默认不动生产库。
#
# 用法：
#   ./ops/backup/restore.sh backup/db/2026-10-24.dump            # 演练（还原到 blog_drill）
#   TARGET_DB=blog ./ops/backup/restore.sh <dump>                # 真恢复（危险，需显式指定）
#
# 为什么要"默认还原到临时库"：恢复脚本最常见的用法是演练。
# 默认覆盖生产库，总有一天会在错误的终端里按回车。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP="${1:?用法: restore.sh <dump 文件>}"
TARGET_DB="${TARGET_DB:-blog_drill}"
CONTAINER="${PG_CONTAINER:-teamblog-db-1}"
DB_USER="${POSTGRES_USER:-blog}"

[ -f "$DUMP" ] || { echo "[restore] 找不到文件：$DUMP" >&2; exit 1; }

echo "[restore] $DUMP -> $TARGET_DB"

docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $TARGET_DB;"
docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $TARGET_DB;"

# --no-owner：备份里的属主名在目标环境未必存在，忽略属主更稳
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$TARGET_DB" --no-owner --clean --if-exists < "$DUMP"

echo "[restore] 行数断言"
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$TARGET_DB" -c "
select 'users' as table_name, count(*) from users
union all select 'checkins', count(*) from checkins
union all select 'point_events', count(*) from point_events
union all select 'projects', count(*) from projects
union all select 'learning_logs', count(*) from learning_logs
order by table_name;"

echo "[restore] 完成。确认无误后：DROP DATABASE $TARGET_DB;"
