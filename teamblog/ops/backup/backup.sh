#!/usr/bin/env bash
# 备份：pg_dump 自定义格式 + 压缩，保留 14 天。
#
# 用法（在 compose 目录）：
#   ./ops/backup/backup.sh                 # 从 db 容器备份到宿主机 ./backup/db
#   RETENTION_DAYS=30 ./ops/backup/backup.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$SCRIPT_DIR/../../backup/db}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
CONTAINER="${PG_CONTAINER:-teamblog-db-1}"
DB_NAME="${POSTGRES_DB:-blog}"
DB_USER="${POSTGRES_USER:-blog}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%F)"
TARGET="$BACKUP_DIR/$STAMP.dump"

echo "[backup] $DB_NAME -> $TARGET"

# -Fc 自定义格式：支持并行恢复、单表恢复，比纯 SQL 更适合运维
# 先写临时文件再 mv：避免备份中途失败留下半个文件被当成有效备份
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc -Z6 > "$TARGET.tmp"
mv "$TARGET.tmp" "$TARGET"

# 备份文件必须是可读的，否则恢复时才发现是 0 字节
if [ ! -s "$TARGET" ]; then
    echo "[backup] 失败：备份文件为空" >&2
    exit 1
fi

echo "[backup] 完成：$(du -h "$TARGET" | cut -f1)"

find "$BACKUP_DIR" -name '*.dump' -mtime "+$RETENTION_DAYS" -print -delete
