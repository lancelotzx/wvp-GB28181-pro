#!/usr/bin/env bash
# 服务器一键部署 wvp standalone docker 镜像
# 用法: bash deploy-on-server.sh [TAG]
# 假设 /opt/wvp/wvp-pro-standalone-<TAG>.tar.gz 已经 scp 上来
# 假设 /opt/wvp/conf/ 下有 application-dev.yml
# 假设 /opt/wvp/docker-compose.yml 已经存在(或本脚本同目录)

set -euo pipefail

TAG="${1:-$(date +%Y%m%d)}"
WVP_DIR="${WVP_DIR:-/opt/wvp}"
TARBALL="${WVP_DIR}/wvp-pro-standalone-${TAG}.tar.gz"
COMPOSE_FILE="${WVP_DIR}/docker-compose.yml"

echo "[1/6] 检查环境 ..."
[ -f "${TARBALL}" ] || { echo "ERROR: ${TARBALL} 不存在"; exit 1; }
[ -f "${COMPOSE_FILE}" ] || { echo "ERROR: ${COMPOSE_FILE} 不存在"; exit 1; }
[ -d "${WVP_DIR}/conf" ] || { echo "ERROR: ${WVP_DIR}/conf 目录不存在"; exit 1; }
mkdir -p "${WVP_DIR}/logs"

echo "[2/6] load 镜像 ..."
docker load < "${TARBALL}"
docker images | grep "wvp-pro-standalone" | head -3

echo "[3/6] 给旧 nohup 实例发停止信号(如有) ..."
OLD_PIDS=$(pgrep -f 'java -jar.*wvp' || true)
if [ -n "${OLD_PIDS}" ]; then
  echo "  发现旧 nohup 实例 PIDs: ${OLD_PIDS}"
  echo "  10 秒后 kill, Ctrl+C 取消"
  sleep 10
  echo "${OLD_PIDS}" | xargs -r kill
  sleep 5
fi

echo "[4/6] 拉起 compose ..."
cd "${WVP_DIR}"
export WVP_TAG="${TAG}"
export WVP_CONF_DIR="${WVP_DIR}/conf"
export WVP_LOG_DIR="${WVP_DIR}/logs"
docker compose -f "${COMPOSE_FILE}" up -d

echo "[5/6] 等待健康检查 ..."
for i in $(seq 1 30); do
  STATUS=$(docker inspect wvp --format '{{.State.Health.Status}}' 2>/dev/null || echo "starting")
  echo "  attempt ${i}: ${STATUS}"
  if [ "${STATUS}" = "healthy" ]; then
    echo "  容器健康, 启动完成"
    break
  fi
  sleep 5
done

echo "[6/6] 最近日志:"
docker compose -f "${COMPOSE_FILE}" logs --tail 30 wvp

echo ""
echo "部署完成。回滚方法:"
echo "  docker compose -f ${COMPOSE_FILE} down"
echo "  nohup java -jar <旧 jar 路径> --spring.config.location=${WVP_DIR}/conf/application-dev.yml > ${WVP_DIR}/logs/nohup.out 2>&1 &"
