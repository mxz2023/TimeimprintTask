#!/bin/bash
# 重建 TimeImprintTask 真库验收 MySQL。由 run-task-acceptance.sh 在验证前调用，不保留数据。
#
# 用法（在 TimeImprintTask 目录）:
#   bash scripts/recreate-acceptance-mysql.sh
#
# 结果:
#   容器 tit-mysql-t01，仅监听 127.0.0.1:13306，镜像 mysql:9.7.2。
#   空库 db_timeimprint_task（镜像入口创建）和 db_timeimprint_task_perf（就绪后补建）。
#   关闭 binlog。容器带 --rm，停止后连同匿名数据卷一起删除。
#
# 本脚本不碰 Compose 里的 timeimprint-mysql。

set -euo pipefail

CONTAINER="${TIT_MYSQL_CONTAINER:-tit-mysql-t01}"
IMAGE="${TIT_MYSQL_IMAGE:-mysql:9.7.2}"
PORT="${TIT_MYSQL_PORT:-13306}"
ROOT_PASSWORD="${TIT_MYSQL_ROOT_PASSWORD:-tit_root_local}"
USER_NAME="${TIT_MYSQL_USER:-tit}"
USER_PASSWORD="${TIT_MYSQL_PASSWORD:-tit_local}"
FUNCTIONAL_DB="${TIT_FUNCTIONAL_DB:-db_timeimprint_task}"
PERF_DB="${TIT_PERF_DB:-db_timeimprint_task_perf}"

usage() {
    cat <<'EOF'
用法: bash scripts/recreate-acceptance-mysql.sh

删除并新建 TimeImprintTask 验收 MySQL。旧数据和数据卷不保留。

环境变量（可选）:
  TIT_MYSQL_CONTAINER       默认 tit-mysql-t01
  TIT_MYSQL_IMAGE           默认 mysql:9.7.2
  TIT_MYSQL_PORT            默认 13306
  TIT_MYSQL_ROOT_PASSWORD   默认 tit_root_local
  TIT_MYSQL_USER            默认 tit
  TIT_MYSQL_PASSWORD        默认 tit_local
  TIT_FUNCTIONAL_DB         默认 db_timeimprint_task
  TIT_PERF_DB               默认 db_timeimprint_task_perf
EOF
}

require_ident() {
    local name="$1"
    local value="$2"
    if [[ ! "$value" =~ ^[A-Za-z0-9_]+$ ]]; then
        echo "错误: ${name} 只能包含字母、数字和下划线" >&2
        exit 2
    fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi
if [[ $# -ne 0 ]]; then
    usage >&2
    exit 2
fi

require_ident "TIT_MYSQL_USER" "$USER_NAME"
require_ident "TIT_FUNCTIONAL_DB" "$FUNCTIONAL_DB"
require_ident "TIT_PERF_DB" "$PERF_DB"
if [[ "$FUNCTIONAL_DB" == "$PERF_DB" ]]; then
    echo "错误: 功能库和性能库不能同名" >&2
    exit 2
fi

if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
    docker rm -f -v "$CONTAINER" >/dev/null
fi

docker run -d \
    --name "$CONTAINER" \
    --rm \
    -p "127.0.0.1:${PORT}:3306" \
    -e "MYSQL_ROOT_PASSWORD=${ROOT_PASSWORD}" \
    -e "MYSQL_DATABASE=${FUNCTIONAL_DB}" \
    -e "MYSQL_USER=${USER_NAME}" \
    -e "MYSQL_PASSWORD=${USER_PASSWORD}" \
    -e "TZ=UTC" \
    "$IMAGE" \
    --skip-log-bin >/dev/null

# 必须走容器内 3306。初始化阶段的临时服务器只开 socket、端口为 0，
# 这时建库会在临时实例关闭时丢掉。macOS 的 /tmp 也不会进 Docker，不能靠挂载初始化 SQL。
ready=0
for _ in $(seq 1 60); do
    if ! docker container inspect "$CONTAINER" >/dev/null 2>&1; then
        echo "错误: 容器已退出" >&2
        exit 1
    fi
    status="$(docker inspect -f '{{.State.Status}}' "$CONTAINER")"
    if [[ "$status" != "running" ]]; then
        echo "错误: 容器状态为 ${status}" >&2
        docker logs --tail 80 "$CONTAINER" >&2 || true
        exit 1
    fi
    if docker exec -e "MYSQL_PWD=${ROOT_PASSWORD}" "$CONTAINER" \
        mysql -uroot -h 127.0.0.1 --protocol=TCP -N -e "SELECT 1;" >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 2
done

if [[ "$ready" != "1" ]]; then
    echo "错误: 等待 MySQL 就绪超时" >&2
    docker logs --tail 80 "$CONTAINER" >&2 || true
    exit 1
fi

docker exec -e "MYSQL_PWD=${ROOT_PASSWORD}" "$CONTAINER" \
    mysql -uroot -h 127.0.0.1 --protocol=TCP -e \
    "CREATE DATABASE IF NOT EXISTS \`${PERF_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
     GRANT ALL PRIVILEGES ON \`${PERF_DB}\`.* TO '${USER_NAME}'@'%';"

dbs="$(docker exec -e "MYSQL_PWD=${ROOT_PASSWORD}" "$CONTAINER" \
    mysql -uroot -h 127.0.0.1 --protocol=TCP -N -e \
    "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('${FUNCTIONAL_DB}','${PERF_DB}') ORDER BY SCHEMA_NAME;")"
log_bin="$(docker exec -e "MYSQL_PWD=${ROOT_PASSWORD}" "$CONTAINER" \
    mysql -uroot -h 127.0.0.1 --protocol=TCP -N -e "SHOW VARIABLES LIKE 'log_bin';")"

if [[ "$(printf '%s\n' "$dbs" | wc -l | tr -d ' ')" != "2" || "$log_bin" != "log_bin	OFF" ]]; then
    echo "错误: 库或 binlog 状态不符合预期" >&2
    printf 'databases:\n%s\nlog_bin: %s\n' "$dbs" "$log_bin" >&2
    exit 1
fi

echo "已重建 ${CONTAINER}，127.0.0.1:${PORT}，空库 ${FUNCTIONAL_DB} 与 ${PERF_DB}。数据不保留。"
