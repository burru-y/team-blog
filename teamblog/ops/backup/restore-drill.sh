#!/bin/sh
# 恢复演练（容器版）：把卷里的某个 dump 还原到临时库，逐表比对行数，然后删掉临时库。
#
# 用法（在 compose 目录）：
#   docker compose run --rm -e RUN_ONCE=1 backup            # 先备一份
#   docker run --rm --network teamblog_default \
#     -v teamblog_backups:/backup \
#     -e PGPASSWORD=... -e DUMP=/backup/db/2026-10-24.dump \
#     postgres:16-alpine sh /scripts/restore-drill.sh
#
# 为什么演练必须是"独立的、可重跑的脚本"：写在文档里的步骤没人会执行第二遍。
set -eu

DUMP="${DUMP:?请通过 -e DUMP=/backup/db/xxx.dump 指定备份文件}"
TARGET_DB="${TARGET_DB:-blog_restore_drill}"
SOURCE_DB="${PGDATABASE:-blog}"
HOST="${PGHOST:-db}"
USER="${PGUSER:-blog}"

TABLES="users checkins point_events projects learning_logs comments feedbacks"

[ -f "$DUMP" ] || { echo "找不到备份文件：$DUMP" >&2; exit 1; }

echo "[drill] $DUMP -> $TARGET_DB"

dropdb -h "$HOST" -U "$USER" --if-exists "$TARGET_DB"
createdb -h "$HOST" -U "$USER" "$TARGET_DB"

# --no-owner：备份里的属主名在目标环境未必存在
pg_restore -h "$HOST" -U "$USER" -d "$TARGET_DB" --no-owner --clean --if-exists "$DUMP"

echo "[drill] 行数比对"
mismatch=0
for table in $TABLES; do
    src=$(psql -h "$HOST" -U "$USER" -d "$SOURCE_DB" -t -A -c "select count(*) from $table;")
    dst=$(psql -h "$HOST" -U "$USER" -d "$TARGET_DB" -t -A -c "select count(*) from $table;")
    if [ "$src" = "$dst" ]; then
        printf "          %-16s 源 %6s  还原 %6s  OK\n" "$table" "$src" "$dst"
    else
        printf "          %-16s 源 %6s  还原 %6s  不一致\n" "$table" "$src" "$dst"
        mismatch=$((mismatch + 1))
    fi
done

dropdb -h "$HOST" -U "$USER" "$TARGET_DB"

if [ "$mismatch" -gt 0 ]; then
    echo "[drill] 失败：$mismatch 张表行数不一致" >&2
    exit 1
fi
echo "[drill] 通过：备份可以还原，行数完全一致"
