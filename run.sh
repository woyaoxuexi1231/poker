#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKERFILE_NAME="${DOCKERFILE_NAME:-Dockerfile}"
IMAGE_NAME="${IMAGE_NAME:-poker-tracker:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-poker-tracker}"
HOST_PORT="${HOST_PORT:-8080}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"
RESTART_POLICY="${RESTART_POLICY:-unless-stopped}"
TZ="${TZ:-Asia/Shanghai}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker 未安装或不在 PATH 中"
  exit 1
fi

if [[ ! -f "${SCRIPT_DIR}/${DOCKERFILE_NAME}" ]]; then
  echo "[ERROR] 未找到 Dockerfile: ${SCRIPT_DIR}/${DOCKERFILE_NAME}"
  exit 1
fi

resolve_jar_file() {
  if [[ -n "${JAR_FILE:-}" ]]; then
    if [[ ! -f "${SCRIPT_DIR}/${JAR_FILE}" ]]; then
      echo "[ERROR] 指定的 JAR_FILE 不存在: ${SCRIPT_DIR}/${JAR_FILE}"
      exit 1
    fi
    printf '%s\n' "${JAR_FILE}"
    return
  fi

  mapfile -t jars < <(
    find "${SCRIPT_DIR}" -maxdepth 2 -type f -name "*.jar" \
      ! -name "original-*.jar" \
      ! -name "*-sources.jar" \
      ! -name "*-javadoc.jar" \
      -printf "%P\n"
  )

  if [[ ${#jars[@]} -eq 0 ]]; then
    echo "[ERROR] 未在 ${SCRIPT_DIR} 或其二级目录中找到可用的 jar 文件"
    echo "        请把 jar 与脚本放在同级，或通过环境变量指定，例如:"
    echo "        JAR_FILE=poker-tracker.jar bash run.sh"
    exit 1
  fi

  if [[ ${#jars[@]} -gt 1 ]]; then
    echo "[ERROR] 检测到多个 jar 文件，请显式指定 JAR_FILE："
    printf '        %s\n' "${jars[@]}"
    exit 1
  fi

  printf '%s\n' "${jars[0]}"
}

JAR_FILE_RELATIVE="$(resolve_jar_file)"

echo "[INFO] 使用 Dockerfile: ${SCRIPT_DIR}/${DOCKERFILE_NAME}"
echo "[INFO] 使用 JAR: ${SCRIPT_DIR}/${JAR_FILE_RELATIVE}"
echo "[INFO] 构建镜像: ${IMAGE_NAME}"
echo "[INFO] 容器时区: ${TZ}"

docker build \
  -f "${SCRIPT_DIR}/${DOCKERFILE_NAME}" \
  --build-arg "JAR_FILE=${JAR_FILE_RELATIVE}" \
  -t "${IMAGE_NAME}" \
  "${SCRIPT_DIR}"

if docker ps -a --format '{{.Names}}' | grep -Fxq "${CONTAINER_NAME}"; then
  echo "[INFO] 删除旧容器: ${CONTAINER_NAME}"
  docker rm -f "${CONTAINER_NAME}"
fi

echo "[INFO] 启动容器: ${CONTAINER_NAME}"
docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart "${RESTART_POLICY}" \
  -e "TZ=${TZ}" \
  -p "${HOST_PORT}:${CONTAINER_PORT}" \
  "${IMAGE_NAME}"

echo "[INFO] 容器已启动"
echo "[INFO] 访问端口: ${HOST_PORT} -> ${CONTAINER_PORT}"
echo "[INFO] 查看日志: docker logs -f ${CONTAINER_NAME}"
echo "[INFO] 停止容器: docker stop ${CONTAINER_NAME}"
echo "[INFO] 手动停止后，unless-stopped 策略不会自动拉起，直到 Docker 服务重启或手动再次启动"
