#!/usr/bin/env bash
# 本地 Mac 一键构建 x86_64 wvp standalone 镜像并导出 tar.gz
# 用法: bash deploy-standalone/build-and-export.sh [TAG]
# 默认 TAG=yyyymmdd

set -euo pipefail

cd "$(dirname "$0")/.."
PROJECT_ROOT="$(pwd)"
TAG="${1:-$(date +%Y%m%d)}"
IMAGE="wvp-pro-standalone:${TAG}"
OUTPUT="/tmp/wvp-pro-standalone-${TAG}.tar.gz"

echo "[1/4] 检查 docker buildx ..."
docker buildx version > /dev/null
docker buildx create --use --name wvpbuilder 2>/dev/null || docker buildx use wvpbuilder

echo "[2/4] 构建 linux/amd64 镜像: ${IMAGE} ..."
docker buildx build \
  --platform=linux/amd64 \
  --load \
  -t "${IMAGE}" \
  -f deploy-standalone/Dockerfile \
  "${PROJECT_ROOT}"

echo "[3/4] 验证 architecture ..."
ARCH=$(docker inspect "${IMAGE}" --format '{{.Architecture}}')
if [ "${ARCH}" != "amd64" ]; then
  echo "ERROR: 期望 amd64, 实际 ${ARCH}"
  exit 1
fi
echo "ARCH=${ARCH} OK"

echo "[4/4] 导出 tar.gz 到 ${OUTPUT} ..."
docker save "${IMAGE}" | gzip > "${OUTPUT}"
ls -lh "${OUTPUT}"

echo ""
echo "构建完成, 下一步:"
echo "  scp ${OUTPUT} <user>@<server>:/opt/wvp/"
echo "  ssh <server>"
echo "  cd /opt/wvp && bash deploy-on-server.sh ${TAG}"
