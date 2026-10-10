#!/usr/bin/env bash
# 清空本地验收 MySQL 上的功能库与性能库业务数据，保留 Flyway 历史与表结构。
# 仅用于同一次手工联调。整轮自动化前改用 MyStudio 的
# Deploy/scripts/06-recreate-task-acceptance-mysql.sh 重建空实例，不保留数据。
# 不要对共享/生产库执行。
set -euo pipefail

CONTAINER="${TIT_MYSQL_CONTAINER:-tit-mysql-t01}"
DB_USER="${DB_USERNAME:-tit}"
DB_PASS="${DB_PASSWORD:-tit_local}"
FUNCTIONAL_DB="${TIT_FUNCTIONAL_DB:-timeimprint_task_local}"
PERF_DB="${TIT_PERF_DB:-timeimprint_task_perf}"
ASSUME_YES=0

usage() {
  cat <<'EOF'
用法: scripts/reset-acceptance-dbs.sh [--yes]

清空验收 MySQL 中的功能库与性能库业务表（TRUNCATE），不碰 flyway_schema_history。

环境变量（可选）:
  TIT_MYSQL_CONTAINER   默认 tit-mysql-t01
  DB_USERNAME           默认 tit
  DB_PASSWORD           默认 tit_local
  TIT_FUNCTIONAL_DB     默认 timeimprint_task_local
  TIT_PERF_DB           默认 timeimprint_task_perf

示例:
  scripts/reset-acceptance-dbs.sh
  scripts/reset-acceptance-dbs.sh --yes
EOF
}

for arg in "$@"; do
  case "$arg" in
    -h|--help)
      usage
      exit 0
      ;;
    -y|--yes)
      ASSUME_YES=1
      ;;
    *)
      echo "未知参数: $arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker 命令" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "容器未运行: $CONTAINER（可先 docker start $CONTAINER）" >&2
  exit 1
fi

echo "将清空业务数据（保留 Flyway 历史）:"
echo "  容器: $CONTAINER"
echo "  功能库: $FUNCTIONAL_DB"
echo "  性能库: $PERF_DB"
echo "建议先停 Task 进程，或设置 WORKER_ENABLED=false。"

if [[ "$ASSUME_YES" -ne 1 ]]; then
  read -r -p "确认继续？输入 yes: " reply
  if [[ "$reply" != "yes" ]]; then
    echo "已取消"
    exit 1
  fi
fi

mysql_exec() {
  local db="$1"
  shift
  # 用容器内临时 defaults 文件传密码，避免 mysql -p 命令行触发 insecure 警告。
  docker exec -i \
    -e TIT_MYSQL_USER="$DB_USER" \
    -e TIT_MYSQL_PASS="$DB_PASS" \
    -e TIT_MYSQL_DB="$db" \
    "$CONTAINER" \
    bash -c '
set -euo pipefail
cnf="$(mktemp)"
trap "rm -f \"$cnf\"" EXIT
umask 077
cat > "$cnf" <<EOF
[client]
user=${TIT_MYSQL_USER}
password=${TIT_MYSQL_PASS}
EOF
mysql --defaults-extra-file="$cnf" "$TIT_MYSQL_DB" "$@"
' _ "$@"
}

reset_db() {
  local db="$1"
  echo "==> 清空 $db"
  if ! mysql_exec "$db" -N -e "SELECT 1" >/dev/null 2>&1; then
    echo "跳过：库不存在或无法连接: $db"
    return 0
  fi

  mysql_exec "$db" <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE tt_inbox;
TRUNCATE TABLE tt_notification;
TRUNCATE TABLE tt_action_attempt;
TRUNCATE TABLE tt_action_job;
TRUNCATE TABLE tt_task_transition;
TRUNCATE TABLE tt_task_signal;
TRUNCATE TABLE tt_task_participant;
TRUNCATE TABLE tt_task_instance;
TRUNCATE TABLE tt_trigger_binding;
TRUNCATE TABLE tt_command_dedup;
TRUNCATE TABLE tt_audit_log;
TRUNCATE TABLE tt_task_definition;
SET FOREIGN_KEY_CHECKS=1;
SQL

  # 测试夹具表可能不存在；存在则一并清空。
  mysql_exec "$db" -e "
TRUNCATE TABLE tt_test_approval_data;
" >/dev/null 2>&1 || true

  mysql_exec "$db" -e "
SELECT
  DATABASE() AS db_name,
  (SELECT COUNT(*) FROM flyway_schema_history) AS flyway_rows,
  (SELECT COUNT(*) FROM tt_task_definition) AS definitions,
  (SELECT COUNT(*) FROM tt_task_instance) AS instances,
  (SELECT COUNT(*) FROM tt_task_signal) AS signals,
  (SELECT COUNT(*) FROM tt_action_job) AS actions,
  (SELECT COUNT(*) FROM tt_inbox) AS inbox;
"
}

reset_db "$FUNCTIONAL_DB"
echo
reset_db "$PERF_DB"
echo "完成。业务表计数应为 0；flyway_rows 应大于 0。"
