#!/bin/sh
# 每日备份调度器（在容器里跑）。
#
# 为什么不用 cron：为了一个任务装 cron、还要处理时区与日志重定向，
# 不如一个 sleep 循环——日志直接进 docker logs，排查时不用翻文件。
#
# 为什么用 postgres:16-alpine 镜像：它自带 pg_dump / pg_restore，
# 且不与宿主机的 PostgreSQL 版本冲突（备份工具版本必须 >= 服务端版本）。
#
# 环境变量：
#   BACKUP_AT       每天几点跑，默认 03:10（容器时区）
#   RETENTION_DAYS  保留天数，默认 14
#   RUN_ONCE        设为 1 时只跑一次就退出（用于验证与手动备份）
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backup/db}"
BACKUP_AT="${BACKUP_AT:-03:10}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

log() {
    echo "[backup $(date '+%F %T')] $*"
}

do_backup() {
    # 文件名带日期：同一天重复执行会覆盖（幂等），而不是堆一堆半成品
    target="$BACKUP_DIR/$(date +%F).dump"
    tmp="$target.tmp"

    log "开始备份 $PGDATABASE@$PGHOST -> $target"
    if ! pg_dump -Fc -Z6 -f "$tmp"; then
        log "备份失败：pg_dump 返回非 0"
        rm -f "$tmp"
        return 1
    fi

    # 空文件比没有文件更危险：它看起来成功，恢复时才发现什么都没有
    if [ ! -s "$tmp" ]; then
        log "备份失败：生成的文件为空"
        rm -f "$tmp"
        return 1
    fi

    # 先写 .tmp 再 mv：中途失败不会留下半个文件被当成有效备份
    mv "$tmp" "$target"
    log "完成：$(du -h "$target" | cut -f1)"

    find "$BACKUP_DIR" -name '*.dump' -mtime "+$RETENTION_DAYS" -print -delete \
        | while read -r old; do log "清理过期备份：$old"; done
}

if [ "${RUN_ONCE:-0}" = "1" ]; then
    do_backup
    exit $?
fi

log "调度器启动：每天 $BACKUP_AT 备份，保留 $RETENTION_DAYS 天"

while true; do
    # 用 shell 算术算到下一个执行点的秒数，避免依赖 GNU date 的 -d
    now_h=$(date +%H)
    now_m=$(date +%M)
    now_s=$(date +%S)
    target_h=${BACKUP_AT%%:*}
    target_m=${BACKUP_AT##*:}

    current=$((10#$now_h * 3600 + 10#$now_m * 60 + 10#$now_s))
    target=$((10#$target_h * 3600 + 10#$target_m * 60))

    if [ "$current" -lt "$target" ]; then
        wait_seconds=$((target - current))
    else
        wait_seconds=$((86400 - current + target))
    fi

    log "下次备份在 $((wait_seconds / 60)) 分钟后"
    sleep "$wait_seconds"
    do_backup || true
done
