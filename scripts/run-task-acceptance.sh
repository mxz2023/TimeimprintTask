#!/bin/bash
# 使用 tit-mysql-t01 跑 TimeImprintTask 真库验证。单次不超过 20 分钟。
#
# 用法（在 TimeImprintTask 目录）:
#   bash scripts/run-task-acceptance.sh mysql-it
#   bash scripts/run-task-acceptance.sh dual-process-it
#
# 先重建空库，再执行对应 Maven profile。超过 20 分钟则终止本次 Maven 进程，
# 删除 tit-mysql-t01，并以失败退出。单元测试不走这个脚本。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TASK_DIR="${TIT_TASK_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
STUDIO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONTAINER="${TIT_MYSQL_CONTAINER:-tit-mysql-t01}"
LIMIT_SECONDS="${TIT_ACCEPTANCE_TIMEOUT_SECONDS:-1200}"
PROFILE="${1:-}"

usage() {
    cat <<'EOF'
用法: bash scripts/run-task-acceptance.sh <mysql-it|dual-process-it>

使用 tit-mysql-t01 跑一次真库验证。先重建空库。验证超过 20 分钟则终止并删除容器。

环境变量（可选）:
  TIT_ACCEPTANCE_TIMEOUT_SECONDS  默认 1200
  TIT_TASK_DIR                    默认本脚本所在仓库
  TIT_MYSQL_CONTAINER             默认 tit-mysql-t01
EOF
}

kill_tree() {
    local pid="$1"
    local sig="$2"
    local child
    while read -r child; do
        if [[ -n "$child" ]]; then
            kill_tree "$child" "$sig"
        fi
    done < <(pgrep -P "$pid" || true)
    kill "-$sig" "$pid" 2>/dev/null || true
}

if [[ "$PROFILE" == "-h" || "$PROFILE" == "--help" || -z "$PROFILE" ]]; then
    usage
    if [[ -z "$PROFILE" ]]; then
        exit 2
    fi
    exit 0
fi
if [[ "$PROFILE" != "mysql-it" && "$PROFILE" != "dual-process-it" ]]; then
    echo "错误: 只接受 mysql-it 或 dual-process-it" >&2
    usage >&2
    exit 2
fi
if [[ ! "$LIMIT_SECONDS" =~ ^[0-9]+$ || "$LIMIT_SECONDS" -lt 1 ]]; then
    echo "错误: TIT_ACCEPTANCE_TIMEOUT_SECONDS 必须是正整数" >&2
    exit 2
fi
if [[ ! -x "$TASK_DIR/mvnw" ]]; then
    echo "错误: 找不到 $TASK_DIR/mvnw" >&2
    exit 2
fi

check_identity_deploy_contract() {
    local identity="$TASK_DIR/timeimprint-task-identity/src/main/java/cn/net/mxz/timeimprint/task/identity/account/service/ActorKeyContract.java"
    local entrypoint="$STUDIO_ROOT/Deploy/docker/timeimprint-task/entrypoint.sh"
    local application_yml="$TASK_DIR/timeimprint-task-boot-loader/src/main/resources/application.yml"
    local compose
    local token
    local deleted='IDENTITY_(SMS_|WECHAT_|AUTHORIZATION_STRING)'

    if [[ ! -f "$identity" || ! -f "$entrypoint" || ! -f "$application_yml" ]]; then
        echo "错误: 找不到身份实现、入口或应用配置，无法对照 actor_key" >&2
        exit 1
    fi
    for token in 'wx-' 'u-' 'local-actor'; do
        if ! grep -q -F -- "$token" "$identity"; then
            echo "错误: ActorKeyContract 缺少当前 actor_key 标记 ${token}" >&2
            exit 1
        fi
        if ! grep -q -F -- "$token" "$entrypoint"; then
            echo "错误: 入口未保留 actor_key 标记 ${token}" >&2
            exit 1
        fi
    done
    if grep -q -F -- 'recipient-map.local-actor' "$entrypoint"; then
        echo "错误: 入口只绑定 recipient-map.local-actor" >&2
        exit 1
    fi
    for compose in "$STUDIO_ROOT/Deploy/docker/docker-compose.yml" "$STUDIO_ROOT/Deploy/docker/docker-compose.local.yml"; do
        if grep -E -q -- "$deleted" "$compose"; then
            echo "错误: ${compose} 仍引用已删除的身份变量" >&2
            exit 1
        fi
    done
    if grep -E -q -- "$deleted" "$application_yml"; then
        echo "错误: Task 应用配置仍引用已删除的身份变量" >&2
        exit 1
    fi
    if grep -q -F -- 'fixture-tokens' "$application_yml"; then
        echo "错误: Task 应用配置打开了测试令牌" >&2
        exit 1
    fi

    local nginx="$STUDIO_ROOT/Deploy/docker/nginx/nginx.conf"
    local user_upstream='proxy_pass http://timeimprint-user:18081;'
    local upstream_count
    if [[ ! -f "$nginx" ]]; then
        echo "错误: 找不到外层 Nginx 配置" >&2
        exit 1
    fi
    if ! grep -q -F -- 'location = /api/v1/users {' "$nginx" \
        || ! grep -q -F -- 'location ^~ /api/v1/users/ {' "$nginx"; then
        echo "错误: 外层 Nginx 未把 /api/v1/users 转到用户服务" >&2
        exit 1
    fi
    upstream_count="$(grep -F -c -- "$user_upstream" "$nginx" || true)"
    if [[ "$upstream_count" -lt 4 ]]; then
        echo "错误: 外层 Nginx 的用户服务转发不完整" >&2
        exit 1
    fi
    for compose in "$STUDIO_ROOT/Deploy/docker/docker-compose.yml" "$STUDIO_ROOT/Deploy/docker/docker-compose.local.yml"; do
        if ! grep -q -E -- '^  timeimprint-user:' "$compose"; then
            echo "错误: ${compose} 未编排 timeimprint-user" >&2
            exit 1
        fi
    done
    local local_compose="$STUDIO_ROOT/Deploy/docker/docker-compose.local.yml"
    local local_env="$STUDIO_ROOT/Deploy/docker/.env.local.example"
    if grep -q -F -- 'USER_SMS_MODE:-capture' "$local_compose" \
        || grep -q -E -- '^USER_SMS_MODE=capture$' "$local_env"; then
        echo "错误: 本地部署把账号短信默认成了 capture。capture 只允许自动化测试使用" >&2
        exit 1
    fi
    if ! grep -q -F -- 'USER_SMS_MODE:-tencent' "$local_compose" \
        || ! grep -q -E -- '^USER_SMS_MODE=tencent$' "$local_env"; then
        echo "错误: 本地部署的 USER_SMS_MODE 必须默认 tencent" >&2
        exit 1
    fi
}

check_identity_deploy_contract

# 验收库名由脚本重建并写在默认 JDBC 中。忽略调用方导出的旧库地址。
unset DB_JDBC_URL

bash "$SCRIPT_DIR/recreate-acceptance-mysql.sh"

timed_out_file="$(mktemp)"
rm -f "$timed_out_file"
watchdog_pid=""
mvn_pid=""

cleanup() {
    if [[ -n "$watchdog_pid" ]]; then
        kill "$watchdog_pid" 2>/dev/null || true
        wait "$watchdog_pid" 2>/dev/null || true
    fi
    rm -f "$timed_out_file"
}
trap cleanup EXIT

(
    cd "$TASK_DIR"
    exec ./mvnw -P"$PROFILE" -pl timeimprint-task-boot-loader -am verify
) &
mvn_pid=$!

(
    sleep "$LIMIT_SECONDS"
    if kill -0 "$mvn_pid" 2>/dev/null; then
        echo 1 > "$timed_out_file"
        kill_tree "$mvn_pid" TERM
        sleep 3
        kill_tree "$mvn_pid" KILL
    fi
) &
watchdog_pid=$!

set +e
wait "$mvn_pid"
status=$?
set -e

kill "$watchdog_pid" 2>/dev/null || true
wait "$watchdog_pid" 2>/dev/null || true
watchdog_pid=""

if [[ -f "$timed_out_file" ]]; then
    docker rm -f -v "$CONTAINER" >/dev/null 2>&1 || true
    echo "错误: 使用 ${CONTAINER} 的 ${PROFILE} 超过 ${LIMIT_SECONDS} 秒，已终止并删除容器" >&2
    exit 124
fi

exit "$status"
