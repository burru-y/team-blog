#!/usr/bin/env bash
# 把数据库备份同步到 OSS（异地备份）
#
#   bash ops/backup-offsite.sh
#
# 前置：装好 ossutil 并配好 ~/.ossutilconfig，见 docs/oss-backup.md
#
# 为什么要异地：现在的备份和数据库在同一台机器、同一块云盘上。
# 服务器整机故障、误删实例、云盘损坏，三份备份会一起消失。
set -euo pipefail

OSS_BUCKET="${OSS_BUCKET:?需要设置 OSS_BUCKET，例如 yuxiao-backup}"
OSS_PREFIX="${OSS_PREFIX:-db}"
LOCAL_DIR="${LOCAL_DIR:-/var/lib/docker/volumes/yuxiao_backups/_data}"
KEEP_DAYS="${KEEP_DAYS:-30}"

if [[ ! -d "$LOCAL_DIR" ]]; then
  echo "本地备份目录不存在：$LOCAL_DIR" >&2
  exit 1
fi

if ! command -v ossutil >/dev/null 2>&1; then
  echo "未安装 ossutil，见 docs/oss-backup.md 第 4 步" >&2
  exit 1
fi

echo "== 上传最新的 dump =="
# 只传变化过的文件：ossutil 的 -u 按修改时间判断，没变的不重传
ossutil cp -r -u "$LOCAL_DIR/" "oss://${OSS_BUCKET}/${OSS_PREFIX}/" --include "*.dump"

echo
echo "== 清理 OSS 上超过 ${KEEP_DAYS} 天的备份 =="
cutoff=$(date -d "-${KEEP_DAYS} days" +%Y%m%d)
ossutil ls "oss://${OSS_BUCKET}/${OSS_PREFIX}/" | awk -v cutoff="$cutoff" '
  /oss:\/\// {
    # 行尾是对象地址，取最后一段文件名里的日期
    path = $NF
    if (match(path, /[0-9]{8}/)) {
      day = substr(path, RSTART, 8)
      if (day < cutoff) print path
    }
  }
' > /tmp/oss-expired.txt || true

if [[ -s /tmp/oss-expired.txt ]]; then
  count=$(wc -l < /tmp/oss-expired.txt)
  echo "将删除 $count 个过期对象"
  ossutil rm -f $(cat /tmp/oss-expired.txt) 2>/dev/null || \
    while read -r obj; do ossutil rm -f "$obj"; done < /tmp/oss-expired.txt
else
  echo "没有需要清理的对象"
fi
rm -f /tmp/oss-expired.txt

echo
echo "== 当前 OSS 上的备份 =="
ossutil ls "oss://${OSS_BUCKET}/${OSS_PREFIX}/" | tail -5
