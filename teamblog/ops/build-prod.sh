#!/usr/bin/env bash
# 生产环境构建与启动。
#
#   bash ops/build-prod.sh          # 逐个构建 api / web / ai，再启动全部
#   bash ops/build-prod.sh web      # 只重建某一个服务
#
# 为什么逐个构建：2G 内存的机器上，Node 与 Maven 同时编译会双双被 OOM 杀掉，
# 报错是 "Killed" 或 exit code 137，看起来像构建失败但找不到原因。
set -euo pipefail

cd "$(dirname "$0")/compose"

COMPOSE=(docker compose -f compose.prod.yaml --env-file .env.prod)

if [[ ! -f .env.prod ]]; then
  echo "缺少 .env.prod，先按 docs/deploy.md 第 3.2 节生成" >&2
  exit 1
fi

mem_line() {
  free -h | awk 'NR==2 {printf "内存 %s 已用 %s / 可用 %s；", $2, $3, $7} NR==3 {printf " swap 已用 %s\n", $3}'
}

build_one() {
  local svc="$1"
  echo
  echo "== 构建 $svc =="
  mem_line
  "${COMPOSE[@]}" build "$svc"
}

if [[ $# -gt 0 ]]; then
  build_one "$1"
else
  for svc in api web ai; do
    build_one "$svc"
  done
fi

echo
echo "== 启动全部服务 =="
"${COMPOSE[@]}" up -d

echo
echo "== 等待健康检查 =="
for i in $(seq 1 60); do
  unhealthy=$("${COMPOSE[@]}" ps --format '{{.Name}} {{.Health}}' | grep -c 'starting\|unhealthy' || true)
  if [[ "$unhealthy" == "0" ]]; then
    break
  fi
  sleep 3
  if [[ "$i" == "60" ]]; then
    echo "健康检查超时，看日志：" >&2
    echo "  docker compose -f compose.prod.yaml --env-file .env.prod logs --tail=80" >&2
    exit 1
  fi
done

"${COMPOSE[@]}" ps

echo
echo "== 本机自检 =="
port=$(grep -E '^HTTP_PORT=' .env.prod | cut -d= -f2)
port=${port:-80}
if curl -fsS -o /dev/null "http://127.0.0.1:${port}/"; then
  echo "首页正常：http://127.0.0.1:${port}/"
else
  echo "首页返回异常，看 nginx 日志：" >&2
  echo "  docker compose -f compose.prod.yaml --env-file .env.prod logs --tail=50 nginx" >&2
  exit 1
fi

if curl -fsS -o /dev/null "http://127.0.0.1:${port}/api/v1/health"; then
  echo "API 正常：/api/v1/health"
else
  echo "API 异常，看 api 日志：" >&2
  echo "  docker compose -f compose.prod.yaml --env-file .env.prod logs --tail=80 api" >&2
  exit 1
fi

echo
echo "完成。外网访问：http://<公网IP>:${port}/"
