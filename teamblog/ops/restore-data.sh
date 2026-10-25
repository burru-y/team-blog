#!/usr/bin/env bash
# 把本机导出的数据库与上传文件迁移到新服务器。
#
#   bash ops/restore-data.sh /root/backup-20261025-1530.pgdump /root/uploads-20261025-1531.tar.gz
#
# 顺序很重要：数据库容器必须先用空库起来，恢复完成后才能启动 api，
# 否则 Flyway 会先跑迁移、再被 restore 覆盖，schema 版本可能对不上。
set -euo pipefail

DUMP="${1:?用法: restore-data.sh <dump 文件> [uploads tar.gz]}"
UPLOADS="${2:-}"

cd "$(dirname "$0")/compose"

if [[ ! -f .env.prod ]]; then
  echo "缺少 .env.prod" >&2
  exit 1
fi

COMPOSE=(docker compose -f compose.prod.yaml --env-file .env.prod)
PGUSER=$(grep -E '^POSTGRES_USER=' .env.prod | cut -d= -f2)
PGUSER=${PGUSER:-blog}
PGDB=$(grep -E '^POSTGRES_DB=' .env.prod | cut -d= -f2)
PGDB=${PGDB:-blog}

echo "== 1/4 只启动数据库 =="
"${COMPOSE[@]}" up -d db
for i in $(seq 1 30); do
  if "${COMPOSE[@]}" exec -T db pg_isready -U "$PGUSER" -d "$PGDB" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "== 2/4 恢复数据库 =="
CONTAINER=$("${COMPOSE[@]}" ps -q db)
docker cp "$DUMP" "${CONTAINER}:/tmp/restore.pgdump"
"${COMPOSE[@]}" exec -T db pg_restore -U "$PGUSER" -d "$PGDB" --clean --if-exists --no-owner /tmp/restore.pgdump

echo "== 3/4 恢复上传文件 =="
if [[ -n "$UPLOADS" ]]; then
  VOLUME=$("${COMPOSE[@]}" config --format json | python3 -c 'import json,sys; c=json.load(sys.stdin); print(c["name"]+"_uploads")' 2>/dev/null || echo "yuxiao_uploads")
  TMP=$(mktemp -d)
  tar xzf "$UPLOADS" -C "$TMP"
  docker run --rm -v "${VOLUME}:/data" -v "${TMP}:/in:ro" alpine:3.20 \
    sh -c 'cp -a /in/. /data/ && ls -1 /data | head'
  rm -rf "$TMP"
else
  echo "跳过（未提供 uploads 包）"
fi

echo "== 4/4 启动全部服务 =="
"${COMPOSE[@]}" up -d

sleep 5
echo
echo "== 数据核对 =="
"${COMPOSE[@]}" exec -T db psql -U "$PGUSER" -d "$PGDB" -c \
  "select 'users' as 表, count(*) from users
   union all select 'projects', count(*) from projects
   union all select 'learning_logs', count(*) from learning_logs
   union all select 'checkins', count(*) from checkins;"
